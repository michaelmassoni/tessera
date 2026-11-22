package io.github.michaelmassoni.tessera.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.music.R
import io.github.michaelmassoni.tessera.music.engine.ServerProblem
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What the sign-in form holds. */
data class SignInFields(
    val address: String = "",
    val username: String = "",
    val password: String = "",
)

/**
 * Server address, username and password, with what went wrong last time underneath. The
 * caller supplies the Connect button (setup page or dialog).
 */
@Composable
fun ServerSignInFields(
    fields: SignInFields,
    onChange: (SignInFields) -> Unit,
    problem: ServerProblem?,
    enabled: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = fields.address,
            onValueChange = { onChange(fields.copy(address = it)) },
            label = { Text(stringResource(R.string.server_address)) },
            placeholder = { Text(stringResource(R.string.server_address_hint)) },
            singleLine = true,
            enabled = enabled,
            isError = problem == ServerProblem.BadAddress || problem == ServerProblem.Insecure,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fields.username,
            onValueChange = { onChange(fields.copy(username = it)) },
            label = { Text(stringResource(R.string.server_username)) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = fields.password,
            onValueChange = { onChange(fields.copy(password = it)) },
            label = { Text(stringResource(R.string.server_password)) },
            singleLine = true,
            enabled = enabled,
            isError = problem == ServerProblem.WrongLogin,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        painterResource(if (showPassword) DesignR.drawable.ic_visibility_off else DesignR.drawable.ic_visibility),
                        contentDescription = stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (problem != null) {
            Text(
                stringResource(problem.message()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** What to tell the person about a [ServerProblem]. */
fun ServerProblem.message(): Int =
    when (this) {
        ServerProblem.Unreachable -> R.string.problem_unreachable
        ServerProblem.WrongLogin -> R.string.problem_wrong_login
        ServerProblem.NotSubsonic -> R.string.problem_not_subsonic
        ServerProblem.TooOld -> R.string.problem_too_old
        ServerProblem.Insecure -> R.string.problem_insecure
        ServerProblem.BadAddress -> R.string.problem_bad_address
        ServerProblem.Other -> R.string.problem_other
    }
