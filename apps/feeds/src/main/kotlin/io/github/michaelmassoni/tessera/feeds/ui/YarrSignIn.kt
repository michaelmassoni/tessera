package io.github.michaelmassoni.tessera.feeds.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.dav.SignInError
import io.github.michaelmassoni.tessera.core.dav.SignInProblem
import io.github.michaelmassoni.tessera.core.design.TesseraPasswordField
import io.github.michaelmassoni.tessera.core.settings.LocalNetworkAccess
import io.github.michaelmassoni.tessera.core.settings.LocalNetworkDenied
import io.github.michaelmassoni.tessera.core.settings.rememberLocalNetworkAccess
import io.github.michaelmassoni.tessera.feeds.R
import kotlinx.coroutines.launch
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What the yarr sign-in holds. */
data class YarrFields(
    val address: String = "",
    val username: String = "",
    val password: String = "",
)

/** Address, then username and password (empty when the server has no login). */
@Composable
fun YarrSignInForm(
    fields: YarrFields,
    onChange: (YarrFields) -> Unit,
    problem: SignInProblem?,
    enabled: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            fields.address,
            { onChange(fields.copy(address = it)) },
            label = { Text(stringResource(R.string.server_address)) },
            placeholder = { Text(stringResource(R.string.server_address_hint)) },
            singleLine = true,
            enabled = enabled,
            isError = problem == SignInProblem.BadAddress || problem == SignInProblem.Insecure,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            fields.username,
            { onChange(fields.copy(username = it)) },
            label = { Text(stringResource(R.string.username)) },
            supportingText = { Text(stringResource(R.string.username_hint)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        TesseraPasswordField(
            value = fields.password,
            onValueChange = { onChange(fields.copy(password = it)) },
            label = stringResource(R.string.password),
            enabled = enabled && fields.username.isNotBlank(),
            isError = problem == SignInProblem.WrongLogin,
            onDone = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
        if (problem == SignInProblem.LocalNetwork) {
            LocalNetworkDenied()
        } else if (problem != null) {
            Text(
                stringResource(
                    when (problem) {
                        SignInProblem.BadAddress -> {
                            R.string.problem_bad_address
                        }

                        SignInProblem.Insecure -> {
                            R.string.problem_insecure
                        }

                        SignInProblem.Unreachable -> {
                            R.string.problem_unreachable
                        }

                        SignInProblem.WrongLogin -> {
                            if (fields.username.isBlank()) R.string.problem_needs_login else R.string.problem_wrong_login
                        }

                        SignInProblem.NotCalDav -> {
                            R.string.problem_not_yarr
                        }

                        SignInProblem.LocalNetwork,
                        SignInProblem.Other,
                        -> {
                            R.string.problem_other
                        }
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Runs [signIn] for the form, tracking progress and the problem. */
class YarrSignInState(
    private val network: LocalNetworkAccess,
    private val signIn: suspend (YarrFields) -> Result<Unit>,
) {
    var working by mutableStateOf(false)
    var problem by mutableStateOf<SignInProblem?>(null)

    suspend fun connect(
        fields: YarrFields,
        onDone: () -> Unit,
    ) {
        if (working || fields.address.isBlank()) return
        working = true
        problem = null
        if (!network.allow(fields.address)) {
            problem = SignInProblem.LocalNetwork
            working = false
            return
        }
        signIn(fields)
            .onSuccess { onDone() }
            .onFailure { problem = (it as? SignInError)?.problem ?: SignInProblem.Other }
        working = false
    }
}

/** "Connect to yarr" as a dialog (Settings). */
@Composable
fun YarrSignInDialog(
    signIn: suspend (YarrFields) -> Result<Unit>,
    onDismiss: () -> Unit,
    onSignedIn: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var fields by rememberSaveable(stateSaver = YarrFieldsSaver) { mutableStateOf(YarrFields()) }
    val network = rememberLocalNetworkAccess()
    val sign = remember { YarrSignInState(network, signIn) }
    val connect = { scope.launch { sign.connect(fields, onSignedIn) } }
    AlertDialog(
        onDismissRequest = { if (!sign.working) onDismiss() },
        title = { Text(stringResource(R.string.connect_yarr)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.connect_yarr_text), style = MaterialTheme.typography.bodyMedium)
                YarrSignInForm(fields, { fields = it }, sign.problem, enabled = !sign.working, onDone = { connect() })
            }
        },
        confirmButton = {
            TextButton(onClick = { connect() }, enabled = !sign.working && fields.address.isNotBlank()) {
                if (sign.working) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.connect))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sign.working) { Text(stringResource(R.string.cancel)) } },
    )
}

val YarrFieldsSaver =
    androidx.compose.runtime.saveable.listSaver<YarrFields, String>(
        save = { listOf(it.address, it.username, it.password) },
        restore = { YarrFields(it[0], it[1], it[2]) },
    )
