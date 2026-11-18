package io.github.michaelmassoni.tessera.calculator.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Turns evaluation results into internal-notation strings (see [Symbols]). */
object ResultFormatter {
    /** Significant digits shown in the live preview. */
    const val PREVIEW_DIGITS = 16

    /** Below this magnitude, results switch to scientific notation. */
    private const val MIN_PLAIN_EXPONENT = -6

    fun format(
        value: BigDecimal,
        maxDigits: Int = BigMath.RESULT_DIGITS,
    ): String {
        val rounded = value.round(MathContext(maxDigits, RoundingMode.HALF_EVEN))
        if (rounded.signum() == 0) return "0"
        val stripped = rounded.stripTrailingZeros()
        val exponent = BigMath.exponentOf(stripped)
        val text =
            if (exponent >= maxDigits || exponent < MIN_PLAIN_EXPONENT) {
                scientific(stripped, exponent)
            } else {
                stripped.toPlainString()
            }
        return text.replace('-', Symbols.MINUS)
    }

    private fun scientific(
        value: BigDecimal,
        exponent: Int,
    ): String {
        val digits = value.unscaledValue().abs().toString()
        val mantissa = if (digits.length == 1) digits else digits[0] + "." + digits.substring(1)
        val sign = if (value.signum() < 0) "-" else ""
        val exponentText = if (exponent < 0) "${Symbols.MINUS}${-exponent}" else exponent.toString()
        return "${sign}${mantissa}E$exponentText"
    }
}

/**
 * Localises an internal-notation expression for display: the locale's decimal separator, and
 * grouping separators in the integer part of each number (not in exponents).
 */
class DisplayFormatter(
    private val decimalSeparator: Char,
    private val groupingSeparator: Char,
) {
    constructor(locale: Locale) : this(
        DecimalFormatSymbols.getInstance(locale).decimalSeparator,
        DecimalFormatSymbols.getInstance(locale).groupingSeparator,
    )

    fun format(expression: String): String {
        val out = StringBuilder(expression.length + expression.length / 3)
        var i = 0
        while (i < expression.length) {
            val c = expression[i]
            if (c.isDigit() || c == '.') {
                val start = i
                while (i < expression.length && (expression[i].isDigit() || expression[i] == '.')) i++
                val inExponent = start > 0 && isExponentPrefix(expression, start)
                appendNumber(out, expression.substring(start, i), group = !inExponent)
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun isExponentPrefix(
        expression: String,
        start: Int,
    ): Boolean {
        val previous = expression[start - 1]
        if (previous == 'E') return true
        return (previous == Symbols.MINUS || previous == '+') && start >= 2 && expression[start - 2] == 'E'
    }

    private fun appendNumber(
        out: StringBuilder,
        number: String,
        group: Boolean,
    ) {
        val dot = number.indexOf('.')
        val integerPart = if (dot >= 0) number.substring(0, dot) else number
        if (group && integerPart.length > 3) {
            val firstGroup = integerPart.length % 3
            integerPart.forEachIndexed { index, digit ->
                if (index > 0 && (index - firstGroup) % 3 == 0) out.append(groupingSeparator)
                out.append(digit)
            }
        } else {
            out.append(integerPart)
        }
        if (dot >= 0) {
            out.append(decimalSeparator)
            out.append(number, dot + 1, number.length)
        }
    }

    /** Converts user-visible text (e.g. typed on a hardware keyboard) back to internal notation. */
    fun parseDecimalSeparator(c: Char): Char? = if (c == decimalSeparator || c == '.') '.' else null
}
