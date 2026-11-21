package io.github.michaelmassoni.tessera.sports.live

import android.app.Notification
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.GameState
import io.github.michaelmassoni.tessera.sports.engine.League
import io.github.michaelmassoni.tessera.sports.engine.LiveScore
import io.github.michaelmassoni.tessera.sports.engine.Side
import io.github.michaelmassoni.tessera.sports.engine.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** No game was live while this was written, so the Live Update notification is checked here. */
@RunWith(RobolectricTestRunner::class)
class LiveGamesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val nuggets = Team("nba", "7", "Denver Nuggets", "Nuggets", "DEN")
    private val rockets = Team("nba", "10", "Houston Rockets", "Rockets", "HOU")

    private fun game(
        state: GameState,
        home: String = "117",
        away: String = "115",
        period: Int = 4,
        clock: String? = "3:20",
        league: League = League.NBA,
        status: String = "3:20 - 4th",
    ) = Game(
        id = "401909312",
        league = league,
        start = 0,
        state = state,
        status = status,
        period = period,
        clock = clock,
        home = Side(nuggets, home, null, state == GameState.Final && home.toInt() > away.toInt()),
        away = Side(rockets, away, null, false),
        venue = null,
        broadcast = null,
    )

    @Test
    fun `each team sits beside its own score, and the text says who leads`() {
        val n = LiveGames.notification(context, game(GameState.Live), setOf(nuggets.key), updatedAt = 1_000L)
        assertEquals("Rockets 115 · Nuggets 117", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("3:20 - 4th · Nuggets by 2", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(1_000L, n.`when`)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility)
        assertEquals(LiveGames.CHANNEL, n.channelId)
        // Robolectric's SDK 37 is past Android 16 QPR1: a Live Update, with the bar and the chip.
        assertTrue(n.flags and Notification.FLAG_PROMOTED_ONGOING != 0 || n.extras.getBoolean("android.requestPromotedOngoing"))
        assertEquals(Notification.ProgressStyle::class.java.name, n.extras.getString(Notification.EXTRA_TEMPLATE))
        assertEquals("DEN +2", n.shortCriticalText)
    }

    @Test
    fun `the chip follows your team, behind or level`() {
        assertEquals("HOU −2", LiveGames.notification(context, game(GameState.Live), setOf(rockets.key), 0).shortCriticalText)
        val level = LiveGames.notification(context, game(GameState.Live, home = "99", away = "99"), setOf(nuggets.key), 0)
        assertEquals("DEN =", level.shortCriticalText)
        assertEquals("3:20 - 4th · Tied", level.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `a finished game leaves a dismissible final score`() {
        val n = LiveGames.notification(context, game(GameState.Final), setOf(nuggets.key), 0)
        assertFalse(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals("Final · Nuggets win", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(n.timeoutAfter > 0)
    }

    @Test
    fun `progress follows the clock, a period at a time`() {
        // 3:20 left in the 4th of 12-minute quarters: 3 quarters and 8:40 of 12 played.
        assertEquals(3 + 8.666f / 12, LiveScore.elapsed(game(GameState.Live)), 0.01f)
        assertEquals(0f, LiveScore.elapsed(game(GameState.Scheduled)), 0f)
        assertEquals(4f, LiveScore.elapsed(game(GameState.Live, period = 5, clock = "2:00")), 0f)
        // AFL's clock counts time played in a 20-minute quarter.
        assertEquals(1.5f, LiveScore.elapsed(game(GameState.Live, period = 2, clock = "10:00", league = League.AFL)), 0.01f)
        // Baseball: the bottom of the 7th is half way through it.
        assertEquals(6.5f, LiveScore.elapsed(game(GameState.Live, period = 7, clock = null, league = League.MLB, status = "Bot 7th")), 0f)
        assertEquals(45.2 / 60, LiveScore.minutes("45.2")!!, 1e-9)
        assertEquals(54.0, LiveScore.minutes("54'")!!, 0.0)
        assertNull(LiveScore.minutes(""))
    }

    @Test
    fun `fresh scores replace the old ones, and a game missing from them stays`() {
        val old = game(GameState.Live)
        val fresh = game(GameState.Live, home = "120")
        assertEquals(
            "120",
            LiveScore
                .update(listOf(old), listOf(fresh))
                .single()
                .home.score,
        )
        assertEquals(
            "117",
            LiveScore
                .update(listOf(old), emptyList())
                .single()
                .home.score,
        )
    }
}
