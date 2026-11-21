package io.github.michaelmassoni.tessera.notes.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.michaelmassoni.tessera.notes.EditorState
import io.github.michaelmassoni.tessera.notes.R
import io.github.michaelmassoni.tessera.notes.engine.Checklist
import io.github.michaelmassoni.tessera.notes.engine.ChecklistItem
import io.github.michaelmassoni.tessera.notes.engine.DragTarget
import io.github.michaelmassoni.tessera.notes.engine.Note
import io.github.michaelmassoni.tessera.core.design.R as DesignR

/** What the editor can do; implemented over the ViewModel. */
interface EditorActions {
    fun close()

    fun setTitle(title: String)

    fun setBody(body: String)

    /** Typing in an item. */
    fun setItems(items: List<ChecklistItem>)

    /** A structural change (new item, removed item, checked, moved). */
    fun setItemsStep(items: List<ChecklistItem>)

    fun toggleChecklist()

    fun togglePinned()

    fun toggleArchived()

    fun trash()

    fun duplicate()

    fun share(note: Note)

    fun editLabels(note: Note)

    fun undo()

    fun redo()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(
    state: EditorState,
    checkedLast: Boolean,
    actions: EditorActions,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    val note = state.note
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = actions::close) {
                        Icon(
                            painterResource(if (showBack) DesignR.drawable.ic_arrow_back else DesignR.drawable.ic_close),
                            contentDescription = stringResource(if (showBack) R.string.action_back else R.string.action_close),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = actions::togglePinned) {
                        Icon(
                            painterResource(if (note.pinned) DesignR.drawable.ic_push_pin_filled else DesignR.drawable.ic_push_pin),
                            contentDescription = stringResource(if (note.pinned) R.string.action_unpin else R.string.action_pin),
                        )
                    }
                    IconButton(onClick = actions::toggleArchived) {
                        Icon(
                            painterResource(if (note.archived) DesignR.drawable.ic_unarchive else DesignR.drawable.ic_archive),
                            contentDescription = stringResource(if (note.archived) R.string.action_unarchive else R.string.action_archive),
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            val item: @Composable (Int, Int, () -> Unit) -> Unit = { text, icon, onClick ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(text)) },
                                    leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
                                    onClick = {
                                        menu = false
                                        onClick()
                                    },
                                )
                            }
                            item(
                                if (note.isChecklist) R.string.action_hide_checkboxes else R.string.action_show_checkboxes,
                                DesignR.drawable.ic_checklist,
                                actions::toggleChecklist,
                            )
                            item(R.string.action_labels, DesignR.drawable.ic_label) { actions.editLabels(note) }
                            item(R.string.action_share, DesignR.drawable.ic_share) { actions.share(note) }
                            item(R.string.action_copy, DesignR.drawable.ic_content_copy, actions::duplicate)
                            item(R.string.action_delete, DesignR.drawable.ic_delete, actions::trash)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = { EditorBottomBar(state, actions) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                PlainField(
                    value = note.title,
                    onValueChange = actions::setTitle,
                    placeholder = stringResource(R.string.title_hint),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    singleLine = false,
                )
                val items = note.checklist
                if (items != null) {
                    ChecklistEditor(items, checkedLast, actions, autoFocus = note.isBlank)
                } else {
                    BodyField(note, actions)
                }
                if (note.labels.isNotEmpty()) {
                    TextButton(onClick = { actions.editLabels(note) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(note.labels.sortedWith(String.CASE_INSENSITIVE_ORDER).joinToString("  ·  "))
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyField(
    note: Note,
    actions: EditorActions,
) {
    val focus = remember { FocusRequester() }
    // A new, empty note opens with the keyboard up.
    LaunchedEffect(note.id) { if (note.isBlank) focus.requestFocus() }
    PlainField(
        value = note.body,
        onValueChange = actions::setBody,
        placeholder = stringResource(R.string.body_hint),
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = false,
        modifier = Modifier.focusRequester(focus),
    )
}

@Composable
private fun EditorBottomBar(
    state: EditorState,
    actions: EditorActions,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions::undo, enabled = state.canUndo) {
            Icon(painterResource(DesignR.drawable.ic_undo), contentDescription = stringResource(R.string.action_undo))
        }
        IconButton(onClick = actions::redo, enabled = state.canRedo) {
            Icon(painterResource(DesignR.drawable.ic_redo), contentDescription = stringResource(R.string.action_redo))
        }
        Text(
            stringResource(
                R.string.edited,
                DateUtils.formatSameDayTime(
                    state.note.modified,
                    System.currentTimeMillis(),
                    java.text.DateFormat.MEDIUM,
                    java.text.DateFormat.SHORT,
                ),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/**
 * Checklist items: unchecked first (when [checkedLast]), then a collapsible group of checked
 * items. Enter splits an item, Backspace at the start of an item joins it to the one above.
 */
@Composable
private fun ChecklistEditor(
    items: List<ChecklistItem>,
    checkedLast: Boolean,
    actions: EditorActions,
    autoFocus: Boolean,
) {
    // Which item should take focus after a structural edit, and where its cursor goes.
    var focusIndex by remember { mutableIntStateOf(if (autoFocus) 0 else -1) }
    var focusCursor by remember { mutableIntStateOf(0) }
    var showChecked by rememberSaveable { mutableStateOf(true) }
    val order = Checklist.displayOrder(items, checkedLast)
    val unchecked = if (checkedLast) order.filter { !items[it].checked } else order
    val checked = if (checkedLast) order.filter { items[it].checked } else emptyList()

    // Dragging a row by its handle: the row follows the finger, the rows it passes slide out of
    // the way, and the move is saved (one undo step) when the finger lifts.
    var dragging by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<Int, Int>() }
    val currentItems by rememberUpdatedState(items)
    val moveLabelUp = stringResource(R.string.move_up)
    val moveLabelDown = stringResource(R.string.move_down)

    fun groupOf(index: Int) = if (index in checked) checked else unchecked

    fun drop() {
        val group = groupOf(dragging)
        val from = group.indexOf(dragging)
        if (from >= 0) {
            val to = DragTarget.position(group.map { heights[it] ?: 0 }, from, dragOffset)
            if (to != from) actions.setItemsStep(Checklist.move(currentItems, dragging, group[to]))
        }
        dragging = -1
        dragOffset = 0f
    }

    val row: @Composable (Int) -> Unit = { index ->
        val group = groupOf(index)
        val position = group.indexOf(index)
        val shift =
            if (dragging < 0 || dragging == index || dragging !in group) {
                0f
            } else {
                val from = group.indexOf(dragging)
                val to = DragTarget.position(group.map { heights[it] ?: 0 }, from, dragOffset)
                val draggedHeight = (heights[dragging] ?: 0).toFloat()
                when {
                    position in (from + 1)..to -> -draggedHeight
                    position in to until from -> draggedHeight
                    else -> 0f
                }
            }
        val handle =
            Modifier
                .pointerInput(index) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = index
                            dragOffset = 0f
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount
                        },
                        onDragEnd = { drop() },
                        onDragCancel = {
                            dragging = -1
                            dragOffset = 0f
                        },
                    )
                }.semantics {
                    customActions =
                        listOfNotNull(
                            group.getOrNull(position - 1)?.let { above ->
                                CustomAccessibilityAction(moveLabelUp) {
                                    actions.setItemsStep(Checklist.move(currentItems, index, above))
                                    true
                                }
                            },
                            group.getOrNull(position + 1)?.let { below ->
                                CustomAccessibilityAction(moveLabelDown) {
                                    actions.setItemsStep(Checklist.move(currentItems, index, below))
                                    true
                                }
                            },
                        )
                }
        ItemRow(
            modifier =
                Modifier
                    .onSizeChanged { heights[index] = it.height }
                    .zIndex(if (dragging == index) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging == index) dragOffset else shift
                        shadowElevation = if (dragging == index) 8.dp.toPx() else 0f
                    }.background(if (dragging == index) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent),
            handleModifier = handle,
            item = items[index],
            requestFocus = focusIndex == index,
            cursor = focusCursor,
            onFocused = { focusIndex = -1 },
            onTextChange = { text ->
                val newline = text.indexOf('\n')
                if (newline >= 0) {
                    // Enter: split at the newline; pasted multi-line text becomes several items.
                    val before = text.substring(0, newline)
                    val after = text.substring(newline + 1).split('\n')
                    val updated = items.toMutableList()
                    updated[index] = items[index].copy(text = before)
                    updated.addAll(index + 1, after.map { ChecklistItem(it) })
                    actions.setItemsStep(updated)
                    focusIndex = index + after.size
                    focusCursor = 0
                } else {
                    actions.setItems(items.mapIndexed { i, it -> if (i == index) it.copy(text = text) else it })
                }
            },
            onBackspaceAtStart = {
                val merged = Checklist.mergeWithPrevious(items, index)
                if (merged != null) {
                    actions.setItemsStep(merged.first)
                    focusIndex = index - 1
                    focusCursor = merged.second
                }
            },
            onToggle = { actions.setItemsStep(Checklist.toggle(items, index)) },
            onRemove = {
                if (items.size > 1) {
                    actions.setItemsStep(items.filterIndexed { i, _ -> i != index })
                } else {
                    actions.setItemsStep(listOf(ChecklistItem("")))
                }
            },
        )
    }

    Column {
        unchecked.forEach { index -> androidx.compose.runtime.key(index) { row(index) } }
        TextButton(
            onClick = {
                val updated = items + ChecklistItem("")
                actions.setItemsStep(updated)
                focusIndex = updated.lastIndex
                focusCursor = 0
            },
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Icon(painterResource(DesignR.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.add_item), modifier = Modifier.padding(start = 8.dp))
        }
        if (checked.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            TextButton(onClick = { showChecked = !showChecked }, modifier = Modifier.padding(start = 8.dp)) {
                Icon(
                    painterResource(if (showChecked) DesignR.drawable.ic_expand_less else DesignR.drawable.ic_expand_more),
                    contentDescription = null,
                )
                Text(
                    pluralStringResource(R.plurals.checked_items, checked.size, checked.size),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            AnimatedVisibility(showChecked) {
                Column { checked.forEach { index -> androidx.compose.runtime.key(index) { row(index) } } }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: ChecklistItem,
    requestFocus: Boolean,
    cursor: Int,
    onFocused: () -> Unit,
    onTextChange: (String) -> Unit,
    onBackspaceAtStart: () -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    // Keep the cursor position across recompositions while the text comes from the ViewModel.
    var field by remember { mutableStateOf(TextFieldValue(item.text, TextRange(item.text.length))) }
    if (field.text != item.text) field = TextFieldValue(item.text, TextRange(item.text.length.coerceAtMost(field.selection.start)))
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            field = field.copy(selection = TextRange(cursor.coerceIn(0, item.text.length)))
            focus.requestFocus()
            onFocused()
        }
    }
    val textStyle =
        MaterialTheme.typography.bodyLarge.copy(
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    // A plain field rather than a Material TextField: its 56dp minimum and inner padding spread
    // the list out. Each row is the checkbox's 48dp, growing only when an item wraps.
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(handleModifier.size(width = 32.dp, height = 48.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(DesignR.drawable.ic_drag_indicator),
                contentDescription = stringResource(R.string.reorder_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
        val hint = stringResource(R.string.item_hint)
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                if (it.text != item.text) onTextChange(it.text)
            },
            textStyle = textStyle,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { inner ->
                Box {
                    if (field.text.isEmpty()) Text(hint, style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    inner()
                }
            },
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 4.dp, top = 12.dp, bottom = 12.dp)
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        val atStart = field.selection.collapsed && field.selection.start == 0
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && atStart) {
                            onBackspaceAtStart()
                            true
                        } else {
                            false
                        }
                    },
        )
        if (focused) {
            IconButton(onClick = onRemove) {
                Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.remove_item))
            }
        }
    }
}

/** A Material text field without a container or indicator: the note itself is the surface. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    singleLine: Boolean,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = textStyle) },
        textStyle = textStyle,
        singleLine = singleLine,
        colors = plainFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
}

@Composable
private fun plainFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        disabledContainerColor = MaterialTheme.colorScheme.surface,
        focusedIndicatorColor = MaterialTheme.colorScheme.surface,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.surface,
    )
