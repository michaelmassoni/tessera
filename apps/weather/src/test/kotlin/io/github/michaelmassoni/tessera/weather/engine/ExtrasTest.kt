package io.github.michaelmassoni.tessera.weather.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ExtrasTest {
    private val now = Instant.parse("2026-11-22T00:00:00Z").toEpochMilli()

    @Test fun `warnings in force, major first, expired and cancelled left out`() {
        val body =
            """
            {"data":[
              {"id":"A","type":"marine_wind_warning","title":"Strong Wind Warning","short_title":"Strong Wind","warning_group_type":"minor","issue_time":"2026-11-21T20:00:00Z","expiry_time":"2026-11-22T06:00:00Z","phase":"new"},
              {"id":"B","type":"heatwave_warning","title":"Heatwave Warning for Metropolitan","short_title":"Heatwave Warning","warning_group_type":"major","issue_time":"2026-11-21T03:22:36Z","expiry_time":"2026-11-22T09:22:36Z","phase":"new"},
              {"id":"C","type":"flood_warning","title":"Old","warning_group_type":"major","issue_time":"2026-11-19T00:00:00Z","expiry_time":"2026-11-20T00:00:00Z"},
              {"id":"D","type":"flood_warning","title":"Gone","warning_group_type":"major","issue_time":"2026-11-21T00:00:00Z","phase":"cancelled"}
            ],"metadata":{}}
            """.trimIndent()
        val warnings = Warnings.parse(body, now)
        assertEquals(listOf("B", "A"), warnings.map { it.id })
        assertEquals("Heatwave Warning", warnings[0].shortTitle)
        assertEquals(listOf("B"), Alerts.newWarnings(warnings, seen = emptySet()).map { it.id })
        assertEquals(emptyList<Warning>(), Alerts.newWarnings(warnings, seen = setOf("B")))
        assertEquals("<p>Hot</p>", Warnings.message("""{"data":{"id":"B","message":"<p>Hot</p>"},"metadata":{}}"""))
    }

    @Test fun `air quality bands and pollen`() {
        val body =
            """{"current":{"us_aqi":54,"pm2_5":14.0,"pm10":18.5},"hourly":{"time":[1,2],"grass_pollen":[12.0,64.0],"birch_pollen":[null,null],"alder_pollen":[0.0,3.0]}}"""
        val aq = AirQuality.parse(body)
        assertEquals(54, aq.usAqi)
        assertEquals(AqiBand.Moderate, aq.band)
        assertEquals("grass" to PollenLevel.High, aq.worstPollen)
        assertEquals(setOf("grass", "alder"), aq.pollen.keys)
        assertNull(AirQuality.parse("""{"current":{"us_aqi":10},"hourly":{"time":[1],"grass_pollen":[null]}}""").worstPollen)
        assertEquals(AqiBand.Hazardous, AirQuality.bandOf(350))
    }

    private fun hour(
        offsetHours: Int,
        chance: Int,
    ) = Hour(now + offsetHours * 3_600_000L, 20.0, chance, Condition.Rain, true, null, null, null)

    @Test fun `rain soon is the first rainy hour ahead, unless it's raining already`() {
        assertEquals(now + 3_600_000L, Alerts.rainSoon(listOf(hour(0, 10), hour(1, 70), hour(2, 90)), now)?.time)
        assertNull(Alerts.rainSoon(listOf(hour(0, 80), hour(1, 90)), now))
        assertNull(Alerts.rainSoon(listOf(hour(0, 10), hour(1, 30), hour(3, 90)), now))
    }
}
