package io.github.michaelmassoni.tessera.tasks.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class VTodoTest {
    private val zone = ZoneId.of("Australia/Melbourne")

    // As Tasks.org (with ical4j) writes one: a time zone, an alarm, a category and its own X- lines.
    private val tasksOrg =
        """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:+//IDN tasks.org//android-140100//EN
        BEGIN:VTODO
        DTSTAMP:20261120T010203Z
        UID:9081723
        CREATED:20261101T000000Z
        LAST-MODIFIED:20261120T010203Z
        SUMMARY:Take tablets\, with food
        DESCRIPTION:Two in the morning\nOne at night
        PRIORITY:1
        DUE;TZID=Australia/Melbourne:20261123T080000
        DTSTART;TZID=Australia/Melbourne:20261123T080000
        RRULE:FREQ=DAILY
        CATEGORIES:Health
        X-APPLE-SORT-ORDER:42
        X-TASKS-ORG-SOMETHING:keep me
        STATUS:NEEDS-ACTION
        BEGIN:VALARM
        TRIGGER;RELATED=END:PT0S
        ACTION:DISPLAY
        DESCRIPTION:Default Tasks.org description
        END:VALARM
        END:VTODO
        BEGIN:VTIMEZONE
        TZID:Australia/Melbourne
        END:VTIMEZONE
        END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")

    @Test
    fun `reads a Tasks org task`() {
        val t = VTodo.read(tasksOrg, "list", zone)!!
        assertEquals("9081723", t.id)
        assertEquals("Take tablets, with food", t.title)
        assertEquals("Two in the morning\nOne at night", t.notes)
        assertEquals(Due("2026-11-23", "08:00"), t.due)
        assertEquals(Repeat(Repeat.Frequency.Daily), t.repeat)
        assertTrue(t.starred)
        assertEquals(42L, t.order)
        assertNull(t.completedAt)
        assertFalse(t.dirty)
    }

    @Test
    fun `writing back keeps what Tessera doesn't edit`() {
        val t = VTodo.read(tasksOrg, "list", zone)!!.copy(title = "Take tablets")
        val out = VTodo.write(t, 1_795_400_000_000, zone)
        assertTrue(out.contains("SUMMARY:Take tablets\r\n"))
        // Unchanged due date: written exactly as before, with its time zone.
        assertTrue(out.contains("DUE;TZID=Australia/Melbourne:20261123T080000"))
        assertTrue(out.contains("CATEGORIES:Health"))
        assertTrue(out.contains("X-TASKS-ORG-SOMETHING:keep me"))
        assertTrue(out.contains("BEGIN:VALARM"))
        assertTrue(out.contains("BEGIN:VTIMEZONE"))
        assertEquals(
            t.copy(modifiedAt = 0, ical = null, dirty = false),
            VTodo.read(out, "list", zone)!!.copy(modifiedAt = 0, ical = null, createdAt = t.createdAt),
        )
    }

    @Test
    fun `moving the due date moves the start and writes floating time`() {
        val t = VTodo.read(tasksOrg, "list", zone)!!.copy(due = Due("2026-11-24", "08:00"))
        val out = VTodo.write(t, 0, zone)
        assertTrue(out.contains("DUE:20261124T080000\r\n"))
        assertTrue(out.contains("DTSTART:20261124T080000\r\n"))
    }

    @Test
    fun `completing, unstarring and a new task`() {
        val done = VTodo.read(tasksOrg, "list", zone)!!.copy(completedAt = 1_795_400_000_000, starred = false)
        val out = VTodo.write(done, 1_795_400_000_000, zone)
        assertTrue(out.contains("STATUS:COMPLETED"))
        assertTrue(out.contains("COMPLETED:"))
        assertFalse(out.contains("PRIORITY"))
        assertEquals(1_795_400_000_000, VTodo.read(out, "list", zone)!!.completedAt)

        val fresh = Task("abc", "list", "Buy milk", due = Due("2026-11-25"), parentId = "p1", order = 3)
        val text = VTodo.write(fresh, 0, zone)
        assertTrue(text.contains("DUE;VALUE=DATE:20261125"))
        assertTrue(text.contains("RELATED-TO;RELTYPE=PARENT:p1"))
        val back = VTodo.read(text, "list", zone)!!
        assertEquals("Buy milk", back.title)
        assertEquals("p1", back.parentId)
        assertEquals(Due("2026-11-25"), back.due)
    }

    @Test
    fun `utc times become local and long lines fold`() {
        val utc = "BEGIN:VCALENDAR\r\nBEGIN:VTODO\r\nUID:x\r\nSUMMARY:x\r\nDUE:20261122T210000Z\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"
        assertEquals(Due("2026-11-23", "08:00"), VTodo.read(utc, "l", zone)!!.due)
        val long = Task("y", "l", "A very long title ".repeat(10))
        val text = VTodo.write(long, 0, zone)
        assertTrue(text.split("\r\n").all { it.toByteArray().size <= 75 })
        assertEquals(long.title, VTodo.read(text, "l", zone)!!.title)
    }

    @Test
    fun `after-done repeats survive a round trip`() {
        val t = Task("z", "l", "Meds", due = Due("2026-11-23", "08:00"), repeat = Repeat(Repeat.Frequency.Hourly, 8, fromCompletion = true))
        assertEquals(t.repeat, VTodo.read(VTodo.write(t, 0, zone), "l", zone)!!.repeat)
    }
}
