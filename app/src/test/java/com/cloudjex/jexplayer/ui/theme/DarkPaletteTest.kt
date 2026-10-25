package com.cloudjex.jexplayer.ui.theme

import androidx.compose.ui.graphics.Color
import com.cloudjex.jexplayer.data.model.AppearanceTheme
import com.cloudjex.jexplayer.data.model.AppearanceAccent
import org.junit.Assert.assertEquals
import org.junit.Test

class DarkPaletteTest {

    @Test
    fun darkUsesPureBlackBackgroundAndDarkSurfaces() {
        val palette = paletteFor(AppearanceTheme.DARK)

        assertEquals(Color(0xFF000000), palette.background)
        assertEquals(Color(0xFF111111), palette.surface)
        assertEquals(Color(0xFF1A1A1A), palette.surfaceElevated)
    }

    @Test
    fun darkKeepsWarmTextAndGoldAccent() {
        val palette = paletteFor(AppearanceTheme.DARK)

        assertEquals(Color(0xFFE8C477), palette.primary)
        assertEquals(Color(0xFFF5F5F5), palette.textPrimary)
        assertEquals(Color(0xFFBDBDBD), palette.textSecondary)
    }

    @Test
    fun darkReusesSelectedAccentWhileKeepingItsBlackSurface() {
        val palette = paletteFor(AppearanceTheme.DARK, AppearanceAccent.OCEAN_BLUE)

        assertEquals(Color(0xFF8BC9E8), palette.primary)
        assertEquals(Color(0xFF000000), palette.background)
        assertEquals(Color(0xFF111111), palette.surface)
    }
}
