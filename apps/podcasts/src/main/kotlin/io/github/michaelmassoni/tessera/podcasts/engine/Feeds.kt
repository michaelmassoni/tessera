package io.github.michaelmassoni.tessera.podcasts.engine

/** A show's episodes and show notes, as kept in its cache files. */
data class FeedContent(
    val episodes: List<Episode>,
    /** By episode id. */
    val notes: Map<String, String>,
)

object Feeds {
    /** [show] (new or known) updated from a fetched [feed]. */
    fun show(
        show: Show,
        feed: ParsedFeed,
        content: FeedContent,
        now: Long,
    ): Show =
        show.copy(
            title = feed.title.ifBlank { show.title },
            author = feed.author.ifBlank { show.author },
            description = PlainText.of(feed.description, maxLength = 4000).ifBlank { show.description },
            artworkUrl = feed.artworkUrl ?: show.artworkUrl,
            link = feed.link ?: show.link,
            refreshedAt = now,
            latestAt = content.episodes.maxOfOrNull { it.publishedAt } ?: show.latestAt,
        )

    /**
     * The episodes of [feed], newest first. Episodes that left the feed stay if [keep] holds them
     * (downloaded or in Up Next); a guid seen twice keeps its first item.
     */
    fun content(
        showId: String,
        feed: ParsedFeed,
        old: List<Episode>,
        keep: Set<String>,
    ): FeedContent {
        val seen = HashSet<String>()
        val notes = HashMap<String, String>()
        val episodes =
            feed.items.mapNotNull { item ->
                val id = Ids.episode(showId, item.guid)
                if (!seen.add(id)) return@mapNotNull null
                if (item.notes.isNotBlank()) notes[id] = item.notes
                Episode(
                    id = id,
                    showId = showId,
                    title = item.title.ifBlank { PlainText.of(item.notes, 80) },
                    summary = PlainText.of(item.notes),
                    publishedAt = item.publishedAt,
                    durationMs = item.durationMs,
                    audioUrl = item.audioUrl,
                    audioType = item.audioType,
                    audioBytes = item.audioBytes,
                    artworkUrl = item.artworkUrl,
                    link = item.link,
                    chaptersUrl = item.chaptersUrl,
                )
            }
        val kept = old.filter { it.id in keep && it.id !in seen }
        return FeedContent((episodes + kept).sortedByDescending { it.publishedAt }, notes)
    }

    /** Episodes in [content] that weren't in [old]: new since the last refresh. */
    fun added(
        old: List<Episode>,
        content: FeedContent,
    ): List<Episode> {
        val known = old.mapTo(HashSet()) { it.id }
        return content.episodes.filter { it.id !in known }
    }
}

/** Which episodes to show and act on. */
object Episodes {
    const val NEW_DAYS = 14L
    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /** An episode stopped this close to its end counts as played (outros, ads). */
    const val END_MARGIN_MS = 30_000L

    /** Unplayed episodes of followed shows from the last [NEW_DAYS] days, newest first. */
    fun new(
        library: Library,
        episodes: Map<String, List<Episode>>,
        now: Long,
    ): List<Episode> {
        val since = now - NEW_DAYS * DAY_MS
        return library.followed
            .flatMap { show -> episodes[show.id].orEmpty().filter { it.publishedAt >= since } }
            .filterNot { library.state(it.id).played }
            .sortedByDescending { it.publishedAt }
    }

    fun nearEnd(
        positionMs: Long,
        durationMs: Long,
    ) = durationMs > 0 && positionMs >= durationMs - END_MARGIN_MS

    /** Time left in ms, or the whole duration when not started; 0 when unknown. */
    fun remaining(
        episode: Episode,
        state: EpisodeState,
    ): Long {
        val duration = state.durationMs.takeIf { it > 0 } ?: episode.durationMs
        return (duration - state.positionMs).coerceAtLeast(0)
    }

    /** 0..1, how far through; 0 when not started or unknown. */
    fun progress(
        episode: Episode,
        state: EpisodeState,
    ): Float {
        val duration = state.durationMs.takeIf { it > 0 } ?: episode.durationMs
        if (duration <= 0 || state.positionMs <= 0) return 0f
        return (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    }
}
