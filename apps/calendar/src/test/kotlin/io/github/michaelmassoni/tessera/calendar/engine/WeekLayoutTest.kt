package io.github.michaelmassoni.tessera.calendar.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class WeekLayoutTest {
    private val zone = ZoneId.of("Australia/Melbourne")

    /** Mon 23 – Sun 29 November 2026. */
    private val week = (23..29).map { LocalDate.of(2026, 11, it) }

    private fun timed(
        id: Long,
        day: Int,
        hour: Int,
    ) = Instance(
        id,
        LocalDateTime
            .of(2026, 11, day, hour, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli(),
        LocalDateTime
            .of(2026, 11, day, hour + 1, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli(),
        "Timed $id",
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
        "All day $id",
        true,
        0,
        1,
    )

    private fun plan(
        vararg events: Instance,
        lanes: Int = 4,
    ) = WeekLayout.plan(week, DayLayout.byDay(events.toList(), zone), lanes)

    private fun WeekPlan.bar(id: Long) = bars.single { it.instance.eventId == id }

    @Test
    fun `a multi-day event is one bar across its days`() {
        val p = plan(allDay(1, LocalDate.of(2026, 11, 25), 3))
        assertEquals(WeekBar(p.bars[0].instance, start = 2, span = 3, lane = 0), p.bar(1))
    }

    @Test
    fun `a bar is cut at the week's edges`() {
        val p = plan(allDay(1, LocalDate.of(2026, 11, 20), 5), allDay(2, LocalDate.of(2026, 11, 28), 4))
        assertEquals(0 to 2, p.bar(1).start to p.bar(1).span)
        assertEquals(5 to 2, p.bar(2).start to p.bar(2).span)
    }

    @Test
    fun `longer events take the top lanes and short ones fill in under them`() {
        val p = plan(timed(1, 25, 9), allDay(2, LocalDate.of(2026, 11, 24), 3), timed(3, 23, 9))
        assertEquals(0, p.bar(2).lane)
        assertEquals(1, p.bar(1).lane)
        // Monday is free in lane 0, so its event goes there.
        assertEquals(0, p.bar(3).lane)
    }

    @Test
    fun `within a day, all-day events come before timed ones, then by start`() {
        val p = plan(timed(1, 23, 15), timed(2, 23, 9), allDay(3, LocalDate.of(2026, 11, 23), 1))
        assertEquals(listOf(3L, 2L, 1L), p.bars.sortedBy { it.lane }.map { it.instance.eventId })
    }

    @Test
    fun `a day with too many events shows the first lanes and counts the rest`() {
        val p = plan(timed(1, 23, 8), timed(2, 23, 9), timed(3, 23, 10), timed(4, 23, 11), lanes = 3)
        assertEquals(listOf(1L, 2L), p.bars.map { it.instance.eventId })
        assertEquals(listOf(2, 0, 0, 0, 0, 0, 0), p.more)
    }

    @Test
    fun `exactly as many events as lanes all show`() {
        val p = plan(timed(1, 23, 8), timed(2, 23, 9), timed(3, 23, 10), lanes = 3)
        assertEquals(3, p.bars.size)
        assertEquals(List(7) { 0 }, p.more)
    }

    @Test
    fun `a bar in the last lane that crosses a full day is counted on every day it covers`() {
        // Three Mon–Tue events fill lanes 0–2; Monday's timed event lands in lane 3.
        val p =
            plan(
                allDay(1, LocalDate.of(2026, 11, 23), 2),
                allDay(2, LocalDate.of(2026, 11, 23), 2),
                allDay(3, LocalDate.of(2026, 11, 23), 2),
                timed(4, 23, 9),
                lanes = 3,
            )
        // Lane 2 holds event 3 (Mon–Tue); event 4 is in lane 3, so Monday overflows and event 3 hides.
        assertEquals(listOf(1L, 2L), p.bars.map { it.instance.eventId })
        assertEquals(listOf(2, 1, 0, 0, 0, 0, 0), p.more)
    }

    @Test
    fun `no room for any lane counts everything`() {
        val p = plan(timed(1, 23, 8), allDay(2, LocalDate.of(2026, 11, 23), 2), lanes = 0)
        assertEquals(emptyList<WeekBar>(), p.bars)
        assertEquals(listOf(2, 1, 0, 0, 0, 0, 0), p.more)
    }
}
