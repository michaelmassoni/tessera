package io.github.michaelmassoni.tessera.weather.engine

enum class VisibilityBand { VeryPoor, Poor, Moderate, Good }

/** The numbers behind the detail tiles' pictures: bands, gauge fills and the sun's place in the day. */
object TileMath {
    /** How far you can see, in the usual aviation-ish bands: under 1 km, 1–4 km, 4–10 km, 10 km and more. */
    fun visibility(metres: Double): VisibilityBand =
        when {
            metres < 1_000 -> VisibilityBand.VeryPoor
            metres < 4_000 -> VisibilityBand.Poor
            metres < 10_000 -> VisibilityBand.Moderate
            else -> VisibilityBand.Good
        }

    /** Sea-level pressure on a 960–1060 hPa dial (deep low to strong high), 0 to 1. */
    fun pressure(hPa: Double): Float = ((hPa - 960) / 100).toFloat().coerceIn(0f, 1f)

    /** US AQI on a 0–300 dial (past 300 it's all "hazardous"), 0 to 1. */
    fun aqi(aqi: Int): Float = (aqi / 300f).coerceIn(0f, 1f)

    /** How far the sun is from [rise] to [set] at [now], 0 to 1 (0 before sunrise, 1 after sunset). */
    fun sun(
        now: Long,
        rise: Long,
        set: Long,
    ): Float = if (set <= rise) 0f else ((now - rise).toFloat() / (set - rise)).coerceIn(0f, 1f)

    /** The way the wind blows towards (forecasts give where it comes from), in degrees clockwise from north. */
    fun towards(from: Int): Int = ((from + 180) % 360 + 360) % 360
}
