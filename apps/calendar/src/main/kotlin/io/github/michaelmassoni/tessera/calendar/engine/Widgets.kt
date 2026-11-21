package io.github.michaelmassoni.tessera.calendar.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A line of the agenda widget: a day heading, or an event under it. */
sealed interface AgendaRow {
    data class Day(
        val date: LocalDate,
    ) : AgendaRow

    data class Event(
        val instance: Instance,
        val date: LocalDate,
    ) : AgendaRow
}

object WidgetPlan {
    /** How far ahead the agenda widget looks. */
    const val AGENDA_DAYS = 30L

    /** Enough rows to fill a tall widget; more would only slow it down. */
    const val AGENDA_LIMIT = 60

    /**
     * The agenda from [today] on: each day with events gets a heading, then its events (all-day
     * ones first). Events that have already ended today are left out. A multi-day event shows on
     * each day it covers, so it's there on the day you look.
     */
    fun agenda(
        instances: List<Instance>,
        now: Long,
        zone: ZoneId,
        limit: Int = AGENDA_LIMIT,
    ): List<AgendaRow> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val last = today.plusDays(AGENDA_DAYS - 1)
        val byDay = sortedMapOf<LocalDate, MutableList<Instance>>()
        instances
            .filter { it.allDay || it.end > now }
            .forEach { i ->
                DayLayout
                    .days(i, zone)
                    .filter { !it.isBefore(today) && !it.isAfter(last) }
                    .forEach { day -> byDay.getOrPut(day) { mutableListOf() } += i }
            }
        val rows = mutableListOf<AgendaRow>()
        for ((day, list) in byDay) {
            if (rows.size >= limit) break
            rows += AgendaRow.Day(day)
            list
                .sortedWith(compareBy<Instance>({ !it.allDay }, { it.begin }, { it.title }))
                .forEach { if (rows.size < limit) rows += AgendaRow.Event(it, day) }
        }
        return rows
    }

    /** The next event that hasn't ended, for the small date widget. */
    fun next(
        instances: List<Instance>,
        now: Long,
    ): Instance? = instances.filter { !it.allDay && it.end > now }.minByOrNull { it.begin }

    /**
     * When a widget's content next changes by itself: the next midnight (a new day), or sooner if
     * an event ends today (it drops off the agenda).
     */
    fun nextRedraw(
        instances: List<Instance>,
        now: Long,
        zone: ZoneId,
    ): Long {
        val midnight =
            Instant
                .ofEpochMilli(now)
                .atZone(zone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val nextEnd = instances.filter { !it.allDay && it.end > now }.minOfOrNull { it.end }
        return minOf(midnight, nextEnd ?: Long.MAX_VALUE)
    }

    /** "Today", "Tomorrow", or null for any other day (the caller formats a date). */
    fun relativeDay(
        date: LocalDate,
        today: LocalDate,
    ): RelativeDay? =
        when (date) {
            today -> RelativeDay.Today
            today.plusDays(1) -> RelativeDay.Tomorrow
            else -> null
        }

    /** The UTC midnight an all-day event on [date] is stored at, for opening that day. */
    fun utcMidnight(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

enum class RelativeDay { Today, Tomorrow }
