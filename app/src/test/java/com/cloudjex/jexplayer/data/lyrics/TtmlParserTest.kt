package com.cloudjex.jexplayer.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlParserTest {

    @Test
    fun `lee tiempos por linea y por palabra`() {
        val ttml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xml:lang="es">
              <body>
                <div>
                  <p begin="00:00:16.296" end="00:00:20.028">
                    <span begin="00:00:16.296">I </span>
                    <span begin="00:00:16.885">don't </span>
                    <span begin="00:00:17.147">wan</span><span begin="00:00:17.655">na </span>
                    <span begin="00:00:18.200">know</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val lineas = TtmlParser.parse(ttml)

        assertEquals(1, lineas.size)
        val linea = lineas.first()
        assertEquals(16_296L, linea.tiempo)
        assertEquals("I don't wanna know", linea.texto)
        assertEquals(20_028L, linea.finMs)
        assertEquals(
            listOf(16_296L, 16_885L, 17_147L, 17_655L, 18_200L),
            linea.segmentos.map { it.tiempo }
        )
        assertEquals(
            listOf("I ", "don't ", "wan", "na ", "know"),
            linea.segmentos.map { it.texto }
        )
    }

    @Test
    fun `usa el primer span si el parrafo no tiene begin`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body>
                <div>
                  <p>
                    <span begin="1.5s">Hola </span>
                    <span begin="2s">mundo</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val linea = TtmlParser.parse(ttml).single()

        assertEquals(1_500L, linea.tiempo)
        assertEquals("Hola mundo", linea.texto)
        assertEquals(listOf(1_500L, 2_000L), linea.segmentos.map { it.tiempo })
    }

    @Test
    fun `suma los offsets de palabra al inicio del parrafo`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="16s" end="20s">
                  <span begin="1.5s">Hola </span>
                  <span begin="2s">mundo</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()

        val linea = TtmlParser.parse(ttml).single()

        assertEquals(16_000L, linea.tiempo)
        assertEquals(20_000L, linea.finMs)
        assertEquals(listOf(17_500L, 18_000L), linea.segmentos.map { it.tiempo })
        assertEquals("Hola mundo", linea.texto)
    }

    @Test
    fun `descarta traducciones y acepta lineas sin tiempos por palabra`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xml:lang="es">
              <body>
                <div>
                  <p begin="00:00:01.000" end="00:00:03.000">Hola</p>
                  <p begin="00:00:01.000" ttm:role="x-translation">Hello</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val linea = TtmlParser.parse(ttml).single()

        assertEquals("Hola", linea.texto)
        assertEquals(1_000L, linea.tiempo)
        assertEquals(3_000L, linea.finMs)
        assertTrue(linea.segmentos.isEmpty())
        assertNull(linea.segmentos.firstOrNull())
    }
}
