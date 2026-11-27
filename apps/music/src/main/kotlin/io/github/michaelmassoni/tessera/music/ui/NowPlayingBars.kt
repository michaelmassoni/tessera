package io.github.michaelmassoni.tessera.music.ui

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * The now-playing mark on the current song's row: three bars that rise and fall while it plays
 * and rest when paused. Decorative (the row says "now playing" to TalkBack).
 */
@Composable
internal fun NowPlayingBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "now playing")

    // Each bar has its own period, so together they never fall into step.
    @Composable
    fun bar(
        period: Int,
        rest: Float,
    ): State<Float> {
        val moving by transition.animateFloat(0.25f, 1f, spec(period), label = "bar")
        return animateFloatAsState(if (playing) moving else rest, label = "bar rest")
    }
    val heights = listOf(bar(520, 0.55f), bar(390, 0.9f), bar(610, 0.4f))
    Canvas(modifier) {
        val gap = size.width / 7
        val width = (size.width - gap * 2) / 3
        heights.forEachIndexed { i, h ->
            val height = size.height * h.value
            drawRoundRect(
                color,
                topLeft = Offset(i * (width + gap), size.height - height),
                size = Size(width, height),
                cornerRadius = CornerRadius(width / 2),
            )
        }
    }
}

private fun spec(period: Int): InfiniteRepeatableSpec<Float> = infiniteRepeatable(tween(period), RepeatMode.Reverse)
