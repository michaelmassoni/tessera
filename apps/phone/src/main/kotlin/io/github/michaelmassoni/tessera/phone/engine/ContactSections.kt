package io.github.michaelmassoni.tessera.phone.engine

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Sorting, sectioning and searching the Contacts tab, matching the Contacts app. */
object ContactSections {
    private val DIACRITICS = Regex("\\p{Mn}+")
    private val WORD_BREAK = Regex("[\\s\\-'.,]+")

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase(Locale.ROOT)

    /** The header letter for [name]: its first letter without accents, or "#". */
    fun letter(name: String): String {
        val first = normalize(name.trim()).firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else "#"
    }

    /** Items sorted with [locale]'s collation under their letter, "#" last. */
    fun <T> sections(
        items: List<T>,
        name: (T) -> String,
        locale: Locale = Locale.getDefault(),
    ): List<Pair<String, List<T>>> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
        return items
            .sortedWith { a, b -> collator.compare(name(a), name(b)) }
            .groupBy { letter(name(it)) }
            .toList()
            .sortedWith { (a, _), (b, _) ->
                when {
                    a == "#" && b != "#" -> 1
                    b == "#" && a != "#" -> -1
                    else -> collator.compare(a, b)
                }
            }
    }

    /**
     * Every word of [query] starts a word of [name], or the query is a number (at least 2 digits,
     * mostly digits) found in one of [numbers].
     */
    fun matches(
        query: String,
        name: String,
        numbers: List<String>,
    ): Boolean {
        val q = normalize(query.trim())
        if (q.isEmpty()) return true
        val words = normalize(name).split(WORD_BREAK).filter { it.isNotEmpty() }
        if (q.split(Regex("\\s+")).all { t -> words.any { it.startsWith(t) } }) return true
        val d = digits(q)
        return d.length * 2 >= q.count { !it.isWhitespace() } && numbers.any { NumberMatch.matches(d, it) }
    }
}
