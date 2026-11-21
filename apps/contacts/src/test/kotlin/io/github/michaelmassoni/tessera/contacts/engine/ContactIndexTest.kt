package io.github.michaelmassoni.tessera.contacts.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ContactIndexTest {
    private fun c(
        id: Long,
        name: String,
        starred: Boolean = false,
        phones: List<String> = emptyList(),
        emails: List<String> = emptyList(),
    ) = ContactSummary(id, "k$id", name, starred, phones = phones, emails = emails)

    private val people =
        listOf(
            c(1, "Zoë Adams", phones = listOf("+61 412 345 678")),
            c(2, "Émile Zola", starred = true, emails = listOf("emile@example.org")),
            c(3, "ada Lovelace", starred = true),
            c(4, "123 Pizza"),
            c(5, "Bob", phones = listOf("(02) 9876-5432")),
        )

    @Test fun `sections put favourites first, letters collated, and digits last`() {
        val sections = ContactIndex.sections(people, Locale.ENGLISH)
        assertEquals(
            listOf(
                Section.Favourites,
                Section.Letter("A"),
                Section.Letter("B"),
                Section.Letter("E"),
                Section.Letter("Z"),
                Section.Letter("#"),
            ),
            sections.map { it.first },
        )
        assertEquals(listOf("ada Lovelace", "Émile Zola"), sections[0].second.map { it.name })
    }

    @Test fun `name search matches word starts, ignoring accents and case`() {
        assertEquals(listOf("Émile Zola"), ContactIndex.search(people, "emile", Locale.ENGLISH).map { it.name })
        assertEquals(listOf("Émile Zola", "Zoë Adams"), ContactIndex.search(people, "z", Locale.ENGLISH).map { it.name })
        assertEquals(listOf("ada Lovelace"), ContactIndex.search(people, "LOVE ada", Locale.ENGLISH).map { it.name })
        assertEquals(emptyList<String>(), ContactIndex.search(people, "ovelace", Locale.ENGLISH).map { it.name })
    }

    @Test fun `phone search uses digits only, and email search matches anywhere`() {
        assertEquals(listOf("Zoë Adams"), ContactIndex.search(people, "412 345", Locale.ENGLISH).map { it.name })
        assertEquals(listOf("Zoë Adams"), ContactIndex.search(people, "412345", Locale.ENGLISH).map { it.name })
        assertEquals(listOf("Bob"), ContactIndex.search(people, "9876 5432", Locale.ENGLISH).map { it.name })
        assertEquals(listOf("Émile Zola"), ContactIndex.search(people, "example.org", Locale.ENGLISH).map { it.name })
        assertEquals(emptyList<ContactSummary>(), ContactIndex.search(people, "  ", Locale.ENGLISH))
    }
}
