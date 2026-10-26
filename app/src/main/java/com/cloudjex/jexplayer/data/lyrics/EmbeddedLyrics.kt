package com.cloudjex.jexplayer.data.lyrics

import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagTextField

/**
 * Extrae las letras incrustadas de una etiqueta de audio.
 *
 * `FieldKey.LYRICS` es la clave genérica de jAudioTagger y se traduce al campo
 * nativo de cada formato: `USLT` en MP3, `©lyr` en M4A y `LYRICS` en FLAC/OGG.
 * Las claves literales se mantienen como respaldo para las variantes que
 * escriben algunos editores de etiquetas, incluidos los átomos personalizados
 * de M4A.
 */
private val CLAVES_LETRAS = listOf(
    "LYRICS",
    "UNSYNCEDLYRICS",
    "LYRIC",
    "©lyr",
    "----:com.apple.iTunes:LYRICS",
    "----:com.apple.iTunes:UNSYNCEDLYRICS",
    "----:com.apple.iTunes:LYRIC"
)

internal fun buscarLetrasEnTag(tag: Tag): String? {
    val candidatos = linkedSetOf<String>()

    fun agregar(texto: String?) {
        val limpio = texto?.trim().orEmpty()
        if (limpio.isNotBlank()) candidatos += limpio
    }

    runCatching { tag.getFirst(FieldKey.LYRICS) }
        .getOrNull()
        .let(::agregar)
    runCatching { tag.getFields(FieldKey.LYRICS) }
        .getOrNull()
        ?.forEach { campo -> agregar((campo as? TagTextField)?.content) }

    CLAVES_LETRAS.forEach { clave ->
        runCatching { tag.getFirst(clave) }
            .getOrNull()
            .let(::agregar)
        runCatching { tag.getFields(clave) }
            .getOrNull()
            ?.forEach { campo -> agregar((campo as? TagTextField)?.content) }
    }

    // Algunos M4A traen la letra completa en un átomo personalizado y una
    // versión corta en `©lyr`; nos quedamos con la más completa.
    return candidatos.maxByOrNull { it.length }
}
