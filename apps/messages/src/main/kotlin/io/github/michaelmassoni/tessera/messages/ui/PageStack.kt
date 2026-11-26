package io.github.michaelmassoni.tessera.messages.ui

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

/** The key of the new-message page. */
internal const val COMPOSE_PAGE = "compose"

/**
 * The key of the conversation with [addresses]. Not its thread id: a new conversation only gets
 * one once its first message is sent, and the page must not start over then.
 */
internal fun threadPageKey(addresses: List<String>) = "thread:" + addresses.joinToString(",")

/** The pages open over the list: the conversation with [addresses], or else the new-message page. */
internal fun messagePages(
    addresses: List<String>?,
    composing: Boolean,
): List<String> =
    when {
        addresses != null -> listOf(LIST_PAGE, threadPageKey(addresses))
        composing -> listOf(LIST_PAGE, COMPOSE_PAGE)
        else -> listOf(LIST_PAGE)
    }
