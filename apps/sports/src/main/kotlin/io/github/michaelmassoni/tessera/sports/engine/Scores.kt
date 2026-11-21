package io.github.michaelmassoni.tessera.sports.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Section { Live, Today, Upcoming, Recent }

data class GameSection(
    val section: Section,
    val games: List<Game>,
)

object Scores {
    /** How far back the scores list looks for results. */
    const val RECENT_DAYS = 3L

    /** How far ahead it looks for fixtures. */
    const val UPCOMING_DAYS = 7L

    /** How often live games refresh while visible, and while a Live Update is showing. */
    const val LIVE_REFRESH_MS = 30_000L

    /** How often the rest refreshes while the app is open. */
    const val IDLE_REFRESH_MS = 5 * 60_000L

    /**
     * Games sorted into sections: Live (any time), Today (not live), Upcoming (after today,
     * soonest first) and Recent (finished before today, latest first). Each game appears once.
     */
    fun sections(
        games: List<Game>,
        now: Long,
        zone: ZoneId,
    ): List<GameSection> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

        fun date(g: Game): LocalDate = Instant.ofEpochMilli(g.start).atZone(zone).toLocalDate()
        val unique = games.distinctBy { it.league to it.id }
        val live = unique.filter { it.state == GameState.Live }.sortedBy { it.start }
        val rest = unique - live.toSet()
        val todays = rest.filter { date(it) == today }.sortedBy { it.start }
        val upcoming = rest.filter { date(it).isAfter(today) }.sortedBy { it.start }
        val recent = rest.filter { date(it).isBefore(today) }.sortedByDescending { it.start }
        return listOf(
            GameSection(Section.Live, live),
            GameSection(Section.Today, todays),
            GameSection(Section.Upcoming, upcoming),
            GameSection(Section.Recent, recent),
        ).filter { it.games.isNotEmpty() }
    }

    /** Games worth showing: those involving followed teams, or every game in a league with none followed. */
    fun followed(
        games: List<Game>,
        teams: Set<String>,
        leagues: Set<String>,
    ): List<Game> =
        games.filter { g ->
            val leagueTeams = teams.filter { it.startsWith("${g.league.id}:") }
            g.league.id in leagues && (leagueTeams.isEmpty() || g.involves(teams))
        }

    /** When to look again: soon while anything is live or about to start, otherwise every few minutes. */
    fun nextRefresh(
        games: List<Game>,
        now: Long,
    ): Long {
        if (games.any { it.state == GameState.Live }) return now + LIVE_REFRESH_MS
        val soonest = games.filter { it.state == GameState.Scheduled && it.start > now }.minOfOrNull { it.start }
        return if (soonest != null && soonest - now < IDLE_REFRESH_MS) maxOf(soonest, now + LIVE_REFRESH_MS) else now + IDLE_REFRESH_MS
    }

    /** "117–115", leader first is the caller's choice; null when not started. */
    fun score(game: Game): String? =
        if (game.state == GameState.Scheduled || game.home.score == null || game.away.score == null) {
            null
        } else {
            "${game.away.score}–${game.home.score}"
        }

    /** The first followed team in [games] that has a scheduled game, and when (to start watching). */
    fun nextStart(
        games: List<Game>,
        teams: Set<String>,
        now: Long,
    ): Long? = games.filter { it.state == GameState.Scheduled && it.start > now && it.involves(teams) }.minOfOrNull { it.start }
}
