package io.github.michaelmassoni.tessera.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.phone.R
import io.github.michaelmassoni.tessera.phone.call.AudioInfo
import io.github.michaelmassoni.tessera.phone.call.CallInfo
import io.github.michaelmassoni.tessera.phone.engine.Dialpad
import io.github.michaelmassoni.tessera.phone.engine.formatTimer
import kotlinx.coroutines.delay
import io.github.michaelmassoni.tessera.core.design.R as DesignR

interface InCallActions {
    fun answer(id: String)

    fun decline(id: String)

    fun hangUp(id: String)

    fun hold(
        id: String,
        on: Boolean,
    )

    fun mute(on: Boolean)

    fun speaker(on: Boolean)

    fun dtmf(
        id: String,
        digit: Char,
    )
}

/** The call screen: who, state and timer, then answer/decline or the in-call controls. */
@Composable
fun InCallScreen(
    calls: List<CallInfo>,
    audio: AudioInfo,
    actions: InCallActions,
    modifier: Modifier = Modifier,
) {
    // A ringing call takes over the screen; otherwise the first live call (or the one that just ended).
    val call = calls.firstOrNull { it.ringing } ?: calls.firstOrNull { !it.ended } ?: calls.firstOrNull()
    var keypad by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (call == null) return@Box
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.size(48.dp))
            Box(
                Modifier.size(112.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_person),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(64.dp),
                )
            }
            Text(
                call.name ?: call.number.ifBlank {
                    stringResource(R.string.unknown_number)
                },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp),
            )
            if (call.name !=
                null
            ) {
                Text(call.number, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CallStatus(call)
            Spacer(Modifier.weight(1f))
            when {
                call.ringing -> IncomingControls(call, actions)
                call.ended -> Unit
                keypad -> InCallKeypad(onDigit = { actions.dtmf(call.id, it) }, onClose = { keypad = false })
                else -> ActiveControls(call, audio, actions, onKeypad = { keypad = true })
            }
            if (!call.ringing && !call.ended) {
                FilledIconButton(
                    onClick = { actions.hangUp(call.id) },
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    modifier = Modifier.padding(top = 32.dp).size(72.dp),
                ) { Icon(painterResource(DesignR.drawable.ic_call_end), contentDescription = stringResource(R.string.hang_up)) }
            }
        }
    }
}

@Composable
private fun CallStatus(call: CallInfo) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call.connectedAt) {
        while (call.connectedAt > 0) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val text =
        when {
            call.ringing -> stringResource(R.string.state_incoming)
            call.holding -> stringResource(R.string.state_holding)
            call.ended -> stringResource(R.string.state_ended)
            call.active && call.connectedAt > 0 -> formatTimer((now - call.connectedAt) / 1000)
            else -> stringResource(R.string.state_calling)
        }
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun IncomingControls(
    call: CallInfo,
    actions: InCallActions,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        RoundAction(
            DesignR.drawable.ic_call_end,
            stringResource(R.string.decline),
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.onError,
        ) {
            actions.decline(call.id)
        }
        RoundAction(
            DesignR.drawable.ic_call,
            stringResource(R.string.answer),
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.onPrimary,
        ) {
            actions.answer(call.id)
        }
    }
}

@Composable
private fun RoundAction(
    icon: Int,
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content),
            modifier = Modifier.size(72.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null)
        }
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ActiveControls(
    call: CallInfo,
    audio: AudioInfo,
    actions: InCallActions,
    onKeypad: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Toggle(DesignR.drawable.ic_mic_off, stringResource(R.string.mute), audio.muted) { actions.mute(!audio.muted) }
        Toggle(DesignR.drawable.ic_dialpad, stringResource(R.string.keypad), false) { onKeypad() }
        Toggle(DesignR.drawable.ic_volume_up, stringResource(R.string.speaker), audio.speaker) { actions.speaker(!audio.speaker) }
        Toggle(DesignR.drawable.ic_pause, stringResource(R.string.hold), call.holding) { actions.hold(call.id, !call.holding) }
    }
}

@Composable
private fun Toggle(
    icon: Int,
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconToggleButton(checked = checked, onCheckedChange = { onClick() }, modifier = Modifier.size(64.dp)) {
            Icon(painterResource(icon), contentDescription = label)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun InCallKeypad(
    onDigit: (Char) -> Unit,
    onClose: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        KeypadGrid(onKey = { onDigit(it.first()) }, onLongKey = { onDigit(it.first()) })
        TextButton(onClick = onClose) { Text(stringResource(R.string.hide_keypad)) }
    }
}

/** The 3×4 dialpad, with letters under the digits. */
@Composable
fun KeypadGrid(
    onKey: (String) -> Unit,
    onLongKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Dialpad.keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key -> DialKey(key, onKey, onLongKey) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DialKey(
    key: String,
    onKey: (String) -> Unit,
    onLongKey: (String) -> Unit,
) {
    androidx.compose.material3.Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier =
            Modifier
                .size(width = 88.dp, height = 64.dp)
                .combinedClickable(onClick = { onKey(key) }, onLongClick = { onLongKey(Dialpad.longPress(key)) }),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(key, style = MaterialTheme.typography.headlineSmall)
            val letters = Dialpad.letters(key)
            if (letters.isNotEmpty()) {
                Text(
                    letters,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
