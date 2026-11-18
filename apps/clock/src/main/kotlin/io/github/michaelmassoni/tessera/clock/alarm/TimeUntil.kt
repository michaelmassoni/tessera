package io.github.michaelmassoni.tessera.clock.alarm

import java.time.Duration
import java.time.ZonedDateTime

/** "Alarm set for X hours and Y minutes from now", rounded up to whole minutes like the Pixel. */
data class TimeUntil(
    val days: Long,
    val hours: Long,
    val minutes: Long,
) {
    companion object {
        fun between(
            now: ZonedDateTime,
            trigger: ZonedDateTime,
        ): TimeUntil {
            val totalMinutes = (Duration.between(now, trigger).toMillis() + 59_999) / 60_000
            return TimeUntil(
                days = totalMinutes / (24 * 60),
                hours = (totalMinutes / 60) % 24,
                minutes = totalMinutes % 60,
            )
        }
    }
}
