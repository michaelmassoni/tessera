package io.github.michaelmassoni.tessera.weather

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.work.ExistingWorkPolicy
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.SwitchSetting
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.weather.background.WeatherJobs
import io.github.michaelmassoni.tessera.weather.data.weatherSettings
import io.github.michaelmassoni.tessera.weather.engine.PrecipitationUnit
import io.github.michaelmassoni.tessera.weather.engine.TemperatureUnit
import io.github.michaelmassoni.tessera.weather.engine.WindUnit
import kotlinx.coroutines.launch

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

        // Notifications: turning one on asks for the permission (Android 13+) and starts the background check.
        var morning by remember { mutableStateOf(settings.morningForecast) }
        var rain by remember { mutableStateOf(settings.rainAlerts) }
        var warnings by remember { mutableStateOf(settings.warningAlerts) }
        val scope = rememberCoroutineScope()
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        fun changed(
            on: Boolean,
            replaceMorning: Boolean = false,
        ) {
            if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
            scope.launch {
                WeatherJobs.schedule(applicationContext, if (replaceMorning) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
            }
        }
        SettingsHeader(stringResource(R.string.settings_notifications))
        SwitchSetting(stringResource(R.string.setting_morning), morning, {
            morning = it
            settings.morningForecast = it
            changed(it, replaceMorning = true)
        }, summary = stringResource(R.string.setting_morning_summary))
        SwitchSetting(stringResource(R.string.setting_rain), rain, {
            rain = it
            settings.rainAlerts = it
            changed(it)
        }, summary = stringResource(R.string.setting_rain_summary))
        SwitchSetting(stringResource(R.string.setting_warnings), warnings, {
            warnings = it
            settings.warningAlerts = it
            changed(it)
        }, summary = stringResource(R.string.setting_warnings_summary))
        InfoSetting(stringResource(R.string.setting_notify_place_title), stringResource(R.string.setting_notify_place))
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
