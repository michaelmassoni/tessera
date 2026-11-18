package io.github.michaelmassoni.tessera.clock.timer

/**
 * The timer keypad: digits shift in from the right into hh:mm:ss, like the Pixel clock.
 * "1", "3", "0" → 00:01:30. Fields may exceed 59 (99:99:99 is allowed) and are normalised.
 */
@JvmInline
value class TimerInput(
    val digits: String = "",
) {
    fun type(d: Char): TimerInput {
        require(d.isDigit())
        if (digits.length >= MAX_DIGITS || (digits.isEmpty() && d == '0')) return this
        return TimerInput(digits + d)
    }

    fun typeDoubleZero(): TimerInput = type('0').type('0')

    fun backspace(): TimerInput = TimerInput(digits.dropLast(1))

    val padded: String get() = digits.padStart(MAX_DIGITS, '0')
    val hours: Int get() = padded.substring(0, 2).toInt()
    val minutes: Int get() = padded.substring(2, 4).toInt()
    val seconds: Int get() = padded.substring(4, 6).toInt()

    val durationMillis: Long get() = ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L

    val isEmpty: Boolean get() = digits.isEmpty()

    companion object {
        const val MAX_DIGITS = 6
    }
}
