package io.github.michaelmassoni.tessera.sports.engine

/** Which game the widget shows. */
object WidgetPick {
    /**
     * A followed team's live game; else its next game in the coming week; else its latest result
     * from the last three days. Null when there's none.
     */
    fun game(
        games: List<Game>,
        teams: Set<String>,
        now: Long,
    ): Game? {
        val mine = games.filter { it.involves(teams) }
        return mine.firstOrNull { it.state == GameState.Live }
            ?: mine.filter { it.state == GameState.Scheduled && it.start >= now - HOUR && it.start <= now + WEEK }.minByOrNull { it.start }
            ?: mine.filter { it.state == GameState.Final && it.start >= now - 3 * DAY }.maxByOrNull { it.start }
    }

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR
    private const val WEEK = 7 * DAY
}
