package com.cloudjex.jexplayer.data.model

/** Temas que puede elegir el usuario. El identificador se conserva en DataStore. */
enum class AppearanceTheme(val storageKey: String, val label: String) {
    OCEAN_BLUE("ocean_blue", "Azul Océano"),
    EMERALD_GREEN("emerald_green", "Verde Esmeralda"),
    AMBER("amber", "Ámbar"),
    RUBY_RED("ruby_red", "Rojo Rubí"),
    MINIMALIST_GRAY("minimalist_gray", "Gris Minimalista"),
    SUBTLE_PINK("subtle_pink", "Rosa Sutil");

    companion object {
        val default: AppearanceTheme = MINIMALIST_GRAY

        fun fromStorageKey(value: String?): AppearanceTheme =
            entries.firstOrNull { it.storageKey == value } ?: default
    }
}
