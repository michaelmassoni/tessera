package io.github.michaelmassoni.tessera.messages.engine

/** What's typed in the To field: a name to search for, or a phone number to format and check. */
object Recipients {
    private val numberChars = Regex("[0-9+()\\-. ]+")

    /** Digits with the usual separators, and at least one digit: treat it as a number, not a name. */
    fun looksLikeNumber(text: String): Boolean = numberChars.matches(text) && text.any(Char::isDigit)

    /** The digits, and a leading + if there is one: what gets sent, without formatting. */
    fun dialable(text: String): String {
        val digits = text.filter(Char::isDigit)
        return if (text.trimStart().startsWith("+")) "+$digits" else digits
    }

    /**
     * Short codes (carrier services, verification senders) aren't full phone numbers, so they're
     * never flagged as invalid.
     */
    fun isShortCode(dialable: String): Boolean = !dialable.startsWith("+") && dialable.length <= 6

    /** Long enough that "not a valid number" is worth saying, rather than "not finished yet". */
    fun shouldCheck(dialable: String): Boolean = !isShortCode(dialable) && dialable.count(Char::isDigit) >= 8

    /**
     * [dialable] as [format] lays it out ("0412 345 678"), or unchanged if the formatter can't, or
     * would change the digits themselves.
     */
    fun display(
        dialable: String,
        format: (String) -> String?,
    ): String {
        val formatted = format(dialable) ?: return dialable
        return if (dialable(formatted) == dialable) formatted else dialable
    }

    /**
     * Cursor mapping between [dialable] and its [display]: for each offset in the dialable text,
     * the offset in the display, and back.
     */
    fun offsets(
        dialable: String,
        display: String,
    ): Pair<IntArray, IntArray> {
        val toDisplay = IntArray(dialable.length + 1)
        val toDialable = IntArray(display.length + 1)
        var seen = 0
        display.forEachIndexed { j, c ->
            toDialable[j] = seen
            if (c.isDigit() || c == '+') {
                if (seen < dialable.length) toDisplay[seen] = j
                seen++
            }
        }
        toDialable[display.length] = seen.coerceAtMost(dialable.length)
        toDisplay[dialable.length] = display.length
        return toDisplay to toDialable
    }
}
