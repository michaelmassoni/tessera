package io.github.michaelmassoni.tessera.contacts.ui

/** The key of the list's own saved state (its scroll position and the like), never forgotten. */
internal const val LIST_PAGE = "list"

/**
 * Tracks which pages are open, each by a stable key into a SaveableStateHolder, and forgets the
 * saved state of a page once it is closed for good, so reopening it later starts at the top while
 * the list underneath keeps its place.
 */
internal class PageStack(
    private val forget: (String) -> Unit,
) {
    private var shown = emptyList<String>()

    /** [stack] is what is open now: the list first, the page on top last. */
    fun show(stack: List<String>) {
        shown.filter { it !in stack && it != LIST_PAGE }.forEach(forget)
        shown = stack
    }
}

/**
 * The pages open over the list, bottom first: contact [detailId]'s details, then the editor when
 * [editing] (of contact [editingId], or of a new one when that is null).
 */
internal fun contactPages(
    detailId: Long?,
    editing: Boolean,
    editingId: Long?,
): List<String> =
    listOfNotNull(
        LIST_PAGE,
        detailId?.let { "contact:$it" },
        if (editing) "edit:${editingId ?: "new"}" else null,
    )
