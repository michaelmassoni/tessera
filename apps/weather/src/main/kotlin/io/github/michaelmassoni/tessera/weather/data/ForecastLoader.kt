package io.github.michaelmassoni.tessera.weather.data

import io.github.michaelmassoni.tessera.weather.engine.Bom
import io.github.michaelmassoni.tessera.weather.engine.ForecastCodec
import io.github.michaelmassoni.tessera.weather.engine.OpenMeteo
import io.github.michaelmassoni.tessera.weather.engine.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Fetches a place's forecast in the stored form (see [ForecastCodec]): the Bureau for Australian
 * places, Open-Meteo elsewhere or if BOM fails. Shared by the app and the background check.
 */
class ForecastLoader(
    private val api: WeatherApi,
) {
    suspend fun load(place: Place): String =
        (if (Bom.covers(place.latitude, place.longitude)) runCatching { loadBom(place) }.getOrNull() else null)
            ?: ForecastCodec.openMeteo(api.get(OpenMeteo.forecastUrl(place.latitude, place.longitude)))

    /** BOM's location, daily and hourly forecasts and observations, fetched together and checked. */
    private suspend fun loadBom(place: Place): String =
        coroutineScope {
            val g = Bom.geohash(place.latitude, place.longitude)
            val location = async { api.get(Bom.locationUrl(g)) }
            val daily = async { api.get(Bom.dailyUrl(g)) }
            val hourly = async { api.get(Bom.hourlyUrl(g)) }
            val observations = async { api.get(Bom.observationsUrl(g)) }
            // Open-Meteo's hours for what BOM's three hourly days don't cover (the day details); optional.
            val openMeteo = async { runCatching { api.get(OpenMeteo.forecastUrl(place.latitude, place.longitude)) }.getOrNull() }
            val stored = ForecastCodec.bom(location.await(), daily.await(), hourly.await(), observations.await(), openMeteo.await())
            // A response that doesn't parse (BOM changed its API) falls back to Open-Meteo.
            withContext(Dispatchers.Default) { ForecastCodec.decode(stored) }.also { check(it.days.isNotEmpty() && it.hours.isNotEmpty()) }
            stored
        }
}
