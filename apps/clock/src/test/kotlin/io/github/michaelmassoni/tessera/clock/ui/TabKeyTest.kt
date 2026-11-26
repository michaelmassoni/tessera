package io.github.michaelmassoni.tessera.clock.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TabKeyTest {
    @Test
    fun `each tab has its own key`() {
        val keys = ClockTab.entries.map { tabKey(it, keypad = false) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `the timer keypad is apart from the timer list, other tabs ignore it`() {
        assertNotEquals(tabKey(ClockTab.Timer, keypad = true), tabKey(ClockTab.Timer, keypad = false))
        assertEquals(tabKey(ClockTab.Alarms, keypad = true), tabKey(ClockTab.Alarms, keypad = false))
    }
}
