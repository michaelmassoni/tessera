package io.github.michaelmassoni.tessera.feeds

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.feeds.data.AccountStore
import io.github.michaelmassoni.tessera.feeds.data.ContentStore
import io.github.michaelmassoni.tessera.feeds.data.FeedStore
import io.github.michaelmassoni.tessera.feeds.data.IconStore
import io.github.michaelmassoni.tessera.feeds.notify.NewArticles
import io.github.michaelmassoni.tessera.feeds.sync.SyncWorker
import io.github.michaelmassoni.tessera.feeds.widget.LatestWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.io.File

/**
 * Holds the stores, and keeps what follows the articles in step: the widget, and a sync a few
 * seconds after read/star changes made on the phone (yarr).
 */
class FeedsApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val store: FeedStore by lazy { FeedStore(File(filesDir, "feeds.json"), scope) }
    val content: ContentStore by lazy { ContentStore(File(filesDir, "articles")) }
    val icons: IconStore by lazy { IconStore(File(filesDir, "icons")) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        NewArticles.ensureChannel(this)
        defaultInterval()
        // Nothing to refresh until there are feeds (or a server).
        if (AccountStore(this).yarr != null ||
            store.data.value.feeds
                .isNotEmpty()
        ) {
            schedule()
        }
        store.data
            .drop(1)
            .debounce(CHANGE_DELAY_MS)
            .onEach { data ->
                if (!scheduled && data.feeds.isNotEmpty()) schedule()
                LatestWidget.refresh(this)
                if (data.pending.isNotEmpty() && AccountStore(this).yarr != null) SyncWorker.schedule.afterLocalChange(this)
            }.launchIn(scope)
    }

    private var scheduled = false

    private fun schedule() {
        scheduled = true
        SyncWorker.schedule.apply(this)
    }

    /** Feeds refreshes every hour unless the person chose otherwise (the DAV apps default to 4). */
    private fun defaultInterval() {
        val prefs = getSharedPreferences("sync_settings", MODE_PRIVATE)
        if (!prefs.contains("interval")) prefs.edit { putString("interval", "Hour1") }
    }

    private companion object {
        const val CHANGE_DELAY_MS = 1_000L
    }
}

val Context.app: FeedsApplication get() = applicationContext as FeedsApplication
