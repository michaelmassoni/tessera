package io.github.michaelmassoni.tessera.tasks.engine

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * A task as a VTODO in a VCALENDAR, and back. Writing starts from the server's copy, so every
 * property and component Tessera doesn't edit (alarms, categories, other apps' X- properties)
 * goes back unchanged; a DUE or RRULE Tessera didn't change is kept exactly as it was written
 * (time zone, BYMONTHDAY …). Pure, so it's unit tested.
 */
object VTodo {
    private const val REPEAT_FROM = "X-TESSERA-REPEAT-FROM"
    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val date = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** The master VTODO of a calendar object (not a RECURRENCE-ID override). */
    private fun todo(calendar: Component) =
        calendar.children.firstOrNull { it.name == "VTODO" && it.first("RECURRENCE-ID") == null }
            ?: calendar.children.firstOrNull { it.name == "VTODO" }

    /** The task in [text], in list [listId], with times turned into [zone]'s local time; null if there's no VTODO. */
    fun read(
        text: String,
        listId: String,
        zone: ZoneId,
    ): Task? {
        val calendar = ICal.parse(text) ?: return null
        val t = todo(calendar) ?: return null
        val uid = t.first("UID")?.value ?: return null
        val status = t.first("STATUS")?.value?.uppercase()
        val completed =
            t.first("COMPLETED")?.let { instant(it, zone) }
                ?: if (status == "COMPLETED") time(t.first("LAST-MODIFIED"), zone) ?: 1L else null
        val priority = t.first("PRIORITY")?.value?.toIntOrNull() ?: 0
        val parent =
            t.all("RELATED-TO").firstOrNull { (it.param("RELTYPE") ?: "PARENT").equals("PARENT", ignoreCase = true) }?.value
        return Task(
            id = uid,
            listId = listId,
            title =
                t
                    .first("SUMMARY")
                    ?.value
                    ?.let(ICal::unescape)
                    .orEmpty(),
            notes =
                t
                    .first("DESCRIPTION")
                    ?.value
                    ?.let(ICal::unescape)
                    .orEmpty(),
            due = t.first("DUE")?.let { due(it, zone) },
            repeat =
                t.first("RRULE")?.value?.let {
                    Recurrence.parse(it, fromCompletion = t.first(REPEAT_FROM)?.value.equals("COMPLETION", ignoreCase = true))
                },
            starred = priority in 1..4,
            parentId = parent,
            completedAt = if (status == "COMPLETED" || t.first("COMPLETED") != null) completed else null,
            order = t.first("X-APPLE-SORT-ORDER")?.value?.toLongOrNull() ?: 0,
            createdAt = time(t.first("CREATED"), zone) ?: 0,
            modifiedAt = time(t.first("LAST-MODIFIED"), zone) ?: 0,
            dirty = false,
            ical = text,
        )
    }

    /** The VCALENDAR for [task], built on its server copy if it has one. */
    fun write(
        task: Task,
        now: Long,
        zone: ZoneId,
    ): String {
        val calendar =
            task.ical?.let(ICal::parse)?.takeIf { todo(it) != null }
                ?: Component(
                    "VCALENDAR",
                    mutableListOf(Property("VERSION", value = "2.0"), Property("PRODID", value = "-//Tessera//Tasks//EN")),
                    mutableListOf(Component("VTODO", mutableListOf(Property("UID", value = task.id)))),
                )
        val t = todo(calendar)!!
        val stamp = utc(now)
        t.set(Property("DTSTAMP", value = stamp))
        t.set(Property("LAST-MODIFIED", value = stamp))
        if (t.first("CREATED") == null) t.set(Property("CREATED", value = utc(task.createdAt.takeIf { it > 0 } ?: now)))
        t.set(Property("SUMMARY", value = ICal.escape(task.title)))
        if (task.notes.isBlank()) t.remove("DESCRIPTION") else t.set(Property("DESCRIPTION", value = ICal.escape(task.notes)))

        // Due: kept as written unless it changed here; a moved due date moves DTSTART with it.
        val oldDue = t.first("DUE")?.let { due(it, zone) }
        if (task.due != oldDue) {
            if (task.due == null) {
                t.remove("DUE")
            } else {
                t.set(dueProperty("DUE", task.due))
                if (oldDue != null) shiftStart(t, oldDue, task.due, zone)
            }
        }

        // Repeat: kept as written unless it changed here.
        val oldRule = t.first("RRULE")?.value
        val oldRepeat = oldRule?.let { Recurrence.parse(it, t.first(REPEAT_FROM)?.value.equals("COMPLETION", ignoreCase = true)) }
        if (task.repeat != oldRepeat) {
            if (task.repeat == null) t.remove("RRULE") else t.set(Property("RRULE", value = Recurrence.format(task.repeat)))
        }
        if (task.repeat?.fromCompletion == true) t.set(Property(REPEAT_FROM, value = "COMPLETION")) else t.remove(REPEAT_FROM)

        // Completed, or open again (a repeating task moved to its next date is open).
        if (task.completedAt != null) {
            t.set(Property("STATUS", value = "COMPLETED"))
            t.set(Property("COMPLETED", value = utc(task.completedAt)))
            t.set(Property("PERCENT-COMPLETE", value = "100"))
        } else {
            t.set(Property("STATUS", value = "NEEDS-ACTION"))
            t.remove("COMPLETED")
            t.remove("PERCENT-COMPLETE")
        }

        // Starred is a high priority (as Tasks.org); other priorities are left alone.
        val priority = t.first("PRIORITY")?.value?.toIntOrNull() ?: 0
        if (task.starred && priority !in 1..4) t.set(Property("PRIORITY", value = "1"))
        if (!task.starred && priority in 1..4) t.remove("PRIORITY")

        t.properties.removeAll { it.name == "RELATED-TO" && (it.param("RELTYPE") ?: "PARENT").equals("PARENT", ignoreCase = true) }
        task.parentId?.let { t.properties.add(Property("RELATED-TO", listOf("RELTYPE" to "PARENT"), it)) }
        t.set(Property("X-APPLE-SORT-ORDER", value = task.order.toString()))
        return ICal.write(calendar)
    }

    private fun dueProperty(
        name: String,
        due: Due,
    ): Property =
        if (due.time == null) {
            Property(name, listOf("VALUE" to "DATE"), due.localDate.format(date))
        } else {
            // Floating local time: 8:00 stays 8:00 wherever the phone is and through daylight saving.
            Property(name, value = due.dateTime.format(dateTime))
        }

    private fun shiftStart(
        t: Component,
        from: Due,
        to: Due,
        zone: ZoneId,
    ) {
        val start = t.first("DTSTART") ?: return
        val old = due(start, zone) ?: return
        val minutes = ChronoUnit.MINUTES.between(from.dateTime, to.dateTime)
        val moved = old.dateTime.plusMinutes(minutes)
        t.set(dueProperty("DTSTART", Due.of(moved.toLocalDate(), if (old.time != null) moved.toLocalTime() else null)))
    }

    /** A DUE or DTSTART: a date, a floating time, UTC, or a TZID'd time, as local time in [zone]. */
    fun due(
        p: Property,
        zone: ZoneId,
    ): Due? {
        val v = p.value.trim()
        return try {
            when {
                v.length == 8 || p.param("VALUE").equals("DATE", ignoreCase = true) -> {
                    Due.of(LocalDate.parse(v.take(8), date))
                }

                v.endsWith("Z") -> {
                    val local =
                        LocalDateTime
                            .parse(
                                v.dropLast(1),
                                dateTime,
                            ).atOffset(ZoneOffset.UTC)
                            .atZoneSameInstant(zone)
                            .toLocalDateTime()
                    Due.of(local.toLocalDate(), local.toLocalTime())
                }

                else -> {
                    val parsed = LocalDateTime.parse(v.take(15), dateTime)
                    val tz = p.param("TZID")?.let { runCatching { ZoneId.of(it.removePrefix("/")) }.getOrNull() }
                    val local = if (tz == null) parsed else parsed.atZone(tz).withZoneSameInstant(zone).toLocalDateTime()
                    Due.of(local.toLocalDate(), local.toLocalTime())
                }
            }
        } catch (_: java.time.format.DateTimeParseException) {
            null
        }
    }

    /** A timestamp (COMPLETED, CREATED …) to the second: UTC, TZID'd or floating. */
    private fun instant(
        p: Property,
        zone: ZoneId,
    ): Long? {
        val v = p.value.trim()
        return try {
            when {
                v.length == 8 -> {
                    LocalDate.parse(v, date).atStartOfDay(zone)
                }

                v.endsWith("Z") -> {
                    LocalDateTime.parse(v.dropLast(1), dateTime).atZone(ZoneOffset.UTC)
                }

                else -> {
                    val tz = p.param("TZID")?.let { runCatching { ZoneId.of(it.removePrefix("/")) }.getOrNull() } ?: zone
                    LocalDateTime.parse(v.take(15), dateTime).atZone(tz)
                }
            }.toInstant().toEpochMilli()
        } catch (_: java.time.format.DateTimeParseException) {
            null
        }
    }

    private fun time(
        p: Property?,
        zone: ZoneId,
    ): Long? = p?.let { instant(it, zone) }

    private fun utc(ms: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC).format(dateTime) + "Z"
}
