package io.github.michaelmassoni.tessera.podcasts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.michaelmassoni.tessera.core.settings.SetupGate
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.podcasts.data.Downloads
import io.github.michaelmassoni.tessera.podcasts.data.Net
import io.github.michaelmassoni.tessera.podcasts.data.RefreshWorker
import io.github.michaelmassoni.tessera.podcasts.engine.Episode
import io.github.michaelmassoni.tessera.podcasts.engine.SearchResult
import io.github.michaelmassoni.tessera.podcasts.ui.PlayerUi
import io.github.michaelmassoni.tessera.podcasts.ui.PodcastsActions
import io.github.michaelmassoni.tessera.podcasts.ui.PodcastsApp
import io.github.michaelmassoni.tessera.podcasts.ui.PodcastsState
import io.github.michaelmassoni.tessera.podcasts.ui.SearchModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val podcasts by lazy { applicationContext.podcasts }
    private val player by lazy { PlayerConnection(lifecycleScope) }
    private var refreshing by mutableStateOf(false)
    private val search = SearchModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = podcasts.store
        // Background refresh every 6 hours; scheduling again (KEEP) doesn't move the next run.
        RefreshWorker.schedule(this)
        val work = WorkManager.getInstance(this).getWorkInfosByTagFlow(Downloads.TAG)
        val actions = Actions()
        setTesseraContent {
            SetupGate(podcastsSetup) {
                val loaded by store.loaded.collectAsStateWithLifecycle()
                val library by store.library.collectAsStateWithLifecycle()
                val episodes by store.episodes.collectAsStateWithLifecycle()
                val previews by store.previews.collectAsStateWithLifecycle()
                val downloads by work.collectAsStateWithLifecycle(emptyList())
                val playerState by player.state.collectAsStateWithLifecycle()
                val sleep by SleepTimer.state.collectAsStateWithLifecycle()
                val progress by player.progress.collectAsStateWithLifecycle()
                val downloading =
                    remember(downloads) {
                        downloads
                            .filter { !it.state.isFinished }
                            .mapNotNull { info ->
                                val id =
                                    info.progress.getString(Downloads.KEY_EPISODE)
                                        ?: info.tags.firstOrNull { it.startsWith("episode:") }?.removePrefix("episode:")
                                id?.let {
                                    it to
                                        if (info.state == WorkInfo.State.RUNNING) info.progress.getInt(Downloads.KEY_PROGRESS, -1) else -1
                                }
                            }.toMap()
                    }
                val state =
                    PodcastsState(
                        loaded = loaded,
                        library = library,
                        episodes = episodes + previews.mapValues { it.value.content.episodes },
                        previews = previews.mapValues { it.value.show },
                        downloading = downloading,
                        refreshing = refreshing,
                        player =
                            PlayerUi(
                                episodeId = playerState.episodeId,
                                isPlaying = playerState.isPlaying,
                                buffering = playerState.buffering,
                                failed = playerState.failed,
                                speed = playerState.speed,
                                queue = playerState.queue,
                                sleep = sleep,
                            ),
                    )
                PodcastsApp(state, { progress }, actions, search = search)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player.connect(this)
        // Opening the app brings new episodes, at most once an hour.
        val lib = podcasts.store.library.value
        if (lib.followed.isNotEmpty() && System.currentTimeMillis() - lib.refreshedAt > AUTO_REFRESH_MS) Actions().refresh()
    }

    override fun onStop() {
        player.release()
        super.onStop()
    }

    private inner class Actions : PodcastsActions {
        private fun position(id: String): Long {
            val s =
                podcasts.store.library.value
                    .state(id)
            return if (s.played) 0 else s.positionMs
        }

        override fun play(episode: Episode) {
            lifecycleScope.launch {
                podcasts.keep(episode.showId)
                if (podcasts.store.library.value
                        .state(episode.id)
                        .played
                ) {
                    podcasts.markPlayed(episode.id, false)
                }
                player.play(episode.id, position(episode.id))
            }
        }

        override fun togglePlay() = player.togglePlay()

        override fun playNext(episode: Episode) {
            lifecycleScope.launch {
                podcasts.keep(episode.showId)
                player.playNext(episode.id)
            }
        }

        override fun addToQueue(episode: Episode) {
            lifecycleScope.launch {
                podcasts.keep(episode.showId)
                player.addToQueue(episode.id)
            }
        }

        override fun removeFromQueue(id: String) = player.remove(id)

        override fun moveInQueue(
            from: Int,
            to: Int,
        ) = player.move(from, to)

        override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

        override fun back() = player.back()

        override fun forward() = player.forward()

        override fun setSpeed(speed: Float) = player.setSpeed(speed)

        override fun setSleep(sleep: SleepTimer.Sleep) = SleepTimer.set(sleep)

        override fun markPlayed(
            id: String,
            played: Boolean,
        ) {
            podcasts.markPlayed(id, played)
            // Played episodes leave Up Next (the one loaded too: the next takes its place).
            if (played) player.remove(id)
        }

        override fun download(id: String) {
            lifecycleScope.launch {
                podcasts.keep(id.substringBefore(':'))
                Downloads.enqueue(this@MainActivity, id, manual = true)
            }
        }

        override fun cancelDownload(id: String) = Downloads.cancel(this@MainActivity, id)

        override fun deleteDownload(id: String) {
            lifecycleScope.launch(Dispatchers.IO) { Downloads.delete(this@MainActivity, podcasts.store, id) }
        }

        override fun follow(showId: String) {
            lifecycleScope.launch { podcasts.follow(showId) }
        }

        override fun unfollow(showId: String) {
            podcasts.store.library.value.upNext
                .filter { it.startsWith("$showId:") }
                .forEach(player::remove)
            lifecycleScope.launch { podcasts.unfollow(showId) }
        }

        override fun refresh() {
            if (refreshing) return
            refreshing = true
            lifecycleScope.launch {
                try {
                    val added = podcasts.refresh()
                    RefreshWorker.autoDownload(this@MainActivity, added)
                } finally {
                    refreshing = false
                }
            }
        }

        override fun openSettings() = startActivity(Intent(this@MainActivity, SettingsActivity::class.java))

        override suspend fun search(terms: String): List<SearchResult> = Net.search(terms)

        override suspend fun openFeed(feedUrl: String): String = podcasts.open(feedUrl)

        override suspend fun openAddress(input: String): String = podcasts.openAddress(input)

        override suspend fun notes(episodeId: String): String =
            withContext(Dispatchers.IO) { podcasts.store.notes(episodeId.substringBefore(':'))[episodeId].orEmpty() }
    }

    private companion object {
        const val AUTO_REFRESH_MS = 60 * 60 * 1000L
    }
}
