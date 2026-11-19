package io.github.michaelmassoni.tessera.messages.ui

import android.content.ClipData
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.michaelmassoni.tessera.messages.R
import io.github.michaelmassoni.tessera.messages.engine.Attachment
import io.github.michaelmassoni.tessera.messages.engine.Grouping
import io.github.michaelmassoni.tessera.messages.engine.Message
import io.github.michaelmassoni.tessera.messages.engine.Row
import io.github.michaelmassoni.tessera.messages.engine.SmsLength
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import io.github.michaelmassoni.tessera.core.design.R as DesignR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConversationScreen(
    thread: OpenThread,
    state: MessagesState,
    actions: MessagesActions,
    showBack: Boolean,
    onForward: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = state.names
    val group = thread.addresses.size > 1
    val title = thread.addresses.joinToString { names[it] ?: it }
    val locale = LocalConfiguration.current.locales[0]
    val rows = remember(thread.messages) { Grouping.rows(thread.messages, ZoneId.systemDefault()).asReversed() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable(thread.threadId) { mutableStateOf(emptySet<String>()) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    val chosen = thread.messages.filter { it.id in selected }
    val chosenText = chosen.joinToString("\n") { it.body }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (selected.isNotEmpty()) {
                TopAppBar(
                    title = { Text(selected.size.toString()) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    navigationIcon = {
                        IconButton(onClick = {
                            selected = emptySet()
                        }) {
                            Icon(
                                painterResource(DesignR.drawable.ic_close),
                                contentDescription = stringResource(R.string.clear_selection),
                            )
                        }
                    },
                    actions = {
                        if (chosenText.isNotBlank()) {
                            IconButton(onClick = {
                                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, chosenText))) }
                                selected = emptySet()
                            }) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_content_copy),
                                    contentDescription = stringResource(R.string.copy),
                                )
                            }
                            IconButton(onClick = {
                                actions.share(chosenText)
                                selected = emptySet()
                            }) { Icon(painterResource(DesignR.drawable.ic_share), contentDescription = stringResource(R.string.share)) }
                            IconButton(onClick = {
                                selected = emptySet()
                                onForward(chosenText)
                            }) { Icon(painterResource(DesignR.drawable.ic_forward), contentDescription = stringResource(R.string.forward)) }
                        }
                        if (chosen.size == 1) {
                            IconButton(onClick = {
                                dialog = DIALOG_DETAILS
                            }) { Icon(painterResource(DesignR.drawable.ic_info), contentDescription = stringResource(R.string.details)) }
                        }
                        IconButton(onClick = { dialog = if (state.isDefault) DIALOG_DELETE else DIALOG_NEEDS_DEFAULT }) {
                            Icon(painterResource(DesignR.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val subtitle =
                                if (group) {
                                    pluralStringResource(R.plurals.group_count, thread.addresses.size, thread.addresses.size)
                                } else {
                                    thread.addresses.singleOrNull()?.takeIf {
                                        names[it] !=
                                            null
                                    }
                                }
                            if (subtitle !=
                                null
                            ) {
                                Text(
                                    subtitle,
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
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (!thread.loaded && thread.threadId != null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            } else {
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
                                val id = row.message.id
                                val isSelected = id in selected
                                val toggle = { selected = if (isSelected) selected - id else selected + id }
                                Bubble(
                                    row,
                                    time = DateUtils.formatDateTime(context, row.message.date, DateUtils.FORMAT_SHOW_TIME),
                                    sender = if (group && !row.message.outgoing) row.message.sender?.let { names[it] ?: it } else null,
                                    selected = isSelected,
                                    onClick = { if (selected.isNotEmpty()) toggle() },
                                    onLongClick = toggle,
                                    actions = actions,
                                )
                            }
                        }
                    }
                }
            }
            Composer(thread, actions)
        }
    }

    when (dialog) {
        DIALOG_NEEDS_DEFAULT -> {
            NeedsDefaultDialog(onDismiss = { dialog = null }, onMakeDefault = actions::makeDefault)
        }

        DIALOG_DELETE -> {
            ConfirmDialog(
                title = stringResource(if (selected.size == 1) R.string.delete_message else R.string.delete_messages),
                text = null,
                confirm = stringResource(R.string.delete),
                onDismiss = { dialog = null },
            ) {
                actions.deleteMessages(selected)
                selected = emptySet()
            }
        }

        DIALOG_DETAILS -> {
            chosen.singleOrNull()?.let { m -> Details(m, thread, names) { dialog = null } } ?: run { dialog = null }
        }
    }
}

private const val DIALOG_NEEDS_DEFAULT = "needs_default"
private const val DIALOG_DELETE = "delete"
private const val DIALOG_DETAILS = "details"

@Composable
private fun Details(
    m: Message,
    thread: OpenThread,
    names: Map<String, String>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val who =
        (if (m.outgoing) thread.addresses else listOfNotNull(m.sender ?: thread.addresses.firstOrNull())).joinToString { a ->
            names[a]?.let { "$it ($a)" }
                ?: a
        }
    val time =
        DateUtils.formatDateTime(
            context,
            m.date,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
        )
    val type = stringResource(if (m.id.startsWith("m")) R.string.type_mms else R.string.type_sms)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.detail_type, type))
                Text(stringResource(if (m.outgoing) R.string.detail_to else R.string.detail_from, who))
                Text(stringResource(if (m.outgoing) R.string.detail_sent else R.string.detail_received, time))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

@Composable
private fun Bubble(
    row: Row.Bubble,
    time: String,
    sender: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    actions: MessagesActions,
) {
    val m = row.message
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(R.string.select))
            .semantics { this.selected = selected },
        horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start,
    ) {
        if (sender != null && row.firstInGroup) {
            Text(
                sender,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
            )
        }
        Surface(
            color =
                when {
                    selected -> scheme.tertiaryContainer
                    m.outgoing -> scheme.primaryContainer
                    else -> scheme.surfaceContainerHigh
                },
            contentColor =
                if (selected) {
                    scheme.onTertiaryContainer
                } else if (m.outgoing) {
                    scheme.onPrimaryContainer
                } else {
                    scheme.onSurface
                },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Column {
                m.attachments.forEach { a -> AttachmentView(a, actions, enabled = !selected) }
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (m.downloadable) {
                        Text(stringResource(R.string.picture_not_downloaded), style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onClick = { actions.download(m.id) }, modifier = Modifier.padding(top = 6.dp)) {
                            Icon(
                                painterResource(DesignR.drawable.ic_download),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(stringResource(R.string.download))
                        }
                    }
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
        }
        if (row.lastInGroup || m.failed || m.pending) {
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
private fun AttachmentView(
    a: Attachment,
    actions: MessagesActions,
    enabled: Boolean,
) {
    if (a.isImage) {
        AsyncImage(
            model = a.uri,
            contentDescription = stringResource(R.string.picture),
            contentScale = ContentScale.FillWidth,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .combinedClickable(enabled = enabled, onClick = { actions.openAttachment(a) }),
        )
    } else {
        AssistChip(
            onClick = { actions.openAttachment(a) },
            enabled = enabled,
            label = { Text(stringResource(if (a.mime.startsWith("video/")) R.string.video else R.string.audio)) },
            modifier = Modifier.padding(start = 12.dp, top = 8.dp),
        )
    }
}

@Composable
private fun Composer(
    thread: OpenThread,
    actions: MessagesActions,
) {
    val draft = thread.draft
    val info = SmsLength.info(draft)
    var scheduling by rememberSaveable { mutableStateOf(false) }
    val mms = thread.attachments.isNotEmpty() || thread.addresses.size > 1
    Column(Modifier.fillMaxWidth()) {
        thread.scheduled.forEach { ScheduledRow(it, actions) }
        if (thread.attachments.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(thread.attachments) { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = stringResource(R.string.picture),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        FilledIconButton(
                            onClick = { actions.removeAttachment(uri) },
                            modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                        ) {
                            Icon(
                                painterResource(DesignR.drawable.ic_close),
                                contentDescription = stringResource(R.string.remove_picture),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions::attach) {
                Icon(painterResource(DesignR.drawable.ic_add_photo_alternate), contentDescription = stringResource(R.string.attach_picture))
            }
            TextField(
                value = draft,
                onValueChange = actions::setDraft,
                placeholder = { Text(stringResource(if (mms) R.string.mms_message else R.string.text_message)) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                supportingText =
                    if (!mms && draft.isNotEmpty() &&
                        (info.segments > 1 || info.remaining < 20)
                    ) {
                        ({ Text("${info.remaining} / ${info.segments}") })
                    } else {
                        null
                    },
                modifier = Modifier.weight(1f),
            )
            SendButton(
                enabled = draft.isNotBlank() || thread.attachments.isNotEmpty(),
                onSend = actions::send,
                onSchedule = { scheduling = true },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
    if (scheduling) ScheduleSheet(actions, onDismiss = { scheduling = false })
}
