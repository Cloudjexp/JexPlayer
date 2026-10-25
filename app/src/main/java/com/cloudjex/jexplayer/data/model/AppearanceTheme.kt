package com.cloudjex.jexplayer.data.model

/** Temas que puede elegir el usuario. El identificador se conserva en DataStore. */
enum class AppearanceTheme(val storageKey: String, val label: String) {
    OCEAN_BLUE("ocean_blue", "Azul Océano"),
    EMERALD_GREEN("emerald_green", "Verde Esmeralda"),
    AMBER("amber", "Ámbar"),
    RUBY_RED("ruby_red", "Rojo Rubí"),
    DARK("dark", "Oscuro"),
    SUBTLE_PINK("subtle_pink", "Rosa Sutil");

    companion object {
        private const val LEGACY_MINIMALIST_GRAY = "minimalist_gray"
        private const val LEGACY_AMOLED_BLACK = "amoled_black"

        val default: AppearanceTheme = DARK

        fun fromStorageKey(value: String?): AppearanceTheme =
            entries.firstOrNull { it.storageKey == value }
                ?: when (value) {
                    LEGACY_MINIMALIST_GRAY, LEGACY_AMOLED_BLACK -> DARK
                    else -> default
                }
    }
}
