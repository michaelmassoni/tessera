package io.github.michaelmassoni.tessera.clock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.clock.R
import io.github.michaelmassoni.tessera.clock.timer.ClockTimer
import io.github.michaelmassoni.tessera.clock.timer.TimerInput
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.core.design.R as DesignR

interface TimerActions {
    fun onStart(id: Int)

    fun onPause(id: Int)

    fun onReset(id: Int)

    fun onAddMinute(id: Int)

    fun onDelete(id: Int)
}

/** Running and paused timers. [elapsedNow] ticks from the caller while the tab is visible. */
@Composable
fun TimerList(
    timers: List<ClockTimer>,
    elapsedNow: Long,
    actions: TimerActions,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(timers, key = { it.id }) { TimerCard(it, elapsedNow, actions) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimerCard(
    timer: ClockTimer,
    elapsedNow: Long,
    actions: TimerActions,
) {
    val expired = timer.state == ClockTimer.State.Expired
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = if (expired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    timer.label.ifEmpty { DurationFormat.timer(timer.durationMillis) },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { actions.onDelete(timer.id) }) {
                    Icon(
                        painterResource(DesignR.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_delete),
                    )
                }
            }
            Box(Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { timer.progress(elapsedNow) },
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    DurationFormat.timer(timer.remaining(elapsedNow)),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (expired) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                TextButton(onClick = { actions.onReset(timer.id) }, enabled = timer.state != ClockTimer.State.Reset) {
                    Text(stringResource(R.string.action_reset))
                }
                val running = timer.state == ClockTimer.State.Running
                FilledIconButton(
                    onClick = { if (running) actions.onPause(timer.id) else actions.onStart(timer.id) },
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        painterResource(if (running) DesignR.drawable.ic_pause else DesignR.drawable.ic_play_arrow),
                        contentDescription =
                            stringResource(
                                if (running) R.string.action_pause else R.string.action_start,
                            ),
                        modifier = Modifier.size(36.dp),
                    )
                }
                TextButton(
                    onClick = { actions.onAddMinute(timer.id) },
                ) { Text(stringResource(R.string.action_add_minute)) }
            }
        }
    }
}

/** The keypad: digits shift in from the right into h m s. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TimerKeypad(
    input: TimerInput,
    onInput: (TimerInput) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spoken =
        listOf(
            pluralStringResource(R.plurals.duration_hours, input.hours, input.hours),
            pluralStringResource(R.plurals.duration_minutes, input.minutes, input.minutes),
            pluralStringResource(R.plurals.duration_seconds, input.seconds, input.seconds),
        ).joinToString(", ")
    Column(
        modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        ) {
            val color = if (input.isEmpty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            listOf(
                input.hours to R.string.unit_h,
                input.minutes to R.string.unit_m,
                input.seconds to R.string.unit_s,
            ).forEach { (value, unit) ->
                Text(value.toString().padStart(2, '0'), style = MaterialTheme.typography.displayLarge, color = color)
                Text(
                    stringResource(unit),
                    style = MaterialTheme.typography.titleLarge,
                    color = color,
                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                )
            }
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", "⌫"))
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        val backspace = key == "⌫"
                        FilledTonalButton(
                            onClick = {
                                onInput(
                                    when (key) {
                                        "⌫" -> input.backspace()
                                        "00" -> input.typeDoubleZero()
                                        else -> input.type(key[0])
                                    },
                                )
                            },
                            shapes = ButtonDefaults.shapes(),
                            enabled = !backspace || !input.isEmpty,
                            modifier = Modifier.weight(1f).height(64.dp),
                        ) {
                            if (backspace) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_backspace),
                                    contentDescription = stringResource(R.string.action_backspace),
                                )
                            } else {
                                Text(key, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onStart,
            enabled = input.durationMillis > 0,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.padding(top = 24.dp).height(64.dp).widthIn(min = 160.dp),
        ) {
            Icon(painterResource(DesignR.drawable.ic_play_arrow), contentDescription = null)
            Text(
                stringResource(R.string.action_start),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private object NoTimerActions : TimerActions {
    override fun onStart(id: Int) = Unit

    override fun onPause(id: Int) = Unit

    override fun onReset(id: Int) = Unit

    override fun onAddMinute(id: Int) = Unit

    override fun onDelete(id: Int) = Unit
}

@TesseraPreviews
@Composable
private fun TimersPreview() {
    TesseraTheme(dynamicColor = false) {
        TimerList(
            timers =
                listOf(
                    ClockTimer(1, 300_000, "Pasta").start(0, 0),
                    ClockTimer(2, 90_000).start(0, 0).pause(30_000),
                ),
            elapsedNow = 120_000,
            actions = NoTimerActions,
            contentPadding = PaddingValues(16.dp),
        )
    }
}

@TesseraPreviews
@Composable
private fun TimerKeypadPreview() {
    TesseraTheme(dynamicColor = false) {
        TimerKeypad(input = TimerInput("130"), onInput = {}, onStart = {})
    }
}
