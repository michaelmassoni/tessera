package io.github.michaelmassoni.tessera.recorder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.core.settings.ChoiceSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.SettingsCategory
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.recorder.data.Quality
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
        var quality by remember { mutableStateOf(Quality.get(this)) }
        return listOf(
            SettingsCategory(
                key = "recording",
                title = stringResource(R.string.settings_recording),
                icon = DesignR.drawable.ic_mic,
                summary =
                    stringResource(
                        if (quality ==
                            Quality.Standard
                        ) {
                            R.string.settings_quality_standard_summary
                        } else {
                            R.string.settings_quality_high_summary
                        },
                    ),
            ) {
                ChoiceSetting(
                    title = stringResource(R.string.settings_quality),
                    options = Quality.entries,
                    selected = quality,
                    label = { stringResource(if (it == Quality.Standard) R.string.quality_standard else R.string.quality_high) },
                    onSelect = { q ->
                        quality = q
                        Quality.set(this@SettingsActivity, q)
                    },
                )
            },
        )
    }
}
