package io.github.michaelmassoni.tessera.podcasts.engine

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/** OPML, the subscription list every podcast app imports and exports. */
object Opml {
    data class Entry(
        val feedUrl: String,
        val title: String,
    )

    /** The feeds in an OPML file (outlines with an `xmlUrl`, at any depth). Empty if it isn't OPML. */
    fun read(input: InputStream): List<Entry> {
        val entries = mutableListOf<Entry>()
        val handler =
            object : DefaultHandler() {
                override fun startElement(
                    uri: String,
                    localName: String,
                    qName: String,
                    attributes: Attributes,
                ) {
                    if (localName.ifEmpty { qName } != "outline") return
                    val url = attributes.getValue("xmlUrl") ?: attributes.getValue("xmlurl") ?: return
                    if (url.isBlank()) return
                    val title = attributes.getValue("title") ?: attributes.getValue("text") ?: url
                    entries += Entry(url.trim(), title.trim())
                }
            }
        try {
            val factory = SAXParserFactory.newInstance()
            runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            factory.newSAXParser().parse(InputSource(input), handler)
        } catch (_: SAXException) {
            // Keep what was read before the error.
        } catch (_: ParserConfigurationException) {
            return emptyList()
        }
        return entries.distinctBy { Directory.key(it.feedUrl) }
    }

    fun write(shows: List<Show>): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<opml version=\"2.0\">\n")
            append("  <head><title>Tessera Podcasts</title></head>\n")
            append("  <body>\n")
            for (show in shows) {
                val title = escape(show.title)
                append("    <outline type=\"rss\" text=\"$title\" title=\"$title\" xmlUrl=\"${escape(show.feedUrl)}\"")
                show.link?.let { append(" htmlUrl=\"${escape(it)}\"") }
                append("/>\n")
            }
            append("  </body>\n")
            append("</opml>\n")
        }

    private fun escape(s: String) =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
