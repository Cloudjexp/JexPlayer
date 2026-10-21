package com.cloudjex.jexplayer.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import coil3.compose.AsyncImage
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.data.model.AudioTechnicalInfo
import com.cloudjex.jexplayer.data.model.displayValues
import com.cloudjex.jexplayer.data.model.forSong
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import com.cloudjex.jexplayer.viewmodel.RepeatMode
import kotlin.math.PI
import kotlin.math.sin
import com.cloudjex.jexplayer.viewmodel.AudioTagsLoadState

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
    val audioTagsLoadState by viewModel.audioTagsLoadState.collectAsState()
    val currentSongAudioTechnicalInfo by viewModel.currentSongAudioTechnicalInfo.collectAsState()

    val currentSong = songs.find { it.id == currentSongId }
        ?: restoredSong?.takeIf { it.id == currentSongId }

    // Estado para controlar si se muestra la pantalla de letra
    var showLyrics by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
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
                val technicalInfo = currentSongAudioTechnicalInfo.forSong(currentSong.id)
                    ?: audioTags.takeIf {
                        audioTagsLoadState == AudioTagsLoadState.LOADED
                    }?.let(AudioTechnicalInfo::from)

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
                    audioTechnicalInfo = technicalInfo,
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
                tags = audioTags.takeIf {
                    audioTagsLoadState == AudioTagsLoadState.LOADED
                },
                tagsLoadState = audioTagsLoadState,
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
            JexSymbolIcon(
                symbol = JexSymbol.ArrowBack,
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
    audioTechnicalInfo: AudioTechnicalInfo?,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onArtworkClick: () -> Unit // <-- Añadido parámetro de evento click
) {
    BoxWithConstraints(modifier = modifier) {
        val artworkSize = minOf(maxWidth, maxHeight * 0.46f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlayerArtwork(
                song = song,
                size = artworkSize,
                onClick = onArtworkClick,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = song.artist.ifBlank { "Artista desconocido" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (song.album.isNotBlank()) {
                        Text(
                            text = song.album,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            InteractiveWaveform(
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                onSeek = onSeek
            )

            TechnicalAudioInfo(info = audioTechnicalInfo)

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
}

@Composable
private fun PlayerArtwork(
    song: Song,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val placeholderPainter = ColorPainter(MaterialTheme.colorScheme.secondaryContainer)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(28.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = song.album,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = placeholderPainter,
            error = placeholderPainter,
            fallback = placeholderPainter
        )
        JexSymbolIcon(
            symbol = JexSymbol.Lyrics,
            contentDescription = "Letras de canciones",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                .padding(8.dp)
                .size(24.dp)
        )
    }
}

@Composable
private fun InteractiveWaveform(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val animationPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing)
        ),
        label = "waveformPhase"
    )
    val sliderMax = duration.coerceAtLeast(1L).toFloat()
    var isDragging by remember { mutableStateOf(false) }
    var draggedFraction by remember { mutableStateOf(0f) }
    val progress = if (isDragging) draggedFraction else {
        currentPosition.toFloat().div(sliderMax).coerceIn(0f, 1f)
    }
    val playedBrush = Brush.horizontalGradient(
        colors = listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary)
    )
    val inactiveBrush = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
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
                            draggedFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            draggedFraction =
                                (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            onSeek((draggedFraction * sliderMax).toLong())
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false }
                    )
                }
                .semantics {
                    contentDescription = "Visualización estilizada de reproducción"
                    stateDescription = formatDuration((progress * sliderMax).toLong())
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    setProgress { targetProgress ->
                        onSeek((targetProgress.coerceIn(0f, 1f) * sliderMax).toLong())
                        true
                    }
                }
        ) {
            val slot = 9.dp.toPx()
            val count = (size.width / slot).toInt().coerceIn(18, 64)
            val step = size.width / count
            val baseBarWidth = (step * 0.42f).coerceAtLeast(2.dp.toPx())
            val motion = if (isPlaying) 1f else 0.2f

            for (index in 0 until count) {
                val n = index.toFloat()
                val wave = sin(n * 0.61f + 0.7f) * 0.45f +
                    sin(n * 1.17f + 2.1f) * 0.3f +
                    sin(n * 0.29f + 0.4f) * 0.25f
                val pulse = if (isPlaying) sin(animationPhase + n * 0.13f) * 0.08f else 0f
                val heightFraction = (0.2f + (wave + 1f) * 0.29f * motion + pulse)
                    .coerceIn(0.14f, 0.9f)
                val barHeight = size.height * heightFraction
                val left = index * step + (step - baseBarWidth) / 2f
                val played = (index + 0.5f) / count <= progress

                val topLeft = Offset(left, (size.height - barHeight) / 2f)
                val barSize = Size(baseBarWidth, barHeight)
                val cornerRadius = CornerRadius(baseBarWidth / 2f, baseBarWidth / 2f)
                if (played) {
                    drawRoundRect(playedBrush, topLeft, barSize, cornerRadius)
                } else {
                    drawRoundRect(inactiveBrush, topLeft, barSize, cornerRadius)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatDuration((progress * sliderMax).toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TechnicalAudioInfo(info: AudioTechnicalInfo?) {
    val details = info?.displayValues().orEmpty()
    val line = details.joinToString(" | ")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (line.isNotEmpty()) {
            val style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val availableWidthPx = with(density) { maxWidth.toPx() }
            val measuredWidthPx = textMeasurer.measure(
                text = AnnotatedString(line),
                style = style,
                maxLines = 1,
                softWrap = false
            ).size.width.toFloat().coerceAtLeast(1f)
            val scale = (availableWidthPx / measuredWidthPx).coerceAtMost(1f)
            val adjustedStyle = style.copy(
                fontSize = (style.fontSize.value * scale).coerceAtLeast(9f).sp
            )

            Text(
                text = line,
                modifier = Modifier.fillMaxWidth(),
                style = adjustedStyle,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
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
            JexSymbolIcon(
                symbol = JexSymbol.Shuffle,
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
            JexSymbolIcon(
                symbol = JexSymbol.SkipPrevious,
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
            JexSymbolIcon(
                symbol = if (isPlaying) JexSymbol.Pause else JexSymbol.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        IconButton(onClick = onNext) {
            JexSymbolIcon(
                symbol = JexSymbol.SkipNext,
                contentDescription = "Siguiente",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(34.dp)
            )
        }

        IconButton(onClick = onRepeat) {
            JexSymbolIcon(
                symbol = if (repeatMode == RepeatMode.ONE) {
                    JexSymbol.RepeatOne
                } else {
                    JexSymbol.Repeat
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
private fun PlayerEmptyState(modifier: Modifier) {
    Box(
        modifier = modifier.padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            JexSymbolIcon(
                symbol = JexSymbol.MusicNote,
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
