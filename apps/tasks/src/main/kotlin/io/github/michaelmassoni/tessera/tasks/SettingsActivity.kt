package io.github.michaelmassoni.tessera.tasks

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.settings.InfoSetting
import io.github.michaelmassoni.tessera.core.settings.LauncherIcon
import io.github.michaelmassoni.tessera.core.settings.SettingsHeader
import io.github.michaelmassoni.tessera.core.settings.TesseraSettingsActivity
import io.github.michaelmassoni.tessera.tasks.data.AccountStore
import io.github.michaelmassoni.tessera.tasks.data.Accounts
import io.github.michaelmassoni.tessera.tasks.data.RemoteList
import io.github.michaelmassoni.tessera.tasks.data.SignInProblem
import io.github.michaelmassoni.tessera.tasks.sync.SyncWorker
import io.github.michaelmassoni.tessera.tasks.ui.ListPicker
import io.github.michaelmassoni.tessera.tasks.ui.SignInFields
import io.github.michaelmassoni.tessera.tasks.ui.SignInForm
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
        var signingIn by rememberSaveable { mutableStateOf(false) }
        var picking by remember { mutableStateOf<List<RemoteList>?>(null) }
        var signingOut by remember { mutableStateOf(false) }

        SettingsHeader(stringResource(R.string.settings_sync))
        val a = account
        if (a == null) {
            InfoSetting(stringResource(R.string.sync_server), stringResource(R.string.settings_sync_off), onClick = {
                signingIn = true
            }, external = false)
        } else {
            InfoSetting(stringResource(R.string.settings_server), stringResource(R.string.settings_server_summary, a.username, a.address))
            val last = store.lastSync
            InfoSetting(
                stringResource(R.string.settings_sync_now),
                if (last == 0L) "" else stringResource(R.string.settings_last_sync, DateUtils.getRelativeTimeSpanString(last).toString()),
                onClick = { SyncWorker.soon(context, delaySeconds = 0) },
                external = false,
            )
            InfoSetting(stringResource(R.string.settings_lists), stringResource(R.string.settings_lists_summary), onClick = {
                scope.launch { picking = runCatching { Accounts.remoteLists(context) }.getOrDefault(emptyList()) }
            }, external = false)
            InfoSetting(
                stringResource(R.string.sign_out),
                stringResource(R.string.sign_out_summary),
                onClick = { signingOut = true },
                external = false,
            )
        }

        if (signingIn) {
            SignInDialog(onDismiss = { signingIn = false }) { lists ->
                signingIn = false
                account = store.account
                picking = lists
            }
        }
        picking?.let { lists ->
            val data by context.tasks.data.collectAsState()
            val synced = data.lists.mapNotNull { it.href }.toSet()
            var chosen by remember(lists) { mutableStateOf(lists.map { it.href }.filter { it in synced || synced.isEmpty() }.toSet()) }
            AlertDialog(
                onDismissRequest = { picking = null },
                title = { Text(stringResource(R.string.setup_pick_title)) },
                text = { Column(Modifier.verticalScroll(rememberScrollState())) { ListPicker(lists, chosen, { chosen = it }) } },
                confirmButton = {
                    TextButton(onClick = {
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
                    }) { Text(stringResource(R.string.use_lists)) }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) } },
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

private val FieldsSaver =
    listSaver<SignInFields, String>(
        save = { listOf(it.address, it.username, it.password) },
        restore = { SignInFields(it[0], it[1], it[2]) },
    )

@Composable
private fun SignInDialog(
    onDismiss: () -> Unit,
    onSignedIn: (List<RemoteList>) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fields by rememberSaveable(stateSaver = FieldsSaver) { mutableStateOf(SignInFields()) }
    var problem by remember { mutableStateOf<SignInProblem?>(null) }
    var working by remember { mutableStateOf(false) }

    fun connect() {
        if (working) return
        working = true
        problem = null
        scope.launch {
            Accounts
                .signIn(context, fields.address, fields.username, fields.password)
                .onSuccess(onSignedIn)
                .onFailure { problem = (it as? Accounts.SignInError)?.problem ?: SignInProblem.Other }
            working = false
        }
    }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.setup_server_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SignInForm(fields, { fields = it }, problem, enabled = !working, onDone = ::connect)
            }
        },
        confirmButton = {
            TextButton(onClick = ::connect, enabled = !working && fields.address.isNotBlank() && fields.username.isNotBlank()) {
                if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.connect))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text(stringResource(R.string.cancel)) } },
    )
}
