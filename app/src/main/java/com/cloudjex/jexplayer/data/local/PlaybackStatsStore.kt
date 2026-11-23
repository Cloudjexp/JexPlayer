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
import java.util.Calendar

/*
 * Registro propio de estadísticas. Es independiente de PlaybackHistoryStore:
 * el historial de "Escuchado recientemente" no se borra ni se reescribe, y este
 * archivo nuevo empieza a contar desde la instalación de esta versión.
 */

/** Totales históricos de una canción desde que existe este registro. */
internal data class SongPlaybackStats(
    val playCount: Int = 0,
    val listenedMs: Long = 0L,
    val lastPlayedAt: Long = 0L
)

/**
 * Una reproducción: cuándo empezó y cuánto tiempo se escuchó realmente de ella.
 * Las muestras alimentan los rankings semanales; [SongPlaybackStats] conserva
 * los totales aunque las muestras antiguas se descarten.
 */
internal data class PlaybackSample(
    val songId: String,
    val startedAt: Long,
    val listenedMs: Long = 0L
)

/**
 * Resumen por día y canción. Sobrevive al recorte de [PlaybackSample] para que
 * las ventanas de 30 días y 1 año no pierdan escuchas antiguas.
 */
internal data class DailyPlaybackAggregate(
    val dayStart: Long,
    val songId: String,
    val playCount: Int,
    val listenedMs: Long
)

internal data class PlaybackStatsSnapshot(
    val totals: Map<String, SongPlaybackStats> = emptyMap(),
    val samples: List<PlaybackSample> = emptyList(),
    val daily: List<DailyPlaybackAggregate> = emptyList()
)

/**
 * Persistencia de reproducciones y tiempo escuchado.
 *
 * - `recordPlay` crea una muestra nueva; `addListening` suma el tiempo a la
 *   muestra abierta de esa canción, así una escucha cuenta una sola vez.
 * - Las muestras están acotadas a [maxSamples] (más recientes primero), pero los
 *   totales nunca se recortan: el histórico sobrevive al recorte.
 * - Lectura y escritura fuera del hilo principal, serializadas por un [Mutex] y
 *   con sustitución atómica del archivo, igual que el resto de almacenes.
 */
internal class PlaybackStatsStore(
    private val file: File,
    private val codec: PlaybackStatsCodec = PlaybackStatsCodec(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxSamples: Int = MAX_SAMPLES
) {

    private val mutex = Mutex()
    private var loaded = false
    private val _snapshot = MutableStateFlow(PlaybackStatsSnapshot())

    val snapshot: StateFlow<PlaybackStatsSnapshot> = _snapshot.asStateFlow()

    suspend fun load(): PlaybackStatsSnapshot = withContext(ioDispatcher) {
        mutex.withLock {
            ensureLoadedLocked()
            _snapshot.value
        }
    }

    /** Registra que una canción ha empezado a sonar. */
    suspend fun recordPlay(songId: String, at: Long = System.currentTimeMillis()) {
        if (songId.isBlank()) return
        withContext(ioDispatcher) {
            mutex.withLock {
                ensureLoadedLocked()
                val previous = _snapshot.value.totals[songId] ?: SongPlaybackStats()
                val totals = LinkedHashMap(_snapshot.value.totals).apply {
                    put(
                        songId,
                        previous.copy(
                            playCount = previous.playCount + 1,
                            lastPlayedAt = maxOf(previous.lastPlayedAt, at)
                        )
                    )
                }
                val samples = ArrayList<PlaybackSample>(_snapshot.value.samples.size + 1).apply {
                    add(PlaybackSample(songId, at))
                    addAll(_snapshot.value.samples)
                }
                val daily = _snapshot.value.daily.toMutableList()
                upsertDaily(daily, startOfDay(at), songId, playsDelta = 1, msDelta = 0L)
                publishLocked(totals, samples, daily)
            }
        }
    }

    /**
     * Suma tiempo a la última muestra de esa canción. Si no hay una reproducción
     * registrada se ignora: no inventamos escuchas de antes de esta versión.
     */
    suspend fun addListening(
        songId: String,
        listenedMs: Long,
        at: Long = System.currentTimeMillis()
    ) {
        if (songId.isBlank() || listenedMs <= 0L) return
        withContext(ioDispatcher) {
            mutex.withLock {
                ensureLoadedLocked()
                val samples = _snapshot.value.samples.toMutableList()
                val index = samples.indexOfLast { it.songId == songId }
                val daily = _snapshot.value.daily.toMutableList()
                val hasDaily = daily.any { it.songId == songId }
                if (index < 0 && !hasDaily) return@withLock
                if (index >= 0) {
                    samples[index] = samples[index].copy(
                        listenedMs = samples[index].listenedMs + listenedMs
                    )
                }
                if (hasDaily) {
                    upsertDaily(daily, startOfDay(at), songId, playsDelta = 0, msDelta = listenedMs)
                }
                val previous = _snapshot.value.totals[songId] ?: SongPlaybackStats()
                val totals = LinkedHashMap(_snapshot.value.totals).apply {
                    put(
                        songId,
                        previous.copy(
                            listenedMs = previous.listenedMs + listenedMs,
                            lastPlayedAt = maxOf(previous.lastPlayedAt, at)
                        )
                    )
                }
                publishLocked(totals, samples, daily)
            }
        }
    }

    private fun startOfDay(at: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = at
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun upsertDaily(
        daily: MutableList<DailyPlaybackAggregate>,
        dayStart: Long,
        songId: String,
        playsDelta: Int,
        msDelta: Long
    ) {
        val index = daily.indexOfFirst { it.dayStart == dayStart && it.songId == songId }
        if (index >= 0) {
            val previous = daily[index]
            daily[index] = previous.copy(
                playCount = previous.playCount + playsDelta,
                listenedMs = previous.listenedMs + msDelta
            )
        } else if (playsDelta > 0) {
            daily.add(DailyPlaybackAggregate(dayStart, songId, playsDelta, msDelta))
        }
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true
        _snapshot.value = runCatching {
            if (!file.exists()) PlaybackStatsSnapshot()
            else codec.decode(file.readText())
        }.getOrDefault(PlaybackStatsSnapshot())
    }

    private fun publishLocked(
        totals: Map<String, SongPlaybackStats>,
        samples: List<PlaybackSample>,
        daily: List<DailyPlaybackAggregate>
    ) {
        val bounded = samples.take(maxSamples.coerceAtLeast(1))
        val cutoff = System.currentTimeMillis() - DAILY_RETENTION_DAYS * DAY_MS
        val keptDaily = daily
            .filter { it.dayStart >= cutoff && it.playCount > 0 }
            .take(MAX_DAILY_AGGREGATES)
        _snapshot.value = PlaybackStatsSnapshot(totals, bounded, keptDaily)
        persistLocked(_snapshot.value)
    }

    private fun persistLocked(snapshot: PlaybackStatsSnapshot) {
        runCatching {
            val parent = file.parentFile
            parent?.mkdirs()
            val temporary = File(parent, "${file.name}.tmp")
            temporary.writeText(codec.encode(snapshot))
            if (!temporary.renameTo(file)) {
                temporary.copyTo(file, overwrite = true)
                temporary.delete()
            }
        }
    }

    companion object {
        /** Muestras detalladas: cubren la ventana semanal con holgura. */
        const val MAX_SAMPLES = 2_000
        private const val DAY_MS = 24L * 60 * 60 * 1_000L
        private const val DAILY_RETENTION_DAYS = 400L
        private const val MAX_DAILY_AGGREGATES = 100_000
        private const val FILE_NAME = "playback_stats.json"

        fun from(context: Context): PlaybackStatsStore =
            PlaybackStatsStore(File(context.filesDir, FILE_NAME))
    }
}

/**
 * JSON mínimo escrito a mano, como [PlaybackHistoryCodec], para que las pruebas
 * sean JVM puras y no dependan de `org.json`.
 */
internal class PlaybackStatsCodec(private val version: Int = CURRENT_VERSION) {

    fun encode(snapshot: PlaybackStatsSnapshot): String = buildString {
        append("{\"version\":").append(version).append(",\"totals\":{")
        snapshot.totals.entries.forEachIndexed { index, (songId, stats) ->
            if (index > 0) append(',')
            append(MiniJson.writeString(songId)).append(":{")
            append("\"plays\":").append(stats.playCount)
            append(",\"ms\":").append(stats.listenedMs)
            append(",\"lastAt\":").append(stats.lastPlayedAt)
            append('}')
        }
        append("},\"samples\":[")
        snapshot.samples.forEachIndexed { index, sample ->
            if (index > 0) append(',')
            append("{\"id\":").append(MiniJson.writeString(sample.songId))
            append(",\"at\":").append(sample.startedAt)
            append(",\"ms\":").append(sample.listenedMs)
            append('}')
        }
        append("],\"daily\":[")
        snapshot.daily.forEachIndexed { index, day ->
            if (index > 0) append(',')
            append("{\"d\":").append(day.dayStart)
            append(",\"id\":").append(MiniJson.writeString(day.songId))
            append(",\"p\":").append(day.playCount)
            append(",\"ms\":").append(day.listenedMs)
            append('}')
        }
        append("]}")
    }

    fun decode(text: String): PlaybackStatsSnapshot = runCatching {
        val root = MiniJson.parse(text) as? Map<*, *> ?: return PlaybackStatsSnapshot()
        if ((root["version"] as? Long)?.toInt() != version) return PlaybackStatsSnapshot()

        val totals = LinkedHashMap<String, SongPlaybackStats>()
        val rawTotals = root["totals"] as? Map<*, *> ?: emptyMap<String, Any?>()
        rawTotals.forEach { (key, value) ->
            val songId = key as? String ?: return@forEach
            val item = value as? Map<*, *> ?: return@forEach
            if (songId.isBlank()) return@forEach
            val plays = (item["plays"] as? Long)?.toInt() ?: 0
            val listenedMs = item["ms"] as? Long ?: 0L
            val lastPlayedAt = item["lastAt"] as? Long ?: 0L
            totals[songId] = SongPlaybackStats(
                playCount = plays.coerceAtLeast(0),
                listenedMs = listenedMs.coerceAtLeast(0L),
                lastPlayedAt = lastPlayedAt
            )
        }

        val samples = ArrayList<PlaybackSample>()
        val rawSamples = root["samples"] as? List<*> ?: emptyList<Any?>()
        rawSamples.forEach { value ->
            val item = value as? Map<*, *> ?: return@forEach
            val songId = item["id"] as? String ?: return@forEach
            val startedAt = item["at"] as? Long ?: return@forEach
            val listenedMs = item["ms"] as? Long ?: 0L
            if (songId.isNotBlank()) {
                samples.add(PlaybackSample(songId, startedAt, listenedMs.coerceAtLeast(0L)))
            }
        }

        val daily = ArrayList<DailyPlaybackAggregate>()
        val rawDaily = root["daily"] as? List<*> ?: emptyList<Any?>()
        rawDaily.forEach { value ->
            val item = value as? Map<*, *> ?: return@forEach
            val dayStart = item["d"] as? Long ?: return@forEach
            val songId = item["id"] as? String ?: return@forEach
            val plays = (item["p"] as? Long)?.toInt() ?: return@forEach
            val listenedMs = item["ms"] as? Long ?: 0L
            if (songId.isNotBlank()) {
                daily.add(
                    DailyPlaybackAggregate(
                        dayStart,
                        songId,
                        plays.coerceAtLeast(0),
                        listenedMs.coerceAtLeast(0L)
                    )
                )
            }
        }

        PlaybackStatsSnapshot(totals, samples, daily)
    }.getOrDefault(PlaybackStatsSnapshot())

    companion object {
        const val CURRENT_VERSION = 1
    }
}

/** Tramo de escucha ya cerrado, listo para persistir. */
internal data class ListeningChunk(val songId: String, val listenedMs: Long)

/**
 * Mide el tiempo escuchado a partir de los avances de posición del reproductor,
 * no del reloj: así no cuenta pausas, buffering ni saltos.
 *
 * Los saltos hacia delante se limitan a [maxStepMs] y los hacia atrás se
 * descartan (un seek no es tiempo escuchado).
 */
internal class ListeningTracker(private val maxStepMs: Long = 2_000L) {

    private var songId: String? = null
    private var lastPositionMs = 0L
    private var accumulatedMs = 0L
    private var totalForCurrentSongMs = 0L

    /** Cierra el tramo anterior y empieza a medir [songId] desde [positionMs]. */
    fun switchTo(songId: String?, positionMs: Long): ListeningChunk? {
        val finished = take()
        this.songId = songId?.takeIf { it.isNotBlank() }
        lastPositionMs = positionMs.coerceAtLeast(0L)
        accumulatedMs = 0L
        totalForCurrentSongMs = 0L
        return finished
    }

    fun onSample(songId: String?, positionMs: Long, isPlaying: Boolean) {
        val id = songId?.takeIf { it.isNotBlank() }
        val position = positionMs.coerceAtLeast(0L)
        if (id == null || !isPlaying) return
        if (id != this.songId) {
            this.songId = id
            lastPositionMs = position
            accumulatedMs = 0L
            totalForCurrentSongMs = 0L
            return
        }
        val delta = position - lastPositionMs
        lastPositionMs = position
        if (delta <= 0L) return
        val listened = minOf(delta, maxStepMs)
        accumulatedMs += listened
        totalForCurrentSongMs += listened
    }

    /** Tras un seek, la nueva posición pasa a ser la referencia. */
    fun onSeek(positionMs: Long) {
        lastPositionMs = positionMs.coerceAtLeast(0L)
    }

    /** Tiempo escuchado de la canción actual, incluidos los tramos ya volcados. */
    fun currentSongListenedMs(): Long = totalForCurrentSongMs

    /** Tramo acumulado sin consumirlo; permite decidir cuándo volcar a disco. */
    fun peek(): ListeningChunk? {
        val id = songId ?: return null
        if (accumulatedMs <= 0L) return null
        return ListeningChunk(id, accumulatedMs)
    }

    /** Devuelve y reinicia el tramo acumulado. */
    fun take(): ListeningChunk? {
        val chunk = peek() ?: return null
        accumulatedMs = 0L
        return chunk
    }
}

/**
 * Reglas de scrobble de last.fm: la pista debe durar más de 30 segundos y
 * contarse cuando se haya escuchado la mitad o 4 minutos, lo que ocurra antes.
 * Las pistas cortas nunca cuentan como reproducción.
 */
internal object ScrobbleRule {
    const val MIN_TRACK_DURATION_MS = 30_000L
    const val MAX_LISTEN_BEFORE_SCROBBLE_MS = 4 * 60 * 1_000L

    /** `null` significa que esa pista no es elegible. */
    fun thresholdFor(durationMs: Long): Long? {
        if (durationMs <= MIN_TRACK_DURATION_MS) return null
        return minOf(durationMs / 2, MAX_LISTEN_BEFORE_SCROBBLE_MS)
    }
}
