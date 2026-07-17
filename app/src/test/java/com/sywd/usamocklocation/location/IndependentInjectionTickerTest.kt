package com.sywd.usamocklocation.location

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IndependentInjectionTickerTest {
    @Test
    fun `fused timeout does not stop system ticks`(): Unit = runTest {
        val ticker = IndependentInjectionTicker(intervalMs = 100L, fusedTimeoutMs = 50L)
        var systemTicks = 0
        var fusedFailures = 0

        val systemJob = launch {
            ticker.runSystem(
                tick = { systemTicks++ },
                onSuccess = {},
            )
        }
        val fusedJob = launch {
            ticker.runFused(
                tick = { awaitCancellation() },
                onSuccess = {},
                onFailure = { fusedFailures++ },
            )
        }

        advanceTimeBy(460L)
        runCurrent()

        assertTrue(systemTicks >= 4)
        assertTrue(fusedFailures >= 3)
        systemJob.cancel()
        fusedJob.cancel()
    }

    @Test
    fun `fused exception is isolated and later ticks recover`(): Unit = runTest {
        val ticker = IndependentInjectionTicker(intervalMs = 100L, fusedTimeoutMs = 50L)
        var attempts = 0
        var successes = 0
        var failures = 0
        val job = launch {
            ticker.runFused(
                tick = {
                    attempts++
                    if (attempts == 1) error("transient")
                },
                onSuccess = { successes++ },
                onFailure = { failures++ },
            )
        }

        advanceTimeBy(250L)
        runCurrent()

        assertEquals(1, failures)
        assertTrue(successes >= 2)
        job.cancel()
    }

    @Test
    fun `three consecutive failures request one rebuild and success resets window`() {
        val tracker = ConsecutiveFailureTracker(rebuildThreshold = 3)

        assertEquals(false, tracker.recordFailure())
        assertEquals(false, tracker.recordFailure())
        tracker.recordSuccess()
        assertEquals(false, tracker.recordFailure())
        assertEquals(false, tracker.recordFailure())
        assertEquals(true, tracker.recordFailure())
        assertEquals(false, tracker.recordFailure())
    }
}
