package io.github.michaelmassoni.tessera.feeds

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.feeds.data.AccountStore
import io.github.michaelmassoni.tessera.feeds.data.Adding
import io.github.michaelmassoni.tessera.feeds.data.FeedsPrefs
import io.github.michaelmassoni.tessera.feeds.data.Subscriptions
import io.github.michaelmassoni.tessera.feeds.engine.Article
import io.github.michaelmassoni.tessera.feeds.engine.ArticleState
import io.github.michaelmassoni.tessera.feeds.engine.Feed
import io.github.michaelmassoni.tessera.feeds.engine.Folder
import io.github.michaelmassoni.tessera.feeds.engine.Library
import io.github.michaelmassoni.tessera.feeds.engine.Selection
import io.github.michaelmassoni.tessera.feeds.engine.SwipeAction
import io.github.michaelmassoni.tessera.feeds.notify.NewArticles
import io.github.michaelmassoni.tessera.feeds.sync.SyncWorker
import io.github.michaelmassoni.tessera.feeds.ui.FeedsActions
import io.github.michaelmassoni.tessera.feeds.ui.FeedsApp
import io.github.michaelmassoni.tessera.feeds.ui.FeedsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    private var selection by mutableStateOf<Selection>(Selection.Unread)
    private var openArticle by mutableStateOf<String?>(null)
    private var textScale by mutableFloatStateOf(1f)
    private var swipeRight by mutableStateOf(SwipeAction.ToggleRead)
    private var swipeLeft by mutableStateOf(SwipeAction.ToggleStar)
    private var notice by mutableStateOf<String?>(null)
    private val accounts by lazy { AccountStore(this) }
    private val subscriptions by lazy { Subscriptions(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(STATE_SELECTION)?.let { s ->
            runCatching { Json.decodeFromString(Selection.serializer(), s) }.getOrNull()?.let { selection = it }
        }
        if (savedInstanceState == null) handle(intent)
        val actions = Actions()
        val work = WorkManager.getInstance(this).getWorkInfosForUniqueWorkFlow(SyncWorker.schedule.soonName)
        setTesseraContent {
            SetupGate(feedsSetup) {
                val data by app.store.data.collectAsStateWithLifecycle()
                val infos by work.collectAsStateWithLifecycle(emptyList())
                val syncing = infos.any { it.state == WorkInfo.State.RUNNING }
                // Say so when a sync that was running fails.
                var wasSyncing by remember { mutableStateOf(false) }
                LaunchedEffect(syncing) {
                    if (wasSyncing && !syncing) notice = problemText()
                    wasSyncing = syncing
                }
                val icons =
                    remember(data.feeds) {
                        data.feeds.filter { app.icons.has(it.id) }.associate { it.id to Uri.fromFile(app.icons.file(it.id)).toString() }
                    }
                FeedsApp(
                    FeedsState(
                        data = data,
                        selection = selection,
                        syncing = syncing,
                        onServer = accounts.yarr != null,
                        notice = notice,
                        icons = icons,
                        textScale = textScale,
                        swipeRight = swipeRight,
                        swipeLeft = swipeLeft,
                    ),
                    actions,
                    openArticle = openArticle,
                    onOpenedArticle = { openArticle = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        intent.getStringExtra(EXTRA_ARTICLE)?.let { openArticle = it }
        intent.getStringExtra(EXTRA_FEED)?.let {
            selection = Selection.OfFeed(it)
            NewArticles.cancel(this, it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTION, Json.encodeToString(Selection.serializer(), selection))
    }

    override fun onResume() {
        super.onResume()
        textScale = FeedsPrefs.textScale(this)
        swipeRight = FeedsPrefs.swipeRight(this)
        swipeLeft = FeedsPrefs.swipeLeft(this)
        // Opening the app brings what's new, unless it synced in the last few minutes.
        if (System.currentTimeMillis() - accounts.lastSync > RESYNC_AFTER_MS) SyncWorker.schedule.soon(this, delaySeconds = 0)
    }

    private fun problemText(): String? =
        when (accounts.lastProblem) {
            null -> null
            SyncProblem.WrongLogin.name -> getString(R.string.sync_failed_login)
            SyncProblem.Unreachable.name -> getString(R.string.sync_failed_unreachable)
            else -> getString(R.string.sync_failed)
        }

    private inner class Actions : FeedsActions {
        override fun select(selection: Selection) {
            this@MainActivity.selection = selection
            notice = null
        }

        override fun setState(
            ids: Set<String>,
            state: ArticleState,
        ) {
            val queue = accounts.yarr != null
            app.store.update { Library.setState(it, ids, state, queue) }
        }

        override fun unstar(ids: Set<String>) {
            val queue = accounts.yarr != null
            app.store.update { Library.unstar(it, ids, queue) }
        }

        override fun refresh() {
            notice = null
            SyncWorker.schedule.soon(this@MainActivity, delaySeconds = 0)
        }

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))

        override fun share(article: Article) {
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, article.title)
                    .putExtra(Intent.EXTRA_TEXT, article.link)
            startActivity(Intent.createChooser(send, article.title))
        }

        override fun openLink(url: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, R.string.no_browser, Toast.LENGTH_SHORT).show()
            }
        }

        override suspend fun content(id: String): String? = withContext(Dispatchers.IO) { app.content.read(id) }

        override suspend fun fullArticle(article: Article): String? = subscriptions.fullArticle(article.link)

        override suspend fun addFeed(
            address: String,
            folderId: String?,
        ): Adding = subscriptions.addFeed(address, folderId)

        override suspend fun renameFeed(
            feed: Feed,
            title: String,
        ) = subscriptions.renameFeed(feed, title)

        override suspend fun moveFeed(
            feed: Feed,
            folderId: String?,
        ) = subscriptions.moveFeed(feed, folderId)

        override suspend fun unsubscribe(feed: Feed): Boolean {
            val ok = subscriptions.unsubscribe(feed)
            if (ok && (selection as? Selection.OfFeed)?.feedId == feed.id) selection = Selection.Unread
            return ok
        }

        override fun setNotify(
            feedId: String,
            on: Boolean,
        ) = subscriptions.setNotify(feedId, on)

        override suspend fun newFolder(title: String) = subscriptions.newFolder(title)

        override suspend fun renameFolder(
            folder: Folder,
            title: String,
        ) = subscriptions.renameFolder(folder, title)

        override suspend fun deleteFolder(folder: Folder): Boolean {
            val ok = subscriptions.deleteFolder(folder)
            if (ok && (selection as? Selection.InFolder)?.folderId == folder.id) selection = Selection.Unread
            return ok
        }
    }

    companion object {
        const val EXTRA_ARTICLE = "article"
        const val EXTRA_FEED = "feed"
        private const val STATE_SELECTION = "selection"
        private const val RESYNC_AFTER_MS = 5 * 60 * 1000L
    }
}
