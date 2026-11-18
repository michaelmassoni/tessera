package io.github.michaelmassoni.tessera.notes.ui

import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.notes.EditorState
import io.github.michaelmassoni.tessera.notes.ListState
import io.github.michaelmassoni.tessera.notes.engine.ChecklistItem
import io.github.michaelmassoni.tessera.notes.engine.Note
import io.github.michaelmassoni.tessera.notes.engine.NoteView
import kotlinx.coroutines.flow.emptyFlow

private val sampleNotes =
    listOf(
        Note(
            "1",
            title = "Trip to the coast",
            body = "Book the cabin for the long weekend. Ask about late check-out.",
            pinned = true,
            modified = 9,
        ),
        Note(
            "2",
            title = "Groceries",
            checklist =
                listOf(
                    ChecklistItem("Oat milk"),
                    ChecklistItem("Sourdough"),
                    ChecklistItem("Tomatoes"),
                    ChecklistItem("Coffee beans", checked = true),
                ),
            labels = setOf("Home"),
            modified = 8,
        ),
        Note("3", body = "The quiet hours before anyone else is awake are the best for writing.", modified = 7),
        Note(
            "4",
            title = "Book ideas",
            body = "A lighthouse keeper who collects lost letters.\nA city that moves once a year.",
            labels = setOf("Writing"),
            modified = 6,
        ),
        Note("5", title = "Wi-Fi", body = "Guest network: Lantern", modified = 5),
    )

private object PreviewListActions : ListActions {
    override fun setView(view: NoteView) = Unit

    override fun setSearch(text: String) = Unit

    override fun open(note: Note) = Unit

    override fun newNote(checklist: Boolean) = Unit

    override fun toggleSelection(note: Note) = Unit

    override fun clearSelection() = Unit

    override fun selectAll() = Unit

    override fun pinSelection() = Unit

    override fun archiveSelection() = Unit

    override fun trashSelection() = Unit

    override fun restore(ids: Collection<String>) = Unit

    override fun undoTrash(ids: List<String>) = Unit

    override fun undoArchive(
        ids: List<String>,
        archived: Boolean,
    ) = Unit

    override fun deleteForever(ids: Collection<String>) = Unit

    override fun emptyTrash() = Unit

    override fun shareSelection() = Unit

    override fun setLabels(
        ids: Collection<String>,
        add: Set<String>,
        remove: Set<String>,
    ) = Unit

    override fun openSettings() = Unit
}

private object PreviewEditorActions : EditorActions {
    override fun close() = Unit

    override fun setTitle(title: String) = Unit

    override fun setBody(body: String) = Unit

    override fun setItems(items: List<ChecklistItem>) = Unit

    override fun setItemsStep(items: List<ChecklistItem>) = Unit

    override fun toggleChecklist() = Unit

    override fun togglePinned() = Unit

    override fun toggleArchived() = Unit

    override fun trash() = Unit

    override fun duplicate() = Unit

    override fun share(note: Note) = Unit

    override fun editLabels(note: Note) = Unit

    override fun undo() = Unit

    override fun redo() = Unit
}

private val sampleList = ListState(loaded = true, notes = sampleNotes, labels = listOf("Home", "Writing"))

@TesseraPreviews
@Composable
private fun NotesListPreview() {
    TesseraTheme(dynamicColor = false) {
        NotesApp(sampleList, null, emptyFlow(), true, PreviewListActions, PreviewEditorActions)
    }
}

@TesseraPreviews
@Composable
private fun NotesSelectionPreview() {
    TesseraTheme(dynamicColor = false) {
        NotesApp(sampleList.copy(selection = setOf("2", "3")), null, emptyFlow(), true, PreviewListActions, PreviewEditorActions)
    }
}

@TesseraPreviews
@Composable
private fun ChecklistEditorPreview() {
    TesseraTheme(dynamicColor = false) {
        NotesApp(sampleList, EditorState(sampleNotes[1], canUndo = true), emptyFlow(), true, PreviewListActions, PreviewEditorActions)
    }
}

@TesseraPreviews
@Composable
private fun EmptyNotesPreview() {
    TesseraTheme(dynamicColor = false) {
        NotesApp(ListState(loaded = true), null, emptyFlow(), true, PreviewListActions, PreviewEditorActions)
    }
}
