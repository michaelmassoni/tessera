package io.github.michaelmassoni.tessera.contacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.michaelmassoni.tessera.contacts.data.ContactsRepository
import io.github.michaelmassoni.tessera.contacts.engine.AccountRef
import io.github.michaelmassoni.tessera.contacts.sync.ContactsAccounts
import io.github.michaelmassoni.tessera.contacts.sync.ContactsDavStore
import io.github.michaelmassoni.tessera.contacts.sync.ContactsSyncWorker
import io.github.michaelmassoni.tessera.contacts.sync.GroupMethod
import io.github.michaelmassoni.tessera.contacts.ui.accountName
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        SyncSection()
        val accounts by produceState(emptyList<AccountRef>()) {
            value =
                withContext(Dispatchers.IO) {
                    // Without contacts access (opened from system settings first) there is nothing to choose.
                    runCatching { ContactsRepository(contentResolver).accounts() }.getOrDefault(emptyList())
                }
        }
        if (accounts.size < 2) return
        val resources = LocalResources.current
        var selected by remember { mutableStateOf(defaultAccount ?: AccountRef.Device) }
        SettingsHeader(stringResource(R.string.settings_default_account))
        Column(Modifier.selectableGroup()) {
            accounts.forEach { account ->
                RadioSetting(
                    title = accountName(account, resources),
                    selected = account == selected,
                    onSelect = {
                        selected = account
                        defaultAccount = account
                    },
                )
            }
        }
    }

    /** Sync with a CardDAV server (docs/sync.md). */
    @Composable
    private fun ColumnScope.SyncSection() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val store = remember { ContactsDavStore(context) }
        var dav by remember { mutableStateOf(store.account) }
        var prefs by remember { mutableStateOf(SyncPrefs.load(context)) }
        var method by remember { mutableStateOf(ContactsAccounts.pendingGroupMethod(context) ?: ContactsAccounts.groupMethod(context)) }
        var signingIn by rememberSaveable { mutableStateOf(false) }
        var picking by remember { mutableStateOf<List<RemoteCollection>?>(null) }
        var signingOut by remember { mutableStateOf(false) }
        var confirmMethod by remember { mutableStateOf<GroupMethod?>(null) }
        val work by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ContactsSyncWorker.schedule.soonName) }
            .collectAsStateWithLifecycle(emptyList())
        val syncing = work.any { it.state == WorkInfo.State.RUNNING }

        SettingsHeader(stringResource(R.string.settings_sync))
        val access = context.checkSelfPermission(Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val a = dav
        if (!access) {
            InfoSetting(
                stringResource(R.string.sync_with_server),
                stringResource(R.string.settings_sync_needs_access),
                onClick = { startActivity(Intent(context, MainActivity::class.java)) },
                external = false,
            )
        } else if (a == null) {
            InfoSetting(stringResource(R.string.sync_with_server), stringResource(R.string.settings_sync_off), onClick = {
                signingIn = true
            }, external = false)
        } else {
            InfoSetting(stringResource(R.string.settings_server), stringResource(R.string.settings_server_summary, a.username, a.address))
            InfoSetting(
                stringResource(
                    R.string.settings_address_books,
                ),
                stringResource(R.string.settings_address_books_summary),
                onClick = {
                    scope.launch { picking = runCatching { ContactsAccounts.remoteAddressBooks(context) }.getOrDefault(emptyList()) }
                },
                external = false,
            )
            SyncSettings(
                prefs = prefs,
                onChange = {
                    prefs = it
                    SyncPrefs.save(context, it)
                    ContactsSyncWorker.schedule.apply(context)
                    ContactsAccounts.applyAutoSync(context)
                },
                lastSync = remember(work) { store.lastSync },
                lastProblem = remember(work) { store.lastProblem },
                syncing = syncing,
                onSyncNow = { ContactsSyncWorker.schedule.soon(context, delaySeconds = 0) },
            )
            InfoSetting(
                stringResource(R.string.sign_out),
                stringResource(R.string.sign_out_summary),
                onClick = { signingOut = true },
                external = false,
            )
        }
        if (access) {
            SettingsHeader(stringResource(R.string.settings_group_method))
            Column(Modifier.selectableGroup()) {
                GroupMethod.entries.forEach { m ->
                    RadioSetting(
                        title =
                            stringResource(
                                if (m ==
                                    GroupMethod.SeparateVCards
                                ) {
                                    R.string.group_method_vcards
                                } else {
                                    R.string.group_method_categories
                                },
                            ),
                        selected = method == m,
                        onSelect = {
                            if (m != method) {
                                if (dav == null) {
                                    method = m
                                    ContactsAccounts.setGroupMethod(context, m)
                                } else {
                                    confirmMethod = m
                                }
                            }
                        },
                    )
                }
            }
        }

        if (signingIn) {
            SignInDialog(
                notFound = stringResource(R.string.problem_not_carddav),
                signIn = { ContactsAccounts.signIn(context, it.address, it.username, it.password) },
                onDismiss = { signingIn = false },
            ) { books ->
                signingIn = false
                dav = store.account
                picking = books
            }
        }
        picking?.let { books ->
            val synced = remember(books) { ContactsAccounts.syncedHrefs(context) }
            CollectionsDialog(
                title = stringResource(R.string.setup_pick_title),
                collections = books,
                initiallyChosen = books.map { it.href }.filter { synced.isEmpty() || it in synced }.toSet(),
                empty = stringResource(R.string.no_server_address_books),
                confirm = stringResource(R.string.use_address_books),
                onConfirm = { chosen ->
                    useAddressBooks(context, books.filter { it.href in chosen })
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }
        confirmMethod?.let { m ->
            AlertDialog(
                onDismissRequest = { confirmMethod = null },
                title = { Text(stringResource(R.string.settings_group_method)) },
                text = { Text(stringResource(R.string.group_method_change_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        method = m
                        ContactsAccounts.setGroupMethod(context, m)
                        confirmMethod = null
                    }) { Text(stringResource(R.string.group_method_change)) }
                },
                dismissButton = { TextButton(onClick = { confirmMethod = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
        if (signingOut) {
            AlertDialog(
                onDismissRequest = { signingOut = false },
                title = { Text(stringResource(R.string.sign_out)) },
                text = { Text(stringResource(R.string.sign_out_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        ContactsAccounts.signOut(context)
                        dav = null
                        signingOut = false
                    }) { Text(stringResource(R.string.sign_out)) }
                },
                dismissButton = { TextButton(onClick = { signingOut = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

/** Syncs the chosen address books; new contacts then go to the first one unless another was chosen. */
internal fun useAddressBooks(
    context: Context,
    chosen: List<RemoteCollection>,
) {
    ContactsAccounts.useAddressBooks(context, chosen)
    val first = ContactsAccounts.accounts(context).firstOrNull() ?: return
    if (context.defaultAccount == null || context.defaultAccount == AccountRef.Device) {
        context.defaultAccount = AccountRef(first.name, first.type)
    }
}
