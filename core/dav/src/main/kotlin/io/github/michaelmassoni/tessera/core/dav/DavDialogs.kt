package io.github.michaelmassoni.tessera.core.dav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Keeps what was typed across rotation (the password too: it never leaves the process). */
val SignInFieldsSaver =
    listSaver<SignInFields, String>(
        save = { listOf(it.address, it.username, it.password) },
        restore = { SignInFields(it[0], it[1], it[2]) },
    )

/**
 * "Sign in to your server": the form, Connect with a spinner while [signIn] runs, and its
 * problem shown in place. [notFound] is what the server lacks ("…doesn't offer CalDAV…").
 */
@Composable
fun <T> SignInDialog(
    notFound: String,
    signIn: suspend (SignInFields) -> Result<T>,
    onDismiss: () -> Unit,
    onSignedIn: (T) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var fields by rememberSaveable(stateSaver = SignInFieldsSaver) { mutableStateOf(SignInFields()) }
    var problem by remember { mutableStateOf<SignInProblem?>(null) }
    var working by remember { mutableStateOf(false) }

    fun connect() {
        if (working) return
        working = true
        problem = null
        scope.launch {
            signIn(fields)
                .onSuccess(onSignedIn)
                .onFailure { problem = (it as? SignInError)?.problem ?: SignInProblem.Other }
            working = false
        }
    }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.sign_in_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SignInForm(fields, { fields = it }, problem, enabled = !working, onDone = ::connect, notFound = notFound)
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

/** Which of the server's collections to sync: ticked ones are kept, [onConfirm] gets them. */
@Composable
fun CollectionsDialog(
    title: String,
    collections: List<RemoteCollection>,
    initiallyChosen: Set<String>,
    empty: String,
    confirm: String,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember(collections) { mutableStateOf(initiallyChosen) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { CollectionPicker(collections, chosen, { chosen = it }, empty) } },
        confirmButton = { TextButton(onClick = { onConfirm(chosen) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
