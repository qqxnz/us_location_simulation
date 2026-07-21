package com.sywd.usamocklocation.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout

/** Runs system and Fused ticks in separate jobs so one channel can never block the other. */
class IndependentInjectionTicker(
    private val intervalMs: Long,
    private val fusedTimeoutMs: Long,
) {
    suspend fun runSystem(
        tick: suspend () -> Unit,
        onSuccess: () -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            delay(intervalMs)
            tick()
            onSuccess()
        }
    }

    suspend fun runFused(
        tick: suspend () -> Unit,
        onSuccess: () -> Unit,
        onFailure: suspend (Throwable) -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            try {
                withTimeout(fusedTimeoutMs) { tick() }
                onSuccess()
            } catch (timeout: TimeoutCancellationException) {
                onFailure(timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                onFailure(error)
            }
            delay(intervalMs)
        }
    }
}

class ConsecutiveFailureTracker(
    private val rebuildThreshold: Int,
) {
    private var failures = 0

    fun recordSuccess() {
        failures = 0
    }

    /** Returns true exactly when the threshold is reached, then starts a new window. */
    fun recordFailure(): Boolean {
        failures++
        if (failures < rebuildThreshold) return false
        failures = 0
        return true
    }
}
