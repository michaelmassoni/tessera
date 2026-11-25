package io.github.michaelmassoni.tessera.feeds.data

import android.content.Context
import io.github.michaelmassoni.tessera.core.dav.SignInError
import io.github.michaelmassoni.tessera.core.dav.SignInProblem
import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import io.github.michaelmassoni.tessera.core.settings.ServerAddress
import io.github.michaelmassoni.tessera.feeds.app
import io.github.michaelmassoni.tessera.feeds.engine.Discovery
import io.github.michaelmassoni.tessera.feeds.engine.Feed
import io.github.michaelmassoni.tessera.feeds.engine.FeedData
import io.github.michaelmassoni.tessera.feeds.engine.Folder
import io.github.michaelmassoni.tessera.feeds.engine.Opml
import io.github.michaelmassoni.tessera.feeds.sync.AddResult
import io.github.michaelmassoni.tessera.feeds.sync.LocalFetcher
import io.github.michaelmassoni.tessera.feeds.sync.SyncWorker
import io.github.michaelmassoni.tessera.feeds.sync.YarrClient
import io.github.michaelmassoni.tessera.feeds.sync.YarrMerge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** What adding a feed came to. */
sealed interface Adding {
    data class Added(
        val feedId: String,
    ) : Adding

    /** The address offers several feeds: (url, title). */
    data class Choose(
        val choices: List<Pair<String, String>>,
    ) : Adding

    data object NotFound : Adding

    data object Unreachable : Adding
}

/**
 * Changes to subscriptions and the source, for both modes: on a yarr server they're made on the
 * server first (and fail when it can't be reached), on this phone straight in the store.
 */
class Subscriptions(
    context: Context,
) {
    private val context = context.applicationContext
    private val app = context.app
    private val accounts = AccountStore(context)

    private suspend fun <T> server(block: (YarrClient) -> T): T? =
        withContext(Dispatchers.IO) {
            val client = accounts.client() ?: return@withContext null
            block(client)
        }

    /**
     * Checks a yarr server and saves it as the source, replacing what's on the phone. Feeds that
     * were on this phone are added to the server (it skips ones it has).
     */
    suspend fun useYarr(
        address: String,
        username: String,
        password: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            val base =
                try {
                    ServerAddress.normalize(address)
                } catch (e: ServerAddress.Invalid) {
                    return@withContext Result.failure(
                        SignInError(if (e.problem == ServerAddress.Problem.Insecure) SignInProblem.Insecure else SignInProblem.BadAddress),
                    )
                }
            val user = username.trim().ifEmpty { null }
            var cookie: String? = null
            val client = YarrClient(base, user, password.takeIf { user != null }, onCookie = { cookie = it })
            try {
                client.check()
                val local =
                    app.store.data.value
                        .takeIf { accounts.yarr == null && it.feeds.isNotEmpty() }
                if (local != null) client.importOpml(Opml.write(local.folders, local.feeds))
            } catch (e: SyncException) {
                return@withContext Result.failure(
                    SignInError(
                        when (e.problem) {
                            SyncProblem.Unreachable -> SignInProblem.Unreachable
                            SyncProblem.WrongLogin -> SignInProblem.WrongLogin
                            SyncProblem.NotCalDav -> SignInProblem.NotCalDav
                            SyncProblem.Other -> SignInProblem.Other
                        },
                    ),
                )
            }
            accounts.saveYarr(base, user, password.takeIf { user != null }, cookie)
            clearArticles()
            SyncWorker.schedule.soon(context, delaySeconds = 0)
            Result.success(Unit)
        }

    /** Back to fetching on this phone, keeping the server's feeds and folders (not its articles). */
    fun usePhone() {
        accounts.usePhone()
        app.store.update { d ->
            val ids = d.folders.associate { it.id to UUID.randomUUID().toString() }
            FeedData(
                folders = d.folders.map { it.copy(id = ids.getValue(it.id), remoteId = null) },
                feeds =
                    d.feeds.map {
                        it.copy(id = UUID.randomUUID().toString(), remoteId = null, folderId = it.folderId?.let(ids::get), error = null)
                    },
            )
        }
        app.content.clear()
        SyncWorker.schedule.soon(context, delaySeconds = 0)
    }

    private fun clearArticles() {
        app.store.update { FeedData() }
        app.content.clear()
    }

    suspend fun addFeed(
        address: String,
        folderId: String?,
    ): Adding {
        val data = app.store.data.value
        if (accounts.yarr != null) {
            val remoteFolder = folderId?.let { data.folder(it)?.remoteId }
            return try {
                when (val r = server { it.addFeed(address.trim(), remoteFolder) }) {
                    is AddResult.Added -> {
                        SyncWorker.schedule.soon(context, delaySeconds = 0)
                        Adding.Added(YarrMerge.feedId(r.feed.id))
                    }

                    is AddResult.Choices -> {
                        Adding.Choose(r.choices)
                    }

                    else -> {
                        Adding.NotFound
                    }
                }
            } catch (_: SyncException) {
                Adding.Unreachable
            }
        }
        val found =
            withContext(Dispatchers.IO) {
                try {
                    LocalFetcher().discover(address)
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    emptyList()
                }
            } ?: return Adding.Unreachable
        return when {
            found.isEmpty() -> Adding.NotFound
            found.size > 1 -> Adding.Choose(found.map { it.url to it.title.ifEmpty { it.url } })
            else -> Adding.Added(addLocal(found[0], folderId))
        }
    }

    private fun addLocal(
        found: Discovery.Found,
        folderId: String?,
    ): String {
        val existing =
            app.store.data.value.feeds
                .firstOrNull { it.url == found.url }
        if (existing != null) return existing.id
        val id = UUID.randomUUID().toString()
        app.store.update { d -> d.copy(feeds = d.feeds + Feed(id, found.title, found.url, folderId = folderId)) }
        SyncWorker.schedule.soon(context, delaySeconds = 0)
        return id
    }

    /** Renames [feed]; false when the server couldn't be reached. */
    suspend fun renameFeed(
        feed: Feed,
        title: String,
    ): Boolean =
        attempt(feed.remoteId) { it.updateFeed(feed.remoteId!!, title = title) }.also { ok ->
            if (ok) app.store.update { d -> d.copy(feeds = d.feeds.map { if (it.id == feed.id) it.copy(title = title) else it }) }
        }

    suspend fun moveFeed(
        feed: Feed,
        folderId: String?,
    ): Boolean {
        val remoteFolder =
            folderId?.let {
                app.store.data.value
                    .folder(it)
                    ?.remoteId
            }
        return attempt(feed.remoteId) { it.updateFeed(feed.remoteId!!, moveFolder = true, folderId = remoteFolder) }.also { ok ->
            if (ok) app.store.update { d -> d.copy(feeds = d.feeds.map { if (it.id == feed.id) it.copy(folderId = folderId) else it }) }
        }
    }

    suspend fun unsubscribe(feed: Feed): Boolean =
        attempt(feed.remoteId) { it.deleteFeed(feed.remoteId!!) }.also { ok ->
            if (ok) {
                app.store.update { d ->
                    d.copy(
                        feeds = d.feeds.filterNot { it.id == feed.id },
                        articles = d.articles.filterNot { it.feedId == feed.id },
                        pending = d.pending.filterKeys { id -> d.article(id)?.feedId != feed.id },
                    )
                }
            }
        }

    fun setNotify(
        feedId: String,
        on: Boolean,
    ) = app.store.update { d -> d.copy(feeds = d.feeds.map { if (it.id == feedId) it.copy(notify = on) else it }) }

    /** A new folder's id, or null when the server couldn't be reached. */
    suspend fun newFolder(title: String): String? {
        if (accounts.yarr == null) {
            val id = UUID.randomUUID().toString()
            app.store.update { d -> d.copy(folders = d.folders + Folder(id, title)) }
            return id
        }
        val made =
            try {
                server { it.createFolder(title) }
            } catch (_: SyncException) {
                null
            } ?: return null
        val folder = Folder(YarrMerge.folderId(made.id), made.title, made.id)
        app.store.update { d -> d.copy(folders = d.folders + folder) }
        return folder.id
    }

    suspend fun renameFolder(
        folder: Folder,
        title: String,
    ): Boolean =
        attempt(folder.remoteId) { it.renameFolder(folder.remoteId!!, title) }.also { ok ->
            if (ok) app.store.update { d -> d.copy(folders = d.folders.map { if (it.id == folder.id) it.copy(title = title) else it }) }
        }

    /** Deletes [folder]; its feeds stay, outside any folder (as yarr does). */
    suspend fun deleteFolder(folder: Folder): Boolean =
        attempt(folder.remoteId) { it.deleteFolder(folder.remoteId!!) }.also { ok ->
            if (ok) {
                app.store.update { d ->
                    d.copy(
                        folders = d.folders.filterNot { it.id == folder.id },
                        feeds = d.feeds.map { if (it.folderId == folder.id) it.copy(folderId = null) else it },
                    )
                }
            }
        }

    /** Adds the feeds in [xml]; returns how many were new, or null when it isn't OPML or the server failed. */
    suspend fun importOpml(xml: String): Int? {
        val outlines = Opml.read(xml)
        if (outlines.isEmpty()) return null
        if (accounts.yarr != null) {
            val known =
                app.store.data.value.feeds
                    .map { it.url }
                    .toSet()
            return try {
                server { it.importOpml(xml) }
                SyncWorker.schedule.soon(context, delaySeconds = 0)
                outlines.count { it.url !in known }
            } catch (_: SyncException) {
                null
            }
        }
        var added = 0
        app.store.update { d ->
            var folders = d.folders
            val feeds = d.feeds.toMutableList()
            outlines.forEach { o ->
                if (feeds.any { it.url == o.url }) return@forEach
                val folderId =
                    o.folder?.let { name ->
                        folders.firstOrNull { it.title == name }?.id
                            ?: UUID.randomUUID().toString().also { folders = folders + Folder(it, name) }
                    }
                feeds += Feed(UUID.randomUUID().toString(), o.title, o.url, o.siteUrl, folderId)
            }
            added = feeds.size - d.feeds.size
            d.copy(folders = folders, feeds = feeds)
        }
        SyncWorker.schedule.soon(context, delaySeconds = 0)
        return added
    }

    fun exportOpml(): String =
        app.store.data.value
            .let { Opml.write(it.folders, it.feeds) }

    /** The page behind [link] reduced to its main text (yarr only); null when it failed. */
    suspend fun fullArticle(link: String): String? =
        try {
            server { it.fullArticle(link) }
        } catch (_: SyncException) {
            null
        }

    val canFetchFullArticle: Boolean get() = accounts.yarr != null

    /** Runs [block] on the server when [remoteId] says it lives there; true when done (or local). */
    private suspend fun attempt(
        remoteId: Long?,
        block: (YarrClient) -> Unit,
    ): Boolean {
        if (remoteId == null || accounts.yarr == null) return true
        return try {
            server(block)
            true
        } catch (_: SyncException) {
            false
        }
    }
}
