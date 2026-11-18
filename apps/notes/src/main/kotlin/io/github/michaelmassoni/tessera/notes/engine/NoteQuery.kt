package io.github.michaelmassoni.tessera.notes.engine

import java.text.Normalizer

/** Which notes a list shows. */
sealed interface NoteView {
    data object Notes : NoteView

    data object Archive : NoteView

    data object Trash : NoteView

    data class Label(
        val name: String,
    ) : NoteView
}

/** Filtering, searching and ordering of notes. */
object NoteQuery {
    /**
     * The notes for [view], matching [search] (if not blank), pinned first and then by most
     * recent edit. Search covers archived notes too (but never Trash, unless viewing Trash).
     */
    fun select(
        notes: Collection<Note>,
        view: NoteView,
        search: String = "",
    ): List<Note> {
        val terms = normalize(search).split(' ').filter { it.isNotEmpty() }
        return notes
            .asSequence()
            .filter { note ->
                when (view) {
                    NoteView.Trash -> note.isTrashed
                    NoteView.Archive -> !note.isTrashed && note.archived
                    is NoteView.Label -> !note.isTrashed && view.name in note.labels
                    NoteView.Notes -> !note.isTrashed && (!note.archived || terms.isNotEmpty())
                }
            }.filter { note -> terms.isEmpty() || normalize(note.plainText()).let { text -> terms.all { it in text } } }
            .sortedWith(
                compareByDescending<Note> { view != NoteView.Trash && it.pinned }
                    .thenByDescending { if (view == NoteView.Trash) it.trashedAt else it.modified },
            ).toList()
    }

    /** Every label in use, sorted for display. */
    fun labels(notes: Collection<Note>): List<String> =
        notes
            .filterNot { it.isTrashed }
            .flatMap { it.labels }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** Lower case without accents, so "Cafe" finds "Café". */
    fun normalize(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()

    private val DIACRITICS = Regex("\\p{Mn}+")
}
