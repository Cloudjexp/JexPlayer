package com.cloudjex.jexplayer.data.model

import java.util.Locale

/**
 * Valores técnicos de audio disponibles en ese momento. `null` significa que la
 * fuente consultada todavía no conoce ese campo; no se sustituye por un valor supuesto.
 */
data class AudioTechnicalInfo(
    val sampleRateHz: Int? = null,
    val bitRateBps: Long? = null,
    val channelCount: Int? = null,
    val bitsPerSample: Int? = null
) {
    val hasValues: Boolean
        get() = sampleRateHz.isPositive() || bitRateBps.isPositive() ||
            channelCount.isPositive() || bitsPerSample.isPositive()

    /** Los datos nuevos completan los desconocidos y reemplazan los conocidos. */
    fun merge(available: AudioTechnicalInfo): AudioTechnicalInfo = AudioTechnicalInfo(
        sampleRateHz = available.sampleRateHz.positiveOrNull() ?: sampleRateHz.positiveOrNull(),
        bitRateBps = available.bitRateBps.positiveOrNull() ?: bitRateBps.positiveOrNull(),
        channelCount = available.channelCount.positiveOrNull() ?: channelCount.positiveOrNull(),
        bitsPerSample = available.bitsPerSample.positiveOrNull() ?: bitsPerSample.positiveOrNull()
    )

    companion object {
        fun from(tags: AudioTags): AudioTechnicalInfo = AudioTechnicalInfo(
            sampleRateHz = tags.sampleRate.positiveOrNull(),
            bitRateBps = tags.bitRate.toLong().positiveOrNull(),
            channelCount = tags.canales.positiveOrNull(),
            bitsPerSample = tags.bitsPorMuestra.positiveOrNull()
        )
    }
}

/** Metadatos asociados explícitamente a la canción que los produjo. */
data class SongAudioTechnicalInfo(
    val songId: String,
    val info: AudioTechnicalInfo
)

fun SongAudioTechnicalInfo?.forSong(songId: String): AudioTechnicalInfo? =
    this?.takeIf { it.songId == songId }?.info

/** Formatea solo campos positivos, en el orden de presentación del reproductor. */
fun AudioTechnicalInfo.displayValues(): List<String> = buildList {
    bitRateBps?.takeIf { it > 0L }?.let { bitRate ->
        add(if (bitRate >= 1000L) "${bitRate / 1000L} kbps" else "$bitRate bps")
    }
    sampleRateHz?.takeIf { it > 0 }?.let { sampleRate ->
        add("${"%.1f".format(Locale.ROOT, sampleRate / 1000f)} kHz")
    }
    channelCount?.takeIf { it > 0 }?.let { channels ->
        add(
            when (channels) {
                1 -> "Mono"
                2 -> "Estéreo"
                else -> "$channels canales"
            }
        )
    }
    bitsPerSample?.takeIf { it > 0 }?.let { bits -> add("$bits bits") }
}

private fun Int?.positiveOrNull(): Int? = this?.takeIf { it > 0 }

private fun Long?.positiveOrNull(): Long? = this?.takeIf { it > 0L }

private fun Int?.isPositive(): Boolean = this != null && this > 0

private fun Long?.isPositive(): Boolean = this != null && this > 0L
