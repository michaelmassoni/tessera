package io.github.michaelmassoni.tessera.calendar.sync

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import io.github.michaelmassoni.tessera.calendar.engine.AttendeeRow
import io.github.michaelmassoni.tessera.calendar.engine.EventIcs
import io.github.michaelmassoni.tessera.calendar.engine.EventResource
import io.github.michaelmassoni.tessera.calendar.engine.EventRow
import io.github.michaelmassoni.tessera.core.dav.Dav
import io.github.michaelmassoni.tessera.core.dav.DavClient
import io.github.michaelmassoni.tessera.core.dav.DavKind
import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import java.net.URI
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import android.icu.util.TimeZone as IcuTimeZone

/**
 * Syncs the account's calendars with the server: local changes go up first (new, changed and
 * deleted events, and calendar colours), then the server's changes come down. On a conflict (the
 * server's copy changed since this phone last saw it) the server's version wins.
 *
 * Rows are written as the sync adapter, so the provider doesn't mark them dirty again. Each
 * event's resource name is its _SYNC_ID, its ETag SYNC_DATA1 and the server's text SYNC_DATA2
 * (written back with only what changed, so other apps' properties survive).
 */
class CalendarSync(
    private val context: Context,
    private val account: Account,
    private val client: DavClient,
) {
    private val resolver: ContentResolver = context.contentResolver
    private val ics = EventIcs(ZoneId.systemDefault()) { name -> IcuTimeZone.getIDForWindowsID(name, null) }
    private val eventsUri = CalendarAccounts.asSyncAdapter(Events.CONTENT_URI, account)
    private val calendarsUri = CalendarAccounts.asSyncAdapter(Calendars.CONTENT_URI, account)

    private data class LocalCalendar(
        val id: Long,
        val href: String,
        val token: String?,
        val color: Int,
        val colorDirty: Boolean,
    )

    fun syncAll() {
        val remote = runCatching { CalendarDavStore(context).account?.home?.let { client.calendars(it, "VEVENT") } }.getOrNull()
        for (cal in localCalendars()) {
            remote?.firstOrNull { sameHref(it.href, cal.href) }?.let { r -> refreshCalendar(cal, r.displayName, r.color, r.writable) }
            syncCalendar(cal)
        }
    }

    private fun localCalendars(): List<LocalCalendar> {
        val out = mutableListOf<LocalCalendar>()
        resolver
            .query(
                calendarsUri,
                arrayOf(Calendars._ID, Calendars.NAME, Calendars.CAL_SYNC1, Calendars.CALENDAR_COLOR, Calendars.CAL_SYNC2),
                "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
                arrayOf(account.name, account.type),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out +=
                        LocalCalendar(c.getLong(0), c.getString(1) ?: continue, c.getString(2), c.getInt(3), c.getString(4) == COLOR_DIRTY)
                }
            }
        return out
    }

    /** The server's name, colour and access for a calendar (a local colour not yet sent wins). */
    private fun refreshCalendar(
        cal: LocalCalendar,
        name: String?,
        color: String?,
        writable: Boolean?,
    ) {
        val values = ContentValues()
        name?.let { values.put(Calendars.CALENDAR_DISPLAY_NAME, it) }
        if (!cal.colorDirty) Dav.color(color)?.let { values.put(Calendars.CALENDAR_COLOR, it) }
        writable?.let { values.put(Calendars.CALENDAR_ACCESS_LEVEL, if (it) Calendars.CAL_ACCESS_OWNER else Calendars.CAL_ACCESS_READ) }
        if (values.size() > 0) resolver.update(ContentUris.withAppendedId(calendarsUri, cal.id), values, null, null)
    }

    private fun syncCalendar(cal: LocalCalendar) {
        if (cal.colorDirty && client.setColor(cal.href, cal.color)) {
            resolver.update(
                ContentUris.withAppendedId(calendarsUri, cal.id),
                ContentValues().apply { putNull(Calendars.CAL_SYNC2) },
                null,
                null,
            )
        }
        val refetch = upload(cal)
        download(cal, refetch)
    }

    // ---- upload ----

    private data class LocalEvent(
        val id: Long,
        val name: String?,
        val originalId: Long?,
        val deleted: Boolean,
        val dirty: Boolean,
        val etag: String?,
        val raw: String?,
        val uid: String?,
    )

    private val eventColumns =
        arrayOf(
            Events._ID,
            Events._SYNC_ID,
            Events.ORIGINAL_ID,
            Events.DELETED,
            Events.DIRTY,
            Events.SYNC_DATA1,
            Events.SYNC_DATA2,
            Events.UID_2445,
        )

    private fun Cursor.localEvent() =
        LocalEvent(
            id = getLong(0),
            name = getString(1),
            originalId = if (isNull(2)) null else getLong(2),
            deleted = getInt(3) != 0,
            dirty = getInt(4) != 0,
            etag = getString(5),
            raw = getString(6),
            uid = getString(7),
        )

    private fun events(
        selection: String,
        args: Array<String>,
    ): List<LocalEvent> {
        val out = mutableListOf<LocalEvent>()
        resolver.query(eventsUri, eventColumns, selection, args, null)?.use { c -> while (c.moveToNext()) out += c.localEvent() }
        return out
    }

    /** Sends local changes; returns the resource names the server changed meanwhile (to fetch). */
    private fun upload(cal: LocalCalendar): Set<String> {
        val refetch = mutableSetOf<String>()
        val dirty = events("${Events.CALENDAR_ID} = ? AND ${Events.DIRTY} = 1", arrayOf(cal.id.toString()))
        // A changed occurrence is sent as part of its series' resource.
        val mains = (dirty.filter { it.originalId == null }.map { it.id } + dirty.mapNotNull { it.originalId }).distinct()
        for (mainId in mains) {
            val main = events("${Events._ID} = ?", arrayOf(mainId.toString())).firstOrNull() ?: continue
            if (main.deleted) {
                main.name?.let { name -> client.delete(href(cal, name), null) }
                deleteEvent(main)
                continue
            }
            val uid = main.uid ?: UUID.randomUUID().toString()
            val name = main.name ?: (URLEncoder.encode(uid, "UTF-8") + ".ics")
            val exceptions = events("${Events.ORIGINAL_ID} = ?", arrayOf(mainId.toString()))
            val resource =
                EventResource(
                    uid,
                    readRow(mainId),
                    exceptions.map { x -> if (x.deleted) readRow(x.id).copy(status = EventRow.CANCELED) else readRow(x.id) },
                )
            val text = ics.write(resource, main.raw, Instant.now())
            val reply = client.put(href(cal, name), text, main.name?.let { main.etag })
            when {
                reply.code in 200..299 -> {
                    resolver.update(
                        ContentUris.withAppendedId(eventsUri, mainId),
                        ContentValues().apply {
                            put(Events._SYNC_ID, name)
                            put(Events.UID_2445, uid)
                            put(Events.SYNC_DATA1, reply.etag)
                            put(Events.SYNC_DATA2, text)
                            put(Events.DIRTY, 0)
                        },
                        null,
                        null,
                    )
                    exceptions.forEach { x ->
                        if (x.deleted) {
                            resolver.delete(ContentUris.withAppendedId(eventsUri, x.id), null, null)
                        } else {
                            resolver.update(
                                ContentUris.withAppendedId(eventsUri, x.id),
                                ContentValues().apply {
                                    put(Events.ORIGINAL_SYNC_ID, name)
                                    put(Events.DIRTY, 0)
                                },
                                null,
                                null,
                            )
                        }
                    }
                    // Without an ETag in the reply, fetch it to know the server's copy.
                    if (reply.etag == null) refetch += name
                }

                reply.code == 412 || reply.code == 409 -> {
                    refetch += name
                }

                reply.code == 403 -> {
                    refetch += name
                }

                else -> {
                    throw SyncException(SyncProblem.Other, "PUT ${reply.code}")
                }
            }
        }
        return refetch
    }

    /** One event's columns, reminders and attendees as an [EventRow]. */
    private fun readRow(id: Long): EventRow {
        var row = EventRow()
        resolver
            .query(
                ContentUris.withAppendedId(eventsUri, id),
                arrayOf(
                    Events.TITLE,
                    Events.EVENT_LOCATION,
                    Events.DESCRIPTION,
                    Events.DTSTART,
                    Events.DTEND,
                    Events.DURATION,
                    Events.ALL_DAY,
                    Events.EVENT_TIMEZONE,
                    Events.RRULE,
                    Events.RDATE,
                    Events.EXDATE,
                    Events.STATUS,
                    Events.AVAILABILITY,
                    Events.ACCESS_LEVEL,
                    Events.ORGANIZER,
                    Events.ORIGINAL_INSTANCE_TIME,
                    Events.ORIGINAL_ALL_DAY,
                ),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    row =
                        EventRow(
                            title = c.getString(0).orEmpty(),
                            location = c.getString(1).orEmpty(),
                            description = c.getString(2).orEmpty(),
                            start = c.getLong(3),
                            end = if (c.isNull(4)) null else c.getLong(4),
                            duration = c.getString(5),
                            allDay = c.getInt(6) != 0,
                            timeZone = c.getString(7) ?: "UTC",
                            rrule = c.getString(8)?.ifBlank { null },
                            rdate = c.getString(9)?.ifBlank { null },
                            exdate = c.getString(10)?.ifBlank { null },
                            status = if (c.isNull(11)) null else c.getInt(11),
                            availability = c.getInt(12),
                            accessLevel = c.getInt(13),
                            organizer = c.getString(14)?.takeIf { it.contains('@') && it != account.name },
                            originalInstanceTime = if (c.isNull(15)) null else c.getLong(15),
                            originalAllDay = c.getInt(16) != 0,
                        )
                }
            }
        val reminders = mutableListOf<Int>()
        resolver
            .query(
                CalendarAccounts.asSyncAdapter(Reminders.CONTENT_URI, account),
                arrayOf(Reminders.MINUTES),
                "${Reminders.EVENT_ID} = ?",
                arrayOf(id.toString()),
                null,
            )?.use { c -> while (c.moveToNext()) reminders += c.getInt(0) }
        return row.copy(reminders = reminders.filter { it >= 0 }.distinct().sorted(), attendees = attendees(id))
    }

    private fun attendees(id: Long): List<AttendeeRow> {
        val out = mutableListOf<AttendeeRow>()
        resolver
            .query(
                CalendarAccounts.asSyncAdapter(Attendees.CONTENT_URI, account),
                arrayOf(
                    Attendees.ATTENDEE_EMAIL,
                    Attendees.ATTENDEE_NAME,
                    Attendees.ATTENDEE_RELATIONSHIP,
                    Attendees.ATTENDEE_TYPE,
                    Attendees.ATTENDEE_STATUS,
                ),
                "${Attendees.EVENT_ID} = ?",
                arrayOf(id.toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(2) == Attendees.RELATIONSHIP_ORGANIZER) continue
                    out += AttendeeRow(c.getString(0).orEmpty(), c.getString(1).orEmpty(), false, c.getInt(3), c.getInt(4))
                }
            }
        return out
    }

    // ---- download ----

    private fun download(
        cal: LocalCalendar,
        refetch: Set<String>,
    ) {
        val local = HashMap<String, LocalEvent>()
        events("${Events.CALENDAR_ID} = ? AND ${Events.ORIGINAL_ID} IS NULL", arrayOf(cal.id.toString()))
            .forEach { e -> e.name?.let { local[it] = e } }
        val changed = mutableSetOf<String>()
        val gone = mutableSetOf<String>()
        var token: String? = cal.token
        val changes = runCatching { client.changes(cal.href, cal.token) }.getOrNull()
        if (changes != null) {
            for (r in changes.responses) {
                val name = nameOf(r.href)
                if (r.status == 404) {
                    gone += name
                } else if (local[name]?.etag != r.etag || r.etag == null) {
                    changed += name
                }
            }
            token = changes.syncToken
        } else {
            val onServer = client.etags(cal.href, DavKind.CalDav, "VEVENT").associate { nameOf(it.href) to it.etag }
            onServer.forEach { (name, etag) -> if (local[name]?.etag != etag || etag == null) changed += name }
            gone += local.keys - onServer.keys
            token = null
        }
        changed += refetch
        changed -= gone

        val cutoff = CalendarAccounts.pastDays(context).takeIf { it > 0 }?.let { System.currentTimeMillis() - it * 86_400_000L }
        if (changed.isNotEmpty()) {
            for (r in client.fetch(cal.href, changed.map { href(cal, it) })) {
                val name = nameOf(r.href)
                val text = r.data ?: continue
                val resource = ics.read(text) ?: continue
                val existing = local[name]
                if (cutoff != null && endsBefore(resource.main, cutoff)) {
                    existing?.let(::deleteEvent)
                    continue
                }
                store(cal, name, r.etag, text, resource, existing)
            }
        }
        gone.forEach { name -> local[name]?.let(::deleteEvent) }
        if (token != cal.token) {
            resolver.update(
                ContentUris.withAppendedId(calendarsUri, cal.id),
                ContentValues().apply { put(Calendars.CAL_SYNC1, token) },
                null,
                null,
            )
        }
    }

    /** Ends before [cutoff]: a single event, or a repeating one whose UNTIL is past. */
    private fun endsBefore(
        row: EventRow,
        cutoff: Long,
    ): Boolean {
        if (row.rrule == null && row.rdate == null) return (row.end ?: row.start) < cutoff
        val until =
            row.rrule
                ?.split(';')
                ?.firstOrNull { it.startsWith("UNTIL=") }
                ?.removePrefix("UNTIL=") ?: return false
        val day = until.take(8)
        return runCatching { java.time.LocalDate.parse(day, java.time.format.DateTimeFormatter.BASIC_ISO_DATE) }
            .getOrNull()
            ?.atStartOfDay(java.time.ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
            ?.let { it < cutoff } ?: false
    }

    /** Writes the server's version of a resource: the event updated in place, its occurrences anew. */
    private fun store(
        cal: LocalCalendar,
        name: String,
        etag: String?,
        text: String,
        resource: EventResource,
        existing: LocalEvent?,
    ) {
        val values =
            rowValues(cal, resource.main).apply {
                put(Events._SYNC_ID, name)
                put(Events.UID_2445, resource.uid)
                put(Events.SYNC_DATA1, etag)
                put(Events.SYNC_DATA2, text)
                put(Events.DIRTY, 0)
                put(Events.DELETED, 0)
            }
        val id =
            if (existing != null) {
                resolver.update(ContentUris.withAppendedId(eventsUri, existing.id), values, null, null)
                existing.id
            } else {
                ContentUris.parseId(resolver.insert(eventsUri, values) ?: return)
            }
        replaceDetails(id, resource.main)
        resolver.delete(eventsUri, "${Events.ORIGINAL_ID} = ?", arrayOf(id.toString()))
        for (x in resource.exceptions) {
            val xv =
                rowValues(cal, x).apply {
                    put(Events.ORIGINAL_SYNC_ID, name)
                    put(Events.ORIGINAL_ID, id)
                    put(Events.ORIGINAL_INSTANCE_TIME, x.originalInstanceTime)
                    put(Events.ORIGINAL_ALL_DAY, if (x.originalAllDay) 1 else 0)
                    put(Events.DIRTY, 0)
                }
            val xid = resolver.insert(eventsUri, xv)?.let { ContentUris.parseId(it) } ?: continue
            replaceDetails(xid, x)
        }
    }

    private fun rowValues(
        cal: LocalCalendar,
        row: EventRow,
    ) = ContentValues().apply {
        put(Events.CALENDAR_ID, cal.id)
        put(Events.TITLE, row.title)
        put(Events.EVENT_LOCATION, row.location)
        put(Events.DESCRIPTION, row.description)
        put(Events.DTSTART, row.start)
        put(Events.ALL_DAY, if (row.allDay) 1 else 0)
        put(Events.EVENT_TIMEZONE, row.timeZone)
        if (row.end != null) {
            put(Events.DTEND, row.end)
            putNull(Events.DURATION)
        } else {
            putNull(Events.DTEND)
            put(Events.DURATION, row.duration)
        }
        put(Events.RRULE, row.rrule)
        put(Events.RDATE, row.rdate)
        put(Events.EXDATE, row.exdate)
        if (row.status == null) putNull(Events.STATUS) else put(Events.STATUS, row.status)
        put(Events.AVAILABILITY, row.availability)
        put(Events.ACCESS_LEVEL, row.accessLevel)
        put(Events.ORGANIZER, row.organizer ?: account.name)
        put(Events.HAS_ALARM, if (row.reminders.isEmpty()) 0 else 1)
        put(Events.HAS_ATTENDEE_DATA, 1)
    }

    private fun replaceDetails(
        id: Long,
        row: EventRow,
    ) {
        val reminders = CalendarAccounts.asSyncAdapter(Reminders.CONTENT_URI, account)
        val attendees = CalendarAccounts.asSyncAdapter(Attendees.CONTENT_URI, account)
        resolver.delete(reminders, "${Reminders.EVENT_ID} = ?", arrayOf(id.toString()))
        resolver.delete(attendees, "${Attendees.EVENT_ID} = ?", arrayOf(id.toString()))
        row.reminders.forEach { m ->
            resolver.insert(
                reminders,
                ContentValues().apply {
                    put(Reminders.EVENT_ID, id)
                    put(Reminders.MINUTES, m)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                },
            )
        }
        row.organizer?.let { org ->
            resolver.insert(
                attendees,
                ContentValues().apply {
                    put(Attendees.EVENT_ID, id)
                    put(Attendees.ATTENDEE_EMAIL, org)
                    put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER)
                },
            )
        }
        row.attendees.forEach { a ->
            resolver.insert(
                attendees,
                ContentValues().apply {
                    put(Attendees.EVENT_ID, id)
                    put(Attendees.ATTENDEE_EMAIL, a.email)
                    put(Attendees.ATTENDEE_NAME, a.name)
                    put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
                    put(Attendees.ATTENDEE_TYPE, a.type)
                    put(Attendees.ATTENDEE_STATUS, a.status)
                },
            )
        }
    }

    /** Removes an event and its changed occurrences from the phone (as the sync adapter: for good). */
    private fun deleteEvent(e: LocalEvent) {
        resolver.delete(eventsUri, "${Events.ORIGINAL_ID} = ?", arrayOf(e.id.toString()))
        resolver.delete(ContentUris.withAppendedId(eventsUri, e.id), null, null)
    }

    private fun href(
        cal: LocalCalendar,
        name: String,
    ) = Dav.resolve(if (cal.href.endsWith("/")) cal.href else "${cal.href}/", name)

    private fun nameOf(href: String) = URI(href).rawPath.trimEnd('/').substringAfterLast('/')

    private fun sameHref(
        a: String,
        b: String,
    ) = runCatching { URI(a).path.trimEnd('/') == URI(b).path.trimEnd('/') }.getOrDefault(a == b)

    companion object {
        /** CAL_SYNC2 on a calendar whose colour was changed here and not yet sent. */
        const val COLOR_DIRTY = "color"
    }
}
