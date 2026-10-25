package com.cloudjex.jexplayer.data.local

import android.content.Context
import com.cloudjex.jexplayer.data.model.CustomLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Resultado de leer el archivo de bibliotecas personalizadas. */
internal data class CustomLibrariesLoad(
    val libraries: List<CustomLibrary>,
    val corrupted: Boolean
)

/**
 * Persistencia de las bibliotecas personalizadas en un archivo JSON privado.
 *
 * - Lectura tolerante: si el archivo no existe devuelve una colección vacía; si
 *   está corrupto conserva una copia y lo indica mediante [CustomLibrariesLoad].
 * - Escritura atómica: se escribe en un temporal y se sustituye el archivo
 *   anterior con un `rename`, con copia de respaldo si el rename falla.
 * - La E/S se realiza siempre fuera del hilo principal.
 */
internal class CustomLibrariesStore(
    private val file: File,
    private val codec: CustomLibrariesCodec = CustomLibrariesCodec(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun load(): CustomLibrariesLoad = withContext(ioDispatcher) {
        if (!file.exists()) {
            return@withContext CustomLibrariesLoad(
                libraries = emptyList(),
                corrupted = false
            )
        }

        try {
            CustomLibrariesLoad(
                libraries = codec.decode(file.readText()),
                corrupted = false
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            backupCorruptFile()
            CustomLibrariesLoad(
                libraries = emptyList(),
                corrupted = true
            )
        }
    }

    suspend fun save(libraries: List<CustomLibrary>): Boolean = withContext(ioDispatcher) {
        var temporary: File? = null

        try {
            val parent = file.parentFile
            parent?.mkdirs()

            val temp = File(parent, "${file.name}.$TEMP_EXTENSION")
            temporary = temp
            temp.writeText(codec.encode(libraries))

            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
            }
            true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        } finally {
            temporary?.delete()
        }
    }

    /**
     * Conserva el archivo ilegible sin sobrescribir una copia anterior. Así el
     * usuario no pierde la única evidencia de sus datos si la corrupción ocurre
     * más de una vez.
     */
    private fun backupCorruptFile() {
        if (!file.exists()) return

        val backup = File(file.parentFile, CORRUPT_BACKUP_NAME)
        if (backup.exists()) return

        runCatching {
            file.copyTo(backup, overwrite = false)
        }
    }

    companion object {
        private const val FILE_NAME = "custom_libraries.json"
        private const val CORRUPT_BACKUP_NAME = "custom_libraries.corrupt.json"
        private const val TEMP_EXTENSION = "tmp"

        fun from(context: Context): CustomLibrariesStore =
            CustomLibrariesStore(File(context.filesDir, FILE_NAME))
    }
}
