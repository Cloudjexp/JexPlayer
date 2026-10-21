package com.cloudjex.jexplayer.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRestorePolicyTest {
    @Test
    fun findsTheSavedSongByMediaIdInsteadOfUsingAnOldIndex() {
        assertEquals(1, findMediaIdIndex(listOf("first", "saved", "last"), "saved"))
    }

    @Test
    fun returnsInvalidIndexWhenTheQueueDoesNotContainTheSavedSong() {
        assertEquals(-1, findMediaIdIndex(emptyList(), "saved"))
        assertEquals(-1, findMediaIdIndex(listOf("first"), "saved"))
    }
}
