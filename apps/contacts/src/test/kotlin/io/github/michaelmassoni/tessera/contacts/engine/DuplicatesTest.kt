package io.github.michaelmassoni.tessera.contacts.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatesTest {
    private fun c(
        id: Long,
        name: String,
        phones: List<String> = emptyList(),
        emails: List<String> = emptyList(),
    ) = ContactSummary(id, "k$id", name, phones = phones, emails = emails)

    private fun ids(groups: List<List<ContactSummary>>) = groups.map { g -> g.map { it.id }.toSet() }.toSet()

    @Test
    fun `the same name, ignoring case, accents and spacing`() {
        val groups = Duplicates.find(listOf(c(1, "Zoë  Smith"), c(2, "zoe smith"), c(3, "Zoe Smithers")))
        assertEquals(setOf(setOf(1L, 2L)), ids(groups))
    }

    @Test
    fun `a shared number, written nationally and internationally`() {
        val groups =
            Duplicates.find(
                listOf(c(1, "Mum", listOf("0412 345 678")), c(2, "Jan Smith", listOf("+61 412 345 678")), c(3, "Work", listOf("1300"))),
            )
        assertEquals(setOf(setOf(1L, 2L)), ids(groups))
    }

    @Test
    fun `a shared email, and groups joined through any link`() {
        val groups =
            Duplicates.find(
                listOf(
                    c(1, "Ada L.", emails = listOf("ada@example.org")),
                    c(2, "Ada Lovelace", emails = listOf("ADA@example.org ")),
                    c(3, "Ada Lovelace"),
                    c(4, "Charles Babbage"),
                ),
            )
        assertEquals(setOf(setOf(1L, 2L, 3L)), ids(groups))
    }

    @Test
    fun `nothing in common, no groups`() {
        assertEquals(emptyList<List<ContactSummary>>(), Duplicates.find(listOf(c(1, "A B", listOf("111")), c(2, "C D", listOf("111")))))
    }
}
