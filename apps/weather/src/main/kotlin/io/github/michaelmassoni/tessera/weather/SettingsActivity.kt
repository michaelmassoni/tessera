package io.github.michaelmassoni.tessera.weather

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.weather.data.weatherSettings
import io.github.michaelmassoni.tessera.weather.engine.PrecipitationUnit
import io.github.michaelmassoni.tessera.weather.engine.TemperatureUnit
import io.github.michaelmassoni.tessera.weather.engine.WindUnit

class SettingsActivity : TesseraSettingsActivity() {
    override val launcherIcon = weatherLauncherIcon

    @Composable
    override fun ColumnScope.AppSettings() {
        val settings = remember { weatherSettings() }
        var units by remember { mutableStateOf(settings.units) }

        @Composable
        fun <E : Enum<E>> group(
            title: Int,
            entries: List<E>,
            selected: E,
            label: (E) -> Int,
            pick: (E) -> Unit,
        ) {
            SettingsHeader(stringResource(title))
            Column(Modifier.selectableGroup()) {
                entries.forEach { e ->
                    RadioSetting(title = stringResource(label(e)), selected = e == selected, onSelect = { pick(e) })
                }
            }
        }

        group(R.string.settings_temperature, TemperatureUnit.entries, units.temperature, {
            if (it == TemperatureUnit.Celsius) R.string.unit_celsius else R.string.unit_fahrenheit
        }) {
            units = units.copy(temperature = it)
            settings.units = units
        }
        group(R.string.settings_wind, WindUnit.entries, units.wind, {
            when (it) {
                WindUnit.KilometresPerHour -> R.string.unit_kmh_long
                WindUnit.MetresPerSecond -> R.string.unit_ms_long
                WindUnit.MilesPerHour -> R.string.unit_mph_long
                WindUnit.Knots -> R.string.unit_kn_long
            }
        }) {
            units = units.copy(wind = it)
            settings.units = units
        }
        group(R.string.settings_precipitation, PrecipitationUnit.entries, units.precipitation, {
            if (it == PrecipitationUnit.Millimetres) R.string.unit_mm_long else R.string.unit_in_long
        }) {
            units = units.copy(precipitation = it)
            settings.units = units
        }
    }
}

internal val weatherLauncherIcon =
    LauncherIcon.of(
        glyph = R.drawable.ic_launcher_foreground,
        defaultBackground = R.color.ic_launcher_background,
        lightBackground = R.color.ic_launcher_light_background,
        lightForeground = R.color.ic_launcher_light_foreground,
        darkBackground = R.color.ic_launcher_dark_background,
        darkForeground = R.color.ic_launcher_dark_foreground,
    )
