package io.github.michaelmassoni.tessera.podcasts.data

import android.content.Context
import io.github.michaelmassoni.tessera.podcasts.engine.Directory
import io.github.michaelmassoni.tessera.podcasts.engine.Episode
import io.github.michaelmassoni.tessera.podcasts.engine.FeedContent
import io.github.michaelmassoni.tessera.podcasts.engine.Feeds
import io.github.michaelmassoni.tessera.podcasts.engine.Ids
import io.github.michaelmassoni.tessera.podcasts.engine.Library
import io.github.michaelmassoni.tessera.podcasts.engine.NotAFeedException
import io.github.michaelmassoni.tessera.podcasts.engine.Opml
import io.github.michaelmassoni.tessera.podcasts.engine.Show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/** Why a show couldn't be added. */
enum class AddProblem { Offline, NotAFeed }

class AddException(
    val problem: AddProblem,
) : Exception()

/**
 * The library's operations that aren't playback: opening and following shows, refreshing feeds,
 * marking episodes played, OPML. Queue changes go through the player (PlayerConnection).
 */
class Podcasts(
    private val context: Context,
    val store: PodcastStore,
) {
    private val refreshing = Mutex()

    private fun now() = System.currentTimeMillis()

    /** The id of the show at [feedUrl]: a library show, or a preview fetched now. @throws AddException */
    suspend fun open(feedUrl: String): String {
        val key = Directory.key(feedUrl)
        store.library.value.shows
            .firstOrNull { Directory.key(it.feedUrl) == key }
            ?.let { return it.id }
        store.previews.value.values
            .firstOrNull { Directory.key(it.show.feedUrl) == key }
            ?.let { return it.show.id }
        return withContext(Dispatchers.IO) {
            val fetched =
                try {
                    Net.fetchFeed(feedUrl) as FeedFetch.Fetched
                } catch (_: NotAFeedException) {
                    throw AddException(AddProblem.NotAFeed)
                } catch (_: IOException) {
                    throw AddException(AddProblem.Offline)
                } catch (_: IllegalArgumentException) {
                    throw AddException(AddProblem.NotAFeed)
                }
            val id = Ids.show(feedUrl)
            val content = Feeds.content(id, fetched.feed, emptyList(), emptySet())
            if (content.episodes.isEmpty()) throw AddException(AddProblem.NotAFeed)
            val show =
                Feeds.show(
                    Show(id, fetched.movedTo ?: feedUrl, fetched.feed.title, etag = fetched.etag, lastModified = fetched.lastModified),
                    fetched.feed,
                    content,
                    now(),
                )
            store.putPreview(Preview(show, content))
            id
        }
    }

    /** "Add by URL": a feed address or an Apple Podcasts link. @throws AddException */
    suspend fun openAddress(input: String): String {
        Directory.appleId(input)?.let { appleId ->
            val feed =
                withContext(Dispatchers.IO) {
                    try {
                        Net.appleFeed(appleId)
                    } catch (_: IOException) {
                        throw AddException(AddProblem.Offline)
                    }
                } ?: throw AddException(AddProblem.NotAFeed)
            return open(feed)
        }
        val address = Directory.feedAddress(input) ?: throw AddException(AddProblem.NotAFeed)
        return open(address)
    }

    /** Follows a previewed (or kept) show. */
    suspend fun follow(showId: String) {
        val preview = store.previews.value[showId]
        if (preview != null) {
            withContext(Dispatchers.IO) { store.saveFeed(showId, preview.content) }
            store.update { lib ->
                val shows = lib.shows.filterNot { it.id == showId } + preview.show.copy(followed = true)
                lib.copy(shows = shows)
            }
            store.dropPreview(showId)
        } else {
            store.update { lib -> lib.copy(shows = lib.shows.map { if (it.id == showId) it.copy(followed = true) else it }) }
        }
    }

    /**
     * Keeps a previewed show in the library, not followed, so its episodes can be queued and
     * played after the app restarts. Nothing to do for a library show.
     */
    suspend fun keep(showId: String) {
        val preview = store.previews.value[showId] ?: return
        if (store.library.value.show(showId) != null) return
        withContext(Dispatchers.IO) { store.saveFeed(showId, preview.content) }
        store.update { lib -> lib.copy(shows = lib.shows + preview.show.copy(followed = false)) }
    }

    /**
     * Stops following:the show's downloads and progress go. The show stays (unfollowed) while an
     * episode of it is in [upNext]; the caller takes them out of the player.
     */
    suspend fun unfollow(showId: String) {
        val lib = store.library.value
        val ids = lib.states.keys.filter { Ids.showOf(it) == showId }
        withContext(Dispatchers.IO) { ids.forEach { Downloads.delete(context, store, it) } }
        store.update { l ->
            l.copy(
                shows = l.shows.filterNot { it.id == showId },
                states = l.states.filterKeys { Ids.showOf(it) != showId },
            )
        }
        withContext(Dispatchers.IO) { store.forgetFeed(showId) }
    }

    /** Marks an episode played (deleting its download if Settings say so) or not played. */
    fun markPlayed(
        episodeId: String,
        played: Boolean,
    ) {
        store.update { lib ->
            lib.withState(episodeId) {
                if (played) it.copy(played = true, playedAt = now(), positionMs = 0) else it.copy(played = false, playedAt = 0)
            }
        }
        if (played && Prefs.deletePlayed(context)) Downloads.delete(context, store, episodeId)
    }

    /**
     * Fetches every followed show's feed (or just [showIds]), a few at a time.
     * @return the episodes that are new since the last refresh.
     */
    suspend fun refresh(showIds: Set<String>? = null): List<Episode> =
        refreshing.withLock {
            withContext(Dispatchers.IO) {
                val shows =
                    store.library.value.followed
                        .filter { showIds == null || it.id in showIds }
                val limit = Semaphore(4)
                val added =
                    coroutineScope {
                        shows.map { show -> async { limit.withPermit { refreshShow(show) } } }.awaitAll().flatten()
                    }
                if (showIds == null) store.update { it.copy(refreshedAt = now()) }
                added
            }
        }

    private fun refreshShow(show: Show): List<Episode> {
        val fetched =
            try {
                Net.fetchFeed(show.feedUrl, show.etag, show.lastModified)
            } catch (_: IOException) {
                return emptyList()
            } catch (_: NotAFeedException) {
                return emptyList()
            } catch (_: IllegalArgumentException) {
                return emptyList()
            }
        if (fetched !is FeedFetch.Fetched) {
            store.update { lib -> lib.copy(shows = lib.shows.map { if (it.id == show.id) it.copy(refreshedAt = now()) else it }) }
            return emptyList()
        }
        val lib = store.library.value
        val old = store.episodesOf(show.id)
        val keep = old.map { it.id }.filter { it in lib.upNext || lib.state(it).download != null }.toSet()
        val content: FeedContent = Feeds.content(show.id, fetched.feed, old, keep)
        val added = if (old.isEmpty()) emptyList() else Feeds.added(old, content)
        store.saveFeed(show.id, content)
        store.update { l ->
            l.copy(
                shows =
                    l.shows.map {
                        if (it.id != show.id) {
                            it
                        } else {
                            Feeds.show(it, fetched.feed, content, now()).copy(
                                feedUrl = fetched.movedTo ?: it.feedUrl,
                                etag = fetched.etag,
                                lastModified = fetched.lastModified,
                            )
                        }
                    },
            )
        }
        return added
    }

    /** Follows the feeds in an OPML file. @return how many were added. */
    suspend fun importOpml(input: InputStream): Int {
        val entries = withContext(Dispatchers.IO) { Opml.read(input) }
        val known =
            store.library.value.followed
                .map { Directory.key(it.feedUrl) }
                .toSet()
        val limit = Semaphore(4)
        val added =
            coroutineScope {
                entries.filter { Directory.key(it.feedUrl) !in known }.map { entry ->
                    async {
                        limit.withPermit {
                            runCatching {
                                follow(open(entry.feedUrl))
                                true
                            }.getOrDefault(false)
                        }
                    }
                }
            }.awaitAll()
        return added.count { it }
    }

    fun exportOpml(): String =
        Opml.write(
            store.library.value.followed
                .sortedBy { it.title.lowercase() },
        )

    /** Every library and preview show, for lookups by id. */
    fun show(id: String): Show? = store.library.value.show(id) ?: store.previews.value[id]?.show

    companion object {
        /** Library shows that aren't followed are kept only while Up Next holds one of their episodes. */
        fun pruned(lib: Library): Library {
            val queued = lib.upNext.map(Ids::showOf).toSet()
            return lib.copy(shows = lib.shows.filter { it.followed || it.id in queued })
        }
    }
}
