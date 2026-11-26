package io.github.michaelmassoni.tessera.feeds.engine

/** What swiping an article row does (Settings > Reading, one for each direction). */
enum class SwipeAction {
    ToggleRead,
    ToggleStar,
    None,
    ;

    companion object {
        /** The action stored as [name], or [default] when it's missing or unknown. */
        fun of(
            name: String?,
            default: SwipeAction,
        ): SwipeAction = entries.firstOrNull { it.name == name } ?: default
    }
}
