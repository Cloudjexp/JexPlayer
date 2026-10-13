package com.cloudjex.jexplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class CircularQueueTest {

    @Test
    fun forwardFromLastWrapsToFirst() {
        assertEquals(0, CircularQueue.wrapTarget(itemCount = 3, forward = true))
    }

    @Test
    fun backwardFromFirstWrapsToLast() {
        assertEquals(2, CircularQueue.wrapTarget(itemCount = 3, forward = false))
    }

    @Test
    fun singleItemWrapsToItself() {
        assertEquals(0, CircularQueue.wrapTarget(itemCount = 1, forward = true))
        assertEquals(0, CircularQueue.wrapTarget(itemCount = 1, forward = false))
    }

    @Test
    fun emptyQueueHasNoTarget() {
        assertEquals(CircularQueue.NONE, CircularQueue.wrapTarget(itemCount = 0, forward = true))
        assertEquals(CircularQueue.NONE, CircularQueue.wrapTarget(itemCount = 0, forward = false))
    }
}
