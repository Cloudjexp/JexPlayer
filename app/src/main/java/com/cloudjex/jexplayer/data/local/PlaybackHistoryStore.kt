package com.cloudjex.jexplayer.data.local

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Una reproducción registrada: la canción y el instante en que sonó. */
internal data class PlaybackHistoryEntry(
    val songId: String,
    val playedAt: Long
)

/**
 * Historial de reproducción propio, independiente de [LibraryCache].
 *
 * - Máximo [MAX_ENTRIES] canciones únicas: repetir una canción la sube al frente
 *   y actualiza su marca de tiempo en lugar de duplicarla.
 * - Los identificadores que ya no existen en la biblioteca se conservan en disco
 *   y se descartan al proyectar el historial, así una carpeta recuperada no
 *   pierde su historial.
 * - La lectura y la escritura ocurren fuera del hilo principal, serializadas por
 *   un [Mutex] y con sustitución atómica del archivo.
 */
internal class PlaybackHistoryStore(
    private val file: File,
    private val codec: PlaybackHistoryCodec = PlaybackHistoryCodec(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val limit: Int = MAX_ENTRIES
) {

    private val mutex = Mutex()
    private var loaded = false
    private val _entries = MutableStateFlow<List<PlaybackHistoryEntry>>(emptyList())

    /** Más reciente primero. */
    val entries: StateFlow<List<PlaybackHistoryEntry>> = _entries.asStateFlow()

    suspend fun load(): List<PlaybackHistoryEntry> = withContext(ioDispatcher) {
        mutex.withLock {
            ensureLoadedLocked()
            _entries.value
        }
    }

    suspend fun record(songId: String, playedAt: Long = System.currentTimeMillis()) {
        if (songId.isBlank()) return
        withContext(ioDispatcher) {
            mutex.withLock {
                ensureLoadedLocked()
                val updated = ArrayList<PlaybackHistoryEntry>(limit.coerceAtLeast(1))
                updated.add(PlaybackHistoryEntry(songId, playedAt))
                _entries.value.forEach { entry ->
                    if (entry.songId != songId && updated.size < limit) updated.add(entry)
                }
                _entries.value = updated
                persistLocked(updated)
            }
        }
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true
        _entries.value = runCatching {
            if (!file.exists()) emptyList()
            else codec.decode(file.readText()).take(limit)
        }.getOrDefault(emptyList())
    }

    private fun persistLocked(entries: List<PlaybackHistoryEntry>) {
        runCatching {
            val parent = file.parentFile
            parent?.mkdirs()
            val temporary = File(parent, "${file.name}.tmp")
            temporary.writeText(codec.encode(entries))
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }
    }

    companion object {
        const val MAX_ENTRIES = 20
        private const val FILE_NAME = "playback_history.json"

        fun from(context: Context): PlaybackHistoryStore =
            PlaybackHistoryStore(File(context.filesDir, FILE_NAME))
    }
}

/**
 * Formato JSON mínimo escrito y leído a mano para no depender de `org.json`
 * en las pruebas JVM, igual que el resto de la persistencia local.
 */
internal class PlaybackHistoryCodec(private val version: Int = CURRENT_VERSION) {

    fun encode(entries: List<PlaybackHistoryEntry>): String = buildString {
        append("{\"version\":").append(version).append(",\"entries\":[")
        entries.forEachIndexed { index, entry ->
            if (index > 0) append(',')
            append("{\"id\":").append(MiniJson.writeString(entry.songId))
            append(",\"playedAt\":").append(entry.playedAt).append('}')
        }
        append("]}")
    }

    fun decode(text: String): List<PlaybackHistoryEntry> = runCatching {
        val root = MiniJson.parse(text) as? Map<*, *> ?: return emptyList()
        if ((root["version"] as? Long)?.toInt() != version) return emptyList()
        val raw = root["entries"] as? List<*> ?: return emptyList()
        raw.mapNotNull { item ->
            val entry = item as? Map<*, *> ?: return@mapNotNull null
            val songId = entry["id"] as? String ?: return@mapNotNull null
            val playedAt = entry["playedAt"] as? Long ?: return@mapNotNull null
            if (songId.isBlank()) null else PlaybackHistoryEntry(songId, playedAt)
        }
    }.getOrDefault(emptyList())

    companion object {
        const val CURRENT_VERSION = 1
    }
}
