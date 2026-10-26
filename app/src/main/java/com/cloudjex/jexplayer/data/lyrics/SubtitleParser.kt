package com.cloudjex.jexplayer.data.lyrics

/**
 * Parser de subtítulos SRT y WebVTT.
 *
 * Estas fuentes solo traen tiempos por línea, así que se convierten en
 * [LineaCancion] sin segmentos por palabra.
 */
object SubtitleParser {

    private val CUE = Regex(
        """^\s*(\d{1,2}:\d{2}(?::\d{2})?[.,]\d{1,3})\s*-->\s*(\d{1,2}:\d{2}(?::\d{2})?[.,]\d{1,3})"""
    )

    fun parseSrt(text: String): List<LineaCancion> = parsearBloques(text, esVtt = false)

    fun parseVtt(text: String): List<LineaCancion> = parsearBloques(text, esVtt = true)

    private fun parsearBloques(text: String, esVtt: Boolean): List<LineaCancion> {
        val normalizado = text
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')

        return normalizado
            .split(Regex("""\n\s*\n"""))
            .mapNotNull { bloque -> parsearBloque(bloque, esVtt) }
            .sortedBy { it.tiempo }
    }

    private fun parsearBloque(bloque: String, esVtt: Boolean): LineaCancion? {
        val lineas = bloque.lines()
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
        if (lineas.isEmpty()) return null

        if (esVtt) {
            val primera = lineas.first().trimStart()
            if (primera.startsWith("WEBVTT", ignoreCase = true) ||
                primera.startsWith("NOTE", ignoreCase = true) ||
                primera.startsWith("STYLE", ignoreCase = true) ||
                primera.startsWith("REGION", ignoreCase = true)
            ) {
                return null
            }
        }

        val indiceCue = lineas.indexOfFirst { CUE.containsMatchIn(it) }
        if (indiceCue < 0) return null

        val marca = CUE.find(lineas[indiceCue]) ?: return null
        val inicio = parsearTiempoSubtitulo(marca.groupValues[1]) ?: return null
        val fin = parsearTiempoSubtitulo(marca.groupValues[2]) ?: return null

        val texto = lineas
            .drop(indiceCue + 1)
            .joinToString(" ") { limpiarTexto(it) }
            .trim()
        if (texto.isEmpty()) return null

        return LineaCancion(
            tiempo = inicio,
            texto = texto,
            finMs = fin.takeIf { it > inicio }
        )
    }

    private fun parsearTiempoSubtitulo(valor: String): Long? {
        val partes = valor.trim().replace(',', '.').split(':')
        return when (partes.size) {
            3 -> {
                val horas = partes[0].toLongOrNull() ?: return null
                val minutos = partes[1].toLongOrNull() ?: return null
                val segundos = partes[2].toDoubleOrNull() ?: return null
                (horas * 3_600L + minutos * 60L) * 1_000L +
                    (segundos * 1_000.0).toLong()
            }

            2 -> {
                val minutos = partes[0].toLongOrNull() ?: return null
                val segundos = partes[1].toDoubleOrNull() ?: return null
                minutos * 60_000L + (segundos * 1_000.0).toLong()
            }

            else -> null
        }
    }

    private fun limpiarTexto(texto: String): String =
        texto
            .replace(Regex("""<[^>]*>"""), "")
            .replace(Regex("""\{\\[^}]*}"""), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
}
