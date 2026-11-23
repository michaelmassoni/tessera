package io.github.michaelmassoni.tessera.calendar.engine

import io.github.michaelmassoni.tessera.core.dav.Component
import io.github.michaelmassoni.tessera.core.dav.ICal
import io.github.michaelmassoni.tessera.core.dav.Property
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.zone.ZoneOffsetTransitionRule
import java.util.Locale

/**
 * An event as CalendarContract stores it: the columns sync reads and writes. Times are epoch ms;
 * all-day events use UTC midnights and the "UTC" zone, as the provider expects.
 */
data class EventRow(
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val start: Long = 0,
    /** For a single event; null for a repeating one, which has [duration]. */
    val end: Long? = null,
    /** "P3600S" / "P1D", for a repeating event. */
    val duration: String? = null,
    val allDay: Boolean = false,
    /** An Olson ID ("Australia/Melbourne"), "UTC", or "GMT+10:00" for a fixed offset. */
    val timeZone: String = "UTC",
    val rrule: String? = null,
    val rdate: String? = null,
    /** Comma-separated UTC stamps ("20261125T030000Z"), as the provider stores EXDATE. */
    val exdate: String? = null,
    val status: Int? = null,
    val availability: Int = EventRow.BUSY,
    val accessLevel: Int = EventRow.ACCESS_DEFAULT,
    val organizer: String? = null,
    /** Minutes before the start. */
    val reminders: List<Int> = emptyList(),
    val attendees: List<AttendeeRow> = emptyList(),
    /** For a changed occurrence: the original start it replaces. */
    val originalInstanceTime: Long? = null,
    val originalAllDay: Boolean = false,
) {
    val cancelled: Boolean get() = status == CANCELED

    companion object {
        // CalendarContract's values, kept here so the engine has no Android types.
        const val TENTATIVE = 0
        const val CONFIRMED = 1
        const val CANCELED = 2
        const val BUSY = 0
        const val FREE = 1
        const val ACCESS_DEFAULT = 0
        const val ACCESS_CONFIDENTIAL = 1
        const val ACCESS_PRIVATE = 2
        const val ACCESS_PUBLIC = 3
    }
}

data class AttendeeRow(
    val email: String,
    val name: String = "",
    val organizer: Boolean = false,
    /** Attendees.TYPE_*: 0 none, 1 required, 2 optional, 3 resource. */
    val type: Int = 1,
    /** Attendees.ATTENDEE_STATUS_*: 0 none, 1 accepted, 2 declined, 3 invited, 4 tentative. */
    val status: Int = 0,
)

/** One calendar resource (a .ics file): its event and the occurrences changed in it. */
data class EventResource(
    val uid: String,
    val main: EventRow,
    val exceptions: List<EventRow> = emptyList(),
)

/**
 * VEVENTs to and from [EventRow]s for CalDAV sync. Reading keeps nothing but the mapped fields;
 * writing starts from the previous text of the resource and changes only what changed, so every
 * property and component Tessera doesn't understand survives (attendees, categories, other apps'
 * X- properties). Pure: time zones Java doesn't know are resolved by [windowsZone] (ICU on Android).
 */
class EventIcs(
    /** The zone for a floating time (no TZID, no Z): the phone's. */
    private val localZone: ZoneId,
    /** Windows zone names ("AUS Eastern Standard Time") to Olson IDs; null if unknown. */
    private val windowsZone: (String) -> String? = { null },
) {
    private val utcStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val localStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateStamp = DateTimeFormatter.BASIC_ISO_DATE

    // ---- reading ----

    fun read(text: String): EventResource? {
        val calendar = ICal.parse(text) ?: return null
        val zones = calendar.children.filter { it.name.equals("VTIMEZONE", true) }
        val events = calendar.children.filter { it.name.equals("VEVENT", true) }
        val main = events.firstOrNull { it.first("RECURRENCE-ID") == null } ?: events.firstOrNull() ?: return null
        val uid = main.first("UID")?.value ?: return null
        val mainRow = row(main, zones) ?: return null
        val exceptions =
            events.filter { it !== main && it.first("RECURRENCE-ID") != null }.mapNotNull { e ->
                val rid = e.first("RECURRENCE-ID")!!
                val original = time(rid, zones) ?: return@mapNotNull null
                row(e, zones)?.copy(originalInstanceTime = original.ms, originalAllDay = original.date)
            }
        return EventResource(uid, mainRow, exceptions)
    }

    private data class Time(
        val ms: Long,
        val date: Boolean,
        val zone: String,
    )

    private fun row(
        e: Component,
        zones: List<Component>,
    ): EventRow? {
        val start = e.first("DTSTART")?.let { time(it, zones) } ?: return null
        val end =
            e.first("DTEND")?.let { time(it, zones)?.ms }
                ?: e.first("DURATION")?.let { start.ms + duration(it.value).toMillis() }
                ?: if (start.date) start.ms + DAY else start.ms
        val rrule = e.first("RRULE")?.value?.ifBlank { null }
        val rdates = stamps(e.all("RDATE"), zones)
        val repeating = rrule != null || rdates.isNotEmpty()
        val length = (end - start.ms).coerceAtLeast(0)
        return EventRow(
            title =
                e
                    .first("SUMMARY")
                    ?.value
                    ?.let(ICal::unescape)
                    .orEmpty(),
            location =
                e
                    .first("LOCATION")
                    ?.value
                    ?.let(ICal::unescape)
                    .orEmpty(),
            description =
                e
                    .first("DESCRIPTION")
                    ?.value
                    ?.let(ICal::unescape)
                    .orEmpty(),
            start = start.ms,
            end = if (repeating) null else end,
            duration = if (repeating) duration(length, start.date) else null,
            allDay = start.date,
            timeZone = if (start.date) "UTC" else start.zone,
            rrule = rrule,
            rdate = rdates.ifEmpty { null }?.joinToString(","),
            exdate = stamps(e.all("EXDATE"), zones).ifEmpty { null }?.joinToString(","),
            status =
                when (e.first("STATUS")?.value?.uppercase(Locale.ROOT)) {
                    "TENTATIVE" -> EventRow.TENTATIVE
                    "CONFIRMED" -> EventRow.CONFIRMED
                    "CANCELLED" -> EventRow.CANCELED
                    else -> null
                },
            availability = if (e.first("TRANSP")?.value.equals("TRANSPARENT", true)) EventRow.FREE else EventRow.BUSY,
            accessLevel =
                when (e.first("CLASS")?.value?.uppercase(Locale.ROOT)) {
                    "PRIVATE" -> EventRow.ACCESS_PRIVATE
                    "CONFIDENTIAL" -> EventRow.ACCESS_CONFIDENTIAL
                    "PUBLIC" -> EventRow.ACCESS_PUBLIC
                    else -> EventRow.ACCESS_DEFAULT
                },
            organizer = e.first("ORGANIZER")?.value?.let(::mailto),
            reminders = alarms(e, start.ms).distinct().sorted(),
            attendees = e.all("ATTENDEE").map(::attendee),
        )
    }

    /** A DTSTART/DTEND/RECURRENCE-ID value as epoch ms, with whether it's a date and its zone. */
    private fun time(
        p: Property,
        zones: List<Component>,
    ): Time? {
        val v = p.value.trim()
        return runCatching {
            if (p.param("VALUE").equals("DATE", true) || v.length == 8) {
                val day = LocalDate.parse(v.take(8), dateStamp)
                Time(day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true, "UTC")
            } else if (v.endsWith("Z")) {
                Time(Instant.from(utcStamp.parse(v)).toEpochMilli(), false, "UTC")
            } else {
                val zone = p.param("TZID")?.let { zoneOf(it, zones) } ?: localZone
                val local = LocalDateTime.parse(v.take(15), localStamp)
                Time(local.atZone(zone).toInstant().toEpochMilli(), false, androidZoneId(zone))
            }
        }.getOrNull()
    }

    /** All EXDATE/RDATE values as UTC stamps, as the provider stores them. */
    private fun stamps(
        props: List<Property>,
        zones: List<Component>,
    ): List<String> =
        props.flatMap { p ->
            p.value.split(',').mapNotNull { one ->
                time(p.copy(value = one.trim()), zones)?.let { utcStamp.format(Instant.ofEpochMilli(it.ms)) }
            }
        }

    /** The zone a TZID names: an Olson ID (also inside a path), a Windows name, else the VTIMEZONE's offset. */
    fun zoneOf(
        tzid: String,
        zones: List<Component> = emptyList(),
    ): ZoneId? {
        val id = tzid.trim('"')
        olson(id)?.let { return it }
        // "/mozilla.org/20050126_1/Europe/Berlin", "/freeassociation.sourceforge.net/Europe/Berlin"
        val parts = id.split('/').filter { it.isNotEmpty() }
        for (i in parts.indices) olson(parts.drop(i).joinToString("/"))?.let { return it }
        windowsZone(id)?.let { w -> olson(w)?.let { return it } }
        val vtz = zones.firstOrNull { it.first("TZID")?.value.equals(id, true) } ?: return null
        val standard = vtz.children.firstOrNull { it.name.equals("STANDARD", true) } ?: vtz.children.firstOrNull()
        return standard?.first("TZOFFSETTO")?.value?.let(::offset)
    }

    private fun olson(id: String): ZoneId? = runCatching { ZoneId.of(id) }.getOrNull()?.takeIf { it.id.contains('/') || it.id == "UTC" }

    private fun offset(text: String): ZoneOffset? =
        runCatching {
            val t = text.trim()
            ZoneOffset.of(if (t.length == 5) t.substring(0, 3) + ":" + t.substring(3) else t)
        }.getOrNull()

    /** The provider takes Olson IDs, and "GMT+10:00" for a fixed offset. */
    private fun androidZoneId(zone: ZoneId): String =
        when (zone) {
            is ZoneOffset -> if (zone.totalSeconds == 0) "UTC" else "GMT" + zone.id
            else -> zone.id
        }

    /** VALARMs as minutes before the start (alarms after the start, or e-mail ones, are left out). */
    private fun alarms(
        e: Component,
        start: Long,
    ): List<Int> =
        e.children.filter { it.name.equals("VALARM", true) }.mapNotNull { a ->
            val trigger = a.first("TRIGGER") ?: return@mapNotNull null
            if (a.first("ACTION")?.value.equals("EMAIL", true)) return@mapNotNull null
            val minutes =
                if (trigger.param("VALUE").equals("DATE-TIME", true)) {
                    runCatching { (start - Instant.from(utcStamp.parse(trigger.value)).toEpochMilli()) / 60_000 }.getOrNull()
                } else {
                    if (trigger.param("RELATED").equals("END", true)) return@mapNotNull null
                    -duration(trigger.value).toMinutes()
                }
            minutes?.takeIf { it >= 0 }?.toInt()
        }

    private fun attendee(p: Property) =
        AttendeeRow(
            email = mailto(p.value),
            name = p.param("CN").orEmpty(),
            type =
                when {
                    p.param("CUTYPE").equals("RESOURCE", true) || p.param("CUTYPE").equals("ROOM", true) -> 3
                    p.param("ROLE").equals("OPT-PARTICIPANT", true) -> 2
                    p.param("ROLE").equals("NON-PARTICIPANT", true) -> 0
                    else -> 1
                },
            status =
                when (p.param("PARTSTAT")?.uppercase(Locale.ROOT)) {
                    "ACCEPTED" -> 1
                    "DECLINED" -> 2
                    "TENTATIVE" -> 4
                    "NEEDS-ACTION" -> 3
                    else -> 0
                },
        )

    private fun mailto(v: String) = v.trim().removePrefix("mailto:").removePrefix("MAILTO:")

    // ---- writing ----

    /**
     * The resource's text for [resource], starting from [previous] (the server's last version) so
     * everything not mapped is kept; [now] stamps DTSTAMP and LAST-MODIFIED.
     */
    fun write(
        resource: EventResource,
        previous: String?,
        now: Instant,
    ): String {
        val calendar =
            previous?.let(ICal::parse)?.takeIf { it.name.equals("VCALENDAR", true) }
                ?: Component(
                    "VCALENDAR",
                    mutableListOf(Property("VERSION", value = "2.0"), Property("PRODID", value = "-//Tessera//Calendar//EN")),
                )
        val old = read(previous.orEmpty())
        val oldEvents = calendar.children.filter { it.name.equals("VEVENT", true) }
        val oldMain = oldEvents.firstOrNull { it.first("RECURRENCE-ID") == null }

        // Cancelled occurrences become EXDATEs on the series; the rest are RECURRENCE-ID events.
        val cancelled = resource.exceptions.filter { it.cancelled && it.originalInstanceTime != null }
        val kept = resource.exceptions.filter { !it.cancelled && it.originalInstanceTime != null }
        val exdate =
            (
                resource.main.exdate
                    .orEmpty()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() } + cancelled.map { utcStamp.format(Instant.ofEpochMilli(it.originalInstanceTime!!)) }
            ).distinct()
        val main = resource.main.copy(exdate = exdate.ifEmpty { null }?.joinToString(","))
        val mainComponent = oldMain ?: Component("VEVENT").also { calendar.children += it }
        apply(mainComponent, main, old?.main, resource.uid, null, now)

        // Changed occurrences: update those that were there, add new ones, drop those gone.
        val oldExceptions = oldEvents.filter { it !== oldMain }
        val keepComponents = mutableSetOf<Component>()
        for (x in kept) {
            val previousRow = old?.exceptions?.firstOrNull { it.originalInstanceTime == x.originalInstanceTime }
            val component =
                oldExceptions.firstOrNull { c ->
                    c.first("RECURRENCE-ID")?.let { sameInstant(it, x.originalInstanceTime!!, calendar) } ==
                        true
                }
                    ?: Component("VEVENT").also { calendar.children += it }
            apply(component, x, previousRow, resource.uid, x.originalInstanceTime!! to (x.originalAllDay || main.allDay), now, main)
            keepComponents += component
        }
        calendar.children.removeAll { it.name.equals("VEVENT", true) && it !== mainComponent && it !in keepComponents }

        ensureZones(calendar)
        return ICal.write(calendar)
    }

    private fun sameInstant(
        rid: Property,
        ms: Long,
        calendar: Component,
    ): Boolean = time(rid, calendar.children.filter { it.name.equals("VTIMEZONE", true) })?.ms == ms

    /** Sets [c]'s mapped properties from [row], leaving those unchanged since [before] as they were. */
    private fun apply(
        c: Component,
        row: EventRow,
        before: EventRow?,
        uid: String,
        recurrence: Pair<Long, Boolean>?,
        now: Instant,
        series: EventRow? = null,
    ) {
        c.set(Property("UID", value = uid))
        c.set(Property("DTSTAMP", value = utcStamp.format(now)))
        val changed = before == null || row.copy(reminders = before.reminders, attendees = before.attendees) != before
        if (changed || row.reminders != before.reminders) c.set(Property("LAST-MODIFIED", value = utcStamp.format(now)))

        if (recurrence != null && c.first("RECURRENCE-ID") == null) {
            val (ms, date) = recurrence
            c.set(timeProperty("RECURRENCE-ID", ms, date, series?.timeZone ?: row.timeZone))
        }
        val timesChanged =
            before == null || before.start != row.start || before.allDay != row.allDay || before.timeZone != row.timeZone ||
                endOf(before) != endOf(row)
        if (timesChanged) {
            c.set(timeProperty("DTSTART", row.start, row.allDay, row.timeZone))
            c.remove("DURATION")
            c.set(timeProperty("DTEND", endOf(row), row.allDay, row.timeZone))
            val sequence = c.first("SEQUENCE")?.value?.toIntOrNull()
            if (before != null) c.set(Property("SEQUENCE", value = ((sequence ?: 0) + 1).toString()))
        }
        text(c, "SUMMARY", row.title, before?.title)
        text(c, "LOCATION", row.location, before?.location)
        text(c, "DESCRIPTION", row.description, before?.description)
        if (recurrence == null) {
            if (before == null || before.rrule != row.rrule) {
                if (row.rrule == null) c.remove("RRULE") else c.set(Property("RRULE", value = row.rrule))
            }
            if (before == null || before.exdate != row.exdate) {
                c.remove("EXDATE")
                row.exdate?.split(',')?.filter { it.isNotBlank() }?.forEach { stamp ->
                    c.properties += exdateProperty(stamp.trim(), row)
                }
            }
            if (before == null || before.rdate != row.rdate) {
                c.remove("RDATE")
                row.rdate?.let { c.properties += Property("RDATE", value = it) }
            }
        }
        if (before == null || before.status != row.status) {
            when (row.status) {
                EventRow.TENTATIVE -> c.set(Property("STATUS", value = "TENTATIVE"))
                EventRow.CONFIRMED -> c.set(Property("STATUS", value = "CONFIRMED"))
                EventRow.CANCELED -> c.set(Property("STATUS", value = "CANCELLED"))
                else -> c.remove("STATUS")
            }
        }
        if (before == null || before.availability != row.availability) {
            if (row.availability == EventRow.FREE) c.set(Property("TRANSP", value = "TRANSPARENT")) else c.remove("TRANSP")
        }
        if (before == null || before.accessLevel != row.accessLevel) {
            when (row.accessLevel) {
                EventRow.ACCESS_PRIVATE -> c.set(Property("CLASS", value = "PRIVATE"))
                EventRow.ACCESS_CONFIDENTIAL -> c.set(Property("CLASS", value = "CONFIDENTIAL"))
                EventRow.ACCESS_PUBLIC -> c.set(Property("CLASS", value = "PUBLIC"))
                else -> c.remove("CLASS")
            }
        }
        // Reminders: the server's VALARMs stay as they were unless the reminders changed.
        if (before == null || before.reminders != row.reminders) {
            c.children.removeAll { it.name.equals("VALARM", true) }
            row.reminders.distinct().sorted().forEach { m ->
                c.children +=
                    Component(
                        "VALARM",
                        mutableListOf(
                            Property("ACTION", value = "DISPLAY"),
                            Property("DESCRIPTION", value = ICal.escape(row.title.ifBlank { "Reminder" })),
                            Property("TRIGGER", value = if (m == 0) "PT0S" else "-PT${m}M"),
                        ),
                    )
            }
        }
    }

    private fun text(
        c: Component,
        name: String,
        value: String,
        before: String?,
    ) {
        if (before != null && before == value) return
        if (value.isEmpty()) c.remove(name) else c.set(Property(name, value = ICal.escape(value)))
    }

    private fun endOf(row: EventRow): Long =
        row.end ?: (row.start + (row.duration?.let { duration(it).toMillis() } ?: if (row.allDay) DAY else 0))

    private fun timeProperty(
        name: String,
        ms: Long,
        date: Boolean,
        zone: String,
    ): Property {
        val instant = Instant.ofEpochMilli(ms)
        if (date) return Property(name, listOf("VALUE" to "DATE"), dateStamp.format(instant.atZone(ZoneOffset.UTC).toLocalDate()))
        val z = javaZone(zone)
        if (z == null || z == ZoneOffset.UTC || z is ZoneOffset) return Property(name, value = utcStamp.format(instant))
        return Property(name, listOf("TZID" to z.id), localStamp.format(instant.atZone(z).toLocalDateTime()))
    }

    private fun exdateProperty(
        stamp: String,
        row: EventRow,
    ): Property {
        val ms = runCatching { Instant.from(utcStamp.parse(stamp)).toEpochMilli() }.getOrNull() ?: return Property("EXDATE", value = stamp)
        return timeProperty("EXDATE", ms, row.allDay, row.timeZone)
    }

    private fun javaZone(id: String): ZoneId? =
        when {
            id == "UTC" -> ZoneOffset.UTC
            id.startsWith("GMT+") || id.startsWith("GMT-") -> runCatching { ZoneOffset.of(id.removePrefix("GMT")) }.getOrNull()
            else -> olson(id)
        }

    /** Adds a VTIMEZONE for every TZID used that the calendar doesn't define yet. */
    private fun ensureZones(calendar: Component) {
        val used =
            calendar.children
                .filter { it.name.equals("VEVENT", true) }
                .flatMap { it.properties }
                .mapNotNull { it.param("TZID") }
                .toSet()
        val defined =
            calendar.children
                .filter { it.name.equals("VTIMEZONE", true) }
                .mapNotNull { it.first("TZID")?.value }
                .toSet()
        val missing = used - defined
        if (missing.isEmpty()) return
        val firstEvent = calendar.children.indexOfFirst { it.name.equals("VEVENT", true) }.coerceAtLeast(0)
        missing.mapNotNull { id -> olson(id)?.let(::vtimezone) }.reversed().forEach { calendar.children.add(firstEvent, it) }
    }

    /** A VTIMEZONE from the zone's current rules (or its present offset when it has no changes). */
    fun vtimezone(zone: ZoneId): Component {
        val rules = zone.rules
        val tz = Component("VTIMEZONE", mutableListOf(Property("TZID", value = zone.id)))
        val transitions = rules.transitionRules
        if (transitions.isEmpty()) {
            val offset = rules.getOffset(Instant.now())
            tz.children +=
                Component(
                    "STANDARD",
                    mutableListOf(
                        Property("DTSTART", value = "19700101T000000"),
                        Property("TZOFFSETFROM", value = offsetText(offset)),
                        Property("TZOFFSETTO", value = offsetText(offset)),
                    ),
                )
            return tz
        }
        for (rule in transitions) {
            val daylight = rule.offsetAfter.totalSeconds > rule.standardOffset.totalSeconds
            val first = rule.createTransition(1970)
            tz.children +=
                Component(
                    if (daylight) "DAYLIGHT" else "STANDARD",
                    mutableListOf(
                        Property("DTSTART", value = localStamp.format(first.dateTimeBefore)),
                        Property("TZOFFSETFROM", value = offsetText(rule.offsetBefore)),
                        Property("TZOFFSETTO", value = offsetText(rule.offsetAfter)),
                        Property("RRULE", value = yearly(rule)),
                    ),
                )
        }
        return tz
    }

    private fun yearly(rule: ZoneOffsetTransitionRule): String {
        val month = rule.month.value
        val day = rule.dayOfMonthIndicator
        val dow = rule.dayOfWeek ?: return "FREQ=YEARLY;BYMONTH=$month;BYMONTHDAY=$day"
        val code = dow.name.take(2)
        return if (day < 0) {
            "FREQ=YEARLY;BYMONTH=$month;BYDAY=-1$code"
        } else {
            // "The first <day> on or after <date>": that day of the week within a 7-day window.
            "FREQ=YEARLY;BYMONTH=$month;BYDAY=$code;BYMONTHDAY=${(day..(day + 6)).joinToString(",")}"
        }
    }

    private fun offsetText(o: ZoneOffset): String {
        val s = o.totalSeconds
        val sign = if (s < 0) "-" else "+"
        val a = kotlin.math.abs(s)
        return "%s%02d%02d".format(Locale.ROOT, sign, a / 3600, (a % 3600) / 60)
    }

    companion object {
        private const val DAY = 86_400_000L

        /** RFC 5545 durations: "-PT15M", "P1D", "-P1DT2H", "PT0S", "P1W". */
        fun duration(text: String): Duration {
            val t = text.trim()
            val negative = t.startsWith("-")
            val body = t.removePrefix("-").removePrefix("+")
            val weeks =
                Regex("^P(\\d+)W$")
                    .find(body)
                    ?.groupValues
                    ?.get(1)
                    ?.toLong()
            val d =
                if (weeks != null) {
                    Duration.ofDays(weeks * 7)
                } else {
                    runCatching { Duration.parse(body) }.getOrDefault(Duration.ZERO)
                }
            return if (negative) d.negated() else d
        }

        /** The provider's DURATION: "P2D" for all-day events, "P3600S" otherwise. */
        fun duration(
            millis: Long,
            allDay: Boolean,
        ): String {
            val seconds = (millis / 1000).coerceAtLeast(0)
            return if (allDay) "P${(seconds / 86_400).coerceAtLeast(1)}D" else "P${seconds}S"
        }
    }
}
