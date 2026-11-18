package io.github.michaelmassoni.tessera.calculator.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class AngleUnit { DEGREES, RADIANS }

enum class CalcError { DivideByZero, Domain, Overflow, Syntax }

class CalcException(
    val error: CalcError,
) : RuntimeException(error.name)

sealed interface EvalResult {
    data class Value(
        val value: BigDecimal,
    ) : EvalResult

    data class Error(
        val error: CalcError,
    ) : EvalResult
}

/**
 * Evaluates expressions in the calculator's internal notation (see [Symbols]).
 * The grammar and precedence are documented in docs/calculator.md.
 */
object Evaluator {
    private val RESULT_MC = MathContext(BigMath.RESULT_DIGITS, RoundingMode.HALF_EVEN)

    fun evaluate(
        expression: String,
        angleUnit: AngleUnit = AngleUnit.RADIANS,
    ): EvalResult =
        try {
            val tokens = Tokenizer.tokenize(expression)
            val ast = Parser(tokens).parseAll()
            val raw = ast.eval(angleUnit)
            val rounded = BigMath.checkRange(raw).round(RESULT_MC)
            EvalResult.Value(if (rounded.signum() == 0) BigDecimal.ZERO else rounded.stripTrailingZeros())
        } catch (e: CalcException) {
            EvalResult.Error(e.error)
        } catch (e: ArithmeticException) {
            // BigDecimal overflow of its 32-bit scale; only reachable with absurd inputs.
            EvalResult.Error(CalcError.Overflow)
        }
}

// ------------------------------------------------------------------------- tokens

internal sealed interface Token {
    data class Number(
        val value: BigDecimal,
    ) : Token

    data class Constant(
        val value: BigDecimal,
    ) : Token

    data class Function(
        val name: String,
    ) : Token // includes its "("

    data class Op(
        val symbol: Char,
    ) : Token // + − × ÷ ^ % ! √

    data object LParen : Token

    data object RParen : Token
}

internal object Tokenizer {
    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c.isWhitespace() -> {
                    i++
                }

                c.isDigit() || c == '.' -> {
                    i = readNumber(input, i, tokens)
                }

                c == Symbols.PI -> {
                    tokens += Token.Constant(BigMath.PI)
                    i++
                }

                c == Symbols.E && !startsFunction(input, i) -> {
                    tokens += Token.Constant(BigMath.E)
                    i++
                }

                c == '(' -> {
                    tokens += Token.LParen
                    i++
                }

                c == ')' -> {
                    tokens += Token.RParen
                    i++
                }

                c in Symbols.OPERATORS -> {
                    tokens += Token.Op(c)
                    i++
                }

                c == '-' -> {
                    tokens += Token.Op(Symbols.MINUS)
                    i++
                }

                c == '*' -> {
                    tokens += Token.Op(Symbols.TIMES)
                    i++
                }

                c == '/' -> {
                    tokens += Token.Op(Symbols.DIVIDE)
                    i++
                }

                else -> {
                    val function =
                        Symbols.FUNCTIONS.firstOrNull { input.startsWith(it, i) }
                            ?: throw CalcException(CalcError.Syntax)
                    tokens += Token.Function(function)
                    i += function.length
                }
            }
        }
        return tokens
    }

    private fun startsFunction(
        input: String,
        index: Int,
    ): Boolean = Symbols.FUNCTIONS.any { input.startsWith(it, index) }

    private fun readNumber(
        input: String,
        start: Int,
        tokens: MutableList<Token>,
    ): Int {
        var i = start
        var seenDot = false
        while (i < input.length && (input[i].isDigit() || input[i] == '.')) {
            if (input[i] == '.') {
                if (seenDot) throw CalcException(CalcError.Syntax)
                seenDot = true
            }
            i++
        }
        var text = input.substring(start, i)
        if (text == ".") throw CalcException(CalcError.Syntax)
        // Scientific notation as produced by the result formatter: 1.5E20, 1.5E−7, 2E+5.
        if (i < input.length && input[i] == 'E') {
            var j = i + 1
            val sign =
                when (input.getOrNull(j)) {
                    Symbols.MINUS, '-' -> "-".also { j++ }
                    '+' -> "".also { j++ }
                    else -> ""
                }
            val digitsStart = j
            while (j < input.length && input[j].isDigit()) j++
            if (j == digitsStart) throw CalcException(CalcError.Syntax)
            text += "E" + sign + input.substring(digitsStart, j)
            i = j
        }
        val value =
            try {
                BigDecimal(text.trimEnd('.').ifEmpty { "0" })
            } catch (e: NumberFormatException) {
                throw CalcException(CalcError.Syntax)
            }
        tokens += Token.Number(BigMath.checkRange(value))
        return i
    }
}

// ------------------------------------------------------------------------- AST

internal sealed interface Node {
    fun eval(unit: AngleUnit): BigDecimal

    data class Literal(
        val value: BigDecimal,
    ) : Node {
        override fun eval(unit: AngleUnit) = value
    }

    data class Negate(
        val operand: Node,
    ) : Node {
        override fun eval(unit: AngleUnit): BigDecimal = operand.eval(unit).negate()
    }

    data class Percent(
        val operand: Node,
    ) : Node {
        override fun eval(unit: AngleUnit): BigDecimal = operand.eval(unit).movePointLeft(2)
    }

    data class Unary(
        val op: Char,
        val operand: Node,
    ) : Node {
        override fun eval(unit: AngleUnit): BigDecimal {
            val x = operand.eval(unit)
            return when (op) {
                Symbols.FACTORIAL -> BigMath.factorial(x)
                Symbols.SQRT -> BigMath.sqrt(x)
                else -> error("unknown unary $op")
            }
        }
    }

    data class Binary(
        val op: Char,
        val left: Node,
        val right: Node,
    ) : Node {
        override fun eval(unit: AngleUnit): BigDecimal {
            val a = left.eval(unit)
            // "50+10%" means 50 + 10% of 50.
            if ((op == '+' || op == Symbols.MINUS) && right is Percent) {
                val share = a.multiply(right.operand.eval(unit), BigMath.MC).movePointLeft(2)
                return BigMath.checkRange(if (op == '+') a.add(share, BigMath.MC) else a.subtract(share, BigMath.MC))
            }
            val b = right.eval(unit)
            val result =
                when (op) {
                    '+' -> {
                        a.add(b, BigMath.MC)
                    }

                    Symbols.MINUS -> {
                        a.subtract(b, BigMath.MC)
                    }

                    Symbols.TIMES -> {
                        a.multiply(b, BigMath.MC)
                    }

                    Symbols.DIVIDE -> {
                        if (b.signum() == 0) throw CalcException(CalcError.DivideByZero)
                        a.divide(b, BigMath.MC)
                    }

                    '^' -> {
                        BigMath.pow(a, b)
                    }

                    else -> {
                        error("unknown binary $op")
                    }
                }
            return BigMath.checkRange(result)
        }
    }

    data class Call(
        val function: String,
        val argument: Node,
    ) : Node {
        override fun eval(unit: AngleUnit): BigDecimal {
            val x = argument.eval(unit)
            val degrees = unit == AngleUnit.DEGREES
            val result =
                when (function) {
                    Symbols.SIN -> if (degrees) BigMath.sinDegrees(x) else BigMath.sin(x)
                    Symbols.COS -> if (degrees) BigMath.cosDegrees(x) else BigMath.cos(x)
                    Symbols.TAN -> if (degrees) BigMath.tanDegrees(x) else BigMath.tan(x)
                    Symbols.ASIN -> inverseTrig(BigMath.asin(x), degrees)
                    Symbols.ACOS -> inverseTrig(BigMath.acos(x), degrees)
                    Symbols.ATAN -> inverseTrig(BigMath.atan(x), degrees)
                    Symbols.LN -> BigMath.ln(x)
                    Symbols.LOG -> BigMath.log10(x)
                    else -> error("unknown function $function")
                }
            return BigMath.checkRange(result)
        }

        private fun inverseTrig(
            radians: BigDecimal,
            degrees: Boolean,
        ) = if (degrees) BigMath.toDegrees(radians) else radians
    }
}

// ------------------------------------------------------------------------- parser

/** Recursive-descent parser; precedence levels are documented in docs/calculator.md. */
internal class Parser(
    private val tokens: List<Token>,
) {
    private var pos = 0

    fun parseAll(): Node {
        if (tokens.isEmpty()) throw CalcException(CalcError.Syntax)
        val node = parseAdditive()
        if (pos != tokens.size) throw CalcException(CalcError.Syntax)
        return node
    }

    private fun peek(): Token? = tokens.getOrNull(pos)

    private fun isOp(
        token: Token?,
        vararg symbols: Char,
    ) = token is Token.Op && token.symbol in symbols

    private fun parseAdditive(): Node {
        var left = parseMultiplicative()
        while (isOp(peek(), '+', Symbols.MINUS)) {
            val op = (tokens[pos++] as Token.Op).symbol
            left = Node.Binary(op, left, parseMultiplicative())
        }
        return left
    }

    private fun parseMultiplicative(): Node {
        var left = parsePrefix()
        while (true) {
            val next = peek()
            left =
                when {
                    isOp(next, Symbols.TIMES, Symbols.DIVIDE) -> {
                        pos++
                        Node.Binary((next as Token.Op).symbol, left, parsePrefix())
                    }

                    startsOperand(next) -> {
                        Node.Binary(Symbols.TIMES, left, parsePrefix())
                    }

                    // implicit ×
                    else -> {
                        return left
                    }
                }
        }
    }

    /** Tokens that can start an operand, for implicit multiplication. A sign can't. */
    private fun startsOperand(token: Token?) =
        token is Token.Number ||
            token is Token.Constant ||
            token is Token.Function ||
            token == Token.LParen ||
            isOp(token, Symbols.SQRT)

    private fun parsePrefix(): Node {
        val next = peek()
        return when {
            isOp(next, Symbols.MINUS) -> {
                pos++
                Node.Negate(parsePrefix())
            }

            isOp(next, '+') -> {
                pos++
                parsePrefix()
            }

            isOp(next, Symbols.SQRT) -> {
                pos++
                Node.Unary(Symbols.SQRT, parsePrefix())
            }

            else -> {
                parsePower()
            }
        }
    }

    private fun parsePower(): Node {
        val base = parsePostfix()
        if (isOp(peek(), '^')) {
            pos++
            return Node.Binary('^', base, parsePrefix()) // right-associative, may carry a sign
        }
        return base
    }

    private fun parsePostfix(): Node {
        var node = parsePrimary()
        while (true) {
            node =
                when {
                    isOp(peek(), Symbols.FACTORIAL) -> {
                        pos++
                        Node.Unary(Symbols.FACTORIAL, node)
                    }

                    isOp(peek(), Symbols.PERCENT) -> {
                        pos++
                        Node.Percent(node)
                    }

                    else -> {
                        return node
                    }
                }
        }
    }

    private fun parsePrimary(): Node {
        val token = peek() ?: throw CalcException(CalcError.Syntax)
        pos++
        return when (token) {
            is Token.Number -> {
                Node.Literal(token.value)
            }

            is Token.Constant -> {
                Node.Literal(token.value)
            }

            Token.LParen -> {
                parseGroupBody()
            }

            is Token.Function -> {
                Node.Call(token.name, parseGroupBody())
            }

            else -> {
                throw CalcException(CalcError.Syntax)
            }
        }
    }

    /** Contents of a parenthesised group whose "(" was consumed. A missing ")" at the end is implied. */
    private fun parseGroupBody(): Node {
        val inner = parseAdditive()
        when (peek()) {
            Token.RParen -> pos++

            null -> Unit

            // auto-close at end of input
            else -> throw CalcException(CalcError.Syntax)
        }
        return inner
    }
}
