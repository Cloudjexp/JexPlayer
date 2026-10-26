package com.cloudjex.jexplayer.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BilingualLyricsTest {

    @Test
    fun `agrupa la traduccion debajo del original`() {
        val lineas = listOf(
            LineaCancion(0L, "..."),
            LineaCancion(1_000L, "Original one"),
            LineaCancion(1_000L, "(Traducción uno)"),
            LineaCancion(2_000L, "Original two"),
            LineaCancion(2_000L, "(Traducción dos)")
        )

        val resultado = BilingualLyrics.prepare(lineas, esLrcSimple = true)

        assertEquals(LyricsTranslationSupport.AVAILABLE, resultado.support)
        assertEquals(3, resultado.lineas.size)
        assertEquals("...", resultado.lineas[0].texto)
        assertEquals("Original one", resultado.lineas[1].texto)
        assertEquals("(Traducción uno)", resultado.lineas[1].traduccion)
        assertEquals("Original two", resultado.lineas[2].texto)
        assertEquals("(Traducción dos)", resultado.lineas[2].traduccion)
        assertEquals(1_000L, resultado.lineas[1].tiempo)
        assertEquals(2_000L, resultado.lineas[2].tiempo)
    }

    @Test
    fun `no agrupa letras con karaoke por palabra`() {
        val lineas = listOf(
            LineaCancion(
                tiempo = 1_000L,
                texto = "Original one",
                segmentos = listOf(SegmentoLetra(1_000L, "Original one"))
            ),
            LineaCancion(1_000L, "(Traducción uno)"),
            LineaCancion(2_000L, "Original two"),
            LineaCancion(2_000L, "(Traducción dos)")
        )

        val resultado = BilingualLyrics.prepare(lineas, esLrcSimple = true)

        assertEquals(LyricsTranslationSupport.UNAVAILABLE_KARAOKE, resultado.support)
        assertEquals(lineas, resultado.lineas)
        assertNull(resultado.lineas[0].traduccion)
    }

    @Test
    fun `solo admite lrc simple`() {
        val lineas = listOf(
            LineaCancion(1_000L, "Original one"),
            LineaCancion(1_000L, "(Traducción uno)"),
            LineaCancion(2_000L, "Original two"),
            LineaCancion(2_000L, "(Traducción dos)")
        )

        val resultado = BilingualLyrics.prepare(lineas, esLrcSimple = false)

        assertEquals(LyricsTranslationSupport.UNAVAILABLE_PATTERN, resultado.support)
        assertEquals(lineas, resultado.lineas)
    }

    @Test
    fun `no agrupa duplicados sin parentesis`() {
        val lineas = listOf(
            LineaCancion(1_000L, "Original one"),
            LineaCancion(1_000L, "Traducción uno"),
            LineaCancion(2_000L, "Original two"),
            LineaCancion(2_000L, "Traducción dos")
        )

        val resultado = BilingualLyrics.prepare(lineas, esLrcSimple = true)

        assertEquals(LyricsTranslationSupport.UNAVAILABLE_PATTERN, resultado.support)
        assertEquals(lineas, resultado.lineas)
    }

    @Test
    fun `necesita al menos dos parejas`() {
        val lineas = listOf(
            LineaCancion(1_000L, "Original one"),
            LineaCancion(1_000L, "(Traducción uno)")
        )

        val resultado = BilingualLyrics.prepare(lineas, esLrcSimple = true)

        assertEquals(LyricsTranslationSupport.UNAVAILABLE_PATTERN, resultado.support)
        assertEquals(lineas, resultado.lineas)
    }
}
