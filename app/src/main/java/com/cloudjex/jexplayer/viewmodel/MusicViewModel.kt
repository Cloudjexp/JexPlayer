package com.cloudjex.jexplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.cloudjex.jexplayer.data.local.AppearancePreferenceStore
import com.cloudjex.jexplayer.data.local.CustomLibrariesStore
import com.cloudjex.jexplayer.data.local.LibraryCache
import com.cloudjex.jexplayer.data.local.LastSongStore
import com.cloudjex.jexplayer.data.local.ScreenPreferenceStore
import com.cloudjex.jexplayer.data.model.AudioTags
import com.cloudjex.jexplayer.data.model.AudioTechnicalInfo
import com.cloudjex.jexplayer.data.model.SongAudioTechnicalInfo
import com.cloudjex.jexplayer.data.model.AppearanceTheme
import com.cloudjex.jexplayer.data.model.CachedSong
import com.cloudjex.jexplayer.data.model.CustomLibrary
import com.cloudjex.jexplayer.data.model.CustomLibraryEditResult
import com.cloudjex.jexplayer.data.model.CustomLibraryNameError
import com.cloudjex.jexplayer.data.model.CustomLibraryRules
import com.cloudjex.jexplayer.data.model.MusicFolder
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.data.model.toSong
import com.cloudjex.jexplayer.data.repository.FolderRepository
import com.cloudjex.jexplayer.data.repository.SafMusicScanner
import com.cloudjex.jexplayer.playback.PlaybackService
import com.cloudjex.jexplayer.ui.player.FlacInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.cloudjex.jexplayer.data.local.FavoritesStore
import com.cloudjex.jexplayer.data.update.CheckResult
import com.cloudjex.jexplayer.data.update.GithubReleaseClient
import com.cloudjex.jexplayer.data.update.GithubRelease
import com.cloudjex.jexplayer.data.update.UpdateChecker
import com.cloudjex.jexplayer.data.update.updateCheckStore
import com.cloudjex.jexplayer.BuildConfig



enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class AudioTagsLoadState {
    IDLE,
    LOADING,
    LOADED,
    ERROR
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val folderRepository = FolderRepository(appContext)
    private val albumArtStore = AlbumArtStore.from(appContext)
    private val scanner = SafMusicScanner(appContext, albumArtStore)
    private val libraryCache = LibraryCache.from(appContext)
    private val screenPreferences = ScreenPreferenceStore.from(appContext)
    private val appearancePreferences =
        AppearancePreferenceStore.from(appContext)
    private val updateChecker = UpdateChecker(
        client = GithubReleaseClient(),
        store = updateCheckStore(appContext),
        installedVersion = BuildConfig.VERSION_NAME
    )

    private val favoritesStore = FavoritesStore.from(appContext)
    private val customLibrariesStore = CustomLibrariesStore.from(appContext)
    private val customLibrariesSaveMutex = Mutex()
    private val lastSongStore = LastSongStore(appContext)
    private val restoredLastSong = lastSongStore.load()
    private var scanJob: Job? = null
    private var userChangedScreen = false
    private var audioTagsJob: Job? = null
    @Volatile
    private var audioTagsRequestId = 0L
    private var audioTagsRequestedSongId: String? = null
    private val audioTechnicalInfoCache = ConcurrentHashMap<String, AudioTechnicalInfo>()
    private var playbackRestored = false
    private var playbackContext: PlaybackContext = PlaybackContext.Library

    private var cachedEntries: List<CachedSong> = emptyList()
    private var cacheLoaded = false
    private var libraryScanCompleted = false

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
            (activeQueueSongs().find { it.id == songId }
                ?: _songs.value.find { it.id == songId }
                ?: mediaItem?.toSongFallback())?.let { song ->
                _restoredSong.value = song
                lastSongStore.save(song, 0L)
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

    private val _currentSongId = MutableStateFlow(restoredLastSong?.song?.id)
    val currentSongId: StateFlow<String?> = _currentSongId.asStateFlow()

    private val _restoredSong = MutableStateFlow(restoredLastSong?.song)
    val restoredSong: StateFlow<Song?> = _restoredSong.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(restoredLastSong?.position ?: 0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(restoredLastSong?.song?.duration ?: 0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _folders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val folders: StateFlow<List<MusicFolder>> = _folders.asStateFlow()

    private val _favoriteIds =
        MutableStateFlow<Set<String>>(emptySet())

    val favoriteIds: StateFlow<Set<String>> =
        _favoriteIds.asStateFlow()

    private val _customLibraries =
        MutableStateFlow<List<CustomLibrary>>(emptyList())
    val customLibraries: StateFlow<List<CustomLibrary>> =
        _customLibraries.asStateFlow()

    private val _customLibrariesReady = MutableStateFlow(false)
    val customLibrariesReady: StateFlow<Boolean> =
        _customLibrariesReady.asStateFlow()

    private val _libraryMessage = MutableStateFlow<String?>(null)
    val libraryMessage: StateFlow<String?> =
        _libraryMessage.asStateFlow()

    private val _revokedFolderUris = MutableStateFlow<Set<String>>(emptySet())
    val revokedFolderUris: StateFlow<Set<String>> = _revokedFolderUris.asStateFlow()

    private val _folderMessage = MutableStateFlow<String?>(null)
    val folderMessage: StateFlow<String?> = _folderMessage.asStateFlow()

    private val _availableUpdate = MutableStateFlow<GithubRelease?>(null)
    val availableUpdate: StateFlow<GithubRelease?> = _availableUpdate.asStateFlow()
    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()
    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

    private val _audioTags = MutableStateFlow<AudioTags?>(null)
    val audioTags: StateFlow<AudioTags?> = _audioTags.asStateFlow()

    private val _audioTagsLoadState = MutableStateFlow(AudioTagsLoadState.IDLE)
    val audioTagsLoadState: StateFlow<AudioTagsLoadState> = _audioTagsLoadState.asStateFlow()

    private val _currentSongAudioTechnicalInfo = MutableStateFlow<SongAudioTechnicalInfo?>(null)
    val currentSongAudioTechnicalInfo: StateFlow<SongAudioTechnicalInfo?> =
        _currentSongAudioTechnicalInfo.asStateFlow()

    /**
     * `true` = reproductor, `false` = biblioteca, `null` = preferencia aún sin
     * resolver. La UI no dibuja ninguna de las dos pantallas hasta que deja de
     * ser `null`, así el destino restaurado aparece desde el primer render.
     */
    private val _showPlayer = MutableStateFlow<Boolean?>(null)
    val showPlayer: StateFlow<Boolean?> = _showPlayer.asStateFlow()

    private val _selectedAppearanceTheme = MutableStateFlow<AppearanceTheme?>(null)
    val selectedAppearanceTheme: StateFlow<AppearanceTheme?> =
        _selectedAppearanceTheme.asStateFlow()

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
                restorePlaybackIfNeeded(connected)

                pendingAction?.let { action ->
                    action(connected)
                    pendingAction = null

                }
            },
            ContextCompat.getMainExecutor(application.applicationContext)
        )


        startProgressTracking()

        viewModelScope.launch {
            val loaded = customLibrariesStore.load()
            _customLibraries.value = loaded.libraries
            _customLibrariesReady.value = true
            if (loaded.corrupted) {
                _libraryMessage.value =
                    "No se pudieron leer las listas guardadas. Se conservó una copia."
            }

            controller?.let { connected ->
                val previousContext = playbackContext
                inferPlaybackContext(connected)
                if (playbackContext != previousContext && libraryScanCompleted) {
                    syncActiveQueue()
                }
                restorePlaybackIfNeeded(connected)
            }
        }

        reloadLibrary()
        restoredLastSong?.song?.let(::cargarAudioTags)

// Observar los favoritos guardados
        viewModelScope.launch {
            android.util.Log.d(
                "JexFavorites",
                "Starting favorites collection"
            )

            try {
                favoritesStore.favoriteIds.collect { ids ->
                    android.util.Log.d(
                        "JexFavorites",
                        "ViewModel received favorites: ${ids.size}"
                    )

                    val changed = _favoriteIds.value != ids
                    _favoriteIds.value = ids
                    controller?.let(::inferPlaybackContext)
                    if (changed && playbackContext == PlaybackContext.Favorites) {
                        syncActiveQueue()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(
                    "JexFavorites",
                    "Error collecting favorites",
                    e
                )
            }
        }

// Se resuelve la preferencia antes de que la UI dibuje Home o Player;
// hasta entonces `showPlayer` es `null` y no se muestra ninguna pantalla.
        viewModelScope.launch {
            val saved = screenPreferences.lastScreen.first()
            if (!userChangedScreen) {
                _showPlayer.value = saved
            }
        }

        viewModelScope.launch {
            _selectedAppearanceTheme.value = try {
                appearancePreferences.selectedTheme.first()
            } catch (_: IOException) {
                AppearanceTheme.default
            }
        }

        // El ViewModel vive una vez por Activity: no depende de recomposiciones ni de
        // cambios de pantalla. El propio checker aplica los intervalos persistidos.
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (_isCheckingUpdates.value) return
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            when (val result = updateChecker.check(force = manual)) {
                CheckResult.Skipped -> if (manual) {
                    _updateMessage.value = "Ya se comprobó recientemente. Inténtalo más tarde."
                }
                CheckResult.Failed -> if (manual) {
                    _updateMessage.value = "No se pudo comprobar si hay actualizaciones."
                }
                is CheckResult.Success -> {
                    if (result.update == null) {
                        if (manual) _updateMessage.value = "Ya tienes la última versión."
                    } else if (manual || !result.wasDismissed) {
                        _availableUpdate.value = result.update
                        if (manual) _updateMessage.value = "Hay una actualización disponible."
                    }
                }
            }
            _isCheckingUpdates.value = false
        }
    }

    fun dismissUpdate() {
        val version = _availableUpdate.value?.version ?: return
        _availableUpdate.value = null
        viewModelScope.launch { updateChecker.dismiss(version) }
    }

    /**
     * Vuelca el estado actual del reproductor en los StateFlow del ViewModel.
     * Necesario al conectar con una MediaSession ya activa: el MediaController no
     * reemite el estado existente a los listeners añadidos tras la conexión.
     * No crea una nueva instancia ni reinicia la reproducción.
     */
    private fun syncStateFromController(controller: MediaController) {
        inferPlaybackContext(controller)
        controller.currentMediaItem?.mediaId?.let { songId ->
            playbackRestored = true
            _currentSongId.value = songId
            (_songs.value.find { it.id == songId }
                ?: controller.currentMediaItem?.toSongFallback())?.let { song ->
                _restoredSong.value = song
                lastSongStore.save(song, controller.currentPosition)
                _duration.value = song.duration
                cargarAudioTags(song)
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

    /**
     * Si el servicio quedó vivo al cerrar la Activity, su cola es la fuente de
     * verdad. El contexto viaja en los metadatos de cada [MediaItem]; si una
     * sesión antigua no los tiene, se conserva la inferencia por favoritos.
     */
    private fun inferPlaybackContext(controller: MediaController) {
        if (controller.mediaItemCount == 0) return

        val taggedItem = controller.currentMediaItem ?: controller.getMediaItemAt(0)
        val extras = taggedItem.mediaMetadata.extras
        val taggedContext = PlaybackContext.fromMetadata(
            type = extras?.getString(PlaybackContext.METADATA_CONTEXT_TYPE),
            libraryId = extras?.getString(PlaybackContext.METADATA_LIBRARY_ID)
        )

        playbackContext = when {
            taggedContext is PlaybackContext.Custom &&
                _customLibrariesReady.value &&
                _customLibraries.value.none { it.id == taggedContext.libraryId } ->
                PlaybackContext.Library

            taggedContext != null -> taggedContext

            else -> inferLegacyContext(controller)
        }
    }

    private fun inferLegacyContext(controller: MediaController): PlaybackContext {
        val ids = (0 until controller.mediaItemCount).map {
            controller.getMediaItemAt(it).mediaId
        }
        val favoriteIds = _favoriteIds.value

        return if (ids.isNotEmpty() &&
            ids.all { it in favoriteIds } &&
            ids.size <= favoriteIds.size
        ) {
            PlaybackContext.Favorites
        } else {
            PlaybackContext.Library
        }
    }

    /** Prepara la última canción guardada sin iniciar la reproducción. */
    private fun restorePlaybackIfNeeded(controller: MediaController) {
        if (playbackRestored) return

        val saved = restoredLastSong ?: return
        val songs = activeQueueSongs()
        if (songs.isEmpty()) return

        val targetIndex = findMediaIdIndex(songs.map { it.id }, saved.song.id)
        if (targetIndex == -1) return

        val queueIsValid = controllerHasQueue(controller, songs, playbackContext)
        val queueIndex = if (queueIsValid) {
            findMediaIdIndex(
                (0 until controller.mediaItemCount).map { controller.getMediaItemAt(it).mediaId },
                saved.song.id
            )
        } else {
            -1
        }

        if (queueIndex >= 0) {
            controller.seekTo(queueIndex, saved.position)
            controller.prepare()
        } else {
            controller.setMediaItems(
                songs.map { it.toMediaItem(playbackContext) },
                targetIndex,
                saved.position
            )
            controller.shuffleModeEnabled = _isShuffleEnabled.value
            controller.prepare()
        }

        controller.playWhenReady = false
        controller.pause()
        playbackRestored = true
        _currentSongId.value = saved.song.id
        _restoredSong.value = songs[targetIndex]
        _currentPosition.value = saved.position
        _duration.value = songs[targetIndex].duration
        lastSongStore.save(songs[targetIndex], saved.position)
        cargarAudioTags(songs[targetIndex])
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

    /**
     * Cambia de pantalla y la persiste. Solo escribe cuando el valor cambia de
     * verdad, así las recomposiciones de Compose no generan escrituras.
     */
    fun setShowPlayer(showPlayer: Boolean) {
        userChangedScreen = true
        if (_showPlayer.value == showPlayer) return

        _showPlayer.value = showPlayer
        viewModelScope.launch { screenPreferences.save(showPlayer) }
    }

    /** Aplica el tema de inmediato y lo conserva para el siguiente inicio. */
    fun setAppearanceTheme(theme: AppearanceTheme) {
        if (_selectedAppearanceTheme.value == theme) return
        _selectedAppearanceTheme.value = theme
        viewModelScope.launch { appearancePreferences.saveTheme(theme) }
    }

    /**
     * Validación síncrona para los diálogos de crear y renombrar. Devuelve
     * `null` si el nombre es válido o si las listas aún se están cargando.
     */
    fun customLibraryNameError(
        rawName: String,
        excludingId: String? = null
    ): CustomLibraryNameError? =
        if (_customLibrariesReady.value) {
            CustomLibraryRules.nameError(_customLibraries.value, rawName, excludingId)
        } else {
            null
        }

    fun createCustomLibrary(rawName: String): Boolean {
        if (!beginCustomLibraryEdit()) return false

        return applyCustomLibraryEdit(
            result = CustomLibraryRules.create(
                libraries = _customLibraries.value,
                id = UUID.randomUUID().toString(),
                rawName = rawName
            ),
            successMessage = "Lista creada: ${CustomLibraryRules.normalizeName(rawName)}"
        )
    }

    /**
     * Crea una lista y añade la canción en una sola actualización de estado.
     * Si el nombre no es válido, no se crea ninguna lista vacía.
     */
    fun createCustomLibraryWithSong(rawName: String, songId: String): Boolean {
        if (!beginCustomLibraryEdit()) return false
        if (songId.isBlank()) {
            _libraryMessage.value = "No se pudo añadir la canción a la lista."
            return false
        }

        return applyCustomLibraryEdit(
            result = CustomLibraryRules.createWithSong(
                libraries = _customLibraries.value,
                id = UUID.randomUUID().toString(),
                rawName = rawName,
                songId = songId
            ),
            successMessage = "Lista creada: ${CustomLibraryRules.normalizeName(rawName)}"
        )
    }

    fun renameCustomLibrary(libraryId: String, rawName: String): Boolean {
        if (!beginCustomLibraryEdit()) return false

        return applyCustomLibraryEdit(
            result = CustomLibraryRules.rename(
                libraries = _customLibraries.value,
                libraryId = libraryId,
                rawName = rawName
            ),
            successMessage = "Lista renombrada."
        )
    }

    fun deleteCustomLibrary(libraryId: String): Boolean {
        if (!beginCustomLibraryEdit()) return false

        val wasActive = isCustomLibraryActive(libraryId)
        val previousLibraries = _customLibraries.value
        val accepted = applyCustomLibraryEdit(
            result = CustomLibraryRules.delete(
                libraries = _customLibraries.value,
                libraryId = libraryId
            ),
            successMessage = "Lista eliminada."
        )

        if (accepted &&
            _customLibraries.value != previousLibraries &&
            wasActive
        ) {
            playbackContext = PlaybackContext.Library
            syncActiveQueue()
        }

        return accepted
    }

    fun addSongToCustomLibrary(libraryId: String, songId: String): Boolean =
        applySongMembershipEdit(
            libraryId = libraryId,
            result = CustomLibraryRules.addSong(
                libraries = _customLibraries.value,
                libraryId = libraryId,
                songId = songId
            )
        )

    fun removeSongFromCustomLibrary(libraryId: String, songId: String): Boolean =
        applySongMembershipEdit(
            libraryId = libraryId,
            result = CustomLibraryRules.removeSong(
                libraries = _customLibraries.value,
                libraryId = libraryId,
                songId = songId
            )
        )

    fun toggleSongInCustomLibrary(libraryId: String, songId: String): Boolean =
        applySongMembershipEdit(
            libraryId = libraryId,
            result = CustomLibraryRules.toggleSong(
                libraries = _customLibraries.value,
                libraryId = libraryId,
                songId = songId
            )
        )

    fun clearLibraryMessage() {
        _libraryMessage.value = null
    }

    private fun beginCustomLibraryEdit(): Boolean {
        if (!_customLibrariesReady.value) {
            _libraryMessage.value = "Cargando listas…"
            return false
        }

        _libraryMessage.value = null
        return true
    }

    private fun applySongMembershipEdit(
        libraryId: String,
        result: CustomLibraryEditResult
    ): Boolean {
        if (!beginCustomLibraryEdit()) return false

        val previousLibraries = _customLibraries.value
        val accepted = applyCustomLibraryEdit(result)

        if (accepted &&
            _customLibraries.value != previousLibraries &&
            isCustomLibraryActive(libraryId)
        ) {
            syncActiveQueue()
        }

        return accepted
    }

    private fun isCustomLibraryActive(libraryId: String): Boolean =
        (playbackContext as? PlaybackContext.Custom)?.libraryId == libraryId

    private fun applyCustomLibraryEdit(
        result: CustomLibraryEditResult,
        successMessage: String? = null
    ): Boolean = when (result) {
        is CustomLibraryEditResult.Updated -> {
            if (_customLibraries.value != result.libraries) {
                _customLibraries.value = result.libraries
                if (successMessage != null) {
                    _libraryMessage.value = successMessage
                }
                persistCustomLibraries()
            }
            true
        }

        is CustomLibraryEditResult.InvalidName -> {
            _libraryMessage.value = customLibraryNameMessage(result.error)
            false
        }

        CustomLibraryEditResult.NotFound -> {
            _libraryMessage.value = "No se encontró la lista."
            false
        }
    }

    private fun customLibraryNameMessage(error: CustomLibraryNameError): String =
        when (error) {
            CustomLibraryNameError.Blank -> "El nombre no puede estar vacío."
            CustomLibraryNameError.TooLong ->
                "El nombre no puede superar los 80 caracteres."
            CustomLibraryNameError.Reserved -> "«Favoritos» es una lista reservada."
            CustomLibraryNameError.Duplicate ->
                "Ya existe una lista con ese nombre."
        }

    private fun persistCustomLibraries() {
        viewModelScope.launch {
            customLibrariesSaveMutex.withLock {
                val snapshot = _customLibraries.value
                if (customLibrariesStore.save(snapshot)) {
                    if (_libraryMessage.value == CUSTOM_LIBRARIES_SAVE_FAILED) {
                        _libraryMessage.value = null
                    }
                } else {
                    _libraryMessage.value = CUSTOM_LIBRARIES_SAVE_FAILED
                }
            }
        }
    }

    private suspend fun updateLibrary(folders: List<Uri>) {
        libraryScanCompleted = false

        if (folders.isEmpty()) {
            _songs.value = emptyList()
            syncActiveQueue()
            libraryScanCompleted = true
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
        syncActiveQueue()
        libraryScanCompleted = true
        _isScanning.value = false

        val restoredId = _currentSongId.value
        if (restoredId != null) {
            val resolved = _songs.value.find { it.id == restoredId }
            if (resolved != null) {
                _restoredSong.value = resolved
                cargarAudioTags(resolved)
            } else if (controller?.currentMediaItem == null) {
                _currentSongId.value = null
                _restoredSong.value = null
                lastSongStore.clear()
            }
        }

        controller?.let { restorePlaybackIfNeeded(it) }

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
            if (audioTagsRequestedSongId != song.id) {
                audioTagsRequestId++
                audioTagsJob?.cancel()
                audioTagsJob = null
            }
            audioTagsRequestedSongId = song.id
            val tags = audioTagsCache[song.id]
            _audioTags.value = tags
            _audioTagsLoadState.value = AudioTagsLoadState.LOADED
            val technicalInfo = audioTechnicalInfoCache[song.id]
                ?: tags?.let(AudioTechnicalInfo::from)
            _currentSongAudioTechnicalInfo.value = technicalInfo?.let {
                SongAudioTechnicalInfo(song.id, it)
            }
            return
        }

        // El listener del MediaController y playSong pueden notificar el mismo
        // cambio. No cancelamos y empezamos de nuevo una lectura idéntica.
        if (audioTagsRequestedSongId == song.id &&
            _audioTagsLoadState.value == AudioTagsLoadState.LOADING
        ) {
            return
        }

        val requestId = ++audioTagsRequestId
        audioTagsJob?.cancel()
        audioTagsRequestedSongId = song.id
        _audioTags.value = null
        _audioTagsLoadState.value = AudioTagsLoadState.LOADING
        _currentSongAudioTechnicalInfo.value = audioTechnicalInfoCache[song.id]?.let {
            SongAudioTechnicalInfo(song.id, it)
        }

        audioTagsJob = viewModelScope.launch {
            try {
                val quickInfo = withContext(Dispatchers.IO) {
                    FlacInfo.leerInfoRapida(appContext, song.uri) { availableInfo ->
                        publishAudioTechnicalInfo(song.id, requestId, availableInfo)
                    }
                }
                quickInfo?.let { publishAudioTechnicalInfo(song.id, requestId, it) }

                val tags = withContext(Dispatchers.IO) {
                    FlacInfo.leerTags(appContext, song.uri) { availableInfo ->
                        publishAudioTechnicalInfo(song.id, requestId, availableInfo)
                    }
                }

                if (!isCurrentAudioTagsRequest(song.id, requestId)) return@launch

                if (tags != null) {
                    audioTagsCache[song.id] = tags
                    publishAudioTechnicalInfo(song.id, requestId, AudioTechnicalInfo.from(tags))
                    _audioTags.value = tags
                    _audioTagsLoadState.value = AudioTagsLoadState.LOADED
                } else {
                    _audioTagsLoadState.value = AudioTagsLoadState.ERROR
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (isCurrentAudioTagsRequest(song.id, requestId)) {
                    _audioTagsLoadState.value = AudioTagsLoadState.ERROR
                }
            }
        }
    }

    private fun isCurrentAudioTagsRequest(songId: String, requestId: Long): Boolean =
        requestId == audioTagsRequestId && _currentSongId.value == songId

    private fun publishAudioTechnicalInfo(
        songId: String,
        requestId: Long,
        availableInfo: AudioTechnicalInfo
    ) {
        if (!availableInfo.hasValues || !isCurrentAudioTagsRequest(songId, requestId)) return

        var mergedInfo: AudioTechnicalInfo? = null
        audioTechnicalInfoCache.compute(songId) { _, cachedInfo ->
            val visibleInfo = _currentSongAudioTechnicalInfo.value
                ?.takeIf { it.songId == songId }
                ?.info
            (visibleInfo ?: cachedInfo ?: AudioTechnicalInfo())
                .merge(availableInfo)
                .also { mergedInfo = it }
        }

        if (isCurrentAudioTagsRequest(songId, requestId)) {
            mergedInfo?.let { info ->
                _currentSongAudioTechnicalInfo.value = SongAudioTechnicalInfo(songId, info)
            }
        }
    }

    fun playSong(song: Song) {
        playSong(song, null)
    }

    fun playSong(song: Song, context: PlaybackContext?) {
        if (context != null) playbackContext = context
        if (playbackContext is PlaybackContext.Custom && !_customLibrariesReady.value) {
            return
        }

        val queueContext = playbackContext
        val queueSongs = activeQueueSongs()
        if (song !in queueSongs) return

        _currentSongId.value = song.id
        _restoredSong.value = song
        _currentPosition.value = 0L
        _duration.value = song.duration
        lastSongStore.save(song, 0L)
        playbackRestored = true
        cargarAudioTags(song)

        runOnController { controller ->
            val songs = activeQueueSongs()
            val startIndex = songs.indexOfFirst { it.id == song.id }

            if (startIndex >= 0) {
                if (controllerHasQueue(controller, songs, queueContext)) {
                    controller.seekTo(startIndex, 0L)
                } else {
                    controller.setMediaItems(
                        songs.map { item -> item.toMediaItem(queueContext) },
                        startIndex,
                        0L
                    )
                    controller.shuffleModeEnabled = _isShuffleEnabled.value
                    controller.prepare()
                }
            } else {
                controller.setMediaItem(song.toMediaItem(queueContext))
                controller.shuffleModeEnabled = _isShuffleEnabled.value
                controller.prepare()
            }

            controller.play()
        }
    }

    private fun activeQueueSongs(): List<Song> {
        val customLibrarySongIds =
            (playbackContext as? PlaybackContext.Custom)?.let { context ->
                _customLibraries.value
                    .firstOrNull { it.id == context.libraryId }
                    ?.songIds
            }.orEmpty()

        return PlaybackQueue.songsFor(
            context = playbackContext,
            library = _songs.value,
            favoriteIds = _favoriteIds.value,
            customLibrarySongIds = customLibrarySongIds
        )
    }

    /** Actualiza Media3 solo cuando la lista contextual cambió. */
    private fun syncActiveQueue() {
        runOnController { controller ->
            if (playbackContext is PlaybackContext.Custom &&
                !_customLibrariesReady.value
            ) {
                return@runOnController
            }

            // Sin una cola activa la reproducción se construirá en playSong.
            if (controller.mediaItemCount == 0 && controller.currentMediaItem == null) {
                return@runOnController
            }

            val queueContext = playbackContext
            val songs = activeQueueSongs()
            if (controllerHasQueue(controller, songs, queueContext)) {
                return@runOnController
            }

            val currentId = controller.currentMediaItem?.mediaId ?: _currentSongId.value
            val previousIndex = controller.currentMediaItemIndex
            val targetIndex = PlaybackQueue.resolveIndexAfterChange(
                ids = songs.map(Song::id),
                currentId = currentId,
                previousIndex = previousIndex
            )

            if (songs.isEmpty() || targetIndex < 0) {
                controller.clearMediaItems()
                controller.pause()
                _currentSongId.value = null
                _restoredSong.value = null
                lastSongStore.clear()
                return@runOnController
            }

            val targetSong = songs[targetIndex]
            val currentSongKept = targetSong.id == currentId
            val wasPlaying = controller.isPlaying || controller.playWhenReady
            val position = if (currentSongKept) {
                controller.currentPosition.coerceAtLeast(0L)
            } else {
                0L
            }

            controller.setMediaItems(
                songs.map { it.toMediaItem(queueContext) },
                targetIndex,
                position
            )
            controller.shuffleModeEnabled = _isShuffleEnabled.value
            controller.repeatMode = if (_repeatMode.value == RepeatMode.ONE) {
                Player.REPEAT_MODE_ONE
            } else Player.REPEAT_MODE_OFF
            controller.prepare()
            controller.playWhenReady = wasPlaying

            if (!currentSongKept) {
                _currentSongId.value = targetSong.id
                _restoredSong.value = targetSong
                _currentPosition.value = 0L
                _duration.value = targetSong.duration
                lastSongStore.save(targetSong, 0L)
                cargarAudioTags(targetSong)
            }
        }
    }

    private fun controllerHasQueue(
        controller: MediaController,
        songs: List<Song>,
        context: PlaybackContext
    ): Boolean {
        if (controller.mediaItemCount != songs.size) return false

        val expectedType = PlaybackContext.metadataType(context)
        val expectedLibraryId = PlaybackContext.metadataLibraryId(context)

        for (index in songs.indices) {
            val item = controller.getMediaItemAt(index)
            if (item.mediaId != songs[index].id) return false

            val extras = item.mediaMetadata.extras
            if (extras?.getString(PlaybackContext.METADATA_CONTEXT_TYPE) != expectedType) {
                return false
            }
            if (extras?.getString(PlaybackContext.METADATA_LIBRARY_ID) != expectedLibraryId) {
                return false
            }
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
        PlaybackQueue.navigate(
            songs = activeQueueSongs(),
            currentSongId = _currentSongId.value,
            offset = offset,
            shuffle = _isShuffleEnabled.value
        )?.let(::playSong)
    }

    fun toggleFavorite(songId: String) {
        android.util.Log.d(
            "JexFavorites",
            "ViewModel: toggleFavorite called"
        )

        viewModelScope.launch {
            try {
                android.util.Log.d(
                    "JexFavorites",
                    "Saving favorite: $songId"
                )

                favoritesStore.toggleFavorite(songId)

                android.util.Log.d(
                    "JexFavorites",
                    "Favorite saved successfully"
                )

            } catch (e: Exception) {
                android.util.Log.e(
                    "JexFavorites",
                    "Error saving favorite",
                    e
                )
            }
        }
    }

    /**
     * Avance automático al terminar una canción (STATE_ENDED), respetando Repeat.
     * Repeat ONE lo maneja ExoPlayer de forma nativa (REPEAT_MODE_ONE).
     */
    private fun onSongCompleted() {
        val songs = activeQueueSongs()

        if (songs.isEmpty()) return

        PlaybackQueue.automaticNext(
            songs = songs,
            currentSongId = _currentSongId.value,
            repeatMode = _repeatMode.value,
            shuffle = _isShuffleEnabled.value
        )?.let(::playSong)
    }

    fun seekTo(position: Long) {
        _currentPosition.value = position
        _restoredSong.value?.let { lastSongStore.save(it, position) }
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
                        _restoredSong.value?.let {
                            lastSongStore.save(it, _currentPosition.value)
                        }
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
        _restoredSong.value?.let { lastSongStore.save(it, _currentPosition.value) }
        MediaController.releaseFuture(controllerFuture)
    }
}

private const val CUSTOM_LIBRARIES_SAVE_FAILED = "No se pudieron guardar las listas."

private fun Song.toMediaItem(context: PlaybackContext): MediaItem {
    val artwork = albumArtUri
    val extras = Bundle().apply {
        putString(
            PlaybackContext.METADATA_CONTEXT_TYPE,
            PlaybackContext.metadataType(context)
        )
        PlaybackContext.metadataLibraryId(context)?.let { libraryId ->
            putString(PlaybackContext.METADATA_LIBRARY_ID, libraryId)
        }
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setExtras(extras)
        .apply { if (artwork != null) setArtworkUri(artwork) }
        .build()

    return MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id)
        .setMediaMetadata(metadata)
        .build()
}


private fun MediaItem.toSongFallback(): Song? {
    val id = mediaId.takeIf { it.isNotBlank() } ?: return null
    val uri = localConfiguration?.uri ?: return null
    return Song(
        id = id,
        title = mediaMetadata.title?.toString().orEmpty().ifBlank { "Sin título" },
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString().orEmpty(),
        duration = 0L,
        uri = uri,
        albumArtUri = mediaMetadata.artworkUri
    )
}
