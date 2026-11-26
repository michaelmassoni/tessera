package io.github.michaelmassoni.tessera.music.cast

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.OutputStream

/**
 * One message on a Cast connection: the protobuf `CastMessage` with a UTF-8 (JSON) payload.
 * Encoded by hand, since the message has six fields and a protobuf library would be the only
 * use of one in the app. Binary payloads (only device authentication uses them) are skipped.
 */
data class CastMessage(
    val sourceId: String,
    val destinationId: String,
    val namespace: String,
    val payload: String,
) {
    fun encode(): ByteArray {
        val out = ByteArrayOutputStream()
        out.varintField(PROTOCOL_VERSION, 0) // CASTV2_1_0
        out.stringField(SOURCE_ID, sourceId)
        out.stringField(DESTINATION_ID, destinationId)
        out.stringField(NAMESPACE, namespace)
        out.varintField(PAYLOAD_TYPE, 0) // STRING
        out.stringField(PAYLOAD_UTF8, payload)
        return out.toByteArray()
    }

    /** Writes the message with its 4-byte big-endian length, as the connection carries it. */
    fun writeTo(out: OutputStream) {
        val bytes = encode()
        val n = bytes.size
        out.write(byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte()))
        out.write(bytes)
        out.flush()
    }

    companion object {
        private const val PROTOCOL_VERSION = 1
        private const val SOURCE_ID = 2
        private const val DESTINATION_ID = 3
        private const val NAMESPACE = 4
        private const val PAYLOAD_TYPE = 5
        private const val PAYLOAD_UTF8 = 6

        private const val VARINT = 0
        private const val FIXED64 = 1
        private const val LENGTH = 2
        private const val FIXED32 = 5

        /** The protocol's own limit on a message. */
        const val MAX_SIZE = 64 * 1024

        /** Reads one length-prefixed message; throws [EOFException] when the connection closes. */
        fun readFrom(input: DataInputStream): CastMessage {
            val n = input.readInt()
            if (n !in 0..MAX_SIZE) throw IOException("Cast message of $n bytes")
            val bytes = ByteArray(n)
            input.readFully(bytes)
            return decode(bytes)
        }

        fun decode(bytes: ByteArray): CastMessage {
            var source = ""
            var destination = ""
            var namespace = ""
            var payload = ""
            var i = 0

            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (i >= bytes.size || shift > 63) throw IOException("Bad varint")
                    val b = bytes[i++].toInt()
                    result = result or ((b and 0x7f).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            while (i < bytes.size) {
                val tag = varint()
                val field = (tag ushr 3).toInt()
                when ((tag and 7).toInt()) {
                    VARINT -> {
                        varint()
                    }

                    FIXED64 -> {
                        i += 8
                    }

                    FIXED32 -> {
                        i += 4
                    }

                    LENGTH -> {
                        val len = varint().toInt()
                        if (len < 0 || i + len > bytes.size) throw IOException("Bad length")
                        val text by lazy { String(bytes, i, len, Charsets.UTF_8) }
                        when (field) {
                            SOURCE_ID -> source = text
                            DESTINATION_ID -> destination = text
                            NAMESPACE -> namespace = text
                            PAYLOAD_UTF8 -> payload = text
                        }
                        i += len
                    }

                    else -> {
                        throw IOException("Bad wire type")
                    }
                }
            }
            if (i != bytes.size) throw IOException("Truncated message")
            return CastMessage(source, destination, namespace, payload)
        }

        private fun ByteArrayOutputStream.varint(value: Long) {
            var v = value
            while (v and 0x7fL.inv() != 0L) {
                write(((v and 0x7f) or 0x80).toInt())
                v = v ushr 7
            }
            write(v.toInt())
        }

        private fun ByteArrayOutputStream.varintField(
            field: Int,
            value: Long,
        ) {
            varint((field shl 3 or VARINT).toLong())
            varint(value)
        }

        private fun ByteArrayOutputStream.stringField(
            field: Int,
            value: String,
        ) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            varint((field shl 3 or LENGTH).toLong())
            varint(bytes.size.toLong())
            write(bytes)
        }
    }
}
