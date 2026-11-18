package io.github.michaelmassoni.tessera.calculator.engine

/**
 * The calculator's internal expression notation. Numbers always use "." as the decimal point
 * and have no grouping; the UI localises them for display ([DisplayFormatter]).
 */
object Symbols {
    const val MINUS = '−' // U+2212, used both for subtraction and for the sign
    const val TIMES = '×'
    const val DIVIDE = '÷'
    const val POWER = '^'
    const val PERCENT = '%'
    const val FACTORIAL = '!'
    const val SQRT = '√'
    const val PI = 'π'
    const val E = 'e'

    const val SIN = "sin("
    const val COS = "cos("
    const val TAN = "tan("
    const val ASIN = "sin⁻¹("
    const val ACOS = "cos⁻¹("
    const val ATAN = "tan⁻¹("
    const val LN = "ln("
    const val LOG = "log("

    /** Longest first, so "sin⁻¹(" wins over "sin(". */
    val FUNCTIONS: List<String> = listOf(ASIN, ACOS, ATAN, SIN, COS, TAN, LN, LOG)

    /** Single-character operators (binary, prefix and postfix). */
    val OPERATORS: Set<Char> = setOf('+', MINUS, TIMES, DIVIDE, POWER, PERCENT, FACTORIAL, SQRT)

    val BINARY_OPERATORS: Set<Char> = setOf('+', MINUS, TIMES, DIVIDE, POWER)
}
