package com.cloudjex.jexplayer.viewmodel

import com.cloudjex.jexplayer.data.local.PlaybackSample
import com.cloudjex.jexplayer.data.local.PlaybackStatsSnapshot
import com.cloudjex.jexplayer.data.local.SongPlaybackStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DashboardRankingsTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int = 0): Long =
        Calendar.getInstance(utc, Locale.US).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun entry(
        id: String,
        title: String = id,
        artist: String = "Artista",
        album: String = "Álbum",
        albumArtist: String = "",
        genre: String = ""
    ) = SongEntry(id, title, artist, album, albumArtist, genre)

    private fun samples(songId: String, count: Int, at: Long, listenedMs: Long = 30_000L) =
        (1..count).map { PlaybackSample(songId, at + it, listenedMs) }

    // --- Ventanas móviles ---

    @Test
    fun periodsAreRollingWindowsNotCalendarBoundaries() {
        val now = utcMillis(2026, 11, 23, 10)
        assertEquals(utcMillis(2026, 11, 16, 10), periodStartMillis(now, StatsPeriod.WEEK))
        assertEquals(utcMillis(2026, 10, 24, 10), periodStartMillis(now, StatsPeriod.MONTH))
        assertEquals(utcMillis(2025, 11, 23, 10), periodStartMillis(now, StatsPeriod.YEAR))
        assertEquals(null, periodStartMillis(now, StatsPeriod.ALL_TIME))
    }

    @Test
    fun yesterdayCountsInTheSevenDayWindowEvenOnMonday() {
        val entries = listOf(entry("a"))
        val snapshot = PlaybackStatsSnapshot(
            samples = listOf(PlaybackSample("a", utcMillis(2026, 11, 22, 20), 30_000L))
        )

        val rankings = rankingsFor(snapshot, entries, utcMillis(2026, 11, 23, 9), utc)

        assertEquals(1, rankings.weeklySongs.size)
        assertEquals("a", rankings.weeklySongs.first().entry.id)
    }

    // --- Semana vs histórico ---

    @Test
    fun weeklyRankingsOnlyCountSamplesFromThisWeek() {
        val entries = listOf(entry("a", artist = "Queen"), entry("b", artist = "Queen"))
        val snapshot = PlaybackStatsSnapshot(
            totals = mapOf(
                "a" to SongPlaybackStats(3, 90_000L),
                "b" to SongPlaybackStats(1, 30_000L)
            ),
            samples = samples("a", 2, utcMillis(2026, 11, 17), 30_000L) +
                samples("b", 1, utcMillis(2026, 11, 9), 30_000L)
        )

        val rankings = rankingsFor(snapshot, entries, utcMillis(2026, 11, 18, 20), utc)

        assertEquals(1, rankings.weeklySongs.size)
        assertEquals("a", rankings.weeklySongs.first().entry.id)
        assertEquals(2, rankings.weeklySongs.first().playCount)
        assertEquals(60_000L, rankings.weeklySongs.first().listenedMs)
        assertEquals(2, rankings.monthlySongs.size)
        assertEquals(2, rankings.yearlySongs.size)
        assertEquals(2, rankings.allTimeSongs.size)
        assertEquals(90_000L, rankings.allTimeSongs.first().listenedMs)
    }

    @Test
    fun allTimeRankingUsesStoredTotalsNotSamples() {
        val entries = listOf(entry("a"), entry("b"))
        val snapshot = PlaybackStatsSnapshot(
            totals = mapOf(
                "a" to SongPlaybackStats(5, 200_000L),
                "b" to SongPlaybackStats(6, 100_000L)
            ),
            samples = emptyList()
        )

        val rankings = rankingsFor(snapshot, entries, utcMillis(2026, 11, 18), utc)

        assertTrue(rankings.weeklySongs.isEmpty())
        assertEquals(listOf("b", "a"), rankings.allTimeSongs.map { it.entry.id })
    }

    @Test
    fun tiesAreBrokenByListenedTime() {
        val entries = listOf(entry("a"), entry("b"))
        val snapshot = PlaybackStatsSnapshot(
            totals = mapOf(
                "a" to SongPlaybackStats(2, 10_000L),
                "b" to SongPlaybackStats(2, 20_000L)
            )
        )

        val rankings = rankingsFor(snapshot, entries, utcMillis(2026, 11, 18), utc)

        assertEquals("b", rankings.allTimeSongs.first().entry.id)
    }

    // --- Agrupaciones ---

    @Test
    fun artistsAreGroupedCaseInsensitivelyAndUseTheirMostPlayedSongAsCover() {
        val entries = listOf(
            entry("a", artist = "Queen"),
            entry("b", artist = "queen"),
            entry("c", artist = "Daft Punk")
        )
        val snapshot = PlaybackStatsSnapshot(
            totals = mapOf(
                "a" to SongPlaybackStats(3, 90_000L),
                "b" to SongPlaybackStats(1, 30_000L),
                "c" to SongPlaybackStats(2, 60_000L)
            )
        )

        val rankings = rankingsFor(snapshot, entries, utcMillis(2026, 11, 18), utc)

        assertEquals(2, rankings.allTimeArtists.size)
        val queen = rankings.allTimeArtists.first { it.key == "queen" }
        assertEquals("Queen", queen.name)
        assertEquals(4, queen.playCount)
        assertEquals(120_000L, queen.listenedMs)
        assertEquals("a", queen.representativeSongId)
    }

    @Test
    fun albumsAreGroupedWithTheSameKeyAsTheLibraryAndReuseACover() {
        val entries = listOf(
            entry("a", album = "Greatest Hits", albumArtist = "Queen"),
            entry("b", album = "Greatest Hits", albumArtist = "Queen"),
            entry("c", album = "Discovery", albumArtist = "Daft Punk")
        )
        val snapshot = PlaybackStatsSnapshot(
            totals = mapOf(
                "a" to SongPlaybackStats(1, 30_000L),
                "b" to SongPlaybackStats(3, 90_000L),
                "c" to SongPlaybackStats(2, 60_000L)
            )
        )

        val rankings = rankingsFor(snapshot, entries, utcMillis(2026, 11, 18), utc)

        assertEquals(2, rankings.allTimeAlbums.size)
        val greatest = rankings.allTimeAlbums.first { it.title == "Greatest Hits" }
        assertEquals("Queen", greatest.artist)
        assertEquals(4, greatest.playCount)
        assertEquals("b", greatest.representativeSongId)
        assertEquals(albumKeyFor("Greatest Hits", "Queen", ""), greatest.key)
    }

    @Test
    fun missingSongsAreSkippedInEveryRanking() {
        val snapshot = PlaybackStatsSnapshot(
            totals = mapOf("gone" to SongPlaybackStats(9, 999_000L)),
            samples = listOf(PlaybackSample("gone", utcMillis(2026, 11, 17)))
        )

        val rankings = rankingsFor(snapshot, emptyList(), utcMillis(2026, 11, 18), utc)

        assertTrue(rankings.weeklySongs.isEmpty())
        assertTrue(rankings.weeklyArtists.isEmpty())
        assertTrue(rankings.allTimeSongs.isEmpty())
        assertTrue(rankings.allTimeAlbums.isEmpty())
    }

    @Test
    fun rankingsRespectTheLimit() {
        val entries = (1..10).map { entry("s$it") }
        val snapshot = PlaybackStatsSnapshot(
            totals = entries.associate { it.id to SongPlaybackStats(it.id.removePrefix("s").toInt()) }
        )

        val rankings = rankingsFor(
            snapshot, entries, utcMillis(2026, 11, 18), utc, limit = 3
        )

        assertEquals(listOf("s10", "s9", "s8"), rankings.allTimeSongs.map { it.entry.id })
    }

    // --- Etiqueta de tiempo ---

    @Test
    fun fullRankingsIncludeEveryLibraryItemWithTheMostListenedFirst() {
        val entries = listOf(
            entry("a", title = "A", artist = "Queen", album = "X"),
            entry("b", title = "B", artist = "Daft Punk", album = "Y"),
            entry("c", title = "C", artist = "Queen", album = "X")
        )
        val snapshot = PlaybackStatsSnapshot(
            totals = mapOf("b" to SongPlaybackStats(2, 60_000L))
        )

        val full = fullRankingsFor(snapshot, entries, utcMillis(2026, 11, 18), utc)

        assertEquals(listOf("b", "a", "c"), full.allTime.songs.map { it.entry.id })
        assertEquals(0, full.allTime.songs.first { it.entry.id == "a" }.playCount)
        assertEquals(listOf("Daft Punk", "Queen"), full.allTime.artists.map { it.name })
        assertEquals(listOf("Y", "X"), full.allTime.albums.map { it.title })
        assertEquals("a", full.allTime.artists.first { it.key == "queen" }.representativeSongId)
    }

    @Test
    fun fullWeeklyRankingsOnlyCountThisWeekAndLeaveTheRestAtZero() {
        val entries = listOf(entry("a"), entry("b"))
        val snapshot = PlaybackStatsSnapshot(
            samples = listOf(PlaybackSample("b", utcMillis(2026, 11, 17), 30_000L))
        )

        val full = fullRankingsFor(snapshot, entries, utcMillis(2026, 11, 18), utc)

        assertEquals(1, full.weekly.songs.first { it.entry.id == "b" }.playCount)
        assertEquals(0, full.weekly.songs.first { it.entry.id == "a" }.playCount)
        assertEquals(listOf("b", "a"), full.weekly.songs.map { it.entry.id })
    }

    @Test
    fun insightsGroupDaysGenresAndEstimatedMoods() {
        val entries = listOf(
            entry("a", genre = "Rock"),
            entry("b", genre = "Jazz; Chill")
        )
        val now = utcMillis(2026, 11, 23, 12)
        val snapshot = PlaybackStatsSnapshot(
            samples = listOf(
                PlaybackSample("a", utcMillis(2026, 11, 22, 10), 60_000L),
                PlaybackSample("a", utcMillis(2026, 11, 22, 11), 60_000L),
                PlaybackSample("b", utcMillis(2026, 11, 21, 10), 30_000L),
                PlaybackSample("a", utcMillis(2026, 10, 1, 10), 600_000L)
            )
        )

        val insights = insightsFor(snapshot, entries, StatsPeriod.WEEK, now, utc)

        val bestDay = insights.days.maxByOrNull { it.listenedMs }!!
        assertEquals(120_000L, bestDay.listenedMs)
        assertEquals(2, bestDay.playCount)
        assertEquals("Rock", insights.genres.first().label)
        assertEquals(2, insights.genres.first().playCount)
        assertEquals("Enérgico", insights.moods.first().label)
        assertEquals(2, insights.moods.first().playCount)
    }

    @Test
    fun longPeriodsKeepTheLastThirtyDaysInTheChart() {
        val insights = insightsFor(
            PlaybackStatsSnapshot(),
            emptyList(),
            StatsPeriod.YEAR,
            utcMillis(2026, 11, 23, 12),
            utc
        )

        assertEquals("Últimos 30 días", insights.chartLabel)
        assertEquals(31, insights.days.size)
    }

    @Test
    fun formatsListenedTimeCompactly() {
        assertEquals("0 min", formatListenedTime(0L))
        assertEquals("0 min", formatListenedTime(59_000L))
        assertEquals("1 min", formatListenedTime(60_000L))
        assertEquals("59 min", formatListenedTime(3_599_000L))
        assertEquals("1 h", formatListenedTime(3_600_000L))
        assertEquals("1 h 5 min", formatListenedTime(3_900_000L))
        assertEquals("2 h", formatListenedTime(7_200_000L))
    }
}
