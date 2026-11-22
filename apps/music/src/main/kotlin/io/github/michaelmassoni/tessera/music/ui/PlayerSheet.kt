package io.github.michaelmassoni.tessera.music.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * The full player as a sheet over [content]: [content] opens it (the mini player), and it slides
 * up from the bottom. It follows a finger dragged down (from its top bar, or from anywhere once
 * its content is scrolled to the top) and a predictive back gesture, and on release slides away or
 * back depending on how far and how fast it went.
 */
@Composable
internal fun PlayerSheet(
    hasMedia: Boolean,
    player: @Composable (onClose: () -> Unit, modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (openPlayer: () -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // 0 is closed (below the screen), 1 is open.
    var fraction by remember { mutableFloatStateOf(if (open) 1f else 0f) }
    val scope = rememberCoroutineScope()
    var settling by remember { mutableStateOf<Job?>(null) }

    fun slideTo(
        target: Float,
        velocity: Float = 0f,
    ) {
        settling?.cancel()
        open = target == 1f
        settling =
            scope.launch {
                // Stiff enough not to creep over the last few pixels; no bounce.
                val motion = spring(Spring.DampingRatioNoBouncy, STIFFNESS, visibilityThreshold = 0.001f)
                animate(fraction, target, velocity, motion) { v, _ -> fraction = v }
            }
    }
    LaunchedEffect(hasMedia) {
        if (!hasMedia) {
            open = false
            fraction = 0f
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        fun dragBy(dy: Float) {
            settling?.cancel()
            fraction = (fraction - dy / height).coerceIn(0f, 1f)
        }

        // Released: a quick flick decides; otherwise more than a quarter of the way down closes it.
        fun release(velocityY: Float) {
            val target =
                when {
                    velocityY > FLING -> 0f
                    velocityY < -FLING -> 1f
                    fraction < 0.75f -> 0f
                    else -> 1f
                }
            slideTo(target, -velocityY / height)
        }

        content { slideTo(1f) }

        if (hasMedia && fraction > 0f) {
            PredictiveBackHandler(enabled = open) { events ->
                try {
                    events.collect { fraction = 1f - it.progress * 0.3f }
                    slideTo(0f)
                } catch (e: CancellationException) {
                    slideTo(1f)
                    throw e
                }
            }
            val nested =
                remember(height) {
                    object : NestedScrollConnection {
                        // Pulled down part way, scrolling up puts the sheet back before the content scrolls.
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            if (fraction < 1f && available.y < 0) {
                                dragBy(available.y)
                                return Offset(0f, available.y)
                            }
                            return Offset.Zero
                        }

                        // Content already at the top: a further pull down moves the sheet.
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {
                            if (source == NestedScrollSource.UserInput && available.y > 0) {
                                dragBy(available.y)
                                return Offset(0f, available.y)
                            }
                            return Offset.Zero
                        }

                        override suspend fun onPreFling(available: Velocity): Velocity {
                            if (fraction < 1f) {
                                release(available.y)
                                return available
                            }
                            return Velocity.Zero
                        }
                    }
                }
            player(
                { slideTo(0f) },
                Modifier
                    .offset { IntOffset(0, ((1f - fraction) * height).roundToInt()) }
                    .nestedScroll(nested)
                    .draggable(
                        rememberDraggableState { dragBy(it) },
                        Orientation.Vertical,
                        onDragStopped = { release(it) },
                    ),
            )
        }
    }
}

private const val STIFFNESS = 900f

/** A flick faster than this (px/s) opens or closes the sheet whatever its position. */
private const val FLING = 1500f
