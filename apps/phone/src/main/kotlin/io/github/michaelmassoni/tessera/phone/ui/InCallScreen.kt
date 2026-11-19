package io.github.michaelmassoni.tessera.phone.ui

import android.telecom.Call
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.phone.R
import io.github.michaelmassoni.tessera.phone.call.CallInfo
import io.github.michaelmassoni.tessera.phone.engine.AudioInfo
import io.github.michaelmassoni.tessera.phone.engine.AudioRoute
import io.github.michaelmassoni.tessera.phone.engine.Dialpad
import io.github.michaelmassoni.tessera.phone.engine.Route
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

    /** Switches call audio to the route with this id. */
    fun route(id: String)

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
    // A Surface, not just a background: it also sets the content colour, so text that doesn't pick
    // its own colour is onSurface (light on dark in dark theme) rather than the default black.
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(
            Modifier.fillMaxSize().safeDrawingPadding(),
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
        AudioButton(audio, actions)
        Toggle(DesignR.drawable.ic_pause, stringResource(R.string.hold), call.holding) { actions.hold(call.id, !call.holding) }
    }
}

/**
 * Speaker on and off, or, with a headset or Bluetooth connected, a button showing where audio
 * goes that opens the list of routes.
 */
@Composable
private fun AudioButton(
    audio: AudioInfo,
    actions: InCallActions,
) {
    if (!audio.needsPicker) {
        Toggle(DesignR.drawable.ic_volume_up, stringResource(R.string.speaker), audio.speaker) {
            audio.toggleTarget()?.let { actions.route(it.id) }
        }
        return
    }
    var open by remember { mutableStateOf(false) }
    Box {
        Toggle(
            routeIcon(audio.current),
            routeLabel(audio.available.firstOrNull { it.id == audio.currentId } ?: AudioRoute("", audio.current)),
            audio.current != Route.Earpiece,
        ) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            audio.available.forEach { r ->
                DropdownMenuItem(
                    text = { Text(routeLabel(r)) },
                    leadingIcon = { Icon(painterResource(routeIcon(r.route)), contentDescription = null) },
                    trailingIcon = {
                        if (r.id == audio.currentId) {
                            Icon(painterResource(DesignR.drawable.ic_check), contentDescription = stringResource(R.string.route_selected))
                        }
                    },
                    onClick = {
                        open = false
                        actions.route(r.id)
                    },
                )
            }
        }
    }
}

private fun routeIcon(route: Route) =
    when (route) {
        Route.Earpiece -> DesignR.drawable.ic_phone_in_talk
        Route.Speaker -> DesignR.drawable.ic_volume_up
        Route.Bluetooth -> DesignR.drawable.ic_bluetooth
        Route.Wired, Route.Streaming -> DesignR.drawable.ic_headphones
    }

@Composable
private fun routeLabel(r: AudioRoute): String =
    r.name ?: stringResource(
        when (r.route) {
            Route.Earpiece -> R.string.route_phone
            Route.Speaker -> R.string.speaker
            Route.Bluetooth -> R.string.route_bluetooth
            Route.Wired -> R.string.route_headset
            Route.Streaming -> R.string.route_other
        },
    )

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

private object NoInCallActions : InCallActions {
    override fun answer(id: String) = Unit

    override fun decline(id: String) = Unit

    override fun hangUp(id: String) = Unit

    override fun hold(
        id: String,
        on: Boolean,
    ) = Unit

    override fun mute(on: Boolean) = Unit

    override fun route(id: String) = Unit

    override fun dtmf(
        id: String,
        digit: Char,
    ) = Unit
}

private fun previewCall(state: Int) = CallInfo("1", "+61 412 345 678", "Ada Lovelace", null, state, 0, incoming = true)

// Both themes are checked (TesseraPreviews includes dark): text here must never fall back to black.
@TesseraPreviews
@Composable
private fun RingingPreview() {
    TesseraTheme(dynamicColor = false) {
        InCallScreen(listOf(previewCall(Call.STATE_RINGING)), AudioInfo(), NoInCallActions)
    }
}

/** On hold rather than active, so the screenshot has no running timer. */
@TesseraPreviews
@Composable
private fun ActiveCallPreview() {
    TesseraTheme(dynamicColor = false) {
        InCallScreen(
            listOf(previewCall(Call.STATE_HOLDING)),
            AudioInfo(
                muted = true,
                current = Route.Bluetooth,
                currentId = "bt",
                available =
                    listOf(
                        AudioRoute("ear", Route.Earpiece),
                        AudioRoute("spk", Route.Speaker),
                        AudioRoute("bt", Route.Bluetooth, "Pixel Buds"),
                    ),
            ),
            NoInCallActions,
        )
    }
}
