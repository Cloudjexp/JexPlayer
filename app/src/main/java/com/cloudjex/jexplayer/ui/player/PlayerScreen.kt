package com.cloudjex.jexplayer.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.ui.theme.JexPrimary
import com.cloudjex.jexplayer.ui.theme.JexProgress
import com.cloudjex.jexplayer.ui.theme.JexRedSecondary
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import com.cloudjex.jexplayer.viewmodel.RepeatMode
import kotlin.math.PI
import kotlin.math.sin
import com.cloudjex.jexplayer.ui.icons.JexArrowBackIcon
import com.cloudjex.jexplayer.ui.icons.JexMusicIcon
import com.cloudjex.jexplayer.ui.icons.JexLyricsIcon
import com.cloudjex.jexplayer.ui.icons.JexRepeatIcon
import com.cloudjex.jexplayer.ui.icons.JexShuffleIcon
import com.cloudjex.jexplayer.ui.icons.JexPlayIcon
import com.cloudjex.jexplayer.ui.icons.JexRepeatOneIcon
import com.cloudjex.jexplayer.ui.icons.JexSkipNextIcon
import com.cloudjex.jexplayer.ui.icons.JexSkipPreviousIcon
import com.cloudjex.jexplayer.ui.icons.JexPauseIcon

@Composable
fun PlayerScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val currentSongId by viewModel.currentSongId.collectAsState()
    val restoredSong by viewModel.restoredSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val audioTags by viewModel.audioTags.collectAsState()

    val currentSong = songs.find { it.id == currentSongId }
        ?: restoredSong?.takeIf { it.id == currentSongId }

    // Estado para controlar si se muestra la pantalla de letra
    var showLyrics by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            PlayerTopBar(onBack = onBack)

            if (currentSong == null) {
                PlayerEmptyState(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                PlayerContent(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                    onArtworkClick = { showLyrics = true } // <-- Activamos la apertura aquí
                )
            }
        }

        // Si showLyrics es true, se dibuja encima cubriendo toda la pantalla
        if (showLyrics && currentSong != null) {
            LyricsScreen(
                onSeek = { viewModel.seekTo(it) },
                song = currentSong,
                tags = audioTags,
                onClose = { showLyrics = false })

        }
    }
}

@Composable
private fun PlayerTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = JexArrowBackIcon,
                contentDescription = "Volver a la biblioteca",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Text(
            text = "REPRODUCTOR",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun PlayerContent(
    modifier: Modifier,
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
    onArtworkClick: () -> Unit // <-- Añadido parámetro de evento click
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PlayerArtwork(song = song, onClick = onArtworkClick) // <-- Pasamos la función aquí

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.album.isNotBlank()) {
                Text(
                    text = song.album,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        AudioVisualizer(isPlaying = isPlaying)

        PlayerProgress(
            currentPosition = currentPosition,
            duration = duration,
            onSeek = onSeek
        )

        PlayerControls(
            isPlaying = isPlaying,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onShuffle = onShuffle,
            onRepeat = onRepeat
        )
    }
}

@Composable
private fun PlayerArtwork(song: Song, onClick: () -> Unit) {
    val placeholderPainter = ColorPainter(MaterialTheme.colorScheme.secondaryContainer)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = song.album,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
            placeholder = placeholderPainter,
            error = placeholderPainter,
            fallback = placeholderPainter
        )
        Icon(
            imageVector = JexLyricsIcon,
            contentDescription = "Letras de canciones",
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(24.dp)
        )
    }
}

@Composable
private fun PlayerProgress(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var draggedPosition by remember { mutableStateOf(0f) }

    val sliderMax = duration.coerceAtLeast(1L).toFloat()
    val sliderValue = if (isDragging) {
        draggedPosition.coerceIn(0f, sliderMax)
    } else {
        currentPosition.toFloat().coerceIn(0f, sliderMax)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatDuration(sliderValue.toLong()),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .weight(1f)
                // La zona táctil es amplia aunque la línea sea deliberadamente fina.
                .height(28.dp)
                .padding(horizontal = 6.dp)
                .pointerInput(sliderMax) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onSeek((fraction * sliderMax).toLong())
                    }
                }
                .pointerInput(sliderMax) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
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
                    radius = 3.dp.toPx(),
                    center = Offset(progressX, centerY)
                )
            }
        }

        Text(
            text = formatDuration(duration),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onShuffle) {
            Icon(
                imageVector = JexShuffleIcon,
                contentDescription = "Aleatorio",
                tint = if (isShuffleEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
        }

        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = JexSkipPreviousIcon,
                contentDescription = "Anterior",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(34.dp)
            )
        }

        Box(
            modifier = Modifier
            .size(58.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) JexPauseIcon else JexPlayIcon,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                imageVector = JexSkipNextIcon,
                contentDescription = "Siguiente",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(34.dp)
            )
        }

        IconButton(onClick = onRepeat) {
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
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun AudioVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "visualizer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing)
        ),
        label = "progress"
    )

    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.16f,
        animationSpec = tween(durationMillis = 700),
        label = "amplitude"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
    ) {
        val slot = 10.dp.toPx()
        val count = (size.width / slot).toInt().coerceIn(12, 38)
        val step = size.width / count
        val baseBarWidth = (step * 0.34f).coerceAtLeast(1.5.dp.toPx())
        val minFraction = 0.12f
        val maxFraction = 0.88f
        val fullTurn = (2 * PI).toFloat()

        val brush = Brush.horizontalGradient(
            colors = listOf(JexRedSecondary, JexPrimary, JexRedSecondary)
        )

        for (index in 0 until count) {
            val n = index.toFloat()

            // Variación determinista para evitar un patrón de ecualizador uniforme.
            val barWidth = (baseBarWidth * (0.72f + 0.42f *
                    (sin(n * 0.73f + 1.2f) * 0.5f + 0.5f)))
                .coerceAtLeast(1.5.dp.toPx())
            val radius = barWidth / 2f

            val wave = sin(fullTurn * progress + n * 0.55f) * 0.5f +
                    sin(fullTurn * 2f * progress + n * 1.15f + 0.8f) * 0.3f +
                    sin(fullTurn * 3f * progress + n * 0.35f + 2.1f) * 0.2f
            val dynamic = ((wave + 1f) * 0.5f).coerceIn(0f, 1f)

            val organicHeight = 0.78f + 0.22f * (sin(n * 0.37f + 0.9f) * 0.5f + 0.5f)
            val fraction = minFraction + dynamic * amplitude * organicHeight *
                    (maxFraction - minFraction)
            val barHeight = fraction * size.height
            val left = index * step + (step - barWidth) / 2f
            val top = size.height - barHeight

            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}

@Composable
private fun PlayerEmptyState(modifier: Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = JexMusicIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No hay ninguna canción en reproducción.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%d:%02d".format(minutes, seconds)
}

//private fun jexVector(name: String, pathData: String): ImageVector =
//    ImageVector.Builder(
//        name = name,
//        defaultWidth = 24.dp,
//        defaultHeight = 24.dp,
//        viewportWidth = 24f,
//        viewportHeight = 24f
//    ).addPath(
//        pathData = PathParser().parsePathString(pathData).toNodes(),
//        fill = SolidColor(Color.Black)
//    ).build()
//
//private val JexArrowBackIcon = jexVector(
//    "JexArrowBack",
//    "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"
//)
//
//private val JexMoreIcon = jexVector(
//    "JexMore",
//    "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 " +
//            "2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"
//)
//
//private val JexPlayIcon = jexVector(
//    "JexPlay",
//    "M8 5v14l11-7z"
//)
//
//private val JexPauseIcon = jexVector(
//    "JexPause",
//    "M6 19h4V5H6v14zm8-14v14h4V5h-4z"
//)
//
//private val JexSkipPreviousIcon = jexVector(
//    "JexSkipPrevious",
//    "M6 6h2v12H6zm3.5 6l8.5 6V6z"
//)
//
//private val JexSkipNextIcon = jexVector(
//    "JexSkipNext",
//    "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"
//)
//
//private val JexShuffleIcon = jexVector(
//    "JexShuffle",
//    "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 " +
//            "5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 " +
//            "2.04-3.13-3.13z"
//)
//
//private val JexRepeatIcon = jexVector(
//    "JexRepeat",
//    "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z"
//)
//
//private val JexRepeatOneIcon = jexVector(
//    "JexRepeatOne",
//    "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z"
//)
//
//private val JexMusicIcon = jexVector(
//    "JexMusic",
//    "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 " +
//            "4-4V7h4V3h-6z"
//)
