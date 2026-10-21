package com.cloudjex.jexplayer.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.cloudjex.jexplayer.data.model.AudioTags
import com.cloudjex.jexplayer.data.model.Song
import com.cloudjex.jexplayer.ui.icons.JexSymbol
import com.cloudjex.jexplayer.ui.icons.JexSymbolIcon
import com.cloudjex.jexplayer.viewmodel.MusicViewModel
import com.cloudjex.jexplayer.viewmodel.AudioTagsLoadState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.launch

// Esta es la letra de "In da club" Solo es para hacer pruebas.
val letramusica: String = "[id: nqyu1jrt]\n" +
        "[ar: 50 Cent]\n" +
        "[al: Get Rich Or Die Tryin']\n" +
        "[ti: In Da Club]\n" +
        "[by: Wann Kennedy]\n" +
        "\n" +
        "[re: LRC Editor - Android app]\n" +
        "[ve: Version 3.2.6]\n" +
        "\n" +
        "[00:00.00] \n" +
        "[00:00.56]Go, go, go, go, go, go\n" +
        "[00:04.29]Go, shorty\n" +
        "[00:05.64]It's your birthday\n" +
        "[00:06.97]We gon' party like it's your birthday\n" +
        "[00:09.34]We gon' sip Bacardi like it's your birthday\n" +
        "[00:11.96]And you know we don't give a fuck it's not your birthday\n" +
        "[00:14.88]You can find me in the club, bottle full of bub'\n" +
        "[00:16.96]Look, mami, I got the X, if you into takin' drugs\n" +
        "[00:19.73]I'm into havin' sex, I ain't into makin' love\n" +
        "[00:22.79]So come give me a hug, if you into getting rubbed\n" +
        "[00:24.94]You can find me in the club, bottle full of bub'\n" +
        "[00:27.76]Look, mami, I got the X, if you into takin' drugs\n" +
        "[00:30.65]I'm into havin' sex, I ain't into makin' love\n" +
        "[00:33.25]So come give me a hug, if you into getting rubbed\n" +
        "[00:35.62]When I pull out up front, you see the Benz on dubs\n" +
        "[00:38.25]When I roll 20 deep, it's 20 knives in the club\n" +
        "[00:40.97]Niggas heard I fuck with Dre, now they wanna show me love\n" +
        "[00:43.95]When you sell like Eminem, and the hoes they wanna fuck\n" +
        "[00:46.30]But, homie, ain't nothing change hoes down, G's up\n" +
        "[00:48.95]I see Xzibit in the cut, that nigga roll that weed up\n" +
        "[00:52.02]If you watch how I move, you'll mistake me for a playa or pimp\n" +
        "[00:55.34]Been hit wit' a few shells, but I don't walk wit' a limp (I'm ight)\n" +
        "[00:58.09]In the hood, in L.A., they saying \"50 you hot\"\n" +
        "[01:00.39]They like me, I want them to love me like they love 'Pac\n" +
        "[01:03.16]But holla, in New York them niggas'll tell ya I'm loco\n" +
        "[01:06.08]And the plan is to put the rap game in a chokehold\n" +
        "[01:08.36]I'm full of focused man, my money on my mind\n" +
        "[01:11.66]I got a mill' out the deal and I'm still on the grind\n" +
        "[01:13.41]Now shorty said she feeling my style, she feeling my flow\n" +
        "[01:16.33]Her girlfriend wanna get bi and they ready to go\n" +
        "[01:19.10]You can find me in the club, bottle full of bub'\n" +
        "[01:21.05]Look, mami, I got the X, if you into takin' drugs\n" +
        "[01:23.82]I'm into havin' sex, I ain't into makin' love\n" +
        "[01:26.56]So come give me a hug, if you into getting rubbed\n" +
        "[01:29.28]You can find me in the club, bottle full of bub'\n" +
        "[01:31.77]Look, mami, I got the X, if you into takin' drugs\n" +
        "[01:34.52]I'm into havin' sex I, ain't into makin' love\n" +
        "[01:36.75]So come give me a hug, if you into getting rubbed\n" +
        "[01:40.21]My flow, my show brought me the dough\n" +
        "[01:43.91]That bought me all my fancy things\n" +
        "[01:46.50]My crib, my cars, my clothes, my jewels\n" +
        "[01:49.36]Look, nigga, I done came up and I ain't change\n" +
        "[01:51.79]And you should love it, way more then you hate it\n" +
        "[01:53.93]Nigga, you mad? I thought that you'd be happy I made it\n" +
        "[01:56.25]I'm that cat by the bar toasting to the good life\n" +
        "[01:59.28]You that faggot ass nigga trying to pull me back, right?\n" +
        "[02:01.61]When my jaws get to bumpin' in the club it's on\n" +
        "[02:04.46]I wink my eye at you, bitch, if she smiles she gone\n" +
        "[02:07.29]If the roof on fire, let the motherfucker burn\n" +
        "[02:09.74]If the talk ain't 'bout money, homie, I ain't concerned\n" +
        "[02:12.35]I'm a tell you what Banks told me, \"Cuz, go 'head switch the style up\n" +
        "[02:15.76]If the niggas hate then let 'em hate and watch the money pile up\"\n" +
        "[02:18.38]Or we can go upside your head wit' a bottle of bub'\n" +
        "[02:21.15]They know where we fuckin' be\n" +
        "[02:22.62]You can find me in the club, bottle full of bub'\n" +
        "[02:25.23]Look, mami, I got the X, if you into takin' drugs\n" +
        "[02:27.82]I'm into havin' sex, I ain't into makin' love\n" +
        "[02:30.56]So come give me a hug, if you into getting rubbed\n" +
        "[02:33.34]You can find me in the club, bottle full of bub'\n" +
        "[02:36.18]Look, mami, I got the X, if you into takin' drugs\n" +
        "[02:38.76]I'm into havin' sex, I ain't into makin' love\n" +
        "[02:41.59]So come give me a hug, if you into getting rubbed\n" +
        "[02:46.02]Don't try to act like you don't know where we be neither, nigga\n" +
        "[02:49.38]We in the club all the time, nigga, so pop, pop off, nigga\n" +
        "[02:52.80]G-Unit\n" +
        "[02:53.58]"

@Composable
fun LyricsScreen(
    onSeek: (Long) -> Unit,
    song: Song,
    tags: AudioTags?,
    tagsLoadState: AudioTagsLoadState,
    onClose: () -> Unit
) {
    val letras = tags?.letras.orEmpty()
    val listaSincronizada = remember(letras) { obtenerListaSincronizada(letras) }

    // 1. Estado para controlar el scroll del LazyColumn
    val listState = rememberLazyListState()

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
        ) {
            IconButton(onClick = onClose) {
                JexSymbolIcon(
                    symbol = JexSymbol.ArrowBack,
                    contentDescription = "Cerrar letras"
                )
            }
        }

        var lineaSeleccionada by remember { mutableStateOf(-1) }

        // Calculamos la línea activa cuando cambia el tiempo de la canción
        val tiempoActualMs = tiempoActual()
        LaunchedEffect(tiempoActualMs) {
            val nuevoIndice = listaSincronizada.indexOfLast { it.tiempo <= tiempoActualMs }
            if (nuevoIndice != -1 && nuevoIndice != lineaSeleccionada) {
                lineaSeleccionada = nuevoIndice
            }
        }

        val coroutineScope = rememberCoroutineScope()
        LaunchedEffect(lineaSeleccionada) {
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
            tagsLoadState == AudioTagsLoadState.LOADING ||
                tagsLoadState == AudioTagsLoadState.IDLE -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            tagsLoadState == AudioTagsLoadState.ERROR -> {
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
                        targetValue = if (esLaSeleccionada) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                        animationSpec = tween(durationMillis = 300),
                        label = "ColorAnimation"
                    )

                    Text(
                        text = linealetra.texto,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 18.sp,
                        fontStyle = FontStyle.Italic,
                        color = colorAnimado,
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
                    )
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
    }
}

@Composable
fun tiempoActual(): Long{
    val viewModel: MusicViewModel = viewModel()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    return currentPosition
}

data class LineaCancion(val tiempo: Long, val texto: String)

// 2. Función que procesa todo el texto y devuelve una lista completa
fun obtenerListaSincronizada(letramusica: String): List<LineaCancion> {
    val letraCompleta = letramusica.trimIndent()
    val regexLrc = Regex("""\[(\d{2}:\d{2}\.\d{2})\]\s*(.*)""")

    val listaLineas = mutableListOf<LineaCancion>()

    letraCompleta.lines().forEach { linea ->
        val resultado = regexLrc.find(linea)
        if (resultado != null) {
            val tiempo = tiempoAMilisegundos(resultado.groupValues[1])
            val texto = resultado.groupValues[2]
            // Agregamos a la lista cada par de tiempo-texto
            listaLineas.add(LineaCancion(tiempo, texto))
        }
    }

    return listaLineas
}

fun tiempoAMilisegundos(tiempoStr: String): Long {
    // tiempoStr llega como "00:05.13"
    val partes = tiempoStr.split(":", ".")
    if (partes.size != 3) return 0L

    val minutos = partes[0].toLong()
    val segundos = partes[1].toLong()
    // Nota: El formato .13 representa centisegundos (centésimas de segundo), por lo que se multiplica por 10
    val centisegundos = partes[2].toLong()

    val minEnMs = minutos * 60 * 1000
    val segEnMs = segundos * 1000
    val centEnMs = centisegundos * 10

    return minEnMs + segEnMs + centEnMs
}
