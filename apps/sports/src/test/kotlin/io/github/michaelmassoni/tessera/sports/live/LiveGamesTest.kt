package io.github.michaelmassoni.tessera.sports.live

import android.app.Notification
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.GameState
import io.github.michaelmassoni.tessera.sports.engine.League
import io.github.michaelmassoni.tessera.sports.engine.Side
import io.github.michaelmassoni.tessera.sports.engine.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** No game was live while this was written, so the Live Update notification is checked here. */
@RunWith(RobolectricTestRunner::class)
class LiveGamesTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun game(state: GameState) =
        Game(
            id = "401909312",
            league = League.NBA,
            start = 0,
            state = state,
            status = "3:20 - 4th",
            period = 4,
            clock = "3:20",
            home = Side(Team("nba", "7", "Denver Nuggets", "Nuggets", "DEN"), "117", null, state == GameState.Final),
            away = Side(Team("nba", "10", "Houston Rockets", "Rockets", "HOU"), "115", null, false),
            venue = null,
            broadcast = null,
        )

    @Test
    fun `a live game is an ongoing, public notification asking to be a Live Update`() {
        val n = LiveGames.notification(context, game(GameState.Live))
        assertEquals("HOU 115 – 117 DEN", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("3:20 - 4th", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility)
        assertEquals(LiveGames.CHANNEL, n.channelId)
        // Robolectric's SDK 37 is past Android 16 QPR1, so the promotion request is there.
        assertTrue(n.flags and Notification.FLAG_PROMOTED_ONGOING != 0 || n.extras.getBoolean("android.requestPromotedOngoing"))
    }

    @Test
    fun `a finished game leaves a dismissible final score`() {
        val n = LiveGames.notification(context, game(GameState.Final))
        assertFalse(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals("Final", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(n.timeoutAfter > 0)
    }
}
