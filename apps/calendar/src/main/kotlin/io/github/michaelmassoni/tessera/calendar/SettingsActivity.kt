package io.github.michaelmassoni.tessera.calendar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.michaelmassoni.tessera.calendar.sync.CalendarAccounts
import io.github.michaelmassoni.tessera.calendar.sync.CalendarDavStore
import io.github.michaelmassoni.tessera.calendar.sync.CalendarSyncWorker
import io.github.michaelmassoni.tessera.core.dav.CollectionsDialog
import io.github.michaelmassoni.tessera.core.dav.RemoteCollection
import io.github.michaelmassoni.tessera.core.dav.SignInDialog
import io.github.michaelmassoni.tessera.core.dav.SyncPrefs
import io.github.michaelmassoni.tessera.core.dav.SyncSettings
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.RadioSetting
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import kotlinx.coroutines.launch
import java.net.URI

class SettingsActivity : TesseraSettingsActivity() {
    override val launcherIcon =
        LauncherIcon.of(
            glyph = R.drawable.ic_launcher_foreground,
            defaultBackground = R.color.ic_launcher_background,
            lightBackground = R.color.ic_launcher_light_background,
            lightForeground = R.color.ic_launcher_light_foreground,
            darkBackground = R.color.ic_launcher_dark_background,
            darkForeground = R.color.ic_launcher_dark_foreground,
        )

    @Composable
    override fun ColumnScope.AppSettings() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val store = remember { CalendarDavStore(context) }
        var dav by remember { mutableStateOf(store.account) }
        var prefs by remember { mutableStateOf(SyncPrefs.load(context)) }
        var pastDays by remember { mutableIntStateOf(CalendarAccounts.pastDays(context)) }
        var signingIn by rememberSaveable { mutableStateOf(false) }
        var picking by remember { mutableStateOf<List<RemoteCollection>?>(null) }
        var choosingPast by rememberSaveable { mutableStateOf(false) }
        var signingOut by remember { mutableStateOf(false) }
        val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(CalendarSyncWorker.schedule.soonName) }
            .collectAsStateWithLifecycle(emptyList())
        val syncing = work.any { it.state == WorkInfo.State.RUNNING }
        val alsoDavx5 = stringResource(R.string.sync_also_davx5)
        val alsoOther = stringResource(R.string.sync_also_other)

        SettingsHeader(stringResource(R.string.settings_sync))
        val a = dav
        val access = context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!access) {
            // Synced calendars go into the system calendar database, which needs calendar access.
            InfoSetting(
                stringResource(R.string.sync_with_server),
                stringResource(R.string.settings_sync_needs_access),
                onClick = { startActivity(Intent(context, MainActivity::class.java)) },
                external = false,
            )
        } else if (a == null) {
            InfoSetting(
                stringResource(R.string.sync_with_server),
                stringResource(R.string.settings_sync_off),
                onClick = { signingIn = true },
                external = false,
            )
        } else {
            InfoSetting(stringResource(R.string.settings_server), stringResource(R.string.settings_server_summary, a.username, a.address))
            InfoSetting(stringResource(R.string.settings_calendars), stringResource(R.string.settings_calendars_summary), onClick = {
                scope.launch {
                    picking =
                        runCatching {
                            CalendarAccounts.remoteCalendars(
                                context,
                            )
                        }.getOrDefault(emptyList()).withNotes(context, alsoDavx5, alsoOther)
                }
            }, external = false)
            InfoSetting(stringResource(R.string.settings_past), pastLabel(pastDays), onClick = { choosingPast = true }, external = false)
            SyncSettings(
                prefs = prefs,
                onChange = {
                    prefs = it
                    SyncPrefs.save(context, it)
                    CalendarSyncWorker.schedule.apply(context)
                    CalendarAccounts.applyAutoSync(context)
                },
                lastSync = remember(work) { store.lastSync },
                lastProblem = remember(work) { store.lastProblem },
                syncing = syncing,
                onSyncNow = { CalendarSyncWorker.schedule.soon(context, delaySeconds = 0) },
            )
            InfoSetting(
                stringResource(R.string.sign_out),
                stringResource(R.string.sign_out_summary),
                onClick = { signingOut = true },
                external = false,
            )
        }

        if (signingIn) {
            SignInDialog(
                notFound = stringResource(R.string.problem_not_caldav),
                signIn = { CalendarAccounts.signIn(context, it.address, it.username, it.password) },
                onDismiss = { signingIn = false },
            ) { calendars ->
                signingIn = false
                dav = store.account
                picking = calendars.withNotes(context, alsoDavx5, alsoOther)
            }
        }
        picking?.let { calendars ->
            val synced = remember(calendars) { CalendarAccounts.syncedHrefs(context) }
            CollectionsDialog(
                title = stringResource(R.string.setup_pick_title),
                collections = calendars,
                // At first, every calendar not already synced by another app.
                initiallyChosen = calendars.filter { if (synced.isEmpty()) it.note == null else it.href in synced }.map { it.href }.toSet(),
                empty = stringResource(R.string.no_server_calendars),
                confirm = stringResource(R.string.use_calendars),
                onConfirm = { chosen ->
                    CalendarAccounts.useCalendars(context, calendars.filter { it.href in chosen })
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }
        if (choosingPast) {
            AlertDialog(
                onDismissRequest = { choosingPast = false },
                title = { Text(stringResource(R.string.settings_past)) },
                text = {
                    Column {
                        listOf(30, 90, 180, 365, 0).forEach { d ->
                            RadioSetting(pastLabel(d), selected = pastDays == d, onSelect = {
                                pastDays = d
                                CalendarAccounts.setPastDays(context, d)
                                choosingPast = false
                                CalendarSyncWorker.schedule.soon(context, delaySeconds = 0)
                            })
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { choosingPast = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        if (signingOut) {
            AlertDialog(
                onDismissRequest = { signingOut = false },
                title = { Text(stringResource(R.string.sign_out)) },
                text = { Text(stringResource(R.string.sign_out_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        CalendarAccounts.signOut(context)
                        dav = null
                        signingOut = false
                    }) { Text(stringResource(R.string.sign_out)) }
                },
                dismissButton = { TextButton(onClick = { signingOut = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }

    @Composable
    private fun pastLabel(days: Int): String =
        if (days == 0) stringResource(R.string.past_keep_all) else pluralStringResource(R.plurals.past_days, days, days)
}

/** Marks calendars another app (DAVx5) already syncs from the same server. */
internal fun List<RemoteCollection>.withNotes(
    context: Context,
    davx5: String,
    other: String,
): List<RemoteCollection> {
    val elsewhere = CalendarAccounts.syncedElsewhere(context)
    return map { c ->
        val type = runCatching { elsewhere[URI(c.href).path.trimEnd('/')] }.getOrNull()
        if (type == null) c else c.copy(note = if (type == CalendarAccounts.DAVX5) davx5 else other)
    }
}
