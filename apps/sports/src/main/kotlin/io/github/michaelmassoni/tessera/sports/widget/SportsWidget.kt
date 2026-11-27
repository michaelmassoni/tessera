package io.github.michaelmassoni.tessera.sports.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.michaelmassoni.tessera.sports.MainActivity
import io.github.michaelmassoni.tessera.sports.R
import io.github.michaelmassoni.tessera.sports.data.EspnSource
import io.github.michaelmassoni.tessera.sports.data.followStore
import io.github.michaelmassoni.tessera.sports.data.followedGames
import io.github.michaelmassoni.tessera.sports.data.sportsSettings
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.GameState
import io.github.michaelmassoni.tessera.sports.engine.Side
import io.github.michaelmassoni.tessera.sports.engine.Team
import io.github.michaelmassoni.tessera.sports.engine.WidgetPick
import io.github.michaelmassoni.tessera.sports.ui.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A followed team's game: live, else the next one this week, else the latest result. Fetched when
 * it's drawn (every 30 minutes) and redrawn by Live scores' checks while a game is live. Scores
 * stay hidden in spoiler-free mode.
 */
class SportsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        // Glance calls this on the main thread: read saved teams off it.
        val follows = withContext(Dispatchers.IO) { context.followStore().load() }
        val zone = ZoneId.systemDefault()
        val game =
            runCatching { EspnSource().followedGames(follows, zone) }
                .getOrNull()
                ?.let { WidgetPick.game(it, follows.teamKeys, System.currentTimeMillis()) }
        val logos = game?.let { g -> listOf(g.away.team, g.home.team).associate { it.key to logo(context, it) } }.orEmpty()
        val hide = withContext(Dispatchers.IO) { context.sportsSettings().hideScores }
        provideContent { GlanceTheme { Content(context, game, logos, hide, follows.teams.isEmpty(), zone) } }
    }

    private suspend fun logo(
        context: Context,
        team: Team,
    ): Bitmap? {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val url = (if (dark) team.logoOnDark() else team.logo) ?: return null
        val request =
            ImageRequest
                .Builder(context)
                .data(url)
                .size(LOGO_PX)
                .allowHardware(false)
                .build()
        return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    @Composable
    private fun Content(
        context: Context,
        game: Game?,
        logos: Map<String, Bitmap?>,
        hide: Boolean,
        noTeams: Boolean,
        zone: ZoneId,
    ) {
        val open = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Box(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(28.dp)
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(open),
            contentAlignment = Alignment.Center,
        ) {
            if (game == null) {
                Text(
                    context.getString(if (noTeams) R.string.widget_pick_teams else R.string.widget_no_games),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center),
                )
                return@Box
            }
            val started = game.state == GameState.Live || game.state == GameState.Final
            val status =
                when (game.state) {
                    GameState.Scheduled -> {
                        val at = Instant.ofEpochMilli(game.start).atZone(zone)
                        DateTimeFormatter.ofPattern("EEE").format(at) + " " +
                            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at)
                    }

                    GameState.Final -> {
                        context.getString(R.string.final_score)
                    }

                    GameState.Postponed -> {
                        context.getString(R.string.postponed)
                    }

                    GameState.Live -> {
                        game.status
                    }
                }
            Column(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    context.getString(game.league.label()),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TeamColumn(game.away, logos[game.away.team.key], GlanceModifier.defaultWeight())
                    Score(game.away, started, hide)
                    Text(
                        status,
                        maxLines = 1,
                        style =
                            TextStyle(
                                color = if (game.state == GameState.Live) GlanceTheme.colors.error else GlanceTheme.colors.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            ),
                        modifier = GlanceModifier.padding(horizontal = 8.dp),
                    )
                    Score(game.home, started, hide)
                    TeamColumn(game.home, logos[game.home.team.key], GlanceModifier.defaultWeight())
                }
            }
        }
    }

    @Composable
    private fun TeamColumn(
        side: Side,
        logo: Bitmap?,
        modifier: GlanceModifier = GlanceModifier,
    ) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            if (logo != null) {
                Image(ImageProvider(logo), contentDescription = null, modifier = GlanceModifier.size(36.dp))
            } else {
                Spacer(GlanceModifier.size(36.dp))
            }
            Text(
                side.team.abbreviation,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
        }
    }

    @Composable
    private fun Score(
        side: Side,
        started: Boolean,
        hide: Boolean,
    ) {
        Text(
            when {
                !started -> side.record.orEmpty()
                hide -> "–"
                else -> side.score.orEmpty()
            },
            maxLines = 1,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (started) 26.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            modifier = GlanceModifier.width(48.dp),
        )
    }

    companion object {
        private const val LOGO_PX = 96

        suspend fun refresh(context: Context) {
            if (GlanceAppWidgetManager(context).getGlanceIds(SportsWidget::class.java).isNotEmpty()) SportsWidget().updateAll(context)
        }
    }
}

class SportsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SportsWidget()
}
