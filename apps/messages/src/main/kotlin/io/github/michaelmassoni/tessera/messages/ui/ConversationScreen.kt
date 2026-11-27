package io.github.michaelmassoni.tessera.messages.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import io.github.michaelmassoni.tessera.messages.R
import io.github.michaelmassoni.tessera.messages.engine.Attachment
import io.github.michaelmassoni.tessera.messages.engine.Grouping
import io.github.michaelmassoni.tessera.messages.engine.Message
import io.github.michaelmassoni.tessera.messages.engine.MessageLink
import io.github.michaelmassoni.tessera.messages.engine.MessageLinks
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
                        // One message with text: pick out part of it to copy.
                        if (chosen.singleOrNull()?.body?.isNotBlank() == true) {
                            IconButton(onClick = { dialog = DIALOG_SELECT_TEXT }) {
                                Icon(
                                    painterResource(DesignR.drawable.ic_select_all),
                                    contentDescription = stringResource(R.string.select_text),
                                )
                            }
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
                    actions = {
                        // One person with a phone number: call them (the phone app opens with the number).
                        val number = thread.addresses.singleOrNull()?.takeIf { a -> a.count(Char::isDigit) >= 3 }
                        if (number != null) {
                            val context = LocalContext.current
                            IconButton(onClick = { dial(context, "tel:$number") }) {
                                Icon(painterResource(DesignR.drawable.ic_call), contentDescription = stringResource(R.string.call, title))
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
                val listState = rememberLazyListState()
                // The newest message is item 0 (the list is reversed). When one arrives, a reversed list
                // stays on what was showing, leaving it just out of sight: follow it if you sent it, or
                // if you were already at the bottom.
                val newest = thread.messages.maxByOrNull { it.date }
                LaunchedEffect(newest?.key) {
                    if (newest != null && (newest.outgoing || listState.firstVisibleItemIndex <= 1)) listState.animateScrollToItem(0)
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(rows, key = { r -> if (r is Row.Bubble) r.message.key else "d" + (r as Row.Day).day }) { row ->
                        // New bubbles fade in and the rest slide up to make room.
                        Box(Modifier.animateItem()) {
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
                                        onClick = {
                                            when {
                                                selected.isNotEmpty() -> toggle()

                                                // A failed text: tapping it sends it again.
                                                row.message.failed && row.message.id.startsWith("s") -> actions.retry(row.message.id)
                                            }
                                        },
                                        selecting = selected.isNotEmpty(),
                                        onLongClick = toggle,
                                        actions = actions,
                                    )
                                }
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

        DIALOG_SELECT_TEXT -> {
            chosen.singleOrNull()?.let { m -> SelectTextDialog(m.body) { dialog = null } } ?: run { dialog = null }
        }
    }
}

private const val DIALOG_NEEDS_DEFAULT = "needs_default"
private const val DIALOG_DELETE = "delete"
private const val DIALOG_DETAILS = "details"
private const val DIALOG_SELECT_TEXT = "select_text"

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
    // Messages are being selected: a tap or long-press selects, even on a link.
    selecting: Boolean = false,
) {
    val m = row.message
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // Long-press on a link, email or number: a menu to copy or open it. Anywhere else: select the message.
    val links = remember(m.body) { MessageLinks.find(m.body) }
    var pressAt by remember { mutableStateOf(Offset.Zero) }
    var bubbleCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var linkMenu by remember { mutableStateOf<MessageLink?>(null) }
    val openLabel = stringResource(R.string.open_item)
    val copyLabel = stringResource(R.string.copy_item)

    fun linkPressed(): MessageLink? {
        val outer = bubbleCoords ?: return null
        val inner = textCoords?.takeIf { it.isAttached } ?: return null
        val layout = textLayout ?: return null
        val local = inner.localPositionOf(outer, pressAt)
        if (local.x < 0 || local.y < 0 || local.x > inner.size.width || local.y > inner.size.height) return null
        val offset = layout.getOffsetForPosition(local)
        return links.firstOrNull { offset in it.start until it.end }
    }
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { bubbleCoords = it }
            // Where the finger went down (watched, not taken: selecting still works).
            .pointerInput(Unit) {
                awaitEachGesture { pressAt = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position }
            }.combinedClickable(
                // The bubble handles its links: a tap opens one, a long-press offers to copy it.
                onClick = { (if (selecting) null else linkPressed())?.let { openLink(context, it) } ?: onClick() },
                onLongClick = { (if (selecting) null else linkPressed())?.let { linkMenu = it } ?: onLongClick() },
                onLongClickLabel = stringResource(R.string.select),
            ).semantics {
                this.selected = selected
                // TalkBack can't touch a link inside the bubble: each gets Open and Copy actions.
                customActions =
                    links.flatMap { link ->
                        val shown = m.body.substring(link.start, link.end)
                        listOf(
                            CustomAccessibilityAction(openLabel.format(shown)) {
                                openLink(context, link)
                                true
                            },
                            CustomAccessibilityAction(copyLabel.format(shown)) {
                                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(shown, copyValue(link, shown)))) }
                                true
                            },
                        )
                    }
            },
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
                // A picture with no text has no text area (it left an empty band under the picture).
                val label = m.hasMedia && m.attachments.isEmpty()
                if (m.downloadable || label || m.body.isNotEmpty()) {
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
                        if (label) {
                            Text(
                                stringResource(R.string.picture_message),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        if (m.body.isNotEmpty()) {
                            Box {
                                Text(
                                    linked(m.body),
                                    style = MaterialTheme.typography.bodyLarge,
                                    onTextLayout = { textLayout = it },
                                    modifier = Modifier.onGloballyPositioned { textCoords = it },
                                )
                                linkMenu?.let { link -> LinkMenu(link, m.body.substring(link.start, link.end)) { linkMenu = null } }
                            }
                        }
                    }
                }
            }
        }
        if (row.lastInGroup || m.failed || m.pending) {
            Text(
                when {
                    m.failed && m.id.startsWith("s") -> stringResource(R.string.not_sent_retry)
                    m.failed -> stringResource(R.string.not_sent)
                    m.pending -> stringResource(R.string.sending)
                    m.delivered -> stringResource(R.string.delivered_at, time)
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

/** [text] with its web links, email addresses and phone numbers underlined and tappable. */
@Composable
private fun linked(text: String): AnnotatedString {
    val links = remember(text) { MessageLinks.find(text) }
    if (links.isEmpty()) return AnnotatedString(text)
    val style = SpanStyle(textDecoration = TextDecoration.Underline)
    return remember(text, links) {
        buildAnnotatedString {
            append(text)
            // Drawn as links; the bubble opens them on a tap and offers Copy on a long-press.
            links.forEach { link -> addStyle(style, link.start, link.end) }
        }
    }
}

/** Opens the default phone app with [tel] ready to call. */
private fun dial(
    context: Context,
    tel: String,
) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, tel.toUri())) }
}

/** Long-press on a link, email address or number in a message: copy it, or open it. */
@Composable
private fun LinkMenu(
    link: MessageLink,
    shown: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val kind =
        when {
            link.target.startsWith("tel:") -> LinkKind.Number
            link.target.startsWith("mailto:") -> LinkKind.Email
            else -> LinkKind.Web
        }
    val copied = copyValue(link, shown)
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        when (kind) {
                            LinkKind.Number -> R.string.copy_number
                            LinkKind.Email -> R.string.copy_email
                            LinkKind.Web -> R.string.copy_link
                        },
                    ),
                )
            },
            leadingIcon = { Icon(painterResource(DesignR.drawable.ic_content_copy), contentDescription = null) },
            onClick = {
                onDismiss()
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(shown, copied))) }
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        when (kind) {
                            LinkKind.Number -> R.string.call_number
                            LinkKind.Email -> R.string.send_email
                            LinkKind.Web -> R.string.open_link
                        },
                    ),
                )
            },
            leadingIcon = {
                Icon(
                    painterResource(
                        when (kind) {
                            LinkKind.Number -> DesignR.drawable.ic_call
                            LinkKind.Email -> DesignR.drawable.ic_mail
                            LinkKind.Web -> DesignR.drawable.ic_open_in_new
                        },
                    ),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismiss()
                openLink(context, link)
            },
        )
    }
}

private enum class LinkKind { Web, Email, Number }

/** Opens a link: a number in the default phone app, anything else in whatever handles it. */
private fun openLink(
    context: Context,
    link: MessageLink,
) {
    if (link.target.startsWith("tel:")) {
        dial(context, link.target)
    } else {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.target.toUri())) }
    }
}

/** What Copy puts on the clipboard: a web link whole (with https://), a number or address as written. */
private fun copyValue(
    link: MessageLink,
    shown: String,
): String = if (link.target.startsWith("tel:") || link.target.startsWith("mailto:")) shown else link.target

/** A message's text, to pick out part of it and copy (Select text in the selection bar). */
@Composable
private fun SelectTextDialog(
    text: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_text)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.select_text_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                SelectionContainer(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
