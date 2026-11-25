package io.github.michaelmassoni.tessera.clock.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepCyclesTest {
    private val melbourne = ZoneId.of("Australia/Melbourne")

    private fun at(
        date: String,
        time: String,
        zone: ZoneId = melbourne,
    ) = ZonedDateTime.of(java.time.LocalDate.parse(date), LocalTime.parse(time), zone)

    @Test fun `going to bed now gives wake times after 3 to 6 cycles`() {
        val plans = SleepCycles.wakeTimes(at("2026-11-25", "22:47:31"), cycleMinutes = 90, fallAsleepMinutes = 15)
        assertEquals(listOf(3, 4, 5, 6), plans.map { it.cycles })
        // In bed 22:47, asleep 23:02, then 4.5 h ... 9 h.
        assertEquals(listOf("03:32", "05:02", "06:32", "08:02"), plans.map { it.wake.toLocalTime().toString() })
        assertEquals(listOf(270, 360, 450, 540), plans.map { it.sleepMinutes })
        assertEquals(listOf(false, false, true, true), plans.map { it.recommended })
    }

    @Test fun `waking at a time gives bedtimes, most sleep first`() {
        val now = at("2026-11-25", "20:00")
        val wake = SleepCycles.next(LocalTime.of(6, 30), now)
        assertEquals(at("2026-11-26", "06:30"), wake)
        val plans = SleepCycles.bedtimes(wake, now, cycleMinutes = 90, fallAsleepMinutes = 15)
        assertEquals(listOf(6, 5, 4, 3), plans.map { it.cycles })
        assertEquals(listOf("21:15", "22:45", "00:15", "01:45"), plans.map { it.bedtime.toLocalTime().toString() })
    }

    @Test fun `bedtimes already past are left out`() {
        val now = at("2026-11-26", "00:30")
        val plans = SleepCycles.bedtimes(SleepCycles.next(LocalTime.of(6, 30), now), now, 90, 15)
        assertEquals(listOf(3), plans.map { it.cycles })
        val late = at("2026-11-26", "02:00")
        assertTrue(SleepCycles.bedtimes(SleepCycles.next(LocalTime.of(6, 30), late), late, 90, 15).isEmpty())
    }

    @Test fun `cycle length and falling asleep come from settings`() {
        val plans = SleepCycles.wakeTimes(at("2026-11-25", "23:00"), cycleMinutes = 100, fallAsleepMinutes = 0)
        assertEquals(
            "04:00",
            plans
                .first()
                .wake
                .toLocalTime()
                .toString(),
        )
        assertEquals(600, plans.last().sleepMinutes)
    }

    @Test fun `a night across daylight saving still gets the real hours`() {
        // Melbourne springs forward at 02:00 on 2026-10-04: 02:00-03:00 doesn't exist.
        val plans = SleepCycles.wakeTimes(at("2026-10-03", "23:00"), 90, 15)
        val five = plans.first { it.cycles == 5 }
        assertEquals("07:45", five.wake.toLocalTime().toString()) // 7 h 45 m later in real time, +1 h on the clock
        assertEquals(
            java.time.Duration.ofMinutes(465),
            java.time.Duration.between(five.bedtime, five.wake),
        )
    }

    @Test fun `the next occurrence is today if still to come, else tomorrow`() {
        val now = at("2026-11-25", "07:00")
        assertEquals(at("2026-11-25", "07:01"), SleepCycles.next(LocalTime.of(7, 1), now))
        assertEquals(at("2026-11-26", "07:00"), SleepCycles.next(LocalTime.of(7, 0), now))
    }

    @Test fun `a wake time more than a day away can't be a one-off alarm`() {
        val now = at("2026-11-25", "08:00")
        assertTrue(SleepCycles.canBeAlarm(at("2026-11-26", "06:30"), now))
        // 08:15 tomorrow is more than a day away: an 08:15 alarm would ring today, in 15 minutes.
        assertFalse(SleepCycles.canBeAlarm(at("2026-11-26", "08:15"), now))
    }
}
