package io.github.michaelmassoni.tessera.weather.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Open-Meteo: request URLs and response parsing. Everything is requested in metric. */
object OpenMeteo {
    private val json = Json { ignoreUnknownKeys = true }

    private const val CURRENT =
        "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code," +
            "pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m"
    private const val HOURLY = "temperature_2m,precipitation_probability,weather_code,is_day,uv_index,visibility,dew_point_2m"
    private const val DAILY =
        "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_sum," +
            "precipitation_probability_max"

    /** Coordinates are rounded to 2 decimals (about 1 km): enough for a forecast, and less revealing. */
    fun forecastUrl(
        latitude: Double,
        longitude: Double,
    ): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${round2(latitude)}&longitude=${round2(longitude)}" +
            "&current=$CURRENT&hourly=$HOURLY&daily=$DAILY&timezone=auto&timeformat=unixtime&forecast_days=10"

    fun searchUrl(
        query: String,
        language: String,
    ): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}" +
            "&count=10&language=$language&format=json"

    private fun round2(value: Double) = String.format(Locale.ROOT, "%.2f", value)

    fun parseForecast(body: String): Forecast {
        val r = json.decodeFromString<ForecastResponse>(body)
        val zone = runCatching { ZoneId.of(r.timezone) }.getOrDefault(ZoneId.of("UTC"))
        val c = r.current
        val current =
            Current(
                time = c.time * 1000,
                temperature = c.temperature,
                feelsLike = c.apparentTemperature ?: c.temperature,
                humidity = c.humidity ?: 0,
                isDay = c.isDay != 0,
                condition = Wmo.condition(c.weatherCode),
                precipitation = c.precipitation ?: 0.0,
                pressure = c.pressure ?: 0.0,
                windSpeed = c.windSpeed ?: 0.0,
                windDirection = c.windDirection ?: 0,
                windGusts = c.windGusts ?: 0.0,
            )
        val h = r.hourly
        val hours =
            h.time.indices.mapNotNull { i ->
                val temperature = h.temperature.getOrNull(i) ?: return@mapNotNull null
                Hour(
                    time = h.time[i] * 1000,
                    temperature = temperature,
                    rainChance = h.rainChance?.getOrNull(i),
                    condition = Wmo.condition(h.weatherCode.getOrNull(i) ?: 0),
                    isDay = (h.isDay?.getOrNull(i) ?: 1) != 0,
                    uv = h.uv?.getOrNull(i),
                    visibility = h.visibility?.getOrNull(i),
                    dewPoint = h.dewPoint?.getOrNull(i),
                )
            }
        val d = r.daily
        val days =
            d.time.indices.mapNotNull { i ->
                val high = d.high.getOrNull(i) ?: return@mapNotNull null
                val low = d.low.getOrNull(i) ?: return@mapNotNull null
                Day(
                    date = Instant.ofEpochSecond(d.time[i]).atZone(zone).toLocalDate(),
                    condition = Wmo.condition(d.weatherCode.getOrNull(i) ?: 0),
                    high = high,
                    low = low,
                    sunrise = d.sunrise?.getOrNull(i)?.times(1000),
                    sunset = d.sunset?.getOrNull(i)?.times(1000),
                    uvMax = d.uvMax?.getOrNull(i),
                    precipitation = d.precipitation?.getOrNull(i),
                    rainChance = d.rainChance?.getOrNull(i),
                )
            }
        return Forecast(zone, current, hours, days)
    }

    fun parseSearch(body: String): List<Place> =
        json.decodeFromString<SearchResponse>(body).results.orEmpty().map {
            Place(it.id, it.name, it.admin1, it.country, it.latitude, it.longitude)
        }

    @Serializable
    private class ForecastResponse(
        val timezone: String = "UTC",
        val current: CurrentDto,
        val hourly: HourlyDto,
        val daily: DailyDto,
    )

    @Serializable
    private class CurrentDto(
        val time: Long,
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("relative_humidity_2m") val humidity: Int? = null,
        @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
        @SerialName("is_day") val isDay: Int = 1,
        val precipitation: Double? = null,
        @SerialName("weather_code") val weatherCode: Int = 0,
        @SerialName("pressure_msl") val pressure: Double? = null,
        @SerialName("wind_speed_10m") val windSpeed: Double? = null,
        @SerialName("wind_direction_10m") val windDirection: Int? = null,
        @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    )

    @Serializable
    private class HourlyDto(
        val time: List<Long> = emptyList(),
        @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
        @SerialName("precipitation_probability") val rainChance: List<Int?>? = null,
        @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
        @SerialName("is_day") val isDay: List<Int?>? = null,
        @SerialName("uv_index") val uv: List<Double?>? = null,
        val visibility: List<Double?>? = null,
        @SerialName("dew_point_2m") val dewPoint: List<Double?>? = null,
    )

    @Serializable
    private class DailyDto(
        val time: List<Long> = emptyList(),
        @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
        @SerialName("temperature_2m_max") val high: List<Double?> = emptyList(),
        @SerialName("temperature_2m_min") val low: List<Double?> = emptyList(),
        val sunrise: List<Long?>? = null,
        val sunset: List<Long?>? = null,
        @SerialName("uv_index_max") val uvMax: List<Double?>? = null,
        @SerialName("precipitation_sum") val precipitation: List<Double?>? = null,
        @SerialName("precipitation_probability_max") val rainChance: List<Int?>? = null,
    )

    @Serializable
    private class SearchResponse(
        val results: List<SearchResult>? = null,
    )

    @Serializable
    private class SearchResult(
        val id: Long,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val country: String? = null,
        val admin1: String? = null,
    )
}

/** WMO weather interpretation codes, as Open-Meteo uses them. */
object Wmo {
    fun condition(code: Int): Condition =
        when (code) {
            0 -> Condition.Clear
            1 -> Condition.MostlyClear
            2 -> Condition.PartlyCloudy
            3 -> Condition.Overcast
            45, 48 -> Condition.Fog
            51, 53, 55 -> Condition.Drizzle
            56, 57, 66, 67 -> Condition.FreezingRain
            61 -> Condition.LightRain
            63 -> Condition.Rain
            65 -> Condition.HeavyRain
            71, 73, 75, 77 -> Condition.Snow
            80, 81, 82 -> Condition.Showers
            85, 86 -> Condition.SnowShowers
            95 -> Condition.Thunderstorm
            96, 99 -> Condition.ThunderstormHail
            else -> Condition.Overcast
        }
}
