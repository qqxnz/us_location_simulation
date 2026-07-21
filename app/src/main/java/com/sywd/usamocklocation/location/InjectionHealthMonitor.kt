package com.sywd.usamocklocation.location

data class HealthActions(
    val restartSystem: Boolean,
    val restartFused: Boolean,
    val reacquireWakeLock: Boolean,
)

/** Pure health policy shared by the service and deterministic unit tests. */
class InjectionHealthMonitor(
    private val systemStaleAfterMs: Long = 5_000L,
    private val fusedStaleAfterMs: Long = 12_000L,
) {
    fun evaluate(
        nowElapsedMs: Long,
        lastSystemSuccessMs: Long,
        lastFusedSuccessMs: Long,
        wakeLockHeld: Boolean,
    ) = HealthActions(
        restartSystem = nowElapsedMs - lastSystemSuccessMs > systemStaleAfterMs,
        restartFused = nowElapsedMs - lastFusedSuccessMs > fusedStaleAfterMs,
        reacquireWakeLock = !wakeLockHeld,
    )
}
