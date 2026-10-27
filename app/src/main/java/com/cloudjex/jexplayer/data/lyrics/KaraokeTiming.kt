package com.cloudjex.jexplayer.data.lyrics

/**
 * Tramo de tiempo ya resuelto para pintar un segmento del karaoke.
 *
 * [inicioMs] y [finMs] están en milisegundos de reproducción. [finMs] puede
 * coincidir con [inicioMs] cuando la fuente trae marcas repetidas; en ese caso
 * el segmento se considera completado en cuanto llega su instante.
 */
data class VentanaKaraoke(
    val inicioMs: Long,
    val finMs: Long
) {
    val duracionMs: Long
        get() = (finMs - inicioMs).coerceAtLeast(0L)

    /** Progreso del segmento entre 0 y 1. */
    fun progreso(posicionMs: Long): Float {
        if (finMs <= inicioMs) {
            return if (posicionMs >= inicioMs) 1f else 0f
        }
        return ((posicionMs - inicioMs).toFloat() / duracionMs.toFloat())
            .coerceIn(0f, 1f)
    }
}

/** Duración mínima para que un segmento estimado no se complete de golpe. */
private const val DURACION_MINIMA_MS = 140L

/** Tope de la estimación para no alargar una palabra durante un interludio. */
private const val DURACION_MAXIMA_ESTIMADA_MS = 2_000L

/** Ritmo inicial cuando todavía no hay segmentos previos que medir. */
private const val RITMO_POR_DEFECTO_MS = 55f

private const val RITMO_MINIMO_MS = 25f
private const val RITMO_MAXIMO_MS = 120f

/**
 * Si la siguiente línea empieza más lejos que esto, se considera un silencio
 * o interludio y el último segmento se estima con el ritmo de la propia línea.
 */
private const val MAXIMO_ULTIMO_SEGMENTO_MS = 5_000L

/**
 * Resuelve el inicio y el fin de cada segmento de una línea.
 *
 * El fin se toma, por este orden, del siguiente segmento, del `finMs`
 * declarado por la fuente, del inicio de la siguiente línea o de una
 * estimación basada en el ritmo de los segmentos anteriores.
 *
 * Los parsers mantienen los segmentos ordenados y con tiempos no decrecientes,
 * así que las duraciones resultantes nunca son negativas. Si la fuente repite
 * un tiempo, se busca la siguiente marca estrictamente mayor.
 */
fun ventanasKaraoke(
    linea: LineaCancion,
    siguienteLineaMs: Long? = null
): List<VentanaKaraoke> {
    if (linea.segmentos.isEmpty()) return emptyList()

    val finDeclarado = linea.finMs?.takeIf { it > 0L }
    val inicioSiguienteLinea = siguienteLineaMs?.takeIf { it > 0L }
    val ventanas = ArrayList<VentanaKaraoke>(linea.segmentos.size)

    var caracteresConocidos = 0
    var duracionConocidaMs = 0L

    linea.segmentos.forEachIndexed { indice, segmento ->
        val inicio = segmento.tiempo.coerceAtLeast(0L)
        val siguienteInicio = linea.segmentos
            .asSequence()
            .drop(indice + 1)
            .map { it.tiempo }
            .firstOrNull { it > inicio }

        val fin = when {
            siguienteInicio != null -> siguienteInicio
            finDeclarado != null && finDeclarado > inicio -> finDeclarado
            inicioSiguienteLinea != null &&
                inicioSiguienteLinea > inicio &&
                inicioSiguienteLinea - inicio <= MAXIMO_ULTIMO_SEGMENTO_MS ->
                inicioSiguienteLinea
            else -> null
        }

        val finResuelto = fin ?: (
            inicio + estimarDuracion(
                texto = segmento.texto,
                caracteresConocidos = caracteresConocidos,
                duracionConocidaMs = duracionConocidaMs
            )
        )
        ventanas += VentanaKaraoke(inicioMs = inicio, finMs = finResuelto)

        if (fin != null) {
            caracteresConocidos += segmento.texto.trim().length.coerceAtLeast(1)
            duracionConocidaMs += (finResuelto - inicio).coerceAtLeast(0L)
        }
    }

    return ventanas
}

private fun estimarDuracion(
    texto: String,
    caracteresConocidos: Int,
    duracionConocidaMs: Long
): Long {
    val caracteres = texto.trim().length.coerceAtLeast(1)
    val ritmo = if (caracteresConocidos > 0 && duracionConocidaMs > 0L) {
        (duracionConocidaMs.toFloat() / caracteresConocidos)
            .coerceIn(RITMO_MINIMO_MS, RITMO_MAXIMO_MS)
    } else {
        RITMO_POR_DEFECTO_MS
    }

    return (ritmo * caracteres)
        .toLong()
        .coerceIn(DURACION_MINIMA_MS, DURACION_MAXIMA_ESTIMADA_MS)
}
