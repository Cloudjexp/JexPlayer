package com.cloudjex.jexplayer.data.lyrics

/**
 * Agrupa el patrón bilingüe de las letras LRC simples: una línea original y
 * su traducción entre paréntesis justo debajo, con el mismo tiempo.
 *
 * Solo se admite LRC sin tiempos por palabra. Las letras enriquecidas
 * (karaoke) y los formatos TTML/SRT/VTT quedan fuera para no mezclar líneas
 * con tiempos distintos ni romper el resaltado por palabra.
 */
object BilingualLyrics {

    private const val MIN_PARES = 2
    private const val COBERTURA_MINIMA = 0.6f

    fun prepare(
        lineas: List<LineaCancion>,
        esLrcSimple: Boolean
    ): BilingualLyricsResult {
        if (lineas.isEmpty() || !esLrcSimple) {
            return BilingualLyricsResult(lineas, LyricsTranslationSupport.UNAVAILABLE_PATTERN)
        }
        if (lineas.any { it.segmentos.isNotEmpty() }) {
            return BilingualLyricsResult(lineas, LyricsTranslationSupport.UNAVAILABLE_KARAOKE)
        }

        val resultado = mutableListOf<LineaCancion>()
        var pares = 0

        agruparPorTiempo(lineas).forEach { grupo ->
            if (grupo.size == 2 && esOriginal(grupo[0]) && esTraduccion(grupo[1])) {
                resultado += grupo[0].copy(traduccion = grupo[1].texto.trim())
                pares++
            } else {
                resultado += grupo
            }
        }

        val cobertura = (pares * 2).toFloat() / lineas.size
        if (pares < MIN_PARES || cobertura < COBERTURA_MINIMA) {
            return BilingualLyricsResult(lineas, LyricsTranslationSupport.UNAVAILABLE_PATTERN)
        }

        return BilingualLyricsResult(resultado, LyricsTranslationSupport.AVAILABLE)
    }

    private fun agruparPorTiempo(lineas: List<LineaCancion>): List<List<LineaCancion>> {
        val grupos = mutableListOf<MutableList<LineaCancion>>()
        var indice = 0

        while (indice < lineas.size) {
            val tiempo = lineas[indice].tiempo
            val grupo = mutableListOf<LineaCancion>()
            while (indice < lineas.size && lineas[indice].tiempo == tiempo) {
                grupo += lineas[indice]
                indice++
            }
            grupos += grupo
        }

        return grupos
    }

    private fun esOriginal(linea: LineaCancion): Boolean = !esParentizada(linea.texto)

    private fun esTraduccion(linea: LineaCancion): Boolean = esParentizada(linea.texto)

    private fun esParentizada(texto: String): Boolean {
        val limpio = texto.trim()
        return limpio.startsWith("(") && limpio.endsWith(")")
    }
}

data class BilingualLyricsResult(
    val lineas: List<LineaCancion>,
    val support: LyricsTranslationSupport
)
