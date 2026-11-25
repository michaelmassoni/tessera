package io.github.michaelmassoni.tessera.feeds.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class FeedParserTest {
    private fun millis(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun rss2() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/" xmlns:dc="http://purl.org/dc/elements/1.1/"
                 xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <title>Example &amp; Co</title>
                <link>https://example.com/</link>
                <item>
                  <title>First &lt;b&gt;post&lt;/b&gt;</title>
                  <link>/posts/1</link>
                  <guid isPermaLink="false">post-1</guid>
                  <pubDate>Wed, 25 Nov 2026 10:30:00 +1100</pubDate>
                  <dc:creator>Ada</dc:creator>
                  <description>Short</description>
                  <content:encoded><![CDATA[<p>Full <em>text</em></p>]]></content:encoded>
                  <media:thumbnail url="https://example.com/t.jpg"/>
                </item>
                <item>
                  <title>Second</title>
                  <link>https://example.com/posts/2</link>
                  <description>&lt;p&gt;Escaped &amp;amp; html&lt;/p&gt;</description>
                  <pubDate>Tue, 24 Nov 2026 08:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
            """.trimIndent()
        val feed = FeedParser.parse(xml, "https://example.com/feed.xml")
        assertEquals("Example & Co", feed.title)
        assertEquals("https://example.com/", feed.siteUrl)
        assertEquals(2, feed.entries.size)
        val first = feed.entries[0]
        assertEquals("post-1", first.id)
        assertEquals("First post", first.title)
        assertEquals("https://example.com/posts/1", first.link)
        assertEquals("Ada", first.author)
        assertEquals(millis("2026-11-25T10:30:00+11:00"), first.date)
        assertEquals("<p>Full <em>text</em></p>", first.content)
        assertEquals("https://example.com/t.jpg", first.image)
        val second = feed.entries[1]
        // No guid: the link is the id.
        assertEquals("https://example.com/posts/2", second.id)
        assertEquals("<p>Escaped &amp; html</p>", second.content)
        assertNull(second.image)
    }

    @Test
    fun atom() {
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title type="text">Atom blog</title>
              <link href="https://blog.example/" rel="alternate"/>
              <link href="https://blog.example/atom.xml" rel="self"/>
              <author><name>Grace</name></author>
              <entry>
                <title type="html">A &amp;lt;tag&amp;gt; title</title>
                <id>tag:blog.example,2026:1</id>
                <link href="/2026/hello"/>
                <published>2026-11-20T09:00:00Z</published>
                <updated>2026-11-21T09:00:00Z</updated>
                <content type="xhtml"><div xmlns="http://www.w3.org/1999/xhtml"><p>Hello <b>world</b></p></div></content>
              </entry>
              <entry>
                <title>Plain</title>
                <id>tag:blog.example,2026:2</id>
                <updated>2026-11-22T09:00:00+01:00</updated>
                <summary type="text">Line one
            Line &lt;two&gt;</summary>
              </entry>
            </feed>
            """.trimIndent()
        val feed = FeedParser.parse(xml, "https://blog.example/atom.xml")
        assertEquals("Atom blog", feed.title)
        assertEquals("https://blog.example/", feed.siteUrl)
        val first = feed.entries[0]
        assertEquals("tag:blog.example,2026:1", first.id)
        assertEquals("A <tag> title", first.title)
        assertEquals("https://blog.example/2026/hello", first.link)
        assertEquals("Grace", first.author)
        assertEquals(millis("2026-11-20T09:00:00Z"), first.date)
        assertTrue(first.content, first.content.contains("<b>world</b>"))
        val second = feed.entries[1]
        assertEquals(millis("2026-11-22T09:00:00+01:00"), second.date)
        // Plain text is escaped into HTML, keeping its line break.
        assertEquals("Line one<br>Line &lt;two&gt;", second.content)
    }

    @Test
    fun rdf() {
        val xml =
            """
            <?xml version="1.0"?>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns="http://purl.org/rss/1.0/"
                     xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel rdf:about="https://old.example/"><title>Old site</title><link>https://old.example/</link></channel>
              <item rdf:about="https://old.example/a"><title>A</title><link>https://old.example/a</link><dc:date>2026-11-01</dc:date></item>
            </rdf:RDF>
            """.trimIndent()
        val feed = FeedParser.parse(xml, "https://old.example/index.rdf")
        assertEquals("Old site", feed.title)
        assertEquals(1, feed.entries.size)
        assertEquals("https://old.example/a", feed.entries[0].id)
        assertEquals(millis("2026-11-01T00:00:00Z"), feed.entries[0].date)
    }

    @Test
    fun brokenXmlStillParses() {
        // An unescaped ampersand and raw HTML in a description, as some feeds serve.
        val xml =
            "<rss><channel><title>Tom & Jerry</title>" +
                "<item><title>Hi</title><description><p>Raw <b>html</b></p></description></item></channel></rss>"
        val feed = FeedParser.parse(xml, "https://x.example/rss")
        assertEquals("Tom & Jerry", feed.title)
        assertTrue(feed.entries[0].content.contains("<b>html</b>"))
    }

    @Test(expected = NotAFeed::class)
    fun htmlIsNotAFeed() {
        FeedParser.parse("<html><body>Hello</body></html>", "https://x.example/")
    }

    @Test
    fun bytesHonourDeclaredEncoding() {
        val xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><rss><channel><title>Café</title></channel></rss>"
        val feed = FeedParser.parse(xml.toByteArray(Charsets.ISO_8859_1), "https://x.example/rss")
        assertEquals("Café", feed.title)
    }

    @Test
    fun dates() {
        assertEquals(millis("2026-11-25T15:00:00Z"), Dates.parse("Wed, 25 Nov 2026 10:00:00 EST"))
        assertEquals(millis("2026-11-25T10:00:00Z"), Dates.parse("25 Nov 2026 10:00 GMT"))
        assertEquals(millis("2026-11-05T10:00:00+05:30"), Dates.parse("Thu, 5 Nov 2026 10:00:00 +05:30"))
        assertEquals(millis("2026-11-26T10:00:00+01:00"), Dates.parse("2026-11-26T10:00:00+0100"))
        assertEquals(millis("2026-11-26T10:00:00.5Z"), Dates.parse("2026-11-26T10:00:00.500Z"))
        assertEquals(0L, Dates.parse("someday"))
        assertEquals(0L, Dates.parse(""))
    }
}
