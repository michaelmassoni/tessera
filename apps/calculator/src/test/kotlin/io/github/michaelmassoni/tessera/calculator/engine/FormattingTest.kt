package io.github.michaelmassoni.tessera.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class FormattingTest {
    private fun result(
        value: String,
        digits: Int = BigMath.RESULT_DIGITS,
    ) = ResultFormatter.format(BigDecimal(value), digits)

    @Test fun `plain results`() {
        assertEquals("0", result("0"))
        assertEquals("0", result("0.000"))
        assertEquals("42", result("42.000"))
        assertEquals("−42.5", result("-42.5"))
        assertEquals("0.000001", result("0.000001"))
        assertEquals("123456789012345678", result("123456789012345678"))
    }

    @Test fun `scientific notation for very large and very small results`() {
        assertEquals("1E−7", result("0.0000001"))
        assertEquals("1.5E−7", result("0.00000015"))
        assertEquals("1.234567890123457E20", result("123456789012345678901", ResultFormatter.PREVIEW_DIGITS))
        assertEquals("−1E100", result("-1E100"))
    }

    @Test fun `preview rounds to 16 significant digits`() {
        assertEquals("0.3333333333333333", result("0.33333333333333333333333333333333", ResultFormatter.PREVIEW_DIGITS))
        assertEquals("1", result("0.99999999999999999999", ResultFormatter.PREVIEW_DIGITS))
        // Rounding can carry into a new digit and change the notation.
        assertEquals("1E16", result("9999999999999999.9", ResultFormatter.PREVIEW_DIGITS))
    }

    @Test fun `formatted results evaluate back to the same value`() {
        listOf("0.1", "-7", "1E-7", "1.5E+300", "-2.5E-40", "123.456").forEach {
            val text = result(it)
            val back = (Evaluator.evaluate(text) as EvalResult.Value).value
            assertEquals(it, 0, BigDecimal(it).compareTo(back))
        }
    }

    @Test fun `shorten rounds stored results for display only`() {
        assertEquals("0.3333333333333333", ResultFormatter.shorten("0.33333333333333333333333333333333"))
        assertEquals("−42", ResultFormatter.shorten("−42"))
        assertEquals("2+3", ResultFormatter.shorten("2+3"))
        assertEquals("1.5E−7", ResultFormatter.shorten("1.5E−7"))
    }

    @Test fun `display formatting for US English`() {
        val us = DisplayFormatter(Locale.US)
        assertEquals("1,234,567.89", us.format("1234567.89"))
        assertEquals("123", us.format("123"))
        assertEquals("1,000+2,000", us.format("1000+2000"))
        assertEquals("1.5E10000", us.format("1.5E10000"))
        assertEquals("2.5E−12345", us.format("2.5E−12345"))
        assertEquals("sin(1,000)", us.format("sin(1000)"))
        assertEquals("0.12345678", us.format("0.12345678"))
    }

    @Test fun `display formatting for German`() {
        val de = DisplayFormatter(Locale.GERMANY)
        assertEquals("1.234.567,89", de.format("1234567.89"))
        assertEquals("0,5×2", de.format("0.5×2"))
    }

    @Test fun `display formatting for French uses its grouping space`() {
        val fr = DisplayFormatter(Locale.FRANCE)
        val grouping =
            java.text.DecimalFormatSymbols
                .getInstance(Locale.FRANCE)
                .groupingSeparator
        assertEquals("1${grouping}234,5", fr.format("1234.5"))
    }
}
