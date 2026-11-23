package com.cloudjex.jexplayer.viewmodel

import com.cloudjex.jexplayer.data.local.DailyPlaybackAggregate
import com.cloudjex.jexplayer.data.local.PlaybackHistoryEntry
import com.cloudjex.jexplayer.data.local.PlaybackSample
import com.cloudjex.jexplayer.data.local.PlaybackStatsSnapshot
import com.cloudjex.jexplayer.data.local.SongPlaybackStats
import com.cloudjex.jexplayer.data.model.CachedSong
import com.cloudjex.jexplayer.data.model.Song
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/*
 * Transformaciones puras del panel. Trabajan sobre [SongEntry] y [CachedSong],
 * que no contienen tipos de Android, para que toda la lógica se pueda probar en
 * JVM. La resolución a [Song] (reproducción y portadas) ocurre en la UI con un
 * mapa de la biblioteca que ya está en memoria.
 */

internal const val UNKNOWN_ARTIST_LABEL = "Artista desconocido"
internal const val UNKNOWN_ALBUM_LABEL = "Álbum desconocido"

// El escáner guarda literalmente "Unknown" cuando no hay metadatos de artista.
private const val UNKNOWN_ARTIST_VALUE = "unknown"
private const val RECENTLY_PLAYED_LIMIT = 20
private const val RECENTLY_ADDED_LIMIT = 5
private const val RANKING_LIMIT = 5
private const val MINUTE_MS = 60_000L
private const val DAY_MS = 24L * 60 * 60 * 1_000L
private const val INSIGHT_CHART_DAYS = 30
private const val TOP_GENRE_LIMIT = 5

/** Ventanas móviles: el periodo siempre incluye el día de hoy. */
enum class StatsPeriod(val days: Long?) {
    WEEK(7L),
    MONTH(30L),
    YEAR(365L),
    ALL_TIME(null)
}

fun greetingFor(hour: Int): String = when (hour) {
    in 6..11 -> "Buenos días"
    in 12..19 -> "Buenas tardes"
    else -> "Buenas noches"
}

/** Canción reducida a lo que el panel necesita para agrupar y ordenar. */
data class SongEntry(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String = ""
)

fun Song.toEntry(): SongEntry = SongEntry(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    genre = genre
)

/**
 * Elemento del carrusel "Escuchado recientemente". Hoy el historial solo
 * contiene canciones, así que únicamente se produce [SongItem]; las variantes de
 * álbum y artista quedan declaradas para cuando el historial las registre y
 * nunca se fabrican a partir de una canción.
 */
sealed interface DashboardMediaItem {
    val key: String

    data class SongItem(val entry: SongEntry) : DashboardMediaItem {
        override val key: String = "song:${entry.id}"
    }

    data class AlbumItem(val albumKey: String) : DashboardMediaItem {
        override val key: String = "album:$albumKey"
    }

    data class ArtistItem(val artistKey: String) : DashboardMediaItem {
        override val key: String = "artist:$artistKey"
    }
}

/** Historial proyectado sobre la biblioteca actual, más reciente primero. */
internal fun recentlyPlayedItems(
    entries: List<PlaybackHistoryEntry>,
    entriesById: Map<String, SongEntry>,
    limit: Int = RECENTLY_PLAYED_LIMIT
): List<DashboardMediaItem> = entries
    .sortedByDescending { it.playedAt }
    .mapNotNull { entry -> dashboardItemFor(entry, entriesById) }
    .distinctBy { it.key }
    .take(limit)

/**
 * Clasifica una entrada del historial. Un identificador que ya no está en la
 * biblioteca no produce elemento: el historial se conserva en disco, pero no se
 * muestra contenido que el usuario no puede abrir.
 */
internal fun dashboardItemFor(
    entry: PlaybackHistoryEntry,
    entriesById: Map<String, SongEntry>
): DashboardMediaItem? {
    val song = entriesById[entry.songId] ?: return null
    return DashboardMediaItem.SongItem(song)
}

/**
 * Ordena por fecha de modificación del archivo (no es una fecha de
 * incorporación real a la biblioteca). Las entradas sin marca se excluyen.
 */
fun recentlyAddedSongs(
    cached: List<CachedSong>,
    limit: Int = RECENTLY_ADDED_LIMIT
): List<CachedSong> = cached
    .asSequence()
    .filter { it.lastModified != null }
    .distinctBy { it.id }
    .sortedByDescending { it.lastModified ?: Long.MIN_VALUE }
    .take(limit)
    .toList()

data class LibraryStats(val songCount: Int, val artistCount: Int)

fun libraryStatsFor(entries: List<SongEntry>): LibraryStats = LibraryStats(
    songCount = entries.distinctBy { it.id }.size,
    artistCount = entries.map { artistKey(it.artist) }.distinct().size
)

/** Etiqueta visible del artista, con un único grupo para metadatos ausentes. */
fun artistLabelFor(artist: String): String {
    val trimmed = artist.trim()
    return if (trimmed.isEmpty() || trimmed.equals(UNKNOWN_ARTIST_VALUE, ignoreCase = true)) {
        UNKNOWN_ARTIST_LABEL
    } else {
        trimmed
    }
}

internal fun artistKey(artist: String): String = artistLabelFor(artist).lowercase()

internal fun albumKeyFor(album: String, albumArtist: String, artist: String): String {
    val albumName = album.trim().lowercase()
    val artistName = albumArtist.trim().ifBlank { artist }
    return "$albumName|${artistKey(artistName)}"
}

private fun SongEntry.albumKey(): String = albumKeyFor(album, albumArtist, artist)

private fun SongEntry.albumTitle(): String = album.trim().ifBlank { UNKNOWN_ALBUM_LABEL }

private fun SongEntry.albumArtistLabel(): String =
    albumArtist.trim().ifBlank { artistLabelFor(artist) }

data class AlbumGroup(
    val key: String,
    val title: String,
    val artist: String,
    val songs: List<SongEntry>
) {
    val songCount: Int = songs.size
}

data class ArtistGroup(
    val key: String,
    val name: String,
    val songs: List<SongEntry>
) {
    val songCount: Int = songs.size
    val albumCount: Int = songs.map { it.albumKey() }.distinct().size
}

fun groupAlbumsFor(entries: List<SongEntry>): List<AlbumGroup> = entries
    .groupBy { it.albumKey() }
    .map { (key, group) ->
        AlbumGroup(
            key = key,
            title = group.first().albumTitle(),
            artist = group.first().albumArtistLabel(),
            songs = group
        )
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

fun groupArtistsFor(entries: List<SongEntry>): List<ArtistGroup> = entries
    .groupBy { artistKey(it.artist) }
    .map { (key, group) ->
        ArtistGroup(
            key = key,
            name = artistLabelFor(group.first().artist),
            songs = group
        )
    }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

/** Agrupa las canciones de un artista por álbum. */
fun albumsForArtist(entries: List<SongEntry>): List<AlbumGroup> = entries
    .groupBy { it.albumKey() }
    .map { (key, group) ->
        AlbumGroup(
            key = key,
            title = group.first().albumTitle(),
            artist = group.first().albumArtistLabel(),
            songs = group
        )
    }

// ---------------------------------------------------------------------------
// Estadísticas musicales: rankings semanales e históricos
// ---------------------------------------------------------------------------

/** Canción rankeada con sus reproducciones y su tiempo real de escucha. */
data class RankedSong(
    val entry: SongEntry,
    val playCount: Int,
    val listenedMs: Long
)

/** Artista rankeado. La portada sale de [representativeSongId] (portada del álbum). */
data class RankedArtist(
    val key: String,
    val name: String,
    val playCount: Int,
    val listenedMs: Long,
    val representativeSongId: String?
)

/** Álbum rankeado, con una canción representativa para reutilizar su portada. */
data class RankedAlbum(
    val key: String,
    val title: String,
    val artist: String,
    val playCount: Int,
    val listenedMs: Long,
    val representativeSongId: String?
)

data class DashboardRankings(
    val weeklySongs: List<RankedSong> = emptyList(),
    val weeklyArtists: List<RankedArtist> = emptyList(),
    val weeklyAlbums: List<RankedAlbum> = emptyList(),
    val monthlySongs: List<RankedSong> = emptyList(),
    val monthlyArtists: List<RankedArtist> = emptyList(),
    val monthlyAlbums: List<RankedAlbum> = emptyList(),
    val yearlySongs: List<RankedSong> = emptyList(),
    val yearlyArtists: List<RankedArtist> = emptyList(),
    val yearlyAlbums: List<RankedAlbum> = emptyList(),
    val allTimeSongs: List<RankedSong> = emptyList(),
    val allTimeArtists: List<RankedArtist> = emptyList(),
    val allTimeAlbums: List<RankedAlbum> = emptyList()
)

private data class RankAccumulator(var playCount: Int = 0, var listenedMs: Long = 0L)

private val songRankingComparator = compareByDescending<RankedSong> { it.playCount }
    .thenByDescending { it.listenedMs }
    .thenBy { it.entry.title.lowercase(Locale.getDefault()) }

private val artistRankingComparator = compareByDescending<RankedArtist> { it.playCount }
    .thenByDescending { it.listenedMs }
    .thenBy { it.name.lowercase(Locale.getDefault()) }

private val albumRankingComparator = compareByDescending<RankedAlbum> { it.playCount }
    .thenByDescending { it.listenedMs }
    .thenBy { it.title.lowercase(Locale.getDefault()) }

/**
 * Inicio de la ventana móvil del periodo. `null` en [StatsPeriod.ALL_TIME]
 * significa “sin límite inferior”.
 */
fun periodStartMillis(now: Long, period: StatsPeriod): Long? =
    period.days?.let { now - it * DAY_MS }

/** Canciones ordenadas por reproducciones de una lista de muestras. */
internal fun rankedSongsFromSamples(
    samples: List<PlaybackSample>,
    entriesById: Map<String, SongEntry>,
    since: Long? = null
): List<RankedSong> {
    val accumulated = LinkedHashMap<String, RankAccumulator>()
    samples.forEach { sample ->
        if (since != null && sample.startedAt < since) return@forEach
        val current = accumulated.getOrPut(sample.songId) { RankAccumulator() }
        current.playCount += 1
        current.listenedMs += sample.listenedMs.coerceAtLeast(0L)
    }
    return accumulated.mapNotNull { (songId, value) ->
        val entry = entriesById[songId] ?: return@mapNotNull null
        RankedSong(entry, value.playCount, value.listenedMs)
    }.sortedWith(songRankingComparator)
}

/** Canciones a partir de los resúmenes diarios (ventanas largas). */
internal fun rankedSongsFromDaily(
    daily: List<DailyPlaybackAggregate>,
    entriesById: Map<String, SongEntry>,
    since: Long? = null
): List<RankedSong> {
    val accumulated = LinkedHashMap<String, RankAccumulator>()
    daily.forEach { item ->
        if (since != null && item.dayStart < since) return@forEach
        val current = accumulated.getOrPut(item.songId) { RankAccumulator() }
        current.playCount += item.playCount
        current.listenedMs += item.listenedMs.coerceAtLeast(0L)
    }
    return accumulated.mapNotNull { (songId, value) ->
        val entry = entriesById[songId] ?: return@mapNotNull null
        RankedSong(entry, value.playCount, value.listenedMs)
    }.sortedWith(songRankingComparator)
}

/** Canciones ordenadas por los totales acumulados desde esta versión. */
internal fun rankedSongsFromTotals(
    totals: Map<String, SongPlaybackStats>,
    entriesById: Map<String, SongEntry>
): List<RankedSong> = totals.mapNotNull { (songId, stats) ->
    val entry = entriesById[songId] ?: return@mapNotNull null
    if (stats.playCount <= 0) return@mapNotNull null
    RankedSong(entry, stats.playCount, stats.listenedMs.coerceAtLeast(0L))
}.sortedWith(songRankingComparator)

/** Agrupa canciones rankeadas por artista, sumando reproducciones y tiempo. */
internal fun rankedArtistsFromSongs(
    songs: List<RankedSong>,
    limit: Int = RANKING_LIMIT
): List<RankedArtist> = songs.groupBy { artistKey(it.entry.artist) }
    .map { (key, group) ->
        val representative = group.maxWith(
            compareBy<RankedSong> { it.playCount }
                .thenBy { it.listenedMs }
                .thenByDescending { it.entry.id }
        )
        RankedArtist(
            key = key,
            name = artistLabelFor(group.first().entry.artist),
            playCount = group.sumOf { it.playCount },
            listenedMs = group.sumOf { it.listenedMs },
            representativeSongId = representative.entry.id
        )
    }
    .sortedWith(artistRankingComparator)
    .take(limit)

/** Agrupa canciones rankeadas por álbum (mismo criterio que la biblioteca). */
internal fun rankedAlbumsFromSongs(
    songs: List<RankedSong>,
    limit: Int = RANKING_LIMIT
): List<RankedAlbum> = songs.groupBy { it.entry.albumKey() }
    .map { (key, group) ->
        val representative = group.maxWith(
            compareBy<RankedSong> { it.playCount }
                .thenBy { it.listenedMs }
                .thenByDescending { it.entry.id }
        )
        RankedAlbum(
            key = key,
            title = group.first().entry.albumTitle(),
            artist = group.first().entry.albumArtistLabel(),
            playCount = group.sumOf { it.playCount },
            listenedMs = group.sumOf { it.listenedMs },
            representativeSongId = representative.entry.id
        )
    }
    .sortedWith(albumRankingComparator)
    .take(limit)

/**
 * Rankings que alimentan el panel. Cada ventana se calcula desde las muestras;
 * el histórico usa los totales acumulados. Las canciones que ya no están en la
 * biblioteca se omiten, igual que en "Escuchado recientemente".
 */
@Suppress("UNUSED_PARAMETER")
internal fun rankingsFor(
    snapshot: PlaybackStatsSnapshot,
    entries: List<SongEntry>,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
    limit: Int = RANKING_LIMIT
): DashboardRankings {
    val entriesById = entries.associateBy { it.id }
    val weeklySongs = rankedSongsFromSamples(
        snapshot.samples, entriesById, periodStartMillis(now, StatsPeriod.WEEK)
    ).take(limit)
    val monthlySince = periodStartMillis(now, StatsPeriod.MONTH)
    val monthlySongs = if (snapshot.daily.isNotEmpty()) {
        rankedSongsFromDaily(snapshot.daily, entriesById, monthlySince)
    } else {
        rankedSongsFromSamples(snapshot.samples, entriesById, monthlySince)
    }.take(limit)
    val yearlySince = periodStartMillis(now, StatsPeriod.YEAR)
    val yearlySongs = if (snapshot.daily.isNotEmpty()) {
        rankedSongsFromDaily(snapshot.daily, entriesById, yearlySince)
    } else {
        rankedSongsFromSamples(snapshot.samples, entriesById, yearlySince)
    }.take(limit)
    val allTimeSongs = rankedSongsFromTotals(snapshot.totals, entriesById)
        .take(limit)

    return DashboardRankings(
        weeklySongs = weeklySongs,
        weeklyArtists = rankedArtistsFromSongs(weeklySongs, limit),
        weeklyAlbums = rankedAlbumsFromSongs(weeklySongs, limit),
        monthlySongs = monthlySongs,
        monthlyArtists = rankedArtistsFromSongs(monthlySongs, limit),
        monthlyAlbums = rankedAlbumsFromSongs(monthlySongs, limit),
        yearlySongs = yearlySongs,
        yearlyArtists = rankedArtistsFromSongs(yearlySongs, limit),
        yearlyAlbums = rankedAlbumsFromSongs(yearlySongs, limit),
        allTimeSongs = allTimeSongs,
        allTimeArtists = rankedArtistsFromSongs(allTimeSongs, limit),
        allTimeAlbums = rankedAlbumsFromSongs(allTimeSongs, limit)
    )
}

/** Etiqueta compacta de tiempo escuchado para las tarjetas del panel. */
fun formatListenedTime(listenedMs: Long): String {
    val minutes = listenedMs.coerceAtLeast(0L) / MINUTE_MS
    return when {
        minutes < 1L -> "0 min"
        minutes < 60L -> "$minutes min"
        else -> {
            val hours = minutes / 60L
            val remainder = minutes % 60L
            if (remainder == 0L) "$hours h" else "$hours h $remainder min"
        }
    }
}

// ---------------------------------------------------------------------------
// Listas completas para la pantalla de detalle de estadísticas
// ---------------------------------------------------------------------------

/** Los tres rankings completos de un periodo, con los elementos sin escuchas al final. */
data class FullRankingSet(
    val songs: List<RankedSong> = emptyList(),
    val artists: List<RankedArtist> = emptyList(),
    val albums: List<RankedAlbum> = emptyList()
)

data class FullDashboardRankings(
    val weekly: FullRankingSet = FullRankingSet(),
    val monthly: FullRankingSet = FullRankingSet(),
    val yearly: FullRankingSet = FullRankingSet(),
    val allTime: FullRankingSet = FullRankingSet()
)

/**
 * Parte de toda la biblioteca, no solo de lo que tiene estadísticas: lo más
 * escuchado va primero y el resto aparece detrás con 0 reproducciones (o con
 * las que tenga), ordenado alfabéticamente.
 */
private fun allSongsRanked(
    entries: List<SongEntry>,
    values: Map<String, Pair<Int, Long>>
): List<RankedSong> {
    val result = ArrayList<RankedSong>(entries.size)
    entries.distinctBy { it.id }.forEach { entry ->
        val (plays, listenedMs) = values[entry.id] ?: 0 to 0L
        result.add(RankedSong(entry, plays, listenedMs))
    }
    result.sortWith(songRankingComparator)
    return result
}

private fun fullRankingSetFrom(songs: List<RankedSong>) = FullRankingSet(
    songs = songs,
    artists = rankedArtistsFromSongs(songs, Int.MAX_VALUE),
    albums = rankedAlbumsFromSongs(songs, Int.MAX_VALUE)
)

/**
 * Igual que [rankingsFor] pero sin límite: alimenta la tarjeta de detalle, donde
 * se muestran todas las canciones, artistas y álbumes de la biblioteca.
 */
@Suppress("UNUSED_PARAMETER")
internal fun fullRankingsFor(
    snapshot: PlaybackStatsSnapshot,
    entries: List<SongEntry>,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): FullDashboardRankings {
    fun valuesSince(since: Long?): Map<String, Pair<Int, Long>> {
        val values = LinkedHashMap<String, Pair<Int, Long>>()
        snapshot.samples.forEach { sample ->
            if (since != null && sample.startedAt < since) return@forEach
            val previous = values[sample.songId] ?: 0 to 0L
            values[sample.songId] =
                (previous.first + 1) to (previous.second + sample.listenedMs.coerceAtLeast(0L))
        }
        return values
    }

    fun valuesFromDaily(since: Long?): Map<String, Pair<Int, Long>> {
        val values = LinkedHashMap<String, Pair<Int, Long>>()
        snapshot.daily.forEach { item ->
            if (since != null && item.dayStart < since) return@forEach
            val previous = values[item.songId] ?: 0 to 0L
            values[item.songId] = (previous.first + item.playCount) to
                (previous.second + item.listenedMs.coerceAtLeast(0L))
        }
        return values
    }

    fun valuesFor(period: StatsPeriod): Map<String, Pair<Int, Long>> {
        val since = periodStartMillis(now, period)
        return if (snapshot.daily.isNotEmpty()) valuesFromDaily(since) else valuesSince(since)
    }

    val allTimeValues = snapshot.totals.mapValues { (_, stats) ->
        stats.playCount to stats.listenedMs.coerceAtLeast(0L)
    }

    return FullDashboardRankings(
        weekly = fullRankingSetFrom(
            allSongsRanked(entries, valuesSince(periodStartMillis(now, StatsPeriod.WEEK)))
        ),
        monthly = fullRankingSetFrom(allSongsRanked(entries, valuesFor(StatsPeriod.MONTH))),
        yearly = fullRankingSetFrom(allSongsRanked(entries, valuesFor(StatsPeriod.YEAR))),
        allTime = fullRankingSetFrom(allSongsRanked(entries, allTimeValues))
    )
}

// ---------------------------------------------------------------------------
// Hábitos de escucha: días más activos, géneros y ánimo estimado
// ---------------------------------------------------------------------------

data class DailyListening(
    val label: String,
    val dateLabel: String,
    val dayStart: Long,
    val playCount: Int,
    val listenedMs: Long
)

data class GenreListening(val label: String, val playCount: Int, val listenedMs: Long)

data class MoodListening(val label: String, val playCount: Int, val listenedMs: Long)

data class ListeningInsights(
    val days: List<DailyListening> = emptyList(),
    val genres: List<GenreListening> = emptyList(),
    val moods: List<MoodListening> = emptyList(),
    val chartLabel: String = ""
)

data class AllListeningInsights(
    val weekly: ListeningInsights = ListeningInsights(),
    val monthly: ListeningInsights = ListeningInsights(),
    val yearly: ListeningInsights = ListeningInsights(),
    val allTime: ListeningInsights = ListeningInsights()
)

private data class DailyAccumulator(var playCount: Int = 0, var listenedMs: Long = 0L)
private data class LabelAccumulator(var playCount: Int = 0, var listenedMs: Long = 0L)

/** Los tags pueden traer varios géneros separados por ; , o /. */
private fun genreParts(genre: String): List<String> {
    if (genre.isBlank()) return listOf("Sin género")
    return genre.split(';', ',', '/')
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
        .distinct()
        .ifEmpty { listOf("Sin género") }
}

/**
 * Agrupación orientativa de géneros en estados de ánimo. No es un dato real del
 * usuario: solo sirve para dar contexto a lo que escucha.
 */
private val moodKeywords = linkedMapOf(
    "Enérgico" to listOf(
        "rock", "metal", "punk", "electronic", "electro", "dance", "techno",
        "trance", "house", "hip hop", "hip-hop", "rap", "dubstep", "hardcore"
    ),
    "Calmo" to listOf(
        "ambient", "chill", "acoustic", "classical", "clásica", "jazz", "folk",
        "lo-fi", "lofi", "piano", "instrumental", "new age"
    ),
    "Melancólico" to listOf(
        "blues", "indie", "alternative", "balada", "ballad", "soul", "r&b"
    ),
    "Alegre" to listOf(
        "pop", "reggae", "funk", "disco", "country", "latin", "latina", "ska"
    )
)

private fun moodFor(genre: String): String {
    val value = genre.lowercase(Locale.getDefault())
    return moodKeywords.firstNotNullOfOrNull { (mood, keywords) ->
        mood.takeIf { keywords.any(value::contains) }
    } ?: "Otros"
}

private data class ListeningRecord(
    val songId: String,
    val at: Long,
    val playCount: Int,
    val listenedMs: Long
)

private fun <T> rankedLabels(
    values: Map<String, LabelAccumulator>,
    limit: Int,
    build: (String, Int, Long) -> T
): List<T> = values.entries
    .sortedWith(
        compareByDescending<Map.Entry<String, LabelAccumulator>> { it.value.playCount }
            .thenByDescending { it.value.listenedMs }
            .thenBy { it.key.lowercase(Locale.getDefault()) }
    )
    .take(limit)
    .map { (label, value) -> build(label, value.playCount, value.listenedMs) }

/**
 * Días con escuchas del periodo y géneros/ánimo estimado. Para periodos largos
 * la gráfica se limita a los últimos 30 días para que siga siendo legible.
 */
internal fun insightsFor(
    snapshot: PlaybackStatsSnapshot,
    entries: List<SongEntry>,
    period: StatsPeriod,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): ListeningInsights {
    val entriesById = entries.associateBy { it.id }
    val since = periodStartMillis(now, period)
    val chartSince = periodStartMillis(
        now,
        if (period == StatsPeriod.WEEK || period == StatsPeriod.MONTH) period
        else StatsPeriod.MONTH
    )!!
    val useDaily = period != StatsPeriod.WEEK && snapshot.daily.isNotEmpty()
    val relevant = if (useDaily) {
        snapshot.daily
            .filter {
                it.dayStart >= (since ?: Long.MIN_VALUE) && entriesById.containsKey(it.songId)
            }
            .map { ListeningRecord(it.songId, it.dayStart, it.playCount, it.listenedMs) }
    } else {
        snapshot.samples
            .filter {
                it.startedAt >= (since ?: Long.MIN_VALUE) && entriesById.containsKey(it.songId)
            }
            .map { ListeningRecord(it.songId, it.startedAt, 1, it.listenedMs) }
    }

    val calendar = Calendar.getInstance(timeZone, Locale.getDefault())
    fun startOfDay(time: Long): Long {
        calendar.timeInMillis = time
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    val firstDay = startOfDay(chartSince)
    val lastDay = startOfDay(now)
    val dayStarts = ArrayList<Long>()
    calendar.timeInMillis = firstDay
    var cursor = firstDay
    while (cursor <= lastDay && dayStarts.size <= INSIGHT_CHART_DAYS) {
        dayStarts.add(cursor)
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        cursor = calendar.timeInMillis
    }

    val daily = LinkedHashMap<Long, DailyAccumulator>()
    relevant.forEach { record ->
        if (record.at < chartSince) return@forEach
        val accumulator = daily.getOrPut(startOfDay(record.at)) { DailyAccumulator() }
        accumulator.playCount += record.playCount
        accumulator.listenedMs += record.listenedMs.coerceAtLeast(0L)
    }
    val days = dayStarts.map { start ->
        val value = daily[start] ?: DailyAccumulator()
        calendar.timeInMillis = start
        DailyListening(
            label = calendar.getDisplayName(
                Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()
            ).orEmpty(),
            dateLabel = "${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}",
            dayStart = start,
            playCount = value.playCount,
            listenedMs = value.listenedMs
        )
    }

    val genreValues = LinkedHashMap<String, LabelAccumulator>()
    val moodValues = LinkedHashMap<String, LabelAccumulator>()
    relevant.forEach { record ->
        val listenedMs = record.listenedMs.coerceAtLeast(0L)
        val parts = genreParts(entriesById.getValue(record.songId).genre)
        parts.forEach { genre ->
            val accumulator = genreValues.getOrPut(genre) { LabelAccumulator() }
            accumulator.playCount += record.playCount
            accumulator.listenedMs += listenedMs
        }
        val mood = moodFor(parts.first())
        val accumulator = moodValues.getOrPut(mood) { LabelAccumulator() }
        accumulator.playCount += record.playCount
        accumulator.listenedMs += listenedMs
    }

    return ListeningInsights(
        days = days,
        genres = rankedLabels(genreValues, TOP_GENRE_LIMIT) { label, plays, listened ->
            GenreListening(label, plays, listened)
        },
        moods = rankedLabels(moodValues, moodValues.size) { label, plays, listened ->
            MoodListening(label, plays, listened)
        },
        chartLabel = if (period == StatsPeriod.WEEK) "Últimos 7 días" else "Últimos 30 días"
    )
}

internal fun allListeningInsights(
    snapshot: PlaybackStatsSnapshot,
    entries: List<SongEntry>,
    now: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault()
): AllListeningInsights = AllListeningInsights(
    weekly = insightsFor(snapshot, entries, StatsPeriod.WEEK, now, timeZone),
    monthly = insightsFor(snapshot, entries, StatsPeriod.MONTH, now, timeZone),
    yearly = insightsFor(snapshot, entries, StatsPeriod.YEAR, now, timeZone),
    allTime = insightsFor(snapshot, entries, StatsPeriod.ALL_TIME, now, timeZone)
)
