package io.github.michaelmassoni.tessera.weather.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId

/**
 * The Bureau of Meteorology's forecasts and observations, from the API its own app uses
 * (`api.weather.bom.gov.au`, undocumented). Used for places in Australia; anywhere else, or if it
 * fails, the app falls back to Open-Meteo.
 */
object Bom {
    private const val BASE = "https://api.weather.bom.gov.au/v1/locations"
    private val json = Json { ignoreUnknownKeys = true }

    /** Roughly Australia (with Tasmania): BOM answers only for Australian locations. */
    fun covers(
        latitude: Double,
        longitude: Double,
    ): Boolean = latitude in -44.0..-9.0 && longitude in 112.0..154.5

    /** BOM addresses locations by 6-character geohash (about a 1 km × 0.6 km cell). */
    fun locationUrl(geohash: String) = "$BASE/$geohash"

    fun dailyUrl(geohash: String) = "$BASE/$geohash/forecasts/daily"

    fun hourlyUrl(geohash: String) = "$BASE/$geohash/forecasts/hourly"

    fun observationsUrl(geohash: String) = "$BASE/$geohash/observations"

    fun parse(
        location: String,
        daily: String,
        hourly: String,
        observations: String,
    ): Forecast {
        val zone =
            runCatching {
                ZoneId.of(json.decodeFromString<LocationResponse>(location).data.timezone)
            }.getOrDefault(ZoneId.of("Australia/Sydney"))
        val dailyResponse = json.decodeFromString<DailyResponse>(daily)
        val hours =
            json.decodeFromString<HourlyResponse>(hourly).data.map {
                Hour(
                    time = Instant.parse(it.time).toEpochMilli(),
                    temperature = it.temp,
                    rainChance = it.rain?.chance,
                    condition = condition(it.icon),
                    isDay = !it.isNight,
                    uv = it.uv,
                    visibility = null,
                    dewPoint = it.dewPoint,
                    feelsLike = it.feelsLike,
                    humidity = it.humidity,
                    windSpeed = it.wind?.speed,
                    windDirection = it.wind?.direction?.let(::degrees),
                    windGusts = it.wind?.gust,
                    // BOM gives a range; its upper end, or the lower when that's all there is.
                    precipitation = it.rain?.amount?.let { a -> a.max ?: a.min },
                )
            }
        val firstHour = json.decodeFromString<HourlyResponse>(hourly).data.firstOrNull()
        val obs = json.decodeFromString<ObservationsResponse>(observations)
        val o = obs.data
        val days =
            dailyResponse.data.mapIndexedNotNull { i, d ->
                val date = Instant.parse(d.date).atZone(zone).toLocalDate()
                // Today's low has passed by the afternoon, so BOM leaves it out: use the observed one.
                val high = d.tempMax ?: (if (i == 0) o?.maxTemp?.value else null) ?: return@mapIndexedNotNull null
                val low = d.tempMin ?: (if (i == 0) o?.minTemp?.value else null) ?: high
                Day(
                    date = date,
                    condition = condition(d.icon),
                    high = maxOf(high, low),
                    low = minOf(high, low),
                    sunrise = d.astronomical?.sunrise?.let { Instant.parse(it).toEpochMilli() },
                    sunset = d.astronomical?.sunset?.let { Instant.parse(it).toEpochMilli() },
                    uvMax = d.uv?.maxIndex,
                    precipitation = d.rain?.amount?.max ?: d.rain?.amount?.min,
                    rainChance = d.rain?.chance,
                )
            }
        val now = obs.metadata?.observationTime?.let { Instant.parse(it).toEpochMilli() } ?: hours.firstOrNull()?.time ?: 0
        val current =
            Current(
                time = now,
                temperature = o?.temp ?: firstHour?.temp ?: 0.0,
                feelsLike = o?.feelsLike ?: firstHour?.feelsLike ?: o?.temp ?: 0.0,
                humidity = o?.humidity ?: firstHour?.humidity ?: 0,
                isDay = firstHour?.isNight != true,
                condition = condition(firstHour?.icon),
                precipitation = o?.rainSince9am ?: 0.0,
                pressure = 0.0,
                windSpeed = o?.wind?.speed ?: firstHour?.wind?.speed ?: 0.0,
                windDirection = degrees(o?.wind?.direction ?: firstHour?.wind?.direction),
                windGusts = o?.gust?.speed ?: firstHour?.wind?.gust ?: 0.0,
            )
        return Forecast(
            zone = zone,
            current = current,
            hours = hours,
            days = days,
            source = ForecastSource.Bom,
            summary =
                dailyResponse.data
                    .firstOrNull()
                    ?.extendedText
                    ?.takeIf { it.isNotBlank() },
            station = o?.station?.name,
        )
    }

    /** BOM's icon descriptors. */
    fun condition(descriptor: String?): Condition =
        when (descriptor) {
            "sunny", "clear" -> Condition.Clear
            "mostly_sunny" -> Condition.MostlyClear
            "partly_cloudy", "wind" -> Condition.PartlyCloudy
            "cloudy" -> Condition.Overcast
            "hazy", "haze", "fog", "dusty", "dust" -> Condition.Fog
            "light_rain" -> Condition.LightRain
            "light_shower", "light_showers" -> Condition.LightRain
            "rain" -> Condition.Rain
            "shower", "showers" -> Condition.Showers
            "heavy_shower", "heavy_showers" -> Condition.HeavyRain
            "storm", "storms", "tropicalcyclone", "cyclone" -> Condition.Thunderstorm
            "snow" -> Condition.Snow
            "frost" -> Condition.Clear
            else -> Condition.Overcast
        }

    private val Points = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

    /** "ENE" → 68° (the middle of the sector); calm or unknown → 0. */
    fun degrees(compass: String?): Int = Points.indexOf(compass?.uppercase()).takeIf { it >= 0 }?.let { (it * 22.5 + 0.5).toInt() } ?: 0

    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    /** Standard geohash of [latitude], [longitude] to [precision] characters. */
    fun geohash(
        latitude: Double,
        longitude: Double,
        precision: Int = 6,
    ): String {
        var latRange = -90.0 to 90.0
        var lonRange = -180.0 to 180.0
        val out = StringBuilder()
        var bits = 0
        var bit = 0
        var even = true
        while (out.length < precision) {
            if (even) {
                val mid = (lonRange.first + lonRange.second) / 2
                if (longitude >= mid) {
                    bits = bits shl 1 or 1
                    lonRange = mid to lonRange.second
                } else {
                    bits = bits shl 1
                    lonRange = lonRange.first to mid
                }
            } else {
                val mid = (latRange.first + latRange.second) / 2
                if (latitude >= mid) {
                    bits = bits shl 1 or 1
                    latRange = mid to latRange.second
                } else {
                    bits = bits shl 1
                    latRange = latRange.first to mid
                }
            }
            even = !even
            if (++bit == 5) {
                out.append(BASE32[bits])
                bits = 0
                bit = 0
            }
        }
        return out.toString()
    }

    @Serializable
    private class LocationResponse(
        val data: LocationData,
    )

    @Serializable
    private class LocationData(
        val timezone: String,
    )

    @Serializable
    private class DailyResponse(
        val data: List<DailyDto> = emptyList(),
    )

    @Serializable
    private class DailyDto(
        val date: String,
        @SerialName("temp_max") val tempMax: Double? = null,
        @SerialName("temp_min") val tempMin: Double? = null,
        @SerialName("icon_descriptor") val icon: String? = null,
        @SerialName("extended_text") val extendedText: String? = null,
        val rain: RainDto? = null,
        val uv: UvDto? = null,
        val astronomical: AstroDto? = null,
    )

    @Serializable
    private class RainDto(
        val chance: Int? = null,
        val amount: AmountDto? = null,
    )

    @Serializable
    private class AmountDto(
        val min: Double? = null,
        val max: Double? = null,
    )

    @Serializable
    private class UvDto(
        @SerialName("max_index") val maxIndex: Double? = null,
    )

    @Serializable
    private class AstroDto(
        @SerialName("sunrise_time") val sunrise: String? = null,
        @SerialName("sunset_time") val sunset: String? = null,
    )

    @Serializable
    private class HourlyResponse(
        val data: List<HourDto> = emptyList(),
    )

    @Serializable
    private class HourDto(
        val time: String,
        val temp: Double,
        @SerialName("temp_feels_like") val feelsLike: Double? = null,
        @SerialName("dew_point") val dewPoint: Double? = null,
        @SerialName("relative_humidity") val humidity: Int? = null,
        val uv: Double? = null,
        val rain: RainDto? = null,
        val wind: WindDto? = null,
        @SerialName("icon_descriptor") val icon: String? = null,
        @SerialName("is_night") val isNight: Boolean = false,
    )

    @Serializable
    private class WindDto(
        @SerialName("speed_kilometre") val speed: Double? = null,
        val direction: String? = null,
        @SerialName("gust_speed_kilometre") val gust: Double? = null,
    )

    @Serializable
    private class ObservationsResponse(
        val metadata: ObsMeta? = null,
        val data: ObsDto? = null,
    )

    @Serializable
    private class ObsMeta(
        @SerialName("observation_time") val observationTime: String? = null,
    )

    @Serializable
    private class ObsDto(
        val temp: Double? = null,
        @SerialName("temp_feels_like") val feelsLike: Double? = null,
        val humidity: Int? = null,
        @SerialName("rain_since_9am") val rainSince9am: Double? = null,
        val wind: WindDto? = null,
        val gust: GustDto? = null,
        @SerialName("max_temp") val maxTemp: TempAt? = null,
        @SerialName("min_temp") val minTemp: TempAt? = null,
        val station: StationDto? = null,
    )

    @Serializable
    private class GustDto(
        @SerialName("speed_kilometre") val speed: Double? = null,
    )

    @Serializable
    private class TempAt(
        val value: Double? = null,
    )

    @Serializable
    private class StationDto(
        val name: String? = null,
    )
}
