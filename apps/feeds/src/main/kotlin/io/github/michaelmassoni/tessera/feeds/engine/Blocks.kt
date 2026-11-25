package io.github.michaelmassoni.tessera.feeds.engine

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Inline styling over a range of a [RichText]. */
sealed interface Style {
    data object Bold : Style

    data object Italic : Style

    data object Code : Style

    data object Strike : Style

    data class Link(
        val url: String,
    ) : Style
}

data class Span(
    val start: Int,
    val end: Int,
    val style: Style,
)

/** Text with inline styles: what a paragraph or heading holds. */
data class RichText(
    val text: String,
    val spans: List<Span> = emptyList(),
)

/** A block of an article, as the article screen lays them out. */
sealed interface Block {
    data class Paragraph(
        val text: RichText,
    ) : Block

    data class Heading(
        val level: Int,
        val text: RichText,
    ) : Block

    data class Image(
        val url: String,
        val alt: String,
        /** Where tapping it goes, when the image is a link. */
        val link: String? = null,
    ) : Block

    /** A figure's caption, shown small under its image. */
    data class Caption(
        val text: RichText,
    ) : Block

    data class Quote(
        val blocks: List<Block>,
    ) : Block

    data class Bullets(
        val ordered: Boolean,
        val items: List<List<Block>>,
    ) : Block

    data class Code(
        val text: String,
    ) : Block

    /** An embedded video or frame: shown as a link out. */
    data class Embed(
        val url: String,
    ) : Block

    data object Rule : Block
}

/**
 * An article's HTML as [Block]s, to show natively rather than in a WebView. Keeps structure and
 * inline styles, drops scripts, styles and tracking pixels. Pure: unit tested.
 */
object Blocks {
    private val skip = setOf("script", "style", "noscript", "template", "head", "svg", "button", "form", "input", "select", "textarea")
    private val containers =
        setOf(
            "p",
            "div",
            "section",
            "article",
            "header",
            "footer",
            "main",
            "aside",
            "nav",
            "center",
            "figure",
            "details",
            "summary",
            "dl",
            "dt",
            "dd",
            "table",
            "thead",
            "tbody",
            "tfoot",
            "tr",
            "td",
            "th",
            "caption",
            "address",
            "body",
            "html",
            "picture",
        )

    fun of(
        html: String,
        base: String,
    ): List<Block> {
        val doc = Jsoup.parse(html, base)
        val b = Builder()
        b.children(doc.body())
        b.flush()
        return b.out
    }

    private class Builder {
        val out = mutableListOf<Block>()
        private val text = StringBuilder()
        private val spans = mutableListOf<Span>()
        private val links = ArrayDeque<String>()
        private var heading = 0
        private var caption = false

        fun flush() {
            // Styles still open (a paragraph inside <b>) cover what's here, and carry on in the next block.
            open.forEach { (style, at) -> if (text.length > at) spans += Span(at, text.length, style) }
            // Trim, keeping spans in step.
            val raw = text.toString()
            val start = raw.indexOfFirst { !it.isWhitespace() }
            if (start < 0) {
                reset()
                return
            }
            val end = raw.indexOfLast { !it.isWhitespace() } + 1
            val rich =
                RichText(
                    raw.substring(start, end),
                    spans
                        .mapNotNull { s ->
                            val a = (s.start - start).coerceIn(0, end - start)
                            val z = (s.end - start).coerceIn(0, end - start)
                            if (z > a) Span(a, z, s.style) else null
                        },
                )
            out +=
                when {
                    heading > 0 -> Block.Heading(heading, rich)
                    caption -> Block.Caption(rich)
                    else -> Block.Paragraph(rich)
                }
            reset()
        }

        private val open = mutableListOf<Pair<Style, Int>>()

        private fun reset() {
            for (i in open.indices) open[i] = open[i].first to 0
            text.clear()
            spans.clear()
        }

        fun children(e: Element) = e.childNodes().forEach { node(it) }

        private fun append(s: String) {
            // Collapse whitespace as a browser does.
            val collapsed = s.replace(Regex("[\\s ]+")) { if (it.value.contains(' ') && it.value.length == 1) " " else " " }
            if (collapsed.startsWith(" ") && (text.isEmpty() || text.last() == ' ' || text.last() == '\n')) {
                text.append(collapsed.drop(1))
            } else {
                text.append(collapsed)
            }
        }

        private fun styled(
            style: Style,
            e: Element,
        ) {
            open += style to text.length
            children(e)
            val (_, start) = open.removeAt(open.lastIndex)
            if (text.length > start) spans += Span(start, text.length, style)
        }

        private fun block(e: Element) {
            flush()
            children(e)
            flush()
        }

        private fun sub(e: Element): List<Block> {
            val inner = Builder()
            inner.children(e)
            inner.flush()
            return inner.out
        }

        private fun node(n: Node) {
            when (n) {
                is TextNode -> append(n.text())
                is Element -> element(n)
            }
        }

        private fun element(e: Element) {
            val tag = e.normalName()
            when {
                tag in skip -> {}

                tag.length == 2 && tag[0] == 'h' && tag[1] in '1'..'6' -> {
                    flush()
                    heading = tag[1] - '0'
                    children(e)
                    flush()
                    heading = 0
                }

                tag == "figcaption" -> {
                    flush()
                    caption = true
                    children(e)
                    flush()
                    caption = false
                }

                tag == "ul" || tag == "ol" -> {
                    flush()
                    val items =
                        e
                            .children()
                            .filter { it.normalName() == "li" }
                            .map { sub(it) }
                            .filter { it.isNotEmpty() }
                    if (items.isNotEmpty()) out += Block.Bullets(tag == "ol", items)
                }

                tag == "blockquote" -> {
                    flush()
                    val inner = sub(e)
                    if (inner.isNotEmpty()) out += Block.Quote(inner)
                }

                tag == "pre" -> {
                    flush()
                    val code = e.wholeText().trimEnd().trimStart('\n')
                    if (code.isNotBlank()) out += Block.Code(code)
                }

                tag == "hr" -> {
                    flush()
                    out += Block.Rule
                }

                tag == "br" -> {
                    text.append('\n')
                }

                tag == "img" -> {
                    image(e)
                }

                tag == "iframe" || tag == "video" || tag == "embed" -> {
                    val src = e.absUrl("src").ifEmpty { e.selectFirst("source[src]")?.absUrl("src").orEmpty() }
                    if (src.startsWith("http")) {
                        flush()
                        out += Block.Embed(src)
                    }
                }

                tag == "a" -> {
                    val href = e.absUrl("href").ifEmpty { e.attr("href") }
                    if (href.isBlank() || href.startsWith("javascript:")) {
                        children(e)
                    } else {
                        links.addLast(href)
                        styled(Style.Link(href), e)
                        links.removeLast()
                    }
                }

                tag == "b" || tag == "strong" -> {
                    styled(Style.Bold, e)
                }

                tag == "i" || tag == "em" || tag == "cite" || tag == "dfn" -> {
                    styled(Style.Italic, e)
                }

                tag == "code" || tag == "kbd" || tag == "samp" || tag == "tt" -> {
                    styled(Style.Code, e)
                }

                tag == "s" || tag == "del" || tag == "strike" -> {
                    styled(Style.Strike, e)
                }

                tag == "li" -> {
                    block(e)
                }

                tag in containers -> {
                    block(e)
                }

                else -> {
                    children(e)
                }
            }
        }

        private fun image(e: Element) {
            val src =
                e.absUrl("src").ifEmpty {
                    // Lazy-loading markup keeps the real address elsewhere.
                    e.absUrl("data-src").ifEmpty { srcset(e) }
                }
            if (!src.startsWith("http")) return
            val w = e.attr("width").toIntOrNull()
            val h = e.attr("height").toIntOrNull()
            if ((w != null && w <= 1) || (h != null && h <= 1)) return
            flush()
            out += Block.Image(src, e.attr("alt").trim(), links.lastOrNull())
        }

        private fun srcset(e: Element): String {
            val set = e.attr("srcset").ifEmpty { e.attr("data-srcset") }
            val first =
                set
                    .split(',')
                    .firstOrNull()
                    ?.trim()
                    ?.substringBefore(' ')
                    .orEmpty()
            return if (first.isEmpty()) "" else FeedParser.absolute(first, e.baseUri())
        }
    }
}
