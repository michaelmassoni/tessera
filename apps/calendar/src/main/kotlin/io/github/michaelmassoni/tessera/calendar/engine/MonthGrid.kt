package io.github.michaelmassoni.tessera.calendar.engine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

object MonthGrid {
    /**
     * The weeks shown for [month]: whole weeks starting on [firstDay], from the week containing
     * the 1st to the week containing the last day (4 to 6 rows).
     */
    fun weeks(
        month: YearMonth,
        firstDay: DayOfWeek,
    ): List<List<LocalDate>> {
        val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay))
        val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(firstDay.minus(1)))
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .chunked(7)
            .toList()
    }
}

object DayLayout {
    /**
     * The days an instance covers in [zone]. All-day events are stored at UTC midnights and
     * cover [begin, end) in UTC dates; timed events cover every local day they touch (an event
     * ending exactly at midnight doesn't spill into the next day).
     */
    fun days(
        instance: Instance,
        zone: ZoneId,
    ): List<LocalDate> {
        val (first, last) =
            if (instance.allDay) {
                val s = Instant.ofEpochMilli(instance.begin).atZone(ZoneOffset.UTC).toLocalDate()
                val e =
                    Instant
                        .ofEpochMilli(instance.end)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .minusDays(1)
                s to maxOf(s, e)
            } else {
                val s = Instant.ofEpochMilli(instance.begin).atZone(zone).toLocalDate()
                val e = Instant.ofEpochMilli(maxOf(instance.begin, instance.end - 1)).atZone(zone).toLocalDate()
                s to e
            }
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    /**
     * Instances per day, each day sorted all-day first, then by start time, then title.
     */
    fun byDay(
        instances: List<Instance>,
        zone: ZoneId,
    ): Map<LocalDate, List<Instance>> {
        val map = mutableMapOf<LocalDate, MutableList<Instance>>()
        for (i in instances) days(i, zone).forEach { map.getOrPut(it) { mutableListOf() } += i }
        return map.mapValues { (_, list) -> list.sortedWith(compareBy<Instance> { !it.allDay }.thenBy { it.begin }.thenBy { it.title }) }
    }

    /** Epoch ms bounds [start, end) of the local [days] in [zone], for querying instances. */
    fun range(
        first: LocalDate,
        last: LocalDate,
        zone: ZoneId,
    ): Pair<Long, Long> =
        // Widen by a day each side so all-day events stored in UTC are never cut off.
        first
            .minusDays(1)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli() to
            last
                .plusDays(2)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
}
