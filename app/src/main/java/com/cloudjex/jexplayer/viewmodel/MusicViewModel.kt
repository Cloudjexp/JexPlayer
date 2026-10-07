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
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application.applicationContext)

    private val playerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                nextSong()
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
        _isPlaying.value = true
        _currentPosition.value = 0L
        _duration.value = song.duration
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else {
            player.play()
            _isPlaying.value = true
        }
    }

    fun nextSong() {
        val currentIndex = _songs.value.indexOfFirst {
            it.id == _currentSongId.value
        }

        if (currentIndex != -1 && currentIndex < _songs.value.lastIndex) {
            playSong(_songs.value[currentIndex + 1])
        }
    }

    fun previousSong() {
        val currentIndex = _songs.value.indexOfFirst {
            it.id == _currentSongId.value
        }

        if (currentIndex > 0) {
            playSong(_songs.value[currentIndex - 1])
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
                    _isPlaying.value = true
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
        player.release()
    }
}