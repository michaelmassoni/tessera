package io.github.michaelmassoni.tessera.calculator.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class EvaluatorTest {
    private fun eval(
        expression: String,
        unit: AngleUnit = AngleUnit.RADIANS,
    ): String =
        when (val r = Evaluator.evaluate(expression, unit)) {
            is EvalResult.Value -> r.value.toPlainStringOrSci()
            is EvalResult.Error -> "Error:${r.error}"
        }

    private fun BigDecimal.toPlainStringOrSci(): String =
        if (BigMath.exponentOf(abs().max(BigDecimal.ONE)) >
            40
        ) {
            toString()
        } else {
            toPlainString()
        }

    private fun deg(expression: String) = eval(expression, AngleUnit.DEGREES)

    /** Compares the first [digits] significant digits. */
    private fun assertApprox(
        expected: String,
        expression: String,
        unit: AngleUnit = AngleUnit.RADIANS,
        digits: Int = 30,
    ) {
        val r = Evaluator.evaluate(expression, unit)
        assertTrue("$expression gave $r", r is EvalResult.Value)
        val actual = (r as EvalResult.Value).value
        val mc = java.math.MathContext(digits)
        assertEquals(
            "$expression",
            BigDecimal(expected).round(mc).stripTrailingZeros(),
            actual.round(mc).stripTrailingZeros(),
        )
    }

    // ------------------------------------------------------------- exact decimal arithmetic

    @Test fun `no floating point artifacts`() {
        assertEquals("0.3", eval("0.1+0.2"))
        assertEquals("0.1", eval("0.3−0.2"))
        assertEquals("0.06", eval("0.2×0.3"))
        assertEquals("3.3", eval("1.1×3"))
        assertEquals("1", eval("0.1×10"))
        assertEquals("0", eval("0.1+0.2−0.3"))
        assertEquals("100.01", eval("100+0.01"))
    }

    @Test fun `division rounds to 32 significant digits and recovers integers`() {
        assertEquals("0.33333333333333333333333333333333", eval("1÷3"))
        assertEquals("1", eval("1÷3×3"))
        assertEquals("0.66666666666666666666666666666667", eval("2÷3"))
        assertEquals("2", eval("√2×√2"))
        assertEquals("2", eval("√2^2"))
    }

    @Test fun `large exact integers`() {
        assertEquals("12345678901234567890", eval("12345678901234567890×1"))
        assertEquals("1000000000000000000000000", eval("10^24"))
    }

    // ------------------------------------------------------------- precedence

    @Test fun precedence() {
        assertEquals("14", eval("2+3×4"))
        assertEquals("20", eval("(2+3)×4"))
        assertEquals("2", eval("8÷2÷2"))
        assertEquals("1", eval("5−3−1"))
        assertEquals("512", eval("2^3^2"))
        assertEquals("-4", eval("−2^2"))
        assertEquals("4", eval("(−2)^2"))
        assertEquals("0.5", eval("2^−1"))
        assertEquals("-6", eval("2×−3"))
        assertEquals("5", eval("−−5"))
        assertEquals("3", eval("+3"))
    }

    @Test fun `implicit multiplication`() {
        assertEquals("6", eval("2(3)"))
        assertEquals("6", eval("(2)(3)"))
        assertEquals("6", eval("(2)3"))
        assertApprox("6.283185307179586476925286766559", "2π")
        assertApprox("9.869604401089358618834490999876", "ππ")
        assertEquals("1", eval("2sin(30)", AngleUnit.DEGREES))
        assertEquals("6", eval("3√4"))
    }

    @Test fun `unclosed parentheses are closed automatically`() {
        assertEquals("5", eval("(2+3"))
        assertEquals("10", eval("2×(1+(2+2"))
        assertEquals("1", deg("sin(90"))
    }

    // ------------------------------------------------------------- percent

    @Test fun percent() {
        assertEquals("0.5", eval("50%"))
        assertEquals("55", eval("50+10%"))
        assertEquals("45", eval("50−10%"))
        assertEquals("5", eval("50×10%"))
        assertEquals("500", eval("50÷10%"))
        assertEquals("0.0001", eval("1%%"))
        assertEquals("110", eval("(50+50)+10%"))
    }

    // ------------------------------------------------------------- functions

    @Test fun `square roots`() {
        assertEquals("3", eval("√9"))
        assertEquals("1.4142135623730950488016887242097", eval("√2"))
        assertEquals("4", eval("√(10+6)"))
        assertEquals("Error:Domain", eval("√−1"))
        assertEquals("1E+100", eval("√1E200"))
    }

    @Test fun `powers`() {
        assertEquals("1024", eval("2^10"))
        assertEquals("1", eval("0^0"))
        assertEquals("Error:DivideByZero", eval("0^−1"))
        assertApprox("1.4142135623730950488016887242097", "2^0.5")
        assertEquals("Error:Domain", eval("(−8)^0.5"))
        assertEquals("-8", eval("(−2)^3"))
        assertEquals("0.001", eval("10^−3"))
    }

    @Test fun factorial() {
        assertEquals("1", eval("0!"))
        assertEquals("120", eval("5!"))
        assertEquals("3628800", eval("10!"))
        assertEquals("720", eval("3!!"))
        assertEquals("Error:Domain", eval("2.5!"))
        assertEquals("Error:Domain", eval("(−1)!"))
        assertEquals("Error:Overflow", eval("4000!"))
        assertApprox("9.3326215443944152681699238856267E+157", "100!")
    }

    @Test fun `logarithms and exponentials`() {
        assertEquals("1", eval("ln(e)"))
        assertEquals("0", eval("ln(1)"))
        assertEquals("3", eval("log(1000)"))
        assertEquals("-2", eval("log(0.01)"))
        assertApprox("0.30102999566398119521373889472449", "log(2)")
        assertApprox("2.3025850929940456840179914546844", "ln(10)")
        assertApprox("7.3890560989306502272304274605750", "e^2")
        assertApprox("2.7182818284590452353602874713527", "e")
        assertEquals("Error:Domain", eval("ln(0)"))
        assertEquals("Error:Domain", eval("log(−5)"))
        assertApprox(
            BigDecimal("2.302585092994045684017991454684364").multiply(BigDecimal(9999)).toPlainString(),
            "ln(1E9999)",
            digits = 30,
        )
    }

    @Test fun `trigonometry in degrees`() {
        assertEquals("0", deg("sin(0)"))
        assertEquals("1", deg("sin(90)"))
        assertEquals("0", deg("sin(180)"))
        assertEquals("-1", deg("sin(270)"))
        assertEquals("0", deg("sin(360)"))
        assertEquals("0.5", deg("sin(30)"))
        assertEquals("0.5", deg("cos(60)"))
        assertEquals("0", deg("cos(90)"))
        assertEquals("-1", deg("cos(180)"))
        assertEquals("1", deg("tan(45)"))
        assertEquals("0", deg("tan(180)"))
        assertEquals("Error:Domain", deg("tan(90)"))
        assertEquals("Error:Domain", deg("tan(−270)"))
        assertEquals("0", deg("sin(−720)"))
        assertEquals("1", deg("sin(450)"))
    }

    @Test fun `trigonometry in radians`() {
        assertEquals("0", eval("sin(π)"))
        assertEquals("-1", eval("cos(π)"))
        assertEquals("1", eval("sin(π÷2)"))
        assertEquals("0", eval("sin(2π)"))
        assertEquals("0", eval("tan(π)"))
        assertEquals("Error:Domain", eval("tan(π÷2)"))
        assertApprox("0.84147098480789650665250232163030", "sin(1)")
        assertApprox("0.54030230586813971740093660744298", "cos(1)")
        assertApprox("1.5574077246549022305069748074584", "tan(1)")
        // Large arguments are reduced with enough digits of π.
        assertApprox(Math.sin(1e10).toString(), "sin(1E10)", digits = 14)
    }

    @Test fun `inverse trigonometry`() {
        assertEquals("90", deg("sin⁻¹(1)"))
        assertEquals("30", deg("sin⁻¹(0.5)"))
        assertEquals("60", deg("cos⁻¹(0.5)"))
        assertEquals("45", deg("tan⁻¹(1)"))
        assertEquals("180", deg("cos⁻¹(−1)"))
        assertApprox("1.5707963267948966192313216916398", "sin⁻¹(1)")
        assertApprox("0.78539816339744830961566084581988", "tan⁻¹(1)")
        assertEquals("Error:Domain", eval("sin⁻¹(2)"))
        assertApprox(Math.atan(100.0).toString(), "tan⁻¹(100)", digits = 15)
    }

    // ------------------------------------------------------------- errors and limits

    @Test fun `division by zero`() {
        assertEquals("Error:DivideByZero", eval("1÷0"))
        assertEquals("Error:DivideByZero", eval("0÷0"))
        assertEquals("Error:DivideByZero", eval("5÷(2−2)"))
    }

    @Test fun overflow() {
        assertEquals("Error:Overflow", eval("10^10000"))
        assertEquals("Error:Overflow", eval("1E9999×10"))
        assertEquals("Error:Overflow", eval("e^30000"))
        assertEquals("0", eval("10^−10001"))
        assertEquals("Error:Overflow", eval("9^9^9"))
    }

    @Test fun `syntax errors`() {
        listOf("", "+", "2+", "×2", "2××3", "()", "1..2", "2)", ".", "sin()", "√", "%", "abc").forEach {
            assertEquals(it, "Error:Syntax", eval(it))
        }
    }

    @Test fun `scientific notation input round-trips`() {
        assertEquals("150000000000000000000", eval("1.5E20"))
        assertEquals("0.00000015", eval("1.5E−7"))
        assertEquals("200000", eval("2E+5"))
        assertEquals("300000000000000000000", eval("1.5E20×2"))
    }

    @Test fun `numbers with leading or trailing decimal point`() {
        assertEquals("0.5", eval(".5"))
        assertEquals("5", eval("5."))
        assertEquals("5.5", eval("5.+.5"))
    }

    @Test fun `ascii operators are accepted for hardware keyboards and paste`() {
        assertEquals("6", eval("2*3"))
        assertEquals("2", eval("6/3"))
        assertEquals("-1", eval("2-3"))
    }
}
