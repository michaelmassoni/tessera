package io.github.michaelmassoni.tessera.contacts.engine

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** A list section: "Favourites", a letter, or "#". */
sealed interface Section {
    data object Favourites : Section

    data class Letter(
        val letter: String,
    ) : Section
}

/** Sorting, sectioning and searching the contact list. */
object ContactIndex {
    private val DIACRITICS = Regex("\\p{Mn}+")

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase(Locale.ROOT)

    /** Digits only, for matching phone numbers however they are formatted. */
    fun digits(text: String): String = text.filter { it.isDigit() }

    /** The header letter for [name]: its first letter without accents, or "#" if it doesn't start with a letter. */
    fun letter(name: String): String {
        val first = normalize(name.trim()).firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else "#"
    }

    /**
     * Favourites first, then every contact under its letter, sorted with [locale]'s collation
     * ("#" last). A favourite appears in both places, like the system app.
     */
    fun sections(
        contacts: List<ContactSummary>,
        locale: Locale = Locale.getDefault(),
    ): List<Pair<Section, List<ContactSummary>>> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        val sorted = contacts.sortedWith { a, b -> collator.compare(a.name, b.name) }
        val favourites = sorted.filter { it.starred }
        val letters =
            sorted
                .groupBy { letter(it.name) }
                .toList()
                .sortedWith { (a, _), (b, _) ->
                    when {
                        a == "#" && b != "#" -> 1
                        b == "#" && a != "#" -> -1
                        else -> collator.compare(a, b)
                    }
                }.map { (letter, list) -> Section.Letter(letter) to list }
        return (if (favourites.isEmpty()) emptyList() else listOf(Section.Favourites to favourites)) + letters
    }

    /**
     * Contacts matching [query]: every word must start a word of the name, or the query's digits
     * (at least 3) must appear in a phone number, or the query must appear in an email.
     */
    fun search(
        contacts: List<ContactSummary>,
        query: String,
        locale: Locale = Locale.getDefault(),
    ): List<ContactSummary> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return emptyList()
        val terms = q.split(Regex("\\s+"))
        val qDigits = digits(q)
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return contacts
            .filter { c ->
                val words = normalize(c.name).split(Regex("[\\s\\-'.,]+")).filter { it.isNotEmpty() }
                val nameMatch = terms.all { t -> words.any { it.startsWith(t) } }
                val phoneMatch =
                    qDigits.length >= 3 && qDigits.length * 2 >= q.count { !it.isWhitespace() } &&
                        c.phones.any { digits(it).contains(qDigits) }
                val emailMatch = q.length >= 2 && c.emails.any { normalize(it).contains(q) }
                nameMatch || phoneMatch || emailMatch
            }.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }
}
