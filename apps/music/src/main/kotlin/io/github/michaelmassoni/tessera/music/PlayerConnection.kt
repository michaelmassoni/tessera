package io.github.michaelmassoni.tessera.music

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.michaelmassoni.tessera.music.engine.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueItem(
    val title: String,
    val artist: String,
)

/** What the UI shows about playback. */
data class PlaybackState(
    val hasMedia: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: Long = 0,
    val artwork: ByteArray? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueItem> = emptyList(),
    val index: Int = 0,
) {
    override fun equals(other: Any?): Boolean =
        other is PlaybackState && hasMedia == other.hasMedia && title == other.title && artist == other.artist && album == other.album &&
            albumId == other.albumId && artwork.contentEquals(other.artwork) && isPlaying == other.isPlaying &&
            positionMs == other.positionMs &&
            durationMs == other.durationMs && shuffle == other.shuffle && repeatMode == other.repeatMode && queue == other.queue &&
            index == other.index

    override fun hashCode(): Int = listOf(title, artist, isPlaying, positionMs, index).hashCode()
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
    ) {
        val c = controller ?: return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(songs.map(::mediaItem), if (shuffle) (songs.indices).random() else index, 0)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
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
                QueueItem(m.title?.toString().orEmpty(), m.artist?.toString().orEmpty())
            }
        _state.value =
            PlaybackState(
                hasMedia = player.mediaItemCount > 0,
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                album = meta.albumTitle?.toString().orEmpty(),
                albumId =
                    player.currentMediaItem
                        ?.mediaMetadata
                        ?.extras
                        ?.getLong(EXTRA_ALBUM_ID) ?: 0,
                artwork = meta.artworkData,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition,
                durationMs = player.duration.coerceAtLeast(0),
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                queue = queue,
                index = player.currentMediaItemIndex,
            )
        // Position changes continuously while playing; poll it twice a second for the progress UI.
        ticker?.cancel()
        if (player.isPlaying) {
            ticker =
                scope.launch {
                    while (isActive) {
                        delay(500)
                        _state.value = _state.value.copy(positionMs = player.currentPosition, durationMs = player.duration.coerceAtLeast(0))
                    }
                }
        }
    }

    private fun mediaItem(song: Song): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(song.id.toString())
            .setRequestMetadata(
                MediaItem.RequestMetadata
                    .Builder()
                    .setMediaUri(song.uri.toUri())
                    .build(),
            ).setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setExtras(android.os.Bundle().apply { putLong(EXTRA_ALBUM_ID, song.albumId) })
                    .build(),
            ).build()

    private companion object {
        const val EXTRA_ALBUM_ID = "album_id"
    }
}
