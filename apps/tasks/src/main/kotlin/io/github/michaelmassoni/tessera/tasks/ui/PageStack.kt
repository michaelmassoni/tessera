package io.github.michaelmassoni.tessera.tasks.ui

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

/** The key of the editor of task [id], or of the list when no task is open. */
internal fun taskPageKey(id: String?) = if (id == null) LIST_PAGE else "task:$id"

/**
 * The pages open over the list when task [open] is: the editors of its parents (back from a
 * subtask goes to its parent), then its own.
 */
internal fun taskPages(
    open: String?,
    parentOf: (String) -> String?,
): List<String> {
    // A broken parent loop must not hang the UI: tasks nest only a few levels deep.
    val chain = generateSequence(open, parentOf).take(MAX_DEPTH).toList().asReversed()
    return listOf(LIST_PAGE) + chain.map(::taskPageKey)
}

private const val MAX_DEPTH = 16
