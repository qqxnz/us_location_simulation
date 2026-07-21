package com.sywd.usamocklocation.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectionHealthMonitorTest {
    private val monitor = InjectionHealthMonitor(
        systemStaleAfterMs = 5_000L,
        fusedStaleAfterMs = 12_000L,
    )

    @Test
    fun `healthy channels require no recovery`() {
        val actions = monitor.evaluate(
            nowElapsedMs = 20_000L,
            lastSystemSuccessMs = 19_000L,
            lastFusedSuccessMs = 18_000L,
            wakeLockHeld = true,
        )

        assertFalse(actions.restartSystem)
        assertFalse(actions.restartFused)
        assertFalse(actions.reacquireWakeLock)
    }

    @Test
    fun `stale channels are recovered independently`() {
        val actions = monitor.evaluate(
            nowElapsedMs = 20_000L,
            lastSystemSuccessMs = 10_000L,
            lastFusedSuccessMs = 15_000L,
            wakeLockHeld = true,
        )

        assertTrue(actions.restartSystem)
        assertFalse(actions.restartFused)
    }

    @Test
    fun `lost wake lock requests reacquisition`() {
        val actions = monitor.evaluate(
            nowElapsedMs = 20_000L,
            lastSystemSuccessMs = 20_000L,
            lastFusedSuccessMs = 20_000L,
            wakeLockHeld = false,
        )

        assertTrue(actions.reacquireWakeLock)
    }
}
