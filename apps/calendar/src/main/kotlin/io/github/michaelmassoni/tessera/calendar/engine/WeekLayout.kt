package io.github.michaelmassoni.tessera.calendar.engine

import java.time.LocalDate

/** An event drawn as a bar across [span] days of a week row, from column [start], in [lane]. */
data class WeekBar(
    val instance: Instance,
    val start: Int,
    val span: Int,
    val lane: Int,
)

/** A week row of the month grid: the bars that fit, and per day how many events didn't fit. */
data class WeekPlan(
    val bars: List<WeekBar>,
    val more: List<Int>,
)

/**
 * Lays a week's events out in lanes, as month views with event pills do: an event covering several
 * days is one bar across them (cut at the week's edges), shorter events fill the lanes under it.
 */
object WeekLayout {
    /**
     * [byDay] gives each day's events (as `DayLayout.byDay`). At most [lanes] lanes are shown; on a
     * day with more events than that, the last lane says "+N" instead, so a bar in the last lane
     * that crosses such a day is left out too (and counted in each day it covers).
     */
    fun plan(
        week: List<LocalDate>,
        byDay: Map<LocalDate, List<Instance>>,
        lanes: Int,
    ): WeekPlan {
        // Each event once, with the first and last column it appears in.
        val columns = linkedMapOf<Pair<Long, Long>, Triple<Instance, Int, Int>>()
        week.forEachIndexed { col, day ->
            byDay[day].orEmpty().forEach { i ->
                val key = i.eventId to i.begin
                val seen = columns[key]
                columns[key] = if (seen == null) Triple(i, col, col) else seen.copy(third = col)
            }
        }
        val events =
            columns.values.sortedWith(
                compareByDescending<Triple<Instance, Int, Int>> { it.third - it.second }
                    .thenBy { it.second }
                    .thenBy { !it.first.allDay }
                    .thenBy { it.first.begin }
                    .thenBy { it.first.title },
            )

        // Greedy: each event goes in the first lane that's free across its days.
        val taken = mutableListOf<BooleanArray>()
        val placed =
            events.map { (instance, first, last) ->
                var lane = taken.indexOfFirst { row -> (first..last).none { row[it] } }
                if (lane < 0) {
                    taken += BooleanArray(week.size)
                    lane = taken.lastIndex
                }
                (first..last).forEach { taken[lane][it] = true }
                WeekBar(instance, first, last - first + 1, lane)
            }

        if (lanes <= 0) return WeekPlan(emptyList(), week.indices.map { col -> placed.count { col in it.columns } })
        val overflowing = week.indices.filter { col -> placed.any { it.lane >= lanes && col in it.columns } }.toSet()
        val shown =
            placed.filter {
                it.lane < lanes - 1 || (it.lane == lanes - 1 && it.columns.none(overflowing::contains))
            }
        val more = week.indices.map { col -> placed.count { col in it.columns } - shown.count { col in it.columns } }
        return WeekPlan(shown, more)
    }

    private val WeekBar.columns get() = start until start + span
}
