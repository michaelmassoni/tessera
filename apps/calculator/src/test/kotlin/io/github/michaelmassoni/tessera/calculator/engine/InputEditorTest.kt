package io.github.michaelmassoni.tessera.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputEditorTest {
    /** Types a sequence of inputs from an empty (or given) state and returns the expression. */
    private fun type(
        vararg inputs: Input,
        from: InputState = InputState(),
    ): String = inputs.fold(from) { s, i -> InputEditor.apply(s, i) }.expression

    private fun d(c: Char) = Input.Digit(c)

    private fun op(c: Char) = Input.BinaryOperator(c)

    private val minus = op(Symbols.MINUS)
    private val times = op(Symbols.TIMES)
    private val plus = op('+')

    @Test fun `digits and decimals`() {
        assertEquals("12.5", type(d('1'), d('2'), Input.Decimal, d('5')))
        assertEquals("0.5", type(Input.Decimal, d('5')))
        assertEquals("1.5", type(d('1'), Input.Decimal, Input.Decimal, d('5')))
        assertEquals("2+0.", type(d('2'), plus, Input.Decimal))
    }

    @Test fun `leading zero is replaced but not after a decimal point`() {
        assertEquals("7", type(d('0'), d('7')))
        assertEquals("0", type(d('0'), d('0')))
        assertEquals("0.07", type(d('0'), Input.Decimal, d('0'), d('7')))
        assertEquals("10", type(d('1'), d('0')))
        assertEquals("5+7", type(d('5'), plus, d('0'), d('7')))
    }

    @Test fun `operators on empty input`() {
        assertEquals("", type(plus))
        assertEquals("", type(times))
        assertEquals("−", type(minus))
        assertEquals("−5", type(minus, d('5')))
    }

    @Test fun `second operator replaces the first except a sign after multiply`() {
        assertEquals("5×", type(d('5'), plus, times))
        assertEquals("5×−", type(d('5'), times, minus))
        assertEquals("5+", type(d('5'), times, minus, plus))
        assertEquals("5−", type(d('5'), plus, minus))
        assertEquals("2^−", type(d('2'), op(Symbols.POWER), minus))
    }

    @Test fun `operator after open parenthesis only accepts a sign`() {
        assertEquals("(", type(Input.OpenParen, plus))
        assertEquals("(−", type(Input.OpenParen, minus))
    }

    @Test fun `smart parenthesis`() {
        assertEquals("(", type(Input.Parenthesis))
        assertEquals("(2", type(Input.Parenthesis, d('2')))
        assertEquals("(2)", type(Input.Parenthesis, d('2'), Input.Parenthesis))
        assertEquals("(2+(", type(Input.Parenthesis, d('2'), plus, Input.Parenthesis))
        assertEquals("2(", type(d('2'), Input.Parenthesis))
        assertEquals("sin(30)", type(Input.Function(Symbols.SIN), d('3'), d('0'), Input.Parenthesis))
    }

    @Test fun `close parenthesis needs an open one and an operand`() {
        assertEquals("2", type(d('2'), Input.CloseParen))
        assertEquals("(2+", type(Input.OpenParen, d('2'), plus, Input.CloseParen))
    }

    @Test fun `postfix needs an operand`() {
        assertEquals("", type(Input.Postfix(Symbols.PERCENT)))
        assertEquals("5%", type(d('5'), Input.Postfix(Symbols.PERCENT)))
        assertEquals("5+", type(d('5'), plus, Input.Postfix(Symbols.FACTORIAL)))
        assertEquals("3!", type(d('3'), Input.Postfix(Symbols.FACTORIAL)))
    }

    @Test fun `backspace removes whole function names`() {
        assertEquals("2×", type(d('2'), times, Input.Function(Symbols.ASIN), Input.Backspace))
        assertEquals("1", type(d('1'), d('2'), Input.Backspace))
        assertEquals("", type(Input.Backspace))
    }

    @Test fun `clear empties everything`() {
        assertEquals("", type(d('1'), plus, d('2'), Input.Clear))
    }

    @Test fun `after equals a digit starts over and an operator continues`() {
        val result = InputState("42", justEvaluated = true)
        assertEquals("7", type(d('7'), from = result))
        assertEquals("42+", type(plus, from = result))
        assertEquals("42!", type(Input.Postfix(Symbols.FACTORIAL), from = result))
        assertEquals("π", type(Input.Constant(Symbols.PI), from = result))
        assertEquals("0.", type(Input.Decimal, from = result))
        assertEquals("4", type(Input.Backspace, from = result))
    }

    @Test fun `negative result is parenthesised before power, square and postfix`() {
        val result = InputState("−5", justEvaluated = true)
        assertEquals("(−5)^2", type(Input.Square, from = result))
        assertEquals("(−5)^", type(op(Symbols.POWER), from = result))
        assertEquals("(−5)!", type(Input.Postfix(Symbols.FACTORIAL), from = result))
        assertEquals("−5×", type(times, from = result))
        assertEquals(
            "25",
            Evaluator.evaluate(type(Input.Square, from = result)).let {
                (it as EvalResult.Value).value.toPlainString()
            },
        )
    }

    @Test fun `ten power gets an explicit multiply after a digit`() {
        assertEquals("5×10^(", type(d('5'), Input.TenPower))
        assertEquals("e^(", type(Input.ExpPower))
        assertEquals("5^2", type(d('5'), Input.Square))
        assertEquals("5^−1", type(d('5'), Input.Reciprocal))
        assertEquals("", type(Input.Reciprocal))
    }

    @Test fun `scientific notation from a result can be edited`() {
        val result = InputState("1.5E−7", justEvaluated = false)
        assertEquals("1.5E−7", type(Input.Decimal, from = result)) // number already has a point
        assertEquals("1.5E−", type(Input.Backspace, from = result))
        assertEquals("1.5E−7×", type(times, from = result))
    }

    @Test fun `expression length is capped`() {
        val long = "1".repeat(InputEditor.MAX_LENGTH)
        assertEquals(long, type(d('2'), from = InputState(long)))
        assertEquals(long.dropLast(1), type(Input.Backspace, from = InputState(long)))
    }

    @Test fun `insert from history`() {
        assertEquals("0.3", InputEditor.insert(InputState(), "0.3", asGroup = false).expression)
        assertEquals("2+0.3", InputEditor.insert(InputState("2+"), "0.3", asGroup = false).expression)
        assertEquals("2×0.3", InputEditor.insert(InputState("2"), "0.3", asGroup = false).expression)
        assertEquals("2+(0.1+0.2)", InputEditor.insert(InputState("2+"), "0.1+0.2", asGroup = true).expression)
        assertEquals("0.1+0.2", InputEditor.insert(InputState(), "0.1+0.2", asGroup = true).expression)
        assertEquals("7", InputEditor.insert(InputState("42", justEvaluated = true), "7", asGroup = false).expression)
    }

    @Test fun `preview is only useful when there is an operation`() {
        assertFalse(InputEditor.hasOperation(""))
        assertFalse(InputEditor.hasOperation("42"))
        assertFalse(InputEditor.hasOperation("−42.5"))
        assertFalse(InputEditor.hasOperation("1.5E−7"))
        assertFalse(InputEditor.hasOperation(".5"))
        assertTrue(InputEditor.hasOperation("2+3"))
        assertTrue(InputEditor.hasOperation("π"))
        assertTrue(InputEditor.hasOperation("5%"))
        assertTrue(InputEditor.hasOperation("−−5"))
        assertTrue(InputEditor.hasOperation("sin(30"))
    }
}
