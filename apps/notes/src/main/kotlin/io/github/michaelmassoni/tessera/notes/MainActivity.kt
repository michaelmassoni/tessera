package io.github.michaelmassoni.tessera.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.notes.engine.ChecklistItem
import io.github.michaelmassoni.tessera.notes.engine.Note
import io.github.michaelmassoni.tessera.notes.engine.NoteColor
import io.github.michaelmassoni.tessera.notes.engine.NoteView
import io.github.michaelmassoni.tessera.notes.ui.EditorActions
import io.github.michaelmassoni.tessera.notes.ui.ListActions
import io.github.michaelmassoni.tessera.notes.ui.NotesApp
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    private val viewModel: NotesViewModel by viewModels {
        viewModelFactory { initializer { NotesViewModel(notes, Dispatchers.IO, Dispatchers.Default) } }
    }
    private var checkedLastPref by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val listActions = ListActionsImpl()
        val editorActions = EditorActionsImpl()
        setTesseraContent {
            SetupGate(notesSetup) {
                val list by viewModel.list.collectAsStateWithLifecycle()
                val editor by viewModel.editor.collectAsStateWithLifecycle()
                NotesApp(list, editor, viewModel.events, checkedLastPref, listActions, editorActions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkedLastPref = checkedLast
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app saves the open note right away instead of waiting for the debounce.
        viewModel.flush()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_NEW_NOTE -> {
                viewModel.newNote(checklist = false)
            }

            ACTION_NEW_CHECKLIST -> {
                viewModel.newNote(checklist = true)
            }

            ACTION_OPEN_NOTE -> {
                intent.getStringExtra(EXTRA_NOTE)?.let(viewModel::openNoteWhenLoaded)
            }

            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                val title = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE).orEmpty()
                if (text.isNotBlank() || title.isNotBlank()) viewModel.newNote(checklist = false, title = title, text = text)
            }
        }
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, getString(R.string.action_share)))
    }

    private inner class ListActionsImpl : ListActions {
        override fun setView(view: NoteView) = viewModel.setView(view)

        override fun setSearch(text: String) = viewModel.setSearch(text)

        override fun open(note: Note) = viewModel.openNote(note.id)

        override fun newNote(checklist: Boolean) = viewModel.newNote(checklist)

        override fun toggleSelection(note: Note) = viewModel.toggleSelection(note.id)

        override fun clearSelection() = viewModel.clearSelection()

        override fun selectAll() = viewModel.selectAll()

        override fun pinSelection() = viewModel.pinSelection()

        override fun archiveSelection() = viewModel.archiveSelection()

        override fun trashSelection() = viewModel.trashSelection()

        override fun restore(ids: Collection<String>) = viewModel.restore(ids)

        override fun undoTrash(ids: List<String>) = viewModel.restore(ids)

        override fun undoArchive(
            ids: List<String>,
            archived: Boolean,
        ) = viewModel.setArchived(ids, !archived, announce = false)

        override fun deleteForever(ids: Collection<String>) = viewModel.deleteForever(ids)

        override fun emptyTrash() = viewModel.emptyTrash()

        override fun shareSelection() {
            val list = viewModel.list.value
            share(list.notes.filter { it.id in list.selection }.joinToString("\n\n———\n\n") { it.plainText() })
            viewModel.clearSelection()
        }

        override fun setLabels(
            ids: Collection<String>,
            add: Set<String>,
            remove: Set<String>,
        ) = viewModel.setLabels(ids, add, remove)

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
    }

    private inner class EditorActionsImpl : EditorActions {
        override fun close() = viewModel.closeNote()

        override fun setTitle(title: String) = viewModel.setTitle(title)

        override fun setBody(body: String) = viewModel.setBody(body)

        override fun setItems(items: List<ChecklistItem>) = viewModel.setItems(items)

        override fun setItemsStep(items: List<ChecklistItem>) = viewModel.setItemsStep(items)

        override fun toggleChecklist() = viewModel.toggleChecklist()

        override fun togglePinned() = viewModel.togglePinned()

        override fun toggleArchived() = viewModel.toggleArchived()

        override fun trash() = viewModel.trashOpenNote()

        override fun duplicate() = viewModel.duplicateOpenNote()

        override fun share(note: Note) = share(note.plainText())

        override fun editLabels(note: Note) = Unit // Handled by NotesApp (it owns the dialog).

        override fun undo() {
            viewModel.undo()
        }

        override fun redo() {
            viewModel.redo()
        }

        override fun setColor(color: NoteColor?) = viewModel.setColor(color)

        override fun setReminder(at: Long?) = viewModel.setReminder(at)
    }

    companion object {
        const val ACTION_NEW_NOTE = "io.github.michaelmassoni.tessera.notes.NEW_NOTE"
        const val ACTION_NEW_CHECKLIST = "io.github.michaelmassoni.tessera.notes.NEW_CHECKLIST"
        const val ACTION_OPEN_NOTE = "io.github.michaelmassoni.tessera.notes.OPEN_NOTE"
        const val EXTRA_NOTE = "note"
    }
}
