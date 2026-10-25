package com.cloudjex.jexplayer.data.model

import java.text.Normalizer
import java.util.Locale

enum class LibrarySortField(val storageKey: String, val label: String) {
    TITLE("title", "Título"),
    ALBUM("album", "Álbum"),
    ARTIST("artist", "Artista"),
    ALBUM_ARTIST("album_artist", "Artista del álbum"),
    GENRE("genre", "Género"),
    YEAR("year", "Año");

    companion object {
        fun fromStorageKey(value: String?): LibrarySortField =
            entries.firstOrNull { it.storageKey == value } ?: TITLE
    }
}

enum class LibrarySortDirection(val storageKey: String, val label: String) {
    ASCENDING("ascending", "Ascendente"),
    DESCENDING("descending", "Descendente");

    companion object {
        fun fromStorageKey(value: String?): LibrarySortDirection =
            entries.firstOrNull { it.storageKey == value } ?: ASCENDING
    }
}

data class LibrarySortSettings(
    val field: LibrarySortField = LibrarySortField.TITLE,
    val direction: LibrarySortDirection = LibrarySortDirection.ASCENDING
)

data class LibrarySortEntry(
    val id: String,
    val title: String,
    val album: String,
    val artist: String,
    val albumArtist: String,
    val genre: String,
    val year: Int?
)

object LibrarySongSorter {
    fun sort(songs: List<Song>, settings: LibrarySortSettings): List<Song> =
        songs.mapIndexed { index, song -> index to song }
            .sortedWith { left, right ->
                compare(
                    entry(left.second),
                    entry(right.second),
                    settings,
                    left.first,
                    right.first
                )
            }
            .map { it.second }

    fun sortEntries(
        entries: List<LibrarySortEntry>,
        settings: LibrarySortSettings
    ): List<LibrarySortEntry> = entries.mapIndexed { index, entry -> index to entry }
        .sortedWith { left, right ->
            compare(left.second, right.second, settings, left.first, right.first)
        }
        .map { it.second }

    private fun entry(song: Song): LibrarySortEntry = LibrarySortEntry(
        id = song.id,
        title = song.title,
        album = song.album,
        artist = song.artist,
        albumArtist = song.albumArtist,
        genre = song.genre,
        year = song.year
    )

    private fun compare(
        left: LibrarySortEntry,
        right: LibrarySortEntry,
        settings: LibrarySortSettings,
        leftIndex: Int,
        rightIndex: Int
    ): Int {
        val leftValue = valueFor(left, settings.field)
        val rightValue = valueFor(right, settings.field)
        val leftMissing = leftValue == null || leftValue.isEmpty()
        val rightMissing = rightValue == null || rightValue.isEmpty()

        return when {
            leftMissing && !rightMissing -> 1
            !leftMissing && rightMissing -> -1
            leftMissing && rightMissing -> leftIndex.compareTo(rightIndex)
            else -> {
                val comparison = when (settings.field) {
                    LibrarySortField.YEAR ->
                        leftValue!!.toInt().compareTo(rightValue!!.toInt())
                    else -> compareText(leftValue!!, rightValue!!)
                }
                if (comparison != 0 && settings.direction == LibrarySortDirection.DESCENDING) {
                    -comparison
                } else if (comparison != 0) {
                    comparison
                } else {
                    leftIndex.compareTo(rightIndex)
                }
            }
        }
    }

    private fun valueFor(entry: LibrarySortEntry, field: LibrarySortField): String? = when (field) {
        LibrarySortField.TITLE -> entry.title
        LibrarySortField.ALBUM -> entry.album
        LibrarySortField.ARTIST -> entry.artist
        LibrarySortField.ALBUM_ARTIST -> entry.albumArtist
        LibrarySortField.GENRE -> entry.genre
        LibrarySortField.YEAR -> entry.year?.toString()
    }

    private fun compareText(left: String, right: String): Int =
        normalizeText(left).compareTo(normalizeText(right))

    private fun normalizeText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.ROOT)
}
