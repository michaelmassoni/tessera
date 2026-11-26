package io.github.michaelmassoni.tessera.music.cast

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteQueueTest {
    @Test
    fun `next song, with each repeat mode`() {
        assertEquals(2, RemoteQueue.next(1, 5, Player.REPEAT_MODE_OFF))
        assertNull(RemoteQueue.next(4, 5, Player.REPEAT_MODE_OFF))
        assertEquals(0, RemoteQueue.next(4, 5, Player.REPEAT_MODE_ALL))
        assertEquals(4, RemoteQueue.next(4, 5, Player.REPEAT_MODE_ONE))
        assertNull(RemoteQueue.next(0, 0, Player.REPEAT_MODE_ALL))
    }
}

class MediaTypeTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test
    fun `audio and images by their first bytes`() {
        assertEquals("audio/mpeg", MediaType.sniff("ID3\u0004\u0000".toByteArray()))
        assertEquals("audio/mpeg", MediaType.sniff(bytes(0xff, 0xfb, 0x90, 0x64)))
        assertEquals("audio/aac", MediaType.sniff(bytes(0xff, 0xf1, 0x50, 0x80)))
        assertEquals("audio/flac", MediaType.sniff("fLaC".toByteArray()))
        assertEquals("audio/ogg", MediaType.sniff("OggS".toByteArray()))
        assertEquals("audio/mp4", MediaType.sniff(bytes(0, 0, 0, 0x20) + "ftypM4A ".toByteArray()))
        assertEquals("audio/wav", MediaType.sniff("RIFF\u0000\u0000\u0000\u0000WAVE".toByteArray()))
        assertEquals("image/jpeg", MediaType.sniff(bytes(0xff, 0xd8, 0xff, 0xe0)))
        assertEquals("image/png", MediaType.sniff(bytes(0x89) + "PNG".toByteArray()))
        assertNull(MediaType.sniff("hello".toByteArray()))
        assertNull(MediaType.sniff(ByteArray(0)))
    }
}

class ByteRangeTest {
    @Test
    fun `ranges cast devices send, and ones it leaves alone`() {
        assertEquals(ByteRange(0, null), ByteRange.parse("bytes=0-"))
        assertEquals(ByteRange(100, 199), ByteRange.parse("bytes=100-199"))
        assertNull(ByteRange.parse(null))
        assertNull(ByteRange.parse("bytes=-500"))
        assertNull(ByteRange.parse("bytes=0-1,5-9"))
        assertNull(ByteRange.parse("bytes=9-3"))
        assertNull(ByteRange.parse("items=0-"))
    }
}
