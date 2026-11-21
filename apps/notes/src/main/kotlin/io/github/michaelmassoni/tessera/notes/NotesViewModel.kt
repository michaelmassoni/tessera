package io.github.michaelmassoni.tessera.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.michaelmassoni.tessera.notes.data.NoteRepository
import io.github.michaelmassoni.tessera.notes.engine.Checklist
import io.github.michaelmassoni.tessera.notes.engine.ChecklistItem
import io.github.michaelmassoni.tessera.notes.engine.Note
import io.github.michaelmassoni.tessera.notes.engine.NoteQuery
import io.github.michaelmassoni.tessera.notes.engine.NoteView
import io.github.michaelmassoni.tessera.notes.engine.UndoStack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ListState(
    val loaded: Boolean = false,
    val view: NoteView = NoteView.Notes,
    val search: String = "",
    val notes: List<Note> = emptyList(),
    val labels: List<String> = emptyList(),
    val selection: Set<String> = emptySet(),
)

data class EditorState(
    val note: Note,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

sealed interface NotesEvent {
    /** Notes moved to Trash; [ids] can be restored with Undo. */
    data class Trashed(
        val ids: List<String>,
    ) : NotesEvent

    data class Archived(
        val ids: List<String>,
        val archived: Boolean,
    ) : NotesEvent

    data object EmptyDiscarded : NotesEvent
}

/** What edits are coalesced together for undo. */
private enum class EditKind { Title, Body, Item }

/**
 * The notes list and the open note. All disk access runs on [io]. The open note is saved shortly
 * after each change (debounced) and immediately when it is closed.
 */
class NotesViewModel(
    private val repository: NoteRepository,
    private val io: CoroutineDispatcher,
    /** Filtering, sorting and labelling the list; off the main thread, so long lists don't jank. */
    private val compute: CoroutineDispatcher = io,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val view = MutableStateFlow<NoteView>(NoteView.Notes)
    private val search = MutableStateFlow("")
    private val selection = MutableStateFlow<Set<String>>(emptySet())

    val list: StateFlow<ListState> =
        combine(repository.notes, repository.loaded, view, search, selection) { notes, loaded, v, s, sel ->
            ListState(
                loaded = loaded,
                view = v,
                search = s,
                notes = NoteQuery.select(notes.values, v, s),
                labels = NoteQuery.labels(notes.values),
                selection = sel.filter { it in notes }.toSet(),
            )
        }.flowOn(compute)
            .stateIn(viewModelScope, SharingStarted.Eagerly, ListState())

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    private var undo: UndoStack<Note>? = null
    private var pendingSave: Job? = null

    private val eventChannel = Channel<NotesEvent>(Channel.BUFFERED)
    val events: Flow<NotesEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch(io) {
            repository.load()
            repository.purgeTrash()
        }
    }

    // ------------------------------------------------------------------ list

    fun setView(v: NoteView) {
        view.value = v
        selection.value = emptySet()
    }

    fun setSearch(text: String) {
        search.value = text
    }

    fun toggleSelection(id: String) = selection.update { if (id in it) it - id else it + id }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun selectAll() {
        selection.value =
            list.value.notes
                .map { it.id }
                .toSet()
    }

    fun pinSelection() {
        val ids = selection.value
        val pin = ids.any { repository.get(it)?.pinned == false }
        background { repository.update(ids) { it.copy(pinned = pin) } }
        clearSelection()
    }

    fun archiveSelection() = setArchived(selection.value.toList(), !list.value.view.isArchive())

    fun setArchived(
        ids: List<String>,
        archived: Boolean,
        announce: Boolean = true,
    ) {
        background { repository.update(ids) { it.copy(archived = archived, pinned = if (archived) false else it.pinned) } }
        clearSelection()
        if (announce) eventChannel.trySend(NotesEvent.Archived(ids, archived))
    }

    fun trashSelection() = trash(selection.value.toList())

    fun trash(ids: List<String>) {
        background { repository.trash(ids) }
        clearSelection()
        eventChannel.trySend(NotesEvent.Trashed(ids))
    }

    fun restore(ids: Collection<String>) {
        background { repository.restore(ids) }
        clearSelection()
    }

    fun deleteForever(ids: Collection<String>) {
        background { ids.forEach(repository::delete) }
        clearSelection()
    }

    fun emptyTrash() = background { repository.emptyTrash() }

    fun setLabels(
        ids: Collection<String>,
        add: Set<String>,
        remove: Set<String>,
    ) {
        background { repository.update(ids) { it.copy(labels = it.labels + add - remove) } }
        _editor.value?.let { e -> if (e.note.id in ids) edit(null) { it.copy(labels = it.labels + add - remove) } }
        clearSelection()
    }

    // ------------------------------------------------------------------ editor

    /** Opens a new note. [text] and [title] pre-fill it (from another app's share). */
    fun newNote(
        checklist: Boolean,
        title: String = "",
        text: String = "",
    ) {
        closeNote()
        val base = repository.create(checklist)
        val note =
            when {
                text.isEmpty() -> base
                checklist -> base.copy(checklist = Checklist.fromText(text))
                else -> base.copy(body = text)
            }.copy(title = title, labels = (list.value.view as? NoteView.Label)?.let { setOf(it.name) }.orEmpty())
        open(note)
        // Text shared from another app is kept even if the process dies before the note is closed.
        if (!note.isBlank) scheduleSave()
    }

    fun openNote(id: String) {
        if (_editor.value?.note?.id == id) return
        closeNote()
        repository.get(id)?.let(::open)
    }

    /** Opens [id] once the notes have loaded (from the widget, possibly at a cold start). */
    fun openNoteWhenLoaded(id: String) {
        viewModelScope.launch {
            repository.loaded.first { it }
            openNote(id)
        }
    }

    private fun open(note: Note) {
        undo = UndoStack(note)
        _editor.value = EditorState(note)
    }

    /** Saves the open note now (the app is going to the background). */
    fun flush() {
        pendingSave?.cancel()
        val note = _editor.value?.note?.takeUnless { it.isBlank } ?: return
        if (repository.get(note.id) != note) background { repository.save(note, touch = false) }
    }

    /** Saves and closes the open note. A note left empty is discarded. */
    fun closeNote() {
        val state = _editor.value ?: return
        pendingSave?.cancel()
        _editor.value = null
        undo = null
        val note = state.note
        val stored = repository.get(note.id)
        if (note.isBlank) {
            if (stored != null) background { repository.delete(note.id) }
            if (note.created != note.modified || stored != null) eventChannel.trySend(NotesEvent.EmptyDiscarded)
            return
        }
        if (stored != note) background { repository.save(note, touch = false) }
    }

    fun setTitle(title: String) = edit(EditKind.Title) { it.copy(title = title) }

    fun setBody(body: String) = edit(EditKind.Body) { it.copy(body = body) }

    fun setItems(items: List<ChecklistItem>) = edit(EditKind.Item) { it.copy(checklist = items) }

    /** Item edits that are steps of their own (enter, delete, check, move) rather than typing. */
    fun setItemsStep(items: List<ChecklistItem>) = edit(null) { it.copy(checklist = items) }

    fun toggleChecklist() =
        edit(null) { note ->
            if (note.isChecklist) {
                note.copy(body = Checklist.toText(note.checklist.orEmpty()), checklist = null)
            } else {
                note.copy(checklist = Checklist.fromText(note.body), body = "")
            }
        }

    fun togglePinned() = edit(null) { it.copy(pinned = !it.pinned, archived = false) }

    fun toggleArchived() {
        val note = _editor.value?.note ?: return
        edit(null) { it.copy(archived = !it.archived, pinned = false) }
        val archived = !note.archived
        closeNote()
        eventChannel.trySend(NotesEvent.Archived(listOf(note.id), archived))
    }

    fun trashOpenNote() {
        val note = _editor.value?.note ?: return
        if (note.isBlank) {
            closeNote()
            return
        }
        closeNote()
        trash(listOf(note.id))
    }

    fun duplicateOpenNote() {
        val note = _editor.value?.note ?: return
        closeNote()
        val t = now()
        val copy = note.copy(id = repository.newId(), created = t, modified = t, pinned = false)
        background { repository.save(copy, touch = false) }
        open(copy)
    }

    fun undo() = undo?.undo()?.let { applyHistory(it) }

    fun redo() = undo?.redo()?.let { applyHistory(it) }

    private fun applyHistory(note: Note) {
        _editor.value = EditorState(note, undo?.canUndo == true, undo?.canRedo == true)
        scheduleSave()
    }

    private fun edit(
        kind: EditKind?,
        transform: (Note) -> Note,
    ) {
        val current = _editor.value ?: return
        val next = transform(current.note).copy(modified = now())
        if (next == current.note) return
        undo?.push(next, now(), kind)
        _editor.value = EditorState(next, undo?.canUndo == true, undo?.canRedo == true)
        scheduleSave()
    }

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave =
            viewModelScope.launch {
                delay(SAVE_DELAY_MILLIS)
                val note = _editor.value?.note ?: return@launch
                if (!note.isBlank) withContext(io) { repository.save(note, touch = false) }
            }
    }

    private fun background(block: () -> Unit) {
        viewModelScope.launch(io) { block() }
    }

    override fun onCleared() {
        // Last chance to keep an unsaved edit (the process is going away soon after this).
        _editor.value
            ?.note
            ?.takeUnless { it.isBlank }
            ?.let { repository.save(it, touch = false) }
    }

    private fun NoteView.isArchive() = this == NoteView.Archive

    private companion object {
        const val SAVE_DELAY_MILLIS = 400L
    }
}
