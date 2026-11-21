package io.github.michaelmassoni.tessera.sports.engine

import kotlinx.serialization.Serializable

/** A team, as followed and shown. [key] is unique across leagues. */
@Serializable
data class Team(
    val league: String,
    val id: String,
    val name: String,
    val shortName: String,
    val abbreviation: String,
    val logo: String? = null,
    /** "c8102e", the team's colour from the source (data, not a theme colour). */
    val color: String? = null,
) {
    val key: String get() = "$league:$id"
}

enum class GameState { Scheduled, Live, Final, Postponed }

/** One side of a game. */
data class Side(
    val team: Team,
    val score: String?,
    /** "10-6" (W-L) or the source's summary of the record. */
    val record: String?,
    val winner: Boolean,
)

data class Game(
    val id: String,
    val league: League,
    /** Epoch ms. */
    val start: Long,
    val state: GameState,
    /** The source's short status: "Q3 4:12", "3:20 - 4th", "Final", "Half". */
    val status: String,
    val period: Int,
    val clock: String?,
    val home: Side,
    val away: Side,
    val venue: String?,
    val broadcast: String?,
) {
    fun involves(teamKeys: Set<String>) = home.team.key in teamKeys || away.team.key in teamKeys
}

/** A row of a standings table: the team and its values for the league's columns. */
data class StandingsRow(
    val position: Int,
    val team: Team,
    val values: List<String>,
)

data class StandingsGroup(
    val name: String,
    val rows: List<StandingsRow>,
)

/** A team's line in the game detail: scores per period. */
data class LineScore(
    val team: Team,
    val periods: List<String>,
    val total: String?,
)

/** A stat leader: "Points: N. Jokić 34". */
data class Leader(
    val category: String,
    val player: String,
    val value: String,
)

data class GameDetail(
    val game: Game,
    val lines: List<LineScore>,
    /** Per team (keyed by team id), the leading players. */
    val leaders: Map<String, List<Leader>>,
)
