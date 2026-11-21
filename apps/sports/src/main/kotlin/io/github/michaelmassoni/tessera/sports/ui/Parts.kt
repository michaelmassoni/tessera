package io.github.michaelmassoni.tessera.sports.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import io.github.michaelmassoni.tessera.sports.R
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.GameState
import io.github.michaelmassoni.tessera.sports.engine.Header
import io.github.michaelmassoni.tessera.sports.engine.League
import io.github.michaelmassoni.tessera.sports.engine.Side
import io.github.michaelmassoni.tessera.sports.engine.Sport
import io.github.michaelmassoni.tessera.sports.engine.Team
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Sport.icon(): Int =
    when (this) {
        Sport.Basketball -> R.drawable.ic_sports_basketball
        Sport.AustralianFootball, Sport.RugbyLeague -> R.drawable.ic_sports_rugby
        Sport.Baseball -> R.drawable.ic_sports_baseball
        Sport.AmericanFootball -> R.drawable.ic_sports_football
    }

fun League.label(): Int =
    when (this) {
        League.NBA -> R.string.league_nba
        League.WNBA -> R.string.league_wnba
        League.NBL -> R.string.league_nbl
        League.AFL -> R.string.league_afl
        League.NRL -> R.string.league_nrl
        League.MLB -> R.string.league_mlb
        League.NFL -> R.string.league_nfl
    }

fun Header.label(): Int =
    when (this) {
        Header.Played -> R.string.col_played
        Header.Won -> R.string.col_won
        Header.Lost -> R.string.col_lost
        Header.Drawn -> R.string.col_drawn
        Header.Tied -> R.string.col_tied
        Header.Pct -> R.string.col_pct
        Header.GamesBehind -> R.string.col_gb
        Header.Streak -> R.string.col_streak
        Header.Percentage -> R.string.col_percentage
        Header.Points -> R.string.col_points
        Header.PointsDiff -> R.string.col_pd
    }

/** The team's logo; without one, its abbreviation on its colour (the team's own colour is data). */
@Composable
fun TeamLogo(
    team: Team,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (team.logo != null) {
        AsyncImage(model = team.logo, contentDescription = null, modifier = modifier.size(size))
    } else {
        val color =
            team.color?.let { runCatching { Color("#$it".toColorInt()) }.getOrNull() }
                ?: MaterialTheme.colorScheme.secondaryContainer
        Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(team.abbreviation.take(3), style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

@Composable
fun timeFormat(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hmm" else "hmma"),
            locale,
        )
    }
}

@Composable
fun dayLabel(
    start: Long,
    zone: ZoneId,
    now: Long,
): String {
    val locale = LocalConfiguration.current.locales[0]
    val day = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> stringResource(R.string.section_today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> day.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale))
    }
}

/**
 * A game as a card: away team left, home team right, and between them the score (live or final)
 * or the start time. Live games get a dot and the period and clock.
 */
@Composable
fun GameCard(
    game: Game,
    now: Long,
    zone: ZoneId,
    onClick: () -> Unit,
    followed: Set<String>,
    modifier: Modifier = Modifier,
) {
    val time = timeFormat()
    val start = time.format(Instant.ofEpochMilli(game.start).atZone(zone))
    val day = dayLabel(game.start, zone, now)
    val status =
        when (game.state) {
            GameState.Scheduled -> "$day $start"
            GameState.Final -> stringResource(R.string.final_score)
            GameState.Postponed -> stringResource(R.string.postponed)
            GameState.Live -> game.status
        }
    val description =
        if (game.state == GameState.Scheduled || game.state == GameState.Postponed) {
            stringResource(R.string.game_upcoming_description, game.away.team.name, game.home.team.name, status)
        } else {
            stringResource(
                R.string.game_description,
                game.away.team.name,
                game.away.score.orEmpty(),
                game.home.team.name,
                game.home.score.orEmpty(),
                status,
            )
        }
    Surface(
        onClick = onClick,
        modifier = modifier.widthIn(max = 640.dp).fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(game.league.label()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                game.broadcast?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SideColumn(game.away, game, followed, Modifier.weight(1f))
                Middle(game, start, day, status, Modifier.weight(1.2f))
                SideColumn(game.home, game, followed, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SideColumn(
    side: Side,
    game: Game,
    followed: Set<String>,
    modifier: Modifier = Modifier,
) {
    val lost = game.state == GameState.Final && !side.winner
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TeamLogo(side.team, 44.dp)
        Text(
            side.team.shortName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (side.team.key in followed) FontWeight.Bold else FontWeight.Medium,
            color = if (lost) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        side.record?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Middle(
    game: Game,
    start: String,
    day: String,
    status: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (game.state) {
            GameState.Scheduled, GameState.Postponed -> {
                Text(start, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (game.state == GameState.Postponed) status else day,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            GameState.Live, GameState.Final -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Score(game.away.score, dim = game.state == GameState.Final && !game.away.winner)
                    Score(game.home.score, dim = game.state == GameState.Final && !game.home.winner)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (game.state == GameState.Live) {
                        Box(
                            Modifier
                                .padding(end = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                        )
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (game.state ==
                                GameState.Live
                            ) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun Score(
    score: String?,
    dim: Boolean,
) {
    Text(
        score.orEmpty(),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = if (dim) FontWeight.Normal else FontWeight.SemiBold,
        color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

/** A rounded tonal section with an optional title. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier.widthIn(max = 640.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }
            content()
        }
    }
}

/** Today's date in [zone], for "Today"/"Tomorrow" labels. */
fun today(
    now: Long,
    zone: ZoneId,
): LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
