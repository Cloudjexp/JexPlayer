package com.cloudjex.jexplayer.viewmodel

import android.app.Application
import android.media.session.PlaybackState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.data.repository.MusicRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.media3.common.Player


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

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                onSongCompleted()
            }
        }
    }

    private val player: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        addListener(playerListener)
    }
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
        startProgressTracking()
    }

    fun onPermissionGranted() {
        _hasPermission.value = true
        loadSongs()
    }

    fun playSong(song: Song) {
        player.setMediaItem(MediaItem.fromUri(song.uri))
        player.prepare()
        player.play()

        _currentSongId.value = song.id
        _currentPosition.value = 0L
        _duration.value = song.duration
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
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
        player.repeatMode = when (_repeatMode.value) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE

            RepeatMode.ALL,
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
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
        player.seekTo(position)
        _currentPosition.value = position
    }

    private fun startProgressTracking() {
        viewModelScope.launch {
            while (true) {
                if (player.isPlaying) {
                    _currentPosition.value = player.currentPosition
                    _duration.value = player.duration.coerceAtLeast(0L)
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
        player.removeListener(playerListener)
        player.release()
    }
}