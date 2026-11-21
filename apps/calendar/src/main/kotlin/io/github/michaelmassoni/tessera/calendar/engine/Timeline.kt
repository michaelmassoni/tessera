package io.github.michaelmassoni.tessera.calendar.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A timed event placed in a day column: minutes from midnight, and its share of the width. */
data class TimelineBlock(
    val instance: Instance,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columns: Int,
)

/** One day of the week and day views: events across the top, timed events in the grid. */
data class TimelineDay(
    val allDay: List<Instance>,
    val blocks: List<TimelineBlock>,
)

/**
 * Lays out a day for the week and day views. All-day events, and timed events that cover the
 * whole day, go in the strip at the top. Other timed events become blocks between their start and
 * end (clipped to the day, at least [MIN_MINUTES] tall so short ones stay tappable). Overlapping
 * blocks share the width side by side, as many columns as the busiest moment of their cluster
 * needs, each block in the first column free when it starts.
 */
object Timeline {
    const val MIN_MINUTES = 20
    private const val DAY_MINUTES = 24 * 60

    fun day(
        date: LocalDate,
        instances: List<Instance>,
        zone: ZoneId,
    ): TimelineDay {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd =
            date
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        val (allDay, timed) = instances.partition { it.allDay || (it.begin <= dayStart && it.end >= dayEnd) }
        val spans =
            timed
                .map { i ->
                    val start = minuteOf(maxOf(i.begin, dayStart), zone, date)
                    val end = if (i.end >= dayEnd) DAY_MINUTES else minuteOf(i.end, zone, date)
                    Triple(i, start, maxOf(end, start + MIN_MINUTES).coerceAtMost(DAY_MINUTES))
                }.sortedWith(compareBy<Triple<Instance, Int, Int>> { it.second }.thenByDescending { it.third }.thenBy { it.first.title })
        val blocks = mutableListOf<TimelineBlock>()
        var cluster = mutableListOf<Pair<Triple<Instance, Int, Int>, Int>>()
        var clusterEnd = -1

        fun close() {
            val columns = (cluster.maxOfOrNull { it.second } ?: -1) + 1
            cluster.forEach { (span, column) -> blocks += TimelineBlock(span.first, span.second, span.third, column, columns) }
            cluster = mutableListOf()
        }
        for (span in spans) {
            if (span.second >= clusterEnd) close()
            // The first column whose last block has ended by the time this one starts.
            val column =
                generateSequence(0) { it + 1 }.first { c ->
                    cluster.none { (other, oc) -> oc == c && other.third > span.second }
                }
            cluster += span to column
            clusterEnd = maxOf(clusterEnd, span.third)
        }
        close()
        return TimelineDay(allDay.sortedWith(compareBy<Instance> { it.begin }.thenBy { it.title }), blocks)
    }

    private fun minuteOf(
        millis: Long,
        zone: ZoneId,
        date: LocalDate,
    ): Int {
        val t = Instant.ofEpochMilli(millis).atZone(zone)
        return if (t.toLocalDate().isAfter(date)) DAY_MINUTES else t.hour * 60 + t.minute
    }

    /** The days of the week containing [day], starting on [firstDay]. */
    fun week(
        day: LocalDate,
        firstDay: java.time.DayOfWeek,
    ): List<LocalDate> {
        val start = day.minusDays(((day.dayOfWeek.value - firstDay.value + 7) % 7).toLong())
        return (0L until 7L).map(start::plusDays)
    }
}
