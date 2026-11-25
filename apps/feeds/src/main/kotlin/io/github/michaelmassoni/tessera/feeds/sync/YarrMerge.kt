package io.github.michaelmassoni.tessera.feeds.sync

import io.github.michaelmassoni.tessera.feeds.engine.Article
import io.github.michaelmassoni.tessera.feeds.engine.ArticleState
import io.github.michaelmassoni.tessera.feeds.engine.Dates
import io.github.michaelmassoni.tessera.feeds.engine.Feed
import io.github.michaelmassoni.tessera.feeds.engine.FeedData
import io.github.michaelmassoni.tessera.feeds.engine.Folder
import io.github.michaelmassoni.tessera.feeds.engine.Text

/** What a pull from yarr found. */
data class YarrSnapshot(
    val folders: List<YarrFolder>,
    val feeds: List<YarrFeed>,
    val errors: Map<Long, String>,
    /** Every unread item. */
    val unread: List<YarrItem>,
    /** Every starred item. */
    val starred: List<YarrItem>,
    /** The newest items of any state, down to one the phone already had. */
    val recent: List<YarrItem>,
)

/** The result of a merge: the new data, and the new articles with their content (to store). */
data class Merged(
    val data: FeedData,
    val added: List<Pair<Article, String>>,
)

/** Applies a [YarrSnapshot] to the phone's data. Pure: unit tested. */
object YarrMerge {
    fun folderId(remote: Long) = "f$remote"

    fun feedId(remote: Long) = "y$remote"

    fun articleId(remote: Long) = "y$remote"

    fun state(status: String) =
        when (status) {
            "read" -> ArticleState.Read
            "starred" -> ArticleState.Starred
            else -> ArticleState.Unread
        }

    fun status(state: ArticleState) =
        when (state) {
            ArticleState.Read -> "read"
            ArticleState.Starred -> "starred"
            ArticleState.Unread -> "unread"
        }

    /**
     * The server's folders and feeds replace the phone's (keeping each feed's notify choice).
     * Articles take the server's state unless a change made on the phone is still pending; an article
     * the server no longer lists as unread or starred has been read elsewhere.
     */
    fun merge(
        data: FeedData,
        snap: YarrSnapshot,
        now: Long,
    ): Merged {
        val folders = snap.folders.map { Folder(folderId(it.id), it.title, it.id) }
        val oldFeeds = data.feeds.associateBy { it.id }
        val feeds =
            snap.feeds.map { f ->
                val id = feedId(f.id)
                Feed(
                    id = id,
                    title = f.title.ifEmpty { f.feedLink },
                    url = f.feedLink,
                    siteUrl = f.link,
                    folderId = f.folderId?.let(::folderId),
                    remoteId = f.id,
                    notify = oldFeeds[id]?.notify ?: false,
                    error = snap.errors[f.id]?.takeIf { it.isNotBlank() },
                )
            }
        val feedIds = feeds.map { it.id }.toSet()
        val listed = (snap.unread + snap.starred + snap.recent).associateBy { it.id }
        val unreadOrStarred = (snap.unread + snap.starred).map { it.id }.toSet()
        val existing = data.articles.associateBy { it.id }

        val kept =
            data.articles.mapNotNull { a ->
                if (a.feedId !in feedIds) return@mapNotNull null
                val pending = data.pending[a.id]
                val remote = a.remoteId?.let { listed[it] }
                val state =
                    when {
                        pending != null -> pending
                        remote != null -> state(remote.status)
                        a.remoteId != null && a.remoteId !in unreadOrStarred -> ArticleState.Read
                        else -> a.state
                    }
                a.copy(state = state)
            }
        val added =
            listed.values
                .filter { articleId(it.id) !in existing && feedId(it.feedId) in feedIds }
                .sortedBy { it.id }
                .map { item -> article(item, now) to item.content }
        return Merged(
            data.copy(folders = folders, feeds = feeds, articles = kept + added.map { it.first }),
            added,
        )
    }

    fun article(
        item: YarrItem,
        now: Long,
    ): Article {
        val title = Text.plain(item.title)
        return Article(
            id = articleId(item.id),
            feedId = feedId(item.feedId),
            title = title.ifEmpty { Text.snippet(item.content, 80) },
            link = item.link,
            date = Dates.parse(item.date),
            state = state(item.status),
            snippet = Text.snippet(item.content),
            image = Text.firstImage(item.content, item.link),
            remoteId = item.id,
            added = now,
        )
    }
}
