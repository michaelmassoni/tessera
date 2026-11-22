package io.github.michaelmassoni.tessera.tasks

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.michaelmassoni.tessera.core.settings.SetupCustom
import io.github.michaelmassoni.tessera.core.settings.SetupGrant
import io.github.michaelmassoni.tessera.core.settings.SetupIcon
import io.github.michaelmassoni.tessera.core.settings.SetupPage
import io.github.michaelmassoni.tessera.core.settings.SetupSpec
import io.github.michaelmassoni.tessera.core.settings.SetupStep
import io.github.michaelmassoni.tessera.tasks.data.Accounts
import io.github.michaelmassoni.tessera.tasks.data.RemoteList
import io.github.michaelmassoni.tessera.tasks.data.SignInProblem
import io.github.michaelmassoni.tessera.tasks.ui.ListPicker
import io.github.michaelmassoni.tessera.tasks.ui.SignInFields
import io.github.michaelmassoni.tessera.tasks.ui.SignInForm
import kotlinx.coroutines.launch
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** Tasks' first run: notifications and exact alarms for reminders, sync, restore. */
internal val tasksSetup =
    SetupSpec(
        custom = listOf(SetupCustom { next -> SyncPage(next) }),
        steps =
            listOfNotNull(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SetupStep(
                        DesignR.drawable.ic_notifications,
                        R.string.setup_notifications_title,
                        R.string.setup_notifications_text,
                        SetupGrant.Permissions(listOf(Manifest.permission.POST_NOTIFICATIONS)),
                        required = false,
                    )
                } else {
                    null
                },
                SetupStep(
                    DesignR.drawable.ic_alarm,
                    R.string.setup_alarms_title,
                    R.string.setup_alarms_text,
                    SetupGrant.Special(
                        intent = { Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${it.packageName}".toUri()) },
                        granted = { it.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() },
                    ),
                    required = false,
                ),
            ),
        offerRestore = true,
    )

private val FieldsSaver =
    listSaver<SignInFields, String>(
        save = { listOf(it.address, it.username, it.password) },
        restore = { SignInFields(it[0], it[1], it[2]) },
    )

/** "Sync with a server?": keep tasks on the phone, or sign in and choose the server's lists. */
@Composable
private fun SyncPage(next: () -> Unit) {
    var signingIn by rememberSaveable { mutableStateOf(false) }
    var remote by remember { mutableStateOf<List<RemoteList>?>(null) }
    val context = LocalContext.current
    when {
        remote != null -> {
            var chosen by remember { mutableStateOf(remote!!.map { it.href }.toSet()) }
            SetupPage(
                image = { SetupIcon(DesignR.drawable.ic_checklist) },
                title = stringResource(R.string.setup_pick_title),
                text = stringResource(R.string.setup_pick_text),
                body = { ListPicker(remote!!, chosen, { chosen = it }) },
            ) {
                Button(onClick = {
                    Accounts.useLists(context, remote!!.filter { it.href in chosen })
                    next()
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.use_lists)) }
            }
        }

        signingIn -> {
            SignInPage(onSignedIn = { remote = it }, onBack = { signingIn = false })
        }

        else -> {
            SetupPage(
                image = { SetupIcon(DesignR.drawable.ic_sync) },
                title = stringResource(R.string.setup_sync_title),
                text = stringResource(R.string.setup_sync_text),
                body = {
                    Choice(DesignR.drawable.ic_smartphone, R.string.sync_local, R.string.sync_local_text, onClick = next)
                    Choice(DesignR.drawable.ic_dns, R.string.sync_server, R.string.sync_server_text) { signingIn = true }
                },
            ) {}
        }
    }
}

@Composable
private fun Choice(
    icon: Int,
    title: Int,
    text: Int,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SignInPage(
    onSignedIn: (List<RemoteList>) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fields by rememberSaveable(stateSaver = FieldsSaver) { mutableStateOf(SignInFields()) }
    var problem by remember { mutableStateOf<SignInProblem?>(null) }
    var working by remember { mutableStateOf(false) }

    fun connect() {
        if (working || fields.address.isBlank() || fields.username.isBlank()) return
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
    SetupPage(
        image = { SetupIcon(DesignR.drawable.ic_dns) },
        title = stringResource(R.string.setup_server_title),
        text = stringResource(R.string.setup_server_text),
        body = { SignInForm(fields, { fields = it }, problem, enabled = !working, onDone = ::connect) },
    ) {
        Button(
            onClick = ::connect,
            enabled = !working && fields.address.isNotBlank() && fields.username.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.connect))
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.sync_local)) }
    }
}
