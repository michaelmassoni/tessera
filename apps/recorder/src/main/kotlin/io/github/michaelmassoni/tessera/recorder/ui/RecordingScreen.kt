package io.github.michaelmassoni.tessera.recorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.recorder.R
import io.github.michaelmassoni.tessera.recorder.engine.Durations
import io.github.michaelmassoni.tessera.recorder.engine.RecorderState
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Full screen while recording: the time, the live waveform, Pause/Resume, Stop and Discard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordingScreen(
    state: RecorderState.Active,
    live: FloatArray,
    actions: RecorderActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var discarding by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.weight(1f))
            // Read out every 10 seconds, not every tenth.
            val spoken = Durations.clock(state.elapsedMs / 10_000 * 10_000)
            Text(
                Durations.clockTenths(state.elapsedMs),
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
                modifier =
                    Modifier.clearAndSetSemantics {
                        contentDescription = spoken
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            Text(
                when {
                    state.silenced -> stringResource(R.string.status_silenced)
                    state.paused -> stringResource(R.string.status_paused)
                    else -> stringResource(R.string.status_recording)
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (state.silenced) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            LiveWaveform(
                live,
                stringResource(R.string.live_waveform),
                Modifier.fillMaxWidth().widthIn(max = 720.dp).height(160.dp),
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                OutlinedIconButton(onClick = { discarding = true }, modifier = Modifier.size(64.dp)) {
                    Icon(painterResource(DesignR.drawable.ic_delete), contentDescription = stringResource(R.string.discard))
                }
                FilledIconButton(
                    onClick = if (state.paused) actions::resume else actions::pause,
                    modifier = Modifier.size(96.dp),
                ) {
                    Icon(
                        painterResource(if (state.paused) DesignR.drawable.ic_mic else DesignR.drawable.ic_pause_filled),
                        contentDescription = stringResource(if (state.paused) R.string.resume else R.string.pause),
                        modifier = Modifier.size(40.dp),
                    )
                }
                FilledTonalIconButton(onClick = actions::stop, modifier = Modifier.size(64.dp)) {
                    Icon(painterResource(DesignR.drawable.ic_stop), contentDescription = stringResource(R.string.stop_and_save))
                }
            }
        }
    }
    if (discarding) {
        AlertDialog(
            onDismissRequest = { discarding = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_text)) },
            confirmButton = {
                TextButton(onClick = {
                    discarding = false
                    actions.discard()
                }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = { TextButton(onClick = { discarding = false }) { Text(stringResource(R.string.keep_recording)) } },
        )
    }
}
