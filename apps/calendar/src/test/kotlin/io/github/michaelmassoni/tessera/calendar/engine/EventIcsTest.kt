package io.github.michaelmassoni.tessera.calendar.engine

import io.github.michaelmassoni.tessera.core.dav.ICal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class EventIcsTest {
    private val melbourne = ZoneId.of("Australia/Melbourne")
    private val ics = EventIcs(melbourne) { if (it == "AUS Eastern Standard Time") "Australia/Sydney" else null }
    private val now = Instant.parse("2026-11-23T00:00:00Z")

    private fun ms(
        text: String,
        zone: ZoneId = melbourne,
    ) = LocalDateTime
        .parse(text)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    private val meeting =
        """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Other//App//EN
        BEGIN:VTIMEZONE
        TZID:Australia/Melbourne
        BEGIN:STANDARD
        DTSTART:19700405T030000
        TZOFFSETFROM:+1100
        TZOFFSETTO:+1000
        END:STANDARD
        END:VTIMEZONE
        BEGIN:VEVENT
        UID:abc-123
        DTSTAMP:20261101T000000Z
        DTSTART;TZID=Australia/Melbourne:20261124T090000
        DTEND;TZID=Australia/Melbourne:20261124T100000
        SUMMARY:Team meeting\, weekly
        LOCATION:Room 4
        RRULE:FREQ=WEEKLY;BYDAY=TU
        EXDATE;TZID=Australia/Melbourne:20261201T090000
        CATEGORIES:Work
        X-OTHER-APP:keep me
        ATTENDEE;CN=Sam;PARTSTAT=ACCEPTED;ROLE=REQ-PARTICIPANT:mailto:sam@example.com
        ORGANIZER:mailto:boss@example.com
        BEGIN:VALARM
        ACTION:DISPLAY
        TRIGGER:-PT15M
        X-ALARM-EXTRA:yes
        END:VALARM
        END:VEVENT
        BEGIN:VEVENT
        UID:abc-123
        RECURRENCE-ID;TZID=Australia/Melbourne:20261208T090000
        DTSTART;TZID=Australia/Melbourne:20261208T110000
        DTEND;TZID=Australia/Melbourne:20261208T120000
        SUMMARY:Team meeting (moved)
        END:VEVENT
        END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")

    @Test
    fun `reads a repeating event with zones, exclusions, alarms, attendees and a moved occurrence`() {
        val r = ics.read(meeting)!!
        assertEquals("abc-123", r.uid)
        val m = r.main
        assertEquals("Team meeting, weekly", m.title)
        assertEquals(ms("2026-11-24T09:00"), m.start)
        assertNull(m.end)
        assertEquals("P3600S", m.duration)
        assertEquals("Australia/Melbourne", m.timeZone)
        assertEquals("FREQ=WEEKLY;BYDAY=TU", m.rrule)
        assertEquals("20261130T220000Z", m.exdate)
        assertEquals(listOf(15), m.reminders)
        assertEquals("boss@example.com", m.organizer)
        assertEquals(AttendeeRow("sam@example.com", "Sam", type = 1, status = 1), m.attendees.single())
        val x = r.exceptions.single()
        assertEquals(ms("2026-12-08T09:00"), x.originalInstanceTime)
        assertEquals(ms("2026-12-08T11:00"), x.start)
        assertEquals(ms("2026-12-08T12:00"), x.end)
    }

    @Test
    fun `all-day, UTC, floating, Windows and unknown zones`() {
        fun one(lines: String) = ics.read("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:u\r\n$lines\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n")!!.main
        val allDay = one("DTSTART;VALUE=DATE:20261225\r\nDTEND;VALUE=DATE:20261227")
        assertTrue(allDay.allDay)
        assertEquals("UTC", allDay.timeZone)
        assertEquals(ms("2026-12-25T00:00", ZoneOffset.UTC), allDay.start)
        assertEquals(ms("2026-12-27T00:00", ZoneOffset.UTC), allDay.end)
        // An all-day event without an end lasts the day.
        assertEquals(ms("2026-12-26T00:00", ZoneOffset.UTC), one("DTSTART;VALUE=DATE:20261225").end)
        assertEquals(ms("2026-11-24T09:00", ZoneOffset.UTC), one("DTSTART:20261124T090000Z\r\nDURATION:PT30M").start)
        assertEquals(ms("2026-11-24T09:00"), one("DTSTART:20261124T090000").start)
        val windows = one("DTSTART;TZID=AUS Eastern Standard Time:20261124T090000")
        assertEquals("Australia/Sydney", windows.timeZone)
        assertEquals("Europe/Berlin", one("DTSTART;TZID=/mozilla.org/20050126_1/Europe/Berlin:20261124T090000").timeZone)
    }

    @Test
    fun `a VTIMEZONE with an unknown name gives its standard offset`() {
        val text =
            "BEGIN:VCALENDAR\r\nBEGIN:VTIMEZONE\r\nTZID:My Zone\r\nBEGIN:STANDARD\r\nDTSTART:19700101T000000\r\nTZOFFSETFROM:+0530\r\n" +
                "TZOFFSETTO:+0530\r\nEND:STANDARD\r\nEND:VTIMEZONE\r\nBEGIN:VEVENT\r\nUID:u\r\nDTSTART;TZID=My Zone:20261124T090000\r\n" +
                "END:VEVENT\r\nEND:VCALENDAR\r\n"
        val e = ics.read(text)!!.main
        assertEquals("GMT+05:30", e.timeZone)
        assertEquals(ms("2026-11-24T09:00", ZoneOffset.ofHoursMinutes(5, 30)), e.start)
    }

    @Test
    fun `writing back keeps what Tessera doesn't map and what didn't change`() {
        val r = ics.read(meeting)!!
        val out = ics.write(r.copy(main = r.main.copy(title = "Team meeting")), meeting, now)
        assertTrue(out.contains("SUMMARY:Team meeting\r\n"))
        assertTrue(out.contains("X-OTHER-APP:keep me"))
        assertTrue(out.contains("CATEGORIES:Work"))
        assertTrue(out.contains("ATTENDEE;CN=Sam"))
        // Reminders didn't change, so the server's VALARM stays as it was.
        assertTrue(out.contains("X-ALARM-EXTRA:yes"))
        // Times didn't change: the original DTSTART is untouched and SEQUENCE isn't bumped.
        assertTrue(out.contains("DTSTART;TZID=Australia/Melbourne:20261124T090000"))
        assertFalse(out.contains("SEQUENCE"))
        assertTrue(out.contains("PRODID:-//Other//App//EN"))
        // The moved occurrence survives.
        assertEquals(1, ics.read(out)!!.exceptions.size)
        assertEquals(r.main.copy(title = "Team meeting"), ics.read(out)!!.main)
    }

    @Test
    fun `moving an event rewrites its times, bumps SEQUENCE and replaces changed reminders`() {
        val r = ics.read(meeting)!!
        val moved = r.main.copy(start = r.main.start + 3_600_000, reminders = listOf(0, 60))
        val out = ics.write(r.copy(main = moved), meeting, now)
        assertTrue(out.contains("DTSTART;TZID=Australia/Melbourne:20261124T100000"))
        assertTrue(out.contains("SEQUENCE:1"))
        assertFalse(out.contains("X-ALARM-EXTRA"))
        assertTrue(out.contains("TRIGGER:PT0S"))
        assertTrue(out.contains("TRIGGER:-PT60M"))
        assertEquals(listOf(0, 60), ics.read(out)!!.main.reminders)
    }

    @Test
    fun `a cancelled occurrence becomes an EXDATE and a removed one disappears`() {
        val r = ics.read(meeting)!!
        val cancel = EventRow(status = EventRow.CANCELED, originalInstanceTime = ms("2026-12-15T09:00"))
        val out = ics.write(r.copy(exceptions = listOf(cancel)), meeting, now)
        val back = ics.read(out)!!
        assertTrue(back.exceptions.isEmpty())
        assertEquals("20261130T220000Z,20261214T220000Z", back.main.exdate)
    }

    @Test
    fun `a new event gets a calendar, UID, VTIMEZONE and VALARM`() {
        val row =
            EventRow(
                title = "Dentist",
                start = ms("2026-11-27T14:30"),
                end = ms("2026-11-27T15:00"),
                timeZone = "Australia/Melbourne",
                reminders = listOf(30),
            )
        val out = ics.write(EventResource("new-uid", row), null, now)
        assertTrue(out.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"))
        assertTrue(out.contains("UID:new-uid"))
        assertTrue(out.contains("BEGIN:VTIMEZONE\r\nTZID:Australia/Melbourne"))
        assertTrue(out.contains("DTSTART;TZID=Australia/Melbourne:20261127T143000"))
        val back = ics.read(out)!!.main
        assertEquals(row.copy(status = null), back)
    }

    @Test
    fun `generated VTIMEZONE follows the zone's daylight saving`() {
        val tz = ics.vtimezone(melbourne)
        val text = ICal.write(tz)
        assertTrue(text.contains("BEGIN:DAYLIGHT"))
        assertTrue(text.contains("TZOFFSETTO:+1100"))
        assertTrue(text.contains("BYMONTH=10;BYDAY=SU;BYMONTHDAY=1,2,3,4,5,6,7"))
        assertTrue(text.contains("BYMONTH=4;BYDAY=SU;BYMONTHDAY=1,2,3,4,5,6,7"))
        val fixed = ICal.write(ics.vtimezone(ZoneId.of("Asia/Kolkata")))
        assertTrue(fixed.contains("TZOFFSETTO:+0530"))
        assertFalse(fixed.contains("RRULE"))
    }

    @Test
    fun durations() {
        assertEquals(-15L, EventIcs.duration("-PT15M").toMinutes())
        assertEquals(-1560L, EventIcs.duration("-P1DT2H").toMinutes())
        assertEquals(7L, EventIcs.duration("P1W").toDays())
        assertEquals("P2D", EventIcs.duration(2 * 86_400_000L, allDay = true))
        assertEquals("P1800S", EventIcs.duration(1_800_000L, allDay = false))
        assertNotNull(ics.zoneOf("Europe/Berlin"))
    }
}
