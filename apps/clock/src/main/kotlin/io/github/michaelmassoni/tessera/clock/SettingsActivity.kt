package io.github.michaelmassoni.tessera.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michaelmassoni.tessera.clock.data.ClockSettings
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import kotlin.concurrent.thread

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
    override fun ColumnScope.AppSettings() {
        val store = graph.settings
        val settings by store.state.collectAsStateWithLifecycle()
        val update: ((ClockSettings) -> ClockSettings) -> Unit = { transform -> thread { store.update(transform) } }

        SettingsHeader(stringResource(R.string.settings_snooze))
        Column(Modifier.selectableGroup()) {
            ClockSettings.SNOOZE_CHOICES.forEach { minutes ->
                RadioSetting(
                    title = pluralStringResource(R.plurals.duration_minutes, minutes, minutes),
                    selected = settings.snoozeMinutes == minutes,
                    onSelect = { update { it.copy(snoozeMinutes = minutes) } },
                )
            }
        }
        SettingsHeader(stringResource(R.string.settings_silence_after))
        Column(Modifier.selectableGroup()) {
            ClockSettings.SILENCE_CHOICES.forEach { minutes ->
                RadioSetting(
                    title =
                        if (minutes == 0) {
                            stringResource(R.string.settings_never)
                        } else {
                            pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
                        },
                    selected = settings.silenceAfterMinutes == minutes,
                    onSelect = { update { it.copy(silenceAfterMinutes = minutes) } },
                )
            }
        }
    }
}
