package io.github.michaelmassoni.tessera.podcasts.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FeedParserTest {
    private fun parse(
        xml: String,
        base: String = "https://example.com/feed.xml",
    ) = FeedParser.parse(xml.byteInputStream(), base)

    private val rss =
        """<?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:content="http://purl.org/rss/1.0/modules/content/">
          <channel>
            <title>Morning &amp; Co</title>
            <link>https://example.com</link>
            <description>A daily show.</description>
            <itunes:author>Example Media</itunes:author>
            <itunes:image href="https://example.com/cover.jpg"/>
            <itunes:new-feed-url>https://new.example.com/feed</itunes:new-feed-url>
            <image><url>https://example.com/small.jpg</url><title>ignored</title></image>
            <item>
              <title>Episode 2</title>
              <guid isPermaLink="false">ep-2</guid>
              <pubDate>Tue, 24 Nov 2026 06:00:00 +0000</pubDate>
              <itunes:duration>1:02:03</itunes:duration>
              <description>Short</description>
              <content:encoded><![CDATA[<p>Long <b>notes</b> with a <a href="https://x.y">link</a>.</p>]]></content:encoded>
              <enclosure url="audio/2.mp3" type="audio/mpeg" length="12345"/>
              <itunes:image href="https://example.com/ep2.jpg"/>
            </item>
            <item>
              <title>Episode 1</title>
              <pubDate>Mon, 23 Nov 2026 06:00:00 GMT</pubDate>
              <itunes:duration>2700</itunes:duration>
              <enclosure url="https://example.com/audio/1.m4a" type="audio/x-m4a"/>
            </item>
            <item>
              <title>Blog post with no audio</title>
              <guid>post</guid>
            </item>
            <item>
              <title>A PDF</title>
              <enclosure url="https://example.com/a.pdf" type="application/pdf"/>
            </item>
          </channel>
        </rss>"""

    @Test
    fun `reads an RSS podcast feed`() {
        val feed = parse(rss)
        assertEquals("Morning & Co", feed.title)
        assertEquals("Example Media", feed.author)
        assertEquals("A daily show.", feed.description)
        assertEquals("https://example.com/cover.jpg", feed.artworkUrl)
        assertEquals("https://example.com", feed.link)
        assertEquals("https://new.example.com/feed", feed.newFeedUrl)
        assertEquals(2, feed.items.size)

        val two = feed.items[0]
        assertEquals("ep-2", two.guid)
        assertEquals("Episode 2", two.title)
        assertEquals(Instant.parse("2026-11-24T06:00:00Z").toEpochMilli(), two.publishedAt)
        assertEquals((3600 + 2 * 60 + 3) * 1000L, two.durationMs)
        // Relative enclosure URLs are resolved against the feed.
        assertEquals("https://example.com/audio/2.mp3", two.audioUrl)
        assertEquals(12345L, two.audioBytes)
        assertTrue(two.notes.contains("<b>notes</b>"))
        assertEquals("https://example.com/ep2.jpg", two.artworkUrl)

        val one = feed.items[1]
        // No guid: the audio URL stands in.
        assertEquals("https://example.com/audio/1.m4a", one.guid)
        assertEquals(2_700_000L, one.durationMs)
    }

    @Test
    fun `falls back to the rss image`() {
        val feed = parse(rss.replace("""<itunes:image href="https://example.com/cover.jpg"/>""", ""))
        assertEquals("https://example.com/small.jpg", feed.artworkUrl)
    }

    @Test
    fun `reads an Atom feed`() {
        val atom =
            """<?xml version="1.0"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Show</title>
              <subtitle>About things</subtitle>
              <author><name>Ann</name></author>
              <link rel="alternate" href="https://atom.example/"/>
              <entry>
                <id>urn:1</id>
                <title>First</title>
                <published>2026-11-20T10:00:00+01:00</published>
                <summary>Hello</summary>
                <link rel="enclosure" href="https://atom.example/1.mp3" type="audio/mpeg" length="99"/>
              </entry>
            </feed>"""
        val feed = parse(atom)
        assertEquals("Atom Show", feed.title)
        assertEquals("Ann", feed.author)
        assertEquals("About things", feed.description)
        assertEquals("https://atom.example/", feed.link)
        val item = feed.items.single()
        assertEquals("urn:1", item.guid)
        assertEquals("https://atom.example/1.mp3", item.audioUrl)
        assertEquals(Instant.parse("2026-11-20T09:00:00Z").toEpochMilli(), item.publishedAt)
    }

    @Test(expected = NotAFeedException::class)
    fun `an HTML page is not a feed`() {
        parse("<!DOCTYPE html><html><head><title>Hi</title></head><body><p>Hello</p></body></html>")
    }

    @Test
    fun `keeps the items before a break`() {
        val broken = rss.substringBefore("<item>\n              <title>Episode 1") + "<item><title>Cut"
        val feed = parse(broken)
        assertEquals(listOf("Episode 2"), feed.items.map { it.title })
    }

    @Test
    fun `content of a feed becomes episodes`() {
        val feed = parse(rss)
        val content = Feeds.content("show", feed, emptyList(), emptySet())
        assertEquals(listOf("Episode 2", "Episode 1"), content.episodes.map { it.title })
        assertEquals("Long notes with a link.", content.episodes[0].summary)
        assertTrue(content.notes.getValue(content.episodes[0].id).contains("<a href"))
        assertNull(content.notes[content.episodes[1].id])

        // An episode that left the feed stays when it's kept (downloaded, queued); others go.
        val gone = Episode("show:gone", "show", "Gone", audioUrl = "https://x/g.mp3", publishedAt = 1)
        val again = Feeds.content("show", feed, content.episodes + gone, setOf(gone.id))
        assertEquals(3, again.episodes.size)
        assertEquals(listOf("show:gone"), Feeds.added(content.episodes, again).map { it.id })
        assertEquals(2, Feeds.content("show", feed, content.episodes + gone, emptySet()).episodes.size)
    }

    @Test
    fun `reads the podcast namespace chapters link`() {
        val feed =
            parse(
                """<rss version="2.0" xmlns:podcast="https://podcastindex.org/namespace/1.0"><channel><title>T</title>
                <item><guid>1</guid><title>E</title><enclosure url="https://x/e.mp3" type="audio/mpeg"/>
                <podcast:chapters url="chapters/1.json" type="application/json+chapters"/></item>
                <item><guid>2</guid><title>F</title><enclosure url="https://x/f.mp3" type="audio/mpeg"/></item>
                </channel></rss>""",
            )
        assertEquals("https://example.com/chapters/1.json", feed.items[0].chaptersUrl)
        assertNull(feed.items[1].chaptersUrl)
    }
}
