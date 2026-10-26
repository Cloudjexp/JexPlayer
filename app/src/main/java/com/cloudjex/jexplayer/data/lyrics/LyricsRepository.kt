package com.cloudjex.jexplayer.data.lyrics

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lee las letras incrustadas o los sidecars SAF y las convierte en un
 * [LyricsDocument].
 *
 * La lectura y el parseo siempre ocurren fuera del hilo principal. La fuente
 * seleccionada puede fallar (archivo borrado, XML inválido); en ese caso el
 * ViewModel decide si prueba otra fuente.
 */
class LyricsRepository(private val context: Context) {

    fun embeddedSource(text: String?): LyricsSource? {
        val contenido = text?.trim().orEmpty()
        if (contenido.isEmpty()) return null

        val formato = LyricsParser.detectFormat(contenido) ?: return null
        return LyricsSource(
            id = EMBEDDED_LYRICS_SOURCE_ID,
            label = "Letra del archivo",
            format = formato,
            isEmbedded = true
        )
    }

    fun sourceFromMatch(match: LyricsMatch): LyricsSource = LyricsSource(
        id = match.sidecar.uri,
        label = match.sidecar.displayName,
        format = match.sidecar.format,
        uri = match.sidecar.uri,
        language = match.language,
        folderHint = match.sidecar.parentDocumentId?.let { padre ->
            padre.substringAfterLast('/')
                .substringAfterLast(':')
                .takeIf { it.isNotBlank() }
                ?: padre
        }
    )

    suspend fun load(
        source: LyricsSource,
        embeddedText: String?
    ): LyricsDocument? = withContext(Dispatchers.IO) {
        val texto = if (source.isEmbedded) {
            embeddedText
        } else {
            source.uri?.let(::leerTexto)
        }

        if (texto.isNullOrBlank()) return@withContext null

        val lineas = LyricsParser.parse(texto, source.format)
            .filter { it.texto.isNotBlank() }
        if (lineas.isEmpty()) return@withContext null

        val formatoReal = LyricsParser.detectFormat(texto) ?: source.format
        val preparada = BilingualLyrics.prepare(
            lineas = lineas,
            esLrcSimple = formatoReal == LyricsFormat.LRC
        )

        LyricsDocument(
            source = source,
            lineas = preparada.lineas,
            tieneTiemposPorPalabra = LyricsParser.hasWordTiming(preparada.lineas),
            translationSupport = preparada.support
        )
    }

    private fun leerTexto(uriString: String): String? = runCatching {
        val uri = Uri.parse(uriString)
        context.contentResolver.openInputStream(uri)?.use { entrada ->
            val bytes = entrada.readBytes()
            if (bytes.size > TAMANO_MAXIMO_BYTES) return@use null
            decodificar(bytes)
        }
    }.getOrNull()

    /** Respeta el BOM de archivos UTF-16 y UTF-8, comunes en letras antiguas. */
    private fun decodificar(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
        }

        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }

        return String(bytes, Charsets.UTF_8)
    }

    private companion object {
        const val TAMANO_MAXIMO_BYTES = 4 * 1024 * 1024
    }
}
