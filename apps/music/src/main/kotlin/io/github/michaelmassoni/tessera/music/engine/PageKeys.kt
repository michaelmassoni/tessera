package io.github.michaelmassoni.tessera.music.engine

/**
 * Keys for the saved state (scroll position and so on) of each level of the library: a tab, then
 * each page opened from it. A page's key is its whole path, so the same album opened twice in one
 * stack keeps two states.
 */
object PageKeys {
    /** The key of every level, from the tab down to the page on top: the last one is the page shown. */
    fun levels(
        tab: String,
        pages: List<String>,
    ): List<String> = pages.runningFold(tab) { path, page -> "$path/$page" }

    /** The pages closed for good going from [old] to [new], whose state can go. A tab's own state stays. */
    fun closed(
        old: List<String>,
        new: List<String>,
    ): List<String> = old.drop(1).filter { it !in new }
}
