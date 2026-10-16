package com.cloudjex.jexplayer.data.model

/**
 * Metadatos y etiquetas leídos de un archivo de audio.
 *
 * @param letras contenido del campo `LYRICS`, o `null` si el archivo no lo tiene.
 * @param sampleRate frecuencia de muestreo en Hz (por ejemplo 44100).
 * @param bitRate tasa de bits del stream de audio en bits por segundo.
 * @param canales número de canales de audio (1 mono, 2 estéreo).
 * @param duracionSegundos duración en segundos reportada por el propio archivo.
 * @param bitsPorMuestra bits por muestra (16, 24, 32). `0` si el formato no lo expone.
 * @param muestrasTotales total de muestras de audio. `0` si el formato no lo expone.
 * @param exacto `true` cuando los valores de audio vienen del bloque `STREAMINFO`
 *   de un FLAC (fuente de verdad) y no de una estimación de jAudioTagger.
 * @param todosLosCampos todas las etiquetas indexadas por nombre, para consultar
 *   cualquier campo existente (género, año, compositor, ISRC...) sin volver a
 *   tocar la librería.
 */
data class AudioTags(
    val letras: String?,
    val sampleRate: Int,
    val bitRate: Int,
    val canales: Int,
    val duracionSegundos: Int,
    val bitsPorMuestra: Int,
    val muestrasTotales: Long,
    val exacto: Boolean,
    val todosLosCampos: Map<String, String>
) {
    /** `true` cuando el archivo es estéreo. */
    val esEstereo: Boolean get() = canales >= 2

    /** `true` cuando la frecuencia de muestreo supera 44.1 kHz. */
    val esHiRes: Boolean get() = sampleRate > 44100

    /** `true` cuando el audio es sin pérdida según su profundidad de bits. */
    val esSinPerdida: Boolean get() = exacto && bitsPorMuestra >= 16
}