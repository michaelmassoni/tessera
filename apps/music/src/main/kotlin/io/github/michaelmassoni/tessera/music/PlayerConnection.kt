package io.github.michaelmassoni.tessera.music

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.michaelmassoni.tessera.music.engine.PlayingFrom
import io.github.michaelmassoni.tessera.music.engine.SavedItem
import io.github.michaelmassoni.tessera.music.engine.Song
import io.github.michaelmassoni.tessera.music.engine.Subsonic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueItem(
    /** The song's id (MediaItem.mediaId); a queue may hold a song twice. */
    val id: String = "",
    val title: String,
    val artist: String,
    val albumId: Long = 0,
)

/** Where the current track is. Separate from [PlaybackState] so the twice-a-second tick only redraws progress. */
data class Progress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Whether the position was moving when sampled, and when that was ([SystemClock.elapsedRealtime]). */
    val playing: Boolean = false,
    val sampledAt: Long = 0,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Where playback is at [now], carried on from the sample while playing, so the seek bar moves smoothly between samples. */
    fun at(now: Long): Progress {
        if (!playing || sampledAt <= 0 || now <= sampledAt) return this
        val moved = positionMs + (now - sampledAt)
        return copy(positionMs = if (durationMs > 0) moved.coerceAtMost(durationMs) else moved, sampledAt = now)
    }
}

/** What the UI shows about playback. */
data class PlaybackState(
    val hasMedia: Boolean = false,
    /** The song playing: its library id and where it plays from. */
    val songId: Long = 0,
    val uri: String = "",
    val durationMs: Long = 0,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: Long = 0,
    val artwork: ByteArray? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueItem> = emptyList(),
    val index: Int = 0,
    /** Where the queue came from: an album, a playlist ... */
    val from: PlayingFrom? = null,
    /** The song couldn't be played. */
    val failed: Boolean = false,
    /** ... and it's a server song: most likely offline and not cached. */
    val failedServer: Boolean = false,
) {
    override fun equals(other: Any?): Boolean =
        other is PlaybackState && hasMedia == other.hasMedia && songId == other.songId && uri == other.uri &&
            durationMs == other.durationMs && title == other.title && artist == other.artist && album == other.album &&
            albumId == other.albumId && artwork.contentEquals(other.artwork) && isPlaying == other.isPlaying &&
            shuffle == other.shuffle && repeatMode == other.repeatMode && queue == other.queue &&
            index == other.index && from == other.from && failed == other.failed && failedServer == other.failedServer

    override fun hashCode(): Int = listOf(title, artist, isPlaying, index).hashCode()
}

/**
 * Connects to [PlaybackService] with a MediaController and mirrors its state into [state].
 * Call [connect] when the UI starts and [release] when it stops.
 */
class PlayerConnection(
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val listener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) = publish(player)
        }

    fun connect(context: Context) {
        if (controller != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            publish(c)
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        ticker?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /** Replaces the queue with [songs] and starts at [index]. */
    fun play(
        songs: List<Song>,
        index: Int,
        shuffle: Boolean = false,
        from: PlayingFrom? = null,
    ) {
        val c = controller ?: return
        // Shuffle stays on for the new queue (reshuffled around the song tapped); off first, so the old
        // queue isn't what gets shuffled.
        val shuffled = shuffle || c.shuffleModeEnabled
        c.shuffleModeEnabled = false
        c.setMediaItems(songs.map(::mediaItem), if (shuffle) songs.indices.random() else index, 0)
        c.playlistMetadata = PlaybackService.playlistMetadata(from)
        c.prepare()
        c.play()
        if (shuffled) c.shuffleModeEnabled = true
    }

    /** Right after the song playing, or as the queue when nothing is queued. */
    fun playNext(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(songs, 0)
        c.addMediaItems(c.currentMediaItemIndex + 1, songs.map(::mediaItem))
    }

    fun addToQueue(songs: List<Song>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(songs, 0)
        c.addMediaItems(songs.map(::mediaItem))
    }

    fun moveQueueItem(
        from: Int,
        to: Int,
    ) = controller?.moveMediaItem(from, to)

    fun removeQueueItem(index: Int) = controller?.removeMediaItem(index)

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            // After an error (offline), playing again tries again.
            if (c.playerError != null) c.prepare()
            c.play()
        }
    }

    fun next() = controller?.seekToNext()

    fun previous() = controller?.seekToPrevious()

    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)

    fun skipTo(index: Int) = controller?.seekTo(index, 0)

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Off → all → one → off. */
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode =
            when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
    }

    private fun publish(player: Player) {
        val meta = player.mediaMetadata
        val queue =
            (0 until player.mediaItemCount).map { i ->
                val m = player.getMediaItemAt(i).mediaMetadata
                QueueItem(
                    id = player.getMediaItemAt(i).mediaId,
                    title = m.title?.toString().orEmpty(),
                    artist = m.artist?.toString().orEmpty(),
                    albumId = m.extras?.getLong(PlaybackService.EXTRA_ALBUM_ID) ?: 0,
                )
            }
        _state.value =
            PlaybackState(
                hasMedia = player.mediaItemCount > 0,
                songId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: 0,
                uri = (player.currentMediaItem?.let { it.localConfiguration?.uri ?: it.requestMetadata.mediaUri })?.toString().orEmpty(),
                durationMs = player.duration.coerceAtLeast(0),
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                album = meta.albumTitle?.toString().orEmpty(),
                albumId =
                    player.currentMediaItem
                        ?.mediaMetadata
                        ?.extras
                        ?.getLong(PlaybackService.EXTRA_ALBUM_ID) ?: 0,
                artwork = meta.artworkData,
                isPlaying = player.isPlaying,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                queue = queue,
                index = player.currentMediaItemIndex,
                from = PlaybackService.playingFrom(player.playlistMetadata),
                failed = player.playerError != null,
                failedServer =
                    player.playerError != null &&
                        player.currentMediaItem
                            ?.requestMetadata
                            ?.mediaUri
                            ?.toString()
                            ?.let(Subsonic::isServerUri) == true,
            )
        _progress.value =
            Progress(player.currentPosition, player.duration.coerceAtLeast(0), player.isPlaying, SystemClock.elapsedRealtime())
        // Position changes continuously while playing; poll it twice a second for the progress UI.
        ticker?.cancel()
        if (player.isPlaying) {
            ticker =
                scope.launch {
                    while (isActive) {
                        delay(500)
                        _progress.value =
                            Progress(
                                player.currentPosition,
                                player.duration.coerceAtLeast(0),
                                player.isPlaying,
                                SystemClock.elapsedRealtime(),
                            )
                    }
                }
        }
    }

    private fun mediaItem(song: Song): MediaItem =
        PlaybackService.mediaItem(SavedItem(song.id.toString(), song.uri, song.title, song.artist, song.album, song.albumId, song.art))
}
