package io.github.michaelmassoni.tessera.clock.ringing

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michaelmassoni.tessera.clock.R
import io.github.michaelmassoni.tessera.clock.graph
import io.github.michaelmassoni.tessera.clock.ui.DurationFormat
import io.github.michaelmassoni.tessera.clock.ui.formatClockTime
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import kotlinx.coroutines.delay

/**
 * Full-screen ringing UI, shown over the lock screen by the ringing notification's full-screen
 * intent. Back is ignored so a stray gesture can't silence an alarm; use the buttons.
 */
class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val graph = graph
        setContent {
            TesseraTheme {
                val ringing by graph.ringing.state.collectAsStateWithLifecycle()
                val current = ringing
                if (current == null) {
                    LaunchedEffect(Unit) { finish() }
                    return@TesseraTheme
                }
                val timer = (current as? Ringing.Timer)?.let { graph.timers.get(it.id) }
                BackHandler { /* ignore: dismiss with the buttons */ }
                RingingScreen(
                    ringing = current,
                    timerEndElapsed = timer?.endElapsed,
                    onPrimary = { send(RingingService.ACTION_DISMISS) },
                    onSecondary = {
                        send(
                            if (current is Ringing.Alarm) RingingService.ACTION_SNOOZE else RingingService.ACTION_ADD_MINUTE,
                        )
                    },
                )
            }
        }
    }

    private fun send(action: String) {
        startService(RingingService.command(this, action))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RingingScreen(
    ringing: Ringing,
    timerEndElapsed: Long?,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (ringing) {
                is Ringing.Alarm -> {
                    Text(
                        text = ringing.label.ifEmpty { stringResource(R.string.alarm_default_label) },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = formatClockTime(ringing.hour, ringing.minute),
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }

                is Ringing.Timer -> {
                    Text(
                        text = stringResource(R.string.timer_times_up),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(250)
                            now = SystemClock.elapsedRealtime()
                        }
                    }
                    Text(
                        text = DurationFormat.timer((timerEndElapsed ?: now) - now),
                        style = MaterialTheme.typography.displayLarge,
                    )
                    if (ringing.label.isNotEmpty()) Text(ringing.label, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(64.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            ) {
                FilledTonalButton(
                    onClick = onSecondary,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(96.dp),
                ) {
                    Text(
                        stringResource(
                            if (ringing is Ringing.Alarm) R.string.action_snooze else R.string.action_add_minute,
                        ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Button(
                    onClick = onPrimary,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(96.dp),
                ) {
                    Text(
                        stringResource(if (ringing is Ringing.Alarm) R.string.action_dismiss else R.string.action_stop),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

@TesseraPreviews
@Composable
private fun RingingAlarmPreview() {
    TesseraTheme(dynamicColor = false) {
        RingingScreen(Ringing.Alarm(1, "Gym", 6, 45, null, true), null, {}, {})
    }
}
