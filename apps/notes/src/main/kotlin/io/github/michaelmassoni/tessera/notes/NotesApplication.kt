package io.github.michaelmassoni.tessera.notes

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.notes.data.NoteRepository
import io.github.michaelmassoni.tessera.notes.widget.NotesWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File

/** Holds the one [NoteRepository] shared by the activities of this process. */
class NotesApplication : Application() {
    val repository: NoteRepository by lazy { NoteRepository(File(filesDir, "notes")) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Keep the widget in step with the notes: once a burst of edits settles, redraw it.
        repository.notes
            .drop(1)
            .debounce(WIDGET_DELAY_MS)
            .onEach { NotesWidget.refresh(this) }
            .launchIn(scope)
    }

    private companion object {
        const val WIDGET_DELAY_MS = 1_000L
    }
}

val Context.notes: NoteRepository get() = (applicationContext as NotesApplication).repository

/** "Move checked items to the bottom" (on by default). */
var Context.checkedLast: Boolean
    get() = getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("checked_last", true)
    set(value) = getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("checked_last", value) }
