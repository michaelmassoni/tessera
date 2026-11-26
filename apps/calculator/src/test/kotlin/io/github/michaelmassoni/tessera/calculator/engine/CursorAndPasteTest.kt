package io.github.michaelmassoni.tessera.calculator.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CursorAndPasteTest {
    private fun at(
        expression: String,
        cursor: Int,
    ) = InputState(expression, cursor = cursor)

    private val m = Symbols.MINUS
    private val x = Symbols.TIMES

    @Test
    fun `input goes at the cursor and the cursor follows it`() {
        val s = InputEditor.apply(at("12+5", 2), Input.Digit('3'))
        assertEquals("123+5", s.expression)
        assertEquals(3, s.cursor)
        assertEquals(InputState("12$x+5", cursor = 3), InputEditor.apply(at("12+5", 2), Input.BinaryOperator(x)))
        assertEquals(InputState("sin(1+5", cursor = 4), InputEditor.apply(at("1+5", 0), Input.Function(Symbols.SIN)))
    }

    @Test
    fun `backspace deletes before the cursor`() {
        assertEquals(InputState("13", cursor = 1), InputEditor.apply(at("123", 2), Input.Backspace))
        assertEquals(InputState("3", cursor = 0), InputEditor.apply(at("sin(3", 4), Input.Backspace))
        // Nothing before it: nothing happens.
        assertEquals(InputState("123", cursor = 0), InputEditor.apply(at("123", 0), Input.Backspace))
    }

    @Test
    fun `one decimal point per number, counting after the cursor`() {
        assertEquals("12.34", InputEditor.apply(at("1234", 2), Input.Decimal).expression)
        assertEquals("12.5", InputEditor.apply(at("12.5", 2), Input.Decimal).expression)
        assertEquals("1.25", InputEditor.apply(at("1.25", 3), Input.Decimal).expression)
    }

    @Test
    fun `clear clears everything and a cursor at the end is the usual end`() {
        assertEquals(InputState(), InputEditor.apply(at("12+5", 2), Input.Clear))
        assertEquals("12+56", InputEditor.apply(InputState("12+5", cursor = 4), Input.Digit('6')).expression)
    }

    @Test
    fun `paste normalises numbers and expressions`() {
        assertEquals("${m}1234.5", InputEditor.normalizePaste(" -1,234.5 ", '.', ','))
        assertEquals("1234.5", InputEditor.normalizePaste("1.234,5", ',', '.'))
        assertEquals("1234.5", InputEditor.normalizePaste("1 234,5", ',', ' '))
        assertEquals("3${x}4÷2", InputEditor.normalizePaste("3*4/2", '.', ','))
        assertEquals("sin(30)", InputEditor.normalizePaste("sin(30)", '.', ','))
        assertNull(InputEditor.normalizePaste("hello", '.', ','))
        assertNull(InputEditor.normalizePaste("", '.', ','))
    }

    @Test
    fun `paste goes at the cursor, or replaces a result`() {
        assertEquals(InputState("1+250", cursor = null), InputEditor.paste(InputState("1+"), "250", '.', ','))
        assertEquals(InputState("1+250+3", cursor = 5), InputEditor.paste(at("1++3", 2), "250", '.', ','))
        assertEquals(InputState("42"), InputEditor.paste(InputState("7", justEvaluated = true), "42", '.', ','))
        // After a number, a pasted number multiplies rather than joining digits.
        assertEquals("5${x}42", InputEditor.paste(InputState("5"), "42", '.', ',')!!.expression)
        assertNull(InputEditor.paste(InputState("5"), "abc", '.', ','))
    }

    @Test
    fun `display offsets skip grouping separators`() {
        val f = DisplayFormatter('.', ',')
        // "12345+6" shows as "12,345+6".
        assertArrayEquals(intArrayOf(0, 1, 3, 4, 5, 6, 7, 8), f.offsets("12345+6"))
        assertEquals(2, f.rawOffset("12345+6", 3))
        assertEquals(7, f.rawOffset("12345+6", 99))
        // German: "1234.5" shows as "1.234,5" — the "." is grouping, the "," is the decimal point.
        val de = DisplayFormatter(',', '.')
        assertArrayEquals(intArrayOf(0, 2, 3, 4, 5, 6, 7), de.offsets("1234.5"))
    }
}
