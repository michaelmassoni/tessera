package io.github.michaelmassoni.tessera.tasks.engine

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** A list of tasks: on this phone, or a CalDAV calendar collection ([href]). */
@Serializable
data class TaskList(
    val id: String,
    val name: String,
    /** ARGB from the server, or null for the theme's colour. */
    val color: Int? = null,
    /** The collection's URL, for a synced list. */
    val href: String? = null,
    /** The collection's sync token (RFC 6578) after the last sync. */
    val syncToken: String? = null,
    val order: Int = 0,
) {
    val synced: Boolean get() = href != null
}

/**
 * When a task is due: a date, and a time of day if it has one. Times are local ("floating", as
 * iCalendar calls them): 8:00 is 8:00 wherever the phone is, through daylight saving.
 */
@Serializable
data class Due(
    /** ISO date, yyyy-MM-dd. */
    val date: String,
    /** HH:mm, or null for all day. */
    val time: String? = null,
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
    val localTime: LocalTime? get() = time?.let(LocalTime::parse)
    val dateTime: LocalDateTime get() = localDate.atTime(localTime ?: LocalTime.MIDNIGHT)

    companion object {
        fun of(
            date: LocalDate,
            time: LocalTime? = null,
        ) = Due(date.toString(), time?.let { "%02d:%02d".format(it.hour, it.minute) })
    }
}

/** How a task repeats. */
@Serializable
data class Repeat(
    val frequency: Frequency,
    val interval: Int = 1,
    /** Weekdays (1 = Monday … 7 = Sunday) for weekly repeats; empty means the due date's. */
    val weekdays: Set<Int> = emptySet(),
    /** Count from when it was completed rather than from the due date ("every 8 hours after done"). */
    val fromCompletion: Boolean = false,
    /** Stop after this date (inclusive), if set. */
    val until: String? = null,
) {
    enum class Frequency { Hourly, Daily, Weekly, Monthly, Yearly }
}

@Serializable
data class Task(
    /** The iCalendar UID. */
    val id: String,
    val listId: String,
    val title: String,
    val notes: String = "",
    val due: Due? = null,
    val repeat: Repeat? = null,
    val starred: Boolean = false,
    /** The parent task's id, for a subtask. */
    val parentId: String? = null,
    /** When it was completed (ms), or null while open. */
    val completedAt: Long? = null,
    /** Position in "my order": lower first. */
    val order: Long = 0,
    val createdAt: Long = 0,
    val modifiedAt: Long = 0,
    /** Snoozed reminder time (ms); this phone only. */
    val snoozedUntil: Long? = null,
    /** Sync state: the resource's URL on the server, once uploaded. */
    val href: String? = null,
    val etag: String? = null,
    /** Changed here since the last sync. */
    val dirty: Boolean = true,
    /** Deleted here, waiting to be deleted on the server. */
    val deleted: Boolean = false,
    /** The server's copy as last synced (a VCALENDAR), kept for what Tessera doesn't edit. */
    val ical: String? = null,
) {
    val completed: Boolean get() = completedAt != null
}
