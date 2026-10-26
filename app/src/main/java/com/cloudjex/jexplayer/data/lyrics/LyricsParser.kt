package com.cloudjex.jexplayer.data.lyrics

/**
 * Detección del formato y despacho al parser correspondiente.
 *
 * El contenido manda sobre la extensión: así un `.lrc` que en realidad contiene
 * TTML (o al revés) se sigue mostrando correctamente. Si el contenido no se
 * reconoce, se usa el formato esperado por la extensión.
 */
object LyricsParser {

    private val MARCA_LRC = Regex(
        """(?m)^\s*\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?]"""
    )
    private val ETIQUETA_TTML = Regex(
        """<\s*(?:[A-Za-z0-9_.-]+:)?tt(?:\s|>)""",
        RegexOption.IGNORE_CASE
    )
    private val TIEMPO_SUBTITULO = Regex(
        """(?m)^\s*\d{1,2}:\d{2}(?::\d{2})?[.,]\d{1,3}\s*-->\s*\d{1,2}:\d{2}(?::\d{2})?[.,]\d{1,3}"""
    )

    fun detectFormat(text: String): LyricsFormat? {
        val limpio = text.removePrefix("\uFEFF").trimStart()
        if (limpio.isEmpty()) return null

        if (limpio.startsWith("WEBVTT", ignoreCase = true)) {
            return LyricsFormat.VTT
        }
        if (ETIQUETA_TTML.containsMatchIn(limpio)) {
            return LyricsFormat.TTML
        }
        if (TIEMPO_SUBTITULO.containsMatchIn(limpio)) {
            return LyricsFormat.SRT
        }
        if (MARCA_LRC.containsMatchIn(normalizarLrc(limpio))) {
            return LyricsFormat.LRC
        }
        return null
    }

    fun parse(text: String, preferredFormat: LyricsFormat?): List<LineaCancion> {
        val limpio = text.removePrefix("\uFEFF")
        if (limpio.isBlank()) return emptyList()

        val formato = detectFormat(limpio) ?: preferredFormat ?: return emptyList()
        val lineas = when (formato) {
            LyricsFormat.TTML -> TtmlParser.parse(limpio)
            LyricsFormat.LRC -> obtenerListaSincronizada(limpio)
            LyricsFormat.SRT -> SubtitleParser.parseSrt(limpio)
            LyricsFormat.VTT -> SubtitleParser.parseVtt(limpio)
            LyricsFormat.EMBEDDED -> emptyList()
        }

        return lineas
            .filter { it.texto.isNotBlank() }
            .sortedBy { it.tiempo }
    }

    fun hasWordTiming(lineas: List<LineaCancion>): Boolean =
        lineas.any { it.segmentos.isNotEmpty() }
}
