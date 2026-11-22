package io.github.michaelmassoni.tessera.recorder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.recorder.engine.Waveform
import kotlin.math.max

private val BarWidth = 3.dp
private val BarGap = 2.dp

/**
 * The live waveform while recording: the newest level at the right edge, older ones scrolling
 * left. [levels] are 0..1, newest last.
 */
@Composable
fun LiveWaveform(
    levels: FloatArray,
    description: String,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val quiet = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.semantics { contentDescription = description }) {
        val step = (BarWidth + BarGap).toPx()
        val count = (size.width / step).toInt()
        val mid = size.height / 2
        val stroke = BarWidth.toPx()
        // A faint centre line where there's no sound yet.
        drawLine(quiet, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        val shown = minOf(count, levels.size)
        for (i in 0 until shown) {
            val level = levels[levels.size - shown + i]
            val x = size.width - (shown - i) * step + stroke / 2
            val half = max(stroke / 2, level * (size.height / 2 - stroke))
            drawLine(color, Offset(x, mid - half), Offset(x, mid + half), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/**
 * A recording's waveform with the played part in the accent colour. Tap or drag to seek:
 * [onSeek] gets a fraction 0..1. Without a [waveform], an even line stands in for it.
 */
@Composable
fun PlayerWaveform(
    waveform: Waveform?,
    progress: Float,
    onSeek: (Float) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.outlineVariant
    val seek by rememberUpdatedState(onSeek)
    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) { detectTapGestures { seek((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> seek((change.position.x / size.width).coerceIn(0f, 1f)) }
            },
    ) {
        val step = (BarWidth + BarGap).toPx()
        val count = max(1, (size.width / step).toInt())
        val bars = waveform?.bars(count) ?: FloatArray(count) { 0.08f }
        val mid = size.height / 2
        val stroke = BarWidth.toPx()
        val playedBars = (progress.coerceIn(0f, 1f) * count).toInt()
        for (i in 0 until count) {
            val x = i * step + stroke / 2
            val half = max(stroke / 2, bars[i] * (size.height / 2 - stroke))
            val color = if (i < playedBars) played else rest
            drawLine(
                color,
                Offset(x, mid - half),
                Offset(x, mid + half),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
