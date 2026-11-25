package io.github.michaelmassoni.tessera.feeds.engine

import kotlinx.serialization.Serializable

/** What the article list shows, chosen in the drawer. */
@Serializable
sealed interface Selection {
    @Serializable
    data object Unread : Selection

    @Serializable
    data object Starred : Selection

    @Serializable
    data object All : Selection

    @Serializable
    data class InFolder(
        val folderId: String,
    ) : Selection

    @Serializable
    data class OfFeed(
        val feedId: String,
    ) : Selection
}

/** Lists, counts and changes over [FeedData]. Pure: unit tested. */
object Library {
    /** The articles [selection] shows, newest first, optionally matching [query]. */
    fun articles(
        data: FeedData,
        selection: Selection,
        query: String = "",
        // Shown in Unread although read (read while the list was open, so they don't jump away).
        also: Set<String> = emptySet(),
    ): List<Article> {
        val feedsOfFolder =
            (selection as? Selection.InFolder)?.let { s ->
                data.feeds
                    .filter { it.folderId == s.folderId }
                    .map { it.id }
                    .toSet()
            }
        val words =
            query
                .trim()
                .lowercase()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }
        return data.articles
            .filter { a ->
                when (selection) {
                    Selection.Unread -> a.unread || a.id in also
                    Selection.Starred -> a.starred
                    Selection.All -> true
                    is Selection.InFolder -> a.feedId in feedsOfFolder.orEmpty()
                    is Selection.OfFeed -> a.feedId == selection.feedId
                }
            }.filter { a ->
                words.isEmpty() ||
                    (a.title + " " + a.snippet + " " + a.author).lowercase().let { hay -> words.all { hay.contains(it) } }
            }.sortedWith(compareByDescending<Article> { it.date.takeIf { d -> d > 0 } ?: it.added }.thenByDescending { it.id })
    }

    /** Unread articles per feed id. */
    fun unreadByFeed(data: FeedData): Map<String, Int> =
        data.articles
            .filter { it.unread }
            .groupingBy { it.feedId }
            .eachCount()

    fun unreadInFolder(
        data: FeedData,
        folderId: String,
        byFeed: Map<String, Int> = unreadByFeed(data),
    ): Int = data.feeds.filter { it.folderId == folderId }.sumOf { byFeed[it.id] ?: 0 }

    /**
     * Sets [ids] to [state] and, when [queue] (synced with a server), remembers the change to
     * send. Starring keeps a starred article starred; "read" on a starred one leaves it starred.
     */
    fun setState(
        data: FeedData,
        ids: Set<String>,
        state: ArticleState,
        queue: Boolean,
    ): FeedData {
        val changed = mutableMapOf<String, ArticleState>()
        val articles =
            data.articles.map { a ->
                if (a.id !in ids || a.state == state) return@map a
                // Marking read (opening, mark all) doesn't unstar.
                if (state == ArticleState.Read && a.starred) return@map a
                changed[a.id] = state
                a.copy(state = state)
            }
        if (changed.isEmpty()) return data
        return data.copy(articles = articles, pending = if (queue) data.pending + changed else data.pending)
    }

    /**
     * Drops read articles older than [keepReadDays] (by date, or arrival when undated) and
     * articles of feeds no longer subscribed. Unread, starred and pending ones stay.
     */
    fun prune(
        data: FeedData,
        now: Long,
        keepReadDays: Int,
    ): FeedData {
        val feedIds = data.feeds.map { it.id }.toSet()
        val cutoff = now - keepReadDays * DAY_MS
        val kept =
            data.articles.filter { a ->
                a.feedId in feedIds &&
                    (a.state != ArticleState.Read || a.id in data.pending || maxOf(a.date, a.added) >= cutoff)
            }
        if (kept.size == data.articles.size) return data
        val keptIds = kept.map { it.id }.toSet()
        return data.copy(articles = kept, pending = data.pending.filterKeys { it in keptIds })
    }

    /**
     * Adds a fetched feed's new entries (on this phone) as unread articles of [feedId]; entries
     * already on the phone keep their state. Returns the new data and the new articles with their
     * content (to store apart).
     */
    fun addEntries(
        data: FeedData,
        feedId: String,
        parsed: ParsedFeed,
        now: Long,
        keepReadDays: Int,
    ): Pair<FeedData, List<Pair<Article, String>>> {
        val known =
            data.articles
                .filter { it.feedId == feedId }
                .map { it.id }
                .toSet()
        val cutoff = now - keepReadDays * DAY_MS
        val fresh =
            parsed.entries
                .distinctBy { it.id }
                .map { e -> e to articleId(feedId, e.id) }
                .filter { (e, id) -> id !in known && (e.date == 0L || e.date >= cutoff) }
                .map { (e, id) ->
                    val base = e.link.ifEmpty { parsed.siteUrl }
                    Article(
                        id = id,
                        feedId = feedId,
                        title = e.title.ifEmpty { Text.snippet(e.content, 80) },
                        link = e.link,
                        author = e.author,
                        date = e.date,
                        snippet = Text.snippet(e.content),
                        image = e.image ?: Text.firstImage(e.content, base),
                        added = now,
                    ) to e.content
                }
        val feeds =
            data.feeds.map { f ->
                if (f.id != feedId) {
                    f
                } else {
                    f.copy(
                        title = f.title.ifEmpty { parsed.title },
                        siteUrl = f.siteUrl.ifEmpty { parsed.siteUrl },
                        error = null,
                    )
                }
            }
        return data.copy(feeds = feeds, articles = data.articles + fresh.map { it.first }) to fresh
    }

    /** A stable article id from its feed and entry id (on this phone). */
    fun articleId(
        feedId: String,
        entryId: String,
    ): String {
        val digest =
            java.security.MessageDigest
                .getInstance("SHA-1")
                .digest((feedId + "\u0000" + entryId).toByteArray())
        return "l" + digest.take(10).joinToString("") { "%02x".format(it) }
    }

    /** Ids of articles a feed or folder's "mark all as read" covers: the unread ones shown. */
    fun unreadIds(articles: List<Article>): Set<String> = articles.filter { it.unread }.map { it.id }.toSet()

    const val DAY_MS = 24 * 60 * 60 * 1000L
}
