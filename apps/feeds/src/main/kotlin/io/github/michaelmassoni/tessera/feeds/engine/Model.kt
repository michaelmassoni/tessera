package io.github.michaelmassoni.tessera.feeds.engine

import kotlinx.serialization.Serializable

/** A folder of feeds. One level, as yarr and OPML readers have. */
@Serializable
data class Folder(
    val id: String,
    val title: String,
    /** The folder's id on the yarr server. */
    val remoteId: Long? = null,
)

/** A subscription. */
@Serializable
data class Feed(
    val id: String,
    val title: String,
    /** The feed's own address (RSS or Atom). */
    val url: String,
    /** The site it belongs to. */
    val siteUrl: String = "",
    val folderId: String? = null,
    val remoteId: Long? = null,
    /** Post a notification for its new articles. */
    val notify: Boolean = false,
    /** Why the last fetch failed, or null. */
    val error: String? = null,
    /** Conditional GET state (fetched on this phone). */
    val etag: String? = null,
    val lastModified: String? = null,
)

/** Read, unread or starred. Starred is a state of its own (as in yarr): unstarring makes it read. */
@Serializable
enum class ArticleState { Unread, Read, Starred }

/** An article's metadata. Its content is kept apart (see ContentStore). */
@Serializable
data class Article(
    val id: String,
    val feedId: String,
    val title: String,
    val link: String = "",
    val author: String = "",
    /** Published, epoch milliseconds. */
    val date: Long = 0,
    val state: ArticleState = ArticleState.Unread,
    /** The first lines of text, for the list. */
    val snippet: String = "",
    /** The first image, for the list's thumbnail. */
    val image: String? = null,
    val remoteId: Long? = null,
    /** When it first reached the phone. */
    val added: Long = 0,
) {
    val unread: Boolean get() = state == ArticleState.Unread
    val starred: Boolean get() = state == ArticleState.Starred
}

/** Everything kept in `files/feeds.json`. */
@Serializable
data class FeedData(
    val folders: List<Folder> = emptyList(),
    val feeds: List<Feed> = emptyList(),
    val articles: List<Article> = emptyList(),
    /** State changes made on the phone that the server hasn't had yet, by article id. */
    val pending: Map<String, ArticleState> = emptyMap(),
) {
    fun feed(id: String) = feeds.firstOrNull { it.id == id }

    fun folder(id: String) = folders.firstOrNull { it.id == id }

    fun article(id: String) = articles.firstOrNull { it.id == id }
}
