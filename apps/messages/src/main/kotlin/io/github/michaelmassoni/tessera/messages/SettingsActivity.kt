package io.github.michaelmassoni.tessera.messages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.SettingsCategory
import io.github.michaelmassoni.tessera.core.settings.SwitchSetting
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.messages.data.MessagesPrefs
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
        val prefs = remember { MessagesPrefs(this) }
        var reports by remember { mutableStateOf(prefs.deliveryReports) }
        return listOf(
            SettingsCategory(
                key = "sending",
                title = stringResource(R.string.settings_sending),
                icon = DesignR.drawable.ic_send,
                summary = stringResource(R.string.settings_delivery_reports),
            ) {
                SwitchSetting(
                    title = stringResource(R.string.settings_delivery_reports),
                    checked = reports,
                    onCheckedChange = {
                        prefs.deliveryReports = it
                        reports = it
                    },
                    summary = stringResource(R.string.settings_delivery_reports_summary),
                )
            },
        )
    }
}
