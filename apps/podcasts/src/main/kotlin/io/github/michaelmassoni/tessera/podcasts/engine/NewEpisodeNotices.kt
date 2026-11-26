package io.github.michaelmassoni.tessera.podcasts.engine

/**
 * What to say about new episodes after a refresh (docs/podcasts.md, Notifications): one notice
 * per followed show, its newest episode first, with how many there are. Pure, so it's unit tested.
 */
object NewEpisodeNotices {
    data class Notice(
        val show: Show,
        /** Newest first. */
        val episodes: List<Episode>,
    ) {
        val newest: Episode get() = episodes.first()
    }

    fun from(
        added: List<Episode>,
        library: Library,
    ): List<Notice> {
        val followed = library.followed.associateBy { it.id }
        return added
            .filter { !library.state(it.id).played }
            .groupBy { it.showId }
            .mapNotNull { (showId, episodes) ->
                val show = followed[showId] ?: return@mapNotNull null
                Notice(show, episodes.sortedByDescending { it.publishedAt })
            }.sortedByDescending { it.newest.publishedAt }
    }
}
