package io.github.michaelmassoni.tessera.notes.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteQueryTest {
    private val shopping =
        Note("1", title = "Shopping", checklist = listOf(ChecklistItem("Café beans")), modified = 10, labels = setOf("home"))
    private val ideas = Note("2", title = "Ideas", body = "A note-taking app", modified = 30, pinned = true)
    private val old = Note("3", title = "Old", body = "archived thing", modified = 50, archived = true, labels = setOf("home"))
    private val gone = Note("4", title = "Gone", body = "trash", modified = 60, trashedAt = 100)
    private val recent = Note("5", body = "most recent", modified = 40)
    private val all = listOf(shopping, ideas, old, gone, recent)

    private fun ids(
        view: NoteView,
        search: String = "",
    ) = NoteQuery.select(all, view, search).map { it.id }

    @Test fun `main list shows pinned first then newest, without archive and trash`() {
        assertEquals(listOf("2", "5", "1"), ids(NoteView.Notes))
    }

    @Test fun `archive, trash and label views`() {
        assertEquals(listOf("3"), ids(NoteView.Archive))
        assertEquals(listOf("4"), ids(NoteView.Trash))
        assertEquals(listOf("3", "1"), ids(NoteView.Label("home")))
    }

    @Test fun `search ignores case and accents, needs every word, and includes archived notes`() {
        assertEquals(listOf("1"), ids(NoteView.Notes, "cafe"))
        assertEquals(listOf("1"), ids(NoteView.Notes, "SHOPPING beans"))
        assertEquals(emptyList<String>(), ids(NoteView.Notes, "shopping ideas"))
        assertEquals(listOf("3"), ids(NoteView.Notes, "archived"))
        assertEquals(emptyList<String>(), ids(NoteView.Notes, "trash"))
    }

    @Test fun `labels are distinct, sorted, and ignore trashed notes`() {
        val notes = all + Note("6", labels = setOf("Work", "apple")) + Note("7", labels = setOf("zzz"), trashedAt = 1)
        assertEquals(listOf("apple", "home", "Work"), NoteQuery.labels(notes))
    }
}
