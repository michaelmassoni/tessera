package io.github.michaelmassoni.tessera.podcasts.engine

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.net.URI
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/** A feed as read, before it becomes a [Show] and [Episode]s. */
data class ParsedFeed(
    val title: String,
    val author: String = "",
    val description: String = "",
    val artworkUrl: String? = null,
    val link: String? = null,
    /** `itunes:new-feed-url`: the feed has moved. */
    val newFeedUrl: String? = null,
    val items: List<ParsedItem> = emptyList(),
)

data class ParsedItem(
    val guid: String,
    val title: String,
    /** The show notes as HTML (or plain text). */
    val notes: String = "",
    val publishedAt: Long = 0,
    val durationMs: Long = 0,
    val audioUrl: String,
    val audioType: String? = null,
    val audioBytes: Long = 0,
    val artworkUrl: String? = null,
    val link: String? = null,
)

class NotAFeedException : Exception()

/**
 * Reads RSS 2.0 (with the iTunes tags) and Atom podcast feeds, streaming, so feeds with thousands
 * of episodes don't build a DOM. Items without audio are skipped.
 */
object FeedParser {
    private const val ITUNES = "itunes.com/dtds/podcast-1.0.dtd"
    private const val CONTENT = "purl.org/rss/1.0/modules/content/"
    private const val ATOM = "www.w3.org/2005/Atom"
    private const val MEDIA = "search.yahoo.com/mrss/"

    /** @throws NotAFeedException when [input] isn't RSS or Atom. */
    fun parse(
        input: InputStream,
        baseUrl: String,
    ): ParsedFeed {
        val handler = Handler(baseUrl)
        try {
            val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
            // No external entities or DTDs (XXE). Parsers that don't know a feature don't fetch them anyway.
            listOf(
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
            ).forEach { runCatching { factory.setFeature(it, false) } }
            factory.newSAXParser().parse(InputSource(input), handler)
        } catch (_: SAXException) {
            // A feed broken near the end still gives the items before the break.
            if (!handler.isFeed) throw NotAFeedException()
        } catch (_: ParserConfigurationException) {
            throw NotAFeedException()
        }
        if (!handler.isFeed) throw NotAFeedException()
        return handler.feed()
    }

    private fun ns(
        uri: String,
        part: String,
    ) = uri.contains(part, ignoreCase = true)

    private class Handler(
        private val baseUrl: String,
    ) : DefaultHandler() {
        var isFeed = false
        private var atom = false
        private val text = StringBuilder()
        private var depth = 0
        private var itemDepth = -1

        private var title = ""
        private var author = ""
        private var description = ""
        private var itunesSummary = ""
        private var artwork: String? = null
        private var rssImage: String? = null
        private var link: String? = null
        private var newFeedUrl: String? = null
        private val items = mutableListOf<ParsedItem>()

        private var item: ItemBuilder? = null
        private var inImage = false

        fun feed(): ParsedFeed =
            ParsedFeed(
                title = title.trim(),
                author = author.trim(),
                description = description.ifBlank { itunesSummary }.trim(),
                artworkUrl = artwork ?: rssImage,
                link = link,
                newFeedUrl = newFeedUrl,
                items = items,
            )

        override fun startElement(
            uri: String,
            localName: String,
            qName: String,
            attributes: Attributes,
        ) {
            depth++
            text.setLength(0)
            val name = localName.ifEmpty { qName.substringAfter(':') }
            if (depth == 1) {
                isFeed = name == "rss" || name == "RDF" || (name == "feed" && ns(uri, ATOM))
                atom = name == "feed"
            }
            val current = item
            when {
                (name == "item" && !atom) || (name == "entry" && atom) -> {
                    item = ItemBuilder()
                    itemDepth = depth
                }

                current != null -> {
                    current.start(uri, name, attributes)
                }

                name == "image" && ns(uri, ITUNES) -> {
                    attributes.getValue("href")?.let { artwork = resolve(it) }
                }

                name == "image" && uri.isEmpty() -> {
                    inImage = true
                }

                name == "link" && atom && attributes.getValue("rel").let { it == null || it == "alternate" } -> {
                    link = attributes.getValue("href")?.let(::resolve)
                }
            }
        }

        override fun endElement(
            uri: String,
            localName: String,
            qName: String,
        ) {
            val name = localName.ifEmpty { qName.substringAfter(':') }
            val value = text.toString().trim()
            val current = item
            if (current != null && depth == itemDepth) {
                current.build()?.let(items::add)
                item = null
                itemDepth = -1
            } else if (current != null) {
                current.end(uri, name, value)
            } else if (inImage) {
                if (name == "url" && value.isNotEmpty()) rssImage = resolve(value)
                if (name == "image") inImage = false
            } else if ((!atom && depth == 3) || (atom && depth == 2)) {
                // Channel-level elements (rss > channel > x, or feed > x).
                when {
                    name == "title" && (uri.isEmpty() || ns(uri, ATOM)) -> title = value
                    name == "author" && ns(uri, ITUNES) -> author = value
                    name == "description" && uri.isEmpty() -> description = value
                    (name == "subtitle" && atom) -> description = value
                    name == "summary" && ns(uri, ITUNES) -> itunesSummary = value
                    name == "link" && uri.isEmpty() && value.isNotEmpty() -> link = value
                    name == "new-feed-url" && ns(uri, ITUNES) && value.isNotEmpty() -> newFeedUrl = value
                }
            } else if (atom && depth == 3 && name == "name" && author.isEmpty()) {
                author = value
            }
            text.setLength(0)
            depth--
        }

        override fun characters(
            ch: CharArray,
            start: Int,
            length: Int,
        ) {
            text.appendRange(ch, start, start + length)
        }

        fun resolve(url: String): String =
            runCatching {
                URI(baseUrl).resolve(url.trim().replace(" ", "%20")).toString()
            }.getOrDefault(url.trim())

        private inner class ItemBuilder {
            var guid = ""
            var title = ""
            var description = ""
            var encoded = ""
            var summary = ""
            var date = 0L
            var duration = 0L
            var url: String? = null
            var type: String? = null
            var bytes = 0L
            var mediaUrl: String? = null
            var mediaType: String? = null
            var image: String? = null
            var link: String? = null

            fun start(
                uri: String,
                name: String,
                attributes: Attributes,
            ) {
                when {
                    name == "enclosure" && url == null -> {
                        enclosure(attributes.getValue("url"), attributes.getValue("type"), attributes)
                    }

                    // Atom: <link rel="enclosure" href=… type=… length=…>.
                    name == "link" && atom && attributes.getValue("rel") == "enclosure" && url == null -> {
                        enclosure(attributes.getValue("href"), attributes.getValue("type"), attributes)
                    }

                    name == "link" && atom && attributes.getValue("rel").let { it == null || it == "alternate" } -> {
                        link = attributes.getValue("href")?.let(::resolve)
                    }

                    name == "content" && ns(uri, MEDIA) && mediaUrl == null &&
                        (attributes.getValue("type")?.startsWith("audio/") == true || attributes.getValue("medium") == "audio") -> {
                        mediaUrl = attributes.getValue("url")?.let(::resolve)
                        mediaType = attributes.getValue("type")
                    }

                    name == "image" && ns(uri, ITUNES) -> {
                        image = attributes.getValue("href")?.let(::resolve)
                    }
                }
            }

            private fun enclosure(
                href: String?,
                mime: String?,
                attributes: Attributes,
            ) {
                if (href.isNullOrBlank()) return
                // Video and other enclosures play as audio too, but a document isn't an episode.
                if (mime != null && !(mime.startsWith("audio/") || mime.startsWith("video/") || mime == "application/octet-stream")) {
                    return
                }
                url = resolve(href)
                type = mime
                bytes = attributes.getValue("length")?.trim()?.toLongOrNull() ?: 0
            }

            fun end(
                uri: String,
                name: String,
                value: String,
            ) {
                when {
                    name == "guid" || (name == "id" && atom) -> {
                        guid = value
                    }

                    name == "title" && (uri.isEmpty() || ns(uri, ATOM)) -> {
                        title = value
                    }

                    name == "description" && uri.isEmpty() -> {
                        description = value
                    }

                    name == "summary" && ns(uri, ITUNES) -> {
                        summary = value
                    }

                    (name == "summary" || name == "content") && atom && ns(uri, ATOM) -> {
                        if (value.length > encoded.length) encoded = value
                    }

                    name == "encoded" && ns(uri, CONTENT) -> {
                        encoded = value
                    }

                    name == "pubDate" || (name == "published" && atom) || (name == "updated" && atom && date == 0L) -> {
                        date = Dates.parse(value)
                    }

                    name == "duration" && ns(uri, ITUNES) -> {
                        duration = Durations.parse(value)
                    }

                    name == "link" && uri.isEmpty() && value.isNotEmpty() -> {
                        link = value
                    }
                }
            }

            fun build(): ParsedItem? {
                val audio = url ?: mediaUrl ?: return null
                return ParsedItem(
                    guid = guid.ifEmpty { audio },
                    title = title.ifEmpty { "" },
                    notes = listOf(encoded, description, summary).maxBy { it.length },
                    publishedAt = date,
                    durationMs = duration,
                    audioUrl = audio,
                    audioType = type ?: mediaType,
                    audioBytes = bytes,
                    artworkUrl = image,
                    link = link,
                )
            }
        }
    }
}
