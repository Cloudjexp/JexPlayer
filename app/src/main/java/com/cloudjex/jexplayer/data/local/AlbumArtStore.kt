package com.cloudjex.jexplayer.data.local

import android.content.Context
import java.io.File
import java.net.URI
import java.security.MessageDigest

/**
 * Almacén de portadas extraídas de los archivos de audio.
 *
 * Las portadas nuevas se guardan por contenido (`sha-<hash>.img`): dos canciones
 * del mismo álbum comparten un único archivo aunque el extractor entregue una
 * copia de los mismos bytes. Esto es importante para Coil, porque la clave de su
 * caché en memoria se deriva del archivo: antes cada pista decodificaba la misma
 * imagen por separado y llenaba la caché con duplicados.
 *
 * Se conservan los métodos antiguos basados en la identidad de la canción para
 * poder migrar cachés ya existentes sin volver a leer los archivos de audio.
 */
class AlbumArtStore(private val directory: File) {

    /** Ruta usada por versiones anteriores (una portada por canción). */
    fun fileFor(songId: String, size: Long?, lastModified: Long?): File =
        File(directory, "${key(songId, size, lastModified)}.$EXTENSION")

    fun exists(songId: String, size: Long?, lastModified: Long?): Boolean =
        fileFor(songId, size, lastModified).isFile

    /** Escritura antigua, conservada para compatibilidad con pruebas y datos previos. */
    fun store(songId: String, size: Long?, lastModified: Long?, bytes: ByteArray): File? {
        val target = fileFor(songId, size, lastModified)
        return writeAtomically(target, bytes)
    }

    /**
     * Guarda la portada una sola vez por contenido. Es idempotente y seguro ante
     * llamadas repetidas: si el hash ya existe no se vuelve a escribir.
     */
    fun storeShared(bytes: ByteArray): File? {
        if (bytes.isEmpty()) return null
        val target = File(directory, "$SHARED_PREFIX${contentHash(bytes)}.$EXTENSION")
        if (target.isFile) return target
        return writeAtomically(target, bytes)
    }

    fun existsUri(uri: String?): Boolean = fileFromUri(uri)?.isFile == true

    /**
     * Convierte una portada antigua (una por canción) en una portada compartida
     * por contenido. Devuelve `null` si la URI no es un archivo válido; el
     * llamador debe conservar la URI original en ese caso.
     */
    fun migrateToShared(uri: String?): File? {
        val file = fileFromUri(uri) ?: return null
        if (!file.isFile) return null
        if (file.name.startsWith("$SHARED_PREFIX") && file.name.endsWith(".$EXTENSION")) {
            return file
        }
        if (!file.name.endsWith(".$EXTENSION")) return null

        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        return storeShared(bytes)
    }

    fun prune(referencedArtUris: Collection<String?>): Int {
        val keep = referencedArtUris.mapNotNull(::fileNameOf).toSet()
        val files = directory.listFiles() ?: return 0

        var removed = 0
        files.forEach { file ->
            if (!file.isFile) return@forEach
            val isTemporary = file.name.endsWith(".$TEMP_EXTENSION")
            val isOwnArtwork = file.name.endsWith(".$EXTENSION")
            if ((isTemporary || (isOwnArtwork && file.name !in keep)) && file.delete()) {
                removed++
            }
        }
        return removed
    }

    fun clear(): Int {
        val files = directory.listFiles() ?: return 0
        var removed = 0
        files.forEach { file ->
            if (file.isFile && file.delete()) removed++
        }
        return removed
    }

    private fun writeAtomically(target: File, bytes: ByteArray): File? = runCatching {
        directory.mkdirs()

        // Un temporal único evita que dos escrituras del mismo contenido (por
        // ejemplo, dos canciones de un álbum) compartan el mismo archivo temporal.
        val temporary = File.createTempFile("art-", ".$TEMP_EXTENSION", directory)
        try {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                if (!target.isFile) temporary.copyTo(target, overwrite = true)
            }
            target
        } finally {
            temporary.delete()
        }
    }.getOrNull()

    private fun fileFromUri(uri: String?): File? {
        if (uri.isNullOrBlank()) return null
        return runCatching { File(URI(uri)) }.getOrNull()
    }

    private fun fileNameOf(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        return uri.substringAfterLast('/').takeIf { it.isNotBlank() && it.contains('.') }
    }

    companion object {
        internal const val DIRECTORY_NAME = "album_art"
        internal const val EXTENSION = "img"
        internal const val SHARED_PREFIX = "sha-"
        private const val TEMP_EXTENSION = "tmp"
        private val HEX = "0123456789abcdef".toCharArray()

        fun from(context: Context): AlbumArtStore =
            AlbumArtStore(File(context.filesDir, DIRECTORY_NAME))

        internal fun key(songId: String, size: Long?, lastModified: Long?): String {
            val payload = buildString {
                append(songId)
                append('\u0000').append(size?.let { "s$it" } ?: "s?")
                append('\u0000').append(lastModified?.let { "m$it" } ?: "m?")
            }
            return digest(payload.toByteArray(Charsets.UTF_8)).take(32)
        }

        private fun contentHash(bytes: ByteArray): String = digest(bytes).take(32)

        private fun digest(bytes: ByteArray): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            val builder = StringBuilder(64)
            hash.forEach { value ->
                val intValue = value.toInt()
                builder.append(HEX[(intValue ushr 4) and 0xF])
                    .append(HEX[intValue and 0xF])
            }
            return builder.toString()
        }

        /**
         * Clave estable para la caché de Coil. Las portadas compartidas usan el
         * hash del contenido (sin ruta ni extensión), de modo que todas las
         * pistas de un álbum reutilizan exactamente la misma entrada.
         */
        internal fun contentCacheKey(uri: String): String {
            val name = uri.substringAfterLast('/').substringBefore('?')
            return if (name.startsWith(SHARED_PREFIX) && name.endsWith(".$EXTENSION")) {
                name.substringBeforeLast('.')
            } else {
                uri
            }
        }
    }
}
