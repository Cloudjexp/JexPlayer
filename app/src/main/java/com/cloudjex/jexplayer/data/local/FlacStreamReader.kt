package com.cloudjex.jexplayer.data.local

import java.io.File
import java.io.InputStream

/**
 * Información exacta del stream de audio de un archivo FLAC.
 *
 * jAudioTagger calcula el bitrate dividiendo el tamaño *total* del archivo entre
 * la duración, lo que infla el resultado con los metadatos (portadas, tags). Estos
 * valores se leen del bloque `STREAMINFO`, que es la fuente de verdad del formato.
 *
 * @param sampleRate frecuencia de muestreo en Hz.
 * @param canales número de canales.
 * @param bitsPorMuestra bits por muestra (16, 24, ...).
 * @param muestrasTotales total de muestras de audio del stream.
 * @param audioBytes bytes ocupados por los frames de audio, sin metadatos.
 * @param metadatosBytes bytes ocupados por la cabecera y los bloques de metadatos.
 */
data class FlacStreamInfo(
    val sampleRate: Int,
    val canales: Int,
    val bitsPorMuestra: Int,
    val muestrasTotales: Long,
    val audioBytes: Long,
    val metadatosBytes: Long
) {
    /** Duración exacta en segundos, derivada de las muestras totales. */
    val duracionSegundos: Double
        get() = if (sampleRate > 0) muestrasTotales.toDouble() / sampleRate else 0.0

    /** Bitrate promedio real del stream comprimido, en bits por segundo. */
    val bitRateBps: Long
        get() = if (duracionSegundos > 0.0) (audioBytes * 8.0 / duracionSegundos).toLong() else 0L

    /** Bitrate que tendría el audio sin comprimir, en bits por segundo. */
    val bitRateSinComprimirBps: Long
        get() = sampleRate.toLong() * canales * bitsPorMuestra

    /** Relación de compresión respecto al audio sin comprimir (0..1). */
    val factorCompresion: Double
        get() = if (bitRateSinComprimirBps > 0) bitRateBps.toDouble() / bitRateSinComprimirBps else 0.0
}

/**
 * Lee la cabecera FLAC y el bloque `STREAMINFO` sin depender de jAudioTagger.
 *
 * El formato es: marca `fLaC`, seguida de una cadena de bloques de metadatos. Cada
 * bloque tiene un byte de cabecera (1 bit de "último bloque" + 7 bits de tipo) y
 * 3 bytes con la longitud de sus datos. El bloque de tipo 0 es `STREAMINFO` y sus
 * primeros 18 bytes contienen los parámetros del stream.
 */
object FlacStreamReader {

    private val MAGIC = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"

    /** Ventana máxima donde se busca la marca, por si hay un ID3v2 antepuesto. */
    private const val VENTANA_MAGIC = 256 * 1024

    /** Devuelve la información del stream, o `null` si el archivo no es un FLAC legible. */
    fun leer(archivo: File): FlacStreamInfo? {
        if (!archivo.isFile || archivo.length() <= 0L) return null
        return try {
            archivo.inputStream().buffered().use { leer(it, archivo.length()) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Variante sobre [InputStream] para poder probar el parser sin archivos.
     *
     * @param tamanoArchivo tamaño total en bytes, usado para descontar metadatos.
     */
    fun leer(entrada: InputStream, tamanoArchivo: Long): FlacStreamInfo? {
        val desplazamiento = buscarMagic(entrada) ?: return null

        var metadatosBytes = (desplazamiento + MAGIC.size).toLong()
        var streamInfo: FlacStreamInfo? = null

        while (true) {
            val cabecera = entrada.read()
            if (cabecera < 0) break

            val esUltimo = (cabecera and 0x80) != 0
            val tipo = cabecera and 0x7F
            val longitud = leerEntero(entrada, 3) ?: break
            val longitudValida = longitud >= 0

            metadatosBytes += 4L + longitud.toLong()

            if (tipo == 0 && longitudValida && longitud >= 18) {
                val bloque = ByteArray(18)
                if (!leerCompleto(entrada, bloque)) break
                streamInfo = parseStreamInfo(bloque)
                if (longitud > 18) saltar(entrada, (longitud - 18).toLong())
            } else if (longitudValida) {
                saltar(entrada, longitud.toLong())
            } else {
                break
            }

            if (esUltimo) break
        }

        // Los metadatos totales solo se conocen al terminar de recorrer los bloques,
        // así que los bytes de audio se calculan recién aquí.
        return streamInfo?.let { info ->
            val audioBytes = (tamanoArchivo - metadatosBytes).coerceAtLeast(0L)
            info.copy(metadatosBytes = metadatosBytes, audioBytes = audioBytes)
        }
    }

    /**
     * Recorre la entrada hasta encontrar la marca `fLaC`.
     *
     * @return el offset donde empieza la marca, o `null` si no aparece.
     */
    private fun buscarMagic(entrada: InputStream): Int? {
        var offset = 0
        var coincidencias = 0

        while (offset < VENTANA_MAGIC) {
            val byte = entrada.read()
            if (byte < 0) return null

            if (byte.toByte() == MAGIC[coincidencias]) {
                coincidencias++
                if (coincidencias == MAGIC.size) return offset - (MAGIC.size - 1)
            } else {
                // Reintenta desde el inicio si el byte actual vuelve a coincidir.
                coincidencias = if (byte.toByte() == MAGIC[0]) 1 else 0
            }
            offset++
        }
        return null
    }

    /** Extrae sample rate, canales, bits y muestras totales de los primeros 18 bytes. */
    private fun parseStreamInfo(bloque: ByteArray): FlacStreamInfo? {
        // Los bytes 10..17 del STREAMINFO empaquetan exactamente 64 bits:
        // 20 de sample rate, 3 de canales, 5 de bits por muestra y 36 de muestras.
        var empaquetado = 0L
        for (indice in 10..17) {
            empaquetado = (empaquetado shl 8) or (bloque[indice].toLong() and 0xFF)
        }

        val sampleRate = ((empaquetado ushr 44) and 0xFFFFF).toInt()
        val canales = ((empaquetado ushr 41) and 0x7).toInt() + 1
        val bitsPorMuestra = ((empaquetado ushr 36) and 0x1F).toInt() + 1
        val muestrasTotales = empaquetado and 0xFFFFFFFFFL

        if (sampleRate <= 0 || canales <= 0 || muestrasTotales <= 0L) return null

        return FlacStreamInfo(
            sampleRate = sampleRate,
            canales = canales,
            bitsPorMuestra = bitsPorMuestra,
            muestrasTotales = muestrasTotales,
            audioBytes = 0L, // se completa al terminar de recorrer los metadatos
            metadatosBytes = 0L
        )
    }

    private fun leerEntero(entrada: InputStream, bytes: Int): Int? {
        var valor = 0
        repeat(bytes) {
            val byte = entrada.read()
            if (byte < 0) return null
            valor = (valor shl 8) or (byte and 0xFF)
        }
        return valor
    }

    private fun leerCompleto(entrada: InputStream, destino: ByteArray): Boolean {
        var leidos = 0
        while (leidos < destino.size) {
            val cantidad = entrada.read(destino, leidos, destino.size - leidos)
            if (cantidad < 0) return false
            leidos += cantidad
        }
        return true
    }

    private fun saltar(entrada: InputStream, bytes: Long) {
        var restantes = bytes
        while (restantes > 0) {
            val salto = entrada.skip(restantes)
            if (salto <= 0) {
                if (entrada.read() < 0) return
                restantes--
            } else {
                restantes -= salto
            }
        }
    }
}