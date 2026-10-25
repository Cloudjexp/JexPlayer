package com.cloudjex.jexplayer.data.model

/** Colores de acento disponibles para la paleta Oscuro. */
enum class AppearanceAccent(val storageKey: String, val label: String) {
    OCEAN_BLUE("ocean_blue", "Azul Océano"),
    EMERALD_GREEN("emerald_green", "Verde Esmeralda"),
    AMBER("amber", "Ámbar"),
    RUBY_RED("ruby_red", "Rojo Rubí"),
    MINIMALIST_GRAY("minimalist_gray", "Gris Minimalista"),
    SUBTLE_PINK("subtle_pink", "Rosa Sutil");

    companion object {
        val default: AppearanceAccent = AMBER

        fun fromStorageKey(value: String?): AppearanceAccent =
            entries.firstOrNull { it.storageKey == value } ?: default
    }
}
