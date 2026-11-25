package io.github.michaelmassoni.tessera.feeds.engine

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Entities
import org.jsoup.parser.Parser

/** Subscriptions in OPML, as readers (yarr included) import and export them. Pure: unit tested. */
object Opml {
    data class Outline(
        val title: String,
        val url: String,
        val siteUrl: String,
        /** The folder, or null at the top level. */
        val folder: String?,
    )

    /**
     * Every feed in [xml]. One level of folders: a feed nested deeper belongs to its top-level
     * folder. A feed listed twice is kept once.
     */
    fun read(xml: String): List<Outline> {
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val body = doc.getElementsByTag("body").firstOrNull() ?: return emptyList()
        val out = mutableListOf<Outline>()

        fun walk(
            e: Element,
            folder: String?,
        ) {
            e.children().filter { it.tagName().equals("outline", ignoreCase = true) }.forEach { o ->
                val url = o.attr("xmlUrl").ifEmpty { o.attr("xmlurl") }.trim()
                val title = o.attr("title").ifEmpty { o.attr("text") }.trim()
                if (url.isNotEmpty()) {
                    out += Outline(title.ifEmpty { url }, url, o.attr("htmlUrl").ifEmpty { o.attr("htmlurl") }.trim(), folder)
                } else {
                    walk(o, folder ?: title.ifEmpty { null })
                }
            }
        }
        walk(body, null)
        return out.distinctBy { it.url }
    }

    /** [feeds] as OPML 2.0, in folders as yarr writes them. */
    fun write(
        folders: List<Folder>,
        feeds: List<Feed>,
        title: String = "Tessera Feeds",
    ): String {
        fun esc(s: String) = Entities.escape(s).replace("\"", "&quot;")

        fun line(f: Feed) =
            """<outline type="rss" text="${esc(f.title)}" title="${esc(f.title)}" xmlUrl="${esc(f.url)}" htmlUrl="${esc(f.siteUrl)}"/>"""
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n")
        sb.append("  <head><title>${esc(title)}</title></head>\n  <body>\n")
        folders.forEach { folder ->
            val inside = feeds.filter { it.folderId == folder.id }
            if (inside.isEmpty()) return@forEach
            sb.append("    <outline text=\"${esc(folder.title)}\" title=\"${esc(folder.title)}\">\n")
            inside.forEach { sb.append("      ").append(line(it)).append('\n') }
            sb.append("    </outline>\n")
        }
        val folderIds = folders.map { it.id }.toSet()
        feeds.filter { it.folderId == null || it.folderId !in folderIds }.forEach { sb.append("    ").append(line(it)).append('\n') }
        sb.append("  </body>\n</opml>\n")
        return sb.toString()
    }
}

/** Finds the feeds a web page offers (`<link rel="alternate" type="application/rss+xml">`). */
object Discovery {
    data class Found(
        val url: String,
        val title: String,
    )

    fun feedsIn(
        html: String,
        pageUrl: String,
    ): List<Found> {
        val doc = Jsoup.parse(html, pageUrl)
        return doc
            .select("link[rel~=(?i)alternate][href]")
            .filter { l ->
                val type = l.attr("type").lowercase()
                type.contains("rss") || type.contains("atom") || type.contains("rdf")
            }.map { Found(it.absUrl("href").ifEmpty { it.attr("href") }, it.attr("title").trim()) }
            .filter { it.url.startsWith("http") }
            .distinctBy { it.url }
    }

    /** Whether [body] looks like a feed rather than a web page. */
    fun looksLikeFeed(body: String): Boolean {
        val head = body.take(2048).lowercase()
        return head.contains("<rss") || head.contains("<feed") || head.contains("<rdf:rdf")
    }
}
