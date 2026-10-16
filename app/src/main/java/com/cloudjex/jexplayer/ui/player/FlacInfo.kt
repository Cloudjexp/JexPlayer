package com.cloudjex.jexplayer.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudjex.jexplayer.data.local.FlacStreamReader
import com.cloudjex.jexplayer.data.model.AudioTags
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagTextField
import java.io.File

/**
 * Lectura de etiquetas y metadatos de audio mediante jAudioTagger.
 *
 * La biblioteca se escanea con SAF, por lo que las canciones se referencian como
 * `content://` y no como rutas del sistema de archivos. jAudioTagger solo admite
 * [File] porque trabaja con `RandomAccessFile`, así que el contenido se vuelca a
 * un archivo temporal en la caché antes de analizarlo.
 */
object FlacInfo {

    /**
     * Lee las letras y los metadatos técnicos del audio en [uri].
     *
     * @return un [AudioTags] con toda la información disponible, o `null` si el
     *   archivo no se pudo copiar o leer.
     */
    fun leerTags(context: Context, uri: Uri): AudioTags? {
        val archivo = copiarALaCache(context, uri) ?: return null
        return try {
            val audioFile = runCatching { AudioFileIO.read(archivo) }
                .recoverCatching { AudioFileIO.readMagic(archivo) }
                .getOrElse { error ->
                    error.printStackTrace()
                    return null
                }
            val tag = audioFile.tag
            val header = audioFile.audioHeader

            // Para FLAC preferimos el bloque STREAMINFO: es la fuente de verdad y
            // no arrastra el peso de la portada al calcular el bitrate.
            val flac = FlacStreamReader.leer(archivo)

            AudioTags(
                letras = buscarLetras(tag),
                sampleRate = flac?.sampleRate
                    ?: runCatching { header.sampleRateAsNumber }.getOrDefault(0),
                bitRate = (flac?.bitRateBps
                    ?: runCatching { header.bitRateAsNumber * 1000L }.getOrDefault(0L))
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt(),
                canales = flac?.canales
                    ?: header.channels?.trim()?.toIntOrNull() ?: 0,
                duracionSegundos = flac?.duracionSegundos?.toInt()
                    ?: runCatching { header.trackLength }.getOrDefault(0),
                bitsPorMuestra = flac?.bitsPorMuestra ?: 0,
                muestrasTotales = flac?.muestrasTotales ?: 0L,
                exacto = flac != null,
                todosLosCampos = tag.getFields().asSequence()
                    .associate { campo ->
                        campo.getId() to ((campo as? TagTextField)?.content ?: campo.toString())
                    }
                    .also { campos ->
                        Log.d(
                            "JexTags",
                            "uri=$uri flac=$flac campos=${campos.keys.toList()} " +
                                "bitRate=${flac?.bitRateBps ?: header.bitRateAsNumber}"
                        )
                    }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            archivo.delete()
        }
    }

    /**
     * Busca el texto de las letras en el [Tag].
     *
     * No alcanza con `tag.getFirst(FieldKey.LYRICS)`: esa clave corresponde al
     * frame ID3v2 `USLT`, mientras que los FLAC guardan las letras en un Vorbis
     * Comment con la clave literal `LYRICS`. Según el mutagen y la versión que
     * escribió el archivo, también puede aparecer como `UNSYNCEDLYRICS`.
     */
    private fun buscarLetras(tag: Tag): String? {
        val claves = listOf("LYRICS", "UNSYNCEDLYRICS", "LYRIC")

        val desdeTags = claves.firstNotNullOfOrNull { clave ->
            runCatching { tag.getFirst(clave) }.getOrNull()
        }

        val desdeCampos = claves.firstNotNullOfOrNull { clave ->
            runCatching { tag.getFields(clave) }.getOrNull()
                ?.firstNotNullOfOrNull { campo ->
                    (campo as? TagTextField)?.content?.takeIf { it.isNotBlank() }
                }
        }

        return (desdeTags ?: desdeCampos)?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Vuelca el contenido de [uri] a un archivo temporal en la caché para que
     * jAudioTagger pueda abrirlo. Devuelve `null` si no hay nada que copiar.
     */
    private fun copiarALaCache(context: Context, uri: Uri): File? = try {
        // jAudioTagger elige el lector por la EXTENSIÓN del archivo, así que el
        // temporal debe conservar la extensión original (`.flac`, `.mp3`, ...).
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?.lowercase()
            ?: "tmp"
        val destino = File(context.cacheDir, "audio_${uri.toString().hashCode()}.$extension")

        if (destino.exists() && destino.length() > 0L) {
            destino
        } else {
            context.contentResolver.openInputStream(uri)?.use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            }
            if (destino.length() > 0L) destino else null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}