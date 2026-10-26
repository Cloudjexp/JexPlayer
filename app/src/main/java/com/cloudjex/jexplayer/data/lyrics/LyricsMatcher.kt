package com.cloudjex.jexplayer.data.lyrics

import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

/**
 * Empareja archivos de letras con la canción actual.
 *
 * Se compara el nombre del archivo de audio (no el título de la etiqueta) con
 * el nombre del sidecar. Los sufijos de idioma habituales (`Canción.es.lrc`)
 * cuentan como variantes de la misma canción. También se prefiere el archivo
 * que está en la misma carpeta y, entre formatos, TTML sobre LRC, VTT y SRT.
 */
object LyricsMatcher {

    private val SUFIJO_IDIOMA = Regex("""[a-z]{2,3}(?:[-_][a-z]{2,4})?""")

    fun match(songId: String, sidecars: List<LyricsSidecar>): List<LyricsMatch> {
        val talloCancion = nombreBaseCancion(songId) ?: return emptyList()
        val carpetaCancion = carpetaDeCancion(songId)
        val mejores = LinkedHashMap<String, LyricsMatch>()

        sidecars.forEach { sidecar ->
            val talloLetra = normalizar(
                sidecar.displayName.substringBeforeLast('.', sidecar.displayName)
            )
            if (talloLetra.isEmpty()) return@forEach

            val esExacto = talloLetra == talloCancion
            val idioma = if (esExacto) null else idiomaVariante(talloLetra, talloCancion)
            if (!esExacto && idioma == null) return@forEach

            val mismaCarpeta = carpetaCancion != null &&
                sidecar.parentDocumentId == carpetaCancion
            val puntuacion = (if (mismaCarpeta) 100 else 0) +
                (if (esExacto) 30 else 20) +
                (10 - sidecar.format.prioridad)

            val coincidencia = LyricsMatch(
                sidecar = sidecar,
                language = idioma,
                score = puntuacion
            )
            val anterior = mejores[sidecar.uri]
            if (anterior == null || coincidencia.score > anterior.score) {
                mejores[sidecar.uri] = coincidencia
            }
        }

        return mejores.values.sortedWith(
            compareByDescending<LyricsMatch> { it.score }
                .thenBy { it.sidecar.displayName.lowercase(Locale.ROOT) }
                .thenBy { it.sidecar.uri }
        )
    }

    /**
     * Nombre del archivo de audio sin extensión, tomado del id SAF.
     * Es público para poder probar la coincidencia sin depender de Android.
     */
    fun nombreBaseCancion(songId: String): String? {
        val documento = documentId(songId) ?: return null
        val archivo = if (documento.contains('/')) {
            documento.substringAfterLast('/')
        } else {
            // Solo se quita el prefijo del proveedor; un nombre de archivo
            // puede contener dos puntos sin que eso sea un prefijo.
            documento.substringAfter(':')
        }
        val tallo = archivo.substringBeforeLast('.', archivo)
        return normalizar(tallo).takeIf { it.isNotEmpty() }
    }

    fun carpetaDeCancion(songId: String): String? {
        val documento = documentId(songId) ?: return null
        val ultimaBarra = documento.lastIndexOf('/')
        if (ultimaBarra >= 0) {
            return documento.substring(0, ultimaBarra).takeIf { it.isNotBlank() }
        }

        // En proveedores cuyo id no usa rutas (`primary:Song.flac`) la carpeta
        // raíz suele ser el prefijo anterior a los dos puntos.
        return documento.substringBefore(':', "")
            .takeIf { it.isNotBlank() }
            ?.plus(":")
    }

    private fun idiomaVariante(talloLetra: String, talloCancion: String): String? {
        val prefijo = "$talloCancion."
        if (!talloLetra.startsWith(prefijo)) return null
        val sufijo = talloLetra.removePrefix(prefijo)
        return sufijo.takeIf { SUFIJO_IDIOMA.matches(it) }
    }

    private fun documentId(uri: String): String? {
        val sinConsulta = uri.substringBefore('?').substringBefore('#')
        val codificado = sinConsulta.substringAfterLast('/')
            .takeIf { it.isNotBlank() }
            ?: return null

        // URLDecoder convierte '+' en espacio; en una ruta eso sería incorrecto.
        val seguro = codificado.replace("+", "%2B")
        return runCatching {
            URLDecoder.decode(seguro, Charsets.UTF_8.name())
        }.getOrNull()
    }

    private fun normalizar(valor: String): String =
        Normalizer.normalize(valor, Normalizer.Form.NFC)
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
}
