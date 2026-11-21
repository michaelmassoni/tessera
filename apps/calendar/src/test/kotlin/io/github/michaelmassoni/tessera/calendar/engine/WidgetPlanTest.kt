package io.github.michaelmassoni.tessera.calendar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class WidgetPlanTest {
    private val zone = ZoneId.of("Australia/Melbourne")

    private fun ms(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ) = LocalDateTime
        .of(2026, 11, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun timed(
        id: Long,
        day: Int,
        from: Int,
        to: Int,
        title: String = "Event $id",
    ) = Instance(id, ms(day, from), ms(day, to), title, false, 0, 1)

    private fun allDay(
        id: Long,
        day: Int,
        days: Int = 1,
    ) = Instance(
        id,
        LocalDate
            .of(2026, 11, day)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
        LocalDate
            .of(2026, 11, day + days)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
        "All day $id",
        true,
        0,
        1,
    )

    @Test fun `agenda groups by day, all-day first, and drops what has ended`() {
        val now = ms(20, 12)
        val rows =
            WidgetPlan.agenda(
                listOf(timed(1, 20, 9, 10), timed(2, 20, 13, 14), allDay(3, 20), timed(4, 22, 8, 9)),
                now,
                zone,
            )
        assertEquals(
            listOf("day 2026-11-20", "event 3", "event 2", "day 2026-11-22", "event 4"),
            rows.map { if (it is AgendaRow.Day) "day ${it.date}" else "event ${(it as AgendaRow.Event).instance.eventId}" },
        )
    }

    @Test fun `a multi-day event shows on each of its days, and the limit holds`() {
        val rows = WidgetPlan.agenda(listOf(allDay(1, 20, days = 3)), ms(20, 8), zone)
        assertEquals(6, rows.size)
        assertEquals(3, WidgetPlan.agenda(listOf(allDay(1, 20, days = 3)), ms(20, 8), zone, limit = 3).size)
    }

    @Test fun `the next redraw is midnight, or sooner when an event ends today`() {
        val midnight =
            LocalDate
                .of(2026, 11, 21)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        assertEquals(midnight, WidgetPlan.nextRedraw(emptyList(), ms(20, 12), zone))
        assertEquals(ms(20, 14), WidgetPlan.nextRedraw(listOf(timed(1, 20, 13, 14)), ms(20, 12), zone))
    }

    @Test fun `the next event skips all-day and finished ones`() {
        val next = WidgetPlan.next(listOf(allDay(1, 20), timed(2, 20, 9, 10), timed(3, 20, 15, 16)), ms(20, 12))
        assertEquals(3L, next?.eventId)
        assertNull(WidgetPlan.next(listOf(timed(2, 20, 9, 10)), ms(20, 12)))
    }

    @Test fun `today and tomorrow are named`() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals(RelativeDay.Today, WidgetPlan.relativeDay(today, today))
        assertEquals(RelativeDay.Tomorrow, WidgetPlan.relativeDay(today.plusDays(1), today))
        assertNull(WidgetPlan.relativeDay(today.plusDays(2), today))
    }
}
