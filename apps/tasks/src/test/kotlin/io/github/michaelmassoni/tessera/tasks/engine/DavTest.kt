package io.github.michaelmassoni.tessera.tasks.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DavTest {
    @Test
    fun `discovery replies`() {
        val principal =
            """<?xml version='1.0' encoding='utf-8'?>
            <multistatus xmlns="DAV:"><response><href>/</href><propstat><prop><current-user-principal><href>/tester/</href></current-user-principal></prop>
            <status>HTTP/1.1 200 OK</status></propstat></response></multistatus>"""
        assertEquals(
            "/tester/",
            Dav
                .parse(principal)
                .responses
                .single()
                .principal,
        )

        // As Radicale lists a home: the home itself, a task list and a calendar.
        val home =
            """<?xml version='1.0' encoding='utf-8'?>
            <multistatus xmlns="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav" xmlns:ICAL="http://apple.com/ns/ical/">
            <response><href>/tester/</href><propstat><prop><resourcetype><collection/></resourcetype></prop><status>HTTP/1.1 200 OK</status></propstat></response>
            <response><href>/tester/tasks/</href><propstat><prop><resourcetype><C:calendar/><collection/></resourcetype><displayname>Tasks &amp; chores</displayname>
            <ICAL:calendar-color>#2e7d32ff</ICAL:calendar-color><C:supported-calendar-component-set><C:comp name="VTODO"/></C:supported-calendar-component-set>
            <sync-token>http://radicale.org/ns/sync/abc</sync-token></prop><status>HTTP/1.1 200 OK</status></propstat></response>
            <response><href>/tester/events/</href><propstat><prop><resourcetype><C:calendar/><collection/></resourcetype><displayname>Events</displayname>
            <C:supported-calendar-component-set><C:comp name="VEVENT"/></C:supported-calendar-component-set></prop><status>HTTP/1.1 200 OK</status></propstat>
            <propstat><prop><ICAL:calendar-color/></prop><status>HTTP/1.1 404 Not Found</status></propstat></response>
            </multistatus>"""
        val r = Dav.parse(home).responses
        assertFalse(r[0].isCalendar)
        assertEquals("Tasks & chores", r[1].displayName)
        assertEquals(setOf("VTODO"), r[1].components)
        assertTrue(r[1].isCalendar)
        assertEquals(0xFF2E7D32.toInt(), Dav.color(r[1].color))
        assertEquals(setOf("VEVENT"), r[2].components)
        assertEquals(null, r[2].color)
    }

    @Test
    fun `sync-collection with changes and a deletion`() {
        val xml =
            """<multistatus xmlns="DAV:"><response><href>/tester/tasks/a.ics</href><propstat><prop><getetag>"e1"</getetag></prop>
            <status>HTTP/1.1 200 OK</status></propstat></response><response><href>/tester/tasks/gone.ics</href><status>HTTP/1.1 404 Not Found</status></response>
            <sync-token>http://radicale.org/ns/sync/def</sync-token></multistatus>"""
        val m = Dav.parse(xml)
        assertEquals("http://radicale.org/ns/sync/def", m.syncToken)
        assertEquals("\"e1\"", m.responses[0].etag)
        assertEquals(404, m.responses[1].status)
    }

    @Test
    fun `multiget calendar data and paths`() {
        val xml =
            """<d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:response><d:href>/t/a.ics</d:href><d:propstat><d:prop>
            <d:getetag>"e2"</d:getetag><c:calendar-data>BEGIN:VCALENDAR&#13;
            END:VCALENDAR&#13;
            </c:calendar-data></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>"""
        assertTrue(
            Dav
                .parse(xml)
                .responses
                .single()
                .calendarData!!
                .startsWith("BEGIN:VCALENDAR"),
        )
        assertEquals("https://cloud.example.com/dav/t/a.ics", Dav.resolve("https://cloud.example.com/dav/", "/dav/t/a.ics"))
        assertTrue(Dav.multigetBody(listOf("/a&b.ics")).contains("/a&amp;b.ics"))
    }
}

class MergeTest {
    private val base = Task("t", "l", "Buy milk", notes = "2L", starred = false, dirty = false)

    @Test
    fun `each side keeps the fields it changed`() {
        val local = base.copy(title = "Buy oat milk")
        val remote = base.copy(notes = "1L", starred = true, etag = "\"new\"")
        val m = Merge.merge(base, local, remote)
        assertEquals("Buy oat milk", m.title)
        assertEquals("1L", m.notes)
        assertTrue(m.starred)
        assertEquals("\"new\"", m.etag)
        assertTrue(m.dirty)
    }

    @Test
    fun `nothing of ours left to send`() {
        val m = Merge.merge(base, base.copy(title = "Same"), base.copy(title = "Same"))
        assertFalse(m.dirty)
    }
}
