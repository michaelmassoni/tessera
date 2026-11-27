package io.github.michaelmassoni.tessera.core.design

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A search field that looks like Material's collapsed search bar and filters in place.
 *
 * Material's `SearchBar(state = …)` is only the collapsed half of a pair: it blocks the soft
 * keyboard on purpose, expecting an `ExpandedFullScreenSearchBar` to take over typing. Used on its
 * own (as every Tessera list does), tapping it focuses the field with a blinking cursor but the
 * keyboard never opens. This draws the same container without that block.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TesseraSearchBar(
    text: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    // Given, the field can be focused from outside (a search screen focuses it on opening).
    focusRequester: FocusRequester? = null,
    // The keyboard's search key (for searches that run on request, not as you type).
    onSearch: (String) -> Unit = {},
) {
    val searchState = rememberSearchBarState()
    val colors = SearchBarDefaults.colors()
    Surface(
        modifier = modifier,
        shape = SearchBarDefaults.inputFieldShape,
        color = colors.containerColor,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = searchState,
            modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            onSearch = onSearch,
            // One line: a long hint ends in "…" rather than wrapping the bar to two lines.
            placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            colors = colors.inputFieldColors,
        )
    }
}

/**
 * Whether the soft keyboard is showing. A `BackHandler` that clears a search should be disabled
 * while it is: re-enabled after the keyboard opened, it would take Back before the keyboard does,
 * clearing the text instead of closing the keyboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun keyboardVisible(): Boolean = WindowInsets.isImeVisible

/**
 * A screen's top bar when search is what it's for (Contacts, Recents, a search screen): no title,
 * just the search bar, with back outside it when there is somewhere to go back to. While there's
 * text the bar ends in a clear button; otherwise in [action] (settings, a menu).
 */
@Composable
fun TesseraSearchTopBar(
    text: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
    // Focus the field (keyboard up) when the bar first appears, not again after rotation.
    focusOnOpen: Boolean = false,
    onSearch: (String) -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    var focused by rememberSaveable { mutableStateOf(false) }
    if (focusOnOpen) {
        LaunchedEffect(Unit) {
            if (!focused) focus.requestFocus()
            focused = true
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = if (onBack != null) 4.dp else 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.design_back))
            }
        }
        TesseraSearchBar(
            text = text,
            placeholder = placeholder,
            modifier = Modifier.weight(1f),
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = {
                if (text.text.isNotEmpty()) {
                    IconButton(onClick = { text.clearText() }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.design_clear_search))
                    }
                } else {
                    action?.invoke()
                }
            },
            focusRequester = focus,
            onSearch = onSearch,
        )
    }
}

/** [TesseraSearchTopBar] for a screen that keeps its query as a String. */
@Composable
fun TesseraSearchTopBar(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: @Composable (() -> Unit)? = null,
    focusOnOpen: Boolean = false,
    onSearch: (String) -> Unit = {},
) {
    val text = rememberTextFieldState(query)
    // Typing reports the query; a query changed from outside (cleared on close) shows in the field.
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect { if (it != query) onQuery(it) } }
    LaunchedEffect(query) { if (text.text.toString() != query) text.setTextAndPlaceCursorAtEnd(query) }
    TesseraSearchTopBar(text, placeholder, modifier, onBack, action, focusOnOpen, onSearch)
}
