package io.github.michaelmassoni.tessera.sports.ui

import io.github.michaelmassoni.tessera.sports.engine.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PageKeysTest {
    private fun team(
        league: String,
        id: String,
    ) = Team(league = league, id = id, name = "Team $id", shortName = id, abbreviation = id)

    @Test
    fun `a team's page key is stable and different per team and league`() {
        assertEquals(teamPageKey(team("nba", "10")), teamPageKey(team("nba", "10").copy(name = "Renamed")))
        assertNotEquals(teamPageKey(team("nba", "10")), teamPageKey(team("nba", "11")))
        assertNotEquals(teamPageKey(team("nba", "10")), teamPageKey(team("nfl", "10")))
    }
}
