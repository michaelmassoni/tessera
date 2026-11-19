package io.github.michaelmassoni.tessera.keyboard.engine

/** A key on the keyboard. [weight] is its width relative to a letter key. */
sealed interface Key {
    val weight: Float

    /** Types [text]. [long] is what a long press types directly (the top-row digits). */
    data class Char(
        val text: String,
        val long: String? = null,
        override val weight: Float = 1f,
    ) : Key

    data class Shift(
        override val weight: Float = 1.5f,
    ) : Key

    data class Backspace(
        override val weight: Float = 1.5f,
    ) : Key

    data class Space(
        override val weight: Float = 5f,
    ) : Key

    data class Action(
        override val weight: Float = 1.5f,
    ) : Key

    /** Switches to [layout]; [label] is what the key shows ("?123", "ABC", "=\\<"). */
    data class Switch(
        val layout: Layout,
        val label: String,
        override val weight: Float = 1.5f,
    ) : Key

    /** Fills space so a row lines up, like the half-key indents of the middle letter row. */
    data class Gap(
        override val weight: Float,
    ) : Key
}

enum class Layout { Letters, Symbols, MoreSymbols, Numbers, Phone }

object Layouts {
    private val digits = "1234567890"

    fun rows(
        layout: Layout,
        numberRow: Boolean,
        decimal: Boolean = false,
    ): List<List<Key>> =
        when (layout) {
            Layout.Letters -> letters(numberRow)
            Layout.Symbols -> symbols()
            Layout.MoreSymbols -> moreSymbols()
            Layout.Numbers -> numbers(decimal)
            Layout.Phone -> phone()
        }

    private fun chars(
        text: String,
        long: String? = null,
    ) = text.mapIndexed { i, c -> Key.Char(c.toString(), long?.getOrNull(i)?.toString()) }

    private fun letters(numberRow: Boolean): List<List<Key>> =
        listOfNotNull(
            if (numberRow) chars(digits) else null,
            chars("qwertyuiop", if (numberRow) null else digits),
            listOf(Key.Gap(0.5f)) + chars("asdfghjkl") + Key.Gap(0.5f),
            listOf(Key.Shift()) + chars("zxcvbnm") + Key.Backspace(),
            listOf(Key.Switch(Layout.Symbols, "?123"), Key.Char(","), Key.Space(), Key.Char("."), Key.Action()),
        )

    private fun symbols(): List<List<Key>> =
        listOf(
            chars(digits),
            chars("@#$%&_-+()"),
            listOf(Key.Switch(Layout.MoreSymbols, "=\\<")) + chars("*\"':;!?") + Key.Backspace(),
            listOf(Key.Switch(Layout.Letters, "ABC"), Key.Char(","), Key.Space(4f), Key.Char("/"), Key.Char("."), Key.Action()),
        )

    private fun moreSymbols(): List<List<Key>> =
        listOf(
            chars("~`|•√π÷×¶∆"),
            chars("£€¥^°={}\\§"),
            listOf(Key.Switch(Layout.Symbols, "?123")) + chars("%©®™✓[]") + Key.Backspace(),
            listOf(
                Key.Switch(Layout.Letters, "ABC"),
                Key.Char("<"),
                Key.Char(">"),
                Key.Space(3f),
                Key.Char("¢"),
                Key.Char("…"),
                Key.Action(),
            ),
        )

    private fun numbers(decimal: Boolean): List<List<Key>> =
        listOf(
            chars("123") + (if (decimal) Key.Char("-") else Key.Gap(1f)),
            chars("456") + (if (decimal) Key.Char(".") else Key.Gap(1f)),
            chars("789") + Key.Backspace(1f),
            listOf(Key.Gap(1f), Key.Char("0"), Key.Gap(1f), Key.Action(1f)),
        )

    private fun phone(): List<List<Key>> =
        listOf(
            chars("123") + Key.Char("+"),
            chars("456") + Key.Char(" "),
            chars("789") + Key.Backspace(1f),
            listOf(Key.Char("*"), Key.Char("0"), Key.Char("#"), Key.Action(1f)),
        )
}

/** Shift: off, on for one letter, or locked. Double-tapping within [doubleTapMillis] locks it. */
enum class ShiftState { Off, Once, Locked }

class Shift(
    private val doubleTapMillis: Long = 400,
) {
    var state: ShiftState = ShiftState.Off
        private set
    private var lastTap = Long.MIN_VALUE / 2

    fun tap(now: Long) {
        state =
            when {
                state == ShiftState.Once && now - lastTap <= doubleTapMillis -> ShiftState.Locked
                state == ShiftState.Off -> ShiftState.Once
                else -> ShiftState.Off
            }
        lastTap = now
    }

    /** After typing a letter: a one-shot shift turns off. */
    fun typed() {
        if (state == ShiftState.Once) state = ShiftState.Off
    }

    /** Auto-capitalisation from the field: turn on (or off) unless locked. */
    fun auto(capitalize: Boolean) {
        if (state != ShiftState.Locked) state = if (capitalize) ShiftState.Once else ShiftState.Off
    }
}

object Accents {
    private val map =
        mapOf(
            "a" to "àáâäãåāæ",
            "c" to "çćč",
            "e" to "éèêëēę",
            "i" to "íìîïī",
            "l" to "ł",
            "n" to "ñń",
            "o" to "óòôöõøōœ",
            "s" to "ßśš",
            "u" to "úùûüū",
            "y" to "ýÿ",
            "z" to "žźż",
            "." to ",?!:;'\"-",
            "," to "'\"",
            "-" to "–—_",
            "$" to "£€¥¢",
        )

    /** Alternatives for [key] in the current case. */
    fun of(
        key: String,
        upper: Boolean,
    ): List<String> {
        val alts = map[key.lowercase()]?.map { it.toString() }.orEmpty()
        return if (upper) alts.map { it.uppercase() } else alts
    }
}

/** What the action key does, derived from EditorInfo.imeOptions/inputType (passed as ints). */
enum class EnterAction { Newline, Go, Search, Send, Next, Previous, Done }

object ActionKey {
    // EditorInfo constants, copied so this stays plain Kotlin (checked in tests against the platform).
    const val IME_MASK_ACTION = 0x000000ff
    const val IME_ACTION_GO = 2
    const val IME_ACTION_SEARCH = 3
    const val IME_ACTION_SEND = 4
    const val IME_ACTION_NEXT = 5
    const val IME_ACTION_DONE = 6
    const val IME_ACTION_PREVIOUS = 7
    const val IME_FLAG_NO_ENTER_ACTION = 0x40000000
    const val TYPE_TEXT_FLAG_MULTI_LINE = 0x00020000

    fun of(
        imeOptions: Int,
        inputType: Int,
    ): EnterAction {
        val multiLine = inputType and TYPE_TEXT_FLAG_MULTI_LINE != 0
        if (imeOptions and IME_FLAG_NO_ENTER_ACTION != 0 || (multiLine && imeOptions and IME_MASK_ACTION <= 1)) return EnterAction.Newline
        return when (imeOptions and IME_MASK_ACTION) {
            IME_ACTION_GO -> EnterAction.Go
            IME_ACTION_SEARCH -> EnterAction.Search
            IME_ACTION_SEND -> EnterAction.Send
            IME_ACTION_NEXT -> EnterAction.Next
            IME_ACTION_PREVIOUS -> EnterAction.Previous
            IME_ACTION_DONE -> EnterAction.Done
            else -> if (multiLine) EnterAction.Newline else EnterAction.Done
        }
    }
}
