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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.cloudjex.jexplayer.data.lyrics.LineaCancion
import com.cloudjex.jexplayer.data.lyrics.LyricsDocument
import com.cloudjex.jexplayer.data.lyrics.LyricsLoadState
import com.cloudjex.jexplayer.data.lyrics.LyricsSource
import com.cloudjex.jexplayer.data.lyrics.LyricsTranslationSupport
import com.cloudjex.jexplayer.data.model.LyricsSettings
import com.cloudjex.jexplayer.data.model.Song
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
    val soporteTraduccion = lyricsDocument?.translationSupport
        ?: LyricsTranslationSupport.UNAVAILABLE_PATTERN

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
                    modifier = Modifier.padding(top = 38.dp, start = 40.dp, end = 40.dp)
                ) {
                    itemsIndexed(listaSincronizada) { indice, linealetra ->
                        val esLaSeleccionada = (indice == lineaSeleccionada)

                        val scaleAnimado by animateFloatAsState(
                            targetValue = if (esLaSeleccionada) 1.12f else 1.0f,
                            animationSpec = tween(durationMillis = 300),
                            label = "ScaleAnimation"
                        )

                        val colorAnimado by animateColorAsState(
                            targetValue = if (esLaSeleccionada) colorActivo
                            else colorActivo.copy(alpha = 0.4f),
                            animationSpec = tween(durationMillis = 300),
                            label = "ColorAnimation"
                        )

                        val finLineaMs = linealetra.finMs
                            ?: listaSincronizada.getOrNull(indice + 1)?.tiempo
                            ?: (linealetra.tiempo + 8_000L)
                        val texto = if (
                            esLaSeleccionada && linealetra.segmentos.isNotEmpty()
                        ) {
                            textoKaraoke(
                                linea = linealetra,
                                posicionMs = posicionActual.value,
                                finLineaMs = finLineaMs,
                                colorCantado = colorActivo,
                                colorPendiente = colorPendiente
                            )
                        } else {
                            AnnotatedString(linealetra.texto)
                        }

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
                            Text(
                                text = texto,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Black,
                                fontFamily = familiaLetra,
                                fontSize = tamanoLetra,
                                fontStyle = estiloLetra,
                                color = colorAnimado,
                                modifier = Modifier.fillMaxWidth()
                            )
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

@Composable
private fun posicionKaraoke(): State<Long> {
    val viewModel: MusicViewModel = viewModel()
    return produceState(initialValue = viewModel.currentPlaybackPositionMs()) {
        var ultimaPosicion = value
        while (true) {
            val posicion = viewModel.currentPlaybackPositionMs()
            val retroceso = ultimaPosicion - posicion
            if (
                posicion >= ultimaPosicion ||
                !viewModel.isPlaying.value ||
                retroceso > RETROCESO_TOLERADO_MS
            ) {
                ultimaPosicion = posicion
            }
            value = ultimaPosicion
            delay(50)
        }
    }
}

private const val RETROCESO_TOLERADO_MS = 300L

/**
 * Construye la línea activa con un degradado que avanza con los tiempos de
 * cada palabra o sílaba. Las palabras ya cantadas usan el color activo y las
 * pendientes el color de espera de la paleta elegida.
 */
private fun textoKaraoke(
    linea: LineaCancion,
    posicionMs: Long,
    finLineaMs: Long,
    colorCantado: Color,
    colorPendiente: Color
): AnnotatedString = buildAnnotatedString {
    linea.segmentos.forEachIndexed { indice, segmento ->
        val finSegmento = linea.segmentos.getOrNull(indice + 1)?.tiempo ?: finLineaMs
        val transicionMs = (finSegmento - segmento.tiempo).coerceIn(1L, 500L)
        val transcurrido = posicionMs - segmento.tiempo

        val color = when {
            transcurrido < 0L -> colorPendiente
            transcurrido >= transicionMs -> colorCantado
            else -> lerp(
                colorPendiente,
                colorCantado,
                (transcurrido.toFloat() / transicionMs).coerceIn(0f, 1f)
            )
        }

        withStyle(SpanStyle(color = color)) {
            append(segmento.texto)
        }
    }
}

// Los parsers viven en data/lyrics para poder probarlos como lógica pura.
