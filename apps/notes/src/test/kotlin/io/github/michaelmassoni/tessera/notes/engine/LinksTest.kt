package io.github.michaelmassoni.tessera.notes.engine

import io.github.michaelmassoni.tessera.notes.engine.Links.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    private fun kinds(text: String) = Links.find(text).map { it.kind to it.text }

    @Test
    fun `web addresses, with and without a scheme`() {
        assertEquals(listOf(Kind.Web to "https://example.com/a?b=1"), kinds("see https://example.com/a?b=1 now"))
        assertEquals(listOf(Kind.Web to "www.example.org"), kinds("www.example.org"))
        assertEquals(listOf(Kind.Web to "bom.gov.au/nsw"), kinds("check bom.gov.au/nsw."))
        assertEquals("https://bom.gov.au/nsw", Links.find("bom.gov.au/nsw").single().target)
    }

    @Test
    fun `sentence punctuation isn't part of the link`() {
        assertEquals("example.com", Links.find("Try example.com, then (example.org).")[0].text)
        assertEquals("example.org", Links.find("Try example.com, then (example.org).")[1].text)
    }

    @Test
    fun `emails, not mistaken for web addresses`() {
        assertEquals(listOf(Kind.Email to "sam@example.com"), kinds("mail sam@example.com today"))
        assertEquals("mailto:sam@example.com", Links.find("sam@example.com").single().target)
    }

    @Test
    fun `phone numbers with the usual separators`() {
        assertEquals(listOf(Kind.Phone to "0412 345 678"), kinds("call 0412 345 678 later"))
        assertEquals("tel:+61412345678", Links.find("ring +61 412 345 678").single().target)
        assertEquals(listOf(Kind.Phone to "(02) 9876-5432"), kinds("office (02) 9876-5432"))
        // Short numbers, dates and quantities aren't phone numbers.
        assertEquals(emptyList<Pair<Kind, String>>(), kinds("buy 12 eggs, 1.5 kg flour on 3/4"))
    }

    @Test
    fun `the link at the cursor`() {
        val text = "see example.com and 0412 345 678"
        assertEquals(Kind.Web, Links.at(text, 6)?.kind)
        assertEquals(Kind.Web, Links.at(text, 15)?.kind) // right at its end
        assertNull(Links.at(text, 17))
        assertEquals(Kind.Phone, Links.at(text, 22)?.kind)
    }
}
