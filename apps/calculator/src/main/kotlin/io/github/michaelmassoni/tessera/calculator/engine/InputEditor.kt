package io.github.michaelmassoni.tessera.calculator.engine

/**
 * What the user has typed. [justEvaluated] is true right after "=", when [expression] holds the
 * result: the next digit starts a new calculation, the next operator continues from it.
 */
data class InputState(
    val expression: String = "",
    val justEvaluated: Boolean = false,
)

/** Things the user can enter. The UI and hardware keyboard both map onto these. */
sealed interface Input {
    data class Digit(
        val digit: Char,
    ) : Input

    data object Decimal : Input

    /** + − × ÷ ^ */
    data class BinaryOperator(
        val op: Char,
    ) : Input

    /** % or ! */
    data class Postfix(
        val op: Char,
    ) : Input

    /** π or e */
    data class Constant(
        val symbol: Char,
    ) : Input

    /** One of [Symbols.FUNCTIONS]. */
    data class Function(
        val name: String,
    ) : Input

    data object SquareRoot : Input

    /** Inserts "^2". */
    data object Square : Input

    /** Inserts "^−1". */
    data object Reciprocal : Input

    /** Inserts "e^(". */
    data object ExpPower : Input

    /** Inserts "10^(". */
    data object TenPower : Input

    /** The single smart parenthesis key. */
    data object Parenthesis : Input

    data object OpenParen : Input

    data object CloseParen : Input

    data object Backspace : Input

    data object Clear : Input
}

/**
 * Pure input-editing rules (docs/calculator.md, "Input rules"). It never evaluates anything;
 * it only keeps the expression well-formed as the user types.
 */
object InputEditor {
    const val MAX_LENGTH = 1000

    private val SIGN_AFTER = setOf(Symbols.TIMES, Symbols.DIVIDE, Symbols.POWER, '(')

    /** Inputs that bind tighter than a leading sign, so a negative result needs parentheses. */
    private fun binds(input: Input) =
        input == Input.Square ||
            input == Input.Reciprocal ||
            input is Input.Postfix ||
            (input is Input.BinaryOperator && input.op == Symbols.POWER)

    fun apply(
        state: InputState,
        input: Input,
    ): InputState {
        if (input == Input.Clear) return InputState()
        if (input == Input.Backspace) return InputState(backspace(state.expression))
        if (state.expression.length >= MAX_LENGTH) return state

        // After "=": operators continue from the result, anything else starts afresh.
        val continues =
            input is Input.BinaryOperator ||
                input is Input.Postfix ||
                input == Input.Square ||
                input == Input.Reciprocal ||
                input == Input.CloseParen
        val base =
            when {
                state.justEvaluated && !continues -> ""

                state.justEvaluated &&
                    binds(
                        input,
                    ) && state.expression.startsWith(Symbols.MINUS) -> "(${state.expression})"

                else -> state.expression
            }

        val next =
            when (input) {
                is Input.Digit -> digit(base, input.digit)
                Input.Decimal -> decimal(base)
                is Input.BinaryOperator -> binary(base, input.op)
                is Input.Postfix -> if (endsWithOperand(base)) base + input.op else base
                is Input.Constant -> base + input.symbol
                is Input.Function -> base + input.name
                Input.SquareRoot -> base + Symbols.SQRT
                Input.Square -> if (endsWithOperand(base)) base + "^2" else base
                Input.Reciprocal -> if (endsWithOperand(base)) base + "^" + Symbols.MINUS + "1" else base
                Input.ExpPower -> base + "e^("
                Input.TenPower -> (if (endsWithNumberChar(base)) base + Symbols.TIMES else base) + "10^("
                Input.Parenthesis -> if (openParens(base) > 0 && endsWithOperand(base)) "$base)" else "$base("
                Input.OpenParen -> "$base("
                Input.CloseParen -> if (openParens(base) > 0 && endsWithOperand(base)) "$base)" else base
                Input.Backspace, Input.Clear -> error("handled above")
            }
        return if (next == base && state.justEvaluated && continues) {
            state // e.g. "!" after an error-free result that can't take it: keep the result shown
        } else {
            InputState(next.take(MAX_LENGTH))
        }
    }

    /** Inserts a value from history: its result, or its whole expression in parentheses. */
    fun insert(
        state: InputState,
        text: String,
        asGroup: Boolean,
    ): InputState {
        val base = if (state.justEvaluated) "" else state.expression
        val piece = if (asGroup && base.isNotEmpty()) "($text)" else text
        val joined =
            if (endsWithNumberChar(base) && (piece.first().isDigit() || piece.first() == '.')) {
                base + Symbols.TIMES + piece
            } else {
                base + piece
            }
        return if (joined.length > MAX_LENGTH) state else InputState(joined)
    }

    // -------------------------------------------------------------------- rules

    private fun digit(
        base: String,
        d: Char,
    ): String {
        val number = trailingNumber(base)
        // Replace a lone leading zero: "0" then "7" is "7", but "0.0" then "7" is "0.07".
        if (number == "0") return base.dropLast(1) + d
        return base + d
    }

    private fun decimal(base: String): String {
        val number = trailingNumber(base)
        return when {
            number.contains('.') || number.contains('E') -> base
            number.isEmpty() -> base + "0."
            else -> "$base."
        }
    }

    private fun binary(
        base: String,
        op: Char,
    ): String {
        if (base.isEmpty()) return if (op == Symbols.MINUS) base + op else base
        val last = base.last()
        return when {
            last == '(' || last == Symbols.SQRT -> {
                if (op == Symbols.MINUS) base + op else base
            }

            last == 'E' -> {
                base
            }

            last in Symbols.BINARY_OPERATORS -> {
                if (op == Symbols.MINUS && last in SIGN_AFTER) {
                    base + op
                } else {
                    replaceOperatorRun(base, op)
                }
            }

            else -> {
                base + op
            }
        }
    }

    /** Replaces trailing operators (e.g. the "×−" in "5×−") with [op]. */
    private fun replaceOperatorRun(
        base: String,
        op: Char,
    ): String {
        var end = base.length
        while (end > 0 && base[end - 1] in Symbols.BINARY_OPERATORS) end--
        val stripped = base.substring(0, end)
        if (stripped.isEmpty() || stripped.last() == '(' || stripped.last() == Symbols.SQRT) {
            return if (op == Symbols.MINUS) stripped + op else stripped
        }
        return stripped + op
    }

    private fun backspace(expression: String): String {
        val function = Symbols.FUNCTIONS.firstOrNull { expression.endsWith(it) }
        return when {
            function != null -> expression.dropLast(function.length)
            else -> expression.dropLast(1)
        }
    }

    /** The number being typed at the end of [expression], including any exponent ("1.5E−7"). */
    private fun trailingNumber(expression: String): String {
        var i = expression.length
        while (i > 0) {
            val c = expression[i - 1]
            val isExponentSign =
                (c == Symbols.MINUS || c == '+') && i >= 2 && expression[i - 2] == 'E'
            if (c.isDigit() || c == '.' || c == 'E' || isExponentSign) i-- else break
        }
        return expression.substring(i)
    }

    private fun endsWithNumberChar(expression: String): Boolean =
        expression.lastOrNull()?.let { it.isDigit() || it == '.' } == true

    fun endsWithOperand(expression: String): Boolean {
        val last = expression.lastOrNull() ?: return false
        return last.isDigit() ||
            last == '.' ||
            last == ')' ||
            last == Symbols.PI ||
            last == Symbols.E ||
            last == Symbols.PERCENT ||
            last == Symbols.FACTORIAL
    }

    fun openParens(expression: String): Int = expression.count { it == '(' } - expression.count { it == ')' }

    private val PLAIN_NUMBER = Regex("""^−?(\d+\.?\d*|\.\d+)(E[−+]?\d+)?$""")

    /** True when the expression does more than state a number, so a live preview is useful. */
    fun hasOperation(expression: String): Boolean = expression.isNotEmpty() && !PLAIN_NUMBER.matches(expression)
}
