package io.github.michaelmassoni.tessera.keyboard

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
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
        var autoCaps by remember { mutableStateOf(prefs.autoCaps) }
        var suggestions by remember { mutableStateOf(prefs.suggestions) }
        var autocorrect by remember { mutableStateOf(prefs.autocorrect) }
        var blockOffensive by remember { mutableStateOf(prefs.blockOffensive) }
        var learned by remember { mutableIntStateOf(prefs.learned.size) }
        SettingsHeader(stringResource(R.string.settings_typing))
        SwitchSetting(stringResource(R.string.settings_number_row), numberRow, {
            numberRow = it
            prefs.numberRow = it
        }, summary = stringResource(R.string.settings_number_row_summary))
        SwitchSetting(stringResource(R.string.settings_haptics), haptics, {
            haptics = it
            prefs.haptics = it
        })
        SwitchSetting(stringResource(R.string.settings_auto_caps), autoCaps, {
            autoCaps = it
            prefs.autoCaps = it
        }, summary = stringResource(R.string.settings_auto_caps_summary))
        SwitchSetting(stringResource(R.string.settings_double_space), period, {
            period = it
            prefs.doubleSpacePeriod = it
        }, summary = stringResource(R.string.settings_double_space_summary))
        SettingsHeader(stringResource(R.string.settings_corrections))
        SwitchSetting(stringResource(R.string.settings_suggestions), suggestions, {
            suggestions = it
            prefs.suggestions = it
        }, summary = stringResource(R.string.settings_suggestions_summary))
        SwitchSetting(stringResource(R.string.settings_autocorrect), autocorrect, {
            autocorrect = it
            prefs.autocorrect = it
        }, summary = stringResource(R.string.settings_autocorrect_summary))
        SwitchSetting(stringResource(R.string.settings_block_offensive), blockOffensive, {
            blockOffensive = it
            prefs.blockOffensive = it
        }, summary = stringResource(R.string.settings_block_offensive_summary))
        ListItem(
            onClick = {
                prefs.learned = emptySet()
                learned = 0
            },
            enabled = learned > 0,
            supportingContent = { Text(pluralStringResource(R.plurals.settings_clear_learned_summary, learned, learned)) },
        ) { Text(stringResource(R.string.settings_clear_learned)) }
        InfoSetting(stringResource(R.string.settings_dictionary_credit), stringResource(R.string.settings_dictionary_credit_summary))
    }
}
