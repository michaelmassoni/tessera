package io.github.michaelmassoni.tessera.podcasts.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale

/** Feed dates: RFC 822 as RSS has them (in all their variety), ISO 8601 as Atom has them. */
object Dates {
    private val zones =
        mapOf(
            "UT" to "+0000",
            "UTC" to "+0000",
            "GMT" to "+0000",
            "Z" to "+0000",
            "EST" to "-0500",
            "EDT" to "-0400",
            "CST" to "-0600",
            "CDT" to "-0500",
            "MST" to "-0700",
            "MDT" to "-0600",
            "PST" to "-0800",
            "PDT" to "-0700",
            "BST" to "+0100",
            "CET" to "+0100",
            "CEST" to "+0200",
            "AEST" to "+1000",
            "AEDT" to "+1100",
        )

    private val rfc822: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .parseLenient()
            .appendPattern("d MMM yyyy H:mm")
            .optionalStart()
            .appendPattern(":ss")
            .optionalEnd()
            .appendPattern(" xx")
            .toFormatter(Locale.ENGLISH)

    private val rfc822NoZone: DateTimeFormatter =
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMM yyyy")
            .optionalStart()
            .appendPattern(" H:mm")
            .optionalEnd()
            .optionalStart()
            .appendPattern(":ss")
            .optionalEnd()
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .toFormatter(Locale.ENGLISH)

    /** Milliseconds since the epoch, or 0 when [text] isn't a date we can read. */
    fun parse(text: String): Long {
        val s = text.trim()
        if (s.isEmpty()) return 0
        iso(s)?.let { return it }
        // Drop the day name ("Wed, "), full month names to three letters, and name the zone by offset.
        var t = s.substringAfter(", ", s).trim()
        t = t.replace(Regex("\\s+"), " ")
        val parts = t.split(' ').toMutableList()
        if (parts.size >= 2) parts[1] = parts[1].take(3)
        if (parts.size >= 5) {
            val zone = parts[4]
            parts[4] =
                zones[zone.uppercase()]
                    ?: if (Regex("[+-]\\d{2}:?\\d{2}").matches(zone)) zone.replace(":", "") else "+0000"
            t = parts.take(5).joinToString(" ")
            return try {
                ZonedDateTime.parse(t, rfc822).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                0
            }
        }
        return try {
            LocalDateTime.parse(parts.joinToString(" "), rfc822NoZone).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: DateTimeParseException) {
            0
        }
    }

    private fun iso(s: String): Long? {
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}").containsMatchIn(s)) return null
        return try {
            OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: DateTimeParseException) {
                try {
                    LocalDate
                        .parse(s.take(10))
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)
                        .toEpochMilli()
                } catch (_: DateTimeParseException) {
                    0
                }
            }
        }
    }
}

/** `itunes:duration`: "1:02:03", "62:03", "3723" or "3723.5" seconds. */
object Durations {
    fun parse(text: String): Long {
        val parts = text.trim().split(':')
        if (parts.size > 3) return 0
        var seconds = 0.0
        for (part in parts) {
            val n = part.trim().toDoubleOrNull() ?: return 0
            if (n < 0) return 0
            seconds = seconds * 60 + n
        }
        return (seconds * 1000).toLong()
    }
}

/** Show notes as plain text, for summaries in lists. */
object PlainText {
    private val tags = Regex("<[^>]*>")
    private val blocks = Regex("(?i)<\\s*(br|/p|/div|/li|/h\\d)\\s*/?>")
    private val entities =
        mapOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&nbsp;" to " ")
    private val numeric = Regex("&#(x?)([0-9a-fA-F]+);")

    fun of(
        html: String,
        maxLength: Int = 300,
    ): String {
        var t = html.replace(blocks, " ").replace(tags, "")
        entities.forEach { (k, v) -> t = t.replace(k, v) }
        t =
            numeric.replace(t) { m ->
                val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
                if (code != null && Character.isValidCodePoint(code)) String(Character.toChars(code)) else ""
            }
        t = t.replace(Regex("\\s+"), " ").trim()
        return if (t.length > maxLength) t.take(maxLength).trimEnd() + "…" else t
    }
}
