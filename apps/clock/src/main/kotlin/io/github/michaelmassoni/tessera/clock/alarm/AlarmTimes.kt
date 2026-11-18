package io.github.michaelmassoni.tessera.clock.alarm

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When alarms ring. Pure functions of an alarm and "now" (see docs/clock.md, "Time rules").
 */
object AlarmTimes {
    /**
     * The next instant strictly after [now] that [alarm] should ring, or null if
     * it is disabled. A pending snooze takes precedence while it is still in the future.
     */
    fun nextTrigger(
        alarm: Alarm,
        now: ZonedDateTime,
    ): ZonedDateTime? {
        if (!alarm.enabled) return null
        val snooze = alarm.snoozedUntil
        if (snooze != null && snooze > now.toInstant().toEpochMilli()) {
            return Instant.ofEpochMilli(snooze).atZone(now.zone)
        }
        return nextOccurrence(alarm, now)
    }

    /** The next wall-clock occurrence of the alarm's time after [after], ignoring snooze. */
    fun nextOccurrence(
        alarm: Alarm,
        after: ZonedDateTime,
    ): ZonedDateTime {
        val time = LocalTime.of(alarm.hour, alarm.minute)
        var date = after.toLocalDate()
        // At most 8 days ahead covers every repeat pattern (today could already be past).
        repeat(8) {
            if (!alarm.repeats || date.dayOfWeek in alarm.days) {
                val candidate = atZone(date, time, after.zone)
                if (candidate.isAfter(after)) return candidate
            }
            date = date.plusDays(1)
        }
        error("no occurrence found for $alarm after $after")
    }

    /**
     * Resolves a wall-clock time on a date. In a DST gap the time is moved forward by the length
     * of the gap; in an overlap the earlier offset wins (ZonedDateTime.of's documented behaviour).
     */
    private fun atZone(
        date: LocalDate,
        time: LocalTime,
        zone: ZoneId,
    ): ZonedDateTime = ZonedDateTime.of(date, time, zone).withEarlierOffsetAtOverlap()

    /**
     * What an alarm becomes after it has rung (dismissed or auto-silenced). One-off alarms are disabled;
     * repeating ones stay enabled. Any snooze is cleared either way.
     */
    fun afterRinging(alarm: Alarm): Alarm =
        alarm.copy(
            enabled = alarm.repeats,
            snoozedUntil = null,
        )
}
