package io.github.michaelmassoni.tessera.sports.live

import android.app.Notification
import io.github.michaelmassoni.tessera.sports.R
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
        // The Apple Sports-style layouts, in the system's notification frame.
        assertEquals(Notification.DecoratedCustomViewStyle::class.java.name, n.extras.getString(Notification.EXTRA_TEMPLATE))
    }

    @Test
    fun `a level game says so`() {
        val level = LiveGames.notification(context, game(GameState.Live, home = "99", away = "99"), setOf(nuggets.key), 0)
        assertEquals("3:20 - 4th · Tied", level.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Suppress("DEPRECATION") // bigContentView: the only way to read back the layout built.
    @Test
    fun `the expanded card shows scores, clock, names and the last play`() {
        val g = game(GameState.Live).copy(lastPlay = "Jokic makes 2-pt jump shot")
        val parent = android.widget.FrameLayout(context)
        val card = LiveGames.notification(context, g, setOf(nuggets.key), 0).bigContentView.apply(context, parent)

        fun text(id: Int) = card.findViewById<android.widget.TextView>(id).text.toString()
        assertEquals("115", text(R.id.away_score))
        assertEquals("117", text(R.id.home_score))
        assertEquals("3:20 - 4th", text(R.id.status))
        assertEquals("Rockets", text(R.id.away_name))
        assertEquals("Nuggets", text(R.id.home_name))
        assertEquals("NBA", text(R.id.league))
        assertEquals("Jokic makes 2-pt jump shot", text(R.id.last_play))
        assertEquals(android.view.View.GONE, card.findViewById<android.view.View>(R.id.situation).visibility)

        // Spoiler-free: no scores and no last play.
        val hidden = LiveGames.notification(context, g, setOf(nuggets.key), 0, hide = true).bigContentView.apply(context, parent)
        assertEquals(android.view.View.GONE, hidden.findViewById<android.view.View>(R.id.away_score).visibility)
        assertEquals(android.view.View.GONE, hidden.findViewById<android.view.View>(R.id.last_play).visibility)
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
