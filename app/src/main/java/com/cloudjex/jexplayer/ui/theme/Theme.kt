package com.cloudjex.jexplayer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cloudjex.jexplayer.data.model.AppearanceTheme
import com.cloudjex.jexplayer.data.model.AppearanceAccent

@Composable
fun JexPlayerTheme(
    theme: AppearanceTheme = AppearanceTheme.default,
    accent: AppearanceAccent = AppearanceAccent.default,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(theme, accent)
    val colorScheme = darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryContainer,
        onPrimaryContainer = palette.onPrimaryContainer,
        inversePrimary = palette.primary,
        secondary = palette.secondary,
        onSecondary = palette.onSecondary,
        secondaryContainer = palette.secondaryContainer,
        onSecondaryContainer = palette.onSecondaryContainer,
        tertiary = palette.tertiary,
        onTertiary = palette.onTertiary,
        tertiaryContainer = palette.tertiaryContainer,
        onTertiaryContainer = palette.onTertiaryContainer,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        surfaceTint = palette.primary,
        inverseSurface = palette.textPrimary,
        inverseOnSurface = palette.background,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = palette.outline,
        outlineVariant = palette.outlineVariant,
        scrim = Color.Black,
        surfaceBright = palette.surfaceBright,
        surfaceDim = palette.background,
        surfaceContainerLowest = palette.background,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surfaceElevated,
        surfaceContainerHighest = palette.surfaceBright
    )

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = JexShapes,
        typography = Typography,
        content = content
    )
}

private val JexShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
