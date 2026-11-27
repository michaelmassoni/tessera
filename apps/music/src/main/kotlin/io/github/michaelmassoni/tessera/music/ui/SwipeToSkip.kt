package io.github.michaelmassoni.tessera.music.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Swipe the album art sideways to skip (docs/music.md, Player): left for the next song, right for
 * the previous. The art follows the finger; past a quarter of its width (or a quick flick) it slides
 * off and the song changes, otherwise it springs back. TalkBack gets the same as actions.
 */
internal fun Modifier.swipeToSkip(
    state: SkipSwipeState,
    nextLabel: String,
    previousLabel: String,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier {
    val offset = state.offset
    val scope = state.scope
    return this
        .semantics {
            customActions =
                listOf(
                    CustomAccessibilityAction(nextLabel) {
                        onNext()
                        true
                    },
                    CustomAccessibilityAction(previousLabel) {
                        onPrevious()
                        true
                    },
                )
        }.pointerInput(state, onNext, onPrevious) {
            val tracker = VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = { tracker.resetTracking() },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { offset.snapTo(offset.value + amount) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                onDragEnd = {
                    val width = size.width.toFloat()
                    val velocity = tracker.calculateVelocity().x
                    val far = abs(offset.value) > width * SKIP_FRACTION
                    val flung = abs(velocity) > FLING_VELOCITY && velocity * offset.value > 0
                    scope.launch {
                        if (far || flung) {
                            val toNext = offset.value < 0
                            // Off the side it went, then the new song's art comes in from the middle.
                            offset.animateTo(if (toNext) -width else width, tween(SLIDE_MS))
                            if (toNext) onNext() else onPrevious()
                            offset.snapTo(0f)
                        } else {
                            offset.animateTo(0f, spring())
                        }
                    }
                },
            )
        }.graphicsLayer {
            translationX = offset.value
            alpha = 1f - (abs(offset.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.6f)
        }
}

/** Where the art is while it's being swiped. */
@Stable
internal class SkipSwipeState(
    internal val offset: Animatable<Float, AnimationVector1D>,
    internal val scope: CoroutineScope,
)

@Composable
internal fun rememberSkipSwipe(): SkipSwipeState {
    val scope = rememberCoroutineScope()
    return remember { SkipSwipeState(Animatable(0f), scope) }
}

private const val SKIP_FRACTION = 0.25f
private const val FLING_VELOCITY = 1_200f
private const val SLIDE_MS = 160
