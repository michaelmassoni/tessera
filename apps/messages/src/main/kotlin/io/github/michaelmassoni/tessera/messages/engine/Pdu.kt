package io.github.michaelmassoni.tessera.messages.engine

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * MMS PDUs (OMA-MMS-ENC 1.2, with WSP encodings from WAP-230). Android hands a default SMS app
 * raw PDUs: an m-notification-ind when a picture message arrives, then the downloaded
 * m-retrieve-conf. To send, the app builds an m-send-req; after downloading, it acknowledges with
 * an m-notifyresp-ind or m-acknowledge-ind. Android has no public API for any of this.
 */
object Pdu {
    // Message types (X-Mms-Message-Type values).
    const val SEND_REQ = 0x80
    const val NOTIFICATION_IND = 0x82
    const val NOTIFYRESP_IND = 0x83
    const val RETRIEVE_CONF = 0x84
    const val ACKNOWLEDGE_IND = 0x85

    const val STATUS_RETRIEVED = 0x81
    const val STATUS_DEFERRED = 0x83

    // MMS header field codes (already OR'ed with 0x80).
    private const val BCC = 0x81
    private const val CC = 0x82
    private const val CONTENT_LOCATION = 0x83
    private const val CONTENT_TYPE = 0x84
    private const val DATE = 0x85
    private const val EXPIRY = 0x88
    private const val FROM = 0x89
    private const val MESSAGE_CLASS = 0x8A
    private const val MESSAGE_ID = 0x8B
    private const val MESSAGE_TYPE = 0x8C
    private const val MMS_VERSION = 0x8D
    private const val MESSAGE_SIZE = 0x8E
    private const val REPORT_ALLOWED = 0x91
    private const val STATUS = 0x95
    private const val SUBJECT = 0x96
    private const val TO = 0x97
    private const val TRANSACTION_ID = 0x98

    // WSP part header codes.
    private const val PART_CONTENT_LOCATION = 0x8E
    private const val PART_CONTENT_ID = 0xC0

    private const val VERSION_1_2 = 0x92
    const val UTF_8 = 106

    /** An m-notification-ind: a picture message is waiting at [contentLocation]. */
    data class Notification(
        val transactionId: String,
        val contentLocation: String,
        val from: String?,
        val subject: String?,
        val size: Long,
        /** Seconds; absolute epoch seconds or relative to now, as the MMSC sent it. */
        val expiry: Long?,
        val messageClass: String?,
    )

    /** An m-retrieve-conf: the downloaded message. */
    data class Retrieved(
        val transactionId: String?,
        val messageId: String?,
        val from: String?,
        val to: List<String>,
        val cc: List<String>,
        /** Epoch seconds, or null if the MMSC didn't send one. */
        val date: Long?,
        val subject: String?,
        val contentType: String,
        val parts: List<Part>,
    )

    data class Part(
        val contentType: String,
        val data: ByteArray,
        val name: String? = null,
        val charset: Int? = null,
        val contentId: String? = null,
        val contentLocation: String? = null,
    ) {
        val isText get() = contentType.startsWith("text/") && contentType != "text/x-vcard"

        /** The part as text, decoded with its charset (UTF-8 if none or unknown). */
        fun text(): String = String(data, charsetFor(charset))

        override fun equals(other: Any?) =
            other is Part && contentType == other.contentType && data.contentEquals(other.data) && name == other.name &&
                contentId == other.contentId

        override fun hashCode() = contentType.hashCode() * 31 + data.contentHashCode()
    }

    class Malformed(
        message: String,
    ) : Exception(message)

    /** The message type of [pdu], or null if it doesn't start like an MMS PDU. */
    fun type(pdu: ByteArray): Int? = if (pdu.size >= 2 && (pdu[0].toInt() and 0xFF) == MESSAGE_TYPE) pdu[1].toInt() and 0xFF else null

    fun parseNotification(pdu: ByteArray): Notification {
        val h = Reader(pdu).headers()
        if (h.type != NOTIFICATION_IND) throw Malformed("not a notification: ${h.type}")
        return Notification(
            transactionId = h.transactionId ?: throw Malformed("no transaction id"),
            contentLocation = h.contentLocation ?: throw Malformed("no content location"),
            from = h.from,
            subject = h.subject,
            size = h.size ?: 0,
            expiry = h.expiry,
            messageClass = h.messageClass,
        )
    }

    fun parseRetrieved(pdu: ByteArray): Retrieved {
        val r = Reader(pdu)
        val h = r.headers()
        if (h.type != RETRIEVE_CONF) throw Malformed("not a retrieve-conf: ${h.type}")
        val type = h.contentType ?: throw Malformed("no content type")
        val parts =
            if (type.mime.startsWith("application/vnd.wap.multipart") || type.mime.startsWith("multipart/")) {
                r.multipart()
            } else {
                listOf(Part(type.mime, r.rest(), type.name, type.charset))
            }
        return Retrieved(h.transactionId, h.messageId, h.from, h.to, h.cc, h.date, h.subject, type.mime, parts)
    }

    /** An m-notifyresp-ind telling the MMSC the message was [status] (retrieved by default). */
    fun notifyResponse(
        transactionId: String,
        status: Int = STATUS_RETRIEVED,
    ): ByteArray =
        Writer()
            .apply {
                byte(MESSAGE_TYPE, NOTIFYRESP_IND)
                header(TRANSACTION_ID) { text(transactionId) }
                byte(MMS_VERSION, VERSION_1_2)
                byte(STATUS, status)
                byte(REPORT_ALLOWED, 0x81)
            }.bytes()

    /** An m-acknowledge-ind for a retrieve-conf that carried a transaction id. */
    fun acknowledge(transactionId: String): ByteArray =
        Writer()
            .apply {
                byte(MESSAGE_TYPE, ACKNOWLEDGE_IND)
                header(TRANSACTION_ID) { text(transactionId) }
                byte(MMS_VERSION, VERSION_1_2)
                byte(REPORT_ALLOWED, 0x81)
            }.bytes()

    /**
     * An m-send-req to [recipients] with [parts] (text and media), wrapped in a
     * multipart/related body with a generated SMIL layout, as carriers expect.
     */
    fun sendRequest(
        recipients: List<String>,
        parts: List<Part>,
        transactionId: String,
        subject: String? = null,
    ): ByteArray {
        val named =
            parts.mapIndexed { i, p ->
                val ext = extension(p.contentType)
                val location = p.contentLocation ?: "${if (p.isText) "text" else "part"}_$i.$ext"
                p.copy(contentId = p.contentId ?: "<${location.substringBeforeLast('.')}>", contentLocation = location)
            }
        val smil = Part("application/smil", smil(named).toByteArray(), charset = UTF_8, contentId = "<smil>", contentLocation = "smil.xml")
        return Writer()
            .apply {
                byte(MESSAGE_TYPE, SEND_REQ)
                header(TRANSACTION_ID) { text(transactionId) }
                byte(MMS_VERSION, VERSION_1_2)
                // From: insert-address-token; the MMSC fills in our number.
                header(FROM) { valueLength(byteArrayOf(0x81.toByte())) }
                recipients.forEach { header(TO) { encodedString(address(it)) } }
                subject?.let { s -> header(SUBJECT) { encodedString(s) } }
                header(CONTENT_TYPE) {
                    valueLength(
                        Writer()
                            .apply {
                                raw(0x80 or 0x33) // application/vnd.wap.multipart.related
                                raw(0x8A) // Start
                                text("<smil>")
                                raw(0x89) // Type
                                text("application/smil")
                            }.bytes(),
                    )
                }
                multipart(listOf(smil) + named)
            }.bytes()
    }

    /** Recipients go out as "number/TYPE=PLMN"; email addresses as they are. */
    private fun address(recipient: String): String = if ('@' in recipient) recipient else Addresses.normalize(recipient) + "/TYPE=PLMN"

    /** Strips the "/TYPE=PLMN" suffix the MMSC adds to numbers. */
    fun cleanAddress(raw: String): String = raw.substringBefore("/TYPE=")

    private fun smil(parts: List<Part>): String {
        val media =
            parts.filterNot { it.isText }.joinToString("") { p ->
                val tag =
                    when {
                        p.contentType.startsWith("image/") -> "img"
                        p.contentType.startsWith("video/") -> "video"
                        p.contentType.startsWith("audio/") -> "audio"
                        else -> "ref"
                    }
                val region = if (tag == "img" || tag == "video") " region=\"Image\"" else ""
                "<$tag src=\"${p.contentLocation}\"$region/>"
            }
        val text = parts.filter { it.isText }.joinToString("") { "<text src=\"${it.contentLocation}\" region=\"Text\"/>" }
        return "<smil><head><layout><root-layout/>" +
            "<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>" +
            "<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/>" +
            "</layout></head><body><par dur=\"5000ms\">$media$text</par></body></smil>"
    }

    private fun extension(mime: String) =
        when (mime) {
            "text/plain" -> "txt"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "video/mp4" -> "mp4"
            "video/3gpp" -> "3gp"
            "audio/amr" -> "amr"
            "text/x-vcard" -> "vcf"
            else -> "dat"
        }

    // ---- Content types (WSP well-known media, the ones MMS uses). ----

    private val MEDIA =
        mapOf(
            0x00 to "*/*",
            0x01 to "text/*",
            0x02 to "text/html",
            0x03 to "text/plain",
            0x06 to "text/x-vCalendar",
            0x07 to "text/x-vCard",
            0x0B to "multipart/*",
            0x0C to "multipart/mixed",
            0x0F to "multipart/alternative",
            0x10 to "application/*",
            0x1C to "image/*",
            0x1D to "image/gif",
            0x1E to "image/jpeg",
            0x1F to "image/tiff",
            0x20 to "image/png",
            0x21 to "image/vnd.wap.wbmp",
            0x22 to "application/vnd.wap.multipart.*",
            0x23 to "application/vnd.wap.multipart.mixed",
            0x26 to "application/vnd.wap.multipart.alternative",
            0x27 to "application/xml",
            0x28 to "text/xml",
            0x33 to "application/vnd.wap.multipart.related",
            0x3E to "application/vnd.wap.mms-message",
        )

    private val MEDIA_CODES = MEDIA.entries.associate { (k, v) -> v to k }

    private val CHARSETS =
        mapOf(
            3 to "US-ASCII",
            4 to "ISO-8859-1",
            17 to "Shift_JIS",
            38 to "EUC-KR",
            106 to "UTF-8",
            1000 to "UTF-16BE",
            1015 to "UTF-16",
            2025 to "GB2312",
            2026 to "Big5",
        )

    private fun charsetFor(mib: Int?): Charset =
        CHARSETS[mib]?.let { name -> runCatching { Charset.forName(name) }.getOrNull() } ?: Charsets.UTF_8

    private data class ContentType(
        val mime: String,
        val charset: Int? = null,
        val name: String? = null,
    )

    private class Headers {
        var type: Int? = null
        var transactionId: String? = null
        var messageId: String? = null
        var contentLocation: String? = null
        var from: String? = null
        val to = mutableListOf<String>()
        val cc = mutableListOf<String>()
        var subject: String? = null
        var date: Long? = null
        var size: Long? = null
        var expiry: Long? = null
        var messageClass: String? = null
        var contentType: ContentType? = null
    }

    // ---- Decoding ----

    private class Reader(
        private val b: ByteArray,
    ) {
        var pos = 0

        private fun u(): Int {
            if (pos >= b.size) throw Malformed("unexpected end at $pos")
            return b[pos++].toInt() and 0xFF
        }

        private fun peek(): Int = if (pos < b.size) b[pos].toInt() and 0xFF else throw Malformed("unexpected end at $pos")

        fun rest(): ByteArray = b.copyOfRange(pos, b.size).also { pos = b.size }

        fun uintvar(): Long {
            var value = 0L
            repeat(5) {
                val o = u()
                value = (value shl 7) or (o and 0x7F).toLong()
                if (o and 0x80 == 0) return value
            }
            throw Malformed("uintvar too long")
        }

        fun valueLength(): Int {
            val first = u()
            return when {
                first <= 30 -> first
                first == 31 -> uintvar().toInt()
                else -> throw Malformed("bad value-length $first")
            }
        }

        fun longInteger(): Long {
            val length = u()
            if (length > 8) throw Malformed("long-integer too long")
            var v = 0L
            repeat(length) { v = (v shl 8) or u().toLong() }
            return v
        }

        fun integer(): Long = if (peek() and 0x80 != 0) (u() and 0x7F).toLong() else longInteger()

        fun text(): String {
            if (peek() == 127) pos++
            if (peek() == '"'.code) pos++
            val start = pos
            while (pos < b.size && b[pos].toInt() != 0) pos++
            val s = String(b, start, pos - start, Charsets.UTF_8)
            if (pos < b.size) pos++
            return s
        }

        /** Encoded-string-value: plain text, or a length, a charset and text in that charset. */
        fun encodedString(): String {
            val first = peek()
            if (first > 31) return text()
            val length = valueLength()
            val end = pos + length
            val charset = integer().toInt()
            if (pos < end && b[pos].toInt() and 0xFF == 127) pos++
            var stop = pos
            while (stop < end && b[stop].toInt() != 0) stop++
            val s = String(b, pos, stop - pos, charsetFor(charset))
            pos = end
            return s
        }

        /** Skips any header value we don't need. */
        fun skipValue() {
            val first = peek()
            when {
                first <= 30 -> {
                    pos++
                    pos += first
                }

                first == 31 -> {
                    pos++
                    pos += uintvar().toInt()
                }

                first >= 128 -> {
                    pos++
                }

                else -> {
                    text()
                }
            }
        }

        fun headers(): Headers {
            val h = Headers()
            while (pos < b.size) {
                val field = u()
                if (field < 0x80) {
                    // An application header: token-text name, then a text value.
                    pos--
                    text()
                    text()
                    continue
                }
                when (field) {
                    MESSAGE_TYPE -> {
                        h.type = u()
                    }

                    TRANSACTION_ID -> {
                        h.transactionId = text()
                    }

                    MESSAGE_ID -> {
                        h.messageId = text()
                    }

                    CONTENT_LOCATION -> {
                        h.contentLocation = text()
                    }

                    SUBJECT -> {
                        h.subject = encodedString()
                    }

                    TO -> {
                        h.to += cleanAddress(encodedString())
                    }

                    CC -> {
                        h.cc += cleanAddress(encodedString())
                    }

                    BCC -> {
                        encodedString()
                    }

                    FROM -> {
                        h.from = from()
                    }

                    DATE -> {
                        h.date = longInteger()
                    }

                    MESSAGE_SIZE -> {
                        h.size = longInteger()
                    }

                    EXPIRY -> {
                        h.expiry = expiry()
                    }

                    MESSAGE_CLASS -> {
                        h.messageClass =
                            if (peek() >=
                                0x80
                            ) {
                                listOf("personal", "advertisement", "informational", "auto").getOrNull(u() - 0x80)
                            } else {
                                text()
                            }
                    }

                    MMS_VERSION -> {
                        u()
                    }

                    CONTENT_TYPE -> {
                        // Content-Type is always the last header; the body follows.
                        h.contentType = contentType()
                        return h
                    }

                    else -> {
                        skipValue()
                    }
                }
            }
            return h
        }

        private fun from(): String? {
            val length = valueLength()
            val end = pos + length
            val token = u()
            val address = if (token == 0x80 && pos < end) cleanAddress(encodedString()) else null
            pos = end
            return address
        }

        private fun expiry(): Long {
            val length = valueLength()
            val end = pos + length
            u() // absolute (0x80) or relative (0x81)
            val v = longInteger()
            pos = end
            return v
        }

        fun contentType(): ContentType {
            val first = peek()
            if (first >= 0x80) return ContentType(MEDIA[u() and 0x7F] ?: "application/octet-stream")
            if (first > 31) return ContentType(text().lowercase())
            val length = valueLength()
            val end = pos + length
            val mime =
                when {
                    peek() >= 0x80 -> MEDIA[u() and 0x7F] ?: "application/octet-stream"
                    peek() <= 30 -> MEDIA[integer().toInt()] ?: "application/octet-stream"
                    else -> text().lowercase()
                }
            var charset: Int? = null
            var name: String? = null
            while (pos < end) {
                val token = peek()
                if (token >= 0x80) {
                    pos++
                    when (token and 0x7F) {
                        // 0x80 is Any-charset: no particular one.
                        0x01 -> charset = if (peek() == 0x80) null.also { pos++ } else integer().toInt()

                        0x05, 0x17, 0x06, 0x18 -> name = text()

                        else -> if (pos < end) skipValue()
                    }
                } else {
                    // Untyped parameter: token-text name, then an integer or text value.
                    text()
                    if (pos < end) {
                        if (peek() >= 0x80 || peek() <= 30) integer() else text()
                    }
                }
            }
            pos = end
            return ContentType(mime, charset, name)
        }

        fun multipart(): List<Part> {
            val parts = mutableListOf<Part>()
            val count = uintvar().toInt()
            repeat(count) {
                val headersLength = uintvar().toInt()
                val dataLength = uintvar().toInt()
                val headersEnd = pos + headersLength
                val type = contentType()
                var contentId: String? = null
                var location: String? = null
                var name = type.name
                while (pos < headersEnd) {
                    val field = peek()
                    if (field >= 0x80) {
                        pos++
                        when (field) {
                            PART_CONTENT_ID -> contentId = text()
                            PART_CONTENT_LOCATION -> location = text()
                            else -> skipValue()
                        }
                    } else {
                        text()
                        if (pos < headersEnd) text()
                    }
                }
                pos = headersEnd
                if (pos + dataLength > b.size) throw Malformed("part runs past the end")
                val data = b.copyOfRange(pos, pos + dataLength)
                pos += dataLength
                if (name == null) name = location
                parts += Part(type.mime, data, name, type.charset, contentId, location)
            }
            return parts
        }
    }

    // ---- Encoding ----

    private class Writer {
        private val out = ByteArrayOutputStream()

        fun bytes(): ByteArray = out.toByteArray()

        fun raw(v: Int) = out.write(v)

        fun raw(bytes: ByteArray) = out.write(bytes)

        fun byte(
            field: Int,
            value: Int,
        ) {
            raw(field)
            raw(value)
        }

        fun header(
            field: Int,
            value: Writer.() -> Unit,
        ) {
            raw(field)
            value()
        }

        fun text(s: String) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            if (bytes.isNotEmpty() && bytes[0].toInt() and 0x80 != 0) raw(127)
            raw(bytes)
            raw(0)
        }

        fun uintvar(value: Long) {
            val groups = mutableListOf<Int>()
            var v = value
            do {
                groups += (v and 0x7F).toInt()
                v = v shr 7
            } while (v > 0)
            for (i in groups.indices.reversed()) raw(groups[i] or if (i > 0) 0x80 else 0)
        }

        fun valueLength(content: ByteArray) {
            if (content.size <= 30) raw(content.size) else raw(31).also { uintvar(content.size.toLong()) }
            raw(content)
        }

        /** Text, tagged as UTF-8 when it isn't plain ASCII. */
        fun encodedString(s: String) {
            if (s.all { it.code < 128 }) {
                text(s)
            } else {
                valueLength(
                    Writer()
                        .apply {
                            raw(0x80 or UTF_8)
                            text(s)
                        }.bytes(),
                )
            }
        }

        private fun partContentType(p: Part): ByteArray =
            Writer()
                .apply {
                    val code = MEDIA_CODES[p.contentType]
                    val body =
                        Writer().apply {
                            if (code != null && code < 0x80) raw(0x80 or code) else text(p.contentType)
                            if (p.charset != null) {
                                raw(0x81)
                                if (p.charset <
                                    0x80
                                ) {
                                    raw(0x80 or p.charset)
                                } else {
                                    raw(byteArrayOf(2, (p.charset shr 8).toByte(), p.charset.toByte()))
                                }
                            }
                            p.name?.let {
                                raw(0x85)
                                text(it)
                            }
                        }
                    valueLength(body.bytes())
                }.bytes()

        fun multipart(parts: List<Part>) {
            uintvar(parts.size.toLong())
            for (p in parts) {
                val headers =
                    Writer()
                        .apply {
                            raw(partContentType(p))
                            p.contentId?.let {
                                raw(PART_CONTENT_ID)
                                raw('"'.code)
                                text(it)
                            }
                            p.contentLocation?.let {
                                raw(PART_CONTENT_LOCATION)
                                text(it)
                            }
                        }.bytes()
                uintvar(headers.size.toLong())
                uintvar(p.data.size.toLong())
                raw(headers)
                raw(p.data)
            }
        }
    }
}
