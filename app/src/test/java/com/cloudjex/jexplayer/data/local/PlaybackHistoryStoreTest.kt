package com.cloudjex.jexplayer.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaybackHistoryStoreTest {

    private fun newFile(): File = File(
        System.getProperty("java.io.tmpdir"),
        "jexplayer-history-${System.nanoTime()}.json"
    )

    private fun store(file: File, limit: Int = PlaybackHistoryStore.MAX_ENTRIES) =
        PlaybackHistoryStore(
            file = file,
            ioDispatcher = Dispatchers.Unconfined,
            limit = limit
        )

    @Test
    fun survivesARestart() = runBlocking {
        val file = newFile()
        try {
            store(file).record("content://song/a", playedAt = 1_000L)

            val reopened = store(file)
            val entries = reopened.load()

            assertEquals(1, entries.size)
            assertEquals("content://song/a", entries.single().songId)
            assertEquals(1_000L, entries.single().playedAt)
        } finally {
            file.delete()
        }
    }

    @Test
    fun replayingASongMovesItToTheFrontAndUpdatesTheTimestamp() = runBlocking {
        val file = newFile()
        try {
            val store = store(file)
            store.record("a", playedAt = 100L)
            store.record("b", playedAt = 200L)
            store.record("a", playedAt = 300L)

            val entries = store.entries.value

            assertEquals(2, entries.size)
            assertEquals("a", entries.first().songId)
            assertEquals(300L, entries.first().playedAt)
        } finally {
            file.delete()
        }
    }

    @Test
    fun keepsAtMostTwentyUniqueSongs() = runBlocking {
        val file = newFile()
        try {
            val store = store(file)
            (1..25).forEach { store.record("song-$it", playedAt = it.toLong()) }

            val entries = store.entries.value

            assertEquals(PlaybackHistoryStore.MAX_ENTRIES, entries.size)
            assertEquals("song-25", entries.first().songId)
            assertTrue(entries.none { it.songId == "song-5" })
        } finally {
            file.delete()
        }
    }

    @Test
    fun keepsIdsThatAreNoLongerInTheLibrary() = runBlocking {
        val file = newFile()
        try {
            val store = store(file)
            store.record("removed-folder/song", playedAt = 10L)

            val reopened = store(file)

            assertEquals("removed-folder/song", reopened.load().single().songId)
        } finally {
            file.delete()
        }
    }

    @Test
    fun ignoresBlankIdentifiers() = runBlocking {
        val file = newFile()
        try {
            val store = store(file)
            store.record("", playedAt = 10L)
            store.record("   ", playedAt = 20L)

            assertTrue(store.entries.value.isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun corruptFileBehavesAsAnEmptyHistory() = runBlocking {
        val file = newFile()
        try {
            file.writeText("{esto no es json")
            assertTrue(store(file).load().isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun emptyFileBehavesAsAnEmptyHistory() = runBlocking {
        val file = newFile()
        try {
            file.writeText("")
            assertTrue(store(file).load().isEmpty())
        } finally {
            file.delete()
        }
    }

    @Test
    fun codecRoundTripPreservesEntries() {
        val codec = PlaybackHistoryCodec()
        val entries = listOf(
            PlaybackHistoryEntry("content://a/1", 1_000L),
            PlaybackHistoryEntry("content://a/2\"con comillas", 2_000L)
        )

        assertEquals(entries, codec.decode(codec.encode(entries)))
    }

    @Test
    fun codecRejectsAnIncompatibleVersion() {
        val encoded = PlaybackHistoryCodec(version = 1)
            .encode(listOf(PlaybackHistoryEntry("a", 1L)))

        assertTrue(PlaybackHistoryCodec(version = 2).decode(encoded).isEmpty())
    }
}
