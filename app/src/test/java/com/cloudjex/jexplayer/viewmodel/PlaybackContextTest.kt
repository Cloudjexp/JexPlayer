package com.cloudjex.jexplayer.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackContextTest {

    @Test
    fun metadataRoundTripsEveryContext() {
        val contexts = listOf(
            PlaybackContext.Library,
            PlaybackContext.Favorites,
            PlaybackContext.Custom("library-1")
        )

        contexts.forEach { context ->
            val type = PlaybackContext.metadataType(context)
            val libraryId = PlaybackContext.metadataLibraryId(context)

            assertEquals(context, PlaybackContext.fromMetadata(type, libraryId))
        }
    }

    @Test
    fun onlyCustomContextsCarryALibraryId() {
        assertNull(PlaybackContext.metadataLibraryId(PlaybackContext.Library))
        assertNull(PlaybackContext.metadataLibraryId(PlaybackContext.Favorites))
        assertEquals(
            "library-1",
            PlaybackContext.metadataLibraryId(PlaybackContext.Custom("library-1"))
        )
    }

    @Test
    fun customContextWithoutLibraryIdIsRejected() {
        assertNull(PlaybackContext.fromMetadata("custom", null))
        assertNull(PlaybackContext.fromMetadata("custom", "   "))
    }

    @Test
    fun unknownOrMissingTypeIsRejected() {
        assertNull(PlaybackContext.fromMetadata(null, null))
        assertNull(PlaybackContext.fromMetadata("unknown", "library-1"))
    }

    @Test
    fun customContextsWithDifferentLibraryIdsAreDifferent() {
        assertNotEquals(
            PlaybackContext.Custom("library-1"),
            PlaybackContext.Custom("library-2")
        )
    }
}
