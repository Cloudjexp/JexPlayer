package com.cloudjex.jexplayer.ui.theme

import androidx.compose.ui.graphics.Color
import com.cloudjex.jexplayer.data.model.AppearanceTheme

/** Tokens semánticos de color; las pantallas deben consumir MaterialTheme.colorScheme. */
data class JexPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceBright: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val outlineVariant: Color
)

fun paletteFor(theme: AppearanceTheme): JexPalette = when (theme) {
    AppearanceTheme.MINIMALIST_GRAY -> JexPalette(
        background = Color(0xFF111111),
        surface = Color(0xFF242424),
        surfaceElevated = Color(0xFF383838),
        surfaceBright = Color(0xFF454545),
        primary = Color(0xFFB5B5B5),
        onPrimary = Color(0xFF171717),
        primaryContainer = Color(0xFF3D3D3D),
        onPrimaryContainer = Color(0xFFF2F2F2),
        secondary = Color(0xFFA5A5A5),
        onSecondary = Color(0xFF191919),
        secondaryContainer = Color(0xFF343434),
        onSecondaryContainer = Color(0xFFE6E6E6),
        tertiary = Color(0xFFD0D0D0),
        onTertiary = Color(0xFF202020),
        tertiaryContainer = Color(0xFF3A3A3A),
        onTertiaryContainer = Color(0xFFEDEDED),
        textPrimary = Color(0xFFF2F2F2),
        textSecondary = Color(0xFFBDBDBD),
        outline = Color(0xFF9A9A9A),
        outlineVariant = Color(0xFF565656)
    )

    AppearanceTheme.OCEAN_BLUE -> JexPalette(
        background = Color(0xFF0D141B),
        surface = Color(0xFF1B2732),
        surfaceElevated = Color(0xFF293946),
        surfaceBright = Color(0xFF354957),
        primary = Color(0xFF8BC9E8),
        onPrimary = Color(0xFF09202C),
        primaryContainer = Color(0xFF234254),
        onPrimaryContainer = Color(0xFFD8EFF9),
        secondary = Color(0xFFA9C1D0),
        onSecondary = Color(0xFF15242D),
        secondaryContainer = Color(0xFF30434F),
        onSecondaryContainer = Color(0xFFD8E7EF),
        tertiary = Color(0xFFB9C4E0),
        onTertiary = Color(0xFF20283B),
        tertiaryContainer = Color(0xFF38445F),
        onTertiaryContainer = Color(0xFFE1E6F6),
        textPrimary = Color(0xFFF0F5F8),
        textSecondary = Color(0xFFB6C3CB),
        outline = Color(0xFF8D9FA9),
        outlineVariant = Color(0xFF4B5C66)
    )

    AppearanceTheme.EMERALD_GREEN -> JexPalette(
        background = Color(0xFF0D1510),
        surface = Color(0xFF1C2921),
        surfaceElevated = Color(0xFF2B3B31),
        surfaceBright = Color(0xFF394C40),
        primary = Color(0xFF8CD3A3),
        onPrimary = Color(0xFF0B2514),
        primaryContainer = Color(0xFF244632),
        onPrimaryContainer = Color(0xFFD9F3E1),
        secondary = Color(0xFFAFC9B5),
        onSecondary = Color(0xFF1B2B20),
        secondaryContainer = Color(0xFF344A3A),
        onSecondaryContainer = Color(0xFFDFEEE2),
        tertiary = Color(0xFFA6D0C2),
        onTertiary = Color(0xFF183129),
        tertiaryContainer = Color(0xFF2D4A40),
        onTertiaryContainer = Color(0xFFD5EEE5),
        textPrimary = Color(0xFFF0F7F1),
        textSecondary = Color(0xFFB8C9BC),
        outline = Color(0xFF8EA996),
        outlineVariant = Color(0xFF4B5D50)
    )

    AppearanceTheme.AMBER -> JexPalette(
        background = Color(0xFF171309),
        surface = Color(0xFF2A2417),
        surfaceElevated = Color(0xFF3A3120),
        surfaceBright = Color(0xFF4A3E28),
        primary = Color(0xFFE8C477),
        onPrimary = Color(0xFF2B2008),
        primaryContainer = Color(0xFF4B3A1C),
        onPrimaryContainer = Color(0xFFF7E8C3),
        secondary = Color(0xFFD3C19B),
        onSecondary = Color(0xFF302717),
        secondaryContainer = Color(0xFF483B26),
        onSecondaryContainer = Color(0xFFF0E4C9),
        tertiary = Color(0xFFE1B99A),
        onTertiary = Color(0xFF352317),
        tertiaryContainer = Color(0xFF503927),
        onTertiaryContainer = Color(0xFFF4DECB),
        textPrimary = Color(0xFFF8F3E9),
        textSecondary = Color(0xFFC9BEA8),
        outline = Color(0xFFAA9874),
        outlineVariant = Color(0xFF65573C)
    )

    AppearanceTheme.RUBY_RED -> JexPalette(
        background = Color(0xFF170F11),
        surface = Color(0xFF2A1D20),
        surfaceElevated = Color(0xFF3B292D),
        surfaceBright = Color(0xFF4B3439),
        primary = Color(0xFFE99AA1),
        onPrimary = Color(0xFF321216),
        primaryContainer = Color(0xFF51262C),
        onPrimaryContainer = Color(0xFFF9DDE0),
        secondary = Color(0xFFD3AEB2),
        onSecondary = Color(0xFF332124),
        secondaryContainer = Color(0xFF493237),
        onSecondaryContainer = Color(0xFFF1DDE0),
        tertiary = Color(0xFFE2B6A6),
        onTertiary = Color(0xFF38251E),
        tertiaryContainer = Color(0xFF50382F),
        onTertiaryContainer = Color(0xFFF5E0D8),
        textPrimary = Color(0xFFF8F0F1),
        textSecondary = Color(0xFFCDBEC0),
        outline = Color(0xFFAE9296),
        outlineVariant = Color(0xFF684B50)
    )

    AppearanceTheme.SUBTLE_PINK -> JexPalette(
        background = Color(0xFF171114),
        surface = Color(0xFF292126),
        surfaceElevated = Color(0xFF392E34),
        surfaceBright = Color(0xFF493A42),
        primary = Color(0xFFDDB0C2),
        onPrimary = Color(0xFF30212A),
        primaryContainer = Color(0xFF4A333F),
        onPrimaryContainer = Color(0xFFF4E1E9),
        secondary = Color(0xFFCBB6C0),
        onSecondary = Color(0xFF30262C),
        secondaryContainer = Color(0xFF453640),
        onSecondaryContainer = Color(0xFFECDFE5),
        tertiary = Color(0xFFD9BDA8),
        onTertiary = Color(0xFF35291F),
        tertiaryContainer = Color(0xFF4C3B2F),
        onTertiaryContainer = Color(0xFFF2E4D8),
        textPrimary = Color(0xFFF7F1F4),
        textSecondary = Color(0xFFC9BBC2),
        outline = Color(0xFFA9959E),
        outlineVariant = Color(0xFF62505A)
    )
}
