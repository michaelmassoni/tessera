package io.github.michaelmassoni.tessera.core.design

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
            onSearch = {},
            placeholder = { Text(placeholder) },
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
