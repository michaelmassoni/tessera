package io.github.michaelmassoni.tessera.calendar.engine

import io.github.michaelmassoni.tessera.calendar.engine.WidgetLanes.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class WidgetLanesTest {
    private val zone = ZoneId.of("Australia/Melbourne")
    private val week = (23..29).map { LocalDate.of(2026, 11, it) }

    private fun timed(
        id: Long,
        day: LocalDate,
        hour: Int,
    ) = Instance(
        id,
        day
            .atTime(hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli(),
        LocalDateTime
            .of(day, java.time.LocalTime.of(hour, 30))
            .atZone(zone)
            .toInstant()
            .toEpochMilli(),
        "E$id",
        false,
        0,
        1,
    )

    private fun allDay(
        id: Long,
        from: LocalDate,
        days: Int,
    ) = Instance(
        id,
        from.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        from
            .plusDays(days.toLong())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
        "A$id",
        true,
        0,
        1,
    )

    @Test
    fun `empty days merge into one gap`() {
        // A Wed–Thu event: two empty days, then the bar.
        val plan = WeekLayout.plan(week, DayLayout.byDay(listOf(allDay(1, week[2], 2)), zone), 2)
        val segments = WidgetLanes.segments(plan, 0, last = false)
        assertEquals(listOf(Segment.Gap(cells = 2), Segment.Pill(plan.bars.single())), segments)
    }

    @Test
    fun `adjacent pills need nothing between them`() {
        val plan = WeekLayout.plan(week, DayLayout.byDay(listOf(timed(1, week[0], 9), timed(2, week[1], 9)), zone), 1)
        val segments = WidgetLanes.segments(plan, 0, last = true)
        assertEquals(2, segments.size)
        assertTrue(segments.all { it is Segment.Pill })
    }

    @Test
    fun `a lane never has more than seven segments`() {
        // Glance drops a row's children past ten; a pill every day must still all show.
        val events = week.mapIndexed { i, day -> timed(i.toLong(), day, 9) }
        val plan = WeekLayout.plan(week, DayLayout.byDay(events, zone), 1)
        val segments = WidgetLanes.segments(plan, 0, last = true)
        assertEquals(7, segments.size)
        assertTrue(segments.all { it is Segment.Pill })
    }

    @Test
    fun `the last lane carries +N`() {
        val events = (1..4L).map { timed(it, week[0], 8 + it.toInt()) }
        val plan = WeekLayout.plan(week, DayLayout.byDay(events, zone), 2)
        val segments = WidgetLanes.segments(plan, 1, last = true)
        assertEquals(listOf(Segment.More(3)), segments)
    }

    @Test
    fun `a busy month fits the view budget by dropping lanes`() {
        // Four events every day of a six-week month: the case that broke the widget.
        val weeks = MonthGrid.weeks(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        val events = weeks.flatten().flatMapIndexed { d, day -> (0 until 4).map { timed(d * 10L + it, day, 9 + it * 3) } }
        val byDay = DayLayout.byDay(events, zone)
        // Six lanes would need far more than the budget.
        val six = weeks.map { WeekLayout.plan(it, byDay, 6) }
        assertTrue(WidgetLanes.views(six, 6) > 300)
        val (lanes, plans) = WidgetLanes.fit(weeks, byDay, maxLanes = 6, budget = 300)
        assertTrue(lanes in 1..5)
        assertTrue(WidgetLanes.views(plans, lanes) <= 300)
        // One more lane wouldn't have fitted.
        assertTrue(WidgetLanes.views(weeks.map { WeekLayout.plan(it, byDay, lanes + 1) }, lanes + 1) > 300)
    }

    @Test
    fun `no budget means no lanes`() {
        val byDay = DayLayout.byDay(listOf(timed(1, week[0], 9)), zone)
        assertEquals(0, WidgetLanes.fit(listOf(week), byDay, maxLanes = 3, budget = 1).first)
        assertEquals(0, WidgetLanes.views(listOf(WeekLayout.plan(week, byDay, 0)), 0))
    }
}
