package io.github.michaelmassoni.tessera.files

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
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
        val settings = remember { filesSettings() }
        var options by remember { mutableStateOf(settings.options) }
        SettingsHeader(stringResource(R.string.settings_browsing))
        SwitchSetting(
            title = stringResource(R.string.settings_show_hidden),
            summary = stringResource(R.string.settings_show_hidden_summary),
            checked = options.showHidden,
            onCheckedChange = {
                options = options.copy(showHidden = it)
                settings.options = options
            },
        )
        InfoSetting(
            title = stringResource(R.string.settings_access),
            summary = stringResource(R.string.settings_access_summary),
            onClick = {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri()))
            },
        )
    }
}
