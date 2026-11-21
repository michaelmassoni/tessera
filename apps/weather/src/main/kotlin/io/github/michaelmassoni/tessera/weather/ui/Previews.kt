package io.github.michaelmassoni.tessera.weather.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.weather.PlacePage
import io.github.michaelmassoni.tessera.weather.WeatherState
import io.github.michaelmassoni.tessera.weather.engine.Condition
import io.github.michaelmassoni.tessera.weather.engine.Current
import io.github.michaelmassoni.tessera.weather.engine.Day
import io.github.michaelmassoni.tessera.weather.engine.Forecast
import io.github.michaelmassoni.tessera.weather.engine.ForecastSource
import io.github.michaelmassoni.tessera.weather.engine.Hour
import io.github.michaelmassoni.tessera.weather.engine.Place
import io.github.michaelmassoni.tessera.weather.engine.Units
import java.time.LocalDate
import java.time.ZoneId

private val zone = ZoneId.of("UTC")
private val today = LocalDate.of(2026, 11, 21)
private val now =
    today
        .atTime(10, 20)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
private val hour0 =
    today
        .atTime(10, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

private val sampleForecast =
    Forecast(
        zone = zone,
        current = Current(now, 18.4, 17.2, 62, true, Condition.PartlyCloudy, 0.0, 1016.0, 14.0, 200, 31.0),
        hours =
            (0 until 30).map { i ->
                val h = (10 + i) % 24
                Hour(
                    time = hour0 + i * 3_600_000L,
                    temperature = 18.0 + 5 * kotlin.math.sin((h - 9) / 24.0 * 2 * Math.PI),
                    rainChance = if (i in 6..9) 40 + i * 5 else 5,
                    condition =
                        if (i in 6..9) {
                            Condition.Showers
                        } else if (h in 6..19) {
                            Condition.PartlyCloudy
                        } else {
                            Condition.Clear
                        },
                    isDay = h in 6..19,
                    uv = if (h in 7..17) 6.0 else 0.0,
                    visibility = 24_000.0,
                    dewPoint = 10.6,
                )
            },
        days =
            listOf(
                Triple(Condition.PartlyCloudy, 13.0, 24.0),
                Triple(Condition.Showers, 12.0, 19.0),
                Triple(Condition.Rain, 11.0, 16.0),
                Triple(Condition.Overcast, 10.0, 18.0),
                Triple(Condition.Clear, 9.0, 23.0),
                Triple(Condition.Clear, 12.0, 28.0),
                Triple(Condition.Thunderstorm, 16.0, 31.0),
                Triple(Condition.MostlyClear, 14.0, 22.0),
                Triple(Condition.Drizzle, 11.0, 17.0),
                Triple(Condition.PartlyCloudy, 10.0, 20.0),
            ).mapIndexed { i, (condition, low, high) ->
                Day(
                    date = today.plusDays(i.toLong()),
                    condition = condition,
                    high = high,
                    low = low,
                    sunrise =
                        today
                            .plusDays(i.toLong())
                            .atTime(5, 58)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli(),
                    sunset =
                        today
                            .plusDays(i.toLong())
                            .atTime(20, 12)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli(),
                    uvMax = 9.0,
                    precipitation = if (condition in setOf(Condition.Showers, Condition.Rain, Condition.Drizzle)) 4.2 else 0.0,
                    rainChance =
                        if (condition in
                            setOf(Condition.Showers, Condition.Rain, Condition.Drizzle, Condition.Thunderstorm)
                        ) {
                            70
                        } else {
                            10
                        },
                )
            },
    )

private val melbourne = Place(2158177, "Melbourne", "Victoria", "Australia", -37.81, 144.96)
private val sydney = Place(2147714, "Sydney", "New South Wales", "Australia", -33.87, 151.21)

private val state =
    WeatherState(
        loaded = true,
        pages =
            listOf(
                PlacePage(
                    melbourne,
                    sampleForecast.copy(
                        source = ForecastSource.Bom,
                        summary = "Partly cloudy. Medium chance of showers in the afternoon and evening. Winds northerly 15 to 25 km/h.",
                        station = "Melbourne (Olympic Park)",
                    ),
                    fetchedAt = now - 9 * 60_000L,
                ),
                PlacePage(
                    sydney,
                    sampleForecast.copy(current = sampleForecast.current.copy(temperature = 24.0, condition = Condition.Clear)),
                ),
            ),
        units = Units(),
        useLocation = false,
        startPlace = melbourne.id,
    )

private object NoActions : WeatherActions {
    override fun refresh() = Unit

    override fun pageShown(id: Long) = Unit

    override fun show(id: Long) = Unit

    override fun search(query: String) = Unit

    override fun add(place: Place) = Unit

    override fun remove(place: Place) = Unit

    override fun move(
        place: Place,
        by: Int,
    ) = Unit

    override fun setUseLocation(on: Boolean) = Unit

    override fun openSettings() = Unit
}

@TesseraPreviews
@Composable
private fun ForecastPreview() {
    TesseraTheme(dynamicColor = false) { WeatherApp(state, NoActions, now, SnackbarHostState()) }
}

@TesseraPreviews
@Composable
private fun PlacesPreview() {
    TesseraTheme(dynamicColor = false) {
        PlacesScreen(state, NoActions, SnackbarHostState(), onBack = {}, onOpen = {})
    }
}

@TesseraPreviews
@Composable
private fun WelcomePreview() {
    TesseraTheme(dynamicColor = false) {
        WeatherApp(WeatherState(loaded = true), NoActions, now, SnackbarHostState())
    }
}
