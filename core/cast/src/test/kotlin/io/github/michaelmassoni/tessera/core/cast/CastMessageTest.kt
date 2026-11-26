package io.github.michaelmassoni.tessera.core.cast

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException

class CastMessageTest {
    private val ping = CastMessage("sender-0", "receiver-0", "urn:x-cast:com.google.cast.tp.heartbeat", """{"type":"PING"}""")

    @Test
    fun `encodes the fields in protobuf wire format`() {
        val expected =
            byteArrayOf(0x08, 0x00) + // protocol_version = CASTV2_1_0
                byteArrayOf(0x12, 8) + "sender-0".toByteArray() +
                byteArrayOf(0x1a, 10) + "receiver-0".toByteArray() +
                byteArrayOf(0x22, 39) + "urn:x-cast:com.google.cast.tp.heartbeat".toByteArray() +
                byteArrayOf(0x28, 0x00) + // payload_type = STRING
                byteArrayOf(0x32, 15) + """{"type":"PING"}""".toByteArray()
        assertArrayEquals(expected, ping.encode())
    }

    @Test
    fun `round-trips through the length-prefixed frame, long payloads included`() {
        val long = ping.copy(payload = "x".repeat(300) + "é")
        val out = ByteArrayOutputStream()
        ping.writeTo(out)
        long.writeTo(out)
        val bytes = out.toByteArray()
        assertEquals(
            ping.encode().size,
            (bytes[0].toInt() shl 24) or (bytes[1].toInt() shl 16) or (bytes[2].toInt() shl 8) or bytes[3].toInt(),
        )
        val input = DataInputStream(ByteArrayInputStream(bytes))
        assertEquals(ping, CastMessage.readFrom(input))
        assertEquals(long, CastMessage.readFrom(input))
    }

    @Test
    fun `skips fields it doesn't know, like a binary payload`() {
        val extra = ping.encode() + byteArrayOf(0x3a, 3, 1, 2, 3) + byteArrayOf(0x4d, 1, 2, 3, 4)
        assertEquals(ping, CastMessage.decode(extra))
    }

    @Test(expected = IOException::class)
    fun `rejects a truncated message`() {
        CastMessage.decode(ping.encode().copyOf(20))
    }

    @Test(expected = IOException::class)
    fun `rejects an oversized frame`() {
        CastMessage.readFrom(DataInputStream(ByteArrayInputStream(byteArrayOf(0x7f, 0, 0, 0))))
    }
}
