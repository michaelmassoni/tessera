package io.github.michaelmassoni.tessera.clock.worldclock

import java.text.Normalizer
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

data class City(
    val zoneId: String,
    val name: String,
    val country: String,
)

/**
 * Every IANA zone that has an exemplar city, named in the user's language. Names come from ICU
 * through [names], so there's no bundled database and nothing to keep up to date by hand.
 */
class CityCatalog(
    zoneIds: Collection<String>,
    names: (zoneId: String) -> Pair<String, String>?,
) {
    val cities: List<City> =
        zoneIds
            .asSequence()
            .filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") }
            .mapNotNull { id -> names(id)?.let { (name, country) -> City(id, name, country) } }
            .distinctBy { it.name to it.country }
            .sortedBy { it.name }
            .toList()

    private val byZone = cities.associateBy { it.zoneId }

    fun find(zoneId: String): City? = byZone[zoneId]

    /** Case- and accent-insensitive match on the city or country name; prefix matches first. */
    fun search(query: String): List<City> {
        val q = fold(query.trim())
        if (q.isEmpty()) return cities
        val matches = cities.filter { fold(it.name).contains(q) || fold(it.country).contains(q) }
        return matches.sortedBy { if (fold(it.name).startsWith(q)) 0 else 1 }
    }

    companion object {
        private val marks = Regex("\\p{M}+")

        fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase(Locale.ROOT)

        /** Uses Android's ICU for localized exemplar city and country names. */
        fun icu(locale: Locale): CityCatalog {
            val zoneNames =
                android.icu.text.TimeZoneNames
                    .getInstance(locale)
            return CityCatalog(ZoneId.getAvailableZoneIds()) { id ->
                val city = zoneNames.getExemplarLocationName(id) ?: return@CityCatalog null
                val region =
                    android.icu.util.TimeZone
                        .getRegion(id)
                val country =
                    if (region == "001" ||
                        region.isNullOrEmpty()
                    ) {
                        ""
                    } else {
                        runCatching {
                            Locale
                                .Builder()
                                .setRegion(
                                    region,
                                ).build()
                                .getDisplayCountry(locale)
                        }.getOrDefault("")
                    }
                city to country
            }
        }
    }
}

/** How a city's time relates to local time: hour/minute offset and calendar day. */
data class CityOffset(
    val minutes: Long,
    val dayDelta: Int,
) {
    companion object {
        fun of(
            local: ZonedDateTime,
            zone: ZoneId,
        ): CityOffset {
            val there = local.withZoneSameInstant(zone)
            val minutes =
                Duration
                    .ofSeconds(
                        (there.offset.totalSeconds - local.offset.totalSeconds).toLong(),
                    ).toMinutes()
            val days = (there.toLocalDate().toEpochDay() - local.toLocalDate().toEpochDay()).toInt()
            return CityOffset(minutes, days)
        }
    }
}
