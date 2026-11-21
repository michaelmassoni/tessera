package io.github.michaelmassoni.tessera.weather.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class BomTest {
    private fun fixture(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    private fun sydney() =
        Bom.parse(fixture("bom_location.json"), fixture("bom_daily.json"), fixture("bom_hourly.json"), fixture("bom_observations.json"))

    @Test
    fun `a real BOM response parses into observations, hours and days`() {
        val f = sydney()
        assertEquals(ForecastSource.Bom, f.source)
        assertEquals(ZoneId.of("Australia/Sydney"), f.zone)
        assertEquals(28.2, f.current.temperature, 0.0)
        assertEquals(57, f.current.humidity)
        assertEquals("Sydney - Observatory Hill", f.station)
        assertEquals(73, f.hours.size)
        // The last day has only a low, so it's left out as incomplete.
        assertEquals(8, f.days.size)
        assertTrue(f.summary!!.startsWith("Partly cloudy."))
        // Days are consecutive local dates, starting with the issue day in Sydney.
        f.days.zipWithNext().forEach { (a, b) -> assertEquals(a.date.plusDays(1), b.date) }
        assertTrue(f.days.all { it.low <= it.high })
    }

    @Test
    fun `today's missing low comes from the observed minimum`() {
        val today = sydney().days.first()
        assertEquals(19.5, today.low, 0.0)
        assertEquals(29.0, today.high, 0.0)
        assertNotNull(today.sunrise)
        assertEquals(10.0, today.uvMax!!, 0.0)
    }

    @Test
    fun `missing gusts in the observation fall back to the forecast hour`() {
        assertEquals(44.0, sydney().current.windGusts, 0.0)
        assertEquals(Bom.degrees("ENE"), sydney().current.windDirection)
    }

    @Test
    fun `geohash matches BOM's location ids`() {
        assertEquals("r3gx2s", Bom.geohash(-33.857, 151.199))
        // Wikipedia's worked example.
        assertEquals("u4pruydqqvj", Bom.geohash(57.64911, 10.40744, precision = 11))
    }

    @Test
    fun `only Australian coordinates go to BOM`() {
        assertTrue(Bom.covers(-33.87, 151.21)) // Sydney
        assertTrue(Bom.covers(-42.88, 147.33)) // Hobart
        assertTrue(Bom.covers(-31.95, 115.86)) // Perth
        assertTrue(Bom.covers(-12.46, 130.84)) // Darwin
        assertFalse(Bom.covers(-36.85, 174.76)) // Auckland
        assertFalse(Bom.covers(1.35, 103.82)) // Singapore
        assertFalse(Bom.covers(52.52, 13.41)) // Berlin
    }

    @Test
    fun `icon descriptors map to conditions`() {
        assertEquals(Condition.MostlyClear, Bom.condition("mostly_sunny"))
        assertEquals(Condition.Showers, Bom.condition("shower"))
        assertEquals(Condition.Thunderstorm, Bom.condition("storm"))
        assertEquals(Condition.Fog, Bom.condition("hazy"))
        assertEquals(Condition.Overcast, Bom.condition("something_new"))
    }

    @Test
    fun `compass points become degrees`() {
        assertEquals(0, Bom.degrees("N"))
        assertEquals(68, Bom.degrees("ENE"))
        assertEquals(225, Bom.degrees("SW"))
        assertEquals(0, Bom.degrees(null))
        // East-northeast sits between NE and E on eight points; it rounds to E.
        assertEquals(Compass.E, Wind.compass(Bom.degrees("ENE")))
    }

    @Test
    fun `the cache codec round-trips both sources and reads Weather 0_1 files`() {
        val bom =
            ForecastCodec.bom(
                fixture("bom_location.json"),
                fixture("bom_daily.json"),
                fixture("bom_hourly.json"),
                fixture("bom_observations.json"),
            )
        assertEquals(ForecastSource.Bom, ForecastCodec.decode(bom).source)
        val berlin = fixture("forecast_berlin.json")
        assertEquals(ForecastSource.OpenMeteo, ForecastCodec.decode(ForecastCodec.openMeteo(berlin)).source)
        // A 0.1 cache file is the bare Open-Meteo response.
        assertEquals(10, ForecastCodec.decode(berlin).days.size)
    }
}
