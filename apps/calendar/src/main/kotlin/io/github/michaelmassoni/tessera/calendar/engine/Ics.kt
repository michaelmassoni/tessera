package io.github.michaelmassoni.tessera.calendar.engine

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Reads VEVENTs (with their VALARMs) from an iCalendar file into [EventDraft]s. */
object Ics {
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /**
     * @param defaultZone used for "floating" times (no TZID and no trailing Z).
     * Events without a start are skipped. [EventDraft.calendarId] is left 0 for the caller to set.
     */
    fun parse(
        text: String,
        defaultZone: ZoneId,
    ): List<EventDraft> {
        val events = mutableListOf<EventDraft>()
        var event: MutableMap<String, Pair<Map<String, String>, String>>? = null
        var alarms = mutableListOf<Int>()
        var inAlarm = false
        var alarmTrigger: Int? = null
        for (line in unfold(text)) {
            val colon = colonIndex(line)
            if (colon < 0) continue
            val head = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val name = head.substringBefore(';').uppercase()
            val params =
                head
                    .split(';')
                    .drop(1)
                    .mapNotNull { p ->
                        p.indexOf('=').takeIf { it > 0 }?.let {
                            p.substring(0, it).uppercase() to
                                p.substring(it + 1).trim('"')
                        }
                    }.toMap()
            when {
                name == "BEGIN" && value.equals("VEVENT", true) -> {
                    event = mutableMapOf()
                    alarms = mutableListOf()
                }

                name == "BEGIN" && value.equals("VALARM", true) -> {
                    inAlarm = true
                    alarmTrigger = null
                }

                name == "END" && value.equals("VALARM", true) -> {
                    inAlarm = false
                    alarmTrigger?.let { alarms += it }
                }

                name == "END" && value.equals("VEVENT", true) -> {
                    event?.let { build(it, alarms, defaultZone)?.let(events::add) }
                    event = null
                }

                inAlarm && name == "TRIGGER" -> {
                    alarmTrigger = trigger(value, params)
                }

                event != null && !inAlarm -> {
                    event[name] = params to value
                }
            }
        }
        return events
    }

    private fun build(
        props: Map<String, Pair<Map<String, String>, String>>,
        alarms: List<Int>,
        zone: ZoneId,
    ): EventDraft? {
        val (startParams, startValue) = props["DTSTART"] ?: return null
        val allDay = startParams["VALUE"].equals("DATE", true) || startValue.length == 8
        val start = time(startValue, startParams, zone, allDay) ?: return null
        val end =
            props["DTEND"]?.let { (p, v) -> time(v, p, zone, allDay) }
                ?: props["DURATION"]?.let { (_, v) -> runCatching { start + Duration.parse(isoDuration(v)).toMillis() }.getOrNull() }
                ?: if (allDay) start + DAY else start + HOUR
        val tz =
            when {
                allDay -> "UTC"
                startValue.endsWith("Z") -> "UTC"
                else -> startParams["TZID"]?.takeIf { runCatching { ZoneId.of(it) }.isSuccess } ?: zone.id
            }
        return EventDraft(
            title = text(props["SUMMARY"]?.second),
            allDay = allDay,
            start = start,
            end = maxOf(end, start),
            timeZone = tz,
            location = text(props["LOCATION"]?.second),
            description = text(props["DESCRIPTION"]?.second),
            rrule = props["RRULE"]?.second?.takeIf { it.isNotBlank() },
            reminders = alarms.distinct().sorted(),
        )
    }

    private fun time(
        value: String,
        params: Map<String, String>,
        zone: ZoneId,
        allDay: Boolean,
    ): Long? =
        runCatching {
            if (allDay) {
                LocalDate
                    .parse(value.take(8), DATE)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            } else {
                val local = LocalDateTime.parse(value.take(15), DATE_TIME)
                val z =
                    when {
                        value.endsWith("Z") -> ZoneOffset.UTC
                        params["TZID"] != null -> runCatching { ZoneId.of(params["TZID"]) }.getOrDefault(zone)
                        else -> zone
                    }
                local.atZone(z).toInstant().toEpochMilli()
            }
        }.getOrNull()

    /** A VALARM trigger as minutes before the start, or null for absolute or after-start triggers. */
    private fun trigger(
        value: String,
        params: Map<String, String>,
    ): Int? {
        if (params["VALUE"].equals("DATE-TIME", true)) return null
        if (params["RELATED"].equals("END", true)) return null
        val negative = value.startsWith("-")
        val duration = runCatching { Duration.parse(isoDuration(value.removePrefix("-").removePrefix("+"))) }.getOrNull() ?: return null
        val minutes = duration.toMinutes().toInt()
        return when {
            minutes == 0 -> 0
            negative -> minutes
            else -> null
        }
    }

    /** iCalendar durations allow weeks ("P1W") and days without "T"; java.time wants days/hours only. */
    private fun isoDuration(value: String): String {
        val weeks = Regex("^P(\\d+)W$").find(value)
        if (weeks != null) return "P${weeks.groupValues[1].toInt() * 7}D"
        return value
    }

    /** Unescapes TEXT values: backslash-n is a newline; backslash before , ; or backslash is dropped. */
    private fun text(value: String?): String {
        if (value == null) return ""
        val sb = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == BACKSLASH && i + 1 < value.length) {
                val next = value[i + 1]
                sb.append(if (next == 'n' || next == 'N') '\n' else next)
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) &&
                out.isNotEmpty()
            ) {
                out[out.lastIndex] = out.last() + raw.substring(1)
            } else {
                out += raw
            }
        }
        return out
    }

    private fun colonIndex(line: String): Int {
        var quoted = false
        line.forEachIndexed { i, c ->
            if (c == '"') quoted = !quoted
            if (c == ':' && !quoted) return i
        }
        return -1
    }

    private const val BACKSLASH = '\\'
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
}
