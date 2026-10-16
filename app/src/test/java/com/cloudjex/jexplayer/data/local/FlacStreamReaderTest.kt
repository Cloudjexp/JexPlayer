package com.cloudjex.jexplayer.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Construye FLACs sintéticos para verificar el parser de `STREAMINFO`. */
class FlacStreamReaderTest {

    @Test
    fun `lee sample rate canales bits y muestras del STREAMINFO`() {
        val archivo = flacCompleto(
            sampleRate = 44100,
            canales = 2,
            bitsPorMuestra = 16,
            muestrasTotales = 132_300, // 3 segundos exactos
            bloquesExtra = listOf(1 to 8) // un bloque de padding antes del último
        )

        val info = FlacStreamReader.leer(archivo.inputStream(), archivo.length())

        assertEquals(44100, info!!.sampleRate)
        assertEquals(2, info.canales)
        assertEquals(16, info.bitsPorMuestra)
        assertEquals(132_300L, info.muestrasTotales)
        assertEquals(3.0, info.duracionSegundos, 0.0001)
        // 4 (magic) + 4+34 (STREAMINFO) + 4+8 (padding) = 54 bytes de metadatos.
        assertEquals(54L, info.metadatosBytes)
        assertEquals(archivo.length() - 54L, info.audioBytes)
    }

    @Test
    fun `soporta alta resolucion mono a 96 kHz y 24 bits`() {
        val archivo = flacCompleto(
            sampleRate = 96_000,
            canales = 1,
            bitsPorMuestra = 24,
            muestrasTotales = 288_000
        )

        val info = FlacStreamReader.leer(archivo.inputStream(), archivo.length())!!

        assertEquals(96_000, info.sampleRate)
        assertEquals(1, info.canales)
        assertEquals(24, info.bitsPorMuestra)
        assertEquals(2_304_000L, info.bitRateSinComprimirBps)
    }

    @Test
    fun `ignora un ID3v2 antepuesto y encuentra la marca`() {
        val id3 = ByteArray(64) { 0x00 }
        val stream = flacBytes(
            sampleRate = 48000,
            canales = 2,
            bitsPorMuestra = 24,
            muestrasTotales = 480_000,
            bloquesExtra = emptyList()
        )
        val completo = id3 + stream

        val info = FlacStreamReader.leer(completo.inputStream(), completo.size.toLong())!!

        assertEquals(48000, info.sampleRate)
        assertEquals(24, info.bitsPorMuestra)
        // Los 64 bytes del ID3 también cuentan como metadatos.
        assertEquals(64L + 4L + 4L + 34L, info.metadatosBytes)
    }

    @Test
    fun `devuelve null cuando el archivo no es un FLAC`() {
        val random = ByteArray(1024) { 0x42 }

        assertNull(FlacStreamReader.leer(random.inputStream(), random.size.toLong()))
    }

    @Test
    fun `calcula el bitrate exacto del stream`() {
        val flac = FlacStreamReader.leer(
            flacCompleto(
                sampleRate = 44100,
                canales = 2,
                bitsPorMuestra = 16,
                muestrasTotales = 441_000, // 10 segundos
                bloquesExtra = emptyList(),
                audioBytesExtra = 1_000_000
            ).inputStream(),
            (4L + 4L + 34L) + 1_000_000L
        )!!

        // 1_000_000 bytes * 8 / 10 s = 800_000 bps
        assertEquals(800_000L, flac.bitRateBps)
        assertEquals(1_411_200L, flac.bitRateSinComprimirBps)
    }

    /** Genera un archivo temporal con la extensión `.flac`. */
    private fun flacCompleto(
        sampleRate: Int,
        canales: Int,
        bitsPorMuestra: Int,
        muestrasTotales: Long,
        bloquesExtra: List<Pair<Int, Int>> = emptyList(),
        audioBytesExtra: Int = 0
    ): java.io.File {
        val bytes = flacBytes(sampleRate, canales, bitsPorMuestra, muestrasTotales, bloquesExtra) +
            ByteArray(audioBytesExtra)

        val archivo = java.io.File.createTempFile("test", ".flac")
        archivo.deleteOnExit()
        archivo.writeBytes(bytes)
        return archivo
    }

    /** Serializa un FLAC mínimo pero válido a nivel de cabecera. */
    private fun flacBytes(
        sampleRate: Int,
        canales: Int,
        bitsPorMuestra: Int,
        muestrasTotales: Long,
        bloquesExtra: List<Pair<Int, Int>>
    ): ByteArray {
        val salida = ByteArrayOutputStream()
        salida.write(byteArrayOf(0x66, 0x4C, 0x61, 0x43)) // "fLaC"

        val streamInfo = ByteArray(34)
        val empaquetado = (sampleRate.toLong() shl 44) or
            ((canales - 1).toLong() shl 41) or
            ((bitsPorMuestra - 1).toLong() shl 36) or
            muestrasTotales
        for (indice in 0 until 8) {
            streamInfo[10 + indice] = (empaquetado ushr (8 * (7 - indice)) and 0xFF).toByte()
        }

        val esUltimoBloque = bloquesExtra.isEmpty()
        escribirBloque(salida, tipo = 0, datos = streamInfo, ultimo = esUltimoBloque)

        bloquesExtra.forEachIndexed { indice, (tipo, tamano) ->
            escribirBloque(
                salida,
                tipo = tipo,
                datos = ByteArray(tamano),
                ultimo = indice == bloquesExtra.lastIndex
            )
        }

        return salida.toByteArray()
    }

    private fun escribirBloque(
        salida: ByteArrayOutputStream,
        tipo: Int,
        datos: ByteArray,
        ultimo: Boolean
    ) {
        salida.write((if (ultimo) 0x80 else 0x00) or (tipo and 0x7F))
        salida.write((datos.size ushr 16) and 0xFF)
        salida.write((datos.size ushr 8) and 0xFF)
        salida.write(datos.size and 0xFF)
        salida.write(datos)
    }
}