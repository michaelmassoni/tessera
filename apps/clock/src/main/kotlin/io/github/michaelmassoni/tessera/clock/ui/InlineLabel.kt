package io.github.michaelmassoni.tessera.clock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.design.keyboardVisible
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

/**
 * An alarm's or timer's label, edited where it's shown (docs/clock.md, Labels): tap it and type,
 * no dialog. [placeholder] ("Add label") shows while it's empty. Saved on Done, when the field
 * loses focus or the keyboard closes, and shortly after typing stops, so nothing typed is lost.
 * One line, at most [MAX_LENGTH] characters.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun InlineLabel(
    label: String,
    placeholder: String,
    onCommit: (String) -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val state = rememberTextFieldState(label)
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val save by rememberUpdatedState(onCommit)
    val saved by rememberUpdatedState(label)

    fun commit() {
        val text = state.text.toString().trim()
        if (text != saved) save(text)
    }
    // The label changed elsewhere (another device, the notification): show it unless mid-edit.
    LaunchedEffect(label) { if (!focused && state.text.toString() != label) state.setTextAndPlaceCursorAtEnd(label) }
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.drop(1).debounce(SAVE_DELAY_MS).collect { commit() } }
    // Back closes the keyboard but leaves the field focused: end the edit there too.
    val keyboard = keyboardVisible()
    LaunchedEffect(keyboard) { if (!keyboard && focused) focusManager.clearFocus() }
    DisposableEffect(Unit) { onDispose { commit() } }
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        state = state,
        modifier =
            modifier.heightIn(min = 48.dp).onFocusChanged { f ->
                if (focused && !f.isFocused) commit()
                focused = f.isFocused
            },
        textStyle = style.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        lineLimits = TextFieldLineLimits.SingleLine,
        inputTransformation = InputTransformation.maxLengthTrim(MAX_LENGTH),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        onKeyboardAction = { focusManager.clearFocus() },
        decorator = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (state.text.isEmpty()) Text(placeholder, style = style, color = colors.onSurfaceVariant)
                field()
            }
        },
    )
}

private const val MAX_LENGTH = 60
private const val SAVE_DELAY_MS = 800L
