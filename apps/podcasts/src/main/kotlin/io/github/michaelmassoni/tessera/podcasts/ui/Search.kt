package io.github.michaelmassoni.tessera.podcasts.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.core.design.TesseraSearchBar
import io.github.michaelmassoni.tessera.core.design.TesseraSearchTopBar
import io.github.michaelmassoni.tessera.core.design.TesseraTextField
import io.github.michaelmassoni.tessera.podcasts.R
import io.github.michaelmassoni.tessera.podcasts.data.AddException
import io.github.michaelmassoni.tessera.podcasts.data.AddProblem
import io.github.michaelmassoni.tessera.podcasts.engine.SearchResult
import kotlinx.coroutines.launch
import java.io.IOException
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What the search page shows below the field. */
sealed interface SearchPhase {
    data object Idle : SearchPhase

    data object Searching : SearchPhase

    data object Failed : SearchPhase

    data class Results(
        val results: List<SearchResult>,
    ) : SearchPhase
}

/** The search field and results, kept while other tabs are open. */
@Stable
class SearchModel(
    query: String = "",
    phase: SearchPhase = SearchPhase.Idle,
) {
    val text = TextFieldState(query)
    val query: String get() = text.text.toString()
    var phase by mutableStateOf(phase)
}

/** Search Apple's and fyyd's directories; a result opens the show. "Add by URL" in the menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    actions: PodcastsActions,
    onOpenShow: (String) -> Unit,
    modifier: Modifier = Modifier,
    model: SearchModel = remember { SearchModel() },
) {
    val scope = rememberCoroutineScope()
    var phase by model::phase
    // Clearing the field (its clear button) goes back to the introduction.
    LaunchedEffect(model.text) { snapshotFlow { model.text.text.isEmpty() }.collect { if (it) phase = SearchPhase.Idle } }
    var opening by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<AddProblem?>(null) }
    var menu by remember { mutableStateOf(false) }
    var addingUrl by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun search() {
        val terms = model.query.trim()
        if (terms.isEmpty()) return
        keyboard?.hide()
        phase = SearchPhase.Searching
        scope.launch {
            phase =
                try {
                    SearchPhase.Results(actions.search(terms))
                } catch (_: IOException) {
                    SearchPhase.Failed
                }
        }
    }

    fun open(block: suspend () -> String) {
        scope.launch {
            try {
                onOpenShow(block())
            } catch (e: AddException) {
                problem = e.problem
            } finally {
                opening = null
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            // Like Contacts: no title, the search bar at the top with its menu inside.
            TesseraSearchTopBar(
                text = model.text,
                placeholder = stringResource(R.string.search_hint),
                onSearch = { search() },
                action = {
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(
                                painterResource(DesignR.drawable.ic_more_vert),
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_by_url)) },
                                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_link), contentDescription = null) },
                                onClick = {
                                    menu = false
                                    addingUrl = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings)) },
                                leadingIcon = { Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = null) },
                                onClick = {
                                    menu = false
                                    actions.openSettings()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            when (val p = phase) {
                SearchPhase.Idle -> {
                    item(key = "idle") {
                        Text(
                            stringResource(R.string.search_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                }

                SearchPhase.Searching -> {
                    item(key = "searching") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }

                SearchPhase.Failed -> {
                    item(key = "failed") {
                        Text(
                            stringResource(R.string.search_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                }

                is SearchPhase.Results -> {
                    if (p.results.isEmpty()) {
                        item(key = "none") {
                            Text(
                                stringResource(R.string.search_none),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                        }
                    }
                    items(p.results, key = { it.feedUrl }) { result ->
                        ListItem(
                            supportingContent =
                                if (result.author.isNotBlank()) {
                                    { Text(result.author, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                } else {
                                    null
                                },
                            leadingContent = { Cover(result.artworkUrl, 56.dp) },
                            trailingContent =
                                if (opening == result.feedUrl) {
                                    { CircularProgressIndicator(Modifier.padding(4.dp)) }
                                } else {
                                    null
                                },
                            modifier =
                                Modifier.clickable(enabled = opening == null) {
                                    opening = result.feedUrl
                                    open { actions.openFeed(result.feedUrl) }
                                },
                        ) { Text(result.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }

    if (addingUrl) {
        AddByUrlDialog(
            onDismiss = { addingUrl = false },
            onAdd = { address ->
                addingUrl = false
                opening = address
                open { actions.openAddress(address) }
            },
        )
    }
    problem?.let { p ->
        AlertDialog(
            onDismissRequest = { problem = null },
            title = { Text(stringResource(R.string.cant_open_title)) },
            text = { Text(stringResource(if (p == AddProblem.Offline) R.string.cant_open_offline else R.string.cant_open_not_feed)) },
            confirmButton = { TextButton(onClick = { problem = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun AddByUrlDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_by_url)) },
        text = {
            TesseraTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.feed_url)) },
                supportingText = { Text(stringResource(R.string.feed_url_help)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onAdd(text.trim()) }),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
