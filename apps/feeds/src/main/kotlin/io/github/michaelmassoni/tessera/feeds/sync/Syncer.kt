package io.github.michaelmassoni.tessera.feeds.sync

import android.content.Context
import android.util.Base64
import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import io.github.michaelmassoni.tessera.feeds.app
import io.github.michaelmassoni.tessera.feeds.data.AccountStore
import io.github.michaelmassoni.tessera.feeds.data.FeedsPrefs
import io.github.michaelmassoni.tessera.feeds.engine.Article
import io.github.michaelmassoni.tessera.feeds.engine.Library
import io.github.michaelmassoni.tessera.feeds.notify.NewArticles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * One sync: with the yarr server (push the phone's changes, then pull), or on this phone (fetch
 * each feed). Then prunes old read articles, tidies stored content and icons, and notifies.
 */
class Syncer(
    context: Context,
) {
    private val context = context.applicationContext
    private val app = context.app
    private val accounts = AccountStore(context)

    /** Runs a sync; returns the problem, or null when it went well. */
    suspend fun sync(): SyncProblem? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val added =
                try {
                    val client = accounts.client()
                    if (client != null) yarr(client, now) else phone(now)
                } catch (e: SyncException) {
                    accounts.lastProblem = e.problem.name
                    return@withContext e.problem
                }
            val keep = FeedsPrefs.keepReadDays(context)
            app.store.update { Library.prune(it, now, keep) }
            val data = app.store.data.value
            app.content.retain(data.articles.map { it.id }.toSet())
            app.icons.retain(data.feeds.map { it.id }.toSet())
            accounts.lastSync = now
            accounts.lastProblem = null
            NewArticles.post(context, data, added.filter { it.unread })
            null
        }

    private fun yarr(
        client: YarrClient,
        now: Long,
    ): List<Article> {
        push(client)
        val known =
            app.store.data.value.articles
                .mapNotNull { it.remoteId }
                .toSet()
        val snap =
            YarrSnapshot(
                folders = client.folders(),
                feeds = client.feeds(),
                errors = runCatching { client.feedErrors() }.getOrDefault(emptyMap()),
                unread = all(client, "unread"),
                starred = all(client, "starred"),
                recent = recent(client, known),
            )
        var merged: Merged? = null
        app.store.update { d -> YarrMerge.merge(d, snap, now).also { merged = it }.data }
        val added = merged?.added.orEmpty()
        added.forEach { (a, html) -> app.content.write(a.id, html) }
        snap.feeds.forEach { f ->
            val id = YarrMerge.feedId(f.id)
            val icon = f.icon?.let(::dataUri)
            when {
                icon != null -> app.icons.write(id, icon)
                f.hasIcon && !app.icons.has(id) -> client.icon(f.id)?.let { app.icons.write(id, it) }
            }
        }
        return added.map { it.first }
    }

    /** Sends the state changes made on the phone; each is forgotten once the server has it. */
    private fun push(client: YarrClient) {
        val data = app.store.data.value
        data.pending.forEach { (id, state) ->
            val remote = data.article(id)?.remoteId
            if (remote != null) client.setStatus(remote, YarrMerge.status(state))
            app.store.update { d -> if (d.pending[id] == state) d.copy(pending = d.pending - id) else d }
        }
    }

    private fun all(
        client: YarrClient,
        status: String,
    ): List<YarrItem> {
        val out = mutableListOf<YarrItem>()
        var after: Long? = null
        repeat(MAX_PAGES) {
            val page = client.items(status, after)
            out += page.list
            if (!page.hasMore || page.list.isEmpty()) return out
            after = page.list.last().id
        }
        return out
    }

    /** The newest items down to one already on the phone (at most [MAX_RECENT]). */
    private fun recent(
        client: YarrClient,
        known: Set<Long>,
    ): List<YarrItem> {
        val out = mutableListOf<YarrItem>()
        var after: Long? = null
        while (out.size < MAX_RECENT) {
            val page = client.items(null, after)
            val fresh = page.list.takeWhile { it.id !in known }
            out += fresh
            if (fresh.size < page.list.size || !page.hasMore || page.list.isEmpty()) break
            after = page.list.last().id
        }
        return out
    }

    private suspend fun phone(now: Long): List<Article> {
        val fetcher = LocalFetcher()
        val keep = FeedsPrefs.keepReadDays(context)
        val feeds = app.store.data.value.feeds
        val gate = Semaphore(PARALLEL)
        return coroutineScope {
            feeds
                .map { feed ->
                    async(Dispatchers.IO) {
                        gate.withPermit {
                            val result = fetcher.fetch(feed.url, feed.etag, feed.lastModified)
                            val added = mutableListOf<Pair<Article, String>>()
                            app.store.update { d ->
                                when (result) {
                                    is Fetched.Fresh -> {
                                        val (next, fresh) = Library.addEntries(d, feed.id, result.feed, now, keep)
                                        added.clear()
                                        added += fresh
                                        next.copy(
                                            feeds =
                                                next.feeds.map {
                                                    if (it.id ==
                                                        feed.id
                                                    ) {
                                                        it.copy(etag = result.etag, lastModified = result.lastModified)
                                                    } else {
                                                        it
                                                    }
                                                },
                                        )
                                    }

                                    Fetched.Unchanged -> {
                                        d.copy(feeds = d.feeds.map { if (it.id == feed.id) it.copy(error = null) else it })
                                    }

                                    is Fetched.Failed -> {
                                        d.copy(feeds = d.feeds.map { if (it.id == feed.id) it.copy(error = result.reason) else it })
                                    }
                                }
                            }
                            added.forEach { (a, html) -> app.content.write(a.id, html) }
                            if (!app.icons.has(feed.id)) {
                                val site =
                                    app.store.data.value
                                        .feed(feed.id)
                                        ?.siteUrl
                                        ?.ifEmpty { feed.url } ?: feed.url
                                fetcher.favicon(site)?.let { app.icons.write(feed.id, it) }
                            }
                            added.map { it.first }
                        }
                    }
                }.awaitAll()
                .flatten()
        }
    }

    private fun dataUri(uri: String): ByteArray? {
        if (!uri.startsWith("data:")) return null
        val comma = uri.indexOf(',')
        if (comma < 0) return null
        return try {
            Base64.decode(uri.substring(comma + 1), Base64.DEFAULT).takeIf { it.size > 1 }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val MAX_PAGES = 250
        const val MAX_RECENT = 500
        const val PARALLEL = 4
    }
}
