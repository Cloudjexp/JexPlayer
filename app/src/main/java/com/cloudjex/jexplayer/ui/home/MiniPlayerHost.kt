package com.cloudjex.jexplayer.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.viewmodel.MusicViewModel

/**
 * Extraído literalmente de `HomeScreen`: resuelve la canción actual dentro de la
 * biblioteca (o la última restaurada) y delega el dibujo en [MiniPlayer]. Si no
 * hay canción no dibuja nada, igual que antes.
 */
@Composable
internal fun MiniPlayerHost(
    songs: List<Song>,
    viewModel: MusicViewModel,
    onOpenPlayer: () -> Unit
) {
    val currentSongId by viewModel.currentSongId.collectAsState()
    val restoredSong by viewModel.restoredSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val currentSong = songsById[currentSongId]
        ?: restoredSong?.takeIf { it.id == currentSongId }

    if (currentSong != null) {
        MiniPlayer(
            song = currentSong,
            isPlaying = isPlaying,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            onPrevious = { viewModel.previousSong() },
            onPlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.nextSong() },
            onShuffle = { viewModel.toggleShuffle() },
            onRepeat = { viewModel.toggleRepeat() },
            onOpenPlayer = onOpenPlayer
        )
    }
}
