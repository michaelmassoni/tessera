package io.github.michaelmassoni.tessera.core.cast

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.Socket

class MediaServerTest {
    private val loopback = InetAddress.getLoopbackAddress()
    private val server = MediaServer(loopback, loopback)
    private val song = ByteArray(1000) { it.toByte() }

    private val known = MediaSource { position -> Opened(ByteArrayInputStream(song, position.toInt(), song.size), song.size - position) }

    @After
    fun close() = server.close()

    /** Sends [request] and returns the status line, headers and body. */
    private fun fetch(request: String): Triple<String, Map<String, String>, ByteArray> {
        Socket(loopback, server.port).use { s ->
            s.getOutputStream().write(request.toByteArray())
            val all = s.getInputStream().readBytes()
            val split =
                (0 until all.size - 3).first {
                    all[it] == '\r'.code.toByte() && all[it + 1] == '\n'.code.toByte() &&
                        all[it + 2] == '\r'.code.toByte()
                }
            val head = String(all, 0, split).split("\r\n")
            val headers = head.drop(1).associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
            return Triple(head[0], headers, all.copyOfRange(split + 4, all.size))
        }
    }

    private fun path(url: String) = url.substringAfter(server.port.toString())

    @Test
    fun `serves the whole song with its type and length`() {
        val url = server.publish("audio/flac", known)
        assertTrue(url.startsWith("http://127.0.0.1:${server.port}/"))
        val (status, headers, body) = fetch("GET ${path(url)} HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals("HTTP/1.1 200 OK", status)
        assertEquals("audio/flac", headers["content-type"])
        assertEquals("1000", headers["content-length"])
        assertEquals("bytes", headers["accept-ranges"])
        assertArrayEquals(song, body)
    }

    @Test
    fun `serves a byte range for seeking`() {
        val url = server.publish("audio/flac", known)
        val (status, headers, body) = fetch("GET ${path(url)} HTTP/1.1\r\nRange: bytes=900-\r\n\r\n")
        assertEquals("HTTP/1.1 206 Partial Content", status)
        assertEquals("bytes 900-999/1000", headers["content-range"])
        assertArrayEquals(song.copyOfRange(900, 1000), body)
        val (_, h2, b2) = fetch("GET ${path(url)} HTTP/1.1\r\nRange: bytes=10-19\r\n\r\n")
        assertEquals("bytes 10-19/1000", h2["content-range"])
        assertArrayEquals(song.copyOfRange(10, 20), b2)
    }

    @Test
    fun `head sends only the headers`() {
        val url = server.publish("audio/flac", known)
        val (status, headers, body) = fetch("HEAD ${path(url)} HTTP/1.1\r\n\r\n")
        assertEquals("HTTP/1.1 200 OK", status)
        assertEquals("1000", headers["content-length"])
        assertEquals(0, body.size)
    }

    @Test
    fun `a stream of unknown length is sent whole, without ranges`() {
        val url = server.publish("audio/mpeg") { Opened(ByteArrayInputStream(song), -1) }
        val (status, headers, body) = fetch("GET ${path(url)} HTTP/1.1\r\n\r\n")
        assertEquals("HTTP/1.1 200 OK", status)
        assertEquals(null, headers["content-length"])
        assertArrayEquals(song, body)
        assertEquals("HTTP/1.1 416 Range Not Satisfiable", fetch("GET ${path(url)} HTTP/1.1\r\nRange: bytes=50-\r\n\r\n").first)
    }

    @Test
    fun `unknown paths, failures and other methods`() {
        assertEquals("HTTP/1.1 404 Not Found", fetch("GET /nope HTTP/1.1\r\n\r\n").first)
        val broken = server.publish("audio/mpeg") { throw IOException("gone") }
        assertEquals("HTTP/1.1 404 Not Found", fetch("GET ${path(broken)} HTTP/1.1\r\n\r\n").first)
        assertEquals("HTTP/1.1 405 Method Not Allowed", fetch("POST /x HTTP/1.1\r\n\r\n").first)
    }

    @Test
    fun `only the cast device may connect`() {
        val url = server.publish("audio/flac", known)
        server.allowed = InetAddress.getByName("10.9.9.9")
        val closed =
            try {
                fetch("GET ${path(url)} HTTP/1.1\r\n\r\n")
                false
            } catch (_: Exception) {
                true // no response at all
            }
        assertTrue(closed)
    }
}
