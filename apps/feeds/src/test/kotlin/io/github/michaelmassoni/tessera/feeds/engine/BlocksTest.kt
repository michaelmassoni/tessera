package io.github.michaelmassoni.tessera.feeds.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlocksTest {
    private val base = "https://site.example/post/1"

    @Test
    fun paragraphsWithInlineStyles() {
        val blocks = Blocks.of("<p>Hello <b>bold</b> and <a href=\"/x\">a link</a>.</p><p>  Second\n  para </p>", base)
        assertEquals(2, blocks.size)
        val p = blocks[0] as Block.Paragraph
        assertEquals("Hello bold and a link.", p.text.text)
        assertEquals(
            listOf(Span(6, 10, Style.Bold), Span(15, 21, Style.Link("https://site.example/x"))),
            p.text.spans,
        )
        assertEquals("Second para", (blocks[1] as Block.Paragraph).text.text)
    }

    @Test
    fun looseTextAndBreaks() {
        val blocks = Blocks.of("Line one<br>Line two<div>Block</div>tail", base)
        assertEquals(
            listOf("Line one\nLine two", "Block", "tail"),
            blocks.map { (it as Block.Paragraph).text.text },
        )
    }

    @Test
    fun headingsListsQuotesCode() {
        val html =
            """
            <h2>Title</h2>
            <ul><li>One</li><li>Two <i>it</i></li></ul>
            <ol><li><p>First</p></li></ol>
            <blockquote><p>Quoted</p></blockquote>
            <pre><code>val x = 1
            val y = 2</code></pre>
            <hr>
            """.trimIndent()
        val blocks = Blocks.of(html, base)
        assertEquals(Block.Heading(2, RichText("Title")), blocks[0])
        val ul = blocks[1] as Block.Bullets
        assertEquals(false, ul.ordered)
        assertEquals(2, ul.items.size)
        assertEquals(RichText("Two it", listOf(Span(4, 6, Style.Italic))), (ul.items[1][0] as Block.Paragraph).text)
        assertEquals(true, (blocks[2] as Block.Bullets).ordered)
        assertEquals(Block.Quote(listOf(Block.Paragraph(RichText("Quoted")))), blocks[3])
        assertEquals(Block.Code("val x = 1\nval y = 2"), blocks[4])
        assertEquals(Block.Rule, blocks[5])
    }

    @Test
    fun imagesFiguresAndPixels() {
        val html =
            """
            <p>Before <a href="https://big.example/"><img src="/pic.jpg" alt="A pic"></a> after</p>
            <figure><img data-src="https://cdn.example/lazy.png"><figcaption>Caption <em>here</em></figcaption></figure>
            <img src="https://track.example/p.gif" width="1" height="1">
            <iframe src="https://www.youtube.com/embed/abc"></iframe>
            <script>alert(1)</script>
            """.trimIndent()
        val blocks = Blocks.of(html, base)
        assertEquals(Block.Paragraph(RichText("Before")), blocks[0])
        assertEquals(Block.Image("https://site.example/pic.jpg", "A pic", "https://big.example/"), blocks[1])
        assertEquals(Block.Paragraph(RichText("after")), blocks[2])
        assertEquals(Block.Image("https://cdn.example/lazy.png", "", null), blocks[3])
        assertEquals(Block.Caption(RichText("Caption here", listOf(Span(8, 12, Style.Italic)))), blocks[4])
        assertEquals(Block.Embed("https://www.youtube.com/embed/abc"), blocks[5])
        assertEquals(6, blocks.size)
    }

    @Test
    fun spanAroundABlockKeepsItsStyle() {
        val blocks = Blocks.of("<b>lead <p>inside</p> trail</b>", base)
        assertEquals(
            listOf(
                Block.Paragraph(RichText("lead", listOf(Span(0, 4, Style.Bold)))),
                Block.Paragraph(RichText("inside", listOf(Span(0, 6, Style.Bold)))),
                Block.Paragraph(RichText("trail", listOf(Span(0, 5, Style.Bold)))),
            ),
            blocks,
        )
    }

    @Test
    fun snippetsAndFirstImage() {
        assertEquals("Hello world", Text.plain("<p>Hello <b>world</b></p>"))
        val long = "word ".repeat(100)
        val s = Text.snippet(long, 50)
        assertEquals(true, s.endsWith("…") && s.length <= 51)
        assertEquals(
            "https://site.example/real.jpg",
            Text.firstImage("<img src=\"x.gif\" width=\"1\"><img src=\"/real.jpg\">", base),
        )
        assertNull(Text.firstImage("<p>none</p>", base))
    }
}
