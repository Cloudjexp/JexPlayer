package com.cloudjex.jexplayer.data.artwork

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkRequestCoordinatorTest {

    @Test
    fun concurrentRequestsForSameKeyShareOneLoad() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val gate = CompletableDeferred<Unit>()
            val started = CompletableDeferred<Unit>()
            val loads = AtomicInteger()
            val secondLoadCalled = AtomicBoolean(false)
            val coordinator = ArtworkRequestCoordinator<String, String>(scope) { _, load ->
                loads.incrementAndGet()
                started.complete(Unit)
                gate.await()
                load()
            }

            val first = async { coordinator.await("album-cover") { "artwork" } }
            started.await()

            val second = async {
                coordinator.await("album-cover") {
                    secondLoadCalled.set(true)
                    "other"
                }
            }
            delay(50)
            assertFalse(secondLoadCalled.get())
            assertEquals(1, loads.get())

            gate.complete(Unit)
            assertEquals("artwork", first.await())
            assertEquals("artwork", second.await())
            assertEquals(1, loads.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellingLastWaiterCancelsSharedWorkAndAllowsRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val loads = AtomicInteger()
            val coordinator = ArtworkRequestCoordinator<String, String>(scope) { _, _ ->
                val attempt = loads.incrementAndGet()
                if (attempt == 1) {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                } else {
                    "retried"
                }
            }

            val job = launch { coordinator.await("album-cover") { "unused" } }
            started.await()
            job.cancelAndJoin()

            withTimeout(2_000) { cancelled.await() }
            yield()
            assertEquals(1, loads.get())

            val retried = async { coordinator.await("album-cover") { "unused" } }
            assertEquals("retried", withTimeout(2_000) { retried.await() })
            assertEquals(2, loads.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun oneRemainingWaiterKeepsSharedWorkAlive() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val gate = CompletableDeferred<Unit>()
            val started = CompletableDeferred<Unit>()
            val loads = AtomicInteger()
            val coordinator = ArtworkRequestCoordinator<String, String>(scope) { _, load ->
                loads.incrementAndGet()
                started.complete(Unit)
                gate.await()
                load()
            }

            val cancelledWaiter = launch { coordinator.await("album-cover") { "artwork" } }
            started.await()
            val remaining = async { coordinator.await("album-cover") { "artwork" } }
            yield()

            cancelledWaiter.cancelAndJoin()
            gate.complete(Unit)

            assertEquals("artwork", withTimeout(2_000) { remaining.await() })
            assertEquals(1, loads.get())
        } finally {
            scope.cancel()
        }
    }
}
