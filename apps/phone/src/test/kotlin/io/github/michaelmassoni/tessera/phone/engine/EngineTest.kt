package io.github.michaelmassoni.tessera.phone.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class EngineTest {
    @Test fun `dialpad letters, long press and delete`() {
        assertEquals("PQRS", Dialpad.letters("7"))
        assertEquals("", Dialpad.letters("1"))
        assertEquals("+", Dialpad.longPress("0"))
        assertEquals("5", Dialpad.longPress("5"))
        assertEquals("041", Dialpad.delete("0412"))
        assertEquals("", Dialpad.delete(""))
    }

    @Test fun `number matching uses digits only and needs two of them`() {
        assertTrue(NumberMatch.matches("412 3", "+61 412 345 678"))
        assertFalse(NumberMatch.matches("4", "+61 412 345 678"))
        assertFalse(NumberMatch.matches("999", "+61 412 345 678"))
    }

    private fun at(
        d: Int,
        h: Int,
    ) = LocalDateTime.of(2026, 11, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun `recents group by day and collapse repeats`() {
        val calls =
            listOf(
                CallEntry(1, "0412 345 678", "Ada", CallType.Missed, at(19, 10), 0),
                CallEntry(2, "0412345678", "Ada", CallType.Missed, at(19, 9), 0),
                CallEntry(3, "0412345678", "Ada", CallType.Incoming, at(19, 8), 60),
                CallEntry(4, "555", null, CallType.Outgoing, at(18, 12), 30),
            )
        val sections = CallLogGrouping.sections(calls, ZoneOffset.UTC)
        assertEquals(2, sections.size)
        assertEquals(listOf(2, 1), sections[0].second.map { it.count })
        assertEquals(1L, sections[0].second[0].latest.id)
        assertEquals("0:42", formatTimer(42))
        assertEquals("1:02:03", formatTimer(3723))
    }

    @Test fun `call durations drop zero parts and seconds once past an hour`() {
        assertEquals(emptyList<Pair<Long, DurationUnit>>(), callDuration(0))
        assertEquals(listOf(42L to DurationUnit.Seconds), callDuration(42))
        assertEquals(listOf(3L to DurationUnit.Minutes), callDuration(180))
        assertEquals(listOf(3L to DurationUnit.Minutes, 20L to DurationUnit.Seconds), callDuration(200))
        assertEquals(listOf(1L to DurationUnit.Hours, 2L to DurationUnit.Minutes), callDuration(3723))
        assertEquals(listOf(2L to DurationUnit.Hours), callDuration(7205))
    }
}
