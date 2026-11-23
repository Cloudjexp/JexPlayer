package com.cloudjex.jexplayer.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListeningTrackerTest {

    @Test
    fun accumulatesPositionAdvances() {
        val tracker = ListeningTracker()
        tracker.switchTo("a", 0L)

        tracker.onSample("a", 500L, isPlaying = true)
        tracker.onSample("a", 1_000L, isPlaying = true)

        assertEquals(1_000L, tracker.take()?.listenedMs)
        assertNull(tracker.take())
    }

    @Test
    fun clampsBigForwardJumpsAndIgnoresBackwardsOnes() {
        val tracker = ListeningTracker(maxStepMs = 1_000L)
        tracker.switchTo("a", 0L)

        tracker.onSample("a", 1_000L, isPlaying = true)
        tracker.onSample("a", 100_000L, isPlaying = true) // salto: 1.000 ms, no 99.000
        tracker.onSample("a", 50_000L, isPlaying = true)  // hacia atrás: se descarta

        assertEquals(2_000L, tracker.peek()?.listenedMs)
    }

    @Test
    fun seeksDoNotCountAsListeningTime() {
        val tracker = ListeningTracker()
        tracker.switchTo("a", 0L)

        tracker.onSample("a", 1_000L, isPlaying = true)
        tracker.onSeek(10_000L)
        tracker.onSample("a", 10_500L, isPlaying = true)

        assertEquals(1_500L, tracker.take()?.listenedMs)
    }

    @Test
    fun switchingSongsClosesThePreviousChunkAndResetsTheBaseline() {
        val tracker = ListeningTracker(maxStepMs = 5_000L)
        tracker.switchTo("a", 0L)
        tracker.onSample("a", 3_000L, isPlaying = true)

        val finished = tracker.switchTo("b", 50L)

        assertEquals("a", finished?.songId)
        assertEquals(3_000L, finished?.listenedMs)

        tracker.onSample("b", 550L, isPlaying = true)
        val chunkB = tracker.take()
        assertEquals("b", chunkB?.songId)
        assertEquals(500L, chunkB?.listenedMs)
    }

    @Test
    fun pausedSamplesDoNotAccumulateAndAreFlushed() {
        val tracker = ListeningTracker()
        tracker.switchTo("a", 0L)
        tracker.onSample("a", 1_000L, isPlaying = true)

        tracker.onSample("a", 60_000L, isPlaying = false)

        assertEquals(1_000L, tracker.take()?.listenedMs)
    }

    @Test
    fun listenedTotalSurvivesFlushesUntilTheSongChanges() {
        val tracker = ListeningTracker()
        tracker.switchTo("a", 0L)
        tracker.onSample("a", 1_000L, isPlaying = true)

        assertEquals(1_000L, tracker.currentSongListenedMs())
        assertEquals(1_000L, tracker.take()?.listenedMs)

        tracker.onSample("a", 2_000L, isPlaying = true)
        assertEquals(2_000L, tracker.currentSongListenedMs())

        tracker.switchTo("b", 0L)
        assertEquals(0L, tracker.currentSongListenedMs())
    }

    @Test
    fun blankSongIdentifiersAreIgnored() {
        val tracker = ListeningTracker()
        tracker.switchTo("a", 0L)
        tracker.onSample(" ", 5_000L, isPlaying = true)

        assertNull(tracker.peek())
    }
}
