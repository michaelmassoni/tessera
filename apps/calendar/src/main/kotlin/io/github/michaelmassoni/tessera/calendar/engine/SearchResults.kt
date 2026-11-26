package io.github.michaelmassoni.tessera.calendar.engine

import java.time.LocalDate
import java.time.ZoneId

/**
 * Search results, as the person reads them: upcoming events (soonest first), then past ones (most
 * recent first). A repeating event appears once: at its next occurrence, or its last one if it's
 * over, so a weekly meeting doesn't fill the list. Each is listed on the day it starts. Pure, so
 * it's unit tested.
 */
object SearchResults {
    /** How far back and ahead search looks, in days. */
    const val PAST_DAYS = 2L * 365
    const val AHEAD_DAYS = 5L * 365

    data class Arranged(
        val upcoming: List<Pair<LocalDate, List<Instance>>>,
        val past: List<Pair<LocalDate, List<Instance>>>,
    )

    fun arrange(
        instances: List<Instance>,
        now: Long,
        zone: ZoneId,
    ): Arranged {
        val upcoming = mutableListOf<Instance>()
        val past = mutableListOf<Instance>()
        for ((_, occurrences) in instances.groupBy { it.eventId }) {
            val next = occurrences.filter { it.end > now }.minByOrNull { it.begin }
            if (next != null) upcoming += next else past += occurrences.maxBy { it.begin }
        }

        fun startDay(i: Instance) = DayLayout.days(i, zone).firstOrNull() ?: LocalDate.ofEpochDay(0)
        val byDayAscending =
            upcoming
                .sortedWith(compareBy<Instance> { it.begin }.thenBy { it.title })
                .groupBy(::startDay)
                .toList()
        val byDayDescending =
            past
                .sortedWith(compareByDescending<Instance> { it.begin }.thenBy { it.title })
                .groupBy(::startDay)
                .toList()
        return Arranged(byDayAscending, byDayDescending)
    }
}
