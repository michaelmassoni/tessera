package io.github.michaelmassoni.tessera.notes.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PageStackTest {
    private val forgotten = mutableListOf<String>()
    private val pages = PageStack { forgotten += it }

    @Test fun `closing a note forgets it but never the list`() {
        pages.show(listOf(LIST_PAGE))
        pages.show(listOf(LIST_PAGE, notePageKey("a")))
        assertEquals(emptyList<String>(), forgotten)
        pages.show(listOf(LIST_PAGE))
        assertEquals(listOf("note:a"), forgotten)
    }

    @Test fun `keys tell the list and each note apart`() {
        assertEquals(LIST_PAGE, notePageKey(null))
        assertEquals("note:a", notePageKey("a"))
    }
}
