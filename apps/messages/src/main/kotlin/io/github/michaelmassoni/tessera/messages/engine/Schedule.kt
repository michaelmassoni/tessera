package io.github.michaelmassoni.tessera.messages.engine

import java.time.LocalDateTime
import java.time.LocalTime

/** A message waiting to be sent at [at] (epoch milliseconds). [attachments] are app-private copies. */
data class ScheduledMessage(
    val id: Long,
    val addresses: List<String>,
    val text: String,
    val at: Long,
    val attachments: List<String> = emptyList(),
) {
    /** Whether this belongs to the conversation with [others], ignoring order and formatting. */
    fun isFor(others: List<String>): Boolean = addresses.map(Recipients::dialable).toSet() == others.map(Recipients::dialable).toSet()
}

/** The quick choices offered when you long-press Send. */
enum class ScheduleSlot { LaterToday, Tonight, TomorrowMorning }

object ScheduleTimes {
    private val laterToday = LocalTime.of(18, 0)
    private val tonight = LocalTime.of(21, 0)
    private val morning = LocalTime.of(8, 0)

    /** At least this long before a slot for it to be offered, so "later today" isn't in 2 minutes. */
    private const val LEAD_MINUTES = 30L

    /** Later today at 6 PM and tonight at 9 PM while they're still ahead, and tomorrow at 8 AM. */
    fun suggestions(now: LocalDateTime): List<Pair<ScheduleSlot, LocalDateTime>> {
        val soon = now.plusMinutes(LEAD_MINUTES)
        val today = now.toLocalDate()
        return listOfNotNull(
            (ScheduleSlot.LaterToday to today.atTime(laterToday)).takeIf { it.second.isAfter(soon) },
            (ScheduleSlot.Tonight to today.atTime(tonight)).takeIf { it.second.isAfter(soon) },
            ScheduleSlot.TomorrowMorning to today.plusDays(1).atTime(morning),
        )
    }

    /** A picked time must be in the future; a minute ahead at least, so it isn't sent "now" by accident. */
    fun isValid(
        at: LocalDateTime,
        now: LocalDateTime,
    ): Boolean = at.isAfter(now.plusMinutes(1))
}
