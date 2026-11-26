package io.github.michaelmassoni.tessera.core.cast

/**
 * The type of a song or cover from its first bytes, for the cast device (which picks its player
 * by type). Files on the phone and server songs, transcoded or not, all arrive as bytes here, so
 * this is more reliable than a name or what the server claims.
 */
object MediaType {
    const val HEAD = 12

    fun sniff(head: ByteArray): String? {
        fun at(
            offset: Int,
            text: String,
        ) = head.size >= offset + text.length && text.indices.all { head[offset + it] == text[it].code.toByte() }

        fun byte(i: Int) = if (i < head.size) head[i].toInt() and 0xff else -1
        return when {
            at(0, "ID3") -> "audio/mpeg"

            byte(0) == 0xff && byte(1) and 0xf6 == 0xf0 -> "audio/aac"

            // ADTS
            byte(0) == 0xff && byte(1) and 0xe0 == 0xe0 -> "audio/mpeg"

            at(0, "fLaC") -> "audio/flac"

            at(0, "OggS") -> "audio/ogg"

            at(4, "ftyp") -> "audio/mp4"

            at(0, "RIFF") && at(8, "WAVE") -> "audio/wav"

            byte(0) == 0x1a && byte(1) == 0x45 && byte(2) == 0xdf && byte(3) == 0xa3 -> "audio/webm"

            byte(0) == 0xff && byte(1) == 0xd8 -> "image/jpeg"

            at(1, "PNG") -> "image/png"

            at(0, "RIFF") && at(8, "WEBP") -> "image/webp"

            else -> null
        }
    }
}
