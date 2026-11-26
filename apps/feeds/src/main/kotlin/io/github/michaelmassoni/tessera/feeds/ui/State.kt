package io.github.michaelmassoni.tessera.feeds.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.michaelmassoni.tessera.core.design.Avatar
import io.github.michaelmassoni.tessera.feeds.data.Adding
import io.github.michaelmassoni.tessera.feeds.engine.Article
import io.github.michaelmassoni.tessera.feeds.engine.ArticleState
import io.github.michaelmassoni.tessera.feeds.engine.Feed
import io.github.michaelmassoni.tessera.feeds.engine.FeedData
import io.github.michaelmassoni.tessera.feeds.engine.Folder
import io.github.michaelmassoni.tessera.feeds.engine.Selection
import io.github.michaelmassoni.tessera.feeds.engine.SwipeAction

/** What the screens show. */
@Immutable
data class FeedsState(
    val data: FeedData,
    val selection: Selection = Selection.Unread,
    val syncing: Boolean = false,
    /** Synced with a yarr server (rather than fetched on this phone). */
    val onServer: Boolean = false,
    /** A message to show once (a failed sync). */
    val notice: String? = null,
    /** Feed id to its icon file's URI. */
    val icons: Map<String, String> = emptyMap(),
    /** Article text size, as a factor of the system's. */
    val textScale: Float = 1f,
    /** What swiping an article row right and left does. */
    val swipeRight: SwipeAction = SwipeAction.ToggleRead,
    val swipeLeft: SwipeAction = SwipeAction.ToggleStar,
)

/** What the screens can do. */
interface FeedsActions {
    fun select(selection: Selection)

    fun setState(
        ids: Set<String>,
        state: ArticleState,
    )

    /** Unstars [ids], which makes them read. */
    fun unstar(ids: Set<String>)

    fun refresh()

    fun openSettings()

    fun share(article: Article)

    fun openLink(url: String)

    /** The article's stored HTML. */
    suspend fun content(id: String): String?

    /** The article's page reduced to its main text (yarr), or null when that failed. */
    suspend fun fullArticle(article: Article): String?

    suspend fun addFeed(
        address: String,
        folderId: String?,
    ): Adding

    suspend fun renameFeed(
        feed: Feed,
        title: String,
    ): Boolean

    suspend fun moveFeed(
        feed: Feed,
        folderId: String?,
    ): Boolean

    suspend fun unsubscribe(feed: Feed): Boolean

    fun setNotify(
        feedId: String,
        on: Boolean,
    )

    suspend fun newFolder(title: String): String?

    suspend fun renameFolder(
        folder: Folder,
        title: String,
    ): Boolean

    suspend fun deleteFolder(folder: Folder): Boolean
}

/** A feed's icon, or its initial when it has none. */
@Composable
internal fun FeedIcon(
    title: String,
    feedId: String,
    icon: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) = Avatar(name = title.ifBlank { "?" }, key = feedId, photoUri = icon, modifier = modifier, size = size)
