package io.github.michaelmassoni.tessera.notes.ui

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

/** The key of the page showing note [noteId], or of the list when no note is open. */
internal fun notePageKey(noteId: String?) = if (noteId == null) LIST_PAGE else "note:$noteId"
