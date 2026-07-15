package com.sywd.usamocklocation.location

import com.sywd.usamocklocation.data.StateCapital

/** Pure session coordinator kept separate from Android Service lifecycle for deterministic tests. */
class MockLocationSession(
    private val injector: LocationInjector,
) {
    var activeCapital: StateCapital? = null
        private set

    @Synchronized
    fun start(capital: StateCapital) {
        if (activeCapital?.code == capital.code) {
            injector.inject(capital)
            return
        }

        runCatching { injector.stop() }
        try {
            injector.start()
            injector.inject(capital)
            activeCapital = capital
        } catch (error: Throwable) {
            runCatching { injector.stop() }
            activeCapital = null
            throw error
        }
    }

    @Synchronized
    fun tick() {
        activeCapital?.let(injector::inject)
            ?: throw MockLocationUnavailableException("模拟位置会话尚未启动。")
    }

    @Synchronized
    fun stop() {
        activeCapital = null
        injector.stop()
    }
}
