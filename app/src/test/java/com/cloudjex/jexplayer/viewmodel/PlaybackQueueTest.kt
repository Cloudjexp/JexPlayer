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
            context = PlaybackContext.Favorites,
            libraryIds = library,
            favoriteIds = setOf("a", "c", "missing"),
            customLibrarySongIds = emptyList()
        )

        assertEquals(listOf("a", "c"), queue)
    }

    @Test
    fun nextAndPreviousStayInsideFavoritesAndWrap() {
        val favorites = PlaybackQueue.idsFor(
            context = PlaybackContext.Favorites,
            libraryIds = library,
            favoriteIds = setOf("a", "c"),
            customLibrarySongIds = emptyList()
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

    @Test
    fun customQueueUsesOnlyItsSongsInPersistedOrder() {
        val queue = PlaybackQueue.idsFor(
            context = PlaybackContext.Custom("library-1"),
            libraryIds = library,
            favoriteIds = setOf("a"),
            customLibrarySongIds = listOf("c", "a", "missing")
        )

        assertEquals(listOf("c", "a"), queue)
    }

    @Test
    fun customQueueCanBeEmpty() {
        val queue = PlaybackQueue.idsFor(
            context = PlaybackContext.Custom("library-1"),
            libraryIds = library,
            favoriteIds = emptySet(),
            customLibrarySongIds = emptyList()
        )

        assertEquals(emptyList<String>(), queue)
    }

    @Test
    fun nextAndPreviousStayInsideCustomQueue() {
        val custom = PlaybackQueue.idsFor(
            context = PlaybackContext.Custom("library-1"),
            libraryIds = library,
            favoriteIds = emptySet(),
            customLibrarySongIds = listOf("c", "a")
        )

        assertEquals("a", PlaybackQueue.navigateIds(custom, "c", 1, false))
        assertEquals("c", PlaybackQueue.navigateIds(custom, "a", -1, false))
    }

    @Test
    fun shuffleNeverLeavesCustomQueue() {
        val custom = listOf("a", "c")
        repeat(20) {
            assertTrue(
                PlaybackQueue.navigateIds(custom, "a", 1, true, Random(it)) in setOf("a", "c")
            )
        }
    }

    @Test
    fun automaticAdvanceStaysInsideCustomQueue() {
        val custom = listOf("a", "c")

        assertEquals("c", PlaybackQueue.automaticNextIds(custom, "a", RepeatMode.OFF, false))
        assertEquals(null, PlaybackQueue.automaticNextIds(custom, "c", RepeatMode.OFF, false))
        assertEquals("a", PlaybackQueue.automaticNextIds(custom, "c", RepeatMode.ALL, false))
    }

    @Test
    fun resolveIndexKeepsTheCurrentSongWhenItStillExists() {
        assertEquals(
            1,
            PlaybackQueue.resolveIndexAfterChange(
                ids = listOf("a", "b", "c"),
                currentId = "b",
                previousIndex = 0
            )
        )
    }

    @Test
    fun resolveIndexUsesTheNearestIndexWhenTheCurrentSongDisappears() {
        assertEquals(
            1,
            PlaybackQueue.resolveIndexAfterChange(
                ids = listOf("a", "b", "c"),
                currentId = "missing",
                previousIndex = 1
            )
        )
    }

    @Test
    fun resolveIndexClampsThePreviousIndexToTheNewQueue() {
        assertEquals(
            2,
            PlaybackQueue.resolveIndexAfterChange(
                ids = listOf("a", "b", "c"),
                currentId = "missing",
                previousIndex = 99
            )
        )
    }

    @Test
    fun libraryAndFavoritesIgnoreCustomLibraryIds() {
        assertEquals(
            library,
            PlaybackQueue.idsFor(
                context = PlaybackContext.Library,
                libraryIds = library,
                favoriteIds = setOf("a"),
                customLibrarySongIds = listOf("c")
            )
        )
        assertEquals(
            listOf("a"),
            PlaybackQueue.idsFor(
                context = PlaybackContext.Favorites,
                libraryIds = library,
                favoriteIds = setOf("a"),
                customLibrarySongIds = listOf("c")
            )
        )
    }

    @Test
    fun resolveIndexStartsWithTheFirstSongWhenThereWasNoPreviousIndex() {
        assertEquals(
            0,
            PlaybackQueue.resolveIndexAfterChange(
                ids = listOf("a", "b"),
                currentId = "missing",
                previousIndex = -2
            )
        )
    }

    @Test
    fun resolveIndexReturnsMinusOneForAnEmptyQueue() {
        assertEquals(
            -1,
            PlaybackQueue.resolveIndexAfterChange(
                ids = emptyList(),
                currentId = "a",
                previousIndex = 0
            )
        )
    }
}
