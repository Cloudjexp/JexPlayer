package com.cloudjex.jexplayer.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySongSorterTest {
    private fun song(
        id: String,
        title: String = id,
        album: String = "",
        artist: String = "",
        albumArtist: String = "",
        genre: String = "",
        year: Int? = null
    ) = LibrarySortEntry(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        genre = genre,
        year = year
    )

    @Test
    fun textSortIgnoresCaseAndAccents() {
        val songs = listOf(song("z", title = "zeta"), song("a", title = "Álbum"), song("b", title = "album"))

        val sorted = LibrarySongSorter.sortEntries(
            songs,
            LibrarySortSettings(LibrarySortField.TITLE, LibrarySortDirection.ASCENDING)
        )

        assertEquals(listOf("a", "b", "z"), sorted.map { it.id })
    }

    @Test
    fun descendingReversesValuesButKeepsMissingValuesLast() {
        val songs = listOf(
            song("missing", title = ""),
            song("a", title = "A"),
            song("c", title = "C")
        )

        val sorted = LibrarySongSorter.sortEntries(
            songs,
            LibrarySortSettings(LibrarySortField.TITLE, LibrarySortDirection.DESCENDING)
        )

        assertEquals(listOf("c", "a", "missing"), sorted.map { it.id })
    }

    @Test
    fun allSupportedTextFieldsAreUsed() {
        val songs = listOf(
            song("b", album = "Álbum B", artist = "Artista B", albumArtist = "Álbum Artista B", genre = "Rock"),
            song("a", album = "Álbum A", artist = "Artista A", albumArtist = "Álbum Artista A", genre = "Jazz")
        )

        LibrarySortField.entries
            .filterNot { it == LibrarySortField.YEAR }
            .forEach { field ->
                val sorted = LibrarySongSorter.sortEntries(
                    songs,
                    LibrarySortSettings(field, LibrarySortDirection.ASCENDING)
                )
                assertEquals("a", sorted.first().id)
            }
    }

    @Test
    fun yearSortIsNumericAndMissingYearStaysLast() {
        val songs = listOf(
            song("missing", year = null),
            song("ten", year = 10),
            song("two", year = 2)
        )

        val ascending = LibrarySongSorter.sortEntries(
            songs,
            LibrarySortSettings(LibrarySortField.YEAR, LibrarySortDirection.ASCENDING)
        )
        val descending = LibrarySongSorter.sortEntries(
            songs,
            LibrarySortSettings(LibrarySortField.YEAR, LibrarySortDirection.DESCENDING)
        )

        assertEquals(listOf("two", "ten", "missing"), ascending.map { it.id })
        assertEquals(listOf("ten", "two", "missing"), descending.map { it.id })
    }

    @Test
    fun equalValuesPreserveOriginalOrder() {
        val songs = listOf(
            song("first", title = "Álbum"),
            song("second", title = "album"),
            song("third", title = "ÁLBUM")
        )

        val sorted = LibrarySongSorter.sortEntries(
            songs,
            LibrarySortSettings(LibrarySortField.TITLE, LibrarySortDirection.DESCENDING)
        )

        assertEquals(listOf("first", "second", "third"), sorted.map { it.id })
    }
}
