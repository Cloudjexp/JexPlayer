package com.cloudjex.jexplayer.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {

    @Test
    fun `parsea SRT y limpia etiquetas`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            <i>Hola</i>
            mundo

            2
            00:00:05,500 --> 00:00:08,250
            Otra línea
        """.trimIndent()

        val lineas = SubtitleParser.parseSrt(srt)

        assertEquals(2, lineas.size)
        assertEquals(1_000L, lineas[0].tiempo)
        assertEquals("Hola mundo", lineas[0].texto)
        assertEquals(4_000L, lineas[0].finMs)
        assertEquals(5_500L, lineas[1].tiempo)
        assertEquals(8_250L, lineas[1].finMs)
        assertTrue(lineas.all { it.segmentos.isEmpty() })
    }

    @Test
    fun `parsea WebVTT con identificador y nota`() {
        val vtt = """
            WEBVTT

            NOTE esto no es una línea

            cue-1
            00:01.000 --> 00:03.500 align:start
            <v Speaker>Hola</v> mundo
        """.trimIndent()

        val lineas = SubtitleParser.parseVtt(vtt)

        assertEquals(1, lineas.size)
        assertEquals(1_000L, lineas[0].tiempo)
        assertEquals("Hola mundo", lineas[0].texto)
        assertEquals(3_500L, lineas[0].finMs)
    }

    @Test
    fun `detecta el formato por el contenido`() {
        assertEquals(
            LyricsFormat.LRC,
            LyricsParser.detectFormat("[00:01.00]Hola")
        )
        assertEquals(
            LyricsFormat.SRT,
            LyricsParser.detectFormat("1\n00:00:01,000 --> 00:00:02,000\nHola")
        )
        assertEquals(
            LyricsFormat.VTT,
            LyricsParser.detectFormat("WEBVTT\n\n00:01.000 --> 00:02.000\nHola")
        )
        assertEquals(
            LyricsFormat.TTML,
            LyricsParser.detectFormat(
                "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body/></tt>"
            )
        )
    }

    @Test
    fun `el contenido manda sobre la extension esperada`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:01.000">Hola</p>
              </div></body>
            </tt>
        """.trimIndent()

        val lineas = LyricsParser.parse(ttml, LyricsFormat.LRC)

        assertEquals(1, lineas.size)
        assertEquals("Hola", lineas[0].texto)
    }
}
