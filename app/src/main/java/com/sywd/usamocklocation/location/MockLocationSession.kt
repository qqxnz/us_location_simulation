package com.sywd.usamocklocation.location

import com.sywd.usamocklocation.data.StateCapital
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Pure session coordinator kept separate from Android Service lifecycle for deterministic tests. */
class MockLocationSession(
    private val injector: LocationInjector,
) {
    private val mutex = Mutex()

    var activeCapital: StateCapital? = null
        private set

    suspend fun start(capital: StateCapital) = mutex.withLock {
        if (activeCapital?.code == capital.code) return@withLock injector.inject(capital)

        try {
            injector.stop()
        } catch (_: Throwable) {
            // Best-effort cleanup before establishing a new session.
        }
        try {
            injector.start()
            injector.inject(capital)
            activeCapital = capital
        } catch (error: Throwable) {
            try {
                injector.stop()
            } catch (_: Throwable) {
                // Preserve the original startup failure.
            }
            activeCapital = null
            throw error
        }
    }

    suspend fun tick(): Unit = mutex.withLock {
        val capital = activeCapital
            ?: throw MockLocationUnavailableException("模拟位置会话尚未启动。")
        injector.inject(capital)
    }

    suspend fun stop() = mutex.withLock {
        activeCapital = null
        injector.stop()
    }
}
