package com.cloudjex.jexplayer.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LrcParserTest {

    @Test
    fun `lee marcas con tres digitos e ignora prefijos v1`() {
        val letra = """
            [ti:Anywhere For You (Stripped)]
            [ar:BANNERS]
            [by:Mornye (source: lrc.red)]

            [00:00.00]v1:Now that we’re both standing here
            [00:03.677]v1:In this moment
            [00:08.987]v1:You’re in focus
        """.trimIndent()

        val lineas = obtenerListaSincronizada(letra)

        assertEquals(listOf(0L, 3_677L, 8_987L), lineas.map { it.tiempo })
        assertEquals(
            listOf(
                "Now that we’re both standing here",
                "In this moment",
                "You’re in focus"
            ),
            lineas.map { it.texto }
        )
        assertFalse(lineas.any { it.texto.startsWith("v1:") })
    }

    @Test
    fun `acepta fracciones de una dos y tres cifras`() {
        val letra = """
            [00:01.2]Décimas
            [00:02.34]Centésimas
            [00:03.456]Milésimas
            [00:04]Sin fracción
        """.trimIndent()

        val lineas = obtenerListaSincronizada(letra)

        assertEquals(listOf(1_200L, 2_340L, 3_456L, 4_000L), lineas.map { it.tiempo })
    }

    @Test
    fun `ignora etiquetas de metadatos y texto sin marca de tiempo`() {
        val letra = """
            [ti:Canción]
            [ar:Artista]
            [al:Álbum]
            [by:Alguien]
            Esta línea no tiene tiempo
        """.trimIndent()

        assertEquals(emptyList<LineaCancion>(), obtenerListaSincronizada(letra))
    }

    @Test
    fun `solo elimina el prefijo de versión al inicio del texto`() {
        val letra = "[00:01.000]Version v1: no es un prefijo"

        val linea = obtenerListaSincronizada(letra).single()

        assertEquals(1_000L, linea.tiempo)
        assertEquals("Version v1: no es un prefijo", linea.texto)
    }

    @Test
    fun `una linea con varias marcas genera una entrada por marca`() {
        val letra = "[00:01.00][00:05.50]Estribillo"

        val lineas = obtenerListaSincronizada(letra)

        assertEquals(listOf(1_000L, 5_500L), lineas.map { it.tiempo })
        assertEquals(listOf("Estribillo", "Estribillo"), lineas.map { it.texto })
    }

    @Test
    fun `convierte marcas sueltas a milisegundos`() {
        assertEquals(62_345L, tiempoAMilisegundos("01:02.345"))
        assertEquals(8_980L, tiempoAMilisegundos("00:08.98"))
        assertEquals(8_900L, tiempoAMilisegundos("00:08.9"))
        assertEquals(8_000L, tiempoAMilisegundos("00:08"))
        assertEquals(0L, tiempoAMilisegundos("texto inválido"))
    }

    @Test
    fun `lee tiempos por palabra y reconstruye el texto para karaoke`() {
        val letra = "[00:16.296]v1:<00:16.296>I <00:16.885><00:16.885>don't " +
            "<00:17.147><00:17.147>wan<00:17.655><00:17.655>na " +
            "<00:17.891><00:17.891>die <00:18.456><00:18.456>or " +
            "<00:18.675><00:18.675>fade <00:18.935><00:18.935>away<00:20.028>"

        val linea = obtenerListaSincronizada(letra).single()

        assertEquals("I don't wanna die or fade away", linea.texto)
        assertEquals(20_028L, linea.finMs)
        assertEquals(
            listOf(16_296L, 16_885L, 17_147L, 17_655L, 17_891L, 18_456L, 18_675L, 18_935L),
            linea.segmentos.map { it.tiempo }
        )
        assertEquals(
            listOf("I ", "don't ", "wan", "na ", "die ", "or ", "fade ", "away"),
            linea.segmentos.map { it.texto }
        )
    }

    @Test
    fun `acepta tiempos de palabra con dos cifras decimales`() {
        val letra = "[00:25.059]v1:<00:25.059>I <00:25.276>just " +
            "<00:25.495>wan<00:26.70>na <00:27.228>"

        val linea = obtenerListaSincronizada(letra).single()

        assertEquals("I just wanna", linea.texto)
        assertEquals(listOf(25_059L, 25_276L, 25_495L, 26_700L), linea.segmentos.map { it.tiempo })
        assertEquals(27_228L, linea.finMs)
    }

    @Test
    fun `mantiene el orden cuando hay marcas de silaba repetidas o invertidas`() {
        val letra = "[01:13.295]v1:<01:13.295>I <01:13.495><01:13.495>re" +
            "<01:13.505><01:13.501>al<01:13.535><01:13.535>ly " +
            "<01:13.875>need<01:14.235>"

        val linea = obtenerListaSincronizada(letra).single()

        assertEquals("I really need", linea.texto)
        assertEquals(
            listOf(73_295L, 73_495L, 73_505L, 73_535L, 73_875L),
            linea.segmentos.map { it.tiempo }
        )
        assertEquals(listOf("I ", "re", "al", "ly ", "need"), linea.segmentos.map { it.texto })
        assertEquals(74_235L, linea.finMs)
    }

    @Test
    fun `las lineas sin marcas de palabra no generan segmentos`() {
        val linea = obtenerListaSincronizada("[00:01.00]Letra normal").single()

        assertEquals("Letra normal", linea.texto)
        assertEquals(emptyList<SegmentoLetra>(), linea.segmentos)
        assertEquals(null, linea.finMs)
    }

    @Test
    fun `separa marcas de tiempo cuando el lrc llega en una sola linea`() {
        val letra =
            "[ti:What's The Difference] [ar:Dr. Dre] " +
                "[00:11.018]<00:11.018>What's <00:11.212>the " +
                "<00:11.318>difference [00:16.19]<00:16.19>What's " +
                "<00:16.378>the <00:16.506>difference"

        val lineas = obtenerListaSincronizada(letra)

        assertEquals(listOf(11_018L, 16_190L), lineas.map { it.tiempo })
        assertEquals(
            listOf("What's the difference", "What's the difference"),
            lineas.map { it.texto }
        )
        assertEquals(3, lineas[0].segmentos.size)
        assertEquals(3, lineas[1].segmentos.size)
    }

    @Test
    fun `detecta lrc aunque las marcas esten separadas por espacios`() {
        val letra = "[ti:Canción] [00:01.00]Primera [00:02.00]Segunda"

        assertEquals(LyricsFormat.LRC, LyricsParser.detectFormat(letra))
    }
}
