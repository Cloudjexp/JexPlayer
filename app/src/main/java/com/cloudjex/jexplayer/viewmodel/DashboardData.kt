package com.cloudjex.jexplayer.viewmodel

import com.cloudjex.jexplayer.data.local.PlaybackHistoryEntry
import com.cloudjex.jexplayer.data.model.CachedSong
import com.cloudjex.jexplayer.data.model.Song

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
    val albumArtist: String
)

fun Song.toEntry(): SongEntry = SongEntry(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumArtist = albumArtist
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
): DashboardMediaItem? {    val song = entriesById[entry.songId] ?: return null
    return DashboardMediaItem.SongItem(song)
}/**
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
