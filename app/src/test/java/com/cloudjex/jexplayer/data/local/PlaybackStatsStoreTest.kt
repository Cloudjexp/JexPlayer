package com.cloudjex.jexplayer.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaybackStatsStoreTest {

    private fun newFile(): File = File(
        System.getProperty("java.io.tmpdir"),
        "jexplayer-stats-${System.nanoTime()}.json"
    )

    private fun store(
        file: File,
        maxSamples: Int = PlaybackStatsStore.MAX_SAMPLES
    ) = PlaybackStatsStore(
        file = file,
        ioDispatcher = Dispatchers.Unconfined,
        maxSamples = maxSamples
    )

    @Test
    fun survivesARestart() = runBlocking {
        val file = newFile()
        try {
            val store = store(file)
            store.recordPlay("content://song/a", at = 1_000L)
            store.addListening("content://song/a", 30_000L, at = 2_000L)

            val reopened = store(file).load()

            assertEquals(1, reopened.totals.getValue("content://song/a").playCount)
            assertEquals(30_000L, reopened.totals.getValue("content://song/a").listenedMs)
            assertEquals(2_000L, reopened.totals.getValue("content://song/a").lastPlayedAt)
            assertEquals(1, reopened.samples.size)
            assertEquals(30_000L, reopened.samples.single().listenedMs)
        } finally {
            file.delete()
        }
    }

    @Test
    fun replayingASongAddsAnotherPlayAndAnotherSample() = runBlocking {
        val file = newFile()
        try {
            val store = store(file)
            store.recordPlay("a", at = 100L)
            store.recordPlay("a", at = 200L)

            val snapshot = store.snapshot.value

            assertEquals(2, snapshot.totals.getValue("a").playCount)
            assertEquals(2, snapshot.samples.size)
            assertEquals(200L, snapshot.samples.first().startedAt)
        } finally {
            file.delete()
        }
    }

    @Test
    fun listeningIsAddedToTheMostRecentSampleOfThatSong() = runBlocking {
        val file = newFile()
        try {
            val store = store(file)
            store.recordPlay("a", at = 100L)
            store.recordPlay("b", at = 200L)
            store.addListening("a", 10_000L, at = 300L)
            store.addListening("a", 5_000L, at = 400L)

            val snapshot = store.snapshot.value

            assertEquals(15_000L, snapshot.totals.getValue("a").listenedMs)
            assertEquals(0L, snapshot.totals.getValue("b").listenedMs)
            assertEquals(15_000L, snapshot.samples.first { it.songId == "a" }.listenedMs)
        } finally {
            file.delete()
        }
    }

    @Test
    fun listeningWithoutAPlayIsIgnored() = runBlocking {
        val file = newFile()
        try {
            val store = store(file)
            store.addListening("nunca-reproducida", 60_000L)

            assertTrue(store.snapshot.value.totals.isEmpty())
            assertTrue(store.snapshot.value.samples.isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun trimsSamplesButKeepsHistoricalTotals() = runBlocking {
        val file = newFile()
        try {
            val store = store(file, maxSamples = 3)
            (1..5).forEach { store.recordPlay("s$it", at = it.toLong()) }

            val snapshot = store(file, maxSamples = 3).load()

            assertEquals(5, snapshot.totals.size)
            assertEquals(3, snapshot.samples.size)
            assertEquals("s5", snapshot.samples.first().songId)
            assertTrue(snapshot.samples.none { it.songId == "s2" })
        } finally {
            file.delete()
        }
    }

    @Test
    fun dailyAggregatesSurviveSampleTrimming() = runBlocking {
        val file = newFile()
        val base = System.currentTimeMillis() - 5L * 86_400_000L
        try {
            val store = store(file, maxSamples = 1)
            (1..5).forEach { store.recordPlay("a", at = base + it * 86_400_000L) }

            val snapshot = store(file, maxSamples = 1).load()

            assertEquals(1, snapshot.samples.size)
            assertEquals(5, snapshot.daily.sumOf { it.playCount })
        } finally {
            file.delete()
        }
    }

    @Test
    fun ignoresBlankIdentifiersAndNonPositiveListening() = runBlocking {
        val file = newFile()
        try {
            val store = store(file)
            store.recordPlay("  ")
            store.recordPlay("a", at = 100L)
            store.addListening("a", 0L)
            store.addListening("a", -500L)

            assertEquals(1, store.snapshot.value.totals.size)
            assertEquals(0L, store.snapshot.value.totals.getValue("a").listenedMs)
        } finally {
            file.delete()
        }
    }

    @Test
    fun corruptAndEmptyFilesBehaveAsAnEmptyRegister() = runBlocking {
        val corrupt = newFile()
        val empty = newFile()
        try {
            corrupt.writeText("{esto no es json")
            empty.writeText("")

            assertTrue(store(corrupt).load().totals.isEmpty())
            assertTrue(store(empty).load().samples.isEmpty())
        } finally {
            corrupt.delete()
            empty.delete()
        }
    }

    @Test
    fun codecRoundTripPreservesSnapshot() {
        val codec = PlaybackStatsCodec()
        val snapshot = PlaybackStatsSnapshot(
            totals = mapOf(
                "content://a/1" to SongPlaybackStats(3, 90_000L, 1_500L),
                "content://a/2\"con comillas" to SongPlaybackStats(1, 0L, 2_000L)
            ),
            samples = listOf(
                PlaybackSample("content://a/1", 1_000L, 45_000L),
                PlaybackSample("content://a/2\"con comillas", 2_000L)
            ),
            daily = listOf(
                DailyPlaybackAggregate(0L, "content://a/1", 3, 90_000L)
            )
        )

        assertEquals(snapshot, codec.decode(codec.encode(snapshot)))
    }

    @Test
    fun scrobbleRuleFollowsLastFmThresholds() {
        assertEquals(null, ScrobbleRule.thresholdFor(10_000L))
        assertEquals(null, ScrobbleRule.thresholdFor(30_000L))
        assertEquals(15_000L, ScrobbleRule.thresholdFor(30_001L))
        assertEquals(90_000L, ScrobbleRule.thresholdFor(180_000L))
        assertEquals(240_000L, ScrobbleRule.thresholdFor(600_000L))
    }

    @Test
    fun codecRejectsAnIncompatibleVersion() {
        val encoded = PlaybackStatsCodec(version = 1)
            .encode(PlaybackStatsSnapshot(mapOf("a" to SongPlaybackStats(1))))

        assertTrue(PlaybackStatsCodec(version = 2).decode(encoded).totals.isEmpty())
    }
}
