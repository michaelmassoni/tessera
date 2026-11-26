package io.github.michaelmassoni.tessera.messages.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PageStackTest {
    private val forgotten = mutableListOf<String>()
    private val pages = PageStack { forgotten += it }

    @Test fun `a conversation is keyed by who is in it`() {
        assertEquals(listOf(LIST_PAGE), messagePages(null, composing = false))
        assertEquals(listOf(LIST_PAGE, "thread:+611,+612"), messagePages(listOf("+611", "+612"), composing = false))
        assertEquals(listOf(LIST_PAGE, COMPOSE_PAGE), messagePages(null, composing = true))
    }

    @Test fun `back from a conversation forgets it but keeps the list`() {
        pages.show(messagePages(null, composing = false))
        pages.show(messagePages(listOf("+611"), composing = false))
        pages.show(messagePages(null, composing = false))
        assertEquals(listOf("thread:+611"), forgotten)
    }

    @Test fun `picking someone on the new-message page forgets that page`() {
        pages.show(messagePages(null, composing = true))
        pages.show(messagePages(listOf("+611"), composing = false))
        assertEquals(listOf(COMPOSE_PAGE), forgotten)
    }
}
