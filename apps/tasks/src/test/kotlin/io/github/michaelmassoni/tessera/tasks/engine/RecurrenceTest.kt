package io.github.michaelmassoni.tessera.tasks.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceTest {
    private val zone = ZoneId.of("Australia/Melbourne")

    private fun at(text: String) =
        LocalDateTime
            .parse(text)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun next(
        due: Due,
        repeat: Repeat,
        done: String,
    ) = Recurrence.next(due, repeat, at(done), zone)

    @Test
    fun `daily medication at 8 moves to tomorrow at 8`() {
        val r = Repeat(Repeat.Frequency.Daily)
        assertEquals(Due("2026-11-24", "08:00"), next(Due("2026-11-23", "08:00"), r, "2026-11-23T08:05"))
        // Taken a little early: still tomorrow.
        assertEquals(Due("2026-11-24", "08:00"), next(Due("2026-11-23", "08:00"), r, "2026-11-23T07:50"))
        // Forgotten for three days: the next one, not three overdue ones.
        assertEquals(Due("2026-11-27", "08:00"), next(Due("2026-11-23", "08:00"), r, "2026-11-26T20:00"))
    }

    @Test
    fun `daily keeps the local time across daylight saving`() {
        // Melbourne's clocks go back on 5 April 2026.
        assertEquals(Due("2026-04-05", "08:00"), next(Due("2026-04-04", "08:00"), Repeat(Repeat.Frequency.Daily), "2026-04-04T08:01"))
    }

    @Test
    fun `every 8 hours after done`() {
        val r = Repeat(Repeat.Frequency.Hourly, interval = 8, fromCompletion = true)
        assertEquals(Due("2026-11-23", "17:30"), next(Due("2026-11-23", "08:00"), r, "2026-11-23T09:30"))
    }

    @Test
    fun `every 3 days after done keeps the time of day`() {
        val r = Repeat(Repeat.Frequency.Daily, interval = 3, fromCompletion = true)
        assertEquals(Due("2026-11-28", "08:00"), next(Due("2026-11-20", "08:00"), r, "2026-11-25T19:00"))
    }

    @Test
    fun `all-day tasks move past today`() {
        val r = Repeat(Repeat.Frequency.Daily)
        assertEquals(Due("2026-11-24"), next(Due("2026-11-23"), r, "2026-11-23T10:00"))
        assertEquals(Due("2026-11-24"), next(Due("2026-11-01"), r, "2026-11-23T10:00"))
    }

    @Test
    fun `weekly on chosen days`() {
        // Monday and Thursday; 23 Nov 2026 is a Monday.
        val r = Repeat(Repeat.Frequency.Weekly, weekdays = setOf(1, 4))
        assertEquals(Due("2026-11-26"), next(Due("2026-11-23"), r, "2026-11-23T09:00"))
        assertEquals(Due("2026-11-30"), next(Due("2026-11-26"), r, "2026-11-26T09:00"))
        val fortnightly = Repeat(Repeat.Frequency.Weekly, interval = 2, weekdays = setOf(1))
        assertEquals(Due("2026-12-07"), next(Due("2026-11-23"), fortnightly, "2026-11-23T09:00"))
    }

    @Test
    fun `monthly and the end of a repeat`() {
        assertEquals(Due("2026-12-23"), next(Due("2026-11-23"), Repeat(Repeat.Frequency.Monthly), "2026-11-23T09:00"))
        assertNull(next(Due("2026-11-23"), Repeat(Repeat.Frequency.Daily, until = "2026-11-23"), "2026-11-23T09:00"))
    }

    @Test
    fun `rrules round trip`() {
        val r = Repeat(Repeat.Frequency.Weekly, interval = 2, weekdays = setOf(1, 4), until = "2027-01-31")
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH;UNTIL=20270131", Recurrence.format(r))
        assertEquals(r, Recurrence.parse(Recurrence.format(r)))
        assertEquals(Repeat(Repeat.Frequency.Monthly), Recurrence.parse("FREQ=MONTHLY;BYMONTHDAY=23"))
        assertNull(Recurrence.parse("FREQ=SECONDLY"))
    }
}
