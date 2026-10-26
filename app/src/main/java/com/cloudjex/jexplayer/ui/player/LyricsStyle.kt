package com.cloudjex.jexplayer.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.cloudjex.jexplayer.data.model.LyricsColorPreset
import com.cloudjex.jexplayer.data.model.LyricsFontPreset

/** Color de la parte ya cantada (y de la línea activa sin karaoke). */
internal val LyricsColorPreset.colorActivo: Color
    get() = when (this) {
        LyricsColorPreset.GRIS -> Color.White
        LyricsColorPreset.BLANCO -> Color.White
        LyricsColorPreset.AMBAR -> Color(0xFFFFD28A)
        LyricsColorPreset.AZUL -> Color(0xFFA8D8FF)
        LyricsColorPreset.VERDE -> Color(0xFFA9E8B0)
    }

/** Color de la parte pendiente: el activo con menos opacidad. */
internal val LyricsColorPreset.colorPendiente: Color
    get() = colorActivo.copy(alpha = 0.4f)

internal val LyricsFontPreset.familia: FontFamily
    get() = when (this) {
        LyricsFontPreset.SANS -> FontFamily.SansSerif
        LyricsFontPreset.SERIF -> FontFamily.Serif
        LyricsFontPreset.MONO -> FontFamily.Monospace
    }
