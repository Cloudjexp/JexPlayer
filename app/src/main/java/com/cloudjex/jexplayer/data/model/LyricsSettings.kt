package com.cloudjex.jexplayer.data.model

/** Paletas disponibles para la letra activa y el karaoke. */
enum class LyricsColorPreset(
    val storageKey: String,
    val label: String
) {
    GRIS("gris", "Gris"),
    BLANCO("blanco", "Blanco"),
    AMBAR("ambar", "Ámbar"),
    AZUL("azul", "Azul"),
    VERDE("verde", "Verde");

    companion object {
        fun fromStorageKey(value: String?): LyricsColorPreset =
            entries.firstOrNull { it.storageKey == value } ?: GRIS
    }
}

/** Familias tipográficas del sistema, sin descargar fuentes externas. */
enum class LyricsFontPreset(
    val storageKey: String,
    val label: String
) {
    SANS("sans", "Sans"),
    SERIF("serif", "Serif"),
    MONO("mono", "Mono");

    companion object {
        fun fromStorageKey(value: String?): LyricsFontPreset =
            entries.firstOrNull { it.storageKey == value } ?: SANS
    }
}

/** Apariencia persistente de la pantalla de letras. */
data class LyricsSettings(
    val colorPreset: LyricsColorPreset = LyricsColorPreset.GRIS,
    val fontPreset: LyricsFontPreset = LyricsFontPreset.SANS,
    val italic: Boolean = true,
    val fontSizeSp: Int = DEFAULT_FONT_SIZE_SP,
    val showTranslation: Boolean = false
) {
    fun normalizada(): LyricsSettings = copy(
        fontSizeSp = fontSizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
    )

    companion object {
        const val MIN_FONT_SIZE_SP = 13
        const val MAX_FONT_SIZE_SP = 30
        const val DEFAULT_FONT_SIZE_SP = 18
    }
}

/** Combinaciones rápidas que el usuario puede aplicar desde el panel. */
enum class LyricsStylePreset(
    val label: String,
    val settings: LyricsSettings
) {
    CLASICO("Clásico", LyricsSettings()),
    MINIMAL(
        "Minimal",
        LyricsSettings(
            colorPreset = LyricsColorPreset.BLANCO,
            italic = false,
            fontSizeSp = 16
        )
    ),
    SERIF(
        "Serif",
        LyricsSettings(
            fontPreset = LyricsFontPreset.SERIF,
            fontSizeSp = 19
        )
    ),
    GRANDE(
        "Grande",
        LyricsSettings(fontSizeSp = 22)
    ),
    AMBAR(
        "Ámbar",
        LyricsSettings(
            colorPreset = LyricsColorPreset.AMBAR,
            fontSizeSp = 19
        )
    )
}
