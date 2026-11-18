package io.github.michaelmassoni.tessera.notes.data

import io.github.michaelmassoni.tessera.notes.engine.ChecklistItem
import io.github.michaelmassoni.tessera.notes.engine.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * All notes, kept in memory and stored one JSON file per note in [dir]. Writes go to a temporary
 * file that is then renamed over the old one, so a crash never leaves a half-written note.
 * Call [load] once, off the main thread, before anything else. Thread-safe.
 */
class NoteRepository(
    private val dir: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    private val _notes = MutableStateFlow<Map<String, Note>>(emptyMap())
    val notes: StateFlow<Map<String, Note>> = _notes.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    @Synchronized
    fun load() {
        if (_loaded.value) return
        dir.mkdirs()
        val loaded =
            dir
                .listFiles { f -> f.name.endsWith(".json") }
                .orEmpty()
                .mapNotNull { file ->
                    try {
                        json.decodeFromString(Note.serializer(), file.readText())
                    } catch (_: SerializationException) {
                        null // A corrupt file is skipped rather than losing every note.
                    } catch (_: IOException) {
                        null
                    }
                }.associateBy { it.id }
        _notes.value = loaded
        _loaded.value = true
    }

    fun get(id: String): Note? = _notes.value[id]

    fun newId(): String = UUID.randomUUID().toString()

    /** A new, unsaved note. It is only stored once [save] is called with content. */
    fun create(checklist: Boolean = false): Note {
        val t = now()
        return Note(id = newId(), checklist = if (checklist) listOf(ChecklistItem("")) else null, created = t, modified = t)
    }

    /** Stores [note], stamping its modified time unless [touch] is false. Blank notes are deleted instead. */
    @Synchronized
    fun save(
        note: Note,
        touch: Boolean = true,
    ): Note? {
        if (note.isBlank && !note.isTrashed) {
            delete(note.id)
            return null
        }
        val stored = if (touch) note.copy(modified = now()) else note
        write(stored)
        _notes.update { it + (stored.id to stored) }
        return stored
    }

    @Synchronized
    fun saveAll(notes: Collection<Note>) {
        notes.forEach { write(it) }
        _notes.update { current -> current + notes.associateBy { it.id } }
    }

    @Synchronized
    fun delete(id: String) {
        File(dir, "$id.json").delete()
        _notes.update { it - id }
    }

    fun trash(ids: Collection<String>) = update(ids) { it.copy(trashedAt = now(), pinned = false) }

    fun restore(ids: Collection<String>) = update(ids) { it.copy(trashedAt = null) }

    fun update(
        ids: Collection<String>,
        transform: (Note) -> Note,
    ) {
        val changed = ids.mapNotNull { get(it) }.map(transform)
        saveAll(changed)
    }

    /** Permanently removes notes that have been in Trash longer than [days] days. */
    @Synchronized
    fun purgeTrash(days: Int = TRASH_DAYS) {
        val cutoff = now() - days * DAY_MILLIS
        _notes.value.values
            .filter { (it.trashedAt ?: Long.MAX_VALUE) < cutoff }
            .forEach { delete(it.id) }
    }

    @Synchronized
    fun emptyTrash() {
        _notes.value.values
            .filter { it.isTrashed }
            .forEach { delete(it.id) }
    }

    private fun write(note: Note) {
        dir.mkdirs()
        val target = File(dir, "${note.id}.json")
        val temp = File(dir, "${note.id}.json.tmp")
        temp.writeText(json.encodeToString(Note.serializer(), note))
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Couldn't save note ${note.id}")
        }
    }

    companion object {
        const val TRASH_DAYS = 7
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
