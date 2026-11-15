package com.cloudjex.jexplayer.viewmodel

import com.cloudjex.jexplayer.data.local.PlaybackHistoryEntry
import com.cloudjex.jexplayer.data.local.PlaybackHistoryStore
import com.cloudjex.jexplayer.data.model.CachedSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardDataTest {

    private fun entry(
        id: String,
        title: String = "Título",
        artist: String = "Artista",
        album: String = "Álbum",
        albumArtist: String = ""
    ) = SongEntry(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist
    )

    private fun cached(id: String, lastModified: Long?) = CachedSong(
        id = id,
        title = "Título $id",
        artist = "Artista",
        album = "Álbum",
        duration = 1_000L,
        uri = "content://audio/$id",
        albumArtUri = null,
        size = 10L,
        lastModified = lastModified
    )

    // --- Saludo ---

    @Test
    fun greetsMorningBetweenSixAndEleven() {
        assertEquals("Buenos días", greetingFor(6))
        assertEquals("Buenos días", greetingFor(11))
    }

    @Test
    fun greetsAfternoonBetweenTwelveAndNineteen() {
        assertEquals("Buenas tardes", greetingFor(12))
        assertEquals("Buenas tardes", greetingFor(19))
    }

    @Test
    fun greetsNightOutsideDaylightHours() {
        assertEquals("Buenas noches", greetingFor(0))
        assertEquals("Buenas noches", greetingFor(5))
        assertEquals("Buenas noches", greetingFor(20))
        assertEquals("Buenas noches", greetingFor(23))
    }

    // --- Escuchado recientemente ---

    @Test
    fun recentlyPlayedIsEmptyWithoutHistory() {
        assertTrue(recentlyPlayedItems(emptyList(), emptyMap()).isEmpty())
    }

    @Test
    fun recentlyPlayedOrdersByMostRecentFirst() {
        val entries = listOf(
            PlaybackHistoryEntry("a", playedAt = 100L),
            PlaybackHistoryEntry("c", playedAt = 300L),
            PlaybackHistoryEntry("b", playedAt = 200L)
        )
        val songs = listOf(entry("a"), entry("b"), entry("c")).associateBy { it.id }

        val items = recentlyPlayedItems(entries, songs)
            .filterIsInstance<DashboardMediaItem.SongItem>()
            .map { it.entry.id }

        assertEquals(listOf("c", "b", "a"), items)
    }

    @Test
    fun recentlyPlayedSkipsSongsMissingFromTheLibrary() {
        val entries = listOf(
            PlaybackHistoryEntry("gone", playedAt = 200L),
            PlaybackHistoryEntry("kept", playedAt = 100L)
        )
        val songs = mapOf("kept" to entry("kept"))

        val items = recentlyPlayedItems(entries, songs)

        assertEquals(1, items.size)
        assertEquals("kept", (items.first() as DashboardMediaItem.SongItem).entry.id)
    }

    @Test
    fun recentlyPlayedKeepsOneItemPerSongMovingTheNewestFirst() {
        val entries = listOf(
            PlaybackHistoryEntry("a", playedAt = 300L),
            PlaybackHistoryEntry("b", playedAt = 200L),
            PlaybackHistoryEntry("a", playedAt = 100L)
        )
        val songs = listOf(entry("a"), entry("b")).associateBy { it.id }

        val items = recentlyPlayedItems(entries, songs)

        assertEquals(2, items.size)
        assertEquals("a", (items.first() as DashboardMediaItem.SongItem).entry.id)
    }

    @Test
    fun recentlyPlayedRespectsTheLimit() {
        val entries = (1..30).map { PlaybackHistoryEntry("s$it", playedAt = it.toLong()) }
        val songs = entries.associate { it.songId to entry(it.songId) }

        assertEquals(
            PlaybackHistoryStore.MAX_ENTRIES,
            recentlyPlayedItems(entries, songs).size
        )
    }

    // --- Clasificación MIXED ---

    @Test
    fun historyEntryOfAKnownSongClassifiesAsSong() {
        val item = dashboardItemFor(
            PlaybackHistoryEntry("song-1", 10L),
            mapOf("song-1" to entry("song-1"))
        )

        assertTrue(item is DashboardMediaItem.SongItem)
        assertEquals("song:song-1", item?.key)
    }

    @Test
    fun historyEntryOfAnUnknownIdClassifiesAsNothing() {
        assertNull(dashboardItemFor(PlaybackHistoryEntry("missing", 10L), emptyMap()))
    }

    @Test
    fun albumAndArtistItemsHaveStableKeys() {
        assertEquals("album:rock", DashboardMediaItem.AlbumItem("rock").key)
        assertEquals("artist:queen", DashboardMediaItem.ArtistItem("queen").key)
    }

    // --- Estadísticas ---

    @Test
    fun countsUniqueSongsIgnoringDuplicateReferences() {
        val stats = libraryStatsFor(listOf(entry("a"), entry("b"), entry("a")))

        assertEquals(2, stats.songCount)
    }

    @Test
    fun countsUniqueArtistsCaseInsensitively() {
        val stats = libraryStatsFor(
            listOf(
                entry("a", artist = "Queen"),
                entry("b", artist = "queen"),
                entry("c", artist = "Daft Punk")
            )
        )

        assertEquals(2, stats.artistCount)
    }

    @Test
    fun groupsAllMissingArtistMetadataInASingleBucket() {
        val stats = libraryStatsFor(
            listOf(
                entry("a", artist = ""),
                entry("b", artist = "   "),
                entry("c", artist = "Unknown"),
                entry("d", artist = "Artista real")
            )
        )

        assertEquals(2, stats.artistCount)
        assertEquals(UNKNOWN_ARTIST_LABEL, artistLabelFor(""))
        assertEquals(UNKNOWN_ARTIST_LABEL, artistLabelFor("Unknown"))
    }

    @Test
    fun emptyLibraryHasZeroStatistics() {
        val stats = libraryStatsFor(emptyList())

        assertEquals(0, stats.songCount)
        assertEquals(0, stats.artistCount)
    }

    // --- Añadido recientemente ---

    @Test
    fun recentlyAddedOrdersByFileModificationDateDescending() {
        val songs = listOf(
            cached("a", 100L),
            cached("c", 300L),
            cached("b", 200L)
        )

        assertEquals(listOf("c", "b", "a"), recentlyAddedSongs(songs).map { it.id })
    }

    @Test
    fun recentlyAddedExcludesEntriesWithoutFileDate() {
        val songs = listOf(cached("a", null), cached("b", 50L))

        assertEquals(listOf("b"), recentlyAddedSongs(songs).map { it.id })
    }

    @Test
    fun recentlyAddedIsEmptyWhenNoEntryHasAFileDate() {
        assertTrue(recentlyAddedSongs(listOf(cached("a", null))).isEmpty())
    }

    @Test
    fun recentlyAddedRespectsTheLimit() {
        val songs = (1..10).map { cached("s$it", it.toLong()) }

        assertEquals(5, recentlyAddedSongs(songs).size)
    }

    @Test
    fun recentlyAddedIsEmptyForAnEmptyLibrary() {
        assertTrue(recentlyAddedSongs(emptyList()).isEmpty())
    }

    // --- Agrupaciones ---

    @Test
    fun groupsSongsByAlbumAndAlbumArtist() {
        val entries = listOf(
            entry("a", album = "Greatest Hits", albumArtist = "Queen"),
            entry("b", album = "Greatest Hits", albumArtist = "Queen"),
            entry("c", album = "Discovery", albumArtist = "Daft Punk")
        )

        val albums = groupAlbumsFor(entries)

        assertEquals(2, albums.size)
        val queen = albums.first { it.artist == "Queen" }
        assertEquals(2, queen.songCount)
        assertEquals("Greatest Hits", queen.title)
    }

    @Test
    fun albumsWithTheSameNameButDifferentArtistsStaySeparate() {
        val entries = listOf(
            entry("a", album = "Live", albumArtist = "Queen"),
            entry("b", album = "Live", albumArtist = "AC/DC")
        )

        assertEquals(2, groupAlbumsFor(entries).size)
    }

    @Test
    fun albumWithoutMetadataUsesTheUnknownLabel() {
        val albums = groupAlbumsFor(listOf(entry("a", album = "  ")))

        assertEquals(UNKNOWN_ALBUM_LABEL, albums.single().title)
    }

    @Test
    fun groupsSongsByArtistAndCountsAlbums() {
        val entries = listOf(
            entry("a", artist = "Queen", album = "A"),
            entry("b", artist = "Queen", album = "B"),
            entry("c", artist = "Queen", album = "A"),
            entry("d", artist = "Daft Punk", album = "Discovery")
        )

        val artists = groupArtistsFor(entries)

        assertEquals(2, artists.size)
        val queen = artists.first { it.name == "Queen" }
        assertEquals(3, queen.songCount)
        assertEquals(2, queen.albumCount)
    }

    @Test
    fun emptyLibraryProducesNoGroups() {
        assertTrue(groupAlbumsFor(emptyList()).isEmpty())
        assertTrue(groupArtistsFor(emptyList()).isEmpty())
    }

    @Test
    fun discographyGroupsOnlyThatArtistSongsByAlbum() {
        val entries = listOf(
            entry("a", artist = "Queen", album = "A"),
            entry("b", artist = "Queen", album = "B"),
            entry("c", artist = "Daft Punk", album = "Discovery")
        )
        val queen = groupArtistsFor(entries).first { it.name == "Queen" }

        val albums = albumsForArtist(queen.songs)

        assertEquals(2, albums.size)
        assertEquals(listOf("a"), albums.first { it.title == "A" }.songs.map { it.id })
    }
}
