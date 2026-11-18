package io.github.michaelmassoni.tessera.notes

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.notes.data.NoteRepository
import java.io.File

/** Holds the one [NoteRepository] shared by the activities of this process. */
class NotesApplication : Application() {
    val repository: NoteRepository by lazy { NoteRepository(File(filesDir, "notes")) }
}

val Context.notes: NoteRepository get() = (applicationContext as NotesApplication).repository

/** "Move checked items to the bottom" (on by default). */
var Context.checkedLast: Boolean
    get() = getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("checked_last", true)
    set(value) = getSharedPreferences("settings", Context.MODE_PRIVATE).edit { putBoolean("checked_last", value) }
