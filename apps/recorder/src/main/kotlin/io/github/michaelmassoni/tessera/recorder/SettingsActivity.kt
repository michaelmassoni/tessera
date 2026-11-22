package io.github.michaelmassoni.tessera.recorder

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.recorder.data.Quality

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
        var quality by remember { mutableStateOf(Quality.get(this@SettingsActivity)) }
        SettingsHeader(stringResource(R.string.settings_quality))
        Quality.entries.forEach { q ->
            RadioSetting(
                title = stringResource(if (q == Quality.Standard) R.string.quality_standard else R.string.quality_high),
                selected = quality == q,
                onSelect = {
                    quality = q
                    Quality.set(this@SettingsActivity, q)
                },
            )
        }
    }
}
