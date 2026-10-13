package com.cloudjex.jexplayer.data.local

import android.content.Context
import com.cloudjex.jexplayer.data.model.CachedSong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Caché persistente de la biblioteca en un archivo JSON privado de la app.
 *
 * - Escritura atómica: se escribe en un temporal y se sustituye el archivo
 *   anterior con un `rename` (con copia de respaldo si el rename falla).
 * - Lectura tolerante: archivo ausente, corrupto o con versión incompatible
 *   devuelve una lista vacía sin bloquear ni propagar el error.
 * - La E/S se realiza siempre fuera del hilo principal.
 */
internal class LibraryCache(
    private val file: File,
    private val codec: LibraryCacheCodec = LibraryCacheCodec(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun load(): List<CachedSong> = withContext(ioDispatcher) {
        runCatching {
            if (!file.exists()) return@runCatching emptyList()
            codec.decode(file.readText())
        }.getOrDefault(emptyList())
    }

    suspend fun save(songs: List<CachedSong>): Unit = withContext(ioDispatcher) {
        runCatching {
            val parent = file.parentFile
            parent?.mkdirs()

            val temporary = File(parent, "${file.name}.tmp")
            temporary.writeText(codec.encode(songs))

            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }
        Unit
    }

    companion object {
        private const val FILE_NAME = "library_cache.json"

        fun from(context: Context): LibraryCache =
            LibraryCache(File(context.filesDir, FILE_NAME))
    }
}
