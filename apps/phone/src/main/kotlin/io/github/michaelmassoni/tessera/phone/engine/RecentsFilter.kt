package io.github.michaelmassoni.tessera.phone.engine

import java.text.Normalizer
import java.time.LocalDate

/** Recents narrowed by a search and to missed calls (docs/phone.md, Recents search). */
object RecentsFilter {
    fun isMissed(type: CallType) = type == CallType.Missed || type == CallType.Rejected

    /**
     * Keeps groups whose name contains [query] (ignoring case and accents) or whose number
     * contains its digits (two or more), and with [missedOnly], missed and declined calls only.
     * Days left empty are dropped.
     */
    fun filter(
        sections: List<Pair<LocalDate, List<CallGroup>>>,
        query: String,
        missedOnly: Boolean,
    ): List<Pair<LocalDate, List<CallGroup>>> {
        val q = fold(query.trim())
        val digits = query.filter(Char::isDigit)
        if (q.isEmpty() && !missedOnly) return sections
        return sections.mapNotNull { (day, groups) ->
            val kept =
                groups.filter { g ->
                    val c = g.latest
                    (!missedOnly || isMissed(c.type)) &&
                        (
                            q.isEmpty() ||
                                c.name?.let { fold(it).contains(q) } == true ||
                                (digits.length >= 2 && c.number.filter(Char::isDigit).contains(digits))
                        )
                }
            if (kept.isEmpty()) null else day to kept
        }
    }

    private fun fold(s: String) =
        Normalizer
            .normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
}
