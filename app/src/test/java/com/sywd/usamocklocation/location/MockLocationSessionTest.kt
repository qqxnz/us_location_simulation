package com.sywd.usamocklocation.location

import com.sywd.usamocklocation.data.StateCapital
import com.sywd.usamocklocation.data.StateCapitals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.runBlocking

class MockLocationSessionTest {
    private val california = StateCapitals.find("CA")!!
    private val texas = StateCapitals.find("TX")!!

    @Test
    fun `start inject tick and stop follow lifecycle`(): Unit = runBlocking {
        val injector = FakeLocationInjector()
        val session = MockLocationSession(injector)

        session.start(california)
        session.tick()
        session.stop()

        assertEquals(1, injector.startCount)
        assertEquals(listOf("CA", "CA"), injector.injectedCodes)
        assertEquals(2, injector.stopCount) // pre-start cleanup and explicit stop
        assertNull(session.activeCapital)
    }

    @Test
    fun `repeated start does not recreate provider`(): Unit = runBlocking {
        val injector = FakeLocationInjector()
        val session = MockLocationSession(injector)

        session.start(california)
        session.start(california)

        assertEquals(1, injector.startCount)
        assertEquals(listOf("CA", "CA"), injector.injectedCodes)
        assertEquals("CA", session.activeCapital?.code)
    }

    @Test
    fun `switching state recreates provider and injects new capital`(): Unit = runBlocking {
        val injector = FakeLocationInjector()
        val session = MockLocationSession(injector)

        session.start(california)
        session.start(texas)

        assertEquals(2, injector.startCount)
        assertEquals(listOf("CA", "TX"), injector.injectedCodes)
        assertEquals("TX", session.activeCapital?.code)
    }

    @Test
    fun `provider failure clears active session and cleans up`(): Unit = runBlocking {
        val injector = FakeLocationInjector(failOnStart = true)
        val session = MockLocationSession(injector)

        expectThrows<SecurityException> { session.start(california) }
        assertNull(session.activeCapital)
        assertEquals(2, injector.stopCount)
    }

    @Test
    fun `tick before start fails`(): Unit = runBlocking {
        val session = MockLocationSession(FakeLocationInjector())
        expectThrows<MockLocationUnavailableException> { session.tick() }
    }

    private class FakeLocationInjector(
        private val failOnStart: Boolean = false,
    ) : LocationInjector {
        var startCount = 0
        var stopCount = 0
        val injectedCodes = mutableListOf<String>()

        override suspend fun start() {
            startCount++
            if (failOnStart) throw SecurityException("not selected")
        }

        override suspend fun inject(capital: StateCapital) {
            injectedCodes += capital.code
        }

        override suspend fun stop() {
            stopCount++
        }
    }
}

private inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return error
        throw AssertionError("Expected ${T::class.java.simpleName}, got ${error.javaClass.simpleName}", error)
    }
    throw AssertionError("Expected ${T::class.java.simpleName} to be thrown")
}
