package io.github.michaelmassoni.tessera.weather.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayDetailTest {
    private fun fixture(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    private val zone = ZoneId.of("Australia/Melbourne")
    private val day = LocalDate.of(2026, 11, 22)
    private val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun hour(
        h: Int,
        temperature: Double,
        rain: Int? = null,
        precipitation: Double? = null,
        wind: Double? = null,
        gust: Double? = null,
    ) = Hour(
        time = midnight + h * 3_600_000L,
        temperature = temperature,
        rainChance = rain,
        condition = Condition.Clear,
        isDay = h in 6..19,
        uv = null,
        visibility = null,
        dewPoint = null,
        windSpeed = wind,
        windGusts = gust,
        precipitation = precipitation,
    )

    @Test
    fun `a day's hours are picked in the forecast's time zone`() {
        val hours = (-2..26).map { hour(it, 15.0) }
        val forecast =
            Forecast(
                zone = zone,
                current = Current(midnight, 15.0, 15.0, 50, true, Condition.Clear, 0.0, 1013.0, 10.0, 180, 20.0),
                hours = hours,
                days = emptyList(),
            )
        assertEquals(24, DayDetail.hours(forecast, day).size)
    }

    @Test
    fun `only measures with hourly values are offered`() {
        val hours = listOf(hour(0, 14.0, rain = 10, wind = 12.0), hour(1, 13.0, rain = 30, wind = 18.0))
        assertEquals(listOf(Metric.Temperature, Metric.Precipitation, Metric.Wind), DayDetail.metrics(hours))
    }

    @Test
    fun `summaries give the range, the rain total and the strongest gust`() {
        val hours = listOf(hour(0, 12.0, 20, 0.4, 10.0, 25.0), hour(1, 18.0, 60, 1.6, 30.0, 52.0), hour(2, 15.0, 40, null, 20.0, null))
        val temperature = DayDetail.summary(hours, Metric.Temperature)!!
        assertEquals(12.0, temperature.min, 0.0)
        assertEquals(18.0, temperature.max, 0.0)
        assertEquals(15.0, temperature.average, 0.0)
        assertEquals(2.0, DayDetail.summary(hours, Metric.Precipitation)!!.total!!, 1e-9)
        assertEquals(60.0, DayDetail.summary(hours, Metric.Precipitation)!!.max, 0.0)
        assertEquals(52.0, DayDetail.summary(hours, Metric.Wind)!!.gust!!, 0.0)
        assertNull(DayDetail.summary(hours, Metric.Uv))
    }

    @Test
    fun `chart ranges are fixed for percentages and UV, padded for the rest`() {
        assertEquals(ChartRange(0.0, 100.0), DayDetail.range(listOf(10.0, 30.0), Metric.Precipitation))
        assertEquals(ChartRange(0.0, 11.0), DayDetail.range(listOf(3.0, 9.0), Metric.Uv))
        assertEquals(ChartRange(0.0, 13.0), DayDetail.range(listOf(2.0, 13.0), Metric.Uv))
        // A steady 20° day still gets a few degrees either side.
        val steady = DayDetail.range(listOf(20.0, 20.5), Metric.Temperature)
        assertTrue(steady.bottom <= 17.0 && steady.top >= 23.0)
        val r = DayDetail.range(listOf(10.0, 30.0), Metric.Temperature)
        assertTrue(r.bottom < 10.0 && r.top > 30.0)
        assertEquals(0f, r.fraction(r.bottom), 0f)
        assertEquals(1f, r.fraction(r.top), 0f)
    }

    @Test
    fun `BOM's hours are kept, and Open-Meteo fills in only outside them`() {
        val bom = (5..10).map { hour(it, 20.0) }
        val om = (0..23).map { hour(it, 10.0) }
        val merged = Timeline.merge(bom, om)
        assertEquals(24, merged.size)
        assertEquals((0..23).map { midnight + it * 3_600_000L }, merged.map { it.time })
        assertTrue(merged.filter { it.time in bom.first().time..bom.last().time }.all { it.temperature == 20.0 })
        assertEquals(om, Timeline.merge(emptyList(), om))
    }

    @Test
    fun `BOM's hourly wind, humidity, feels-like and rain amounts are read`() {
        val f =
            Bom.parse(
                fixture("bom_location.json"),
                fixture("bom_daily.json"),
                fixture("bom_hourly.json"),
                fixture("bom_observations.json"),
            )
        val first = f.hours.first()
        assertEquals(31.0, first.windSpeed!!, 0.0)
        assertEquals(44.0, first.windGusts!!, 0.0)
        assertEquals(45, first.windDirection)
        assertEquals(58, first.humidity)
        assertEquals(27.0, first.feelsLike!!, 0.0)
        assertEquals(0.0, first.precipitation!!, 0.0)
        assertFalse(f.hoursFromOpenMeteo)
    }

    @Test
    fun `a cached BOM forecast with Open-Meteo hours merges them`() {
        val stored =
            ForecastCodec.bom(
                fixture("bom_location.json"),
                fixture("bom_daily.json"),
                fixture("bom_hourly.json"),
                fixture("bom_observations.json"),
                fixture("forecast_berlin.json"),
            )
        val bomOnly =
            Bom.parse(
                fixture("bom_location.json"),
                fixture("bom_daily.json"),
                fixture("bom_hourly.json"),
                fixture("bom_observations.json"),
            )
        val f = ForecastCodec.decode(stored)
        assertTrue(f.hoursFromOpenMeteo)
        assertTrue(f.hours.size > bomOnly.hours.size)
        assertEquals(f.hours.sortedBy { it.time }, f.hours)
    }
}
