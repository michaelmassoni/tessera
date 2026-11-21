package io.github.michaelmassoni.tessera.weather.engine

import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.floor

/** What a day's detail view charts hour by hour. */
enum class Metric { Temperature, FeelsLike, Precipitation, Uv, Wind, Humidity, Visibility, Pressure }

/** A measure over a day, in the source's units (°C, km/h, mm, m, hPa, %). */
data class DaySummary(
    val min: Double,
    val max: Double,
    val average: Double,
    /** Precipitation: the day's total in mm. */
    val total: Double? = null,
    /** Wind: the strongest gust. */
    val gust: Double? = null,
)

/** The chart's value range: what the bottom and top of the chart stand for. */
data class ChartRange(
    val bottom: Double,
    val top: Double,
) {
    /** 0 at the bottom, 1 at the top. */
    fun fraction(value: Double): Float = if (top <= bottom) 0.5f else ((value - bottom) / (top - bottom)).toFloat().coerceIn(0f, 1f)
}

object DayDetail {
    /** [date]'s hours in the forecast's time zone. */
    fun hours(
        forecast: Forecast,
        date: LocalDate,
    ): List<Hour> = forecast.hours.filter { Instant.ofEpochMilli(it.time).atZone(forecast.zone).toLocalDate() == date }

    /** The hour's value for [metric]; Precipitation is the chance of rain, in %. */
    fun value(
        hour: Hour,
        metric: Metric,
    ): Double? =
        when (metric) {
            Metric.Temperature -> hour.temperature
            Metric.FeelsLike -> hour.feelsLike
            Metric.Precipitation -> hour.rainChance?.toDouble()
            Metric.Uv -> hour.uv
            Metric.Wind -> hour.windSpeed
            Metric.Humidity -> hour.humidity?.toDouble()
            Metric.Visibility -> hour.visibility
            Metric.Pressure -> hour.pressure
        }

    /**
     * The measures [hours] have enough of to chart: at least half the day (BOM has no hourly
     * visibility or pressure; Open-Meteo's hours that fill in around it would chart a sliver).
     */
    fun metrics(hours: List<Hour>): List<Metric> =
        Metric.entries.filter { m -> hours.count { value(it, m) != null }.let { it >= 2 && it * 2 >= hours.size } }

    fun summary(
        hours: List<Hour>,
        metric: Metric,
    ): DaySummary? {
        val values = hours.mapNotNull { value(it, metric) }
        if (values.isEmpty()) return null
        return DaySummary(
            min = values.min(),
            max = values.max(),
            average = values.average(),
            total = if (metric == Metric.Precipitation) hours.mapNotNull { it.precipitation }.takeIf { it.isNotEmpty() }?.sum() else null,
            gust = if (metric == Metric.Wind) hours.mapNotNull { it.windGusts }.maxOrNull() else null,
        )
    }

    /**
     * The chart's range for [values] (already in display units): percentages and UV on fixed
     * scales so days compare; the rest padded around the day's spread, at least [minSpan] tall so
     * a steady day doesn't look dramatic.
     */
    fun range(
        values: List<Double>,
        metric: Metric,
        minSpan: Double = 6.0,
    ): ChartRange {
        if (values.isEmpty()) return ChartRange(0.0, 1.0)
        return when (metric) {
            Metric.Precipitation, Metric.Humidity -> {
                ChartRange(0.0, 100.0)
            }

            Metric.Uv -> {
                ChartRange(0.0, maxOf(11.0, ceil(values.max())))
            }

            Metric.Wind, Metric.Visibility -> {
                ChartRange(0.0, maxOf(minSpan, ceil(values.max() * 1.2)))
            }

            else -> {
                val low = values.min()
                val high = values.max()
                val pad = maxOf((minSpan - (high - low)) / 2, (high - low) * 0.15)
                ChartRange(floor(low - pad), ceil(high + pad))
            }
        }
    }
}
