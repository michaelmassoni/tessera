package io.github.michaelmassoni.tessera.podcasts

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Where the episode playing is. Separate from [PlayerState] so the ticking only redraws progress. */
data class Progress(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** What the UI shows about playback. */
data class PlayerState(
    /** The episode loaded (playing or paused), or null. */
    val episodeId: String? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val failed: Boolean = false,
    val speed: Float = 1f,
    /** Up Next as the player has it: the episode loaded first. */
    val queue: List<String> = emptyList(),
)

/**
 * Connects to [PlaybackService] with a MediaController and mirrors its state. Queue changes go
 * through here; the service saves them to the library.
 */
class PlayerConnection(
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private var controller: MediaController? = null
    private var ticker: Job? = null

    /** Commands given before the controller connected, run once it has. */
    private val pending = mutableListOf<(MediaController) -> Unit>()

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
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        ticker?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending += block
    }

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).build()

    private fun Player.indexOf(id: String) = (0 until mediaItemCount).firstOrNull { getMediaItemAt(it).mediaId == id }

    /** Plays [id] now, from [positionMs]: moved (or added) to the front of Up Next. */
    fun play(
        id: String,
        positionMs: Long,
    ) = withController { c ->
        if (c.currentMediaItem?.mediaId == id) {
            if (c.playerError != null) c.prepare()
            c.play()
            return@withController
        }
        when (val at = c.indexOf(id)) {
            null -> c.addMediaItem(0, item(id))
            else -> c.moveMediaItem(at, 0)
        }
        c.seekTo(0, positionMs)
        c.prepare()
        c.play()
    }

    /** Right after the episode playing (or loaded, paused, when nothing is). */
    fun playNext(id: String) =
        withController { c ->
            c.indexOf(id)?.let { if (it == 0) return@withController else c.removeMediaItem(it) }
            if (c.mediaItemCount == 0) {
                c.addMediaItem(item(id))
                c.prepare()
            } else {
                c.addMediaItem(1, item(id))
            }
        }

    fun addToQueue(id: String) =
        withController { c ->
            if (c.indexOf(id) != null) return@withController
            c.addMediaItem(item(id))
            if (c.mediaItemCount == 1) c.prepare()
        }

    fun remove(id: String) = withController { c -> c.indexOf(id)?.let(c::removeMediaItem) }

    fun move(
        from: Int,
        to: Int,
    ) = withController { c -> c.moveMediaItem(from, to) }

    fun togglePlay() =
        withController { c ->
            if (c.isPlaying) {
                c.pause()
            } else {
                // After an error (offline), playing again tries again.
                if (c.playerError != null) c.prepare()
                c.play()
            }
        }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun back() = withController { it.seekBack() }

    fun forward() = withController { it.seekForward() }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    private fun publish(player: Player) {
        _state.value =
            PlayerState(
                episodeId = player.currentMediaItem?.mediaId,
                isPlaying = player.isPlaying,
                buffering = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady,
                failed = player.playerError != null,
                speed = player.playbackParameters.speed,
                queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId },
            )
        _progress.value = Progress(player.currentPosition, player.duration.coerceAtLeast(0))
        // Position changes continuously while playing; poll it twice a second for the progress UI.
        ticker?.cancel()
        if (player.isPlaying) {
            ticker =
                scope.launch {
                    while (isActive) {
                        delay(500)
                        _progress.value = Progress(player.currentPosition, player.duration.coerceAtLeast(0))
                    }
                }
        }
    }
}
