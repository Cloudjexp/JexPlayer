package com.cloudjex.jexplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.cloudjex.jexplayer.data.lyrics.LineaCancion
import com.cloudjex.jexplayer.data.lyrics.VentanaKaraoke
import com.cloudjex.jexplayer.data.lyrics.ventanasKaraoke

/** Fracción del ancho del segmento que se usa para suavizar el borde del barrido. */
private const val FRACCION_PLUMA = 0.3f
private const val PLUMA_MINIMA_PX = 4f
private const val PLUMA_MAXIMA_PX = 16f

/**
 * Pinta la línea activa del karaoke con un barrido real por palabra o sílaba.
 *
 * La posición se lee dentro de la fase de dibujo: mientras cambia el reloj, solo
 * se invalida el dibujo de este texto, no la fila completa ni la lista.
 */
@Composable
internal fun KaraokeSweepText(
    linea: LineaCancion,
    siguienteLineaMs: Long?,
    posicionMs: State<Long>,
    estilo: TextStyle,
    colorCantado: Color,
    colorPendiente: Color,
    modifier: Modifier = Modifier
) {
    val ventanas = remember(linea, siguienteLineaMs) {
        ventanasKaraoke(linea, siguienteLineaMs)
    }
    val texto = remember(linea.texto) { AnnotatedString(linea.texto) }
    val etiquetaSemantica = remember(linea.texto) { AnnotatedString(linea.texto) }
    val medidor = rememberTextMeasurer(cacheSize = 3)
    val cache = remember { CacheBarrido() }

    Layout(
        content = {},
        modifier = modifier
            .semantics { text = etiquetaSemantica }
            .drawBehind {
                val layout = cache.layout ?: return@drawBehind
                dibujarBarrido(
                    layout = layout,
                    cajas = cache.cajas,
                    ventanas = ventanas,
                    posicionMs = posicionMs.value,
                    colorCantado = colorCantado,
                    colorPendiente = colorPendiente
                )
            }
    ) { _, constraints ->
        val resultado = medidor.measure(
            text = texto,
            style = estilo,
            overflow = TextOverflow.Visible,
            softWrap = true,
            maxLines = Int.MAX_VALUE,
            constraints = constraints
        )
        cache.layout = resultado
        cache.cajas = cajasDeSegmentos(resultado, linea)
        layout(resultado.size.width, resultado.size.height) {}
    }
}

/** Layout y cajas por segmento reutilizados entre frames. */
private class CacheBarrido {
    var layout: TextLayoutResult? = null
    var cajas: List<List<Rect>> = emptyList()
}

/**
 * Obtiene las cajas de cada segmento agrupadas por línea visual. Un segmento
 * puede ocupar varias líneas si el texto se envuelve; las cajas se recorren en
 * orden de lectura para que el barrido siga el mismo orden que el texto.
 */
private fun cajasDeSegmentos(
    layout: TextLayoutResult,
    linea: LineaCancion
): List<List<Rect>> {
    var offset = 0
    return linea.segmentos.map { segmento ->
        val inicio = offset
        val fin = (offset + segmento.texto.length).coerceAtMost(linea.texto.length)
        offset = fin
        if (inicio >= fin) emptyList() else cajasDeRango(layout, inicio, fin)
    }
}

private fun cajasDeRango(
    layout: TextLayoutResult,
    inicio: Int,
    fin: Int
): List<Rect> {
    if (inicio >= fin) return emptyList()

    val primeraLinea = layout.getLineForOffset(inicio)
    val ultimaLinea = layout.getLineForOffset(fin - 1)
    val cajas = ArrayList<Rect>(ultimaLinea - primeraLinea + 1)

    for (linea in primeraLinea..ultimaLinea) {
        val desde = maxOf(inicio, layout.getLineStart(linea))
        val hasta = minOf(fin, layout.getLineEnd(linea))
        if (desde >= hasta) continue

        var union: Rect? = null
        var posicion = desde
        while (posicion < hasta) {
            val caja = layout.getBoundingBox(posicion)
            val actual = union
            union = if (actual == null) {
                caja
            } else {
                Rect(
                    left = minOf(actual.left, caja.left),
                    top = minOf(actual.top, caja.top),
                    right = maxOf(actual.right, caja.right),
                    bottom = maxOf(actual.bottom, caja.bottom)
                )
            }
            posicion++
        }

        union?.takeIf { it.width > 0f }?.let { cajas += it }
    }

    return cajas
}

/**
 * Pinta primero toda la línea pendiente y después el texto ya cantado,
 * recortado por las cajas de cada segmento. El segmento actual lleva un borde
 * suave para que el avance no se vea como un corte recto.
 */
private fun DrawScope.dibujarBarrido(
    layout: TextLayoutResult,
    cajas: List<List<Rect>>,
    ventanas: List<VentanaKaraoke>,
    posicionMs: Long,
    colorCantado: Color,
    colorPendiente: Color
) {
    drawText(layout, color = colorPendiente)

    val completadas = Path()
    val parciales = ArrayList<CajaParcial>(ventanas.size)

    ventanas.forEachIndexed { indice, ventana ->
        val progreso = ventana.progreso(posicionMs)
        if (progreso <= 0f) return@forEachIndexed

        val cajasSegmento = cajas.getOrNull(indice).orEmpty()
        if (cajasSegmento.isEmpty()) return@forEachIndexed

        val anchoTotal = cajasSegmento.fold(0f) { total, caja -> total + caja.width }
        if (anchoTotal <= 0f) return@forEachIndexed

        val pluma = (anchoTotal * FRACCION_PLUMA)
            .coerceIn(PLUMA_MINIMA_PX, PLUMA_MAXIMA_PX)
        var consumido = 0f

        cajasSegmento.forEach { caja ->
            if (caja.width <= 0f) return@forEach
            val anchoRevelado = anchoTotal * progreso - consumido
            when {
                anchoRevelado >= caja.width -> completadas.addRect(caja)
                anchoRevelado > -pluma -> {
                    parciales += CajaParcial(
                        caja = caja,
                        anchoRevelado = anchoRevelado,
                        pluma = pluma
                    )
                }
            }
            consumido += caja.width
        }
    }

    if (!completadas.isEmpty) {
        clipPath(completadas) {
            drawText(layout, color = colorCantado)
        }
    }

    parciales.forEach { parcial ->
        val caja = parcial.caja
        val limite = caja.left + parcial.anchoRevelado
        val finPluma = (limite + parcial.pluma).coerceAtMost(caja.right)
        if (finPluma <= caja.left) return@forEach

        val zona = Rect(caja.left, caja.top, finPluma, caja.bottom)
        if (limite >= finPluma) return@forEach

        val mascara = Brush.horizontalGradient(
            colors = listOf(Color.White, Color.Transparent),
            startX = limite,
            endX = finPluma
        )

        drawIntoCanvas { canvas ->
            canvas.save()
            canvas.clipRect(zona)
            canvas.saveLayer(zona, Paint())
            drawText(layout, color = colorCantado)
            drawRect(
                brush = mascara,
                topLeft = Offset(zona.left, zona.top),
                size = Size(zona.width, zona.height),
                blendMode = BlendMode.DstIn
            )
            canvas.restore()
            canvas.restore()
        }
    }
}

private data class CajaParcial(
    val caja: Rect,
    val anchoRevelado: Float,
    val pluma: Float
)
