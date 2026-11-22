package io.github.michaelmassoni.tessera.tasks

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.michaelmassoni.tessera.core.dav.CollectionsDialog
import io.github.michaelmassoni.tessera.core.dav.RemoteCollection
import io.github.michaelmassoni.tessera.core.dav.SignInDialog
import io.github.michaelmassoni.tessera.core.dav.SyncPrefs
import io.github.michaelmassoni.tessera.core.dav.SyncSettings
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.tasks.data.AccountStore
import io.github.michaelmassoni.tessera.tasks.data.Accounts
import io.github.michaelmassoni.tessera.tasks.sync.SyncWorker
import kotlinx.coroutines.launch

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
        val store = remember { AccountStore(context) }
        var account by remember { mutableStateOf(store.account) }
        var prefs by remember { mutableStateOf(SyncPrefs.load(context)) }
        var signingIn by rememberSaveable { mutableStateOf(false) }
        var picking by remember { mutableStateOf<List<RemoteCollection>?>(null) }
        var signingOut by remember { mutableStateOf(false) }
        val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SyncWorker.schedule.soonName) }
            .collectAsStateWithLifecycle(emptyList())
        val syncing = work.any { it.state == WorkInfo.State.RUNNING }

        SettingsHeader(stringResource(R.string.settings_sync))
        val a = account
        if (a == null) {
            InfoSetting(stringResource(R.string.sync_server), stringResource(R.string.settings_sync_off), onClick = {
                signingIn = true
            }, external = false)
        } else {
            InfoSetting(stringResource(R.string.settings_server), stringResource(R.string.settings_server_summary, a.username, a.address))
            InfoSetting(stringResource(R.string.settings_lists), stringResource(R.string.settings_lists_summary), onClick = {
                scope.launch { picking = runCatching { Accounts.remoteLists(context) }.getOrDefault(emptyList()) }
            }, external = false)
            SyncSettings(
                prefs = prefs,
                onChange = {
                    prefs = it
                    SyncPrefs.save(context, it)
                    SyncWorker.schedule.apply(context)
                },
                // Read again whenever a sync finishes (work changes).
                lastSync = remember(work) { store.lastSync },
                lastProblem = remember(work) { store.lastProblem },
                syncing = syncing,
                onSyncNow = { SyncWorker.schedule.soon(context, delaySeconds = 0) },
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
                signIn = { Accounts.signIn(context, it.address, it.username, it.password) },
                onDismiss = { signingIn = false },
            ) { lists ->
                signingIn = false
                account = store.account
                picking = lists
            }
        }
        picking?.let { lists ->
            val data by context.tasks.data.collectAsState()
            val synced = data.lists.mapNotNull { it.href }.toSet()
            CollectionsDialog(
                title = stringResource(R.string.setup_pick_title),
                collections = lists,
                initiallyChosen = lists.map { it.href }.filter { it in synced || synced.isEmpty() }.toSet(),
                empty = stringResource(R.string.no_server_lists),
                confirm = stringResource(R.string.use_lists),
                onConfirm = { chosen ->
                    Accounts.useLists(context, lists.filter { it.href in chosen })
                    // Lists unticked stop syncing and leave this phone.
                    val dropped = lists.filter { it.href !in chosen }.map { it.href }.toSet()
                    context.tasks.update { d ->
                        val gone =
                            d.lists
                                .filter { it.href in dropped }
                                .map { it.id }
                                .toSet()
                        d.copy(lists = d.lists.filterNot { it.id in gone }, tasks = d.tasks.filterNot { it.listId in gone })
                    }
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }
        if (signingOut) {
            AlertDialog(
                onDismissRequest = { signingOut = false },
                title = { Text(stringResource(R.string.sign_out)) },
                text = { Text(stringResource(R.string.sign_out_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        Accounts.signOut(context)
                        account = null
                        signingOut = false
                    }) { Text(stringResource(R.string.sign_out)) }
                },
                dismissButton = { TextButton(onClick = { signingOut = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
