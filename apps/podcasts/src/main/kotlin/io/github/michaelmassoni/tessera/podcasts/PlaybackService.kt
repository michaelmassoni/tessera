package io.github.michaelmassoni.tessera.podcasts

import android.content.Intent
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.michaelmassoni.tessera.podcasts.data.Downloads
import io.github.michaelmassoni.tessera.podcasts.data.Net
import io.github.michaelmassoni.tessera.podcasts.data.Podcasts
import io.github.michaelmassoni.tessera.podcasts.engine.Episodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background playback with Media3: the media notification, lock screen and Bluetooth controls,
 * audio focus and "becoming noisy". The player's playlist is Up Next (the episode playing first);
 * it's saved to the library whenever it changes, with each episode's position.
 *
 * Controllers send episodes by id only; [Callback.onAddMediaItems] builds the playable item (the
 * downloaded file, or the stream).
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val podcasts by lazy { applicationContext.podcasts }
    private val store get() = podcasts.store

    /** The episode playing and where it was last seen, for saving it after it's gone. */
    private var currentId: String? = null
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val http =
            DefaultHttpDataSource
                .Factory()
                .setUserAgent(Net.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
        val player =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setSeekBackIncrementMs(BACK_MS)
                .setSeekForwardIncrementMs(FORWARD_MS)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
                .build()
        player.setPlaybackSpeed(store.library.value.speed)
        session =
            MediaSession
                .Builder(this, player)
                .setCallback(Callback())
                .setMediaButtonPreferences(
                    listOf(
                        CommandButton
                            .Builder(CommandButton.ICON_SKIP_BACK_10)
                            .setDisplayName(getString(R.string.back_10))
                            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
                            .setSlots(CommandButton.SLOT_BACK)
                            .build(),
                        CommandButton
                            .Builder(CommandButton.ICON_SKIP_FORWARD_30)
                            .setDisplayName(getString(R.string.forward_30))
                            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                            .setSlots(CommandButton.SLOT_FORWARD)
                            .build(),
                    ),
                ).build()
        player.addListener(Listener(player))
        scope.launch { restore(player) }
        scope.launch { sleepTimer(player) }
    }

    /** Puts Up Next back, paused, once the episodes have loaded (unless a controller already set a queue). */
    private suspend fun restore(player: Player) {
        store.loaded.first { it }
        if (player.mediaItemCount > 0) return
        val lib = store.library.value
        val items = lib.upNext.mapNotNull(::mediaItem)
        if (items.isEmpty()) return
        val first = items.first().mediaId
        player.setMediaItems(items, 0, lib.state(first).positionMs)
        player.prepare()
        currentId = first
    }

    private suspend fun sleepTimer(player: Player) {
        SleepTimer.state.collect { sleep ->
            if (sleep is SleepTimer.Sleep.At) {
                delay((sleep.endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                if (SleepTimer.state.value == sleep) {
                    player.pause()
                    SleepTimer.set(SleepTimer.Sleep.Off)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Stop when the app is swiped away while nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        player?.let(::savePosition)
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            savePosition(player)
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    /** The playable item for an episode: its download if there is one, else the stream. */
    private fun mediaItem(episodeId: String): MediaItem? {
        val episode = store.episode(episodeId) ?: return null
        val show = podcasts.show(episode.showId)
        val uri = Downloads.playable(this, store, episodeId)?.toUri() ?: episode.audioUrl.toUri()
        return MediaItem
            .Builder()
            .setMediaId(episode.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(episode.title)
                    .setArtist(show?.title)
                    .setAlbumTitle(show?.title)
                    .setArtworkUri((episode.artworkUrl ?: show?.artworkUrl)?.toUri())
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build(),
            ).build()
    }

    private fun savePosition(player: Player) {
        val id = currentId ?: return
        if (player.currentMediaItem?.mediaId == id) {
            lastPositionMs = player.currentPosition
            if (player.duration > 0) lastDurationMs = player.duration
        }
        savePosition(id, lastPositionMs, lastDurationMs)
    }

    private fun savePosition(
        id: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        store.update { lib ->
            lib.withState(id) { s ->
                if (s.played) {
                    s.copy(durationMs = durationMs.takeIf { it > 0 } ?: s.durationMs)
                } else {
                    s.copy(
                        positionMs = positionMs,
                        durationMs =
                            durationMs.takeIf { it > 0 } ?: s.durationMs,
                    )
                }
            }
        }
    }

    private fun saveUpNext(player: Player) {
        val ids = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        store.update { Podcasts.pruned(it.copy(upNext = ids)) }
    }

    private inner class Listener(
        private val player: Player,
    ) : Player.Listener {
        private var ticker: Job? = null

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            val previous = currentId
            if (previous != null && previous != mediaItem?.mediaId) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    finished(previous)
                } else {
                    savePosition(previous, lastPositionMs, lastDurationMs)
                    // Left in its last seconds (outro, ads): that counts as listened.
                    if (Episodes.nearEnd(lastPositionMs, lastDurationMs)) podcasts.markPlayed(previous, true)
                }
            }
            currentId = mediaItem?.mediaId
            lastPositionMs = 0
            lastDurationMs = 0
            val id = currentId ?: return
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                val state = store.library.value.state(id)
                if (!state.played && state.positionMs > 0) player.seekTo(state.positionMs)
            }
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && SleepTimer.state.value == SleepTimer.Sleep.EndOfEpisode) {
                player.pause()
                SleepTimer.set(SleepTimer.Sleep.Off)
            }
        }

        /** An episode played to the end: marked played and taken out of Up Next. */
        private fun finished(id: String) {
            podcasts.markPlayed(id, true)
            val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == id } ?: return
            // Removed after this callback: Media3 doesn't allow changing the playlist inside it.
            scope.launch {
                if (index < player.mediaItemCount && player.getMediaItemAt(index).mediaId == id) player.removeMediaItem(index)
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY && player.duration > 0) lastDurationMs = player.duration
            if (state == Player.STATE_ENDED) {
                val id = player.currentMediaItem?.mediaId ?: return
                if (SleepTimer.state.value == SleepTimer.Sleep.EndOfEpisode) SleepTimer.set(SleepTimer.Sleep.Off)
                currentId = null
                finished(id)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            savePosition(player)
            ticker?.cancel()
            if (!isPlaying) return
            ticker =
                scope.launch {
                    var seconds = 0
                    while (isActive) {
                        delay(1_000)
                        if (player.currentMediaItem?.mediaId == currentId) {
                            lastPositionMs = player.currentPosition
                            if (player.duration > 0) lastDurationMs = player.duration
                        }
                        if (++seconds % SAVE_EVERY_S == 0) savePosition(player)
                    }
                }
        }

        override fun onEvents(
            player: Player,
            events: Player.Events,
        ) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) saveUpNext(player)
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) && player.currentMediaItem?.mediaId == currentId) {
                lastPositionMs = player.currentPosition
            }
            if (events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                val speed = player.playbackParameters.speed
                store.update { it.copy(speed = speed) }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private inner class Callback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.mapNotNull { mediaItem(it.mediaId) }.toMutableList())

        /** Android's "resume playback" media controls, after the app was closed. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val lib = store.library.value
            val items = lib.upNext.mapNotNull(::mediaItem)
            if (items.isEmpty()) return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            return Futures.immediateFuture(MediaItemsWithStartPosition(items, 0, lib.state(items.first().mediaId).positionMs))
        }
    }

    private companion object {
        const val BACK_MS = 10_000L
        const val FORWARD_MS = 30_000L
        const val SAVE_EVERY_S = 10
    }
}
