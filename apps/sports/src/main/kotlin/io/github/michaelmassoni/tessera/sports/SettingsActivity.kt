package io.github.michaelmassoni.tessera.sports

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
import io.github.michaelmassoni.tessera.sports.data.sportsSettings
import io.github.michaelmassoni.tessera.sports.live.LiveGames
import io.github.michaelmassoni.tessera.core.design.R as DesignR

class SettingsActivity : TesseraSettingsActivity() {
    override val launcherIcon = sportsLauncherIcon

    @Composable
    override fun categories(): List<SettingsCategory> {
        val settings = remember { sportsSettings() }
        var live by remember { mutableStateOf(settings.liveUpdates) }
        var hide by remember { mutableStateOf(settings.hideScores) }
        val summary =
            listOfNotNull(
                stringResource(if (live) R.string.settings_scores_summary_live_on else R.string.settings_scores_summary_live_off),
                if (hide) stringResource(R.string.settings_scores_summary_hidden) else null,
            ).joinToString(" · ")
        return listOf(
            SettingsCategory(
                key = "scores",
                title = stringResource(R.string.settings_scores),
                icon = DesignR.drawable.ic_scoreboard,
                summary = summary,
            ) {
                SwitchSetting(
                    title = stringResource(R.string.settings_live),
                    summary = stringResource(R.string.settings_live_summary),
                    checked = live,
                    onCheckedChange = {
                        live = it
                        settings.liveUpdates = it
                        if (it) LiveGames.kick(applicationContext) else LiveGames.stop(applicationContext)
                    },
                )
                SwitchSetting(
                    title = stringResource(R.string.settings_hide_scores),
                    summary = stringResource(R.string.settings_hide_scores_summary),
                    checked = hide,
                    onCheckedChange = {
                        hide = it
                        settings.hideScores = it
                        // Redraw a live score that's showing, with or without its score.
                        if (settings.liveUpdates) LiveGames.kick(applicationContext)
                    },
                )
            },
        )
    }
}

internal val sportsLauncherIcon =
    LauncherIcon.of(
        glyph = R.drawable.ic_launcher_foreground,
        defaultBackground = R.color.ic_launcher_background,
        lightBackground = R.color.ic_launcher_light_background,
        lightForeground = R.color.ic_launcher_light_foreground,
        darkBackground = R.color.ic_launcher_dark_background,
        darkForeground = R.color.ic_launcher_dark_foreground,
    )
