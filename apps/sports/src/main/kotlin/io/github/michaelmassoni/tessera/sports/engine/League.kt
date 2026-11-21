package io.github.michaelmassoni.tessera.sports.engine

/** A standings column: the source's stat name, and the header to show. */
data class Column(
    val stat: String,
    val header: Header,
)

/** Column headers; the UI turns them into strings. */
enum class Header { Played, Won, Lost, Drawn, Tied, Pct, GamesBehind, Streak, Percentage, Points, PointsDiff }

enum class Sport { Basketball, AustralianFootball, RugbyLeague, Baseball, AmericanFootball }

/** The leagues Sports follows, with what their standings show. */
enum class League(
    val id: String,
    /** ESPN's sport/league path. */
    val path: String,
    val sport: Sport,
    val shortName: String,
    val columns: List<Column>,
    /** The stat standings are ordered by (ascending); null orders by win percentage. */
    val rankStat: String?,
) {
    NBA(
        "nba",
        "basketball/nba",
        Sport.Basketball,
        "NBA",
        basketballColumns,
        "playoffSeed",
    ),
    WNBA("wnba", "basketball/wnba", Sport.Basketball, "WNBA", basketballColumns, "playoffSeed"),
    NBL("nbl", "basketball/nbl", Sport.Basketball, "NBL", basketballColumns, "playoffSeed"),
    AFL(
        "afl",
        "australian-football/afl",
        Sport.AustralianFootball,
        "AFL",
        listOf(
            Column("gamesPlayed", Header.Played),
            Column("wins", Header.Won),
            Column("losses", Header.Lost),
            Column("ties", Header.Drawn),
            Column("percentage", Header.Percentage),
            Column("points", Header.Points),
        ),
        "rank",
    ),
    NRL(
        "nrl",
        "rugby-league/3",
        Sport.RugbyLeague,
        "NRL",
        listOf(
            Column("gamesPlayed", Header.Played),
            Column("gamesWon", Header.Won),
            Column("gamesLost", Header.Lost),
            Column("gamesDrawn", Header.Drawn),
            Column("pointsDifference", Header.PointsDiff),
            Column("points", Header.Points),
        ),
        "rank",
    ),
    MLB(
        "mlb",
        "baseball/mlb",
        Sport.Baseball,
        "MLB",
        listOf(
            Column("wins", Header.Won),
            Column("losses", Header.Lost),
            Column("winPercent", Header.Pct),
            Column("gamesBehind", Header.GamesBehind),
            Column("streak", Header.Streak),
        ),
        null,
    ),
    NFL(
        "nfl",
        "football/nfl",
        Sport.AmericanFootball,
        "NFL",
        listOf(
            Column("wins", Header.Won),
            Column("losses", Header.Lost),
            Column("ties", Header.Tied),
            Column("winPercent", Header.Pct),
            Column("streak", Header.Streak),
        ),
        "playoffSeed",
    ),
    ;

    companion object {
        fun byId(id: String): League? = entries.firstOrNull { it.id == id }
    }
}

private val basketballColumns =
    listOf(
        Column("wins", Header.Won),
        Column("losses", Header.Lost),
        Column("winPercent", Header.Pct),
        Column("gamesBehind", Header.GamesBehind),
        Column("streak", Header.Streak),
    )
