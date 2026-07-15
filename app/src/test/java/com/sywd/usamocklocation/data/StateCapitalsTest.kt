package com.sywd.usamocklocation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateCapitalsTest {
    @Test
    fun `contains fifty states plus DC with unique codes`() {
        assertEquals(51, StateCapitals.all.size)
        assertEquals(51, StateCapitals.all.map { it.code }.toSet().size)
        assertTrue(StateCapitals.all.any { it.code == "DC" })
    }

    @Test
    fun `every coordinate and code is valid`() {
        StateCapitals.all.forEach { capital ->
            assertTrue("Invalid code ${capital.code}", capital.code.matches(Regex("[A-Z]{2}")))
            assertTrue("Invalid latitude for ${capital.code}", capital.latitude in -90.0..90.0)
            assertTrue("Invalid longitude for ${capital.code}", capital.longitude in -180.0..180.0)
        }
    }

    @Test
    fun `representative capitals match expected coordinates`() {
        val california = StateCapitals.find("CA")!!
        val newYork = StateCapitals.find("NY")!!
        val dc = StateCapitals.find("DC")!!

        assertEquals("Sacramento", california.capitalNameEn)
        assertEquals(38.576668, california.latitude, 0.000001)
        assertEquals("Albany", newYork.capitalNameEn)
        assertEquals(-73.757874, newYork.longitude, 0.000001)
        assertEquals("Washington, D.C.", dc.stateNameEn)
    }

    @Test
    fun `search matches bilingual state capital and code`() {
        val texas = StateCapitals.find("TX")!!

        assertTrue(texas.matches("texas"))
        assertTrue(texas.matches("得克萨斯"))
        assertTrue(texas.matches("Austin"))
        assertTrue(texas.matches("tx"))
    }
}
