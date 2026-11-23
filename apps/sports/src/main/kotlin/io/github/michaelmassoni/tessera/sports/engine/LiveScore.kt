package io.github.michaelmassoni.tessera.sports.engine

/** What a live game's notification needs beyond the score: how far through it is, and who leads. */
object LiveScore {
    /** Regulation periods: quarters, halves or innings. */
    fun periods(league: League): Int =
        when (league.sport) {
            Sport.RugbyLeague -> 2
            Sport.Baseball -> 9
            else -> 4
        }

    /** Minutes in a period; null for baseball, which has no clock. */
    fun periodMinutes(league: League): Double? =
        when (league) {
            League.NBA -> 12.0
            League.WNBA, League.NBL -> 10.0
            League.NFL -> 15.0
            League.AFL -> 20.0
            League.NRL -> 40.0
            League.MLB -> null
        }

    /** Basketball and American football clocks count down; AFL and NRL count the time played. */
    private fun countsDown(league: League) = league.sport == Sport.Basketball || league.sport == Sport.AmericanFootball

    /** "3:20" → 3.33 minutes, "45.2" → 0.75, "54'" → 54. */
    fun minutes(clock: String?): Double? {
        val c = clock?.trim()?.removeSuffix("'")?.takeIf { it.isNotEmpty() } ?: return null
        val parts = c.split(":")
        return when (parts.size) {
            1 -> c.toDoubleOrNull()?.let { if ('.' in c) it / 60 else it }
            2 -> parts[0].toIntOrNull()?.let { m -> parts[1].toDoubleOrNull()?.let { s -> m + s / 60 } }
            else -> null
        }
    }

    /**
     * How much of regulation has been played, in periods (0 to [periods]): from the period and the
     * clock, or for baseball the inning and its half. Overtime counts as all of it.
     */
    fun elapsed(game: Game): Float {
        val total = periods(game.league)
        if (game.state == GameState.Final) return total.toFloat()
        if (game.state != GameState.Live || game.period <= 0) return 0f
        if (game.period > total) return total.toFloat()
        val done = (game.period - 1).toFloat()
        val length = periodMinutes(game.league)
        val within =
            if (length == null) {
                // Baseball: the top half is the first half of the inning.
                val s = game.status.lowercase()
                if (s.startsWith("bot") || s.startsWith("mid") || s.startsWith("end")) 0.5f else 0.25f
            } else {
                val clock = minutes(game.clock) ?: return done
                val played = if (countsDown(game.league)) length - clock else clock
                (played / length).toFloat()
            }
        return (done + within.coerceIn(0f, 1f)).coerceIn(0f, total.toFloat())
    }

    private fun Side.points() = score?.toIntOrNull()

    /** The side ahead, or null when level (or not scored yet). */
    fun leader(game: Game): Side? {
        val home = game.home.points() ?: return null
        val away = game.away.points() ?: return null
        return when {
            home > away -> game.home
            away > home -> game.away
            else -> null
        }
    }

    /** How far ahead (positive) or behind the team with [key] is; null without scores. */
    fun margin(
        game: Game,
        key: String,
    ): Int? {
        val home = game.home.points() ?: return null
        val away = game.away.points() ?: return null
        return when (key) {
            game.home.team.key -> home - away
            game.away.team.key -> away - home
            else -> null
        }
    }

    /** The team the notification is about: a followed one (home first when both are). */
    fun yours(
        game: Game,
        followed: Set<String>,
    ): Side = if (game.away.team.key in followed && game.home.team.key !in followed) game.away else game.home

    /**
     * [fresh]'s versions of [live] games (matched by id); a game missing from them keeps its old
     * state, so a scoreboard that dropped it doesn't end the live score.
     */
    fun update(
        live: List<Game>,
        fresh: List<Game>,
    ): List<Game> {
        val byId = fresh.associateBy { it.league to it.id }
        return live.map { byId[it.league to it.id] ?: it }
    }
}
