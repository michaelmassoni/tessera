package io.github.michaelmassoni.tessera.recorder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.recorder.R
import io.github.michaelmassoni.tessera.recorder.data.Recording
import io.github.michaelmassoni.tessera.recorder.engine.Durations
import io.github.michaelmassoni.tessera.recorder.engine.RecorderState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.michaelmassoni.tessera.core.design.R as DesignR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListScreen(
    ui: RecorderUi,
    actions: RecorderActions,
    snackbar: SnackbarHostState,
    onOpen: (Recording) -> Unit,
    onShowRecording: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf(emptySet<Long>()) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val chosen = ui.recordings.filter { it.id in selected }
    // Back clears a selection, then closes search, before leaving the app.
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(searching && selected.isEmpty()) {
        searching = false
        query = ""
    }
    val shown =
        if (query.isBlank()) ui.recordings else ui.recordings.filter { it.title.contains(query.trim(), ignoreCase = true) }
    Scaffold(
        modifier = modifier,
        topBar = {
            when {
                chosen.isNotEmpty() -> {
                    TopAppBar(
                        title = { Text(pluralStringResource(R.plurals.selected, chosen.size, chosen.size)) },
                        navigationIcon = {
                            IconButton(onClick = { selected = emptySet() }) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_close),
                                    contentDescription = stringResource(R.string.clear_selection),
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { actions.share(chosen) }) {
                                Icon(painterResource(DesignR.drawable.ic_share), contentDescription = stringResource(R.string.share))
                            }
                            IconButton(onClick = {
                                actions.delete(chosen)
                                selected = emptySet()
                            }) {
                                Icon(painterResource(DesignR.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                            }
                        },
                    )
                }

                searching -> {
                    val focus = remember { FocusRequester() }
                    // Only when search opens, not when the list comes back from a recording.
                    var focused by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        if (!focused) focus.requestFocus()
                        focused = true
                    }
                    TopAppBar(
                        title = {
                            TextField(
                                query,
                                { query = it },
                                placeholder = { Text(stringResource(R.string.search_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                colors =
                                    TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                    ),
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                searching = false
                                query = ""
                            }) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_arrow_back),
                                    contentDescription = stringResource(R.string.close_search),
                                )
                            }
                        },
                    )
                }

                else -> {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        navigationIcon = {
                            IconButton(onClick = { searching = true }) {
                                Icon(painterResource(DesignR.drawable.ic_search), contentDescription = stringResource(R.string.search))
                            }
                        },
                        actions = {
                            IconButton(onClick = actions::openSettings) {
                                Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                            }
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (ui.state == RecorderState.Idle && chosen.isEmpty()) {
                LargeFloatingActionButton(onClick = onRecord) {
                    Icon(
                        painterResource(DesignR.drawable.ic_mic),
                        contentDescription = stringResource(R.string.record),
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        },
        bottomBar = {
            val state = ui.state
            if (state is RecorderState.Active) RecordingBar(state, onShowRecording, actions::stop)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when {
                ui.loaded && ui.recordings.isEmpty() -> {
                    Empty(stringResource(R.string.empty_title), stringResource(R.string.empty_text))
                }

                ui.loaded && shown.isEmpty() -> {
                    Empty(stringResource(R.string.no_matches), "")
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 120.dp),
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    ) {
                        items(shown, key = { it.id }) { r ->
                            val isSelected = r.id in selected
                            val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            ListItem(
                                selected = isSelected,
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        selected = if (isSelected) selected - r.id else selected + r.id
                                    } else {
                                        onOpen(r)
                                    }
                                },
                                onLongClick = { selected = if (isSelected) selected - r.id else selected + r.id },
                                onLongClickLabel = stringResource(R.string.select),
                                supportingContent = { Text(dateLabel(r.dateMs)) },
                                trailingContent = { Text(Durations.clock(r.durationMs), style = MaterialTheme.typography.labelLarge) },
                                leadingContent = {
                                    Icon(
                                        painterResource(if (isSelected) DesignR.drawable.ic_check_circle else DesignR.drawable.ic_mic),
                                        contentDescription = null,
                                        tint = tint,
                                    )
                                },
                            ) { Text(r.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

/** "Recording · 3:12" while recording, to go back to it, with Stop. */
@Composable
private fun RecordingBar(
    state: RecorderState.Active,
    onOpen: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Icon(painterResource(if (state.paused) DesignR.drawable.ic_pause else DesignR.drawable.ic_mic), contentDescription = null)
            Text(
                stringResource(if (state.paused) R.string.bar_paused else R.string.bar_recording, Durations.clock(state.elapsedMs)),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(onClick = onStop) {
                Icon(painterResource(DesignR.drawable.ic_stop), contentDescription = stringResource(R.string.stop))
            }
        }
    }
}

@Composable
private fun Empty(
    title: String,
    text: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 120.dp, start = 32.dp, end = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(
            painterResource(DesignR.drawable.ic_mic),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "23 Nov 2026, 10:42" in the person's locale. */
@Composable
internal fun dateLabel(ms: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale) }
    return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(formatter)
}
