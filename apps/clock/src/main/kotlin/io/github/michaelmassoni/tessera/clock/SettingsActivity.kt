package io.github.michaelmassoni.tessera.clock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michaelmassoni.tessera.clock.data.ClockSettings
import io.github.michaelmassoni.tessera.core.settings.ChoiceSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.SettingsCategory
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import kotlin.concurrent.thread
import io.github.michaelmassoni.tessera.core.design.R as DesignR

class SettingsActivity : TesseraSettingsActivity() {
    override val launcherIcon =
        LauncherIcon.of(
            glyph = R.drawable.ic_launcher_foreground,
            defaultBackground = R.color.ic_launcher_background,
            lightBackground = R.color.ic_launcher_light_background,
            lightForeground = R.color.ic_launcher_light_foreground,
            darkBackground = R.color.ic_launcher_dark_background,
            darkForeground = R.color.ic_launcher_dark_foreground,
        )

    @Composable
    override fun categories(): List<SettingsCategory> {
        val store = graph.settings
        val settings by store.state.collectAsStateWithLifecycle()
        val update: ((ClockSettings) -> ClockSettings) -> Unit = { transform -> thread { store.update(transform) } }
        val minutes = @Composable { m: Int -> pluralStringResource(R.plurals.duration_minutes, m, m) }
        val silenceLabel = @Composable { m: Int -> if (m == 0) stringResource(R.string.settings_never) else minutes(m) }
        val fallAsleepLabel = @Composable { m: Int -> if (m == 0) stringResource(R.string.settings_none) else minutes(m) }

        return listOf(
            SettingsCategory(
                key = "alarms",
                title = stringResource(R.string.settings_alarms),
                icon = DesignR.drawable.ic_alarm,
                summary =
                    if (settings.silenceAfterMinutes == 0) {
                        stringResource(R.string.settings_alarms_summary_never, minutes(settings.snoozeMinutes))
                    } else {
                        stringResource(
                            R.string.settings_alarms_summary,
                            minutes(settings.snoozeMinutes),
                            minutes(settings.silenceAfterMinutes),
                        )
                    },
            ) {
                ChoiceSetting(
                    title = stringResource(R.string.settings_snooze),
                    options = ClockSettings.SNOOZE_CHOICES,
                    selected = settings.snoozeMinutes,
                    label = minutes,
                    onSelect = { m -> update { it.copy(snoozeMinutes = m) } },
                )
                ChoiceSetting(
                    title = stringResource(R.string.settings_silence_after),
                    options = ClockSettings.SILENCE_CHOICES,
                    selected = settings.silenceAfterMinutes,
                    label = silenceLabel,
                    onSelect = { m -> update { it.copy(silenceAfterMinutes = m) } },
                )
            },
            SettingsCategory(
                key = "sleep",
                title = stringResource(R.string.settings_sleep),
                icon = DesignR.drawable.ic_bedtime,
                summary =
                    if (settings.fallAsleepMinutes == 0) {
                        stringResource(R.string.settings_sleep_summary_none, minutes(settings.sleepCycleMinutes))
                    } else {
                        stringResource(
                            R.string.settings_sleep_summary,
                            minutes(settings.sleepCycleMinutes),
                            minutes(settings.fallAsleepMinutes),
                        )
                    },
            ) {
                ChoiceSetting(
                    title = stringResource(R.string.settings_sleep_cycle),
                    options = ClockSettings.CYCLE_CHOICES,
                    selected = settings.sleepCycleMinutes,
                    label = minutes,
                    onSelect = { m -> update { it.copy(sleepCycleMinutes = m) } },
                )
                ChoiceSetting(
                    title = stringResource(R.string.settings_fall_asleep),
                    options = ClockSettings.FALL_ASLEEP_CHOICES,
                    selected = settings.fallAsleepMinutes,
                    label = fallAsleepLabel,
                    onSelect = { m -> update { it.copy(fallAsleepMinutes = m) } },
                )
            },
        )
    }
}
