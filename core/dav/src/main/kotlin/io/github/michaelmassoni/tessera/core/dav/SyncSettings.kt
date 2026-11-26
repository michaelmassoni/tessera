package io.github.michaelmassoni.tessera.core.dav

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
import io.github.michaelmassoni.tessera.core.settings.LocalNetwork
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SwitchSetting
import io.github.michaelmassoni.tessera.core.settings.rememberIsLocal
import io.github.michaelmassoni.tessera.core.settings.rememberLocalNetworkAccess
import kotlinx.coroutines.launch
import io.github.michaelmassoni.tessera.core.settings.R as SettingsR

@Composable
fun SyncInterval.label(): String =
    stringResource(
        when (this) {
            SyncInterval.Manual -> R.string.sync_manual
            SyncInterval.Min15 -> R.string.sync_15_min
            SyncInterval.Min30 -> R.string.sync_30_min
            SyncInterval.Hour1 -> R.string.sync_1_hour
            SyncInterval.Hour2 -> R.string.sync_2_hours
            SyncInterval.Hour4 -> R.string.sync_4_hours
            SyncInterval.Day -> R.string.sync_day
        },
    )

/**
 * The shared part of an app's Sync settings: how often, on local changes, Wi-Fi only, Sync now
 * with when it last ran. Shown while signed in (docs/sync.md).
 */
@Composable
fun ColumnScope.SyncSettings(
    prefs: SyncPrefs,
    onChange: (SyncPrefs) -> Unit,
    lastSync: Long,
    lastProblem: String?,
    syncing: Boolean,
    onSyncNow: () -> Unit,
    /** The account's server: on the local network, Sync now first asks for Nearby devices. */
    server: String? = null,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val network = rememberLocalNetworkAccess()
    var refused by rememberSaveable { mutableStateOf(false) }
    val blocked = rememberIsLocal(server) && !network.granted
    InfoSetting(
        title = stringResource(R.string.sync_every),
        summary = prefs.interval.label(),
        onClick = { choosing = true },
        external = false,
    )
    SwitchSetting(
        title = stringResource(R.string.sync_on_change),
        summary = stringResource(R.string.sync_on_change_summary),
        checked = prefs.onLocalChange,
        onCheckedChange = { onChange(prefs.copy(onLocalChange = it)) },
    )
    SwitchSetting(
        title = stringResource(R.string.sync_wifi_only),
        summary = stringResource(R.string.sync_wifi_only_summary),
        checked = prefs.wifiOnly,
        onCheckedChange = { onChange(prefs.copy(wifiOnly = it)) },
    )
    InfoSetting(
        title = stringResource(R.string.sync_now),
        summary =
            when {
                syncing -> {
                    stringResource(R.string.sync_syncing)
                }

                blocked -> {
                    stringResource(SettingsR.string.local_network_sync)
                }

                lastProblem == SyncProblem.Unreachable.name -> {
                    stringResource(R.string.sync_failed_unreachable)
                }

                lastProblem == SyncProblem.WrongLogin.name -> {
                    stringResource(R.string.sync_failed_login)
                }

                lastProblem != null -> {
                    stringResource(R.string.sync_failed_other)
                }

                lastSync == 0L -> {
                    stringResource(R.string.sync_never)
                }

                System.currentTimeMillis() - lastSync < DateUtils.MINUTE_IN_MILLIS -> {
                    stringResource(R.string.sync_just_now)
                }

                else -> {
                    stringResource(
                        R.string.sync_last,
                        DateUtils.getRelativeTimeSpanString(lastSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    )
                }
            },
        onClick = {
            when {
                !blocked -> {
                    onSyncNow()
                }

                // Refused before: Android may not ask again, so go where it can be allowed.
                refused -> {
                    context.startActivity(LocalNetwork.appSettings(context))
                }

                else -> {
                    scope.launch { if (network.ask()) onSyncNow() else refused = true }
                }
            }
        },
        external = false,
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.sync_every)) },
            text = {
                Column {
                    SyncInterval.entries.forEach { i ->
                        RadioSetting(
                            title = i.label(),
                            selected = prefs.interval == i,
                            onSelect = {
                                choosing = false
                                onChange(prefs.copy(interval = i))
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
