package io.github.michaelmassoni.tessera.weather.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.weather.R
import io.github.michaelmassoni.tessera.weather.engine.Compass
import io.github.michaelmassoni.tessera.weather.engine.Condition
import io.github.michaelmassoni.tessera.weather.engine.Convert
import io.github.michaelmassoni.tessera.weather.engine.PrecipitationUnit
import io.github.michaelmassoni.tessera.weather.engine.Units
import io.github.michaelmassoni.tessera.weather.engine.UvBand
import io.github.michaelmassoni.tessera.weather.engine.WindUnit

@StringRes
fun Condition.label(isDay: Boolean = true): Int =
    when (this) {
        Condition.Clear -> if (isDay) R.string.condition_sunny else R.string.condition_clear
        Condition.MostlyClear -> if (isDay) R.string.condition_mostly_sunny else R.string.condition_mostly_clear
        Condition.PartlyCloudy -> R.string.condition_partly_cloudy
        Condition.Overcast -> R.string.condition_overcast
        Condition.Fog -> R.string.condition_fog
        Condition.Drizzle -> R.string.condition_drizzle
        Condition.LightRain -> R.string.condition_light_rain
        Condition.Rain -> R.string.condition_rain
        Condition.HeavyRain -> R.string.condition_heavy_rain
        Condition.FreezingRain -> R.string.condition_freezing_rain
        Condition.Showers -> R.string.condition_showers
        Condition.Snow -> R.string.condition_snow
        Condition.SnowShowers -> R.string.condition_snow_showers
        Condition.Thunderstorm -> R.string.condition_thunderstorm
        Condition.ThunderstormHail -> R.string.condition_thunderstorm_hail
    }

@DrawableRes
fun Condition.icon(isDay: Boolean = true): Int =
    when (this) {
        Condition.Clear -> if (isDay) R.drawable.ic_sunny else R.drawable.ic_clear_night
        Condition.MostlyClear, Condition.PartlyCloudy -> if (isDay) R.drawable.ic_partly_cloudy_day else R.drawable.ic_partly_cloudy_night
        Condition.Overcast -> R.drawable.ic_cloud
        Condition.Fog -> R.drawable.ic_foggy
        Condition.Drizzle, Condition.LightRain -> R.drawable.ic_rainy_light
        Condition.Rain, Condition.Showers -> R.drawable.ic_rainy
        Condition.HeavyRain -> R.drawable.ic_rainy_heavy
        Condition.FreezingRain -> R.drawable.ic_weather_hail
        Condition.Snow, Condition.SnowShowers -> R.drawable.ic_weather_snowy
        Condition.Thunderstorm -> R.drawable.ic_thunderstorm
        Condition.ThunderstormHail -> R.drawable.ic_weather_hail
    }

@StringRes
fun UvBand.label(): Int =
    when (this) {
        UvBand.Low -> R.string.uv_low
        UvBand.Moderate -> R.string.uv_moderate
        UvBand.High -> R.string.uv_high
        UvBand.VeryHigh -> R.string.uv_very_high
        UvBand.Extreme -> R.string.uv_extreme
    }

@StringRes
fun Compass.label(): Int =
    when (this) {
        Compass.N -> R.string.compass_n
        Compass.NE -> R.string.compass_ne
        Compass.E -> R.string.compass_e
        Compass.SE -> R.string.compass_se
        Compass.S -> R.string.compass_s
        Compass.SW -> R.string.compass_sw
        Compass.W -> R.string.compass_w
        Compass.NW -> R.string.compass_nw
    }

@StringRes
fun WindUnit.short(): Int =
    when (this) {
        WindUnit.KilometresPerHour -> R.string.unit_kmh
        WindUnit.MetresPerSecond -> R.string.unit_ms
        WindUnit.MilesPerHour -> R.string.unit_mph
        WindUnit.Knots -> R.string.unit_kn
    }

@StringRes
fun PrecipitationUnit.short(): Int = if (this == PrecipitationUnit.Millimetres) R.string.unit_mm else R.string.unit_in

/** "18°" in the chosen unit. */
@Composable
fun temp(
    celsius: Double,
    units: Units,
): String = stringResource(R.string.degrees, Convert.temperature(celsius, units.temperature))
