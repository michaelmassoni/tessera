package io.github.michaelmassoni.tessera.core.dav

import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.settings.SetupIcon
import io.github.michaelmassoni.tessera.core.settings.SetupPage
import io.github.michaelmassoni.tessera.core.settings.rememberLocalNetworkAccess
import kotlinx.coroutines.launch
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** An app's own words on the "Sync with a server?" setup pages. */
data class DavSetupTexts(
    /** "Tasks can sync with your own CalDAV server, so…" */
    val intro: String,
    /** "Keep tasks on this phone". */
    val keepLocal: String,
    /** "OpenCloud, Nextcloud, Radicale or any CalDAV server". */
    val servers: String,
    /** "Which lists?" */
    val pickTitle: String,
    /** "These task lists on your server will sync with this phone." */
    val pickText: String,
    /** "No task lists on the server yet…" */
    val empty: String,
    /** "Sync these lists". */
    val use: String,
    /** "That server doesn't offer CalDAV task lists at that address…" */
    val notFound: String,
)

/**
 * First run: "Sync with a server?" Keep everything on the phone ([next]), or sign in ([signIn]
 * saves the account and returns the server's collections), tick which to sync ([onUse]), then
 * [next].
 */
@Composable
fun DavSetupPage(
    @DrawableRes icon: Int,
    texts: DavSetupTexts,
    signIn: suspend (SignInFields) -> Result<List<RemoteCollection>>,
    onUse: (List<RemoteCollection>) -> Unit,
    next: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var signingIn by rememberSaveable { mutableStateOf(false) }
    var remote by remember { mutableStateOf<List<RemoteCollection>?>(null) }
    val found = remote
    when {
        found != null -> {
            // Those another app already syncs (noted) start unticked, to avoid duplicates.
            var chosen by remember { mutableStateOf(found.filter { it.note == null }.map { it.href }.toSet()) }
            SetupPage(
                image = { SetupIcon(icon) },
                modifier = modifier,
                title = texts.pickTitle,
                text = texts.pickText,
                body = { CollectionPicker(found, chosen, { chosen = it }, texts.empty) },
            ) {
                Button(onClick = {
                    onUse(found.filter { it.href in chosen })
                    next()
                }, modifier = Modifier.fillMaxWidth()) { Text(texts.use) }
            }
        }

        signingIn -> {
            SignInPage(texts, signIn, onSignedIn = { remote = it }, onBack = { signingIn = false }, modifier = modifier)
        }

        else -> {
            SetupPage(
                image = { SetupIcon(DesignR.drawable.ic_sync) },
                modifier = modifier,
                title = stringResource(R.string.setup_sync_title),
                text = texts.intro,
                body = {
                    Choice(DesignR.drawable.ic_smartphone, texts.keepLocal, stringResource(R.string.setup_local_text), onClick = next)
                    Choice(DesignR.drawable.ic_dns, stringResource(R.string.setup_server_choice), texts.servers) { signingIn = true }
                },
            ) {}
        }
    }
}

@Composable
private fun Choice(
    @DrawableRes icon: Int,
    title: String,
    text: String,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SignInPage(
    texts: DavSetupTexts,
    signIn: suspend (SignInFields) -> Result<List<RemoteCollection>>,
    onSignedIn: (List<RemoteCollection>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var fields by rememberSaveable(stateSaver = SignInFieldsSaver) { mutableStateOf(SignInFields()) }
    var problem by remember { mutableStateOf<SignInProblem?>(null) }
    var working by remember { mutableStateOf(false) }
    val network = rememberLocalNetworkAccess()

    fun connect() {
        if (working || fields.address.isBlank() || fields.username.isBlank()) return
        working = true
        problem = null
        scope.launch {
            if (!network.allow(fields.address)) {
                problem = SignInProblem.LocalNetwork
                working = false
                return@launch
            }
            signIn(fields)
                .onSuccess(onSignedIn)
                .onFailure { problem = (it as? SignInError)?.problem ?: SignInProblem.Other }
            working = false
        }
    }
    SetupPage(
        image = { SetupIcon(DesignR.drawable.ic_dns) },
        modifier = modifier,
        title = stringResource(R.string.sign_in_title),
        text = stringResource(R.string.setup_password_text),
        body = { SignInForm(fields, { fields = it }, problem, enabled = !working, onDone = ::connect, notFound = texts.notFound) },
    ) {
        Button(
            onClick = ::connect,
            enabled = !working && fields.address.isNotBlank() && fields.username.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.connect))
        }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(texts.keepLocal) }
    }
}
