package com.cloudjex.jexplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cloudjex.jexplayer.data.local.AlbumArtStore
import com.cloudjex.jexplayer.data.local.LibraryCache
import com.cloudjex.jexplayer.data.model.AudioTags
import com.cloudjex.jexplayer.data.model.CachedSong
import com.cloudjex.jexplayer.data.model.MusicFolder
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.data.model.toSong
import com.cloudjex.jexplayer.data.repository.FolderRepository
import com.cloudjex.jexplayer.data.repository.SafMusicScanner
import com.cloudjex.jexplayer.playback.PlaybackService
import com.cloudjex.jexplayer.ui.player.FlacInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val folderRepository = FolderRepository(appContext)
    private val albumArtStore = AlbumArtStore.from(appContext)
    private val scanner = SafMusicScanner(appContext, albumArtStore)
    private val libraryCache = LibraryCache.from(appContext)
    private var scanJob: Job? = null
    private var audioTagsJob: Job? = null

    private var cachedEntries: List<CachedSong> = emptyList()
    private var cacheLoaded = false

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _isShuffleEnabled.value = shuffleModeEnabled
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val songId = mediaItem?.mediaId ?: return
            _currentSongId.value = songId
            _currentPosition.value = 0L
            _audioTags.value = audioTagsCache[songId]
            _songs.value.find { it.id == songId }?.let { song ->
                _duration.value = song.duration
                // Los tags se cargan solos en cada cambio de canción, así están
                // disponibles en cualquier pantalla sin que la UI lo pida.
                cargarAudioTags(song)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                onSongCompleted()
            }
        }
    }

    private val controllerFuture = MediaController.Builder(
        application.applicationContext,
        SessionToken(application, ComponentName(application, PlaybackService::class.java))
    ).buildAsync()

    private var controller: MediaController? = null

    private var pendingAction: ((MediaController) -> Unit)? = null
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _currentSongId = MutableStateFlow<String?>(null)
    val currentSongId: StateFlow<String?> = _currentSongId.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _folders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val folders: StateFlow<List<MusicFolder>> = _folders.asStateFlow()

    private val _revokedFolderUris = MutableStateFlow<Set<String>>(emptySet())
    val revokedFolderUris: StateFlow<Set<String>> = _revokedFolderUris.asStateFlow()

    private val _folderMessage = MutableStateFlow<String?>(null)
    val folderMessage: StateFlow<String?> = _folderMessage.asStateFlow()

    private val _audioTags = MutableStateFlow<AudioTags?>(null)
    val audioTags: StateFlow<AudioTags?> = _audioTags.asStateFlow()

    /** Caché en memoria por canción para no releer el archivo en cada visita. */
    private val audioTagsCache = mutableMapOf<String, AudioTags?>()

    init {
        controllerFuture.addListener(
            {
                val connected = runCatching { controllerFuture.get() }.getOrNull()
                    ?: return@addListener

                controller = connected
                connected.addListener(playerListener)

                // Al conectar con una sesión ya activa (p. ej. el servicio sigue
                // reproduciendo tras cerrar la Activity), los Player.Listener no
                // reciben el estado actual. Lo leemos una vez de forma explícita.
                syncStateFromController(connected)

                pendingAction?.let { action ->
                    action(connected)
                    pendingAction = null
                }
            },
            ContextCompat.getMainExecutor(application.applicationContext)
        )

        startProgressTracking()
        reloadLibrary()
    }

    /**
     * Vuelca el estado actual del reproductor en los StateFlow del ViewModel.
     * Necesario al conectar con una MediaSession ya activa: el MediaController no
     * reemite el estado existente a los listeners añadidos tras la conexión.
     * No crea una nueva instancia ni reinicia la reproducción.
     */
    private fun syncStateFromController(controller: MediaController) {
        controller.currentMediaItem?.mediaId?.let { songId ->
            _currentSongId.value = songId
            _songs.value.find { it.id == songId }?.let { song ->
                _duration.value = song.duration
            }
        }

        val controllerDuration = controller.duration
        if (controllerDuration != C.TIME_UNSET && controllerDuration > 0L) {
            _duration.value = controllerDuration
        }

        _currentPosition.value = controller.currentPosition.coerceAtLeast(0L)
        _isPlaying.value = controller.isPlaying
        _isShuffleEnabled.value = controller.shuffleModeEnabled
        _repeatMode.value = when (controller.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
    }

    fun onFolderSelected(uri: Uri?) {
        if (uri == null) {
            return
        }

        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            _folderMessage.value = "No se pudo conservar el permiso de lectura de la carpeta."
            refreshFolders()
            return
        } catch (_: Exception) {
            _folderMessage.value = "La carpeta seleccionada no es válida."
            refreshFolders()
            return
        }

        val name = resolveFolderName(uri)
        val added = folderRepository.addFolder(uri.toString(), name)

        _folderMessage.value = if (added) {
            "Carpeta añadida: $name"
        } else {
            "Esa carpeta ya estaba registrada."
        }

        reloadLibrary()
    }

    fun removeFolder(folder: MusicFolder) {
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        folderRepository.removeFolder(folder.uri)
        _folderMessage.value = "Carpeta retirada: ${folder.name}"
        reloadLibrary()
    }

    fun refreshFolders() {
        val stored = folderRepository.getFolders()
        _folders.value = stored
        _revokedFolderUris.value = stored
            .filterNot { hasPersistedReadPermission(Uri.parse(it.uri)) }
            .map { it.uri }
            .toSet()
    }

    private fun reloadLibrary() {
        refreshFolders()

        val folders = activeFolderUris()
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            if (!cacheLoaded) {
                cachedEntries = libraryCache.load()
                cacheLoaded = true
            }

            _songs.value = visibleCache(folders).map { it.toSong() }

            updateLibrary(folders)
        }
    }

    fun refreshLibrary() {
        reloadLibrary()
    }

    private suspend fun updateLibrary(folders: List<Uri>) {
        if (folders.isEmpty()) {
            _songs.value = emptyList()
            _isScanning.value = false
            if (cacheLoaded && cachedEntries.isNotEmpty()) {
                cachedEntries = emptyList()
                libraryCache.save(emptyList())
            }
            withContext(Dispatchers.IO) { albumArtStore.prune(emptyList()) }
            return
        }

        _isScanning.value = true

        val baseline = visibleCache(folders).associateBy { it.id }
        val outcome = scanner.scan(folders, baseline)

        val merged = buildList {
            folders.forEach { folder ->
                val key = folder.toString()
                if (key in outcome.failedFolders) {
                    addAll(cachedEntries.filter { belongsToFolder(it.id, key) })
                } else {
                    addAll(outcome.scannedByFolder[key].orEmpty())
                }
            }
        }.distinctBy { it.id }

        _songs.value = merged.map { it.toSong() }
        _isScanning.value = false

        if (merged != cachedEntries) {
            cachedEntries = merged
            libraryCache.save(merged)
        }
        withContext(Dispatchers.IO) {
            albumArtStore.prune(merged.map { it.albumArtUri })
        }
    }

        private fun visibleCache(folders: List<Uri>): List<CachedSong> =
        cachedEntries
            .filter { entry -> folders.any { belongsToFolder(entry.id, it.toString()) } }
            .distinctBy { it.id }

    private fun belongsToFolder(documentUri: String, folderUri: String): Boolean =
        documentUri.startsWith("$folderUri/")

    private fun activeFolderUris(): List<Uri> =
        _folders.value
            .filterNot { it.uri in _revokedFolderUris.value }
            .map { Uri.parse(it.uri) }

    private fun hasPersistedReadPermission(uri: Uri): Boolean =
        appContext.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission
        }

    private fun resolveFolderName(uri: Uri): String {
        val queriedName = runCatching {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
            appContext.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull()

        if (!queriedName.isNullOrBlank()) {
            return queriedName
        }

        val fallback = uri.lastPathSegment.orEmpty()
        return fallback.substringAfterLast(':')
            .substringAfterLast('/')
            .ifBlank { fallback.ifBlank { uri.toString() } }
    }

    /**
     * Carga las letras y los metadatos técnicos de [song] desde jAudioTagger.
     * El resultado queda en [audioTags] y se reutiliza mientras la app viva.
     */
    fun cargarAudioTags(song: Song) {
        // `containsKey` y no `?.let`: una canción sin letras también queda
        // cacheada (con `null`) y no debe releerse del disco en cada visita.
        if (audioTagsCache.containsKey(song.id)) {
            _audioTags.value = audioTagsCache[song.id]
            return
        }

        audioTagsJob?.cancel()
        audioTagsJob = viewModelScope.launch {
            _audioTags.value = null
            val tags = withContext(Dispatchers.IO) {
                FlacInfo.leerTags(appContext, song.uri)
            }
            audioTagsCache[song.id] = tags
            _audioTags.value = tags
        }
    }

    fun playSong(song: Song) {
        _currentSongId.value = song.id
        _currentPosition.value = 0L
        _duration.value = song.duration

        runOnController { controller ->
            val songs = _songs.value
            val startIndex = songs.indexOfFirst { it.id == song.id }

            if (startIndex >= 0) {
                if (controllerHasQueue(controller, songs)) {
                    controller.seekTo(startIndex, 0L)
                } else {
                    controller.setMediaItems(
                        songs.map { item -> item.toMediaItem() },
                        startIndex,
                        0L
                    )
                    controller.shuffleModeEnabled = _isShuffleEnabled.value
                    controller.prepare()
                }
            } else {
                controller.setMediaItem(song.toMediaItem())
                controller.shuffleModeEnabled = _isShuffleEnabled.value
                controller.prepare()
            }

            controller.play()
        }
    }

    private fun controllerHasQueue(controller: MediaController, songs: List<Song>): Boolean {
        if (controller.mediaItemCount != songs.size) return false

        for (index in songs.indices) {
            if (controller.getMediaItemAt(index).mediaId != songs[index].id) return false
        }

        return true
    }

    private fun runOnController(action: (MediaController) -> Unit) {
        val connected = controller

        if (connected != null) {
            action(connected)
        } else {
            pendingAction = action
        }
    }

    fun togglePlayPause() {
        runOnController { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value

        runOnController { controller ->
            controller.shuffleModeEnabled = _isShuffleEnabled.value
        }
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }

        applyRepeatModeToPlayer()
    }

    private fun applyRepeatModeToPlayer() {
        val mode = when (_repeatMode.value) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE

            RepeatMode.ALL,
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        }

        runOnController { controller ->
            controller.repeatMode = mode
        }
    }

    fun nextSong() {
        navigateBy(offset = 1)
    }

    fun previousSong() {
        navigateBy(offset = -1)
    }

    private fun navigateBy(offset: Int) {
        val songs = _songs.value

        if (songs.isEmpty()) return

        if (_isShuffleEnabled.value) {
            playRandomOtherThan(_currentSongId.value, songs)
            return
        }

        val currentIndex = songs.indexOfFirst { it.id == _currentSongId.value }

        if (currentIndex == -1) return

        val targetIndex = (currentIndex + offset + songs.size) % songs.size
        playSong(songs[targetIndex])
    }

    private fun playRandomOtherThan(currentSongId: String?, songs: List<Song>) {
        val candidates = songs.filter { it.id != currentSongId }

        val nextSong = candidates.ifEmpty { songs }.random()

        playSong(nextSong)
    }

    /**
     * Avance automático al terminar una canción (STATE_ENDED), respetando Repeat.
     * Repeat ONE lo maneja ExoPlayer de forma nativa (REPEAT_MODE_ONE).
     */
    private fun onSongCompleted() {
        val songs = _songs.value

        if (songs.isEmpty()) return

        when (_repeatMode.value) {
            RepeatMode.ONE -> Unit

            // ALL: avanza de forma circular (o aleatoria si Shuffle está activo).
            RepeatMode.ALL -> nextSong()

            // OFF: avanza hasta la última canción y se detiene al alcanzarla.
            RepeatMode.OFF -> {
                if (_isShuffleEnabled.value) {
                    // Con Shuffle no hay un "final" lineal: se mantiene el salto aleatorio.
                    nextSong()
                } else {
                    val currentIndex = songs.indexOfFirst { it.id == _currentSongId.value }

                    if (currentIndex != -1 && currentIndex < songs.lastIndex) {
                        playSong(songs[currentIndex + 1])
                    }
                }
            }
        }
    }

    fun seekTo(position: Long) {
        _currentPosition.value = position
        runOnController { controller ->
            controller.seekTo(position)
        }
    }

    private fun startProgressTracking() {
        viewModelScope.launch {
            while (true) {
                controller?.let { controller ->
                    if (controller.isPlaying) {
                        _currentPosition.value = controller.currentPosition
                        _duration.value = controller.duration.coerceAtLeast(0L)
                    }
                }

                delay(500)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
        controller?.removeListener(playerListener)
        controller = null
        pendingAction = null
        MediaController.releaseFuture(controllerFuture)
    }
}
private fun Song.toMediaItem(): MediaItem {
    val artwork = albumArtUri
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .apply { if (artwork != null) setArtworkUri(artwork) }
        .build()

    return MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id)
        .setMediaMetadata(metadata)
        .build()
}
