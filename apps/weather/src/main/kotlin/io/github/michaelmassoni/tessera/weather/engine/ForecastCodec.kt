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

    fun bom(
        location: String,
        daily: String,
        hourly: String,
        observations: String,
    ): String =
        json.encodeToString(
            Envelope.serializer(),
            Envelope(
                ForecastSource.Bom,
                mapOf("location" to location, "daily" to daily, "hourly" to hourly, "observations" to observations),
            ),
        )

    fun decode(text: String): Forecast {
        val envelope =
            runCatching { json.decodeFromString(Envelope.serializer(), text) }.getOrNull() ?: return OpenMeteo.parseForecast(text)
        val b = envelope.bodies
        return when (envelope.source) {
            ForecastSource.OpenMeteo -> OpenMeteo.parseForecast(b.getValue("forecast"))
            ForecastSource.Bom -> Bom.parse(b.getValue("location"), b.getValue("daily"), b.getValue("hourly"), b.getValue("observations"))
        }
    }
}
