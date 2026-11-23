package io.github.michaelmassoni.tessera.sports.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.michaelmassoni.tessera.sports.engine.Game
import io.github.michaelmassoni.tessera.sports.engine.GameState
import io.github.michaelmassoni.tessera.sports.engine.League
import io.github.michaelmassoni.tessera.sports.engine.Side
import io.github.michaelmassoni.tessera.sports.engine.Team
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The live score card's layouts as pictures, light and dark (goldens in screenshots/), since a
 * notification's RemoteViews have no @Preview.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LiveCardScreenshotTest {
    private val rockets = Team("nba", "10", "Houston Rockets", "Rockets", "HOU")
    private val nuggets = Team("nba", "7", "Denver Nuggets", "Nuggets", "DEN")

    private fun game(state: GameState) =
        Game(
            id = "1",
            league = League.NBA,
            start = 0,
            state = state,
            status = "3:20 - 4th",
            period = 4,
            clock = "3:20",
            home = Side(nuggets, "117", "10-6", state == GameState.Final),
            away = Side(rockets, "115", "8-8", false),
            venue = null,
            broadcast = null,
            lastPlay = "Nikola Jokic makes 15-foot step back jumpshot (Jamal Murray assists)",
        )

    /** A plain disc standing in for a team logo. */
    private fun logo(color: String): Bitmap =
        Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawCircle(48f, 48f, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.parseColor("#$color") })
        }

    @Suppress("DEPRECATION") // contentView/bigContentView: the only way to read back the layouts built.
    private fun capture(
        name: String,
        night: Boolean,
        state: GameState,
    ) {
        if (night) RuntimeEnvironment.setQualifiers("+night")
        val context: Context = RuntimeEnvironment.getApplication()
        val n = LiveGames.notification(context, game(state), setOf(nuggets.key), 0, logo("ce1141"), logo("0e2240"))
        val density = context.resources.displayMetrics.density
        val card =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(if (night) Color.parseColor("#2B2D31") else Color.parseColor("#F1F2F6"))
                val pad = (16 * density).toInt()
                setPadding(pad, pad, pad, pad)
            }
        val parent = FrameLayout(context)
        card.addView(n.contentView.apply(context, parent))
        card.addView(View(context), LinearLayout.LayoutParams(1, (16 * density).toInt()))
        card.addView(n.bigContentView.apply(context, parent))
        parent.addView(card)
        val width = (380 * density).toInt()
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        parent.layout(0, 0, parent.measuredWidth, parent.measuredHeight)
        val image = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
        card.draw(Canvas(image))
        image.captureRoboImage("screenshots/LiveCard_$name.png")
    }

    @Test
    fun liveLight() = capture("live_light", night = false, GameState.Live)

    @Test
    fun liveDark() = capture("live_dark", night = true, GameState.Live)

    @Test
    fun finalLight() = capture("final_light", night = false, GameState.Final)
}
