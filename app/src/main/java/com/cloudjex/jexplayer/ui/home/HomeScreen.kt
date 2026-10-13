package com.cloudjex.jexplayer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cloudjex.jexplayer.data.model.MusicFolder
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import com.cloudjex.jexplayer.viewmodel.RepeatMode
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue


@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onAddFolder: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val revokedFolderUris by viewModel.revokedFolderUris.collectAsState()
    val folderMessage by viewModel.folderMessage.collectAsState()
    val currentSongId by viewModel.currentSongId.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()


    val currentSong = songs.find { it.id == currentSongId }
    var searchQuery by rememberSaveable {
        mutableStateOf("")
    }

    val filteredSongs = songs.filter { song ->
        song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true) ||
                song.album.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Buscar canciones") },
            placeholder = { Text("Título, artista o álbum") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp
                )
        )
        LibraryToolbar(
            isScanning = isScanning,
            onRefresh = { viewModel.refreshLibrary() }
        )
        FoldersSection(
            folders = folders,
            revokedFolderUris = revokedFolderUris,
            message = folderMessage,
            onAddFolder = onAddFolder,
            onRemoveFolder = { viewModel.removeFolder(it) }
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredSongs, key = { it.id }) { song ->
                SongItem(
                    song = song,
                    isCurrent = song.id == currentSongId,
                    onClick = { viewModel.playSong(song) }
                )
            }
            if (filteredSongs.isEmpty()) {
                item {
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "No se encontraron canciones"
                            isScanning -> "Escaneando carpetas…"
                            else -> "No se encontraron archivos de audio en las carpetas seleccionadas."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        if (currentSong != null) {
            MiniPlayer(
                song = currentSong,
                isPlaying = isPlaying,
                isShuffleEnabled = isShuffleEnabled,
                repeatMode = repeatMode,
                currentPosition = currentPosition,
                duration = duration,
                onPrevious = { viewModel.previousSong() },
                onPlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.nextSong() },
                onShuffle = { viewModel.toggleShuffle() },
                onRepeat = { viewModel.toggleRepeat() },
                onSeek = { viewModel.seekTo(it) }
            )
        }
    }
}

@Composable
private fun LibraryToolbar(
    isScanning: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onRefresh,
            enabled = !isScanning
        ) {
            Text("Actualizar biblioteca")
        }

        if (isScanning) {
            Text(
                text = "Actualizando…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FoldersSection(
    folders: List<MusicFolder>,
    revokedFolderUris: Set<String>,
    message: String?,
    onAddFolder: () -> Unit,
    onRemoveFolder: (MusicFolder) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Carpetas de música",
                style = MaterialTheme.typography.titleSmall
            )
            Button(onClick = onAddFolder) {
                Text("Añadir carpeta")
            }
        }

        if (folders.isEmpty()) {
            Text(
                text = "No hay carpetas registradas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            folders.forEach { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = folder.uri,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (folder.uri in revokedFolderUris) {
                            Text(
                                text = "Permiso revocado: vuelve a seleccionarla",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = { onRemoveFolder(folder) }) {
                        Text("Quitar")
                    }
                }
            }
        }

        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SongArtwork(
    song: Song,
    size: Dp,
    cornerRadius: Dp
) {
    val placeholderPainter = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)

    AsyncImage(
        model = song.albumArtUri,
        contentDescription = song.album,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius)),
        contentScale = ContentScale.Crop,
        placeholder = placeholderPainter,
        error = placeholderPainter,
        fallback = placeholderPainter
    )
}

@Composable
private fun SongItem(
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isCurrent) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongArtwork(
                song = song,
                size = 48.dp,
                cornerRadius = 6.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = song.album,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                if (isCurrent) {
                    Text(
                        text = "▶",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = formatDuration(song.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    currentPosition: Long,
    duration: Long,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit
) {
    // Posición temporal del Slider mientras el usuario arrastra.
    var isDragging by remember { mutableStateOf(false) }
    var draggedPosition by remember { mutableStateOf(0f) }

    val sliderMax = duration.coerceAtLeast(1L).toFloat()
    val sliderValue = if (isDragging) {
        draggedPosition.coerceIn(0f, sliderMax)
    } else {
        currentPosition.toFloat().coerceIn(0f, sliderMax)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SongArtwork(
                    song = song,
                    size = 64.dp,
                    cornerRadius = 8.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    isDragging = true
                    draggedPosition = newValue
                },
                onValueChangeFinished = {
                    onSeek(draggedPosition.toLong())
                    isDragging = false
                },
                valueRange = 0f..sliderMax
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(sliderValue.toLong()),
                    style = MaterialTheme.typography.bodySmall
                )

                Button(onClick = onPrevious) {
                    Text("⏮")
                }

                Button(onClick = onPlayPause) {
                    Text(
                        text = if (isPlaying) "⏸" else "▶"
                    )
                }

                Button(onClick = onNext) {
                    Text("⏭")
                }
                Button(
                    onClick = onShuffle,
                    colors = if (isShuffleEnabled) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text("🔀")
                }

                Button(onClick = onRepeat) {
                    Text(
                        text = when (repeatMode) {
                            RepeatMode.OFF -> "🔁"
                            RepeatMode.ALL -> "🔁"
                            RepeatMode.ONE -> "🔂"
                        }
                    )
                }

                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            }
        }
    }


private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(minutes, seconds)
}