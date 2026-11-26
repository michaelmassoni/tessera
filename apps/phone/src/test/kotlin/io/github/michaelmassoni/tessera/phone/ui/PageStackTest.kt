package io.github.michaelmassoni.tessera.phone.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PageStackTest {
    private val forgotten = mutableListOf<String>()
    private val pages = PageStack { forgotten += it }

    @Test fun `keys tell the tabs and each history apart`() {
        assertEquals(LIST_PAGE, historyPageKey(null))
        assertEquals("history:+61400000000", historyPageKey("+61400000000"))
        assertEquals("tab:Recents", tabKey("Recents"))
    }

    @Test fun `closing a call history forgets it but keeps the tabs`() {
        pages.show(listOf(LIST_PAGE))
        pages.show(listOf(LIST_PAGE, historyPageKey("123")))
        pages.show(listOf(LIST_PAGE))
        assertEquals(listOf("history:123"), forgotten)
    }
}
