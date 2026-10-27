package com.cloudjex.jexplayer.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsSettingsTest {

    @Test
    fun `por defecto usa la transicion actual`() {
        assertEquals(LyricsLineMotion.ACTUAL, LyricsSettings().lineMotion)
    }

    @Test
    fun `restaura el modo guardado y cae en actual si no existe`() {
        assertEquals(
            LyricsLineMotion.ENFOQUE,
            LyricsLineMotion.fromStorageKey("enfoque")
        )
        assertEquals(
            LyricsLineMotion.ACTUAL,
            LyricsLineMotion.fromStorageKey("desconocido")
        )
        assertEquals(
            LyricsLineMotion.ACTUAL,
            LyricsLineMotion.fromStorageKey(null)
        )
    }

    @Test
    fun `normalizar conserva el modo de movimiento`() {
        val settings = LyricsSettings(
            fontSizeSp = 99,
            lineMotion = LyricsLineMotion.ENFOQUE
        ).normalizada()

        assertEquals(LyricsLineMotion.ENFOQUE, settings.lineMotion)
        assertEquals(LyricsSettings.MAX_FONT_SIZE_SP, settings.fontSizeSp)
    }
}
