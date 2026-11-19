package io.github.michaelmassoni.tessera.messages.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PduTest {
    private fun bytes(vararg parts: Any): ByteArray =
        parts
            .flatMap { p ->
                when (p) {
                    is Int -> listOf(p.toByte())
                    is String -> (p.toByteArray() + 0).toList()
                    is ByteArray -> p.toList()
                    else -> error(p)
                }
            }.toByteArray()

    @Test
    fun parsesANotification() {
        val from = "+61412345678/TYPE=PLMN"
        val pdu =
            bytes(
                0x8C,
                0x82,
                0x98,
                "T1",
                0x8D,
                0x92,
                0x89,
                from.length + 2,
                0x80,
                from,
                0x8A,
                0x80,
                0x8E,
                0x02,
                0x1F,
                0x40,
                0x88,
                0x05,
                0x81,
                0x03,
                0x01,
                0x51,
                0x80,
                0x83,
                "http://mmsc.example/abc",
            )
        assertEquals(Pdu.NOTIFICATION_IND, Pdu.type(pdu))
        val n = Pdu.parseNotification(pdu)
        assertEquals("T1", n.transactionId)
        assertEquals("http://mmsc.example/abc", n.contentLocation)
        assertEquals("+61412345678", n.from)
        assertEquals(8000L, n.size)
        assertEquals(86400L, n.expiry)
        assertEquals("personal", n.messageClass)
    }

    @Test
    fun notifyResponseBytes() {
        assertArrayEquals(bytes(0x8C, 0x83, 0x98, "T1", 0x8D, 0x92, 0x95, 0x81, 0x91, 0x81), Pdu.notifyResponse("T1"))
        assertArrayEquals(bytes(0x8C, 0x85, 0x98, "T2", 0x8D, 0x92, 0x91, 0x81), Pdu.acknowledge("T2"))
    }

    @Test
    fun sendRequestRoundTripsThroughTheRetrieveParser() {
        val jpeg = ByteArray(300) { (it * 7).toByte() }
        val pdu =
            Pdu.sendRequest(
                recipients = listOf("+61 412 345 678", "0298765432"),
                parts =
                    listOf(
                        Pdu.Part("text/plain", "Café at 7? ☕".toByteArray(), charset = Pdu.UTF_8),
                        Pdu.Part("image/jpeg", jpeg, name = "photo.jpg"),
                    ),
                transactionId = "abc123",
                subject = "Dinner ✨",
            )
        assertEquals(Pdu.SEND_REQ, Pdu.type(pdu))
        // The same layout as a retrieve-conf apart from the type byte.
        val asRetrieved = pdu.copyOf().also { it[1] = Pdu.RETRIEVE_CONF.toByte() }
        val r = Pdu.parseRetrieved(asRetrieved)
        assertEquals("abc123", r.transactionId)
        assertEquals(listOf("+61412345678", "0298765432"), r.to)
        assertNull(r.from)
        assertEquals("Dinner ✨", r.subject)
        assertEquals("application/vnd.wap.multipart.related", r.contentType)
        assertEquals(listOf("application/smil", "text/plain", "image/jpeg"), r.parts.map { it.contentType })
        assertEquals("Café at 7? ☕", r.parts[1].text())
        assertArrayEquals(jpeg, r.parts[2].data)
        assertEquals("<smil>", r.parts[0].contentId)
        assertEquals("part_1.jpg", r.parts[2].contentLocation)
        val smil = r.parts[0].text()
        assertEquals(true, smil.contains("<img src=\"part_1.jpg\" region=\"Image\"/>"))
        assertEquals(true, smil.contains("<text src=\"text_0.txt\" region=\"Text\"/>"))
    }

    @Test
    fun largePartsUseMultiByteLengths() {
        val big = ByteArray(200_000) { it.toByte() }
        val pdu = Pdu.sendRequest(listOf("123"), listOf(Pdu.Part("image/png", big)), "t")
        val r = Pdu.parseRetrieved(pdu.copyOf().also { it[1] = Pdu.RETRIEVE_CONF.toByte() })
        assertArrayEquals(big, r.parts.last().data)
    }

    @Test
    fun parsesEncodedStringWithCharset() {
        // Subject in UTF-8 with an explicit charset: length, 0xEA (106), text.
        val subject = "Grüße".toByteArray(Charsets.UTF_8)
        val pdu =
            bytes(
                0x8C,
                0x84,
                0x96,
                subject.size + 2,
                0xEA,
                subject,
                0x00,
                0x84,
                0x83,
                "hello".toByteArray(),
            )
        val r = Pdu.parseRetrieved(pdu)
        assertEquals("Grüße", r.subject)
        assertEquals("text/plain", r.contentType)
        assertEquals("hello", r.parts.single().text())
    }

    @Test
    fun truncatedPdusAreRejected() {
        val pdu = Pdu.sendRequest(listOf("123"), listOf(Pdu.Part("image/png", ByteArray(500))), "t")
        val cut = pdu.copyOf(pdu.size - 100).also { it[1] = Pdu.RETRIEVE_CONF.toByte() }
        assertThrows(Pdu.Malformed::class.java) { Pdu.parseRetrieved(cut) }
        assertNull(Pdu.type(byteArrayOf(0x01)))
    }

    @Test
    fun cleansAddresses() {
        assertEquals("+61412345678", Pdu.cleanAddress("+61412345678/TYPE=PLMN"))
        assertEquals("a@b.com", Pdu.cleanAddress("a@b.com"))
    }
}
