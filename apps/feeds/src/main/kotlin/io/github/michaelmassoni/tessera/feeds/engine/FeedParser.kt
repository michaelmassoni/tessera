package io.github.michaelmassoni.tessera.feeds.engine

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/** A feed as fetched: its title, site and entries. */
data class ParsedFeed(
    val title: String,
    val siteUrl: String,
    val entries: List<ParsedEntry>,
)

data class ParsedEntry(
    /** guid / id, else the link, else a hash of title and date. */
    val id: String,
    val title: String,
    val link: String,
    val author: String,
    /** Epoch milliseconds, or 0 when the entry has no date. */
    val date: Long,
    /** HTML. */
    val content: String,
    /** An image the feed names for the entry (media:thumbnail, enclosure), if any. */
    val image: String?,
)

class NotAFeed : Exception("not a feed")

/**
 * RSS 2.0, RSS 1.0 (RDF) and Atom, read with jsoup's XML parser, which copes with the broken XML
 * many feeds serve (unescaped ampersands, stray HTML). Pure: unit tested.
 */
object FeedParser {
    /** Parses fetched bytes, honouring the encoding the XML declares. */
    fun parse(
        bytes: ByteArray,
        feedUrl: String,
    ): ParsedFeed {
        val doc = Jsoup.parse(bytes.inputStream(), null, feedUrl, Parser.xmlParser())
        return parse(doc, feedUrl)
    }

    fun parse(
        xml: String,
        feedUrl: String,
    ): ParsedFeed = parse(Jsoup.parse(xml, feedUrl, Parser.xmlParser()), feedUrl)

    private fun parse(
        doc: Document,
        feedUrl: String,
    ): ParsedFeed {
        val root = doc.children().firstOrNull { it.tagName() != "?xml" } ?: throw NotAFeed()
        return when (root.tagName().substringAfter(':').lowercase()) {
            "feed" -> atom(root, feedUrl)
            "rss", "rdf" -> rss(doc, root, feedUrl)
            else -> throw NotAFeed()
        }
    }

    private fun rss(
        doc: Document,
        root: Element,
        feedUrl: String,
    ): ParsedFeed {
        val channel = root.child("channel") ?: root
        val site = absolute(channel.childText("link"), feedUrl)
        val base = site.ifEmpty { feedUrl }
        // RSS 1.0 puts items beside the channel; 2.0 inside it.
        val items = doc.getElementsByTag("item").ifEmpty { doc.getElementsByTag("rss:item") }
        val entries =
            items.map { item ->
                val link =
                    absolute(item.childText("link").ifEmpty { item.childText("guid").takeIf { it.startsWith("http") }.orEmpty() }, base)
                val title = Text.plain(item.childText("title"))
                val date = Dates.parse(item.childText("pubDate").ifEmpty { item.childText("dc:date") })
                val content = item.childHtml("content:encoded").ifEmpty { item.childHtml("description") }
                val author = item.childText("dc:creator").ifEmpty { item.childText("author") }
                ParsedEntry(
                    id = item.childText("guid").ifEmpty { link }.ifEmpty { "${title.hashCode()}:$date" },
                    title = title,
                    link = link,
                    author = Text.plain(author),
                    date = date,
                    content = content.ifEmpty { mediaDescription(item) },
                    image = mediaImage(item, base),
                )
            }
        return ParsedFeed(Text.plain(channel.childText("title")), site, entries)
    }

    private fun atom(
        root: Element,
        feedUrl: String,
    ): ParsedFeed {
        val site = absolute(alternate(root), feedUrl)
        val entries =
            root.children().filter { it.tagName().substringAfter(':') == "entry" }.map { entry ->
                val link = absolute(alternate(entry), site.ifEmpty { feedUrl })
                val title = atomText(entry.child("title"), html = false)
                val date = Dates.parse(entry.childText("published").ifEmpty { entry.childText("updated") })
                val content = atomText(entry.child("content") ?: entry.child("summary"), html = true)
                val author = entry.child("author")?.childText("name") ?: root.child("author")?.childText("name") ?: ""
                ParsedEntry(
                    id = entry.childText("id").ifEmpty { link }.ifEmpty { "${title.hashCode()}:$date" },
                    title = title,
                    link = link,
                    author = author,
                    date = date,
                    content = content.ifEmpty { mediaDescription(entry) },
                    image = mediaImage(entry, site.ifEmpty { feedUrl }),
                )
            }
        return ParsedFeed(atomText(root.child("title"), html = false), site, entries)
    }

    /** Atom text constructs: text, html or xhtml. */
    private fun atomText(
        e: Element?,
        html: Boolean,
    ): String {
        e ?: return ""
        val type = e.attr("type").lowercase()
        val raw =
            when {
                type == "xhtml" -> (e.children().firstOrNull() ?: e).html()
                else -> e.wholeText().trim()
            }
        if (!html) return Text.plain(raw).ifEmpty { raw }
        // Plain text becomes HTML, so it shows as written.
        return if (type == "text") Text.escapeHtml(raw).replace("\n", "<br>") else raw
    }

    /** An Atom-style `<link rel="alternate" href>` (or one with no rel). */
    private fun alternate(e: Element): String {
        val links = e.children().filter { it.tagName() == "link" || it.tagName() == "atom:link" }
        val alt =
            links.firstOrNull { it.attr("rel") == "alternate" && it.attr("type").let { t -> t.isEmpty() || t.contains("html") } }
                ?: links.firstOrNull { it.attr("rel").isEmpty() && it.hasAttr("href") }
        return alt?.attr("href")?.trim().orEmpty()
    }

    private fun mediaImage(
        item: Element,
        base: String,
    ): String? {
        val candidates =
            item.getElementsByTag("media:thumbnail").map { it.attr("url") } +
                item.getElementsByTag("media:content").filter { it.attr("medium") == "image" || it.attr("type").startsWith("image/") }.map {
                    it.attr("url")
                } +
                item.getElementsByTag("enclosure").filter { it.attr("type").startsWith("image/") }.map { it.attr("url") }
        return candidates.firstOrNull { it.isNotBlank() }?.let { absolute(it, base) }
    }

    /** YouTube and others put the text in media:description. */
    private fun mediaDescription(item: Element): String =
        item
            .getElementsByTag("media:description")
            .firstOrNull()
            ?.wholeText()
            ?.trim()
            ?.let { Text.escapeHtml(it).replace("\n", "<br>") }
            .orEmpty()

    private fun Element.child(tag: String): Element? = children().firstOrNull { it.tagName() == tag }

    private fun Element.childText(tag: String): String = child(tag)?.wholeText()?.trim().orEmpty()

    /** Text that should be HTML: escaped or CDATA, or (in a broken feed) raw tags. */
    private fun Element.childHtml(tag: String): String {
        val e = child(tag) ?: return ""
        return if (e.children().isEmpty()) e.wholeText().trim() else e.html().trim()
    }

    fun absolute(
        url: String,
        base: String,
    ): String {
        if (url.isBlank()) return ""
        val u = url.trim()
        return try {
            if (u.startsWith("http://") || u.startsWith("https://")) u else URI(base).resolve(u).toString()
        } catch (_: IllegalArgumentException) {
            u
        }
    }
}

/** Dates as feeds write them: RFC 822 (RSS) and ISO 8601 (Atom, dc:date). */
object Dates {
    private val rfc822 =
        Regex("""(?:\w+,\s*)?(\d{1,2})\s+(\w{3})\w*\.?\s+(\d{2,4})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([+-]\d{2}:?\d{2}|\w+)?""")
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val zones =
        mapOf(
            "GMT" to 0,
            "UT" to 0,
            "UTC" to 0,
            "Z" to 0,
            "EST" to -5,
            "EDT" to -4,
            "CST" to -6,
            "CDT" to -5,
            "MST" to -7,
            "MDT" to -6,
            "PST" to -8,
            "PDT" to -7,
            "BST" to 1,
            "CET" to 1,
            "CEST" to 2,
            "AEST" to 10,
            "AEDT" to 11,
        )

    /** Epoch milliseconds, or 0 when [text] isn't a date this understands. */
    fun parse(text: String): Long {
        val t = text.trim()
        if (t.isEmpty()) return 0
        iso(t)?.let { return it }
        val m = rfc822.find(t) ?: return 0
        val (d, mon, y, h, min, s, zone) = m.destructured
        val month = months.indexOf(mon.lowercase()).takeIf { it >= 0 } ?: return 0
        val year = y.toInt().let { if (it < 100) it + if (it < 70) 2000 else 1900 else it }
        val offset =
            when {
                zone.isEmpty() -> {
                    ZoneOffset.UTC
                }

                zone[0] == '+' || zone[0] == '-' -> {
                    val digits = zone.drop(1).replace(":", "")
                    val sign = if (zone[0] == '-') -1 else 1
                    ZoneOffset.ofHoursMinutes(sign * digits.take(2).toInt(), sign * digits.drop(2).toInt())
                }

                else -> {
                    ZoneOffset.ofHours(zones[zone.uppercase()] ?: 0)
                }
            }
        return try {
            LocalDateTime
                .of(year, month + 1, d.toInt(), h.toInt(), min.toInt(), s.ifEmpty { "0" }.toInt())
                .toInstant(offset)
                .toEpochMilli()
        } catch (_: java.time.DateTimeException) {
            0
        }
    }

    private fun iso(t: String): Long? {
        try {
            return OffsetDateTime.parse(t).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return LocalDateTime.parse(t).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        try {
            return LocalDate
                .parse(t)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli()
        } catch (_: DateTimeParseException) {
        }
        // "2026-11-26T10:00:00+0100" (no colon in the offset).
        Regex("""(.*T[\d:.]+)([+-]\d{2})(\d{2})$""").matchEntire(t)?.let {
            return iso("${it.groupValues[1]}${it.groupValues[2]}:${it.groupValues[3]}")
        }
        return null
    }
}
