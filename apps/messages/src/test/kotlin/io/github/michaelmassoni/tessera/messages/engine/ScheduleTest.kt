package io.github.michaelmassoni.tessera.messages.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTest {
    private fun at(
        hour: Int,
        minute: Int = 0,
    ) = LocalDateTime.of(2026, 11, 19, hour, minute)

    @Test fun `morning offers all three`() {
        val slots = ScheduleTimes.suggestions(at(9))
        assertEquals(listOf(ScheduleSlot.LaterToday, ScheduleSlot.Tonight, ScheduleSlot.TomorrowMorning), slots.map { it.first })
        assertEquals(at(18), slots[0].second)
        assertEquals(LocalDateTime.of(2026, 11, 20, 8, 0), slots[2].second)
    }

    @Test fun `slots too close or past are dropped`() {
        assertEquals(listOf(ScheduleSlot.Tonight, ScheduleSlot.TomorrowMorning), ScheduleTimes.suggestions(at(17, 45)).map { it.first })
        assertEquals(listOf(ScheduleSlot.TomorrowMorning), ScheduleTimes.suggestions(at(22)).map { it.first })
    }

    @Test fun `picked times must be ahead`() {
        assertFalse(ScheduleTimes.isValid(at(9), at(9)))
        assertTrue(ScheduleTimes.isValid(at(9, 5), at(9)))
    }

    @Test fun `a scheduled message matches its conversation however numbers are written`() {
        val m = ScheduledMessage(1, listOf("0412 345 678", "+61 400 000 000"), "hi", 0)
        assertTrue(m.isFor(listOf("+61400000000", "0412345678")))
        assertFalse(m.isFor(listOf("0412345678")))
    }
}
