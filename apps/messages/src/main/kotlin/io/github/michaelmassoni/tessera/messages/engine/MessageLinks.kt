package io.github.michaelmassoni.tessera.messages.engine

/** A link found in a message: [start] until [end] of the text, opened as [target]. */
data class MessageLink(
    val start: Int,
    val end: Int,
    val target: String,
)

/** Web links, email addresses and phone numbers in a message's text (docs/messages.md, Links). */
object MessageLinks {
    private val web =
        Regex(
            """(?i)\b(?:https?://|www\.)[^\s<>"]+|\b[a-z0-9-]+(?:\.[a-z0-9-]+)*\.(?:com|net|org|au|io|co|app|dev|me|ly|ms|gl|tv)(?:/[^\s<>"]*)?""",
        )
    private val email = Regex("""(?i)\b[a-z0-9._%+-]+@[a-z0-9-]+(?:\.[a-z0-9-]+)*\.[a-z]{2,}\b""")
    private val phone = Regex("""(?<![\w/.])\+?\(?\d[\d ()-]{6,}\d(?![\w/])""")

    // Punctuation that ends a sentence, not the link ("see example.com.").
    private val DATE = Regex("""\d{4}-\d{1,2}-\d{1,2}|\d{1,2}-\d{1,2}-\d{4}""")

    private const val TRAILING = ".,;:!?)]}'\""

    fun find(text: String): List<MessageLink> {
        val found = mutableListOf<MessageLink>()

        fun add(
            start: Int,
            end: Int,
            target: (String) -> String,
        ) {
            if (found.none { start < it.end && it.start < end }) found += MessageLink(start, end, target(text.substring(start, end)))
        }
        email.findAll(text).forEach { m -> add(m.range.first, m.range.last + 1) { "mailto:$it" } }
        web.findAll(text).forEach { m ->
            var end = m.range.last + 1
            while (end > m.range.first && text[end - 1] in TRAILING) end--
            // Domain-like words with an @ before them are part of an email (already found).
            add(m.range.first, end) { if (it.contains("://")) it else "https://$it" }
        }
        phone.findAll(text).forEach { m ->
            val digits = m.value.count(Char::isDigit)
            // A date ("2026-11-27") isn't a number to call.
            if (digits in 8..15 &&
                !DATE.matches(m.value)
            ) {
                add(m.range.first, m.range.last + 1) { "tel:" + it.filter { c -> c.isDigit() || c == '+' } }
            }
        }
        return found.sortedBy { it.start }
    }
}
