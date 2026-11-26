package io.github.michaelmassoni.tessera.podcasts.engine

/**
 * Podcasts as Android Auto (and other media browsers) sees them: Up Next, New episodes, the
 * followed shows and Downloaded at the top, shows down to their episodes. Every episode, wherever
 * it's listed, has the id `ep/<episode id>`, and picking one plays it now (it moves to the front of
 * Up Next, the rest stays queued), as tapping play does in the app. Pure, so it's unit tested.
 */
class BrowseTree(
    private val library: Library,
    private val episodes: Map<String, List<Episode>>,
    private val now: Long,
) {
    /** One entry in a browse list: a section or show that opens, or an episode that plays. */
    data class Node(
        val id: String,
        val title: String,
        val subtitle: String = "",
        val episode: Episode? = null,
        val show: Show? = null,
        /** How much of the episode was heard, 0 to 1 (1 when marked played). */
        val progress: Float = 0f,
        /** Shown as a grid of covers (the shows) rather than a list. */
        val grid: Boolean = false,
    ) {
        val playable get() = episode != null
    }

    private val byId = episodes.values.flatten().associateBy { it.id }

    fun root(): List<Node> =
        listOf(
            Node(UP_NEXT, "Up Next"),
            Node(NEW, "New episodes"),
            Node(SHOWS, "Shows", grid = true),
            Node(DOWNLOADS, "Downloaded"),
        )

    /** The entries under [id], or null for an id that isn't a list. */
    fun children(id: String): List<Node>? =
        when {
            id == ROOT -> root()
            id == UP_NEXT -> library.upNext.mapNotNull(byId::get).map { episodeNode(it) }
            id == NEW -> Episodes.new(library, episodes, now).map { episodeNode(it) }
            id == SHOWS -> library.followed.sortedByDescending { it.latestAt }.map(::showNode)
            id == DOWNLOADS -> downloaded().map { episodeNode(it) }
            id.startsWith(SHOW) -> showEpisodes(id.removePrefix(SHOW))?.map { episodeNode(it, withShow = false) }
            else -> null
        }

    /**
     * Up Next once [picked] (a browse id or an episode id) plays now, or null when it isn't an
     * episode this library knows.
     */
    fun playNow(picked: String): List<String>? {
        val id = episodeId(picked)
        if (id !in byId) return null
        return UpNext.playNow(library.upNext, id)
    }

    /**
     * What "play [query]" means, as Up Next: an empty query resumes Up Next; a show's name plays
     * its newest unheard episode (or its newest); otherwise the first episode whose title matches.
     */
    fun search(query: String): List<String>? {
        val q = query.trim()
        if (q.isEmpty()) return library.upNext.takeIf { it.isNotEmpty() }
        val show = matchingShows(q).firstOrNull()
        val episode =
            if (show != null) {
                val all = showEpisodes(show.id).orEmpty()
                all.firstOrNull { !library.state(it.id).played } ?: all.firstOrNull()
            } else {
                matchingEpisodes(q).firstOrNull()
            }
        return episode?.let { UpNext.playNow(library.upNext, it.id) }
    }

    /** Search results for a browser: matching shows, then matching episodes. */
    fun searchNodes(query: String): List<Node> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return matchingShows(q).map(::showNode) + matchingEpisodes(q).take(MAX_RESULTS).map { episodeNode(it) }
    }

    // Every show in the library (followed first): a kept show's episodes can be in Up Next.
    private val searched = library.shows.sortedByDescending { it.followed }

    private fun matchingShows(q: String) = searched.filter { it.title.contains(q, ignoreCase = true) }

    private fun matchingEpisodes(q: String) =
        searched
            .flatMap { episodes[it.id].orEmpty() }
            .filter { it.title.contains(q, ignoreCase = true) }
            .sortedByDescending { it.publishedAt }

    private fun showEpisodes(showId: String): List<Episode>? {
        library.show(showId) ?: return null
        return episodes[showId].orEmpty().sortedByDescending { it.publishedAt }
    }

    private fun downloaded(): List<Episode> =
        library.states
            .filterValues { it.download != null }
            .keys
            .mapNotNull(byId::get)
            .sortedByDescending { it.publishedAt }

    private fun showNode(show: Show) = Node(SHOW + show.id, show.title, show.author, show = show, grid = true)

    private fun episodeNode(
        episode: Episode,
        withShow: Boolean = true,
    ): Node {
        val state = library.state(episode.id)
        val duration = state.durationMs.takeIf { it > 0 } ?: episode.durationMs
        val progress =
            when {
                state.played -> 1f
                duration > 0 -> (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
                else -> 0f
            }
        val show = library.show(episode.showId)
        return Node(
            id = EPISODE + episode.id,
            title = episode.title,
            subtitle = if (withShow) show?.title.orEmpty() else "",
            episode = episode,
            show = show,
            progress = progress,
        )
    }

    companion object {
        const val ROOT = "root"
        const val UP_NEXT = "upnext"
        const val NEW = "new"
        const val SHOWS = "shows"
        const val DOWNLOADS = "downloads"
        const val SHOW = "show/"
        const val EPISODE = "ep/"
        private const val MAX_RESULTS = 50

        /** The episode id in a browse id (the app's own controllers send bare episode ids). */
        fun episodeId(mediaId: String) = mediaId.removePrefix(EPISODE)
    }
}
