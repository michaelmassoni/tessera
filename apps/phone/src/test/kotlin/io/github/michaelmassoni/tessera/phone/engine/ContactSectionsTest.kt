package io.github.michaelmassoni.tessera.phone.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ContactSectionsTest {
    @Test fun `sections by letter with accents folded and # last`() {
        val names = listOf("bob", "Émile", "123 Pizza", "Ada", "Eve")
        val sections = ContactSections.sections(names, { it }, Locale.ENGLISH)
        assertEquals(listOf("A", "B", "E", "#"), sections.map { it.first })
        assertEquals(listOf("Émile", "Eve"), sections[2].second)
    }

    @Test fun `search by word starts or number`() {
        assertTrue(ContactSections.matches("lov", "Ada Lovelace", emptyList()))
        assertTrue(ContactSections.matches("ada lo", "Ada Lovelace", emptyList()))
        assertTrue(ContactSections.matches("emi", "Émile Zola", emptyList()))
        assertFalse(ContactSections.matches("ace", "Ada Lovelace", emptyList()))
        assertTrue(ContactSections.matches("0412", "Bob", listOf("+61 412 345 678", "0412 345 678")))
        assertTrue(ContactSections.matches("", "Bob", emptyList()))
        assertFalse(ContactSections.matches("bob2", "Ada", listOf("02 9999 0000")))
    }
}
