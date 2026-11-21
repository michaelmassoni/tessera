package io.github.michaelmassoni.tessera.weather.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the cache stores: the raw responses and which source they came from, so a cached
 * forecast parses exactly like a fresh one. A file from Weather 0.1 is a bare Open-Meteo response.
 */
object ForecastCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private class Envelope(
        val source: ForecastSource,
        val bodies: Map<String, String>,
    )

    fun openMeteo(body: String): String =
        json.encodeToString(
            Envelope.serializer(),
            Envelope(
                ForecastSource.OpenMeteo,
                mapOf("forecast" to body),
            ),
        )

    /** [openMeteo], when fetched, fills the hours BOM's hourly forecast doesn't reach. */
    fun bom(
        location: String,
        daily: String,
        hourly: String,
        observations: String,
        openMeteo: String? = null,
    ): String =
        json.encodeToString(
            Envelope.serializer(),
            Envelope(
                ForecastSource.Bom,
                mapOf("location" to location, "daily" to daily, "hourly" to hourly, "observations" to observations) +
                    listOfNotNull(openMeteo?.let { "openmeteo" to it }),
            ),
        )

    fun decode(text: String): Forecast {
        val envelope =
            runCatching { json.decodeFromString(Envelope.serializer(), text) }.getOrNull() ?: return OpenMeteo.parseForecast(text)
        val b = envelope.bodies
        return when (envelope.source) {
            ForecastSource.OpenMeteo -> {
                OpenMeteo.parseForecast(b.getValue("forecast"))
            }

            ForecastSource.Bom -> {
                val bom = Bom.parse(b.getValue("location"), b.getValue("daily"), b.getValue("hourly"), b.getValue("observations"))
                val extra = b["openmeteo"]?.let { runCatching { OpenMeteo.parseForecast(it).hours }.getOrNull() }.orEmpty()
                val hours = Timeline.merge(bom.hours, extra)
                bom.copy(hours = hours, hoursFromOpenMeteo = hours.size > bom.hours.size)
            }
        }
    }
}
