package io.github.michaelmassoni.tessera.messages.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageLinksTest {
    private fun targets(text: String) = MessageLinks.find(text).map { it.target }

    private fun spans(text: String) = MessageLinks.find(text).map { text.substring(it.start, it.end) }

    @Test
    fun `web links with and without a scheme`() {
        val text = "Double Dollars https://oce.tx.ms/zJalOVP3xowri\n\nUnsubscribe: https://oce.tx.ms/KEMclgNhEaKva"
        assertEquals(listOf("https://oce.tx.ms/zJalOVP3xowri", "https://oce.tx.ms/KEMclgNhEaKva"), targets(text))
        assertEquals(listOf("https://www.example.com/a"), targets("go to www.example.com/a."))
        assertEquals(listOf("https://ngv.vic.gov.au"), targets("Out the front of ngv.vic.gov.au!"))
    }

    @Test
    fun `sentence punctuation is not part of the link`() {
        assertEquals(listOf("https://a.com/x"), spans("see (https://a.com/x)."))
    }

    @Test
    fun `emails are mailto and not also web links`() {
        assertEquals(listOf("mailto:sam@example.com.au"), targets("email sam@example.com.au please"))
    }

    @Test
    fun `phone numbers need eight to fifteen digits`() {
        assertEquals(listOf("tel:0412345678", "tel:+61398765432"), targets("call 0412 345 678 or +61 (3) 9876-5432"))
        assertEquals(emptyList<String>(), targets("code 12345, see you at 6:30, 2026-11-27"))
    }

    @Test
    fun `plain text has no links`() {
        assertEquals(emptyList<String>(), targets("Running late, see you at 6. ok."))
    }
}
