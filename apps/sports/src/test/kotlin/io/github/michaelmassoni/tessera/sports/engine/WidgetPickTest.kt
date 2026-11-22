package io.github.michaelmassoni.tessera.sports.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPickTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private val mine = Team("nba", "10", "Houston Rockets", "Rockets", "HOU")
    private val other = Team("nba", "7", "Denver Nuggets", "Nuggets", "DEN")

    private fun game(
        id: String,
        start: Long,
        state: GameState,
        home: Team = mine,
    ) = Game(id, League.byId("nba")!!, start, state, "", 0, null, Side(home, null, null, false), Side(other, null, null, false), null, null)

    @Test fun `live first, then the next game this week, then a recent result`() {
        val teams = setOf(mine.key)
        val result = game("r", now - day, GameState.Final)
        val next = game("n", now + 2 * day, GameState.Scheduled)
        val live = game("l", now - 3_600_000, GameState.Live)
        val notMine = game("x", now, GameState.Live, home = Team("nba", "1", "A", "A", "A"))
        assertEquals("l", WidgetPick.game(listOf(result, next, live, notMine), teams, now)?.id)
        assertEquals("n", WidgetPick.game(listOf(result, next), teams, now)?.id)
        assertEquals("r", WidgetPick.game(listOf(result, game("far", now + 9 * day, GameState.Scheduled)), teams, now)?.id)
        assertNull(WidgetPick.game(listOf(game("old", now - 5 * day, GameState.Final)), teams, now))
    }
}
