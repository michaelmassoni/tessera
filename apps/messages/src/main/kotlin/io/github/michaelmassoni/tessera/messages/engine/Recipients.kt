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
     * What [display] adds to [dialable] (the spaces, dashes and brackets of the layout), as
     * (position in the display, character), in order. The field's output transformation inserts
     * them, so Compose keeps the cursor in the right place. Empty when the display isn't [dialable]
     * with characters added.
     */
    fun insertions(
        dialable: String,
        display: String,
    ): List<Pair<Int, Char>> {
        val added = mutableListOf<Pair<Int, Char>>()
        var r = 0
        display.forEachIndexed { s, c ->
            if (r < dialable.length && c == dialable[r]) r++ else added += s to c
        }
        return if (r == dialable.length) added else emptyList()
    }
}
