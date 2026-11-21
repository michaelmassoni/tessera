package io.github.michaelmassoni.tessera.calendar.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class TimelineTest {
    private val zone = ZoneId.of("Australia/Sydney")
    private val day = LocalDate.of(2026, 11, 25)

    private fun at(
        h: Int,
        m: Int = 0,
        d: LocalDate = day,
    ) = d
        .atTime(h, m)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun event(
        title: String,
        begin: Long,
        end: Long,
        allDay: Boolean = false,
    ) = Instance(title.hashCode().toLong(), begin, end, title, allDay, 0, 1)

    @Test fun `overlapping events share the width, separate ones get it all`() {
        val layout =
            Timeline.day(
                day,
                listOf(
                    event("Standup", at(9), at(9, 30)),
                    event("Review", at(9, 15), at(10, 30)),
                    event("Coffee", at(9, 45), at(10)),
                    event("Lunch", at(12), at(13)),
                ),
                zone,
            )
        val byTitle = layout.blocks.associateBy { it.instance.title }
        assertEquals(0, byTitle["Standup"]!!.column)
        assertEquals(1, byTitle["Review"]!!.column)
        // Standup has ended by 9:45, so Coffee reuses its column.
        assertEquals(0, byTitle["Coffee"]!!.column)
        assertEquals(2, byTitle["Standup"]!!.columns)
        assertEquals(1, byTitle["Lunch"]!!.columns)
        assertEquals(12 * 60, byTitle["Lunch"]!!.startMinute)
    }

    @Test fun `all-day and whole-day events go to the strip, edges clip, short events stay tappable`() {
        val layout =
            Timeline.day(
                day,
                listOf(
                    event("Holiday", 0, 1, allDay = true),
                    event("Conference", at(8, d = day.minusDays(1)), at(18, d = day.plusDays(1))),
                    event("Flight", at(22), at(6, d = day.plusDays(1))),
                    event("Call", at(15), at(15, 5)),
                ),
                zone,
            )
        assertEquals(setOf("Holiday", "Conference"), layout.allDay.map { it.title }.toSet())
        val byTitle = layout.blocks.associateBy { it.instance.title }
        assertEquals(24 * 60, byTitle["Flight"]!!.endMinute)
        assertEquals(15 * 60 + Timeline.MIN_MINUTES, byTitle["Call"]!!.endMinute)
    }

    @Test fun `week starts on the given day`() {
        assertEquals(LocalDate.of(2026, 11, 23), Timeline.week(day, DayOfWeek.MONDAY).first())
        assertEquals(LocalDate.of(2026, 11, 22), Timeline.week(day, DayOfWeek.SUNDAY).first())
        assertEquals(7, Timeline.week(day, DayOfWeek.SATURDAY).size)
    }
}
