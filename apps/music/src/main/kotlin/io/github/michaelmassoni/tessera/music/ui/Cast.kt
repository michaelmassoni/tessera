package io.github.michaelmassoni.tessera.music.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.cast.Cast
import io.github.michaelmassoni.tessera.music.cast.CastDevice
import io.github.michaelmassoni.tessera.music.cast.CastState
import kotlinx.coroutines.delay
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** The device being cast to, if any, for "Casting to ...". */
@Composable
fun castingTo(): String? {
    val state by Cast.state.collectAsStateWithLifecycle()
    return (state as? CastState.Connected)?.device?.name
}

/**
 * The Cast button and its sheet (docs/music.md, Casting). On Android 17 the first tap asks for
 * local network access; devices are looked for only while the sheet is open.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by Cast.state.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    // Checked again after the prompt and on coming back (from Settings, say).
    var needsPermission by remember { mutableStateOf(Cast.needsPermission(context)) }
    LifecycleResumeEffect(Unit) {
        needsPermission = Cast.needsPermission(context)
        onPauseOrDispose {}
    }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            needsPermission = Cast.needsPermission(context)
            denied = !granted
            open = true
        }
    CastIconButton(state, modifier) {
        if (needsPermission && !denied) ask.launch(Cast.PERMISSION) else open = true
    }
    if (open) {
        val discovery = remember { Cast.discovery(context) }
        val devices by discovery.devices.collectAsStateWithLifecycle()
        DisposableEffect(needsPermission) {
            if (!needsPermission) discovery.start()
            onDispose { discovery.stop() }
        }
        CastSheet(
            state = state,
            devices = devices,
            needsPermission = needsPermission,
            actions =
                object : CastActions {
                    override fun pick(device: CastDevice) = Cast.connect(context, device)

                    override fun stop() {
                        Cast.disconnect()
                        open = false
                    }

                    override fun volume(level: Float) = Cast.setVolume(level)

                    override fun allow() {
                        denied = false
                        ask.launch(Cast.PERMISSION)
                    }

                    override fun openSettings() {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            onDismiss = {
                open = false
                Cast.clearFailure()
            },
        )
    }
}

@Composable
internal fun CastIconButton(
    state: CastState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val connected = state is CastState.Connected
    val label =
        when (state) {
            is CastState.Connected -> stringResource(R.string.cast_casting_to, state.device.name)
            is CastState.Connecting -> stringResource(R.string.cast_connecting_to, state.device.name)
            else -> stringResource(R.string.cast)
        }
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painterResource(if (connected) R.drawable.ic_cast_connected else R.drawable.ic_cast),
            contentDescription = label,
            tint = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

interface CastActions {
    fun pick(device: CastDevice)

    fun stop()

    fun volume(level: Float)

    fun allow()

    fun openSettings()
}

/** "Cast to": the devices on the Wi-Fi, or the one being cast to with its volume. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastSheet(
    state: CastState,
    devices: List<CastDevice>,
    needsPermission: Boolean,
    actions: CastActions,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        CastSheetContent(state, devices, needsPermission, actions)
    }
}

/** The sheet's contents, separate so previews can show them without a sheet. */
@Composable
fun CastSheetContent(
    state: CastState,
    devices: List<CastDevice>,
    needsPermission: Boolean,
    actions: CastActions,
    modifier: Modifier = Modifier,
    searchMs: Long = SEARCH_MS,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            stringResource(if (state is CastState.Connected) R.string.cast_casting else R.string.cast_to),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        when {
            state is CastState.Connected -> Connected(state, actions)
            needsPermission -> Permission(actions)
            else -> Devices(state, devices, actions, searchMs)
        }
    }
}

@Composable
private fun Connected(
    state: CastState.Connected,
    actions: CastActions,
) {
    Column {
        ListItem(
            leadingContent = { DeviceIcon(state.device) },
            supportingContent = { Text(stringResource(R.string.cast_casting)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) { Text(state.device.name) }
        // Follows the device, but not while the finger is on it.
        var dragging by remember { mutableStateOf(false) }
        val slider = remember { SliderState(state.volume) }
        if (!dragging) slider.value = state.volume
        val volumeLabel = stringResource(R.string.cast_volume)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(DesignR.drawable.ic_volume_up), contentDescription = null)
            Slider(
                state = slider,
                onValueChange = {
                    dragging = true
                    slider.value = it
                    actions.volume(it)
                },
                onValueChangeFinished = { dragging = false },
                modifier = Modifier.weight(1f).semantics { contentDescription = volumeLabel },
            )
        }
        OutlinedButton(
            onClick = actions::stop,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth(),
        ) { Text(stringResource(R.string.cast_stop)) }
    }
}

@Composable
private fun Permission(actions: CastActions) {
    Column {
        Text(
            stringResource(R.string.cast_permission),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = actions::allow) { Text(stringResource(R.string.cast_allow)) }
            OutlinedButton(onClick = actions::openSettings) { Text(stringResource(R.string.cast_open_settings)) }
        }
    }
}

@Composable
private fun Devices(
    state: CastState,
    devices: List<CastDevice>,
    actions: CastActions,
    searchMs: Long,
) {
    Column {
        // Still looking for a while; then, with nothing found, the likely reason.
        var looking by remember { mutableStateOf(searchMs > 0) }
        LaunchedEffect(searchMs) {
            if (searchMs > 0) delay(searchMs)
            looking = false
        }
        if (looking) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
        devices.forEach { device ->
            val connecting = (state as? CastState.Connecting)?.device?.id == device.id
            val failed = (state as? CastState.Failed)?.device?.id == device.id
            ListItem(
                onClick = { actions.pick(device) },
                leadingContent = { DeviceIcon(device) },
                supportingContent =
                    when {
                        connecting -> {
                            { Text(stringResource(R.string.cast_connecting)) }
                        }

                        failed -> {
                            { Text(stringResource(R.string.cast_failed), color = MaterialTheme.colorScheme.error) }
                        }

                        device.model.isNotBlank() -> {
                            { Text(device.model) }
                        }

                        else -> {
                            null
                        }
                    },
                trailingContent = if (connecting) ({ CircularProgressIndicator(Modifier.size(24.dp)) }) else null,
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) { Text(device.name) }
        }
        if (devices.isEmpty()) {
            Text(
                stringResource(if (looking) R.string.cast_searching else R.string.cast_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun DeviceIcon(device: CastDevice) {
    Icon(
        painterResource(if (device.video) R.drawable.ic_tv else R.drawable.ic_speaker),
        contentDescription = null,
    )
}

private const val SEARCH_MS = 10_000L
