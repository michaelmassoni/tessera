package io.github.michaelmassoni.tessera.weather.engine

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

/** What the sky is doing, from a WMO weather code. */
enum class Condition {
    Clear,
    MostlyClear,
    PartlyCloudy,
    Overcast,
    Fog,
    Drizzle,
    LightRain,
    Rain,
    HeavyRain,
    FreezingRain,
    Showers,
    Snow,
    SnowShowers,
    Thunderstorm,
    ThunderstormHail,
}

/** Conditions now. Temperatures °C, wind km/h, precipitation mm, pressure hPa. */
data class Current(
    val time: Long,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val isDay: Boolean,
    val condition: Condition,
    val precipitation: Double,
    val pressure: Double,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double,
)

/** One hour of the forecast; [time] is epoch ms at the start of the hour. */
data class Hour(
    val time: Long,
    val temperature: Double,
    val rainChance: Int?,
    val condition: Condition,
    val isDay: Boolean,
    val uv: Double?,
    /** Metres. */
    val visibility: Double?,
    val dewPoint: Double?,
)

data class Day(
    val date: LocalDate,
    val condition: Condition,
    val high: Double,
    val low: Double,
    val sunrise: Long?,
    val sunset: Long?,
    val uvMax: Double?,
    val precipitation: Double?,
    val rainChance: Int?,
)

enum class ForecastSource { OpenMeteo, Bom }

data class Forecast(
    val zone: ZoneId,
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    val source: ForecastSource = ForecastSource.OpenMeteo,
    /** The forecaster's words for today (BOM's extended forecast), when the source has them. */
    val summary: String? = null,
    /** Where the current conditions were observed (a BOM station), when they were. */
    val station: String? = null,
)

/** A saved place, from a place search. */
@Serializable
data class Place(
    val id: Long,
    val name: String,
    val region: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    /** "Melbourne, Victoria, Australia" without blanks or repeats. */
    val description: String get() = listOfNotNull(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")

    companion object {
        /** The id of the current-location place; never saved. */
        const val HERE = 0L
    }
}

enum class TemperatureUnit { Celsius, Fahrenheit }

enum class WindUnit { KilometresPerHour, MetresPerSecond, MilesPerHour, Knots }

enum class PrecipitationUnit { Millimetres, Inches }

data class Units(
    val temperature: TemperatureUnit = TemperatureUnit.Celsius,
    val wind: WindUnit = WindUnit.KilometresPerHour,
    val precipitation: PrecipitationUnit = PrecipitationUnit.Millimetres,
) {
    companion object {
        /** What people in [country] (ISO code) expect. */
        fun forCountry(country: String): Units =
            when (country.uppercase()) {
                "US", "LR", "MM" -> Units(TemperatureUnit.Fahrenheit, WindUnit.MilesPerHour, PrecipitationUnit.Inches)
                "GB" -> Units(wind = WindUnit.MilesPerHour)
                else -> Units()
            }
    }
}
