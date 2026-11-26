package io.github.michaelmassoni.tessera.notes.engine

import kotlinx.serialization.Serializable

@Serializable
data class ChecklistItem(
    val text: String,
    val checked: Boolean = false,
)

/** A note's colour: a hue tinted onto the theme's surface (docs/notes.md, Colours). */
@Serializable
enum class NoteColor { Red, Orange, Yellow, Green, Teal, Blue, Purple, Pink, Brown, Grey }

/**
 * One note. A note is either text ([checklist] == null, content in [body]) or a checklist
 * ([checklist] != null; [body] is unused). Times are epoch milliseconds.
 */
@Serializable
data class Note(
    val id: String,
    val title: String = "",
    val body: String = "",
    val checklist: List<ChecklistItem>? = null,
    val labels: Set<String> = emptySet(),
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val trashedAt: Long? = null,
    val created: Long = 0,
    val modified: Long = 0,
    /** The card's colour; null for the default. */
    val color: NoteColor? = null,
    /** When to remind (epoch ms); null for none. Kept after it fires, shown as past. */
    val reminderAt: Long? = null,
) {
    val isChecklist: Boolean get() = checklist != null
    val isTrashed: Boolean get() = trashedAt != null

    /** Nothing worth keeping: an empty new note is discarded instead of saved. */
    val isBlank: Boolean
        get() = title.isBlank() && body.isBlank() && checklist.orEmpty().all { it.text.isBlank() }

    /** The note as plain text, for sharing and search. Checklists use "☐"/"☑" markers. */
    fun plainText(): String {
        val content =
            checklist?.joinToString("\n") { (if (it.checked) "☑ " else "☐ ") + it.text } ?: body
        return listOf(title, content).filter { it.isNotBlank() }.joinToString("\n\n")
    }
}
