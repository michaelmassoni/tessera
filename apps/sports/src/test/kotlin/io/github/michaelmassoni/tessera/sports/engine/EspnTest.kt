package io.github.michaelmassoni.tessera.sports.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class EspnTest {
    private fun fixture(name: String) = requireNotNull(javaClass.classLoader?.getResource(name)) { "missing $name" }.readText()

    // ---- Parsing real ESPN responses ----

    @Test
    fun `an NBA scoreboard parses live and finished games`() {
        val games = Espn.parseScoreboard(League.NBA, fixture("nba_scoreboard.json"))
        assertEquals(9, games.size)
        val live = games.filter { it.state == GameState.Live }
        assertEquals(2, live.size)
        val den = live.first { it.home.team.abbreviation == "DEN" }
        assertEquals("117", den.home.score)
        assertEquals("115", den.away.score)
        assertEquals(4, den.period)
        assertEquals("3:20 - 4th", den.status)
        val atl = games.first { it.home.team.abbreviation == "ATL" }
        assertEquals(GameState.Final, atl.state)
        assertTrue(atl.home.winner)
        assertEquals("10-6", atl.home.record)
        assertEquals("nba:1", atl.home.team.key)
        assertNotNull(atl.home.team.logo)
        assertEquals("c8102e", atl.home.team.color)
        assertTrue(atl.start > 0)
    }

    @Test
    fun `an AFL scoreboard parses fixtures`() {
        val games = Espn.parseScoreboard(League.AFL, fixture("afl_scoreboard.json"))
        assertTrue(games.isNotEmpty())
        // This round hadn't started when the fixture was saved.
        assertTrue(games.all { it.state == GameState.Scheduled })
        assertTrue(games.all { it.league == League.AFL && it.home.team.league == "afl" })
    }

    @Test
    fun `a team schedule parses scores given as objects`() {
        val games = Espn.parseSchedule(League.NBA, fixture("heat_schedule.json"))
        assertEquals(20, games.size)
        val first = games.first()
        assertEquals(GameState.Final, first.state)
        assertEquals("106", first.home.score)
        assertEquals("131", first.away.score)
        assertTrue(first.involves(setOf("nba:14")))
    }

    @Test
    fun `teams parse sorted by name`() {
        val teams = Espn.parseTeams(League.NBA, fixture("nba_teams.json"))
        assertEquals(30, teams.size)
        assertEquals(teams.sortedBy { it.name }, teams)
        assertTrue(teams.any { it.name == "Miami Heat" && it.id == "14" })
    }

    @Test
    fun `NBA standings come as conferences ordered by seed`() {
        val groups = Espn.parseStandings(League.NBA, fixture("nba_standings.json"))
        assertEquals(listOf("Eastern Conference", "Western Conference"), groups.map { it.name })
        val east = groups.first()
        assertEquals((1..east.rows.size).toList(), east.rows.map { it.position })
        assertEquals(
            League.NBA.columns.size,
            east.rows
                .first()
                .values.size,
        )
    }

    @Test
    fun `the AFL ladder reads played, wins, losses, draws, percentage and points`() {
        val groups = Espn.parseStandings(League.AFL, fixture("afl_standings.json"))
        assertEquals(1, groups.size)
        val top = groups.single().rows.first()
        assertEquals("Fremantle", top.team.name)
        assertEquals(listOf("23", "19", "4", "0", "137.200", "76"), top.values)
    }

    @Test
    fun `the NRL ladder uses rugby league's stat names`() {
        val top =
            Espn
                .parseStandings(League.NRL, fixture("nrl_standings.json"))
                .single()
                .rows
                .first()
        assertEquals("Panthers", top.team.name)
        assertEquals(listOf("24", "18", "6", "0", "+336", "42"), top.values)
    }

    @Test
    fun `a game summary gives line scores and leaders`() {
        val detail = Espn.parseSummary(League.NBA, fixture("nba_summary.json"))!!
        assertEquals(GameState.Live, detail.game.state)
        assertEquals(2, detail.lines.size)
        assertEquals(listOf("31", "31", "37", "18"), detail.lines.first { it.team.abbreviation == "DEN" }.periods)
        assertEquals(2, detail.leaders.size)
        assertTrue(detail.leaders.values.all { it.isNotEmpty() })
    }

    @Test
    fun `urls ask for one day`() {
        val url = Espn.scoreboardUrl(League.AFL, LocalDate.of(2026, 11, 18))
        assertTrue(url, url.endsWith("/australian-football/afl/scoreboard?dates=20261118"))
        assertTrue(Espn.standingsUrl(League.NRL).endsWith("/apis/v2/sports/rugby-league/3/standings"))
    }

    // ---- Sections, following, refresh ----

    private val zone = ZoneId.of("Australia/Melbourne")
    private val now =
        LocalDate
            .of(2026, 11, 21)
            .atTime(12, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun team(
        id: String,
        league: String = "nba",
    ) = Team(league, id, "Team $id", id, id)

    private fun game(
        id: String,
        hoursFromNow: Long,
        state: GameState = GameState.Scheduled,
        home: String = "1",
        away: String = "2",
        league: League = League.NBA,
    ) = Game(
        id,
        league,
        now + hoursFromNow * 3_600_000L,
        state,
        "",
        0,
        null,
        Side(team(home, league.id), null, null, false),
        Side(team(away, league.id), null, null, false),
        null,
        null,
    )

    @Test
    fun `games sort into live, today, upcoming and recent`() {
        val games =
            listOf(
                game("past", -30, GameState.Final),
                game("older", -60, GameState.Final),
                game("live", -1, GameState.Live),
                game("tonight", 6),
                game("earlier today", -3, GameState.Final),
                game("tomorrow", 24),
                game("next week", 24 * 6),
            )
        val s = Scores.sections(games, now, zone)
        assertEquals(listOf(Section.Live, Section.Today, Section.Upcoming, Section.Recent), s.map { it.section })
        assertEquals(listOf("live"), s[0].games.map { it.id })
        assertEquals(listOf("earlier today", "tonight"), s[1].games.map { it.id })
        assertEquals(listOf("tomorrow", "next week"), s[2].games.map { it.id })
        assertEquals(listOf("past", "older"), s[3].games.map { it.id })
    }

    @Test
    fun `games sort into the yesterday, today and upcoming tabs`() {
        val games =
            listOf(
                game("yesterday late", -16, GameState.Final),
                game("yesterday early", -20, GameState.Final),
                game("two days ago", -40, GameState.Final),
                game("live since last night", -13, GameState.Live),
                game("tonight", 6),
                game("this morning", -3, GameState.Final),
                game("tomorrow", 24),
                game("next week", 24 * 6),
            )
        val today = Scores.tab(games, ScoreTab.Today, now, zone)
        assertEquals(listOf("live since last night", "this morning", "tonight"), today.single().games.map { it.id })
        val yesterday = Scores.tab(games, ScoreTab.Yesterday, now, zone)
        assertEquals(listOf(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 19)), yesterday.map { it.date })
        assertEquals(listOf("yesterday early", "yesterday late"), yesterday[0].games.map { it.id })
        val upcoming = Scores.tab(games, ScoreTab.Upcoming, now, zone)
        assertEquals(listOf(listOf("tomorrow"), listOf("next week")), upcoming.map { day -> day.games.map { it.id } })
        assertTrue(Scores.tab(emptyList(), ScoreTab.Today, now, zone).isEmpty())
    }

    @Test
    fun `dark logos come from the source, or from ESPN's dark logo path`() {
        val nba = Espn.parseTeams(League.NBA, fixture("nba_teams.json")).first()
        assertEquals("https://a.espncdn.com/i/teamlogos/nba/500-dark/atl.png", nba.logoOnDark())
        // A team saved before dark logos were read: ESPN keeps them beside the light ones.
        val saved = Team("nbl", "6", "New Zealand Breakers", "Breakers", "NZL", "https://a.espncdn.com/i/teamlogos/nbl/500/nzl.png")
        assertEquals("https://a.espncdn.com/i/teamlogos/nbl/500-dark/nzl.png", saved.logoOnDark())
        // Another source's logo is used as it is.
        assertEquals("https://example.org/a.png", Team("x", "1", "A", "A", "A", "https://example.org/a.png").logoOnDark())
    }

    @Test
    fun `following a team narrows its league, and a league with none followed shows everything`() {
        val games = listOf(game("heat", 2, home = "14"), game("other", 3), game("afl", 4, league = League.AFL))
        val shown = Scores.followed(games, teams = setOf("nba:14"), leagues = setOf("nba", "afl"))
        assertEquals(listOf("heat", "afl"), shown.map { it.id })
        assertTrue(Scores.followed(games, setOf("nba:14"), setOf("afl")).none { it.league == League.NBA })
    }

    @Test
    fun `refresh is quick while live or about to start, slow otherwise`() {
        assertEquals(now + Scores.LIVE_REFRESH_MS, Scores.nextRefresh(listOf(game("l", 0, GameState.Live)), now))
        assertEquals(now + Scores.IDLE_REFRESH_MS, Scores.nextRefresh(listOf(game("s", 5)), now))
        val soon = now + 2 * 60_000L
        assertEquals(soon, Scores.nextRefresh(listOf(game("s", 0).copy(start = soon)), now))
    }

    @Test
    fun `the next start is the soonest scheduled game of a followed team`() {
        val games = listOf(game("a", 5, home = "14"), game("b", 2), game("c", 9, away = "14"))
        assertEquals(now + 5 * 3_600_000L, Scores.nextStart(games, setOf("nba:14"), now))
        assertEquals(null, Scores.nextStart(games, setOf("nba:99"), now))
        assertFalse(games[1].involves(setOf("nba:14")))
    }
}
