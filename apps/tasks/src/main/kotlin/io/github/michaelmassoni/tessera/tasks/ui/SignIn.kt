package io.github.michaelmassoni.tessera.tasks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.tasks.R
import io.github.michaelmassoni.tessera.tasks.data.RemoteList
import io.github.michaelmassoni.tessera.tasks.data.SignInProblem
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What the sign-in form holds. */
data class SignInFields(
    val address: String = "",
    val username: String = "",
    val password: String = "",
)

/** Server address, username and password, and what went wrong last time. */
@Composable
fun SignInForm(
    fields: SignInFields,
    onChange: (SignInFields) -> Unit,
    problem: SignInProblem?,
    enabled: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by rememberSaveable { mutableStateOf(false) }
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
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            fields.password,
            { onChange(fields.copy(password = it)) },
            label = { Text(stringResource(R.string.app_password)) },
            supportingText = { Text(stringResource(R.string.app_password_hint)) },
            singleLine = true,
            enabled = enabled,
            isError = problem == SignInProblem.WrongLogin,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            trailingIcon = {
                IconButton(onClick = { show = !show }) {
                    Icon(
                        painterResource(if (show) DesignR.drawable.ic_visibility_off else DesignR.drawable.ic_visibility),
                        contentDescription = stringResource(if (show) R.string.hide_password else R.string.show_password),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (problem != null) {
            Text(
                stringResource(
                    when (problem) {
                        SignInProblem.BadAddress -> R.string.problem_bad_address
                        SignInProblem.Insecure -> R.string.problem_insecure
                        SignInProblem.Unreachable -> R.string.problem_unreachable
                        SignInProblem.WrongLogin -> R.string.problem_wrong_login
                        SignInProblem.NotCalDav -> R.string.problem_not_caldav
                        SignInProblem.Other -> R.string.problem_other
                    },
                ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** The server's task lists, ticked to sync. */
@Composable
fun ListPicker(
    lists: List<RemoteList>,
    chosen: Set<String>,
    onChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (lists.isEmpty()) Text(stringResource(R.string.no_server_lists), color = MaterialTheme.colorScheme.onSurfaceVariant)
        lists.forEach { l ->
            val on = l.href in chosen
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(on, role = Role.Checkbox) { onChange(if (it) chosen + l.href else chosen - l.href) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = on, onCheckedChange = null, modifier = Modifier.size(48.dp))
                Text(l.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
