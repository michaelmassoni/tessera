package io.github.michaelmassoni.tessera.clock.worldclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CityCatalogTest {
    private val names =
        mapOf(
            "America/New_York" to ("New York" to "United States"),
            "Europe/Zurich" to ("Zürich" to "Switzerland"),
            "Asia/Tokyo" to ("Tokyo" to "Japan"),
            "America/St_Johns" to ("St. John’s" to "Canada"),
            "Asia/Kolkata" to ("Kolkata" to "India"),
            "Asia/Calcutta" to ("Kolkata" to "India"), // alias: same city, must appear once
            "Etc/GMT+5" to ("GMT+5" to ""),
            "UTC" to ("UTC" to ""),
        )
    private val catalog = CityCatalog(names.keys) { names[it] }

    @Test fun `filters non-city zones and duplicates, sorted by name`() {
        assertEquals(listOf("Kolkata", "New York", "St. John’s", "Tokyo", "Zürich"), catalog.cities.map { it.name })
    }

    @Test fun `search ignores case and accents and matches countries`() {
        assertEquals(listOf("Zürich"), catalog.search("zur").map { it.name })
        assertEquals(listOf("Tokyo"), catalog.search("JAPAN").map { it.name })
        assertEquals(5, catalog.search("").size)
        assertEquals(listOf("New York"), catalog.search("new").map { it.name })
    }

    @Test fun `prefix matches rank first`() {
        val catalog =
            CityCatalog(listOf("A/Oslo", "A/Los_Angeles")) {
                if (it ==
                    "A/Oslo"
                ) {
                    "Oslo" to "Norway"
                } else {
                    "Los Angeles" to "United States"
                }
            }
        assertEquals(listOf("Los Angeles"), catalog.search("los").map { it.name })
        assertEquals(listOf("Oslo", "Los Angeles"), catalog.search("os").map { it.name })
    }

    @Test fun `find by zone`() {
        assertEquals("Tokyo", catalog.find("Asia/Tokyo")?.name)
        assertNull(catalog.find("Etc/GMT+5"))
    }

    @Test fun `offsets include half hours and day changes`() {
        val local = ZonedDateTime.of(2026, 11, 18, 22, 0, 0, 0, ZoneId.of("America/New_York"))
        assertEquals(CityOffset(14 * 60, 1), CityOffset.of(local, ZoneId.of("Asia/Tokyo")))
        assertEquals(CityOffset(90, 0), CityOffset.of(local, ZoneId.of("America/St_Johns")))
        assertEquals(CityOffset(-3 * 60, 0), CityOffset.of(local, ZoneId.of("America/Los_Angeles")))
        val early = local.withHour(1)
        assertEquals(CityOffset(-3 * 60, -1), CityOffset.of(early, ZoneId.of("America/Los_Angeles")))
    }
}
