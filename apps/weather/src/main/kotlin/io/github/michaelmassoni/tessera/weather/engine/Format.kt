package io.github.michaelmassoni.tessera.weather.engine

import kotlin.math.roundToInt

/** Unit conversions from the metric the app stores. */
object Convert {
    fun temperature(
        celsius: Double,
        unit: TemperatureUnit,
    ): Int =
        when (unit) {
            TemperatureUnit.Celsius -> celsius
            TemperatureUnit.Fahrenheit -> celsius * 9 / 5 + 32
        }.roundToInt()

    fun wind(
        kmh: Double,
        unit: WindUnit,
    ): Int =
        when (unit) {
            WindUnit.KilometresPerHour -> kmh
            WindUnit.MetresPerSecond -> kmh / 3.6
            WindUnit.MilesPerHour -> kmh / 1.609344
            WindUnit.Knots -> kmh / 1.852
        }.roundToInt()

    /** Precipitation to one decimal (mm) or two (in). */
    fun precipitation(
        mm: Double,
        unit: PrecipitationUnit,
    ): Double =
        when (unit) {
            PrecipitationUnit.Millimetres -> (mm * 10).roundToInt() / 10.0
            PrecipitationUnit.Inches -> (mm / 25.4 * 100).roundToInt() / 100.0
        }

    /** Visibility in km (or miles with imperial wind units), to a whole number past 10. */
    fun visibility(
        metres: Double,
        miles: Boolean,
    ): Double {
        val v = if (miles) metres / 1609.344 else metres / 1000
        return if (v >= 10) v.roundToInt().toDouble() else (v * 10).roundToInt() / 10.0
    }
}

enum class UvBand { Low, Moderate, High, VeryHigh, Extreme }

object Uv {
    /** The WHO bands: 0–2 low, 3–5 moderate, 6–7 high, 8–10 very high, 11+ extreme. */
    fun band(index: Double): UvBand =
        when (index.roundToInt()) {
            in Int.MIN_VALUE..2 -> UvBand.Low
            in 3..5 -> UvBand.Moderate
            in 6..7 -> UvBand.High
            in 8..10 -> UvBand.VeryHigh
            else -> UvBand.Extreme
        }
}

enum class Compass { N, NE, E, SE, S, SW, W, NW }

object Wind {
    /** Where the wind comes from, as one of eight points. */
    fun compass(degrees: Int): Compass = Compass.entries[(((degrees % 360 + 360) % 360 + 22) / 45) % 8]
}

/** A day's low–high on the scale of all the days shown, as fractions from 0 to 1. */
data class RangeBar(
    val start: Float,
    val end: Float,
)

object Ranges {
    fun bars(days: List<Day>): List<RangeBar> {
        if (days.isEmpty()) return emptyList()
        val min = days.minOf { it.low }
        val max = days.maxOf { it.high }
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        return days.map { RangeBar(((it.low - min) / span).toFloat(), ((it.high - min) / span).toFloat()) }
    }

    /** Where [temperature] sits on the same scale (for today's "now" dot), clamped to the bar. */
    fun position(
        temperature: Double,
        days: List<Day>,
    ): Float {
        if (days.isEmpty()) return 0f
        val min = days.minOf { it.low }
        val max = days.maxOf { it.high }
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        return ((temperature - min) / span).toFloat().coerceIn(0f, 1f)
    }
}

object Timeline {
    /** How old a forecast may get before it's fetched again. */
    const val STALE_MS = 15 * 60 * 1000L

    fun isStale(
        fetchedAt: Long,
        now: Long,
    ): Boolean = now - fetchedAt >= STALE_MS || fetchedAt > now

    /** The next [count] hours, starting with the one we're in. */
    fun nextHours(
        hours: List<Hour>,
        now: Long,
        count: Int = 24,
    ): List<Hour> {
        val start = hours.indexOfLast { it.time <= now }.coerceAtLeast(0)
        return hours.drop(start).take(count)
    }

    /**
     * [primary]'s hours, with [extra]'s outside the span they cover (before the first and after
     * the last): BOM forecasts three days hourly from now, Open-Meteo all day for ten.
     */
    fun merge(
        primary: List<Hour>,
        extra: List<Hour>,
    ): List<Hour> {
        if (primary.isEmpty()) return extra
        val first = primary.first().time
        val last = primary.last().time
        return (extra.filter { it.time < first } + primary + extra.filter { it.time > last }).sortedBy { it.time }
    }

    /** The hour we're in, for the details that only come hourly (UV, visibility, dew point). */
    fun currentHour(
        hours: List<Hour>,
        now: Long,
    ): Hour? = hours.lastOrNull { it.time <= now } ?: hours.firstOrNull()
}
