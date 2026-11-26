package io.github.michaelmassoni.tessera.notes.engine

/**
 * Web addresses, email addresses and phone numbers in a note's text, so the editor can offer to
 * open the one the cursor is in (docs/notes.md, Links). Pure regexes rather than
 * `android.util.Patterns`, so it's unit tested.
 */
object Links {
    enum class Kind { Web, Email, Phone }

    data class Link(
        val kind: Kind,
        /** Where it is in the text: [start, end). */
        val start: Int,
        val end: Int,
        val text: String,
    ) {
        /** What to open: an https:// address, mailto: or tel:. */
        val target: String
            get() =
                when (kind) {
                    Kind.Web -> if (text.contains("://")) text else "https://$text"
                    Kind.Email -> "mailto:$text"
                    Kind.Phone -> "tel:" + text.filter { it.isDigit() || it == '+' }
                }
    }

    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

    // "https://…", "http://…", or a bare "www.…" / "name.tld/…" (a known-looking top-level domain).
    private val web =
        Regex(
            """(?:https?://[^\s<>"]+|www\.[^\s<>"]+|\b[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.(?:com|org|net|io|dev|app|au|uk|nz|de|fr|co|edu|gov|info|me)(?:/[^\s<>"]*)?)""",
        )

    // At least 6 digits, with the usual separators; a leading + for international numbers.
    private val phone = Regex("""(?<![\w/])\+?\(?\d[\d\s().-]{4,}\d(?![\w/])""")

    /** Trailing punctuation that ends a sentence rather than the link: "see example.com." */
    private val trailing = ".,;:!?)]}'\""

    fun find(text: String): List<Link> {
        val found = mutableListOf<Link>()

        fun overlaps(
            s: Int,
            e: Int,
        ) = found.any { s < it.end && e > it.start }
        email.findAll(text).forEach { found += Link(Kind.Email, it.range.first, it.range.last + 1, it.value) }
        web.findAll(text).forEach { m ->
            var end = m.range.last + 1
            while (end > m.range.first && text[end - 1] in trailing) end--
            if (!overlaps(m.range.first, end)) found += Link(Kind.Web, m.range.first, end, text.substring(m.range.first, end))
        }
        phone.findAll(text).forEach { m ->
            val digits = m.value.count { it.isDigit() }
            if (digits in 6..15 && !overlaps(m.range.first, m.range.last + 1)) {
                found += Link(Kind.Phone, m.range.first, m.range.last + 1, m.value.trim())
            }
        }
        return found.sortedBy { it.start }
    }

    /** The link the cursor is in (or right at the end of), if any. */
    fun at(
        text: String,
        cursor: Int,
    ): Link? = find(text).firstOrNull { cursor in it.start..it.end }
}
