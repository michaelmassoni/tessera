package io.github.michaelmassoni.tessera.recorder.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.recorder.R
import io.github.michaelmassoni.tessera.recorder.data.Recording
import io.github.michaelmassoni.tessera.recorder.engine.Durations
import io.github.michaelmassoni.tessera.recorder.engine.Waveform
import java.util.Locale
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** One recording: its waveform to seek in, play/pause, skip 10 s, speed, rename, share, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerScreen(
    recording: Recording,
    waveform: Waveform?,
    playback: Playback,
    controls: PlaybackControls,
    actions: RecorderActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renaming by rememberSaveable { mutableStateOf(false) }
    val duration = playback.durationMs.takeIf { it > 0 } ?: recording.durationMs
    val progress = if (duration > 0) playback.positionMs.toFloat() / duration else 0f
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
                actions = {
                    IconButton(onClick = { actions.share(listOf(recording)) }) {
                        Icon(painterResource(DesignR.drawable.ic_share), contentDescription = stringResource(R.string.share))
                    }
                    IconButton(onClick = { actions.delete(listOf(recording)) }) {
                        Icon(painterResource(DesignR.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { renaming = true }.padding(vertical = 8.dp),
            ) {
                Text(
                    recording.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    painterResource(DesignR.drawable.ic_edit),
                    contentDescription = stringResource(R.string.rename),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                dateLabel(recording.dateMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            PlayerWaveform(
                waveform,
                progress,
                onSeek = { controls.seekTo((it * duration).toLong()) },
                description =
                    stringResource(
                        R.string.waveform_description,
                        Durations.clock(duration),
                        Durations.clock(playback.positionMs),
                    ),
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).height(140.dp),
            )
            Row(Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(top = 8.dp)) {
                Text(Durations.clock(playback.positionMs), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(
                    Durations.clock(duration),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                IconButton(onClick = { controls.skip(-SKIP_MS) }, modifier = Modifier.size(56.dp)) {
                    Icon(
                        painterResource(DesignR.drawable.ic_replay_10),
                        contentDescription = stringResource(R.string.back_10),
                        modifier = Modifier.size(32.dp),
                    )
                }
                FilledIconButton(onClick = controls::toggle, modifier = Modifier.size(88.dp)) {
                    Icon(
                        painterResource(if (playback.playing) DesignR.drawable.ic_pause_filled else DesignR.drawable.ic_play_arrow_filled),
                        contentDescription = stringResource(if (playback.playing) R.string.pause else R.string.play),
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { controls.skip(SKIP_MS) }, modifier = Modifier.size(56.dp)) {
                    Icon(
                        painterResource(DesignR.drawable.ic_forward_10),
                        contentDescription = stringResource(R.string.forward_10),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            TextButton(onClick = controls::nextSpeed, modifier = Modifier.padding(top = 16.dp, bottom = 32.dp)) {
                Icon(painterResource(DesignR.drawable.ic_speed), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    stringResource(R.string.speed, speedLabel(playback.speed)),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
    if (renaming) {
        // The whole name selected, so typing replaces it; the keyboard opens straight away.
        var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(recording.title, TextRange(0, recording.title.length)))
        }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.rename)) },
            text = {
                OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus))
            },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    actions.rename(recording, text.text)
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** "1×", "1.25×", "0.5×". */
internal fun speedLabel(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else "%.2f".format(Locale.ROOT, speed).trimEnd('0')
    return "$text×"
}

private const val SKIP_MS = 10_000L
