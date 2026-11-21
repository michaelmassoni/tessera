package io.github.michaelmassoni.tessera.clock.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class AlarmTimesTest {
    private val ny = ZoneId.of("America/New_York")

    // 2026-11-18 is a Wednesday.
    private fun at(
        text: String,
        zone: ZoneId = ny,
    ) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone)

    private fun alarm(
        hour: Int,
        minute: Int,
        vararg days: java.time.DayOfWeek,
    ) = Alarm(id = 1, hour = hour, minute = minute, days = days.toSet())

    @Test fun `one-off alarm later today rings today`() {
        assertEquals(at("2026-11-18T07:30"), AlarmTimes.nextTrigger(alarm(7, 30), at("2026-11-18T06:00")))
    }

    @Test fun `one-off alarm earlier today rings tomorrow`() {
        assertEquals(at("2026-11-19T07:30"), AlarmTimes.nextTrigger(alarm(7, 30), at("2026-11-18T08:00")))
    }

    @Test fun `alarm exactly now rings tomorrow, never twice`() {
        assertEquals(at("2026-11-19T07:30"), AlarmTimes.nextTrigger(alarm(7, 30), at("2026-11-18T07:30")))
    }

    @Test fun `disabled alarm has no trigger`() {
        assertNull(AlarmTimes.nextTrigger(alarm(7, 30).copy(enabled = false), at("2026-11-18T06:00")))
    }

    @Test fun `repeating alarm picks the next selected day`() {
        val weekdays = alarm(7, 0, MONDAY, WEDNESDAY, FRIDAY)
        assertEquals(at("2026-11-18T07:00"), AlarmTimes.nextTrigger(weekdays, at("2026-11-18T06:59"))) // Wed
        assertEquals(at("2026-11-20T07:00"), AlarmTimes.nextTrigger(weekdays, at("2026-11-18T07:00"))) // -> Fri
        assertEquals(at("2026-11-23T07:00"), AlarmTimes.nextTrigger(weekdays, at("2026-11-20T09:00"))) // Fri -> Mon
    }

    @Test fun `weekly alarm on today's weekday that already passed is a week away`() {
        assertEquals(at("2026-11-25T07:00"), AlarmTimes.nextTrigger(alarm(7, 0, WEDNESDAY), at("2026-11-18T08:00")))
    }

    @Test fun `weekend alarm crosses the week`() {
        assertEquals(
            at("2026-11-21T09:00"),
            AlarmTimes.nextTrigger(alarm(9, 0, SATURDAY, SUNDAY), at("2026-11-18T10:00")),
        )
    }

    @Test fun `midnight alarm`() {
        assertEquals(at("2026-11-19T00:00"), AlarmTimes.nextTrigger(alarm(0, 0), at("2026-11-18T23:59")))
    }

    @Test fun `spring forward gap moves the alarm forward by the gap`() {
        // 2026-03-08: 02:00 -> 03:00 in New York. A 02:30 alarm rings at 03:30 EDT.
        val trigger = AlarmTimes.nextTrigger(alarm(2, 30), at("2026-03-08T01:00"))!!
        assertEquals(ZonedDateTime.of(2026, 3, 8, 3, 30, 0, 0, ny), trigger)
        assertEquals(ZoneOffset.ofHours(-4), trigger.offset)
    }

    @Test fun `fall back overlap rings once at the earlier offset`() {
        // 2026-11-01: 02:00 EDT -> 01:00 EST. 01:30 happens twice; ring at the first (EDT).
        val first = AlarmTimes.nextTrigger(alarm(1, 30), at("2026-11-01T00:00"))!!
        assertEquals(ZoneOffset.ofHours(-4), first.offset)
        // After it rang, the next occurrence is the following day, not the repeated 01:30 EST.
        val next = AlarmTimes.nextOccurrence(alarm(1, 30), first)
        assertEquals(at("2026-11-02T01:30"), next)
    }

    @Test fun `alarm keeps wall-clock time when the zone changes`() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val now = at("2026-11-18T06:00").withZoneSameInstant(tokyo) // 20:00 in Tokyo
        assertEquals(at("2026-11-19T07:30", tokyo), AlarmTimes.nextTrigger(alarm(7, 30), now))
    }

    @Test fun `pending snooze takes precedence`() {
        val now = at("2026-11-18T07:00")
        val snoozed = alarm(7, 0).copy(snoozedUntil = now.plusMinutes(10).toInstant().toEpochMilli())
        assertEquals(now.plusMinutes(10), AlarmTimes.nextTrigger(snoozed, now))
    }

    @Test fun `expired snooze is ignored`() {
        val now = at("2026-11-18T08:00")
        val stale = alarm(7, 0).copy(snoozedUntil = now.minusMinutes(1).toInstant().toEpochMilli())
        assertEquals(at("2026-11-19T07:00"), AlarmTimes.nextTrigger(stale, now))
    }

    @Test fun `after ringing a one-off alarm is disabled and a repeating one is not`() {
        val oneOff = AlarmTimes.afterRinging(alarm(7, 0).copy(snoozedUntil = 5))
        assertFalse(oneOff.enabled)
        assertNull(oneOff.snoozedUntil)
        val repeating = AlarmTimes.afterRinging(alarm(7, 0, MONDAY))
        assertTrue(repeating.enabled)
    }

    @Test fun `every repeat pattern finds a trigger within a week`() {
        val now = at("2026-11-18T12:00")
        for (mask in 1 until 128) {
            val days =
                java.time.DayOfWeek.entries
                    .filterIndexed { i, _ -> mask and (1 shl i) != 0 }
                    .toSet()
            val trigger = AlarmTimes.nextTrigger(Alarm(1, 6, 0, days), now)!!
            assertTrue(trigger.isAfter(now))
            assertTrue(trigger.dayOfWeek in days)
            assertTrue(trigger.isBefore(now.plusDays(8)))
        }
    }

    @Test fun `time until rounds up to whole minutes`() {
        val now = at("2026-11-18T07:00:30".let { it })
        assertEquals(TimeUntil(0, 7, 12), TimeUntil.between(at("2026-11-18T00:00"), at("2026-11-18T07:12")))
        assertEquals(TimeUntil(0, 0, 1), TimeUntil.between(now, at("2026-11-18T07:01")))
        assertEquals(TimeUntil(1, 0, 0), TimeUntil.between(at("2026-11-18T07:00"), at("2026-11-19T07:00")))
    }

    @Test fun `a skipped occurrence moves to the one after, and a past skip does nothing`() {
        val weekdays = alarm(7, 0, MONDAY, WEDNESDAY, FRIDAY)
        val wed = at("2026-11-18T07:00").toInstant().toEpochMilli()
        val skipped = weekdays.copy(skipped = wed)
        assertEquals(at("2026-11-20T07:00"), AlarmTimes.nextTrigger(skipped, at("2026-11-18T06:00")))
        assertEquals(at("2026-11-20T07:00"), AlarmTimes.nextTrigger(skipped, at("2026-11-18T08:00")))
        assertNull(AlarmTimes.afterRinging(skipped).skipped)
    }
}
