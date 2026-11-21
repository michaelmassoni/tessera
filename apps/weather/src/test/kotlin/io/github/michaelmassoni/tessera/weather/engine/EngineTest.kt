package io.github.michaelmassoni.tessera.weather.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EngineTest {
    private fun fixture(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    // ---- Open-Meteo parsing (a real response for Berlin) ----

    @Test
    fun `a real forecast parses into now, 240 hours and 10 days`() {
        val f = OpenMeteo.parseForecast(fixture("forecast_berlin.json"))
        assertEquals(ZoneId.of("Europe/Berlin"), f.zone)
        assertEquals(240, f.hours.size)
        assertEquals(10, f.days.size)
        assertTrue(f.current.time > 0)
        assertTrue(f.days.all { it.low <= it.high })
        // Days are consecutive local dates.
        f.days.zipWithNext().forEach { (a, b) -> assertEquals(a.date.plusDays(1), b.date) }
        // Hours are an hour apart.
        f.hours.zipWithNext().forEach { (a, b) -> assertEquals(3_600_000L, b.time - a.time) }
    }

    @Test
    fun `missing values in the arrays don't drop the forecast`() {
        val body =
            """
            {"timezone":"UTC",
             "current":{"time":1000,"temperature_2m":10.0,"weather_code":61},
             "hourly":{"time":[1000,4600],"temperature_2m":[10.0,null],"weather_code":[61,null],
                       "precipitation_probability":[null,40]},
             "daily":{"time":[0,86400],"weather_code":[61,3],"temperature_2m_max":[12.0,null],"temperature_2m_min":[8.0,7.0]}}
            """.trimIndent()
        val f = OpenMeteo.parseForecast(body)
        assertEquals(Condition.LightRain, f.current.condition)
        assertEquals(10.0, f.current.feelsLike, 0.0)
        assertEquals(1, f.hours.size)
        assertNull(f.hours[0].rainChance)
        assertEquals(1, f.days.size)
        assertEquals(LocalDate.of(1970, 1, 1), f.days[0].date)
    }

    @Test
    fun `place search results read name, region and country`() {
        val places = OpenMeteo.parseSearch(fixture("search_berlin.json"))
        assertTrue(places.isNotEmpty())
        assertEquals("Berlin", places[0].name)
        assertEquals("Berlin, State of Berlin, Germany", places[0].description)
    }

    @Test
    fun `a search with no matches is an empty list`() {
        assertEquals(emptyList<Place>(), OpenMeteo.parseSearch("""{"generationtime_ms":0.1}"""))
    }

    @Test
    fun `coordinates are rounded to about a kilometre`() {
        val url = OpenMeteo.forecastUrl(-37.813629, 144.963058)
        assertTrue(url, url.contains("latitude=-37.81&longitude=144.96"))
        assertTrue(url.contains("timeformat=unixtime"))
    }

    @Test
    fun `search queries are encoded`() {
        assertTrue(OpenMeteo.searchUrl(" São Paulo ", "pt").contains("name=S%C3%A3o+Paulo&"))
    }

    // ---- WMO codes ----

    @Test
    fun `wmo codes map to conditions`() {
        assertEquals(Condition.Clear, Wmo.condition(0))
        assertEquals(Condition.PartlyCloudy, Wmo.condition(2))
        assertEquals(Condition.Fog, Wmo.condition(48))
        assertEquals(Condition.Drizzle, Wmo.condition(53))
        assertEquals(Condition.FreezingRain, Wmo.condition(66))
        assertEquals(Condition.HeavyRain, Wmo.condition(65))
        assertEquals(Condition.Showers, Wmo.condition(82))
        assertEquals(Condition.SnowShowers, Wmo.condition(86))
        assertEquals(Condition.ThunderstormHail, Wmo.condition(99))
        assertEquals(Condition.Overcast, Wmo.condition(42))
    }

    // ---- Units ----

    @Test
    fun `temperatures convert and round`() {
        assertEquals(18, Convert.temperature(18.4, TemperatureUnit.Celsius))
        assertEquals(32, Convert.temperature(0.0, TemperatureUnit.Fahrenheit))
        assertEquals(-40, Convert.temperature(-40.0, TemperatureUnit.Fahrenheit))
        assertEquals(-3, Convert.temperature(-3.4, TemperatureUnit.Celsius))
    }

    @Test
    fun `wind converts to each unit`() {
        assertEquals(36, Convert.wind(36.0, WindUnit.KilometresPerHour))
        assertEquals(10, Convert.wind(36.0, WindUnit.MetresPerSecond))
        assertEquals(22, Convert.wind(36.0, WindUnit.MilesPerHour))
        assertEquals(19, Convert.wind(36.0, WindUnit.Knots))
    }

    @Test
    fun `precipitation and visibility round sensibly`() {
        assertEquals(2.5, Convert.precipitation(2.54, PrecipitationUnit.Millimetres), 0.0)
        assertEquals(0.1, Convert.precipitation(2.54, PrecipitationUnit.Inches), 0.0)
        assertEquals(24.0, Convert.visibility(24_140.0, miles = false), 0.0)
        assertEquals(3.2, Convert.visibility(3_210.0, miles = false), 0.0)
        assertEquals(15.0, Convert.visibility(24_140.0, miles = true), 0.0)
    }

    @Test
    fun `units default from the country`() {
        assertEquals(Units(), Units.forCountry("AU"))
        assertEquals(TemperatureUnit.Fahrenheit, Units.forCountry("us").temperature)
        assertEquals(WindUnit.MilesPerHour, Units.forCountry("GB").wind)
        assertEquals(TemperatureUnit.Celsius, Units.forCountry("GB").temperature)
    }

    // ---- UV, wind direction ----

    @Test
    fun `uv bands follow the WHO scale`() {
        assertEquals(UvBand.Low, Uv.band(2.4))
        assertEquals(UvBand.Moderate, Uv.band(2.5))
        assertEquals(UvBand.High, Uv.band(7.0))
        assertEquals(UvBand.VeryHigh, Uv.band(10.4))
        assertEquals(UvBand.Extreme, Uv.band(13.0))
    }

    @Test
    fun `wind directions round to eight points`() {
        assertEquals(Compass.N, Wind.compass(0))
        assertEquals(Compass.N, Wind.compass(350))
        assertEquals(Compass.NE, Wind.compass(30))
        assertEquals(Compass.S, Wind.compass(180))
        assertEquals(Compass.W, Wind.compass(-90))
        assertEquals(Compass.NW, Wind.compass(315))
    }

    // ---- Range bars, timeline ----

    private fun day(
        n: Long,
        low: Double,
        high: Double,
    ) = Day(LocalDate.of(2026, 11, 21).plusDays(n), Condition.Clear, high, low, null, null, null, null, null)

    @Test
    fun `range bars sit on the scale of all the days`() {
        val days = listOf(day(0, 10.0, 20.0), day(1, 15.0, 30.0))
        val bars = Ranges.bars(days)
        assertEquals(RangeBar(0f, 0.5f), bars[0])
        assertEquals(RangeBar(0.25f, 1f), bars[1])
        assertEquals(0.4f, Ranges.position(18.0, days), 0.0001f)
        assertEquals(1f, Ranges.position(40.0, days), 0f)
    }

    @Test
    fun `a flat forecast doesn't divide by zero`() {
        assertEquals(RangeBar(0f, 0f), Ranges.bars(listOf(day(0, 12.0, 12.0)))[0])
    }

    @Test
    fun `forecasts go stale after 15 minutes, or if the clock went back`() {
        assertFalse(Timeline.isStale(fetchedAt = 0, now = 14 * 60_000L))
        assertTrue(Timeline.isStale(fetchedAt = 0, now = 15 * 60_000L))
        assertTrue(Timeline.isStale(fetchedAt = 10_000, now = 0))
    }

    @Test
    fun `the next hours start with the one we're in`() {
        val hours = (0 until 48).map { Hour(it * 3_600_000L, 10.0, null, Condition.Clear, true, null, null, null) }
        val next = Timeline.nextHours(hours, now = 5 * 3_600_000L + 1_000)
        assertEquals(24, next.size)
        assertEquals(5 * 3_600_000L, next.first().time)
        assertEquals(5 * 3_600_000L, Timeline.currentHour(hours, 5 * 3_600_000L + 1_000)?.time)
        // Before the first hour: start at the beginning.
        assertEquals(0L, Timeline.nextHours(hours, now = -1).first().time)
    }
}
