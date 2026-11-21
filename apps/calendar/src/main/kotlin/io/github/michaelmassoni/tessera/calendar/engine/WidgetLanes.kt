package io.github.michaelmassoni.tessera.calendar.engine

import java.time.LocalDate

/**
 * How the month widget draws a week's lanes, within Glance's limits: 500 views per widget (a
 * busy month with many lanes goes over it, and the widget shows "Can't show content"), and 10
 * children per row (the rest are silently dropped). Each lane is a row of at most seven
 * segments: a gap (one view), a pill (a padded box and its text: two views) or "+N" (one).
 */
object WidgetLanes {
    sealed interface Segment {
        /** Empty space, [cells] day cells wide. */
        data class Gap(
            val cells: Int,
        ) : Segment

        /** An event pill across its bar's days (its margins are inside it). */
        data class Pill(
            val bar: WeekBar,
        ) : Segment

        /** "+N" filling one day cell. */
        data class More(
            val count: Int,
        ) : Segment
    }

    /** The segments of [lane] (the last lane also carries "+N" for days with more events). */
    fun segments(
        plan: WeekPlan,
        lane: Int,
        last: Boolean,
    ): List<Segment> {
        val out = mutableListOf<Segment>()
        var cells = 0

        fun flush() {
            if (cells > 0) out += Segment.Gap(cells)
            cells = 0
        }
        var col = 0
        while (col < plan.more.size) {
            val bar = plan.bars.firstOrNull { it.lane == lane && it.start == col }
            when {
                bar != null -> {
                    flush()
                    out += Segment.Pill(bar)
                    col += bar.span
                }

                last && plan.more[col] > 0 -> {
                    flush()
                    out += Segment.More(plan.more[col])
                    col++
                }

                else -> {
                    cells++
                    col++
                }
            }
        }
        return out
    }

    /** Views the lanes of these weeks take: per week a column, per lane a row and its segments. */
    fun views(
        plans: List<WeekPlan>,
        lanes: Int,
    ): Int =
        if (lanes <= 0) {
            0
        } else {
            plans.sumOf { plan ->
                1 + (0 until lanes).sumOf { lane -> 1 + segments(plan, lane, lane == lanes - 1).sumOf { if (it is Segment.Pill) 2 else 1 } }
            }
        }

    /** The most lanes, up to [maxLanes], whose views fit in [budget], with each week's plan. */
    fun fit(
        weeks: List<List<LocalDate>>,
        byDay: Map<LocalDate, List<Instance>>,
        maxLanes: Int,
        budget: Int,
    ): Pair<Int, List<WeekPlan>> {
        for (lanes in maxLanes.coerceAtLeast(0) downTo 1) {
            val plans = weeks.map { WeekLayout.plan(it, byDay, lanes) }
            if (views(plans, lanes) <= budget) return lanes to plans
        }
        return 0 to weeks.map { WeekLayout.plan(it, byDay, 0) }
    }
}
