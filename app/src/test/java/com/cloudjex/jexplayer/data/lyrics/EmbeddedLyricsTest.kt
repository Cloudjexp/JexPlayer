package com.cloudjex.jexplayer.data.lyrics

import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.mp4.field.Mp4TagTextField
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddedLyricsTest {

    @Test
    fun `lee las letras USLT de un mp3`() {
        val tag = ID3v24Tag()
        tag.setField(FieldKey.LYRICS, "[00:01.00]Hola desde MP3")

        assertEquals("[00:01.00]Hola desde MP3", buscarLetrasEnTag(tag))
    }

    @Test
    fun `lee las letras lyr de un m4a`() {
        val tag = Mp4Tag()
        tag.setField(FieldKey.LYRICS, "[00:01.00]Hola desde M4A")

        assertEquals("[00:01.00]Hola desde M4A", buscarLetrasEnTag(tag))
    }

    @Test
    fun `lee un lrc enriquecido completo guardado en una sola linea de m4a`() {
        val letra =
            "[ti:Canción] [ar:Artista] " +
                "[00:01.000]<00:01.000>Hola <00:01.500>mundo " +
                "[00:03.000]<00:03.000>Adiós <00:03.500>mundo"
        val tag = Mp4Tag()
        tag.setField(FieldKey.LYRICS, letra)

        val extraida = buscarLetrasEnTag(tag)
        val lineas = LyricsParser.parse(extraida.orEmpty(), LyricsFormat.EMBEDDED)

        assertEquals(letra, extraida)
        assertEquals(listOf(1_000L, 3_000L), lineas.map { it.tiempo })
        assertEquals(listOf("Hola mundo", "Adiós mundo"), lineas.map { it.texto })
        assertEquals(2, lineas[0].segmentos.size)
        assertEquals(2, lineas[1].segmentos.size)
    }

    @Test
    fun `prefiere la letra mas completa entre las variantes de m4a`() {
        val completa = "[00:01.00]Primera línea\n[00:02.00]Segunda línea"
        val tag = Mp4Tag()
        tag.setField(FieldKey.LYRICS, "[00:01.00]Corta")
        tag.setField(
            Mp4TagTextField("----:com.apple.iTunes:LYRICS", completa)
        )

        assertEquals(completa, buscarLetrasEnTag(tag))
    }

    @Test
    fun `mantiene el respaldo de claves vorbis`() {
        val tag = VorbisCommentTag()
        tag.setField("UNSYNCEDLYRICS", "[00:01.00]Hola desde FLAC")

        assertEquals("[00:01.00]Hola desde FLAC", buscarLetrasEnTag(tag))
    }

    @Test
    fun `devuelve null cuando no hay letras`() {
        assertNull(buscarLetrasEnTag(ID3v24Tag()))
    }
}
