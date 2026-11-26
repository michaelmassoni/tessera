package io.github.michaelmassoni.tessera.calendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.calendar.R
import io.github.michaelmassoni.tessera.calendar.SearchState
import io.github.michaelmassoni.tessera.calendar.engine.Instance
import io.github.michaelmassoni.tessera.core.design.TesseraSearchBar
import java.time.LocalDate
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/**
 * Event search: a search field (focused on opening) and the matches, upcoming then past
 * ([io.github.michaelmassoni.tessera.calendar.engine.SearchResults]). Back closes it.
 */
@Composable
fun SearchScreen(
    search: SearchState,
    today: LocalDate,
    onQuery: (String) -> Unit,
    onOpen: (Instance) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    // Off in previews and screenshot tests, which have no window to focus.
    focusOnOpen: Boolean = true,
) {
    val text = rememberTextFieldState(search.query)
    val focus = remember { FocusRequester() }
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect(onQuery) }
    if (focusOnOpen) LaunchedEffect(Unit) { focus.requestFocus() }
    Scaffold(
        modifier = modifier,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.close_search))
                }
                TesseraSearchBar(
                    text = text,
                    placeholder = stringResource(R.string.search_events),
                    modifier = Modifier.weight(1f),
                    focusRequester = focus,
                    trailingIcon = {
                        if (text.text.isNotEmpty()) {
                            IconButton(onClick = { text.clearText() }) {
                                Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.clear_search))
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (search.query.isNotBlank() && search.searched) {
                EventList(
                    search.upcoming,
                    today,
                    onOpen,
                    empty = stringResource(R.string.no_matching_events),
                    past = search.past,
                    pastLabel = stringResource(R.string.past_events),
                    withYear = true,
                    contentPadding = PaddingValues(bottom = 24.dp),
                )
            }
        }
    }
}
