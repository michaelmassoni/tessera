package io.github.michaelmassoni.tessera.clock.alarm

import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/**
 * An alarm. Times are wall-clock (hour/minute in whatever zone the device is in), so they
 * follow the user across time zones like every phone alarm clock.
 *
 * @property ringtone null = system default alarm sound, "" = silent, otherwise a content URI.
 * @property snoozedUntil epoch millis of a pending snooze, or null.
 * @property skipped epoch millis of one occurrence of a repeating alarm that won't ring, or null.
 */
@Serializable
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val label: String = "",
    val ringtone: String? = null,
    val vibrate: Boolean = true,
    val snoozedUntil: Long? = null,
    val skipped: Long? = null,
) {
    init {
        require(hour in 0..23 && minute in 0..59) { "invalid time $hour:$minute" }
    }

    val repeats: Boolean get() = days.isNotEmpty()
}
