package io.github.michaelmassoni.tessera.sports.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.github.michaelmassoni.tessera.core.design.R as DesignR

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

/**
 * The team's logo, in its dark-background version on a dark theme; without one, its abbreviation
 * on its colour (the team's own colour is data).
 */
@Composable
fun TeamLogo(
    team: Team,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val logo = if (dark) team.logoOnDark() else team.logo
    if (logo != null) {
        AsyncImage(model = logo, contentDescription = null, modifier = modifier.size(size))
    } else {
        val color =
            team.color?.let { runCatching { Color("#$it".toColorInt()) }.getOrNull() }
                ?: MaterialTheme.colorScheme.secondaryContainer
        Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(team.abbreviation.take(3), style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

/** The star beside a followed team. */
@Composable
fun FollowedStar(modifier: Modifier = Modifier) {
    Icon(
        painterResource(DesignR.drawable.ic_star_filled),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier.size(14.dp),
    )
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

/** "Today", "Tomorrow", "Yesterday" or "Mon, 23 Nov". */
@Composable
fun dayLabel(
    day: LocalDate,
    today: LocalDate,
): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (day) {
        today -> stringResource(R.string.section_today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        today.minusDays(1) -> stringResource(R.string.tab_yesterday)
        else -> day.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale))
    }
}

/**
 * A game as a card, laid out like Apple Sports: the league centred on top; the away team left and
 * the home team right (logo, name under it, a star if followed), each with its score beside it, or
 * before the game its record; between them the clock, "Final" or the start time ([showDay] adds the
 * day, where the list isn't already grouped by day).
 */
@Composable
fun GameCard(
    game: Game,
    now: Long,
    zone: ZoneId,
    onClick: () -> Unit,
    followed: Set<String>,
    modifier: Modifier = Modifier,
    showDay: Boolean = true,
    /** Spoiler-free: the score isn't shown (nor who won) until the game is tapped. */
    hidden: Boolean = false,
) {
    val time = timeFormat()
    val start = time.format(Instant.ofEpochMilli(game.start).atZone(zone))
    val day = dayLabel(Instant.ofEpochMilli(game.start).atZone(zone).toLocalDate(), today(now, zone))
    val status =
        when (game.state) {
            GameState.Scheduled -> "$day $start"
            GameState.Final -> stringResource(R.string.final_score)
            GameState.Postponed -> stringResource(R.string.postponed)
            GameState.Live -> game.status
        }
    val league = stringResource(game.league.label())
    val description =
        if (hidden && (game.state == GameState.Live || game.state == GameState.Final)) {
            stringResource(R.string.game_hidden_description, game.away.team.name, game.home.team.name, status)
        } else if (game.state == GameState.Scheduled || game.state == GameState.Postponed) {
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
        modifier = modifier.widthIn(max = 640.dp).fillMaxWidth().clearAndSetSemantics { contentDescription = "$league. $description" },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Text(
                league,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBlock(game.away, game, followed, starFirst = true, hidden)
                Value(game.away, game, TextAlign.Start, Modifier.weight(1f), hidden)
                Middle(game, start, if (showDay) day else null, status)
                Value(game.home, game, TextAlign.End, Modifier.weight(1f), hidden)
                TeamBlock(game.home, game, followed, starFirst = false, hidden)
            }
        }
    }
}

private fun Side.lost(game: Game) = game.state == GameState.Final && !winner

/** Logo with the short name under it; the star sits on the card's outer edge. */
@Composable
private fun TeamBlock(
    side: Side,
    game: Game,
    followed: Set<String>,
    starFirst: Boolean,
    hidden: Boolean,
) {
    val star = side.team.key in followed
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (starFirst) StarSlot(star)
        Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TeamLogo(side.team, 40.dp)
            Text(
                side.team.shortName,
                style = MaterialTheme.typography.labelLarge,
                color = if (side.lost(game) && !hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!starFirst) StarSlot(star)
    }
}

@Composable
private fun StarSlot(star: Boolean) {
    Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) { if (star) FollowedStar() }
}

/** The score beside the logo; before the game, the team's record. */
@Composable
private fun Value(
    side: Side,
    game: Game,
    align: TextAlign,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
) {
    val started = game.state == GameState.Live || game.state == GameState.Final
    val padding = Modifier.padding(horizontal = 4.dp)
    if (started && hidden) {
        // A dash where the score would be: tap the card to see it.
        Text(
            "–",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            maxLines = 1,
            modifier = modifier.then(padding),
        )
    } else if (started) {
        val dim = side.lost(game)
        // Three-digit scores shrink to fit beside the logo rather than being cut off.
        Text(
            side.score.orEmpty(),
            style = MaterialTheme.typography.headlineLarge,
            autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = MaterialTheme.typography.headlineLarge.fontSize),
            fontWeight = if (dim) FontWeight.Normal else FontWeight.SemiBold,
            color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = align,
            maxLines = 1,
            modifier = modifier.then(padding),
        )
    } else {
        Text(
            side.record.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = align,
            maxLines = 1,
            modifier = modifier.then(padding),
        )
    }
}

@Composable
private fun Middle(
    game: Game,
    start: String,
    day: String?,
    status: String,
) {
    Column(Modifier.widthIn(min = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (game.state) {
            GameState.Scheduled -> {
                Text(start, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                day?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }

            GameState.Postponed, GameState.Final -> {
                Text(status, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }

            GameState.Live -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                    Text(status, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error, maxLines = 1)
                }
            }
        }
    }
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
