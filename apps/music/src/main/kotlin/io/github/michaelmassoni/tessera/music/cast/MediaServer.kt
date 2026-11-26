package io.github.michaelmassoni.tessera.music.cast

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet6Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom

/** Something the server can send, opened at a byte offset. */
fun interface MediaSource {
    fun open(position: Long): Opened
}

/** An open source: its bytes from the requested position, and how many there are (-1 if unknown). */
class Opened(
    val input: InputStream,
    val length: Long,
) : Closeable by input

/** The part of a resource a `Range` header asks for. */
data class ByteRange(
    val start: Long,
    /** Inclusive; null means to the end. */
    val end: Long?,
) {
    companion object {
        /**
         * A single `bytes=a-b` or `bytes=a-` range; null when absent or not understood (several
         * ranges, or the last n bytes, which cast devices don't ask for), so the whole thing is sent.
         */
        fun parse(header: String?): ByteRange? {
            val spec = header?.trim() ?: return null
            if (!spec.startsWith("bytes=")) return null
            val r = spec.removePrefix("bytes=")
            if (',' in r) return null
            val dash = r.indexOf('-').takeIf { it > 0 } ?: return null
            val start = r.substring(0, dash).trim().toLongOrNull() ?: return null
            val b = r.substring(dash + 1).trim()
            val end = if (b.isEmpty()) null else b.toLongOrNull() ?: return null
            return if (start < 0 || (end != null && end < start)) null else ByteRange(start, end)
        }
    }
}

/**
 * The tiny web server a cast device fetches songs and covers from while casting. Each thing
 * published gets a random path; only [allowed] (the cast device) may connect; GET and HEAD with
 * byte ranges, one request per connection. Runs on its own threads until [close].
 */
class MediaServer(
    bind: InetAddress,
    @Volatile var allowed: InetAddress,
) : Closeable {
    private class Entry(
        val type: String,
        val source: MediaSource,
    )

    private val server = ServerSocket(0, BACKLOG, bind)
    private val host =
        bind.hostAddress
            .orEmpty()
            .substringBefore('%')
            .let { if (bind is Inet6Address) "[$it]" else it }
    private val random = SecureRandom()

    // The last few songs and covers: the one playing, the one before (a seek back) and a little slack.
    private val entries =
        object : LinkedHashMap<String, Entry>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > KEEP
        }

    val port: Int get() = server.localPort

    init {
        Thread(::acceptLoop, "cast-http").apply { isDaemon = true }.start()
    }

    /** Makes [source] available and returns its address. */
    fun publish(
        type: String,
        source: MediaSource,
    ): String {
        val token = ByteArray(TOKEN_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        synchronized(entries) { entries[token] = Entry(type, source) }
        return "http://$host:$port/$token"
    }

    override fun close() {
        try {
            server.close()
        } catch (_: IOException) {
        }
        synchronized(entries) { entries.clear() }
    }

    private fun acceptLoop() {
        while (!server.isClosed) {
            val client =
                try {
                    server.accept()
                } catch (_: IOException) {
                    return
                }
            if (client.inetAddress != allowed) {
                client.close()
                continue
            }
            Thread({ serve(client) }, "cast-http-request").apply { isDaemon = true }.start()
        }
    }

    private fun serve(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = READ_TIMEOUT_MS
                val input = BufferedInputStream(socket.getInputStream())
                val out = socket.getOutputStream()
                val request = readLine(input) ?: return
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: return
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                    if (headers.size > MAX_HEADERS) return
                }
                val parts = request.split(' ')
                if (parts.size < 2) return respond(out, 400, "Bad Request")
                val method = parts[0]
                val token = parts[1].removePrefix("/").substringBefore('?')
                if (method == "OPTIONS") return respond(out, 204, "No Content")
                if (method != "GET" && method != "HEAD") return respond(out, 405, "Method Not Allowed")
                val entry = synchronized(entries) { entries[token] } ?: return respond(out, 404, "Not Found")
                send(out, entry, headers["range"], head = method == "HEAD")
            } catch (_: SocketException) {
                // The device closed the connection (a seek, the next song); nothing to do.
            } catch (_: IOException) {
            }
        }
    }

    private fun send(
        out: OutputStream,
        entry: Entry,
        rangeHeader: String?,
        head: Boolean,
    ) {
        val range = ByteRange.parse(rangeHeader)
        val start = range?.start ?: 0
        val opened =
            try {
                entry.source.open(start)
            } catch (_: IOException) {
                // Past the end, or the source is gone.
                return respond(out, if (start > 0) 416 else 404, if (start > 0) "Range Not Satisfiable" else "Not Found")
            }
        opened.use { body ->
            val total = if (body.length >= 0) start + body.length else null
            if (range != null && total == null && start > 0) {
                // A part of something whose size isn't known can't be described; the device copes without seeking.
                return respond(out, 416, "Range Not Satisfiable")
            }
            val end = total?.let { (range?.end ?: (it - 1)).coerceAtMost(it - 1) }
            val length = end?.let { it - start + 1 }
            val headers =
                buildList {
                    add("Content-Type: ${entry.type}")
                    if (total != null) add("Accept-Ranges: bytes")
                    if (length != null) add("Content-Length: $length")
                    if (range != null && total != null) add("Content-Range: bytes $start-$end/$total")
                }
            if (range != null && total != null) {
                writeHead(out, 206, "Partial Content", headers)
            } else {
                writeHead(out, 200, "OK", headers)
            }
            if (head) return
            copy(body.input, out, length)
        }
    }

    private fun copy(
        input: InputStream,
        out: OutputStream,
        length: Long?,
    ) {
        val buffer = ByteArray(BUFFER)
        var left = length ?: Long.MAX_VALUE
        while (left > 0) {
            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (n < 0) break
            out.write(buffer, 0, n)
            left -= n
        }
        out.flush()
    }

    private fun respond(
        out: OutputStream,
        code: Int,
        reason: String,
        vararg extra: String,
    ) = writeHead(out, code, reason, listOf("Content-Length: 0") + extra)

    private fun writeHead(
        out: OutputStream,
        code: Int,
        reason: String,
        headers: List<String>,
    ) {
        val text =
            buildString {
                append("HTTP/1.1 $code $reason\r\n")
                // The Default Media Receiver is a web page; let it read the media whatever its player.
                append("Access-Control-Allow-Origin: *\r\n")
                append("Access-Control-Allow-Headers: Range\r\n")
                append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
                append("Connection: close\r\n")
                headers.forEach { append(it).append("\r\n") }
                append("\r\n")
            }
        out.write(text.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    /** One CRLF-terminated line, or null at end of input or when it's too long to be a request. */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return null
            if (c == '\n'.code) return sb.toString().removeSuffix("\r")
            sb.append(c.toChar())
            if (sb.length > MAX_LINE) return null
        }
    }

    private companion object {
        const val BACKLOG = 8
        const val KEEP = 16
        const val TOKEN_BYTES = 16
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_LINE = 8192
        const val MAX_HEADERS = 64
        const val BUFFER = 64 * 1024
    }
}
