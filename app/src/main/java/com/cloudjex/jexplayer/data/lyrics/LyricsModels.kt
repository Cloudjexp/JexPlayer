package com.cloudjex.jexplayer.data.lyrics

/** Fragmento de una línea con el instante en que debe empezar a resaltarse. */
data class SegmentoLetra(val tiempo: Long, val texto: String)

/**
 * Una línea de letra con su instante de inicio en milisegundos.
 *
 * [segmentos] contiene los tiempos por palabra o sílaba de las letras LRC/TTML
 * enriquecidas. Está vacío cuando la fuente solo tiene tiempos por línea.
 *
 * [finMs] es el final declarado por la fuente, si lo incluye.
 *
 * [traduccion] solo se rellena en letras LRC simples con el patrón bilingüe
 * original/traducción. En karaoke y formatos complejos queda vacío.
 */
data class LineaCancion(
    val tiempo: Long,
    val texto: String,
    val segmentos: List<SegmentoLetra> = emptyList(),
    val finMs: Long? = null,
    val traduccion: String? = null
)

/** Formatos de letra que admite la app. */
enum class LyricsFormat(
    val etiqueta: String,
    val extensiones: Set<String>,
    val prioridad: Int
) {
    EMBEDDED("Etiqueta", emptySet(), 0),
    TTML("TTML", setOf("ttml"), 1),
    LRC("LRC", setOf("lrc"), 2),
    VTT("WebVTT", setOf("vtt"), 3),
    SRT("SRT", setOf("srt"), 4);

    companion object {
        private val porExtension = entries
            .flatMap { formato -> formato.extensiones.map { it to formato } }
            .toMap()

        fun fromFileName(fileName: String): LyricsFormat? =
            porExtension[fileName.substringAfterLast('.', "").lowercase()]
    }
}

/** Archivo de letras encontrado durante el escaneo de una carpeta SAF. */
data class LyricsSidecar(
    val uri: String,
    val displayName: String,
    val parentDocumentId: String?,
    val format: LyricsFormat
)

/** Coincidencia entre una canción y un archivo de letras. */
data class LyricsMatch(
    val sidecar: LyricsSidecar,
    val language: String?,
    val score: Int
)

/** Fuente de letra disponible para la canción actual. */
data class LyricsSource(
    val id: String,
    val label: String,
    val format: LyricsFormat,
    val uri: String? = null,
    val isEmbedded: Boolean = false,
    val language: String? = null,
    val folderHint: String? = null
)

/** Motivo por el que la traducción integrada está o no disponible. */
enum class LyricsTranslationSupport {
    AVAILABLE,
    UNAVAILABLE_PATTERN,
    UNAVAILABLE_KARAOKE
}

/** Letra ya leída y parseada, lista para la pantalla. */
data class LyricsDocument(
    val source: LyricsSource,
    val lineas: List<LineaCancion>,
    val tieneTiemposPorPalabra: Boolean,
    val translationSupport: LyricsTranslationSupport
)

enum class LyricsLoadState {
    IDLE,
    LOADING,
    LOADED,
    ERROR
}

const val EMBEDDED_LYRICS_SOURCE_ID = "embedded"
