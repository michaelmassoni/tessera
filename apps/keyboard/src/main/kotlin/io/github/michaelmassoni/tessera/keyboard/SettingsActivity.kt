package io.github.michaelmassoni.tessera.keyboard

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.SwitchSetting
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity

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
        val prefs = remember { KeyboardPrefs(this@SettingsActivity) }
        var numberRow by remember { mutableStateOf(prefs.numberRow) }
        var haptics by remember { mutableStateOf(prefs.haptics) }
        var period by remember { mutableStateOf(prefs.doubleSpacePeriod) }
        SettingsHeader(stringResource(R.string.settings_typing))
        SwitchSetting(stringResource(R.string.settings_number_row), numberRow, {
            numberRow = it
            prefs.numberRow = it
        }, summary = stringResource(R.string.settings_number_row_summary))
        SwitchSetting(stringResource(R.string.settings_haptics), haptics, {
            haptics = it
            prefs.haptics = it
        })
        SwitchSetting(stringResource(R.string.settings_double_space), period, {
            period = it
            prefs.doubleSpacePeriod = it
        }, summary = stringResource(R.string.settings_double_space_summary))
    }
}
