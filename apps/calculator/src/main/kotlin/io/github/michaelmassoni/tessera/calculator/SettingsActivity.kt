package io.github.michaelmassoni.tessera.calculator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.calculator.engine.AngleUnit
import io.github.michaelmassoni.tessera.core.settings.ChoiceSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.SettingsCategory
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.core.design.R as DesignR

class SettingsActivity : TesseraSettingsActivity() {
    override val launcherIcon = calculatorLauncherIcon

    @Composable
    override fun categories(): List<SettingsCategory> {
        val settings = remember { calculatorSettings() }
        var unit by remember { mutableStateOf(settings.angleUnit) }
        val label = @Composable { u: AngleUnit ->
            stringResource(if (u == AngleUnit.DEGREES) R.string.settings_degrees else R.string.settings_radians)
        }
        return listOf(
            SettingsCategory(
                key = "calculation",
                title = stringResource(R.string.settings_calculation),
                icon = DesignR.drawable.ic_calculate,
                summary = label(unit),
            ) {
                ChoiceSetting(
                    title = stringResource(R.string.settings_angle_unit),
                    options = AngleUnit.entries,
                    selected = unit,
                    label = label,
                    onSelect = { u ->
                        settings.angleUnit = u
                        unit = u
                    },
                )
            },
        )
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
