package com.cloudjex.jexplayer.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeTimingTest {

    @Test
    fun `usa el inicio del siguiente segmento como fin`() {
        val linea = lineaConSegmentos(
            tiempos = listOf(16_296L, 16_885L, 17_147L, 17_655L),
            textos = listOf("I ", "don't ", "wanna ", "die")
        )

        val ventanas = ventanasKaraoke(linea, siguienteLineaMs = 20_000L)

        assertEquals(
            listOf(
                VentanaKaraoke(16_296L, 16_885L),
                VentanaKaraoke(16_885L, 17_147L),
                VentanaKaraoke(17_147L, 17_655L),
                VentanaKaraoke(17_655L, 20_000L)
            ),
            ventanas
        )
    }

    @Test
    fun `prefiere el fin declarado de la linea sobre la siguiente linea`() {
        val linea = lineaConSegmentos(
            tiempos = listOf(1_000L, 1_500L),
            textos = listOf("Ha", "bla"),
            finMs = 1_800L
        )

        val ventanas = ventanasKaraoke(linea, siguienteLineaMs = 30_000L)

        assertEquals(1_800L, ventanas.last().finMs)
    }

    @Test
    fun `estima el ultimo segmento cuando la siguiente linea queda muy lejos`() {
        val linea = lineaConSegmentos(
            tiempos = listOf(0L, 500L),
            textos = listOf("Ha", "la")
        )

        val ventanas = ventanasKaraoke(linea, siguienteLineaMs = 20_000L)

        assertEquals(500L, ventanas.first().finMs)
        // Ritmo de 120 ms por carácter como máximo, dos caracteres de "la".
        assertEquals(740L, ventanas.last().finMs)
        assertTrue(ventanas.last().finMs < 20_000L)
    }

    @Test
    fun `ignora un fin declarado anterior al ultimo segmento`() {
        val linea = lineaConSegmentos(
            tiempos = listOf(500L, 1_000L),
            textos = listOf("uno ", "dos"),
            finMs = 400L
        )

        val ventanas = ventanasKaraoke(linea, siguienteLineaMs = null)

        assertTrue(ventanas.all { it.finMs > it.inicioMs })
        assertEquals(1_000L + 120L * 3, ventanas.last().finMs)
    }

    @Test
    fun `estima con el ritmo de los segmentos anteriores`() {
        val linea = lineaConSegmentos(
            tiempos = listOf(0L, 1_000L, 2_000L),
            textos = listOf("uno ", "dos ", "tres")
        )

        val ventanas = ventanasKaraoke(linea, siguienteLineaMs = 20_000L)

        assertEquals(2_000L + 120L * 4, ventanas.last().finMs)
    }

    @Test
    fun `los tiempos repetidos no generan duraciones negativas`() {
        val linea = lineaConSegmentos(
            tiempos = listOf(1_000L, 1_000L, 1_000L),
            textos = listOf("a", "b", "c")
        )

        val ventanas = ventanasKaraoke(linea, siguienteLineaMs = null)

        ventanas.forEach { ventana ->
            assertTrue(ventana.duracionMs >= 0L)
            assertEquals(0f, ventana.progreso(999L), 0.0001f)
            assertEquals(1f, ventana.progreso(ventana.finMs), 0.0001f)
        }
    }

    @Test
    fun `reparte el progreso entre cero y uno`() {
        val ventana = VentanaKaraoke(1_000L, 2_000L)

        assertEquals(0f, ventana.progreso(500L), 0.0001f)
        assertEquals(0f, ventana.progreso(1_000L), 0.0001f)
        assertEquals(0.5f, ventana.progreso(1_500L), 0.0001f)
        assertEquals(1f, ventana.progreso(2_000L), 0.0001f)
        assertEquals(1f, ventana.progreso(9_000L), 0.0001f)
    }

    @Test
    fun `un tramo sin duracion se completa al llegar a su inicio`() {
        val ventana = VentanaKaraoke(1_000L, 1_000L)

        assertEquals(0f, ventana.progreso(999L), 0.0001f)
        assertEquals(0L, ventana.duracionMs)
        assertEquals(1f, ventana.progreso(1_000L), 0.0001f)
    }

    @Test
    fun `mantiene los segmentos por silaba en orden`() {
        val linea = lineaConSegmentos(
            tiempos = listOf(17_147L, 17_655L, 17_891L),
            textos = listOf("wan", "na ", "die")
        )

        val ventanas = ventanasKaraoke(linea, siguienteLineaMs = 20_000L)

        assertEquals(17_147L, ventanas[0].inicioMs)
        assertEquals(17_655L, ventanas[1].inicioMs)
        assertEquals(17_891L, ventanas[2].inicioMs)
        assertTrue(ventanas.zipWithNext().all { (a, b) -> a.finMs <= b.finMs })
    }

    private fun lineaConSegmentos(
        tiempos: List<Long>,
        textos: List<String>,
        finMs: Long? = null
    ): LineaCancion {
        val segmentos = tiempos.zip(textos) { tiempo, texto ->
            SegmentoLetra(tiempo = tiempo, texto = texto)
        }
        return LineaCancion(
            tiempo = tiempos.first(),
            texto = textos.joinToString(""),
            segmentos = segmentos,
            finMs = finMs
        )
    }
}
