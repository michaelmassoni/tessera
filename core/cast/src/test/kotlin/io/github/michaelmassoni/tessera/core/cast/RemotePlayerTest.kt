package io.github.michaelmassoni.tessera.core.cast

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** [RemotePlayer] keeps the queue on the phone and tells the device one song at a time. */
@RunWith(AndroidJUnit4::class)
class RemotePlayerTest {
    private class FakeLink : RemoteLink {
        override val deviceName = "Fake TV"
        override var mediaSessionId: Long? = null
        override var events: RemoteLink.Events? = null
        val calls = mutableListOf<String>()

        override fun load(
            item: MediaItem,
            positionMs: Long,
            autoplay: Boolean,
            speed: Float,
        ) {
            calls += "load ${item.mediaId} $positionMs $autoplay"
        }

        override fun play() {
            calls += "play"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun seek(positionMs: Long) {
            calls += "seek $positionMs"
        }

        override fun setSpeed(speed: Float) {
            calls += "speed $speed"
        }

        override fun stopMedia() {
            calls += "stop"
        }

        override fun setVolume(level: Float) {
            calls += "volume $level"
        }

        override fun setMuted(muted: Boolean) = Unit

        /** The device starts playing what was loaded last, as a new media session. */
        fun playing(positionMs: Long = 0) {
            mediaSessionId = (mediaSessionId ?: 0) + 1
            events?.onMedia(MediaStatus(mediaSessionId!!, MediaStatus.PlayerState.PLAYING, positionMs, 200_000, null))
        }

        fun finished() =
            events?.onMedia(MediaStatus(mediaSessionId!!, MediaStatus.PlayerState.IDLE, 0, null, MediaStatus.IdleReason.FINISHED))
    }

    private val link = FakeLink()
    private val player = RemotePlayer(link, Looper.getMainLooper())

    private fun item(id: String) =
        MediaItem
            .Builder()
            .setMediaId(id)
            .setUri("content://songs/$id")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(id).build())
            .build()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun queue(vararg ids: String) {
        player.setMediaItems(ids.map(::item), 0, 0)
        player.play()
        idle()
        link.playing()
        idle()
    }

    @Test
    fun `plays the queue a song at a time and ends after the last`() {
        queue("a", "b")
        assertEquals("load a 0 false", link.calls.first()) // set before play, as the app does
        assertEquals(Player.STATE_READY, player.playbackState)
        assertTrue(player.isPlaying)
        assertEquals(200_000L, player.duration)

        link.finished()
        idle()
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals("load b 0 true", link.calls.last())
        link.playing()
        link.finished()
        idle()
        assertEquals(Player.STATE_ENDED, player.playbackState)
    }

    @Test
    fun `repeat all goes round, repeat one plays the song again`() {
        queue("a", "b")
        player.repeatMode = Player.REPEAT_MODE_ONE
        link.finished()
        idle()
        assertEquals("load a 0 true", link.calls.last())
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.seekTo(1, 0)
        idle()
        link.playing()
        link.finished()
        idle()
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals("load a 0 true", link.calls.last())
    }

    @Test
    fun `pause, play and seek go to the device`() {
        queue("a")
        player.pause()
        player.play()
        player.seekTo(60_000)
        idle()
        assertEquals(listOf("pause", "play", "seek 60000"), link.calls.drop(1).take(3))
    }

    @Test
    fun `next loads the next song, and pausing on the TV shows here`() {
        queue("a", "b", "c")
        player.seekToNextMediaItem()
        idle()
        assertEquals("load b 0 true", link.calls.last())
        link.playing()
        link.events?.onMedia(MediaStatus(link.mediaSessionId!!, MediaStatus.PlayerState.PAUSED, 5_000, 200_000, null))
        idle()
        assertTrue(!player.playWhenReady)
        assertEquals(5_000L, player.currentPosition)
    }

    @Test
    fun `statuses about the song before a load are ignored`() {
        queue("a", "b")
        player.seekToNextMediaItem()
        idle()
        // The old song's session reports its interruption after the new load was sent.
        link.events?.onMedia(MediaStatus(link.mediaSessionId!!, MediaStatus.PlayerState.IDLE, 0, null, MediaStatus.IdleReason.INTERRUPTED))
        link.events?.onMedia(MediaStatus(link.mediaSessionId!!, MediaStatus.PlayerState.IDLE, 0, null, MediaStatus.IdleReason.FINISHED))
        idle()
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(Player.STATE_BUFFERING, player.playbackState)
    }

    @Test
    fun `queue edits keep the song playing, and removing it plays the next`() {
        queue("a", "b", "c")
        player.seekTo(1, 0)
        idle()
        link.playing()
        idle()
        val loads = link.calls.count { it.startsWith("load") }
        player.moveMediaItem(0, 2)
        player.addMediaItems(0, listOf(item("x")))
        idle()
        assertEquals("b", player.currentMediaItem?.mediaId)
        assertEquals(loads, link.calls.count { it.startsWith("load") })
        player.removeMediaItem(player.currentMediaItemIndex)
        idle()
        assertEquals("c", player.currentMediaItem?.mediaId)
        assertEquals("load c 0 true", link.calls.last())
        // The last song removed while it plays: the queue ends.
        player.removeMediaItem(player.mediaItemCount - 1)
        player.seekTo(player.mediaItemCount - 1, 0)
        idle()
        player.removeMediaItem(player.currentMediaItemIndex)
        idle()
        assertEquals(Player.STATE_ENDED, player.playbackState)
        assertEquals("stop", link.calls.last())
    }

    @Test
    fun `a song the device can't play is an error, and play tries again`() {
        queue("a")
        link.events?.onLoadFailed()
        idle()
        assertNotNull(player.playerError)
        player.prepare()
        idle()
        assertEquals("load a 0 true", link.calls.last())
    }

    @Test
    fun `volume is the device's`() {
        queue("a")
        link.events?.onVolume(0.5f, false)
        idle()
        assertEquals(10, player.deviceVolume)
        player.increaseDeviceVolume(0)
        idle()
        assertEquals("volume 0.55", link.calls.last())
        assertEquals(20, player.deviceInfo.maxVolume)
    }

    @Test
    fun `speed goes to the device, capped at what it can do, and kept as asked`() {
        queue("a")
        player.setPlaybackSpeed(3f)
        idle()
        assertEquals("speed 2.0", link.calls.last())
        assertEquals(3f, player.playbackParameters.speed)
        player.seekToNextMediaItem()
    }

    @Test
    fun `an item ending on the device is an automatic transition`() {
        queue("a", "b")
        val reasons = mutableListOf<Int>()
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    reasons += reason
                }
            },
        )
        link.finished()
        idle()
        assertEquals(listOf(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO), reasons)
    }
}
