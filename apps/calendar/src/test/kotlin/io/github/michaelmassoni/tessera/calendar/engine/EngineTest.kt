package io.github.michaelmassoni.tessera.calendar.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class EngineTest {
    private val sydney = ZoneId.of("Australia/Sydney")

    @Test fun `month grid has whole weeks from the locale's first day`() {
        val weeks = MonthGrid.weeks(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 26), weeks.first().first())
        assertEquals(LocalDate.of(2026, 12, 6), weeks.last().last())
        assertEquals(6, weeks.size)
        val sunday = MonthGrid.weeks(YearMonth.of(2026, 2), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 2, 1), sunday.first().first())
        assertEquals(4, sunday.size)
    }

    private fun at(
        y: Int,
        m: Int,
        d: Int,
        h: Int,
        min: Int = 0,
        zone: ZoneId = sydney,
    ) = LocalDateTime
        .of(y, m, d, h, min)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private fun utcDay(
        y: Int,
        m: Int,
        d: Int,
    ) = LocalDate
        .of(y, m, d)
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

    @Test fun `timed, overnight, midnight-ending and all-day events land on the right days`() {
        val timed = Instance(1, at(2026, 11, 19, 9), at(2026, 11, 19, 10), "Standup", false, 0, 1)
        val overnight = Instance(2, at(2026, 11, 19, 22), at(2026, 11, 20, 2), "Flight", false, 0, 1)
        val toMidnight = Instance(3, at(2026, 11, 19, 20), at(2026, 11, 20, 0), "Dinner", false, 0, 1)
        // All-day 19–21 Nov is stored as UTC midnights 19 Nov to 22 Nov; it must not shift in Sydney (UTC+11).
        val allDay = Instance(4, utcDay(2026, 11, 19), utcDay(2026, 11, 22), "Trip", true, 0, 1)
        assertEquals(listOf(LocalDate.of(2026, 11, 19)), DayLayout.days(timed, sydney))
        assertEquals(listOf(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 20)), DayLayout.days(overnight, sydney))
        assertEquals(listOf(LocalDate.of(2026, 11, 19)), DayLayout.days(toMidnight, sydney))
        assertEquals((19..21).map { LocalDate.of(2026, 11, it) }, DayLayout.days(allDay, sydney))
        val byDay = DayLayout.byDay(listOf(timed, overnight, allDay), sydney)
        assertEquals(listOf("Trip", "Standup", "Flight"), byDay.getValue(LocalDate.of(2026, 11, 19)).map { it.title })
    }

    @Test fun `repeat choices round-trip and rules are described`() {
        assertEquals(Repeat.Weekly, Repeat.of("FREQ=WEEKLY"))
        assertEquals(Repeat.None, Repeat.of(null))
        assertNull(Repeat.of("FREQ=WEEKLY;BYDAY=MO,WE"))
        val words =
            RecurrenceWords("Daily", "Weekly", "Monthly", "Yearly", {
                "Every $it days"
            }, { "Every $it weeks" }, { "Every $it months" }, { "Every $it years" }, "Repeats")
        assertEquals("Weekly", describe("FREQ=WEEKLY;WKST=MO", words))
        assertEquals("Every 2 days", describe("RRULE:FREQ=DAILY;INTERVAL=2", words))
        assertEquals("Repeats", describe("FREQ=MONTHLY;BYDAY=2TU", words))
    }

    @Test fun `ics events with zones, all-day dates, durations, folding and alarms`() {
        val ics =
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Team lunch\, Friday
            DTSTART;TZID=Europe/London:20261120T123000
            DTEND;TZID=Europe/London:20261120T133000
            LOCATION:The Crown
            DESCRIPTION:Line one\nLine two that is quite long and has been folded by the
              exporting app
            RRULE:FREQ=WEEKLY
            BEGIN:VALARM
            TRIGGER:-PT15M
            ACTION:DISPLAY
            END:VALARM
            BEGIN:VALARM
            TRIGGER;RELATED=START:-P1D
            END:VALARM
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Holiday
            DTSTART;VALUE=DATE:20261224
            DTEND;VALUE=DATE:20261227
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Call
            DTSTART:20261121T080000Z
            DURATION:PT45M
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:No start
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        val (lunch, holiday, call) = Ics.parse(ics, sydney)
        assertEquals("Team lunch, Friday", lunch.title)
        assertEquals(at(2026, 11, 20, 12, 30, ZoneId.of("Europe/London")), lunch.start)
        assertEquals(3_600_000L, lunch.end - lunch.start)
        assertEquals("Europe/London", lunch.timeZone)
        assertEquals("Line one\nLine two that is quite long and has been folded by the exporting app", lunch.description)
        assertEquals("FREQ=WEEKLY", lunch.rrule)
        assertEquals(listOf(15, 1440), lunch.reminders)
        assertEquals(true, holiday.allDay)
        assertEquals(utcDay(2026, 12, 24), holiday.start)
        assertEquals(utcDay(2026, 12, 27), holiday.end)
        assertEquals("UTC", call.timeZone)
        assertEquals(45 * 60_000L, call.end - call.start)
        assertEquals(3, Ics.parse(ics, sydney).size)
    }
}
