package io.github.michaelmassoni.tessera.music.cast

import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** What [RemotePlayer] needs from the cast connection. Calls and events are on the main thread. */
interface RemoteLink {
    val deviceName: String

    /** Loads [item] on the device: resolved, served and sent off the main thread. */
    fun load(
        item: MediaItem,
        positionMs: Long,
        autoplay: Boolean,
    )

    fun play()

    fun pause()

    fun seek(positionMs: Long)

    fun stopMedia()

    fun setVolume(level: Float)

    fun setMuted(muted: Boolean)

    /** The media session id now on the device, so statuses about an older song can be told apart. */
    val mediaSessionId: Long?

    var events: Events?

    interface Events {
        fun onMedia(status: MediaStatus?)

        fun onVolume(
            level: Float?,
            muted: Boolean?,
        )

        /** The song couldn't be opened here or played there. */
        fun onLoadFailed()
    }
}

/**
 * The Media3 player for casting: it keeps the queue, the index, repeat and shuffle on the phone,
 * as ExoPlayer does, and plays one song at a time on the cast device, sending the next when one
 * ends. The media session switches to it while casting, so the app, notification, lock screen,
 * Bluetooth and Android Auto all control the device. Volume is the device's (remote playback).
 */
@OptIn(UnstableApi::class)
class RemotePlayer(
    val link: RemoteLink,
    looper: Looper,
) : SimpleBasePlayer(looper),
    RemoteLink.Events {
    private class Entry(
        val uid: Any,
        val item: MediaItem,
    )

    private val items = mutableListOf<Entry>()
    private var index = 0
    private var playWhenReady = false
    private var playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
    private var playbackState = Player.STATE_IDLE
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var shuffle = false
    private var playlistMetadata = MediaMetadata.EMPTY
    private var error: PlaybackException? = null
    private val durations = mutableMapOf<Any, Long>()

    // Where the device's song is, as of [anchoredAt]; it moves on from there while playing.
    private var positionMs = 0L
    private var anchoredAt = 0L
    private var advancing = false

    private var volume = 0.5f
    private var muted = false

    /** The entry the device has (or is loading), and the device's media session before that load. */
    private var loaded: Any? = null
    private var awaiting = false
    private var staleSession: Long? = null

    private var nextUid = 0L

    init {
        link.events = this
    }

    /** Takes over from the phone's player: the same queue, song, position and play state. */
    fun start(
        from: Player,
        startVolume: Float?,
    ) {
        items.clear()
        for (i in 0 until from.mediaItemCount) items += Entry(nextUid++, from.getMediaItemAt(i))
        index = from.currentMediaItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        repeatMode = from.repeatMode
        shuffle = from.shuffleModeEnabled
        playlistMetadata = from.playlistMetadata
        playWhenReady = from.playWhenReady
        startVolume?.let { volume = it }
        if (items.isNotEmpty()) {
            loadCurrent(from.currentPosition.coerceAtLeast(0), playWhenReady)
        } else {
            playbackState = Player.STATE_ENDED
        }
        invalidateState()
    }

    /** Hands back to the phone's player: the queue as it is now, paused where the device was. */
    fun handBack(to: Player) {
        if (items.isEmpty()) {
            to.clearMediaItems()
        } else {
            to.setMediaItems(items.map { it.item }, index, currentPositionMs())
        }
        to.repeatMode = repeatMode
        to.shuffleModeEnabled = shuffle
        to.playlistMetadata = playlistMetadata
        to.playWhenReady = false
        to.prepare()
    }

    override fun getState(): State {
        val playlist =
            items.map { e ->
                MediaItemData
                    .Builder(e.uid)
                    .setMediaItem(e.item)
                    .setDurationUs(durations[e.uid]?.let { it * 1000 } ?: C.TIME_UNSET)
                    .setIsSeekable(true)
                    .build()
            }
        val builder =
            State
                .Builder()
                .setAvailableCommands(COMMANDS)
                .setPlaylist(playlist)
                .setPlaylistMetadata(playlistMetadata)
                .setPlayWhenReady(playWhenReady, playWhenReadyReason)
                .setPlaybackState(if (items.isEmpty() && playbackState != Player.STATE_IDLE) Player.STATE_ENDED else playbackState)
                .setPlayerError(error)
                .setRepeatMode(repeatMode)
                .setShuffleModeEnabled(shuffle)
                .setDeviceInfo(DEVICE_INFO)
                .setDeviceVolume(Math.round(volume * MAX_VOLUME))
                .setIsDeviceMuted(muted)
                .setContentPositionMs(PositionSupplier { currentPositionMs() })
        if (items.isNotEmpty()) builder.setCurrentMediaItemIndex(index)
        return builder.build()
    }

    private fun currentPositionMs(): Long {
        val moving = playbackState == Player.STATE_READY && playWhenReady && advancing
        val now = if (moving) positionMs + (SystemClock.elapsedRealtime() - anchoredAt) else positionMs
        val duration = items.getOrNull(index)?.let { durations[it.uid] }
        return if (duration != null) now.coerceAtMost(duration) else now
    }

    private fun anchor(ms: Long) {
        positionMs = ms.coerceAtLeast(0)
        anchoredAt = SystemClock.elapsedRealtime()
    }

    private fun loadCurrent(
        position: Long,
        autoplay: Boolean,
    ) {
        val entry = items.getOrNull(index) ?: return
        loaded = entry.uid
        awaiting = true
        staleSession = link.mediaSessionId
        error = null
        playbackState = Player.STATE_BUFFERING
        advancing = false
        anchor(position)
        link.load(entry.item, position, autoplay)
    }

    // The device's news.

    override fun onMedia(status: MediaStatus?) {
        if (status == null) return
        if (awaiting && status.mediaSessionId == staleSession) return // about the song before
        if (awaiting && status.playerState == MediaStatus.PlayerState.IDLE &&
            status.idleReason == MediaStatus.IdleReason.INTERRUPTED
        ) {
            return
        }
        awaiting = false
        items.getOrNull(index)?.let { e -> status.durationMs?.let { durations[e.uid] = it } }
        anchor(status.positionMs)
        when (status.playerState) {
            MediaStatus.PlayerState.PLAYING -> {
                playbackState = Player.STATE_READY
                advancing = true
                remotePlayWhenReady(true)
            }

            MediaStatus.PlayerState.PAUSED -> {
                playbackState = Player.STATE_READY
                advancing = false
                remotePlayWhenReady(false)
            }

            MediaStatus.PlayerState.BUFFERING -> {
                playbackState = Player.STATE_BUFFERING
                advancing = false
            }

            MediaStatus.PlayerState.IDLE -> {
                idle(status)
            }
        }
        invalidateState()
    }

    private fun idle(status: MediaStatus) {
        advancing = false
        loaded = null
        when (status.idleReason) {
            MediaStatus.IdleReason.FINISHED -> {
                val next = RemoteQueue.next(index, items.size, repeatMode)
                if (next == null) {
                    items.getOrNull(index)?.let { e -> durations[e.uid]?.let(::anchor) }
                    playbackState = Player.STATE_ENDED
                } else {
                    index = next
                    loadCurrent(0, true)
                }
            }

            MediaStatus.IdleReason.ERROR -> {
                failed()
            }

            // Stopped from the TV's own controls: paused here; Play loads it again where it was.
            else -> {
                playbackState = Player.STATE_READY
                remotePlayWhenReady(false)
            }
        }
    }

    private fun remotePlayWhenReady(play: Boolean) {
        if (playWhenReady == play) return
        playWhenReady = play
        playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE
    }

    private fun failed() {
        awaiting = false
        loaded = null
        advancing = false
        error = PlaybackException("The cast device couldn't play this song", null, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        playbackState = Player.STATE_IDLE
    }

    override fun onVolume(
        level: Float?,
        muted: Boolean?,
    ) {
        level?.let { volume = it }
        muted?.let { this.muted = it }
        invalidateState()
    }

    override fun onLoadFailed() {
        failed()
        invalidateState()
    }

    // What the app, the notification and the rest ask for.

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        playWhenReadyReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
        val current = items.getOrNull(index)
        when {
            current == null || playbackState == Player.STATE_ENDED -> {}

            loaded == current.uid && !awaiting -> {
                anchor(currentPositionMs())
                advancing = false
                if (playWhenReady) link.play() else link.pause()
            }

            loaded != current.uid && playWhenReady -> {
                loadCurrent(currentPositionMs(), true)
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        if (items.isNotEmpty() && (error != null || loaded == null)) loadCurrent(currentPositionMs(), playWhenReady)
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        anchor(currentPositionMs())
        link.stopMedia()
        loaded = null
        advancing = false
        playbackState = Player.STATE_IDLE
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        link.events = null
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        if (items.isEmpty()) return Futures.immediateVoidFuture()
        val target = if (mediaItemIndex == C.INDEX_UNSET) index else mediaItemIndex.coerceIn(0, items.size - 1)
        val position = if (positionMs == C.TIME_UNSET) 0 else positionMs
        val same = target == index && loaded == items[target].uid && !awaiting && playbackState != Player.STATE_ENDED
        index = target
        if (same) {
            anchor(position)
            advancing = false
            playbackState = Player.STATE_BUFFERING
            link.seek(position)
        } else {
            loadCurrent(position, playWhenReady)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        val keep = startIndex == C.INDEX_UNSET
        val position =
            if (keep) {
                currentPositionMs()
            } else if (startPositionMs == C.TIME_UNSET) {
                0
            } else {
                startPositionMs
            }
        items.clear()
        mediaItems.forEach { items += Entry(nextUid++, it) }
        durations.clear()
        index =
            if (keep) index.coerceIn(0, (items.size - 1).coerceAtLeast(0)) else startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        if (items.isEmpty()) {
            link.stopMedia()
            loaded = null
            playbackState = Player.STATE_ENDED
        } else {
            loadCurrent(position, playWhenReady)
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleAddMediaItems(
        index: Int,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<*> {
        val wasEmpty = items.isEmpty()
        val at = index.coerceIn(0, items.size)
        items.addAll(at, mediaItems.map { Entry(nextUid++, it) })
        if (wasEmpty) {
            this.index = 0
            loadCurrent(0, playWhenReady)
        } else if (at <= this.index) {
            this.index += mediaItems.size
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRemoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
    ): ListenableFuture<*> {
        val from = fromIndex.coerceIn(0, items.size)
        val to = toIndex.coerceIn(from, items.size)
        val removingCurrent = index in from until to
        repeat(to - from) { durations.remove(items.removeAt(from).uid) }
        when {
            items.isEmpty() -> {
                index = 0
                link.stopMedia()
                loaded = null
                playbackState = Player.STATE_ENDED
            }

            !removingCurrent -> {
                if (index >= to) index -= to - from
            }

            from < items.size -> {
                index = from
                loadCurrent(0, playWhenReady)
            }

            repeatMode == Player.REPEAT_MODE_ALL -> {
                index = 0
                loadCurrent(0, playWhenReady)
            }

            else -> {
                index = items.size - 1
                link.stopMedia()
                loaded = null
                playbackState = Player.STATE_ENDED
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleMoveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int,
    ): ListenableFuture<*> {
        val current = items.getOrNull(index)
        val moved = items.subList(fromIndex, toIndex).toList()
        repeat(toIndex - fromIndex) { items.removeAt(fromIndex) }
        items.addAll(newIndex.coerceIn(0, items.size), moved)
        current?.let { c -> index = items.indexOf(c).coerceAtLeast(0) }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        this.repeatMode = repeatMode
        return Futures.immediateVoidFuture()
    }

    // The service reorders the queue itself when shuffle changes (PlaybackService.reorderForShuffle).
    override fun handleSetShuffleModeEnabled(shuffleModeEnabled: Boolean): ListenableFuture<*> {
        shuffle = shuffleModeEnabled
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaylistMetadata(playlistMetadata: MediaMetadata): ListenableFuture<*> {
        this.playlistMetadata = playlistMetadata
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(
        deviceVolume: Int,
        flags: Int,
    ): ListenableFuture<*> {
        volume = (deviceVolume.toFloat() / MAX_VOLUME).coerceIn(0f, 1f)
        link.setVolume(volume)
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        handleSetDeviceVolume(Math.round(volume * MAX_VOLUME) + 1, flags)

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        handleSetDeviceVolume(Math.round(volume * MAX_VOLUME) - 1, flags)

    override fun handleSetDeviceMuted(
        muted: Boolean,
        flags: Int,
    ): ListenableFuture<*> {
        this.muted = muted
        link.setMuted(muted)
        return Futures.immediateVoidFuture()
    }

    private companion object {
        const val MAX_VOLUME = 20

        val DEVICE_INFO =
            DeviceInfo
                .Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
                .setMinVolume(0)
                .setMaxVolume(MAX_VOLUME)
                .build()

        val COMMANDS =
            Player.Commands
                .Builder()
                .addAll(
                    Player.COMMAND_PLAY_PAUSE,
                    Player.COMMAND_PREPARE,
                    Player.COMMAND_STOP,
                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                    Player.COMMAND_SET_REPEAT_MODE,
                    Player.COMMAND_SET_SHUFFLE_MODE,
                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_GET_TIMELINE,
                    Player.COMMAND_GET_METADATA,
                    Player.COMMAND_SET_PLAYLIST_METADATA,
                    Player.COMMAND_SET_MEDIA_ITEM,
                    Player.COMMAND_CHANGE_MEDIA_ITEMS,
                    Player.COMMAND_GET_DEVICE_VOLUME,
                    Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                    Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
                    Player.COMMAND_RELEASE,
                ).build()
    }
}
