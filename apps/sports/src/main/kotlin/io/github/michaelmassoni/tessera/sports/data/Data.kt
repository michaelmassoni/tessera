package io.github.michaelmassoni.tessera.sports.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.sports.engine.Espn
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.GameDetail
import io.github.michaelmassoni.tessera.sports.engine.League
import io.github.michaelmassoni.tessera.sports.engine.Scores
import io.github.michaelmassoni.tessera.sports.engine.StandingsGroup
import io.github.michaelmassoni.tessera.sports.engine.Team
import io.github.michaelmassoni.tessera.sports.engine.TeamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

/** Where scores come from; ESPN today, swappable. */
interface ScoreSource {
    /** One day's games in [league]. */
    suspend fun day(
        league: League,
        date: LocalDate,
    ): List<Game>

    /** A team's season: past results and coming fixtures. */
    suspend fun schedule(team: Team): List<Game>

    suspend fun teams(league: League): List<Team>

    suspend fun standings(league: League): List<StandingsGroup>

    suspend fun detail(game: Game): GameDetail?

    suspend fun team(team: Team): TeamInfo

    /** What's on now in [league]: one small request, for refreshing live games. */
    suspend fun current(league: League): List<Game> = day(league, LocalDate.now())
}

class EspnSource : ScoreSource {
    private suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("User-Agent", "Tessera Sports")
                if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun day(
        league: League,
        date: LocalDate,
    ) = withContext(Dispatchers.Default) { Espn.parseScoreboard(league, get(Espn.scoreboardUrl(league, date))) }

    override suspend fun current(league: League) =
        withContext(Dispatchers.Default) { Espn.parseScoreboard(league, get(Espn.currentScoreboardUrl(league))) }

    override suspend fun schedule(team: Team) =
        withContext(Dispatchers.Default) { Espn.parseSchedule(League.byId(team.league)!!, get(Espn.scheduleUrl(team))) }

    override suspend fun teams(league: League) = withContext(Dispatchers.Default) { Espn.parseTeams(league, get(Espn.teamsUrl(league))) }

    override suspend fun standings(league: League) =
        withContext(Dispatchers.Default) { Espn.parseStandings(league, get(Espn.standingsUrl(league))) }

    override suspend fun detail(game: Game) =
        withContext(Dispatchers.Default) { Espn.parseSummary(game.league, get(Espn.summaryUrl(game))) }

    override suspend fun team(team: Team) = withContext(Dispatchers.Default) { Espn.parseRoster(get(Espn.rosterUrl(team))) }
}

/** The leagues and teams followed, in order. */
@Serializable
data class Follows(
    val leagues: List<String> = emptyList(),
    val teams: List<Team> = emptyList(),
) {
    val leagueSet: Set<String> get() = leagues.toSet()
    val teamKeys: Set<String> get() = teams.map { it.key }.toSet()

    fun leagues(): List<League> = leagues.mapNotNull(League::byId)
}

class FollowStore(
    private val file: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): Follows = runCatching { json.decodeFromString(Follows.serializer(), file.readText()) }.getOrDefault(Follows())

    fun save(follows: Follows) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(Follows.serializer(), follows))
        tmp.renameTo(file)
    }
}

fun Context.followStore() = FollowStore(File(filesDir, "follows.json"))

class SportsSettings(
    private val prefs: SharedPreferences,
) {
    /** live score notifications for followed teams' games. */
    var liveUpdates: Boolean
        get() = prefs.getBoolean(KEY_LIVE, true)
        set(value) = prefs.edit { putBoolean(KEY_LIVE, value) }

    /** Spoiler-free: scores are hidden in the app, the widget and Live scores until you tap a game. */
    var hideScores: Boolean
        get() = prefs.getBoolean(KEY_HIDE, false)
        set(value) = prefs.edit { putBoolean(KEY_HIDE, value) }

    private companion object {
        const val KEY_LIVE = "live_updates"
        const val KEY_HIDE = "hide_scores"
    }
}

fun Context.sportsSettings() = SportsSettings(getSharedPreferences("settings", Context.MODE_PRIVATE))

/** Days around [today]: ESPN's days are US time for US leagues, so yesterday and tomorrow too. */
private fun around(today: LocalDate) = (-1L..1L).map(today::plusDays)

/**
 * Followed games from a few days back to a week ahead. Followed teams' schedules give their
 * fixtures and results; the scoreboards around today give live scores (and, for a league with
 * no team picked, its games). Where both have a game, the scoreboard's is fresher.
 */
suspend fun ScoreSource.followedGames(
    follows: Follows,
    zone: ZoneId,
    today: LocalDate = LocalDate.now(zone),
): List<Game> =
    coroutineScope {
        val schedules = follows.teams.map { team -> async { runCatching { schedule(team) } } }
        val boards = follows.leagues().flatMap { league -> around(today).map { date -> async { runCatching { day(league, date) } } } }
        val results = (schedules + boards).awaitAll()
        if (results.isNotEmpty() && results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        val from = today.minusDays(Scores.RECENT_DAYS)
        val to = today.plusDays(Scores.UPCOMING_DAYS)
        results
            .flatMap { it.getOrDefault(emptyList()) }
            // Later entries win: scoreboards come after schedules.
            .associateBy { it.league to it.id }
            .values
            .filter {
                java.time.Instant
                    .ofEpochMilli(it.start)
                    .atZone(zone)
                    .toLocalDate() in from..to
            }.let { Scores.followed(it, follows.teamKeys, follows.leagueSet) }
    }

/** A league's games from yesterday to a few days ahead, for its tab (a request per day). */
suspend fun ScoreSource.leagueGames(
    league: League,
    today: LocalDate,
): List<Game> =
    coroutineScope {
        val results = (-1L..3L).map { today.plusDays(it) }.map { date -> async { runCatching { day(league, date) } } }.awaitAll()
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrDefault(emptyList()) }.distinctBy { it.id }
    }
