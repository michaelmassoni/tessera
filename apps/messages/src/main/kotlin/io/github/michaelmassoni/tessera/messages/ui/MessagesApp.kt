package io.github.michaelmassoni.tessera.messages.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.michaelmassoni.tessera.messages.R
import io.github.michaelmassoni.tessera.messages.engine.Conversation
import io.github.michaelmassoni.tessera.messages.engine.Grouping
import io.github.michaelmassoni.tessera.messages.engine.Message
import io.github.michaelmassoni.tessera.messages.engine.Row
import io.github.michaelmassoni.tessera.messages.engine.SmsLength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.michaelmassoni.tessera.core.design.R as DesignR

data class MessagesState(
    val loaded: Boolean = false,
    val conversations: List<Conversation> = emptyList(),
    val names: Map<String, String> = emptyMap(),
    val hasPermissions: Boolean = true,
)

/** The open conversation; [threadId] is null for a brand-new one. */
data class OpenThread(
    val threadId: Long?,
    val address: String,
    val messages: List<Message> = emptyList(),
    val draft: String = "",
)

interface MessagesActions {
    fun open(conversation: Conversation)

    fun startNew(address: String)

    fun close()

    fun setDraft(text: String)

    fun send()

    fun suggestions(query: String): List<Pair<String, String>>

    fun grant()

    fun openSettings()
}

private val Wide = 840.dp

@Composable
fun MessagesApp(
    state: MessagesState,
    open: OpenThread?,
    actions: MessagesActions,
    modifier: Modifier = Modifier,
) {
    var composing by rememberSaveable { mutableStateOf(false) }
    BackHandler(open != null) { actions.close() }
    BackHandler(open == null && composing) { composing = false }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= Wide
        val list: @Composable (Modifier) -> Unit = { m -> ConversationList(state, open?.threadId, actions, { composing = true }, m) }
        val detail: @Composable (Modifier) -> Unit = { m ->
            when {
                open != null -> {
                    ConversationScreen(open, state.names, actions, showBack = !wide, modifier = m)
                }

                composing -> {
                    NewMessage(actions, onClose = { composing = false }, onPick = {
                        composing = false
                        actions.startNew(it)
                    }, modifier = m)
                }

                else -> {
                    Box(m.background(MaterialTheme.colorScheme.surface))
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                list(Modifier.width(400.dp).fillMaxHeight())
                VerticalDivider()
                detail(Modifier.weight(1f).fillMaxHeight())
            }
        } else if (open != null || composing) {
            detail(Modifier.fillMaxSize())
        } else {
            list(Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationList(
    state: MessagesState,
    selected: Long?,
    actions: MessagesActions,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = actions::openSettings) {
                        Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = {
                Icon(painterResource(DesignR.drawable.ic_chat), contentDescription = null)
            }, text = { Text(stringResource(R.string.start_chat)) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.hasPermissions) {
                Notice(stringResource(R.string.permissions_needed), stringResource(R.string.grant), actions::grant)
            } else {
                Text(
                    stringResource(R.string.not_default_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            OutlinedTextField(query, {
                query = it
            }, placeholder = {
                Text(
                    stringResource(R.string.search),
                )
            }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp))
            val shown =
                state.conversations.filter { c ->
                    query.isBlank() || c.snippet.contains(query, true) ||
                        c.addresses.any { a -> a.contains(query) || state.names[a]?.contains(query, true) == true }
                }
            if (state.loaded && shown.isEmpty()) {
                Text(
                    stringResource(R.string.empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(shown, key = { it.threadId }) { c ->
                    val title = c.addresses.joinToString { state.names[it] ?: it }.ifBlank { stringResource(R.string.unknown) }
                    ListItem(
                        selected = c.threadId == selected,
                        onClick = { actions.open(c) },
                        leadingContent = { Avatar(title) },
                        supportingContent = {
                            Text(
                                c.snippet,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (c.unread) FontWeight.Bold else null,
                            )
                        },
                        trailingContent = {
                            Text(
                                DateUtils.getRelativeTimeSpanString(context, c.date, false).toString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (c.unread) FontWeight.Bold else null) }
                }
            }
        }
    }
}

@Composable
private fun Notice(
    text: String,
    action: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#", color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationScreen(
    thread: OpenThread,
    names: Map<String, String>,
    actions: MessagesActions,
    showBack: Boolean,
    modifier: Modifier = Modifier,
) {
    val title = names[thread.address] ?: thread.address
    val locale = LocalConfiguration.current.locales[0]
    val rows = remember(thread.messages) { Grouping.rows(thread.messages, ZoneId.systemDefault()).asReversed() }
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (names[thread.address] !=
                            null
                        ) {
                            Text(
                                thread.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = actions::close) {
                            Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(rows, key = { r -> if (r is Row.Bubble) r.message.id else "d" + (r as Row.Day).day }) { row ->
                    when (row) {
                        is Row.Day -> {
                            Text(
                                row.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            )
                        }

                        is Row.Bubble -> {
                            Bubble(row, DateUtils.formatDateTime(context, row.message.date, DateUtils.FORMAT_SHOW_TIME))
                        }
                    }
                }
            }
            Composer(thread.draft, actions)
        }
    }
}

@Composable
private fun Bubble(
    row: Row.Bubble,
    time: String,
) {
    val m = row.message
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start) {
        Surface(
            color = if (m.outgoing) scheme.primaryContainer else scheme.surfaceContainerHigh,
            contentColor = if (m.outgoing) scheme.onPrimaryContainer else scheme.onSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (m.hasMedia) {
                    Text(
                        stringResource(R.string.picture_message),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (m.body.isNotEmpty()) Text(m.body, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (row.lastInGroup || m.failed) {
            Text(
                when {
                    m.failed -> stringResource(R.string.not_sent)
                    m.pending -> stringResource(R.string.sending)
                    else -> time
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (m.failed) scheme.error else scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    actions: MessagesActions,
) {
    val info = SmsLength.info(draft)
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = draft,
            onValueChange = actions::setDraft,
            placeholder = { Text(stringResource(R.string.text_message)) },
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            supportingText =
                if (draft.isNotEmpty() &&
                    (info.segments > 1 || info.remaining < 20)
                ) {
                    ({ Text("${info.remaining} / ${info.segments}") })
                } else {
                    null
                },
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = actions::send, enabled = draft.isNotBlank(), modifier = Modifier.padding(start = 8.dp).size(48.dp)) {
            Icon(painterResource(DesignR.drawable.ic_send), contentDescription = stringResource(R.string.send))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewMessage(
    actions: MessagesActions,
    onClose: () -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var to by rememberSaveable { mutableStateOf("") }
    val suggestions by produceState(emptyList<Pair<String, String>>(), to) {
        value = withContext(Dispatchers.IO) { actions.suggestions(to) }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_message)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text(stringResource(R.string.to)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { if (to.isNotBlank()) onPick(to.trim()) }, enabled = to.isNotBlank()) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_forward), contentDescription = stringResource(R.string.next))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            LazyColumn {
                items(suggestions) { (name, number) ->
                    ListItem(
                        onClick = { onPick(number) },
                        leadingContent = { Avatar(name) },
                        supportingContent = { Text(number) },
                    ) { Text(name) }
                }
            }
        }
    }
}
