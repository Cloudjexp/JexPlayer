package com.cloudjex.jexplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import coil3.compose.AsyncImage
import com.cloudjex.jexplayer.data.model.MusicFolder
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import com.cloudjex.jexplayer.viewmodel.RepeatMode
import com.cloudjex.jexplayer.ui.theme.JexProgress

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val revokedFolderUris by viewModel.revokedFolderUris.collectAsState()
    val folderMessage by viewModel.folderMessage.collectAsState()
    val currentSongId by viewModel.currentSongId.collectAsState()
    val restoredSong by viewModel.restoredSong.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    val currentSong = songs.find { it.id == currentSongId }
        ?: restoredSong?.takeIf { it.id == currentSongId }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var selectedFolderUri by rememberSaveable { mutableStateOf<String?>(null) }

    val filteredSongs = songs.filter { song ->
        (selectedFolderUri == null || song.id.startsWith("${selectedFolderUri}/")) &&
                (song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true) ||
                song.album.contains(searchQuery, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LibraryHeader(
            isScanning = isScanning,
            isSearchVisible = isSearchVisible,
            onSearch = { isSearchVisible = true },
            onSettings = onOpenSettings
        )

        AnimatedVisibility(
            visible = isSearchVisible,
            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
        ) {
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = {
                    searchQuery = ""
                    isSearchVisible = false
                }
            )
        }

        LibrarySummary(
            songCount = songs.size,
            filtersVisible = showFilters,
            onToggleFilters = { showFilters = !showFilters }
        )

        if (showFilters) FoldersFilterRow(
            folders = folders,
            revokedFolderUris = revokedFolderUris,
            selectedFolderUri = selectedFolderUri,
            onFolderSelected = { selectedFolderUri = it }
        )

        folderMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (filteredSongs.isEmpty()) {
                EmptyLibrary(
                    message = when {
                        searchQuery.isNotBlank() || selectedFolderUri != null ->
                            "No se encontraron canciones con estos filtros."
                        isScanning -> "Escaneando carpetas…"
                        else -> "No se encontraron archivos de audio en las carpetas seleccionadas."
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredSongs, key = { it.id }) { song ->
                        SongItem(
                            song = song,
                            isCurrent = song.id == currentSongId,
                            onClick = { viewModel.playSong(song) }
                        )
                    }
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
                onSeek = { viewModel.seekTo(it) },
                onOpenPlayer = onOpenPlayer
            )
        }

        JexBottomBar()
    }
}

@Composable
private fun LibraryHeader(
    isScanning: Boolean,
    isSearchVisible: Boolean,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "夜",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
            Text(
                text = "BIBLIOTECA",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 2.sp
            )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        AnimatedVisibility(
            visible = !isSearchVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = JexSearchIcon,
                    contentDescription = "Buscar canciones",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        IconButton(onClick = onSettings) {
            Icon(
                imageVector = JexSettingsIcon,
                contentDescription = "Ajustes",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }

    if (isScanning) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Buscar canciones…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            )
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = JexCloseIcon,
                    contentDescription = "Cerrar búsqueda",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LibrarySummary(
    songCount: Int,
    filtersVisible: Boolean,
    onToggleFilters: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (songCount == 1) "1 canción" else "$songCount canciones",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onToggleFilters, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = JexFilterIcon,
                contentDescription = if (filtersVisible) "Ocultar filtros" else "Mostrar filtros",
                tint = if (filtersVisible) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun FoldersFilterRow(
    folders: List<MusicFolder>,
    revokedFolderUris: Set<String>,
    selectedFolderUri: String?,
    onFolderSelected: (String?) -> Unit
) {
    if (folders.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selectedFolderUri == null) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                modifier = Modifier.clickable { onFolderSelected(null) }
            ) {
                Text(
                    text = "Todas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            folders.forEach { folder ->
                val isRevoked = folder.uri in revokedFolderUris
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clickable { onFolderSelected(folder.uri) },
                    border = if (isRevoked) {
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error
                        )
                    } else {
                        null
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp)
                        )
                    }
                }
            }
        }

        if (revokedFolderUris.isNotEmpty()) {
            Text(
                text = "Permiso revocado en ${revokedFolderUris.size} carpeta(s): vuelve a seleccionarla.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
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
    val placeholderPainter = ColorPainter(MaterialTheme.colorScheme.secondaryContainer)

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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (isCurrent) {
            // El estado activo se reconoce sin convertir la fila en un bloque rojo.
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongArtwork(song = song, size = 82.dp, cornerRadius = 10.dp)

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist.ifBlank { "Artista desconocido" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.album.isNotBlank()) {
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyLibrary(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
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
    onSeek: (Long) -> Unit,
    onOpenPlayer: () -> Unit
) {
    // Posición temporal mientras el usuario arrastra la barra editorial.
    var isDragging by remember { mutableStateOf(false) }
    var draggedPosition by remember { mutableStateOf(0f) }

    val sliderMax = duration.coerceAtLeast(1L).toFloat()
    val sliderValue = if (isDragging) {
        draggedPosition.coerceIn(0f, sliderMax)
    } else {
        currentPosition.toFloat().coerceIn(0f, sliderMax)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenPlayer),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongArtwork(song = song, size = 52.dp, cornerRadius = 12.dp)

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
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

                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = JexSkipPreviousIcon,
                        contentDescription = "Anterior",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                PlayPauseButton(isPlaying = isPlaying, onClick = onPlayPause)

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = JexSkipNextIcon,
                        contentDescription = "Siguiente",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onShuffle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = JexShuffleIcon,
                        contentDescription = "Aleatorio",
                        tint = if (isShuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = formatDuration(sliderValue.toLong()),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.25.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        // Área táctil cómoda sin convertir la línea en una cápsula.
                        .height(24.dp)
                        .padding(horizontal = 5.dp)
                        .pointerInput(sliderMax) {
                            detectTapGestures { offset ->
                                val fraction =
                                    (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                onSeek((fraction * sliderMax).toLong())
                            }
                        }
                        .pointerInput(sliderMax) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    val fraction =
                                        (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    draggedPosition = fraction * sliderMax
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val fraction =
                                        (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    draggedPosition = fraction * sliderMax
                                },
                                onDragEnd = {
                                    onSeek(draggedPosition.toLong())
                                    isDragging = false
                                }
                            )
                        }
                ) {
                    val trackColor = MaterialTheme.colorScheme.outlineVariant
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerY = size.height / 2f
                        val progressX = size.width * (sliderValue / sliderMax)

                        drawLine(
                            color = trackColor,
                            start = Offset(0f, centerY),
                            end = Offset(size.width, centerY),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = JexProgress,
                            start = Offset(0f, centerY),
                            end = Offset(progressX, centerY),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawCircle(
                            color = JexProgress,
                            radius = 2.5.dp.toPx(),
                            center = Offset(progressX, centerY)
                        )
                    }
                }

                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.25.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = onRepeat,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (repeatMode == RepeatMode.ONE) {
                            JexRepeatOneIcon
                        } else {
                            JexRepeatIcon
                        },
                        contentDescription = "Repetición",
                        tint = if (repeatMode == RepeatMode.OFF) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) JexPauseIcon else JexPlayIcon,
            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun JexBottomBar() {
    val items = listOf(
        BottomBarItem(JexMusicIcon, "Música", selected = true),
        BottomBarItem(JexGridIcon, "Listas", selected = false),
        BottomBarItem(JexFavoriteIcon, "Favoritos", selected = false),
        BottomBarItem(JexFolderIcon, "Carpetas", selected = false)
    )

    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            items.forEach { item ->
                val tint = if (item.selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 2.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (item.selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                    )
                }
            }
        }
    }
}

private data class BottomBarItem(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean
)

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(minutes, seconds)
}

private fun jexVector(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black)
    ).build()

private val JexSearchIcon = jexVector(
    "JexSearch",
    "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 " +
        "5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 " +
        "19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"
)

private val JexFilterIcon = jexVector(
    "JexFilter",
    "M10 18h4v-2h-4v2zM3 6v2h18V6H3zm3 7h12v-2H6v2z"
)

private val JexRefreshIcon = jexVector(
    "JexRefresh",
    "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 " +
        "8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 " +
        "6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"
)

private val JexAddIcon = jexVector(
    "JexAdd",
    "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z"
)

private val JexSettingsIcon = jexVector(
    "JexSettings",
    "M19.43 12.98c.04-.32.07-.65.07-.98s-.02-.66-.07-.98l2.11-1.65c.19-.15.24-.42.12-.64l-2-3.46c-.12-.22-.37-.31-.6-.22l-2.49 1c-.52-.4-1.08-.73-1.69-.98L14.5 2.42C14.47 2.18 14.25 2 14 2h-4c-.25 0-.46.18-.5.42L9.12 5.07c-.61.25-1.17.59-1.69.98l-2.49-1c-.23-.08-.48 0-.6.22l-2 3.46c-.13.22-.07.49.12.64l2.11 1.65c-.04.32-.08.65-.08.98s.03.66.08.98l-2.11 1.65c-.19.15-.24.42-.12.64l2 3.46c.12.22.37.31.6.22l2.49-1c.52.4 1.08.73 1.69.98l.38 2.65c.04.24.25.42.5.42h4c.25 0 .46-.18.5-.42l.38-2.65c.61-.25 1.17-.58 1.69-.98l2.49 1c.23.08.48 0 .6-.22l2-3.46c.12-.22.07-.49-.12-.64l-2.11-1.65zM12 15.5A3.5 3.5 0 1 1 12 8a3.5 3.5 0 0 1 0 7.5z"
)

private val JexCloseIcon = jexVector(
    "JexClose",
    "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 " +
        "17.59 19 19 17.59 13.41 12z"
)

private val JexPlayIcon = jexVector(
    "JexPlay",
    "M8 5v14l11-7z"
)

private val JexPauseIcon = jexVector(
    "JexPause",
    "M6 19h4V5H6v14zm8-14v14h4V5h-4z"
)

private val JexSkipPreviousIcon = jexVector(
    "JexSkipPrevious",
    "M6 6h2v12H6zm3.5 6l8.5 6V6z"
)

private val JexSkipNextIcon = jexVector(
    "JexSkipNext",
    "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"
)

private val JexShuffleIcon = jexVector(
    "JexShuffle",
    "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 " +
        "5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 " +
        "2.04-3.13-3.13z"
)

private val JexRepeatIcon = jexVector(
    "JexRepeat",
    "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z"
)

private val JexRepeatOneIcon = jexVector(
    "JexRepeatOne",
    "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z"
)

private val JexMusicIcon = jexVector(
    "JexMusic",
    "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 " +
        "4-4V7h4V3h-6z"
)

private val JexGridIcon = jexVector(
    "JexGrid",
    "M3 3h8v8H3V3zm10 0h8v8h-8V3zM3 13h8v8H3v-8zm10 0h8v8h-8v-8z"
)

private val JexFavoriteIcon = jexVector(
    "JexFavorite",
    "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 " +
        "3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 " +
        "3.78-3.4 6.86-8.55 11.54L12 21.35z"
)

private val JexFolderIcon = jexVector(
    "JexFolder",
    "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0 " +
        "-1.1-.9-2-2-2h-8l-2-2z"
)
