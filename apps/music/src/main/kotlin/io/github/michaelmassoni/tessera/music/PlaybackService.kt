package io.github.michaelmassoni.tessera.music

import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.michaelmassoni.tessera.music.engine.SavedItem
import io.github.michaelmassoni.tessera.music.engine.SavedQueue
import io.github.michaelmassoni.tessera.music.widget.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * Background playback. Media3 gives the media notification, lock-screen and Bluetooth controls,
 * audio focus and "becoming noisy" handling. Controllers can't pass file URIs directly (Media3
 * strips them), so the app sends each URI in the item's request metadata and [Callback]
 * restores it.
 *
 * The queue, track, position, shuffle and repeat are saved to `queue.json` whenever they change
 * (and the position on pause), and put back, paused, when the service starts again: after the
 * app was swiped away, the process died, or from Android's "resume playback" controls.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queueFile by lazy { File(filesDir, "queue.json") }

    override fun onCreate() {
        super.onCreate()
        val player =
            ExoPlayer
                .Builder(this)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .build()
        session = MediaSession.Builder(this, player).setCallback(Callback()).build()
        loadQueue()?.let { saved ->
            player.setMediaItems(saved.items.map(::mediaItem), saved.index, saved.positionMs)
            player.shuffleModeEnabled = saved.shuffle
            player.repeatMode = saved.repeatMode
            player.prepare()
        }
        player.addListener(
            object : Player.Listener {
                override fun onEvents(
                    player: Player,
                    events: Player.Events,
                ) {
                    if (events.containsAny(
                            Player.EVENT_TIMELINE_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                            Player.EVENT_REPEAT_MODE_CHANGED,
                            Player.EVENT_IS_PLAYING_CHANGED,
                            Player.EVENT_POSITION_DISCONTINUITY,
                        )
                    ) {
                        saveQueue(player)
                    }
                    if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) keepSavingPosition(player)
                    if (events.containsAny(
                            Player.EVENT_MEDIA_METADATA_CHANGED,
                            Player.EVENT_IS_PLAYING_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                            Player.EVENT_TIMELINE_CHANGED,
                        )
                    ) {
                        NowPlaying.publish(this@PlaybackService, player)
                    }
                }
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Stop when the user swipes the app away while nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        player?.let(::saveQueue)
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            saveQueue(player)
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    private var positionSaver: Job? = null

    /** While playing, saves the position every few seconds, so a killed process resumes close to where it was. */
    private fun keepSavingPosition(player: Player) {
        positionSaver?.cancel()
        if (!player.isPlaying) return
        positionSaver =
            scope.launch {
                while (isActive) {
                    delay(POSITION_SAVE_MS)
                    saveQueue(player)
                }
            }
    }

    private fun snapshot(player: Player): SavedQueue? {
        if (player.mediaItemCount == 0) return null
        val items =
            (0 until player.mediaItemCount).mapNotNull { i ->
                val item = player.getMediaItemAt(i)
                val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return@mapNotNull null
                val m = item.mediaMetadata
                SavedItem(
                    id = item.mediaId,
                    uri = uri.toString(),
                    title = m.title?.toString().orEmpty(),
                    artist = m.artist?.toString().orEmpty(),
                    album = m.albumTitle?.toString().orEmpty(),
                    albumId = m.extras?.getLong(EXTRA_ALBUM_ID) ?: 0,
                )
            }
        if (items.isEmpty()) return null
        return SavedQueue(items, player.currentMediaItemIndex, player.currentPosition, player.shuffleModeEnabled, player.repeatMode)
    }

    /** Writes the queue off the main thread; an empty queue removes the file. */
    private fun saveQueue(player: Player) {
        val text = snapshot(player)?.encode()
        scope.launch(Dispatchers.IO) {
            try {
                if (text == null) {
                    queueFile.delete()
                } else {
                    val temp = File(queueFile.path + ".tmp")
                    temp.writeText(text)
                    temp.renameTo(queueFile)
                }
            } catch (_: IOException) {
                // Not worth interrupting playback for; the previous save stays.
            }
        }
    }

    private fun loadQueue(): SavedQueue? =
        try {
            if (queueFile.exists()) SavedQueue.decode(queueFile.readText()) else null
        } catch (_: IOException) {
            null
        }

    private inner class Callback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.map { item -> item.buildUpon().setUri(item.requestMetadata.mediaUri).build() }.toMutableList(),
            )

        /**
         * Android's media controls ("resume playback" after the app was closed). Unstable in Media3
         * 1.x, but the documented way to support playback resumption.
         */
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val saved = loadQueue() ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            return Futures.immediateFuture(MediaItemsWithStartPosition(saved.items.map(::mediaItem), saved.index, saved.positionMs))
        }
    }

    companion object {
        const val EXTRA_ALBUM_ID = "album_id"
        private const val POSITION_SAVE_MS = 10_000L

        fun mediaItem(item: SavedItem): MediaItem =
            MediaItem
                .Builder()
                .setMediaId(item.id)
                .setUri(item.uri.toUri())
                .setRequestMetadata(
                    MediaItem.RequestMetadata
                        .Builder()
                        .setMediaUri(item.uri.toUri())
                        .build(),
                ).setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setExtras(Bundle().apply { putLong(EXTRA_ALBUM_ID, item.albumId) })
                        .build(),
                ).build()
    }
}
