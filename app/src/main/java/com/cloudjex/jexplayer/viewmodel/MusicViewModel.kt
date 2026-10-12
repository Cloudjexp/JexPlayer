package com.cloudjex.jexplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.data.repository.MusicRepository
import com.cloudjex.jexplayer.playback.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch



enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application.applicationContext)

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _isShuffleEnabled.value = shuffleModeEnabled
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val songId = mediaItem?.mediaId?.toLongOrNull() ?: return
            _currentSongId.value = songId
            _currentPosition.value = 0L
            _songs.value.find { it.id == songId }?.let { song ->
                _duration.value = song.duration
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

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _currentSongId = MutableStateFlow<Long?>(null)
    val currentSongId: StateFlow<Long?> = _currentSongId.asStateFlow()

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

    init {
        controllerFuture.addListener(
            {
                val connected = runCatching { controllerFuture.get() }.getOrNull()
                    ?: return@addListener

                controller = connected
                connected.addListener(playerListener)

                pendingAction?.let { action ->
                    action(connected)
                    pendingAction = null
                }
            },
            ContextCompat.getMainExecutor(application.applicationContext)
        )

        startProgressTracking()
    }

    fun onPermissionGranted() {
        _hasPermission.value = true
        loadSongs()
    }

    fun playSong(song: Song) {
        _currentSongId.value = song.id
        _currentPosition.value = 0L
        _duration.value = song.duration

        runOnController { controller ->
            val songs = _songs.value
            val startIndex = songs.indexOfFirst { it.id == song.id }

            if (startIndex >= 0) {
                controller.setMediaItems(
                    songs.map { item ->
                        MediaItem.Builder()
                            .setUri(item.uri)
                            .setMediaId(item.id.toString())
                            .build()
                    },
                    startIndex,
                    0L
                )
            } else {
                controller.setMediaItem(MediaItem.fromUri(song.uri))
            }

            controller.shuffleModeEnabled = _isShuffleEnabled.value
            controller.prepare()
            controller.play()
        }
    }

    /**
     * Ejecuta [action] sobre el controller si ya está conectado. Si todavía no lo
     * está, guarda solo la solicitud más reciente para ejecutarla al conectar.
     */
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

    private fun playRandomOtherThan(currentSongId: Long?, songs: List<Song>) {
        val candidates = songs.filter { it.id != currentSongId }

        // Con una sola canción no existe otra opción: se reproduce la misma sin errores.
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

    private fun loadSongs() {
        viewModelScope.launch {
            _songs.value = repository.getSongs()
        }
    }

    override fun onCleared() {
        super.onCleared()
        controller?.removeListener(playerListener)
        controller = null
        pendingAction = null
        MediaController.releaseFuture(controllerFuture)
    }
}