package io.github.michaelmassoni.tessera.weather

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.work.ExistingWorkPolicy
import io.github.michaelmassoni.tessera.core.settings.ChoiceSetting
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.SettingsCategory
import io.github.michaelmassoni.tessera.core.settings.SwitchSetting
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.weather.background.WeatherJobs
import io.github.michaelmassoni.tessera.weather.data.weatherSettings
import io.github.michaelmassoni.tessera.weather.engine.PrecipitationUnit
import io.github.michaelmassoni.tessera.weather.engine.TemperatureUnit
import io.github.michaelmassoni.tessera.weather.engine.WindUnit
import kotlinx.coroutines.launch
import io.github.michaelmassoni.tessera.core.design.R as DesignR

class SettingsActivity : TesseraSettingsActivity() {
    override val launcherIcon = weatherLauncherIcon

    @Composable
    override fun categories(): List<SettingsCategory> {
        val settings = remember { weatherSettings() }
        var units by remember { mutableStateOf(settings.units) }

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

        val notifySummary =
            listOfNotNull(
                if (morning) stringResource(R.string.setting_morning) else null,
                if (rain) stringResource(R.string.settings_notify_summary_rain) else null,
                if (warnings) stringResource(R.string.settings_notify_summary_warnings) else null,
            ).joinToString(" · ").ifEmpty { stringResource(R.string.settings_notify_summary_off) }

        return listOf(
            SettingsCategory(
                key = "units",
                title = stringResource(R.string.settings_units),
                icon = DesignR.drawable.ic_straighten,
                summary =
                    stringResource(
                        R.string.settings_units_summary,
                        stringResource(units.temperature.short()),
                        stringResource(units.wind.short()),
                        stringResource(units.precipitation.short()),
                    ),
            ) {
                ChoiceSetting(
                    title = stringResource(R.string.settings_temperature),
                    options = TemperatureUnit.entries,
                    selected = units.temperature,
                    label = { stringResource(if (it == TemperatureUnit.Celsius) R.string.unit_celsius else R.string.unit_fahrenheit) },
                    onSelect = {
                        units = units.copy(temperature = it)
                        settings.units = units
                    },
                )
                ChoiceSetting(
                    title = stringResource(R.string.settings_wind),
                    options = WindUnit.entries,
                    selected = units.wind,
                    label = {
                        stringResource(
                            when (it) {
                                WindUnit.KilometresPerHour -> R.string.unit_kmh_long
                                WindUnit.MetresPerSecond -> R.string.unit_ms_long
                                WindUnit.MilesPerHour -> R.string.unit_mph_long
                                WindUnit.Knots -> R.string.unit_kn_long
                            },
                        )
                    },
                    onSelect = {
                        units = units.copy(wind = it)
                        settings.units = units
                    },
                )
                ChoiceSetting(
                    title = stringResource(R.string.settings_precipitation),
                    options = PrecipitationUnit.entries,
                    selected = units.precipitation,
                    label = { stringResource(if (it == PrecipitationUnit.Millimetres) R.string.unit_mm_long else R.string.unit_in_long) },
                    onSelect = {
                        units = units.copy(precipitation = it)
                        settings.units = units
                    },
                )
            },
            SettingsCategory(
                key = "notifications",
                title = stringResource(R.string.settings_notifications),
                icon = DesignR.drawable.ic_notifications,
                summary = notifySummary,
            ) {
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
            },
        )
    }
}

private fun TemperatureUnit.short() = if (this == TemperatureUnit.Celsius) R.string.unit_celsius_short else R.string.unit_fahrenheit_short

private fun WindUnit.short() =
    when (this) {
        WindUnit.KilometresPerHour -> R.string.unit_kmh
        WindUnit.MetresPerSecond -> R.string.unit_ms
        WindUnit.MilesPerHour -> R.string.unit_mph
        WindUnit.Knots -> R.string.unit_kn
    }

private fun PrecipitationUnit.short() = if (this == PrecipitationUnit.Millimetres) R.string.unit_mm else R.string.unit_in

internal val weatherLauncherIcon =
    LauncherIcon.of(
        glyph = R.drawable.ic_launcher_foreground,
        defaultBackground = R.color.ic_launcher_background,
        lightBackground = R.color.ic_launcher_light_background,
        lightForeground = R.color.ic_launcher_light_foreground,
        darkBackground = R.color.ic_launcher_dark_background,
        darkForeground = R.color.ic_launcher_dark_foreground,
    )
