package com.cloudjex.jexplayer.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.cloudjex.jexplayer.data.lyrics.LyricsDocument
import com.cloudjex.jexplayer.data.lyrics.LyricsLoadState
import com.cloudjex.jexplayer.data.lyrics.LyricsSource
import com.cloudjex.jexplayer.data.lyrics.LyricsTranslationSupport
import com.cloudjex.jexplayer.data.model.LyricsSettings
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.ui.home.MiniPlayer
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LyricsScreen(
    onSeek: (Long) -> Unit,
    song: Song,
    lyricsDocument: LyricsDocument?,
    lyricsSources: List<LyricsSource>,
    selectedLyricsSourceId: String?,
    lyricsLoadState: LyricsLoadState,
    settings: LyricsSettings,
    onSelectLyricsSource: (String) -> Unit,
    onLyricsSettingsChange: (LyricsSettings) -> Unit,
    onClose: () -> Unit
) {
    val listaSincronizada = lyricsDocument?.lineas.orEmpty()
    var mostrarOpciones by remember { mutableStateOf(false) }

    // 1. Estado para controlar el scroll del LazyColumn
    val listState = rememberLazyListState()

    val colorActivo = settings.colorPreset.colorActivo
    val colorPendiente = settings.colorPreset.colorPendiente
    val familiaLetra = settings.fontPreset.familia
    val estiloLetra = if (settings.italic) FontStyle.Italic else FontStyle.Normal
    val tamanoLetra = settings.fontSizeSp.sp
    val tamanoTraduccion = (settings.fontSizeSp * 0.78f)
        .coerceAtLeast(11f)
        .sp
    // El lienzo del karaoke debe partir del mismo estilo que el Text normal
    // para que el ancho, el interletrado y la altura de línea coincidan.
    val estiloKaraoke = LocalTextStyle.current.merge(
        TextStyle(
            fontWeight = FontWeight.Black,
            fontFamily = familiaLetra,
            fontSize = tamanoLetra,
            fontStyle = estiloLetra,
            textAlign = TextAlign.Center
        )
    )
    val soporteTraduccion = lyricsDocument?.translationSupport
        ?: LyricsTranslationSupport.UNAVAILABLE_PATTERN
    val viewModel: MusicViewModel = viewModel()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    Box(
        Modifier.fillMaxSize()
    ) {
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = "Fondo de la pantalla de letras.",
            modifier = Modifier
                .fillMaxHeight()
                .blur(50.dp),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.82f))
        )

        var lineaSeleccionada by remember { mutableStateOf(-1) }
        val posicionActual = posicionKaraoke()

        LaunchedEffect(song.id) {
            lineaSeleccionada = -1
            listState.scrollToItem(0)
        }

        // Solo cambia cuando se cruza otra línea, no en cada muestra de posición.
        val lineaPorTiempo by remember(listaSincronizada) {
            derivedStateOf {
                listaSincronizada.indexOfLast { it.tiempo <= posicionActual.value }
            }
        }
        LaunchedEffect(lineaPorTiempo) {
            lineaSeleccionada = lineaPorTiempo
        }

        val coroutineScope = rememberCoroutineScope()
        LaunchedEffect(lineaSeleccionada, settings.showTranslation) {
            if (lineaSeleccionada >= 0) {
                try {
                    // Obtenemos la información visual actual de los elementos en pantalla
                    val layoutInfo = listState.layoutInfo
                    val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == lineaSeleccionada }

                    if (itemInfo != null) {
                        // Calculamos cuántos píxeles nos faltan para centrar exactamente el elemento
                        val offsetCenter = ((layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2)-500
                        val itemCenter = itemInfo.offset + (itemInfo.size / 2)
                        val delta = itemCenter - offsetCenter

                        // Desplazamos la lista exactamente esos píxeles de forma animada y suave
                        listState.animateScrollBy(
                            value = delta.toFloat(),
                            animationSpec = tween(
                                durationMillis = 500, // Aumenta o baja esto para que sea más lento o rápido
                                easing = androidx.compose.animation.core.FastOutSlowInEasing // Curva ultra suave
                            )
                        )
                    } else {
                        // Si el elemento no está visible todavía en pantalla, usamos el scroll normal
                        val indiceObjetivo = (lineaSeleccionada - 2).coerceAtLeast(0)
                        listState.animateScrollToItem(index = indiceObjetivo)
                    }
                } catch (e: Exception) {
                    // Fallback por seguridad si la lista aún no se ha dibujado
                    val indiceObjetivo = (lineaSeleccionada - 2).coerceAtLeast(0)
                    listState.animateScrollToItem(index = indiceObjetivo)
                }
            }
        }

        when {
            lyricsLoadState == LyricsLoadState.LOADING ||
                lyricsLoadState == LyricsLoadState.IDLE -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            lyricsLoadState == LyricsLoadState.ERROR -> {
                Text(
                    text = "No se pudieron leer las letras",
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            listaSincronizada.isNotEmpty() -> {
                LazyColumn(
                    state = listState, // 3. esto es para controlar el auto scroll
                    modifier = Modifier.padding(top = 38.dp, start = 40.dp, end = 40.dp),
                    contentPadding = PaddingValues(bottom = 600.dp)
                ) {
                    itemsIndexed(listaSincronizada) { indice, linealetra ->
                        val esLaSeleccionada = (indice == lineaSeleccionada)

                        val scaleAnimado by animateFloatAsState(
                            targetValue = if (esLaSeleccionada) 1.12f else 1.0f,
                            animationSpec = tween(durationMillis = 200),
                            label = "ScaleAnimation"
                        )

                        val colorAnimado by animateColorAsState(
                            targetValue = if (esLaSeleccionada) colorActivo
                            else colorActivo.copy(alpha = 0.4f),
                            animationSpec = tween(durationMillis = 200),
                            label = "ColorAnimation"
                        )

                        val traduccion = linealetra.traduccion
                            ?.takeIf { settings.showTranslation }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth()
                                .graphicsLayer(
                                    scaleX = scaleAnimado,
                                    scaleY = scaleAnimado
                                )
                                .clickable {
                                    onSeek(linealetra.tiempo)
                                    lineaSeleccionada = indice
                                }
                        ) {
                            if (
                                esLaSeleccionada &&
                                linealetra.segmentos.isNotEmpty()
                            ) {
                                KaraokeSweepText(
                                    linea = linealetra,
                                    siguienteLineaMs = listaSincronizada
                                        .getOrNull(indice + 1)?.tiempo,
                                    posicionMs = posicionActual,
                                    estilo = estiloKaraoke,
                                    colorCantado = colorActivo,
                                    colorPendiente = colorPendiente,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = AnnotatedString(linealetra.texto),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = familiaLetra,
                                    fontSize = tamanoLetra,
                                    fontStyle = estiloLetra,
                                    color = colorAnimado,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (traduccion != null) {
                                Text(
                                    text = traduccion,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = familiaLetra,
                                    fontSize = tamanoTraduccion,
                                    fontStyle = estiloLetra,
                                    color = colorAnimado.copy(
                                        alpha = colorAnimado.alpha * 0.8f
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                Text(
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = 28.sp,
                    text = "Sin Letra",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Los controles van al final para quedar por encima de la lista y que
        // sus toques no los robe la letra.
        IconButton(onClick = onClose) {
            JexSymbolIcon(
                symbol = JexSymbol.ArrowBack,
                contentDescription = "Cerrar letras"
            )
        }
        IconButton(
            onClick = { mostrarOpciones = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp)
        ) {
            JexSymbolIcon(
                symbol = JexSymbol.Settings,
                contentDescription = "Opciones de letra"
            )
        }

        MiniPlayer(
            song = song,
            isPlaying = isPlaying,
            isShuffleEnabled = isShuffleEnabled,
            repeatMode = repeatMode,
            onPrevious = { viewModel.previousSong() },
            onPlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.nextSong() },
            onShuffle = { viewModel.toggleShuffle() },
            onRepeat = { viewModel.toggleRepeat() },
            onOpenPlayer = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        )

        if (mostrarOpciones) {
            LyricsOptionsSheet(
                sources = lyricsSources,
                selectedSourceId = selectedLyricsSourceId,
                settings = settings,
                translationSupport = soporteTraduccion,
                onSourceSelected = onSelectLyricsSource,
                onSettingsChange = onLyricsSettingsChange,
                onDismiss = { mostrarOpciones = false }
            )
        }
    }
}

/**
 * Reloj suave del karaoke.
 *
 * Lee la posición real del reproductor cada [INTERVALO_LECTURA_POSICION_MS] y
 * avanza con [withFrameNanos] entre lecturas, para que el barrido corra a la
 * frecuencia de la pantalla sin consultar el controlador en cada frame.
 *
 * Un seek o un salto grande se reancla de inmediato; una diferencia pequeña se
 * corrige poco a poco para no hacer saltar la letra. En pausa no se avanza y la
 * posición vuelve a la del reproductor.
 */
@Composable
private fun posicionKaraoke(): State<Long> {
    val viewModel: MusicViewModel = viewModel()
    val posicion = remember {
        mutableLongStateOf(viewModel.currentPlaybackPositionMs())
    }

    LaunchedEffect(viewModel) {
        var anclaMs = viewModel.currentPlaybackPositionMs()
        var anclaFrameMs = 0L
        var ultimoFrameMs = 0L
        var ultimaLecturaMs = 0L

        posicion.longValue = anclaMs

        while (true) {
            if (!viewModel.isPlaying.value) {
                val real = viewModel.currentPlaybackPositionMs()
                anclaMs = real
                anclaFrameMs = 0L
                ultimoFrameMs = 0L
                ultimaLecturaMs = 0L
                if (posicion.longValue != real) posicion.longValue = real
                delay(INTERVALO_LECTURA_POSICION_MS)
                continue
            }

            val frameNs = withFrameNanos { it }
            val frameMs = frameNs / 1_000_000L

            // Tras volver del segundo plano o de un parón largo, los frames
            // llegan con un salto enorme: conviene reanclar antes de pintar.
            val saltoDeFrame = ultimoFrameMs != 0L &&
                frameMs - ultimoFrameMs > SALTO_DE_FRAME_MS
            ultimoFrameMs = frameMs

            if (anclaFrameMs == 0L || saltoDeFrame) {
                anclaMs = viewModel.currentPlaybackPositionMs()
                anclaFrameMs = frameMs
                ultimaLecturaMs = frameMs
            } else if (
                frameMs - ultimaLecturaMs >= INTERVALO_LECTURA_POSICION_MS
            ) {
                val real = viewModel.currentPlaybackPositionMs()
                val pronosticada = anclaMs + (frameMs - anclaFrameMs)
                val deriva = real - pronosticada

                if (abs(deriva) >= REANCLAJE_MS) {
                    anclaMs = real
                } else {
                    anclaMs = pronosticada +
                        (deriva * CORRECCION_DERIVA).toLong()
                }
                anclaFrameMs = frameMs
                ultimaLecturaMs = frameMs
            }

            val calculada = (anclaMs + (frameMs - anclaFrameMs))
                .coerceAtLeast(0L)
            if (posicion.longValue != calculada) {
                posicion.longValue = calculada
            }
        }
    }

    return posicion
}

private const val INTERVALO_LECTURA_POSICION_MS = 220L
private const val REANCLAJE_MS = 750L
private const val CORRECCION_DERIVA = 0.25f
private const val SALTO_DE_FRAME_MS = 1_000L
