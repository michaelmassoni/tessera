package io.github.michaelmassoni.tessera.feeds.engine

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

/** Text from HTML, for titles and the list's snippets. Pure: unit tested. */
object Text {
    /** [html] (or text with entities) as plain text on one line. */
    fun plain(html: String): String {
        if (html.isBlank()) return ""
        if (!html.contains('<') && !html.contains('&')) return html.trim().replace(Regex("\\s+"), " ")
        return Jsoup.parse(html).text().trim()
    }

    /** The first [max] characters of the article's text, cut at a word. */
    fun snippet(
        html: String,
        max: Int = 240,
    ): String {
        val text = plain(html)
        if (text.length <= max) return text
        val cut = text.take(max)
        val space = cut.lastIndexOf(' ')
        return (if (space > max / 2) cut.take(space) else cut).trimEnd(',', '.', ';', ':', ' ') + "…"
    }

    /** The first image in [html] that isn't a tracking pixel, made absolute against [base]. */
    fun firstImage(
        html: String,
        base: String,
    ): String? {
        if (!html.contains("<img", ignoreCase = true)) return null
        val doc: Document = Jsoup.parse(html, base)
        return doc
            .select("img[src]")
            .firstOrNull { img ->
                val w = img.attr("width").toIntOrNull()
                val h = img.attr("height").toIntOrNull()
                (w == null || w > 1) && (h == null || h > 1) && !img.attr("src").startsWith("data:")
            }?.absUrl("src")
            ?.takeIf { it.isNotEmpty() }
    }

    fun escapeHtml(text: String): String =
        org.jsoup.nodes.Entities
            .escape(text)
}
