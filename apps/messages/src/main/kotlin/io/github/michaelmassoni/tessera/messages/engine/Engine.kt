package io.github.michaelmassoni.tessera.messages.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One message in a conversation. Times are epoch ms. */
data class Message(
    val id: String,
    val body: String,
    val date: Long,
    val outgoing: Boolean,
    val failed: Boolean = false,
    /** A sent SMS the other phone confirmed receiving (delivery reports on; docs/messages.md). */
    val delivered: Boolean = false,
    val pending: Boolean = false,
    /** For MMS with media that can't be shown inline: a placeholder is shown alongside any text. */
    val hasMedia: Boolean = false,
    /** Pictures and other media in an MMS, shown in the bubble. */
    val attachments: List<Attachment> = emptyList(),
    /** An MMS that arrived as a notification but hasn't been downloaded (or failed to). */
    val downloadable: Boolean = false,
    /** Who sent an incoming message in a group conversation. */
    val sender: String? = null,
    /**
     * What the conversation list keys the bubble by. Usually [id]; a sent message keeps the key of
     * the bubble shown while it was sending, so it doesn't disappear and reappear.
     */
    val key: String = id,
)

/** A media part of an MMS: a content URI and its MIME type. */
data class Attachment(
    val uri: String,
    val mime: String,
) {
    val isImage get() = mime.startsWith("image/")
}

data class Conversation(
    val threadId: Long,
    val addresses: List<String>,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
)

object SmsLength {
    // GSM 03.38 basic set and the escaped extension characters (which count double).
    private const val BASIC =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
    private const val EXTENDED = "^{}\\[~]|€\u000c"

    data class Info(
        val segments: Int,
        val remaining: Int,
        val unicode: Boolean,
    )

    fun info(text: String): Info {
        val unicode = text.any { it !in BASIC && it !in EXTENDED }
        return if (unicode) {
            // UCS-2 counts UTF-16 code units.
            val units = text.length
            val single = 70
            val multi = 67
            segmentsFor(units, single, multi, true)
        } else {
            val septets = text.count() + text.count { it in EXTENDED }
            segmentsFor(septets, 160, 153, false)
        }
    }

    private fun segmentsFor(
        size: Int,
        single: Int,
        multi: Int,
        unicode: Boolean,
    ): Info {
        if (size <= single) return Info(1, single - size, unicode)
        val segments = (size + multi - 1) / multi
        return Info(segments, segments * multi - size, unicode)
    }
}

object Addresses {
    /** Digits only, keeping a leading +, for matching the same number written differently. */
    fun normalize(address: String): String {
        val trimmed = address.trim()
        val digits = trimmed.filter { it.isDigit() }
        return if (trimmed.startsWith("+")) "+$digits" else digits
    }

    /**
     * The key two numbers share when [same] says they match: the last 8 digits, or all of them when
     * shorter. Used to match many numbers at once.
     */
    fun key(address: String): String {
        val digits = normalize(address).trimStart('+')
        return if (digits.length >= 8) digits.takeLast(8) else digits
    }

    /**
     * Contact names for [addresses], from every contact number at once ([contacts] as number to
     * name), instead of one lookup per address.
     */
    fun names(
        addresses: Collection<String>,
        contacts: List<Pair<String, String>>,
    ): Map<String, String> {
        val byKey = HashMap<String, String>()
        contacts.forEach { (number, name) -> if (name.isNotBlank()) byKey.putIfAbsent(key(number), name) }
        return addresses.mapNotNull { a -> key(a).takeIf { it.isNotEmpty() }?.let(byKey::get)?.let { a to it } }.toMap()
    }

    /** True if [a] and [b] look like the same number (same last 8 digits, or equal when short). */
    fun same(
        a: String,
        b: String,
    ): Boolean {
        val x = normalize(a).trimStart('+')
        val y = normalize(b).trimStart('+')
        if (x.isEmpty() || y.isEmpty()) return a.equals(b, ignoreCase = true)
        return if (x.length >= 8 && y.length >= 8) x.takeLast(8) == y.takeLast(8) else x == y
    }
}

/** A row in the conversation: a day separator or a message (with whether it ends a group). */
sealed interface Row {
    data class Day(
        val day: LocalDate,
    ) : Row

    data class Bubble(
        val message: Message,
        /** Last of a run from the same side within [Grouping.GAP_MILLIS]: shows the time. */
        val lastInGroup: Boolean,
        /** First of such a run: in a group conversation, shows who sent it. */
        val firstInGroup: Boolean = true,
    ) : Row
}

object Grouping {
    const val GAP_MILLIS = 3 * 60_000L

    /** Two consecutive messages share a run: same side, same sender, same day, close in time. */
    private fun sameRun(
        a: Message,
        b: Message,
        zone: ZoneId,
    ): Boolean =
        a.outgoing == b.outgoing && a.sender == b.sender && b.date - a.date <= GAP_MILLIS &&
            Instant.ofEpochMilli(a.date).atZone(zone).toLocalDate() == Instant.ofEpochMilli(b.date).atZone(zone).toLocalDate()

    /** Rows oldest first, with day separators. */
    fun rows(
        messages: List<Message>,
        zone: ZoneId,
    ): List<Row> {
        val sorted = messages.sortedBy { it.date }
        val rows = mutableListOf<Row>()
        var lastDay: LocalDate? = null
        sorted.forEachIndexed { i, m ->
            val day = Instant.ofEpochMilli(m.date).atZone(zone).toLocalDate()
            if (day != lastDay) {
                rows += Row.Day(day)
                lastDay = day
            }
            val next = sorted.getOrNull(i + 1)
            val previous = sorted.getOrNull(i - 1)
            val last = next == null || !sameRun(m, next, zone)
            val first = previous == null || !sameRun(previous, m, zone)
            rows += Row.Bubble(m, last, first)
        }
        return rows
    }
}
