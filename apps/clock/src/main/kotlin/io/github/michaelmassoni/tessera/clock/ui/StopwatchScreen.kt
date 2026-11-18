package io.github.michaelmassoni.tessera.clock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.clock.R
import io.github.michaelmassoni.tessera.clock.stopwatch.Stopwatch
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.core.design.R as DesignR

interface StopwatchActions {
    fun onStart()

    fun onPause()

    fun onLap()

    fun onReset()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StopwatchScreen(
    stopwatch: Stopwatch,
    elapsedNow: Long,
    actions: StopwatchActions,
    modifier: Modifier = Modifier,
) {
    val elapsed = stopwatch.elapsed(elapsedNow)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val dial: @Composable (Modifier) -> Unit = { m ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(
                    Modifier.widthIn(max = 320.dp).fillMaxWidth(0.8f).aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator(
                        progress = { (elapsed % 60_000) / 60_000f },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Text(DurationFormat.stopwatch(elapsed), style = MaterialTheme.typography.displaySmall)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    FilledTonalButton(
                        onClick = if (stopwatch.running) actions::onLap else actions::onReset,
                        enabled = !stopwatch.isReset,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Icon(
                            painterResource(
                                if (stopwatch.running) DesignR.drawable.ic_flag else DesignR.drawable.ic_restart_alt,
                            ),
                            contentDescription = null,
                        )
                        Text(
                            stringResource(if (stopwatch.running) R.string.action_lap else R.string.action_reset),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    LargeFloatingActionButton(onClick = if (stopwatch.running) actions::onPause else actions::onStart) {
                        Icon(
                            painterResource(
                                if (stopwatch.running) DesignR.drawable.ic_pause else DesignR.drawable.ic_play_arrow,
                            ),
                            contentDescription =
                                stringResource(
                                    if (stopwatch.running) R.string.action_pause else R.string.action_start,
                                ),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
        val laps: @Composable (Modifier) -> Unit = { m -> LapList(stopwatch, m) }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(16.dp)) {
                dial(Modifier.weight(1f).fillMaxSize())
                laps(Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                dial(Modifier.fillMaxWidth().weight(if (stopwatch.lapTotals.isEmpty()) 1f else 0.62f))
                if (stopwatch.lapTotals.isNotEmpty()) laps(Modifier.fillMaxWidth().weight(0.38f))
            }
        }
    }
}

@Composable
private fun LapList(
    stopwatch: Stopwatch,
    modifier: Modifier = Modifier,
) {
    val laps = stopwatch.laps
    val fastest = stopwatch.fastestLap
    val slowest = stopwatch.slowestLap
    LazyColumn(modifier.widthIn(max = 560.dp)) {
        itemsIndexed(laps.indices.reversed().toList()) { position, index ->
            if (position > 0) HorizontalDivider()
            val note =
                when (index) {
                    fastest -> stringResource(R.string.lap_fastest)
                    slowest -> stringResource(R.string.lap_slowest)
                    else -> ""
                }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.lap_number, index + 1),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    note,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DurationFormat.stopwatch(laps[index]),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
                Text(
                    DurationFormat.stopwatch(stopwatch.lapTotals[index]),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

private object NoStopwatchActions : StopwatchActions {
    override fun onStart() = Unit

    override fun onPause() = Unit

    override fun onLap() = Unit

    override fun onReset() = Unit
}

@TesseraPreviews
@Composable
private fun StopwatchPreview() {
    TesseraTheme(dynamicColor = false) {
        StopwatchScreen(
            stopwatch =
                Stopwatch()
                    .start(0)
                    .lap(61_230)
                    .lap(118_400)
                    .lap(185_020),
            elapsedNow = 201_570,
            actions = NoStopwatchActions,
        )
    }
}
