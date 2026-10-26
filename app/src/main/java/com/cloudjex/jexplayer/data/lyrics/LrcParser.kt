package com.cloudjex.jexplayer.data.lyrics

private const val PATRON_TIEMPO = """(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?"""

/**
 * Marca de tiempo LRC: `[mm:ss]`, `[mm:ss.x]`, `[mm:ss.xx]` o `[mm:ss.xxx]`.
 * También se admite `:` como separador de la fracción.
 */
private val MARCA_TIEMPO = Regex("""\[$PATRON_TIEMPO]""")

/** Marca de tiempo por palabra o sílaba: `<mm:ss.xxx>`. */
private val MARCA_PALABRA = Regex("""<$PATRON_TIEMPO>""")

/** Marca de tiempo sin corchetes, para convertir cadenas sueltas. */
private val TIEMPO_SUELTO = Regex(PATRON_TIEMPO)

/**
 * Marcas de tiempo consecutivas al inicio de una línea. Algunos archivos LRC
 * incluyen varias marcas para reutilizar el mismo texto en distintos momentos.
 */
private val PREFIJO_MARCAS =
    Regex("""^\s*(?:\[$PATRON_TIEMPO]\s*)+""")

/** Prefijos de versión como `v1:` que algunos archivos añaden al texto. */
private val PREFIJO_VERSION = Regex("""^v\d+:\s*""", RegexOption.IGNORE_CASE)

/**
 * Algunas etiquetas M4A guardan todo el LRC en una sola línea y separan las
 * marcas de tiempo con espacios. Insertamos un salto antes de cada marca para
 * que el parser pueda tratar cada línea por separado.
 */
private val ESPACIO_ANTES_DE_MARCA = Regex("""[ \t]+(?=\[$PATRON_TIEMPO])""")

internal fun normalizarLrc(letra: String): String =
    letra.replace(ESPACIO_ANTES_DE_MARCA, "\n")

/**
 * Convierte una letra LRC en líneas sincronizadas.
 *
 * - Acepta fracciones de uno, dos o tres dígitos (`[00:08.9]`, `[00:08.98]`,
 *   `[00:08.987]`).
 * - Lee los tiempos por palabra/sílaba de las letras enriquecidas
 *   (`[00:16.296]<00:16.296>I <00:16.885>don't`).
 * - Ignora las etiquetas de metadatos que no son tiempos (`[ti:...]`,
 *   `[ar:...]`, etc.).
 * - Descarta prefijos de versión como `v1:` al inicio del texto.
 * - Si una línea trae varias marcas de tiempo, genera una entrada por cada una.
 * - Acepta LRC que llegó en una sola línea con las marcas separadas por espacios.
 */
fun obtenerListaSincronizada(letra: String): List<LineaCancion> {
    val lista = mutableListOf<LineaCancion>()

    normalizarLrc(letra).lineSequence().forEach { linea ->
        val prefijo = PREFIJO_MARCAS.find(linea) ?: return@forEach
        val tiemposLinea = MARCA_TIEMPO.findAll(prefijo.value)
            .map { it.tiempoDeMarca() }
            .toList()
        if (tiemposLinea.isEmpty()) return@forEach

        val contenido = linea.substring(prefijo.range.last + 1)
            .trimStart()
            .replace(PREFIJO_VERSION, "")
            .trimStart()

        val procesada = procesarContenido(contenido, tiemposLinea.first())
        tiemposLinea.forEach { tiempo ->
            lista += LineaCancion(
                tiempo = tiempo,
                texto = procesada.texto,
                segmentos = procesada.segmentos,
                finMs = procesada.finMs
            )
        }
    }

    return lista
}

/**
 * Convierte una marca de tiempo como `00:08.987` a milisegundos.
 *
 * La fracción se interpreta según su longitud: `.9` son décimas, `.98`
 * centésimas y `.987` milésimas. Devuelve `0` si el texto no es válido.
 */
fun tiempoAMilisegundos(tiempo: String): Long {
    val marca = TIEMPO_SUELTO.matchEntire(tiempo.trim()) ?: return 0L
    return marca.tiempoDeMarca()
}

private data class ContenidoProcesado(
    val texto: String,
    val segmentos: List<SegmentoLetra>,
    val finMs: Long?
)

private fun procesarContenido(contenido: String, inicioLinea: Long): ContenidoProcesado {
    val marcasPalabra = MARCA_PALABRA.findAll(contenido).toList()
    if (marcasPalabra.isEmpty()) {
        return ContenidoProcesado(
            texto = contenido.trim(),
            segmentos = emptyList(),
            finMs = null
        )
    }

    val segmentos = mutableListOf<SegmentoLetra>()
    var pendiente = inicioLinea
    var ultimaMarca = inicioLinea
    var cursor = 0

    marcasPalabra.forEach { marca ->
        val texto = contenido.substring(cursor, marca.range.first)
        if (texto.isNotBlank()) {
            val tiempo = maxOf(pendiente, segmentos.lastOrNull()?.tiempo ?: inicioLinea)
            segmentos += SegmentoLetra(tiempo = tiempo, texto = texto)
        }

        ultimaMarca = maxOf(ultimaMarca, marca.tiempoDeMarca())
        pendiente = ultimaMarca
        cursor = marca.range.last + 1
    }

    val resto = contenido.substring(cursor)
    if (resto.isNotBlank()) {
        val tiempo = maxOf(pendiente, segmentos.lastOrNull()?.tiempo ?: inicioLinea)
        segmentos += SegmentoLetra(tiempo = tiempo, texto = resto)
    }

    val limpios = recortarExtremos(segmentos)
    val ultimoSegmento = limpios.lastOrNull()?.tiempo ?: inicioLinea
    val finMs = ultimaMarca.takeIf { resto.isBlank() && it > ultimoSegmento }

    return ContenidoProcesado(
        texto = limpios.joinToString("") { it.texto },
        segmentos = limpios,
        finMs = finMs
    )
}

/** Quita solo los espacios de los extremos de la línea, sin romper las palabras. */
private fun recortarExtremos(segmentos: List<SegmentoLetra>): List<SegmentoLetra> {
    if (segmentos.isEmpty()) return emptyList()

    val copia = segmentos.toMutableList()
    copia[0] = copia[0].copy(texto = copia[0].texto.trimStart())
    val ultimo = copia.lastIndex
    copia[ultimo] = copia[ultimo].copy(texto = copia[ultimo].texto.trimEnd())
    return copia.filter { it.texto.isNotEmpty() }
}

private fun MatchResult.tiempoDeMarca(): Long = tiempoAMilisegundos(
    minutos = groupValues[1],
    segundos = groupValues[2],
    fraccion = groupValues[3]
)

private fun tiempoAMilisegundos(
    minutos: String,
    segundos: String,
    fraccion: String
): Long {
    val minutosMs = (minutos.toLongOrNull() ?: return 0L) * 60_000L
    val segundosMs = (segundos.toLongOrNull() ?: return 0L) * 1_000L
    return minutosMs + segundosMs + fraccionAMilisegundos(fraccion)
}

private fun fraccionAMilisegundos(fraccion: String): Long {
    if (fraccion.isEmpty()) return 0L

    val valor = fraccion.toLongOrNull() ?: return 0L
    return when (fraccion.length) {
        1 -> valor * 100L
        2 -> valor * 10L
        else -> valor
    }
}
