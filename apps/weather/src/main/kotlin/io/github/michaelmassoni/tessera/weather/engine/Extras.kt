package io.github.michaelmassoni.tessera.weather.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/** A Bureau of Meteorology warning for a place (Australia only). */
data class Warning(
    val id: String,
    val title: String,
    val shortTitle: String,
    val type: String,
    /** BOM's "major" warnings (severe thunderstorms, floods, fire weather, heatwaves…), not "minor" ones. */
    val major: Boolean,
    val issued: Long,
    val expires: Long?,
)

/** BOM warnings: `/v1/locations/{geohash}/warnings` and `/v1/warnings/{id}`. */
object Warnings {
    private val json = Json { ignoreUnknownKeys = true }

    fun listUrl(geohash: String) = "https://api.weather.bom.gov.au/v1/locations/$geohash/warnings"

    fun detailUrl(id: String) = "https://api.weather.bom.gov.au/v1/warnings/$id"

    /** The warnings in force at [now], major first, newest first. Cancelled or expired ones are left out. */
    fun parse(
        body: String,
        now: Long,
    ): List<Warning> =
        json
            .decodeFromString(ListResponse.serializer(), body)
            .data
            .mapNotNull { w ->
                val issued = w.issueTime?.let(::instant) ?: return@mapNotNull null
                val expires = w.expiryTime?.let(::instant)
                if (w.phase == "cancelled" || (expires != null && expires <= now)) return@mapNotNull null
                Warning(w.id, w.title.orEmpty(), w.shortTitle ?: w.title.orEmpty(), w.type.orEmpty(), w.group == "major", issued, expires)
            }.sortedWith(compareByDescending<Warning> { it.major }.thenByDescending { it.issued })

    /** The warning's message, as HTML (shown as text, without its images). */
    fun message(body: String): String =
        json
            .decodeFromString(DetailResponse.serializer(), body)
            .data
            .message
            .orEmpty()

    private fun instant(text: String) = runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()

    @Serializable
    private class ListResponse(
        val data: List<WarningDto> = emptyList(),
    )

    @Serializable
    private class WarningDto(
        val id: String,
        val type: String? = null,
        val title: String? = null,
        @SerialName("short_title") val shortTitle: String? = null,
        @SerialName("warning_group_type") val group: String? = null,
        @SerialName("issue_time") val issueTime: String? = null,
        @SerialName("expiry_time") val expiryTime: String? = null,
        val phase: String? = null,
    )

    @Serializable
    private class DetailResponse(
        val data: DetailDto,
    )

    @Serializable
    private class DetailDto(
        val message: String? = null,
    )
}

enum class AqiBand { Good, Moderate, SensitiveGroups, Unhealthy, VeryUnhealthy, Hazardous }

enum class PollenLevel { None, Low, Moderate, High, VeryHigh }

/** Air quality now, and today's highest pollen counts where there are any (Europe). */
data class AirQuality(
    val usAqi: Int?,
    val pm25: Double?,
    val pm10: Double?,
    /** Highest count today per pollen type with data, grains/m³: "grass", "birch"… */
    val pollen: Map<String, Double>,
) {
    val band: AqiBand? get() = usAqi?.let(::bandOf)

    /** The worst pollen type today and its level, or null where there's no pollen data. */
    val worstPollen: Pair<String, PollenLevel>? get() = pollen.maxByOrNull { it.value }?.let { it.key to pollenLevel(it.value) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val TYPES = listOf("alder", "birch", "grass", "mugwort", "olive", "ragweed")

        fun url(
            latitude: Double,
            longitude: Double,
        ): String =
            "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${OpenMeteo.round2(latitude)}" +
                "&longitude=${OpenMeteo.round2(longitude)}&current=us_aqi,pm2_5,pm10" +
                "&hourly=${TYPES.joinToString(",") { "${it}_pollen" }}&forecast_days=1&timezone=auto&timeformat=unixtime"

        fun parse(body: String): AirQuality {
            val r = json.decodeFromString(Response.serializer(), body)
            val hourly = r.hourly
            val pollen =
                if (hourly == null) {
                    emptyMap()
                } else {
                    mapOf(
                        "alder" to hourly.alder,
                        "birch" to hourly.birch,
                        "grass" to hourly.grass,
                        "mugwort" to hourly.mugwort,
                        "olive" to hourly.olive,
                        "ragweed" to hourly.ragweed,
                    ).mapNotNull { (type, values) -> values?.filterNotNull()?.maxOrNull()?.let { type to it } }.toMap()
                }
            return AirQuality(r.current?.usAqi?.toInt(), r.current?.pm25, r.current?.pm10, pollen)
        }

        /** The US EPA's bands. */
        fun bandOf(aqi: Int): AqiBand =
            when {
                aqi <= 50 -> AqiBand.Good
                aqi <= 100 -> AqiBand.Moderate
                aqi <= 150 -> AqiBand.SensitiveGroups
                aqi <= 200 -> AqiBand.Unhealthy
                aqi <= 300 -> AqiBand.VeryUnhealthy
                else -> AqiBand.Hazardous
            }

        /** Rough levels for grains/m³ across pollen types (the bands European services use for grass). */
        fun pollenLevel(count: Double): PollenLevel =
            when {
                count < 1 -> PollenLevel.None
                count < 20 -> PollenLevel.Low
                count < 50 -> PollenLevel.Moderate
                count < 200 -> PollenLevel.High
                else -> PollenLevel.VeryHigh
            }
    }

    @Serializable
    private class Response(
        val current: CurrentDto? = null,
        val hourly: HourlyDto? = null,
    )

    @Serializable
    private class CurrentDto(
        @SerialName("us_aqi") val usAqi: Double? = null,
        @SerialName("pm2_5") val pm25: Double? = null,
        val pm10: Double? = null,
    )

    @Serializable
    private class HourlyDto(
        @SerialName("alder_pollen") val alder: List<Double?>? = null,
        @SerialName("birch_pollen") val birch: List<Double?>? = null,
        @SerialName("grass_pollen") val grass: List<Double?>? = null,
        @SerialName("mugwort_pollen") val mugwort: List<Double?>? = null,
        @SerialName("olive_pollen") val olive: List<Double?>? = null,
        @SerialName("ragweed_pollen") val ragweed: List<Double?>? = null,
    )
}

/** What the background check tells the user about. */
object Alerts {
    /**
     * The first hour within the next [withinHours] (from [now]) with at least [threshold]% chance
     * of rain, when it isn't raining yet: worth a "rain soon" notification.
     */
    fun rainSoon(
        hours: List<Hour>,
        now: Long,
        withinHours: Int = 2,
        threshold: Int = 60,
    ): Hour? {
        val upcoming = hours.filter { it.time > now - 3_600_000 && it.time <= now + withinHours * 3_600_000L }
        val first = upcoming.firstOrNull() ?: return null
        if ((first.rainChance ?: 0) >= threshold && first.time <= now) return null // Already raining.
        return upcoming.firstOrNull { it.time > now && (it.rainChance ?: 0) >= threshold }
    }

    /** Warnings not notified before ([seen] ids), major ones only. */
    fun newWarnings(
        warnings: List<Warning>,
        seen: Set<String>,
    ) = warnings.filter { it.major && it.id !in seen }
}
