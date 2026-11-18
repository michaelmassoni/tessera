package io.github.michaelmassoni.tessera.calendar.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.provider.CalendarContract.Reminders
import io.github.michaelmassoni.tessera.calendar.engine.CalendarInfo
import io.github.michaelmassoni.tessera.calendar.engine.EventDraft
import io.github.michaelmassoni.tessera.calendar.engine.Instance

/** Reads and writes the system calendar database. Blocking: call off the main thread. */
class CalendarRepository(
    private val resolver: ContentResolver,
) {
    fun calendars(): List<CalendarInfo> {
        val list = mutableListOf<CalendarInfo>()
        resolver
            .query(
                Calendars.CONTENT_URI,
                arrayOf(
                    Calendars._ID,
                    Calendars.CALENDAR_DISPLAY_NAME,
                    Calendars.ACCOUNT_NAME,
                    Calendars.ACCOUNT_TYPE,
                    Calendars.CALENDAR_COLOR,
                    Calendars.VISIBLE,
                    Calendars.CALENDAR_ACCESS_LEVEL,
                ),
                null,
                null,
                "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}",
            )?.use { c ->
                while (c.moveToNext()) {
                    list +=
                        CalendarInfo(
                            id = c.getLong(0),
                            name = c.getString(1).orEmpty(),
                            accountName = c.getString(2).orEmpty(),
                            accountType = c.getString(3).orEmpty(),
                            color = c.getInt(4),
                            visible = c.getInt(5) != 0,
                            writable = c.getInt(6) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                        )
                }
            }
        return list
    }

    /**
     * Creates the local device calendar when no writable calendar exists. Inserting a calendar is
     * a sync-adapter operation, allowed for ACCOUNT_TYPE_LOCAL.
     */
    fun ensureWritableCalendar(
        name: String,
        color: Int,
    ) {
        if (calendars().any { it.writable }) return
        val uri =
            Calendars.CONTENT_URI
                .buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()
        resolver.insert(
            uri,
            ContentValues().apply {
                put(Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
                put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(Calendars.NAME, name)
                put(Calendars.CALENDAR_DISPLAY_NAME, name)
                put(Calendars.CALENDAR_COLOR, color)
                put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
                put(Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
                put(Calendars.VISIBLE, 1)
                put(Calendars.SYNC_EVENTS, 1)
                put(
                    Calendars.CALENDAR_TIME_ZONE,
                    java.util.TimeZone
                        .getDefault()
                        .id,
                )
            },
        )
    }

    fun setVisible(
        calendarId: Long,
        visible: Boolean,
    ) {
        resolver.update(
            ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId),
            ContentValues().apply { put(Calendars.VISIBLE, if (visible) 1 else 0) },
            null,
            null,
        )
    }

    /** Instances overlapping [begin, end) in visible calendars. */
    fun instances(
        begin: Long,
        end: Long,
    ): List<Instance> {
        val uri =
            Instances.CONTENT_URI
                .buildUpon()
                .also {
                    ContentUris.appendId(it, begin)
                    ContentUris.appendId(it, end)
                }.build()
        val list = mutableListOf<Instance>()
        resolver
            .query(
                uri,
                arrayOf(
                    Instances.EVENT_ID,
                    Instances.BEGIN,
                    Instances.END,
                    Instances.TITLE,
                    Instances.ALL_DAY,
                    Instances.DISPLAY_COLOR,
                    Instances.CALENDAR_ID,
                    Instances.EVENT_LOCATION,
                    Instances.RRULE,
                ),
                "${Instances.VISIBLE} = 1",
                null,
                "${Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    list +=
                        Instance(
                            eventId = c.getLong(0),
                            begin = c.getLong(1),
                            end = c.getLong(2),
                            title = c.getString(3).orEmpty(),
                            allDay = c.getInt(4) != 0,
                            color = c.getInt(5),
                            calendarId = c.getLong(6),
                            location = c.getString(7).orEmpty(),
                            rrule = c.getString(8),
                        )
                }
            }
        return list
    }

    fun event(id: Long): EventDraft? {
        val draft =
            resolver
                .query(
                    ContentUris.withAppendedId(Events.CONTENT_URI, id),
                    arrayOf(
                        Events.CALENDAR_ID,
                        Events.TITLE,
                        Events.ALL_DAY,
                        Events.DTSTART,
                        Events.DTEND,
                        Events.DURATION,
                        Events.EVENT_TIMEZONE,
                        Events.EVENT_LOCATION,
                        Events.DESCRIPTION,
                        Events.RRULE,
                    ),
                    null,
                    null,
                    null,
                )?.use { c ->
                    if (!c.moveToFirst()) return@use null
                    val start = c.getLong(3)
                    val end = if (c.isNull(4)) start + durationMillis(c.getString(5)) else c.getLong(4)
                    EventDraft(
                        id = id,
                        calendarId = c.getLong(0),
                        title = c.getString(1).orEmpty(),
                        allDay = c.getInt(2) != 0,
                        start = start,
                        end = end,
                        timeZone = c.getString(6) ?: "UTC",
                        location = c.getString(7).orEmpty(),
                        description = c.getString(8).orEmpty(),
                        rrule = c.getString(9),
                    )
                } ?: return null
        val reminders = mutableListOf<Int>()
        resolver
            .query(Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES), "${Reminders.EVENT_ID} = ?", arrayOf(id.toString()), null)
            ?.use { c -> while (c.moveToNext()) reminders += c.getInt(0) }
        return draft.copy(reminders = reminders.distinct().sorted())
    }

    /** Inserts or updates [draft] and replaces its reminders. @return the event id. */
    fun save(draft: EventDraft): Long {
        val values =
            ContentValues().apply {
                put(Events.CALENDAR_ID, draft.calendarId)
                put(Events.TITLE, draft.title)
                put(Events.ALL_DAY, if (draft.allDay) 1 else 0)
                put(Events.DTSTART, draft.start)
                put(Events.EVENT_TIMEZONE, if (draft.allDay) "UTC" else draft.timeZone)
                put(Events.EVENT_LOCATION, draft.location)
                put(Events.DESCRIPTION, draft.description)
                put(Events.RRULE, draft.rrule)
                put(Events.HAS_ALARM, if (draft.reminders.isEmpty()) 0 else 1)
                // Repeating events store a duration instead of an end time.
                if (draft.rrule != null) {
                    putNull(Events.DTEND)
                    put(Events.DURATION, rfcDuration(draft.end - draft.start, draft.allDay))
                } else {
                    put(Events.DTEND, draft.end)
                    putNull(Events.DURATION)
                }
            }
        val id =
            if (draft.id == null) {
                ContentUris.parseId(resolver.insert(Events.CONTENT_URI, values) ?: error("Insert failed"))
            } else {
                resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, draft.id), values, null, null)
                resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID} = ?", arrayOf(draft.id.toString()))
                draft.id
            }
        draft.reminders.distinct().forEach { minutes ->
            resolver.insert(
                Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(Reminders.EVENT_ID, id)
                    put(Reminders.MINUTES, minutes)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                },
            )
        }
        return id
    }

    fun delete(id: Long) {
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null)
    }

    fun eventUri(id: Long): Uri = ContentUris.withAppendedId(Events.CONTENT_URI, id)

    companion object {
        const val LOCAL_ACCOUNT = "Tessera"

        /** "P3600S", or "P2D" for all-day events, as CalendarContract expects. */
        fun rfcDuration(
            millis: Long,
            allDay: Boolean,
        ): String {
            val seconds = (millis / 1000).coerceAtLeast(0)
            return if (allDay) "P${(seconds / 86_400).coerceAtLeast(1)}D" else "P${seconds}S"
        }

        /** Parses "P3600S", "PT1H", "P1D", "P1W" (the forms the provider uses). */
        fun durationMillis(value: String?): Long {
            if (value.isNullOrBlank()) return 0
            Regex("^P(\\d+)W$").find(value)?.let { return it.groupValues[1].toLong() * 7 * 86_400_000 }
            Regex("^P(\\d+)D$").find(value)?.let { return it.groupValues[1].toLong() * 86_400_000 }
            Regex("^P(\\d+)S$").find(value)?.let { return it.groupValues[1].toLong() * 1000 }
            return runCatching {
                java.time.Duration
                    .parse(value)
                    .toMillis()
            }.getOrDefault(0)
        }
    }
}
