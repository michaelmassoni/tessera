package io.github.michaelmassoni.tessera.calculator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.calculator.engine.AngleUnit
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity

class SettingsActivity : TesseraSettingsActivity() {
    override val launcherIcon = calculatorLauncherIcon

    @Composable
    override fun ColumnScope.AppSettings() {
        val settings = remember { calculatorSettings() }
        var unit by remember { mutableStateOf(settings.angleUnit) }
        SettingsHeader(stringResource(R.string.settings_angle_unit))
        Column(Modifier.selectableGroup()) {
            AngleUnit.entries.forEach { u ->
                RadioSetting(
                    title = stringResource(if (u == AngleUnit.DEGREES) R.string.settings_degrees else R.string.settings_radians),
                    selected = unit == u,
                    onSelect = {
                        settings.angleUnit = u
                        unit = u
                    },
                )
            }
        }
    }
}

internal val calculatorLauncherIcon =
    LauncherIcon.of(
        glyph = R.drawable.ic_launcher_foreground,
        defaultBackground = R.color.ic_launcher_background,
        lightBackground = R.color.ic_launcher_light_background,
        lightForeground = R.color.ic_launcher_light_foreground,
        darkBackground = R.color.ic_launcher_dark_background,
        darkForeground = R.color.ic_launcher_dark_foreground,
    )
