package com.cloudjex.jexplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JexDarkColorScheme = darkColorScheme(
    primary = JexPrimary,
    onPrimary = JexOnPrimary,
    primaryContainer = JexPrimaryContainer,
    onPrimaryContainer = JexTextPrimary,
    inversePrimary = JexRedSecondary,

    secondary = JexViolet,
    onSecondary = JexOnViolet,
    secondaryContainer = JexVioletContainer,
    onSecondaryContainer = JexTextPrimary,

    tertiary = JexVioletDark,
    onTertiary = JexTextPrimary,
    tertiaryContainer = JexVioletContainer,
    onTertiaryContainer = JexViolet,

    background = JexBackground,
    onBackground = JexTextPrimary,

    surface = JexSurface,
    onSurface = JexTextPrimary,
    surfaceVariant = JexSurfaceVariant,
    onSurfaceVariant = JexTextSecondary,
    surfaceTint = JexPrimary,

    inverseSurface = JexTextPrimary,
    inverseOnSurface = JexBackground,

    outline = JexOutline,
    outlineVariant = JexOutlineVariant,
    scrim = Color.Black,

    surfaceBright = JexSurfaceBright,
    surfaceDim = JexBackground,
    surfaceContainerLowest = JexBackground,
    surfaceContainerLow = JexSurface,
    surfaceContainer = JexSurface,
    surfaceContainerHigh = JexSurfaceElevated,
    surfaceContainerHighest = JexSurfaceBright
)

private val JexLightColorScheme = lightColorScheme(
    primary = JexVioletDark,
    onPrimary = Color.White,
    primaryContainer = JexLightPrimaryContainer,
    onPrimaryContainer = JexLightOnSurface,

    secondary = JexPrimary,
    onSecondary = JexOnPrimary,
    secondaryContainer = JexLightSurfaceVariant,
    onSecondaryContainer = JexLightOnSurface,

    tertiary = JexViolet,
    onTertiary = JexOnViolet,

    background = JexLightBackground,
    onBackground = JexLightOnSurface,

    surface = JexLightSurface,
    onSurface = JexLightOnSurface,
    surfaceVariant = JexLightSurfaceVariant,
    onSurfaceVariant = JexLightTextSecondary,

    outline = JexLightOutline,
    outlineVariant = JexLightSurfaceVariant
)

@Composable
fun JexPlayerTheme(
    darkTheme: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JexDarkColorScheme else JexLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
