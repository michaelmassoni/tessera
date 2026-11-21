package io.github.michaelmassoni.tessera.calendar.engine

/** A calendar from CalendarContract.Calendars. [color] is the account's ARGB colour. */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val visible: Boolean,
    val writable: Boolean,
)

/**
 * One occurrence of an event (CalendarContract.Instances). [begin] and [end] are epoch ms. For
 * all-day events they are UTC midnights, as the provider stores them.
 */
data class Instance(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val title: String,
    val allDay: Boolean,
    val color: Int,
    val calendarId: Long,
    val location: String = "",
    val rrule: String? = null,
)

/** The fields the editor edits. Times are epoch ms; [allDay] events use UTC midnights. */
data class EventDraft(
    val id: Long? = null,
    val calendarId: Long = 0,
    val title: String = "",
    val allDay: Boolean = false,
    val start: Long = 0,
    val end: Long = 0,
    val timeZone: String = "UTC",
    val location: String = "",
    val description: String = "",
    val rrule: String? = null,
    /** Occurrences left out of a repeating event (EXDATE: comma-separated UTC stamps). */
    val exdate: String? = null,
    /** Minutes before the start. */
    val reminders: List<Int> = emptyList(),
)
