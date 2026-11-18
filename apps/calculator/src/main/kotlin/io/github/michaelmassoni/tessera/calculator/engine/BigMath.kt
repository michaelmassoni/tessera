package io.github.michaelmassoni.tessera.calculator.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

/**
 * Arbitrary-precision math on [BigDecimal]. Every function computes to [WORKING_DIGITS]
 * significant digits so that the final result, rounded to [RESULT_DIGITS], is correct.
 *
 * `Double` is used only for initial guesses and range-reduction choices, never for a result.
 */
internal object BigMath {
    const val WORKING_DIGITS = 60
    const val RESULT_DIGITS = 32

    /** Results whose decimal exponent reaches this are reported as too large. */
    const val MAX_EXPONENT = 10_000

    val MC: MathContext = MathContext(WORKING_DIGITS, RoundingMode.HALF_EVEN)

    /** Trig results smaller than this are snapped to exactly zero (sin(π) = 0). */
    private val SNAP_TO_ZERO: BigDecimal = BigDecimal.ONE.scaleByPowerOfTen(-45)

    private val TWO = BigDecimal(2)
    private val HALF = BigDecimal("0.5")
    private val EPSILON: BigDecimal = BigDecimal.ONE.scaleByPowerOfTen(-(WORKING_DIGITS + 5))

    val PI: BigDecimal by lazy { pi(WORKING_DIGITS + 10) }
    val E: BigDecimal by lazy { exp(BigDecimal.ONE) }
    private val LN2: BigDecimal by lazy { atanhSeries(BigDecimal.ONE.divide(BigDecimal(3), MC)).multiply(TWO, MC) }
    private val LN10: BigDecimal by lazy {
        // ln 10 = 3 ln 2 + ln 1.25
        LN2.multiply(BigDecimal(3), MC).add(lnNearOne(BigDecimal("1.25")), MC)
    }

    /** Decimal exponent of [x]: x = m × 10^e with 1 ≤ |m| < 10. Undefined for zero. */
    fun exponentOf(x: BigDecimal): Int = x.precision() - x.scale() - 1

    fun checkRange(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        val exponent = exponentOf(x)
        if (exponent >= MAX_EXPONENT) throw CalcException(CalcError.Overflow)
        if (exponent <= -MAX_EXPONENT) return BigDecimal.ZERO
        return x
    }

    // ---------------------------------------------------------------- roots and powers

    fun sqrt(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) throw CalcException(CalcError.Domain)
        if (x.signum() == 0) return BigDecimal.ZERO
        // Initial guess from the decimal exponent, so huge and tiny values work too.
        val exponent = exponentOf(x)
        val halfExponent = Math.floorDiv(exponent, 2)
        val mantissa = x.scaleByPowerOfTen(-2 * halfExponent).toDouble()
        var guess = BigDecimal(Math.sqrt(mantissa)).scaleByPowerOfTen(halfExponent)
        repeat(200) {
            val next = guess.add(x.divide(guess, MC), MC).multiply(HALF, MC)
            if (next.compareTo(guess) == 0) return next
            guess = next
        }
        return guess
    }

    fun pow(
        base: BigDecimal,
        exponent: BigDecimal,
    ): BigDecimal {
        val integerExponent = exponent.toIntegerOrNull()
        if (integerExponent != null && integerExponent.abs() <= BigInteger.valueOf(999_999)) {
            return integerPow(base, integerExponent.toInt())
        }
        return when (base.signum()) {
            0 -> if (exponent.signum() > 0) BigDecimal.ZERO else throw CalcException(CalcError.DivideByZero)
            -1 -> throw CalcException(CalcError.Domain)
            else -> exp(exponent.multiply(ln(base), MC))
        }
    }

    private fun integerPow(
        base: BigDecimal,
        n: Int,
    ): BigDecimal {
        if (n == 0) return BigDecimal.ONE
        if (base.signum() == 0) {
            if (n < 0) throw CalcException(CalcError.DivideByZero)
            return BigDecimal.ZERO
        }
        // Estimate the size of the result before computing it.
        val magnitude = (exponentOf(base) + log10OfMantissa(base)) * n
        if (magnitude >= MAX_EXPONENT + 1) throw CalcException(CalcError.Overflow)
        if (magnitude <= -MAX_EXPONENT - 1) return BigDecimal.ZERO
        return checkRange(base.pow(n, MC))
    }

    private fun log10OfMantissa(x: BigDecimal): Double = Math.log10(x.abs().scaleByPowerOfTen(-exponentOf(x)).toDouble())

    fun factorial(x: BigDecimal): BigDecimal {
        val n = x.toIntegerOrNull() ?: throw CalcException(CalcError.Domain)
        if (n.signum() < 0) throw CalcException(CalcError.Domain)
        if (n > BigInteger.valueOf(MAX_FACTORIAL.toLong())) throw CalcException(CalcError.Overflow)
        var result = BigDecimal.ONE
        for (i in 2..n.toInt()) result = result.multiply(BigDecimal(i), MC)
        return checkRange(result)
    }

    /** 3249! is the first factorial ≥ 1e10000. */
    const val MAX_FACTORIAL = 3248

    // ---------------------------------------------------------------- exp and logarithms

    fun exp(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ONE
        // e^23026 ≈ 1e10000
        if (x > BigDecimal(23_027)) throw CalcException(CalcError.Overflow)
        if (x < BigDecimal(-23_027)) return BigDecimal.ZERO
        // Halve the argument until it's small, then square the result back up.
        var reduced = x
        var halvings = 0
        while (reduced.abs() > HALF) {
            reduced = reduced.divide(TWO, MC)
            halvings++
        }
        val extra = MathContext(WORKING_DIGITS + halvings, RoundingMode.HALF_EVEN)
        var sum = BigDecimal.ONE
        var term = BigDecimal.ONE
        var k = 1
        while (true) {
            term = term.multiply(reduced, extra).divide(BigDecimal(k), extra)
            if (term.abs() < EPSILON) break
            sum = sum.add(term, extra)
            k++
        }
        repeat(halvings) { sum = sum.multiply(sum, extra) }
        return checkRange(sum.round(MC))
    }

    fun ln(x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw CalcException(CalcError.Domain)
        if (x.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ZERO
        // x = m × 10^d with m in [1, 10), then m = r × 2^k with r near 1.
        val d = exponentOf(x)
        val m = x.scaleByPowerOfTen(-d)
        val k = Math.round(Math.log(m.toDouble()) / Math.log(2.0)).toInt()
        val r = m.divide(TWO.pow(k), MC)
        return lnNearOne(r)
            .add(LN2.multiply(BigDecimal(k), MC), MC)
            .add(LN10.multiply(BigDecimal(d), MC), MC)
    }

    fun log10(x: BigDecimal): BigDecimal {
        if (x.signum() <= 0) throw CalcException(CalcError.Domain)
        // Exact for powers of ten.
        val stripped = x.stripTrailingZeros()
        if (stripped.unscaledValue() == BigInteger.ONE) return BigDecimal(-stripped.scale())
        return ln(x).divide(LN10, MC)
    }

    /** ln(r) for r near 1, via ln(r) = 2·atanh((r−1)/(r+1)). */
    private fun lnNearOne(r: BigDecimal): BigDecimal {
        val z = r.subtract(BigDecimal.ONE).divide(r.add(BigDecimal.ONE), MC)
        return atanhSeries(z).multiply(TWO, MC)
    }

    private fun atanhSeries(z: BigDecimal): BigDecimal {
        val z2 = z.multiply(z, MC)
        var power = z
        var sum = z
        var k = 3
        while (true) {
            power = power.multiply(z2, MC)
            val term = power.divide(BigDecimal(k), MC)
            if (term.abs() < EPSILON) break
            sum = sum.add(term, MC)
            k += 2
        }
        return sum
    }

    // ---------------------------------------------------------------- trigonometry

    /** π to [digits] significant digits (Machin's formula). */
    fun pi(digits: Int): BigDecimal {
        val mc = MathContext(digits + 5, RoundingMode.HALF_EVEN)
        val a = atanInverse(5, mc).multiply(BigDecimal(16), mc)
        val b = atanInverse(239, mc).multiply(BigDecimal(4), mc)
        return a.subtract(b, mc).round(MathContext(digits, RoundingMode.HALF_EVEN))
    }

    /** atan(1/n) by its Taylor series. */
    private fun atanInverse(
        n: Int,
        mc: MathContext,
    ): BigDecimal {
        val epsilon = BigDecimal.ONE.scaleByPowerOfTen(-(mc.precision + 2))
        val x = BigDecimal.ONE.divide(BigDecimal(n), mc)
        val x2 = x.multiply(x, mc)
        var power = x
        var sum = x
        var k = 3
        var sign = -1
        while (true) {
            power = power.multiply(x2, mc)
            val term = power.divide(BigDecimal(k), mc)
            if (term < epsilon) break
            sum = if (sign < 0) sum.subtract(term, mc) else sum.add(term, mc)
            sign = -sign
            k += 2
        }
        return sum
    }

    fun sin(radians: BigDecimal): BigDecimal = snap(sinCos(radians).first)

    fun cos(radians: BigDecimal): BigDecimal = snap(sinCos(radians).second)

    fun tan(radians: BigDecimal): BigDecimal {
        val (s, c) = sinCos(radians)
        val cosine = snap(c)
        if (cosine.signum() == 0) throw CalcException(CalcError.Domain)
        return snap(s).divide(cosine, MC)
    }

    /** Degree-mode trig: whole multiples of 90° are exact, everything else goes via radians. */
    fun sinDegrees(degrees: BigDecimal): BigDecimal = quadrantExact(degrees, isSine = true) ?: sin(toRadians(degrees))

    fun cosDegrees(degrees: BigDecimal): BigDecimal = quadrantExact(degrees, isSine = false) ?: cos(toRadians(degrees))

    fun tanDegrees(degrees: BigDecimal): BigDecimal {
        val reduced = degrees.remainder(BigDecimal(180))
        if (reduced.signum() == 0) return BigDecimal.ZERO
        if (reduced.abs().compareTo(BigDecimal(90)) == 0) throw CalcException(CalcError.Domain)
        return tan(toRadians(degrees))
    }

    fun toRadians(degrees: BigDecimal): BigDecimal = degrees.remainder(BigDecimal(360)).multiply(PI, MC).divide(BigDecimal(180), MC)

    fun toDegrees(radians: BigDecimal): BigDecimal = radians.multiply(BigDecimal(180), MC).divide(PI, MC)

    private fun quadrantExact(
        degrees: BigDecimal,
        isSine: Boolean,
    ): BigDecimal? {
        val ninety = BigDecimal(90)
        if (degrees.remainder(ninety).signum() != 0) return null
        val quadrant =
            degrees
                .divide(ninety)
                .toBigInteger()
                .mod(BigInteger.valueOf(4))
                .toInt()
        val values = if (isSine) intArrayOf(0, 1, 0, -1) else intArrayOf(1, 0, -1, 0)
        return BigDecimal(values[quadrant])
    }

    private fun sinCos(x: BigDecimal): Pair<BigDecimal, BigDecimal> {
        // Reduce modulo 2π. Large arguments need π to more digits so the reduction stays exact.
        val extraDigits = maxOf(0, exponentOf(x.abs().max(BigDecimal.ONE)))
        if (extraDigits > 1000) throw CalcException(CalcError.Overflow)
        val mc = MathContext(WORKING_DIGITS + extraDigits + 10, RoundingMode.HALF_EVEN)
        val twoPi = (if (extraDigits == 0) PI else pi(mc.precision)).multiply(TWO, mc)
        var r = x.remainder(twoPi, mc)
        val piHere = twoPi.divide(TWO, mc)
        if (r > piHere) r = r.subtract(twoPi, mc)
        if (r < piHere.negate()) r = r.add(twoPi, mc)

        val r2 = r.multiply(r, MC)
        var sinTerm = r.round(MC)
        var cosTerm = BigDecimal.ONE
        var sin = sinTerm
        var cos = cosTerm
        var k = 1
        while (true) {
            sinTerm = sinTerm.multiply(r2, MC).divide(BigDecimal((2 * k) * (2 * k + 1)), MC).negate()
            cosTerm = cosTerm.multiply(r2, MC).divide(BigDecimal((2 * k - 1) * (2 * k)), MC).negate()
            if (sinTerm.abs() < EPSILON && cosTerm.abs() < EPSILON) break
            sin = sin.add(sinTerm, MC)
            cos = cos.add(cosTerm, MC)
            k++
        }
        return sin to cos
    }

    private fun snap(x: BigDecimal): BigDecimal = if (x.abs() < SNAP_TO_ZERO) BigDecimal.ZERO else x

    fun atan(x: BigDecimal): BigDecimal {
        if (x.signum() == 0) return BigDecimal.ZERO
        // atan(x) = 2·atan(x / (1 + √(1 + x²))) until |x| is small.
        var reduced = x
        var doublings = 0
        val limit = BigDecimal("0.1")
        while (reduced.abs() > limit) {
            val root = sqrt(BigDecimal.ONE.add(reduced.multiply(reduced, MC), MC))
            reduced = reduced.divide(BigDecimal.ONE.add(root, MC), MC)
            doublings++
        }
        val x2 = reduced.multiply(reduced, MC)
        var power = reduced
        var sum = reduced
        var k = 3
        var sign = -1
        while (true) {
            power = power.multiply(x2, MC)
            val term = power.divide(BigDecimal(k), MC)
            if (term.abs() < EPSILON) break
            sum = if (sign < 0) sum.subtract(term, MC) else sum.add(term, MC)
            sign = -sign
            k += 2
        }
        return sum.multiply(TWO.pow(doublings), MC)
    }

    fun asin(x: BigDecimal): BigDecimal {
        val abs = x.abs()
        if (abs > BigDecimal.ONE) throw CalcException(CalcError.Domain)
        if (abs.compareTo(BigDecimal.ONE) == 0) return PI.divide(TWO, MC).multiply(BigDecimal(x.signum()))
        val root = sqrt(BigDecimal.ONE.subtract(x.multiply(x, MC), MC))
        return atan(x.divide(root, MC))
    }

    fun acos(x: BigDecimal): BigDecimal = PI.divide(TWO, MC).subtract(asin(x), MC)

    private fun BigDecimal.toIntegerOrNull(): BigInteger? {
        val stripped = stripTrailingZeros()
        return if (stripped.scale() <= 0) stripped.toBigIntegerExact() else null
    }
}
