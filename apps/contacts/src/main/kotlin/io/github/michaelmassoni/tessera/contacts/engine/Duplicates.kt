package io.github.michaelmassoni.tessera.contacts.engine

import java.text.Normalizer

/**
 * Contacts that look like the same person: the same name (ignoring case, accents and spacing), or
 * a phone number or email in common. Groups are joined through any link ("Ada L." and "Ada
 * Lovelace" sharing a number, and "Ada Lovelace" with another card of that name, are one group).
 * Pure, so it's unit tested.
 */
object Duplicates {
    fun find(contacts: List<ContactSummary>): List<List<ContactSummary>> {
        val parent = IntArray(contacts.size) { it }

        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            parent[i] = r
            return r
        }

        val firstWith = mutableMapOf<String, Int>()
        contacts.forEachIndexed { i, c ->
            keys(c).forEach { key ->
                val j = firstWith.getOrPut(key) { i }
                if (j != i) parent[root(i)] = root(j)
            }
        }
        return contacts.indices
            .groupBy(::root)
            .values
            .filter { it.size > 1 }
            .map { group -> group.map(contacts::get).sortedBy { it.name.lowercase() } }
            .sortedBy { it.first().name.lowercase() }
    }

    private fun keys(c: ContactSummary): List<String> =
        listOfNotNull(name(c.name)?.let { "n:$it" }) +
            c.phones.mapNotNull(::phone).map { "p:$it" } +
            c.emails.map { "e:" + it.trim().lowercase() }.filter { it.length > 2 }

    /** A name worth matching on: two characters or more, folded. */
    private fun name(name: String): String? =
        Normalizer
            .normalize(name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .takeIf { it.length >= 2 }

    /**
     * A phone number's last nine digits: the same number written nationally ("0412 345 678") and
     * internationally ("+61 412 345 678") matches. Shorter numbers aren't matched on.
     */
    private fun phone(number: String): String? = number.filter(Char::isDigit).takeIf { it.length >= 8 }?.takeLast(9)
}
