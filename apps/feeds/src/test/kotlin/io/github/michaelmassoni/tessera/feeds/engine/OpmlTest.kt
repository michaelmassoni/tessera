package io.github.michaelmassoni.tessera.feeds.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpmlTest {
    @Test
    fun readsFoldersOneLevelDeep() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0"><head><title>subs</title></head><body>
              <outline text="Tech" title="Tech">
                <outline type="rss" text="A" xmlUrl="https://a.example/feed" htmlUrl="https://a.example/"/>
                <outline text="Deeper"><outline type="rss" title="B" xmlUrl="https://b.example/rss"/></outline>
              </outline>
              <outline type="rss" text="C" xmlUrl="https://c.example/atom.xml"/>
              <outline type="rss" text="C again" xmlUrl="https://c.example/atom.xml"/>
            </body></opml>
            """.trimIndent()
        val out = Opml.read(xml)
        assertEquals(
            listOf(
                Opml.Outline("A", "https://a.example/feed", "https://a.example/", "Tech"),
                Opml.Outline("B", "https://b.example/rss", "", "Tech"),
                Opml.Outline("C", "https://c.example/atom.xml", "", null),
            ),
            out,
        )
    }

    @Test
    fun writeThenReadRoundTrips() {
        val folders = listOf(Folder("f1", "News & Views"))
        val feeds =
            listOf(
                Feed("1", "Quote \"this\"", "https://q.example/feed?a=1&b=2", "https://q.example/", folderId = "f1"),
                Feed("2", "Loose", "https://l.example/rss"),
            )
        val xml = Opml.write(folders, feeds)
        assertTrue(xml.contains("&amp;b=2"))
        val back = Opml.read(xml)
        assertEquals(
            listOf(
                Opml.Outline("Quote \"this\"", "https://q.example/feed?a=1&b=2", "https://q.example/", "News & Views"),
                Opml.Outline("Loose", "https://l.example/rss", "", null),
            ),
            back,
        )
    }

    @Test
    fun discoversFeedLinks() {
        val html =
            """
            <html><head>
            <link rel="alternate" type="application/rss+xml" title="Posts" href="/feed.xml">
            <link rel="alternate" type="application/atom+xml" href="https://site.example/atom">
            <link rel="alternate" type="text/html" href="/fr/">
            <link rel="stylesheet" href="/s.css">
            </head></html>
            """.trimIndent()
        val found = Discovery.feedsIn(html, "https://site.example/blog/")
        assertEquals(
            listOf(Discovery.Found("https://site.example/feed.xml", "Posts"), Discovery.Found("https://site.example/atom", "")),
            found,
        )
        assertTrue(Discovery.looksLikeFeed("<?xml version=\"1.0\"?>\n<rss version=\"2.0\">"))
        assertTrue(!Discovery.looksLikeFeed("<!doctype html><html>"))
    }
}
