package io.github.michaelmassoni.tessera.phone.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.phone.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Answering an incoming call by swiping (docs/phone.md, Swipe to answer): one call button to drag up
 * to answer or down to decline, so a stray touch in a pocket can't pick up. Let go before the
 * threshold and it springs back; a tap only says what to do. TalkBack gets Answer and Decline as
 * actions.
 */
@Composable
internal fun SwipeToAnswer(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { THRESHOLD.toPx() }
    var hinted by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val answerLabel = stringResource(R.string.answer)
    val declineLabel = stringResource(R.string.decline)
    val scheme = MaterialTheme.colorScheme
    // How far towards answering (negative, up) or declining (positive, down), from -1 to 1.
    val progress = (offset.value / threshold).coerceIn(-1f, 1f)
    val container =
        when {
            progress < 0 -> lerp(scheme.primaryContainer, scheme.primary, -progress)
            else -> lerp(scheme.primaryContainer, scheme.error, progress)
        }
    val content =
        when {
            progress < -0.5f -> scheme.onPrimary
            progress > 0.5f -> scheme.onError
            else -> scheme.onPrimaryContainer
        }
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(painterResource(DesignR.drawable.ic_expand_less), contentDescription = null, tint = scheme.onSurfaceVariant)
        Text(
            stringResource(if (hinted) R.string.swipe_hint else R.string.swipe_up_to_answer),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
        Box(
            Modifier
                .offset { IntOffset(0, offset.value.roundToInt()) }
                .size(80.dp)
                .background(container, CircleShape)
                .semantics {
                    contentDescription = answerLabel
                    customActions =
                        listOf(
                            CustomAccessibilityAction(answerLabel) {
                                onAnswer()
                                true
                            },
                            CustomAccessibilityAction(declineLabel) {
                                onDecline()
                                true
                            },
                        )
                }.clickable { hinted = true }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            if (!done) scope.launch { offset.snapTo((offset.value + amount).coerceIn(-threshold * 1.2f, threshold * 1.2f)) }
                        },
                        onDragEnd = {
                            when {
                                done -> {}

                                offset.value <= -threshold -> {
                                    done = true
                                    onAnswer()
                                }

                                offset.value >= threshold -> {
                                    done = true
                                    onDecline()
                                }

                                else -> {
                                    if (abs(offset.value) < threshold / 4) hinted = true
                                    scope.launch { offset.animateTo(0f, spring()) }
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(DesignR.drawable.ic_call), contentDescription = null, tint = content, modifier = Modifier.size(32.dp))
        }
        Text(stringResource(R.string.swipe_down_to_decline), style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Icon(painterResource(DesignR.drawable.ic_expand_more), contentDescription = null, tint = scheme.onSurfaceVariant)
    }
}

private val THRESHOLD = 120.dp
