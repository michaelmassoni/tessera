package io.github.michaelmassoni.tessera.core.cast

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CastProtocolTest {
    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `load describes a music track with art and a start position`() {
        val media = CastMedia("http://10.0.0.5:4000/abc", "audio/flac", "Slow Tide", "Aurora Bay", "Polar", "http://10.0.0.5:4000/art")
        val o = json(CastProtocol.load(7, media, 95_500, autoplay = true))
        assertEquals("LOAD", o["type"]!!.jsonPrimitive.content)
        assertEquals(7, o["requestId"]!!.jsonPrimitive.int)
        assertEquals(95.5, o["currentTime"]!!.jsonPrimitive.double, 0.0)
        val m = o["media"]!!.jsonObject
        assertEquals("http://10.0.0.5:4000/abc", m["contentId"]!!.jsonPrimitive.content)
        assertEquals("audio/flac", m["contentType"]!!.jsonPrimitive.content)
        assertEquals("BUFFERED", m["streamType"]!!.jsonPrimitive.content)
        val meta = m["metadata"]!!.jsonObject
        assertEquals(3, meta["metadataType"]!!.jsonPrimitive.int)
        assertEquals("Polar", meta["albumName"]!!.jsonPrimitive.content)
        assertEquals(
            "http://10.0.0.5:4000/art",
            meta["images"]!!
                .jsonArray[0]
                .jsonObject["url"]!!
                .jsonPrimitive.content,
        )
    }

    @Test
    fun `volume is clamped`() {
        val o = json(CastProtocol.setVolume(1, 1.4f))
        assertEquals(1.0, o["volume"]!!.jsonObject["level"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `reads the receiver status`() {
        val status =
            CastProtocol.parse(
                """{"type":"RECEIVER_STATUS","requestId":3,"status":{"applications":[{"appId":"CC1AD845","displayName":"Default Media Receiver",
                "sessionId":"s-1","transportId":"t-1","namespaces":[]}],"volume":{"level":0.25,"muted":false}}}""",
            ) as Incoming.Receiver
        assertEquals(3L, status.requestId)
        assertEquals(listOf(ReceiverStatus.App("CC1AD845", "s-1", "t-1")), status.status.apps)
        assertEquals(0.25f, status.status.volume)
        assertEquals(false, status.status.muted)
    }

    @Test
    fun `reads media status, finished included`() {
        val playing =
            CastProtocol.parse(
                """{"type":"MEDIA_STATUS","status":[{"mediaSessionId":2,"playerState":"PLAYING","currentTime":12.25,"media":{"duration":241.0}}],"requestId":0}""",
            ) as Incoming.Media
        assertEquals(listOf(MediaStatus(2, MediaStatus.PlayerState.PLAYING, 12_250, 241_000, null)), playing.statuses)
        val done =
            CastProtocol.parse("""{"type":"MEDIA_STATUS","status":[{"mediaSessionId":2,"playerState":"IDLE","idleReason":"FINISHED"}]}""")
                as Incoming.Media
        assertEquals(MediaStatus.IdleReason.FINISHED, done.statuses.single().idleReason)
        assertNull(done.statuses.single().durationMs)
        assertEquals(Incoming.Media(emptyList()), CastProtocol.parse("""{"type":"MEDIA_STATUS","status":[]}"""))
    }

    @Test
    fun `heartbeat, close, failures and nonsense`() {
        assertEquals(Incoming.Ping, CastProtocol.parse("""{"type":"PING"}"""))
        assertEquals(Incoming.Close, CastProtocol.parse("""{"type":"CLOSE"}"""))
        assertEquals(Incoming.Failed("LOAD_FAILED", 9), CastProtocol.parse("""{"type":"LOAD_FAILED","requestId":9}"""))
        assertEquals(Incoming.Other, CastProtocol.parse("not json"))
        assertEquals(Incoming.Other, CastProtocol.parse("""[1,2]"""))
        assertEquals(Incoming.Media(emptyList()), CastProtocol.parse("""{"type":"MEDIA_STATUS","status":"what"}"""))
    }
}
