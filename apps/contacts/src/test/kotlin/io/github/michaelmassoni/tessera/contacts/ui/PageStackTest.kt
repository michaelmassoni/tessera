package io.github.michaelmassoni.tessera.contacts.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PageStackTest {
    private val forgotten = mutableListOf<String>()
    private val pages = PageStack { forgotten += it }

    @Test fun `pages stack up from the list`() {
        assertEquals(listOf(LIST_PAGE), contactPages(null, false, null))
        assertEquals(listOf(LIST_PAGE, "contact:7"), contactPages(7, false, null))
        assertEquals(listOf(LIST_PAGE, "contact:7", "edit:7"), contactPages(7, true, 7))
        assertEquals(listOf(LIST_PAGE, "edit:new"), contactPages(null, true, null))
    }

    @Test fun `leaving the editor keeps the contact under it, leaving the contact forgets it`() {
        pages.show(contactPages(null, false, null))
        pages.show(contactPages(7, false, null))
        pages.show(contactPages(7, true, 7))
        assertEquals(emptyList<String>(), forgotten)
        pages.show(contactPages(7, false, null))
        assertEquals(listOf("edit:7"), forgotten)
        pages.show(contactPages(null, false, null))
        assertEquals(listOf("edit:7", "contact:7"), forgotten)
    }

    @Test fun `a new contact saved and shown forgets its editor`() {
        pages.show(contactPages(null, true, null))
        pages.show(contactPages(9, false, null))
        assertEquals(listOf("edit:new"), forgotten)
    }
}
