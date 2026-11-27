package io.github.michaelmassoni.tessera.core.design

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * A password field with a show/hide button: Material's `OutlinedSecureTextField` (the
 * state-based field that replaced `PasswordVisualTransformation`), kept on a plain String so the
 * sign-in forms keep their own state as before.
 */
@Composable
fun TesseraPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    onDone: () -> Unit = {},
) {
    val state = rememberTextFieldState(value)
    var show by rememberSaveable { mutableStateOf(false) }
    val change by rememberUpdatedState(onValueChange)
    val current by rememberUpdatedState(value)
    // Typing reports the new text; a change from outside (the form cleared) replaces it.
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect { if (it != current) change(it) } }
    LaunchedEffect(value) { if (state.text.toString() != value) state.setTextAndPlaceCursorAtEnd(value) }
    OutlinedSecureTextField(
        state = state,
        modifier = modifier,
        // Rounded like TesseraTextField.
        shape = MaterialTheme.shapes.large,
        enabled = enabled,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        textObfuscationMode = if (show) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        onKeyboardAction = { onDone() },
        trailingIcon = {
            IconButton(onClick = { show = !show }) {
                Icon(
                    painterResource(if (show) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(if (show) R.string.design_hide_password else R.string.design_show_password),
                )
            }
        },
    )
}
