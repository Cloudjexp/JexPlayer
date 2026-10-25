package com.cloudjex.jexplayer.viewmodel

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueTest {
    private val library = listOf("a", "b", "c")

    @Test
    fun favoritesQueueContainsOnlyFavorites() {
        val queue = PlaybackQueue.idsFor(
            PlaybackContext.Favorites,
            library,
            setOf("a", "c", "missing")
        )

        assertEquals(listOf("a", "c"), queue)
    }

    @Test
    fun nextAndPreviousStayInsideFavoritesAndWrap() {
        val favorites = PlaybackQueue.idsFor(
            PlaybackContext.Favorites,
            library,
            setOf("a", "c")
        )

        assertEquals("a", PlaybackQueue.navigateIds(favorites, "c", 1, false))
        assertEquals("c", PlaybackQueue.navigateIds(favorites, "a", -1, false))
    }

    @Test
    fun shuffleNeverLeavesFavorites() {
        val favorites = listOf("a", "c")
        repeat(20) {
            assertTrue(
                PlaybackQueue.navigateIds(favorites, "a", 1, true, Random(it)) in setOf("a", "c")
            )
        }
    }

    @Test
    fun automaticAdvanceHonorsRepeatAndEndOfList() {
        val favorites = listOf("a", "c")

        assertEquals("c", PlaybackQueue.automaticNextIds(favorites, "a", RepeatMode.OFF, false))
        assertEquals(null, PlaybackQueue.automaticNextIds(favorites, "c", RepeatMode.OFF, false))
        assertEquals("a", PlaybackQueue.automaticNextIds(favorites, "c", RepeatMode.ALL, false))
        assertEquals(null, PlaybackQueue.automaticNextIds(favorites, "a", RepeatMode.ONE, false))
    }

    @Test
    fun emptyAndSingleQueuesAreSafe() {
        assertEquals(null, PlaybackQueue.navigateIds(emptyList(), "a", 1, false))
        assertEquals("a", PlaybackQueue.navigateIds(listOf("a"), "a", 1, false))
        assertEquals("a", PlaybackQueue.automaticNextIds(
            listOf("a"), "a", RepeatMode.ALL, false
        ))
    }
}
