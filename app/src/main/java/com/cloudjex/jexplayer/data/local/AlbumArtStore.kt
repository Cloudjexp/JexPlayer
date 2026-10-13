package com.cloudjex.jexplayer.data.local

import android.content.Context
import java.io.File
import java.security.MessageDigest

class AlbumArtStore(private val directory: File) {

    fun fileFor(songId: String, size: Long?, lastModified: Long?): File =
        File(directory, "${key(songId, size, lastModified)}.$EXTENSION")

    fun exists(songId: String, size: Long?, lastModified: Long?): Boolean =
        fileFor(songId, size, lastModified).isFile

    fun store(songId: String, size: Long?, lastModified: Long?, bytes: ByteArray): File? =
        runCatching {
            directory.mkdirs()
            val target = fileFor(songId, size, lastModified)
            val temporary = File(directory, "${target.name}.$TEMP_EXTENSION")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            target
        }.getOrNull()
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

    private fun fileNameOf(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        return uri.substringAfterLast('/').takeIf { it.isNotBlank() && it.contains('.') }
    }

    companion object {
        private const val DIRECTORY_NAME = "album_art"
        private const val EXTENSION = "img"
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
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(Charsets.UTF_8))

            val builder = StringBuilder(32)
            for (index in 0 until 16) {
                val value = digest[index].toInt()
                builder.append(HEX[(value ushr 4) and 0xF]).append(HEX[value and 0xF])
            }
            return builder.toString()
        }
    }
}
