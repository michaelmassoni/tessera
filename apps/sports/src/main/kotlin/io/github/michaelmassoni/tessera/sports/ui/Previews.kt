package io.github.michaelmassoni.tessera.sports.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.sports.Load
import io.github.michaelmassoni.tessera.sports.SportsState
import io.github.michaelmassoni.tessera.sports.TeamPage
import io.github.michaelmassoni.tessera.sports.data.Follows
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.GameState
import io.github.michaelmassoni.tessera.sports.engine.League
import io.github.michaelmassoni.tessera.sports.engine.Player
import io.github.michaelmassoni.tessera.sports.engine.Side
import io.github.michaelmassoni.tessera.sports.engine.Team
import io.github.michaelmassoni.tessera.sports.engine.TeamInfo
import java.time.LocalDate
import java.time.ZoneId

private val zone = ZoneId.of("UTC")
private val now =
    LocalDate
        .of(2026, 11, 21)
        .atTime(11, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

// Logos are left out: previews have no network, and teams without one show their colour.
private fun team(
    league: League,
    id: String,
    name: String,
    short: String,
    abbr: String,
    color: String,
) = Team(league.id, id, name, short, abbr, null, color)

private val heat = team(League.NBA, "14", "Miami Heat", "Heat", "MIA", "98002e")
private val celtics = team(League.NBA, "2", "Boston Celtics", "Celtics", "BOS", "008348")
private val nuggets = team(League.NBA, "7", "Denver Nuggets", "Nuggets", "DEN", "0e2240")
private val rockets = team(League.NBA, "10", "Houston Rockets", "Rockets", "HOU", "ce1141")
private val pies = team(League.AFL, "6", "Collingwood", "Collingwood", "COLL", "000000")
private val blues = team(League.AFL, "3", "Carlton", "Carlton", "CARL", "0e1e2d")

private fun game(
    id: String,
    league: League,
    hours: Long,
    state: GameState,
    away: Pair<Team, String?>,
    home: Pair<Team, String?>,
    status: String = "",
) = Game(
    id,
    league,
    now + hours * 3_600_000L,
    state,
    status,
    if (state == GameState.Live) 4 else 0,
    null,
    Side(home.first, home.second, "10-6", state == GameState.Final && (home.second ?: "0").toInt() > (away.second ?: "0").toInt()),
    Side(away.first, away.second, "8-8", state == GameState.Final && (away.second ?: "0").toInt() > (home.second ?: "0").toInt()),
    "Kaseya Center",
    "ESPN",
)

private val games =
    listOf(
        game("1", League.NBA, -2, GameState.Live, rockets to "115", nuggets to "117", "3:20 - 4th"),
        game("2", League.NBA, 9, GameState.Scheduled, celtics to null, heat to null),
        game("3", League.AFL, 30, GameState.Scheduled, blues to null, pies to null),
        game("4", League.NBA, -40, GameState.Final, heat to "106", celtics to "98"),
    )

private val state =
    SportsState(
        loaded = true,
        follows = Follows(listOf("nba", "afl"), listOf(heat, pies)),
        games = games,
        fetchedAt = now,
        teams = mapOf(League.NBA to Load.Ready(listOf(celtics, nuggets, rockets, heat)), League.AFL to Load.Ready(listOf(blues, pies))),
    )

private object NoActions : SportsActions {
    override fun refresh() = Unit

    override fun loadLeague(
        league: League,
        force: Boolean,
    ) = Unit

    override fun open(game: Game) = Unit

    override fun closeDetail() = Unit

    override fun loadTeams(league: League) = Unit

    override fun setLeague(
        league: League,
        on: Boolean,
    ) = Unit

    override fun setTeam(
        team: Team,
        on: Boolean,
    ) = Unit

    override fun doneFollowing() = Unit

    override fun openSettings() = Unit

    override fun reveal(game: Game) = Unit

    override fun openTeam(team: Team) = Unit

    override fun closeTeam() = Unit
}

@TesseraPreviews
@Composable
private fun ScoresPreview() {
    TesseraTheme(dynamicColor = false) { SportsApp(state, NoActions, now, zone, SnackbarHostState()) }
}

@TesseraPreviews
@Composable
private fun FollowPreview() {
    TesseraTheme(dynamicColor = false) {
        SportsApp(state.copy(follows = Follows()), NoActions, now, zone, SnackbarHostState())
    }
}

// Spoiler-free: live and final scores hidden until tapped, in both themes.
@TesseraPreviews
@Composable
private fun HiddenScoresPreview() {
    TesseraTheme(dynamicColor = false) { SportsApp(state.copy(hideScores = true), NoActions, now, zone, SnackbarHostState()) }
}

// A team's page: record, next games, results and roster, in both themes.
@TesseraPreviews
@Composable
private fun TeamPreview() {
    val page =
        TeamPage(
            TeamInfo(
                record = "10-4",
                standing = "2nd in Southwest Division",
                roster =
                    listOf(
                        Player("Amen Thompson", "1", "G"),
                        Player("Alperen Sengun", "28", "C"),
                        Player("Jabari Smith Jr.", "10", "F"),
                    ),
            ),
            games.filter { it.involves(setOf(rockets.key)) },
        )
    TesseraTheme(dynamicColor = false) {
        SportsApp(state.copy(teamOpen = rockets, team = Load.Ready(page)), NoActions, now, zone, SnackbarHostState())
    }
}
