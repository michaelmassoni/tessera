package io.github.michaelmassoni.tessera.podcasts.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EngineTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun dates() {
        assertEquals(ms("2026-11-24T06:00:00Z"), Dates.parse("Tue, 24 Nov 2026 06:00:00 +0000"))
        assertEquals(ms("2026-11-24T06:00:00Z"), Dates.parse("24 Nov 2026 06:00:00 GMT"))
        assertEquals(ms("2026-11-24T11:00:00Z"), Dates.parse("Tue, 24 Nov 2026 06:00:00 EST"))
        assertEquals(ms("2026-11-04T06:00:00Z"), Dates.parse("Wed, 4 Nov 2026 06:00 +0000"))
        assertEquals(ms("2026-11-24T06:00:00Z"), Dates.parse("Tuesday, 24 November 2026 06:00:00 +00:00"))
        assertEquals(ms("2026-11-24T06:00:00Z"), Dates.parse("2026-11-24T06:00:00Z"))
        assertEquals(ms("2026-11-24T00:00:00Z"), Dates.parse("24 Nov 2026"))
        assertEquals(0L, Dates.parse("someday"))
        assertEquals(0L, Dates.parse(""))
    }

    @Test
    fun durations() {
        assertEquals(3_723_000L, Durations.parse("1:02:03"))
        assertEquals(3_723_000L, Durations.parse("62:03"))
        assertEquals(3_723_000L, Durations.parse("3723"))
        assertEquals(3_723_500L, Durations.parse("3723.5"))
        assertEquals(0L, Durations.parse("about an hour"))
        assertEquals(0L, Durations.parse("1:2:3:4"))
    }

    @Test
    fun `plain text`() {
        assertEquals("Hello & welcome. Second ’line’", PlainText.of("<p>Hello &amp; welcome.</p><p>Second &#8217;line&#x2019;</p>"))
        assertEquals("abc…", PlainText.of("abcdef", maxLength = 3))
    }

    @Test
    fun `directory replies`() {
        val apple =
            """{"resultCount":2,"results":[
              {"collectionName":"Show A","artistName":"Ann","feedUrl":"https://a.example/feed","artworkUrl600":"https://img/a.jpg"},
              {"collectionName":"No feed","artistName":"X"}]}"""
        val fyyd =
            """{"status":1,"data":[
              {"title":"Show A again","xmlURL":"http://www.a.example/feed/","imgURL":"https://img/a2.jpg"},
              {"title":"Show B","author":"Bo","xmlURL":"https://b.example/rss","layoutImageURL":"https://img/b.jpg"}]}"""
        val merged = Directory.merge(Directory.apple(apple), Directory.fyyd(fyyd))
        assertEquals(listOf("Show A", "Show B"), merged.map { it.title })
        assertEquals("https://img/a.jpg", merged[0].artworkUrl)
        assertEquals("Bo", merged[1].author)
        assertEquals(emptyList<SearchResult>(), Directory.apple("not json"))
        assertTrue(Directory.appleSearchUrl("tech news").endsWith("term=tech+news"))
    }

    @Test
    fun `addresses and Apple links`() {
        assertEquals("173001861", Directory.appleId("https://podcasts.apple.com/us/podcast/dan-carlins/id173001861?uo=4"))
        assertNull(Directory.appleId("https://example.com/id123"))
        assertEquals("https://example.com/feed", Directory.feedAddress("example.com/feed"))
        assertEquals("https://example.com/feed", Directory.feedAddress("feed://example.com/feed"))
        assertEquals("http://example.com/rss", Directory.feedAddress(" http://example.com/rss "))
        assertNull(Directory.feedAddress("not a url"))
        assertNull(Directory.feedAddress("localhost"))
        assertEquals(Directory.key("https://feeds.example.com/x?format=xml"), Directory.key("http://www.feeds.example.com/x/"))
    }

    @Test
    fun opml() {
        val shows =
            listOf(
                Show("1", "https://a.example/feed?x=1&y=2", "A & B", link = "https://a.example"),
                Show("2", "https://b.example/rss", "\"B\""),
            )
        val text = Opml.write(shows)
        val read = Opml.read(text.byteInputStream())
        assertEquals(listOf("https://a.example/feed?x=1&y=2", "https://b.example/rss"), read.map { it.feedUrl })
        assertEquals(listOf("A & B", "\"B\""), read.map { it.title })

        // Other apps nest outlines in folders.
        val nested =
            """<opml version="1.0"><body><outline text="Folder">
               <outline text="X" type="rss" xmlUrl="https://x.example/f"/></outline>
               <outline text="no url"/></body></opml>"""
        assertEquals(listOf("https://x.example/f"), Opml.read(nested.byteInputStream()).map { it.feedUrl })
        assertTrue(Opml.read("garbage".byteInputStream()).isEmpty())
    }

    @Test
    fun `up next`() {
        val q = listOf("a", "b", "c")
        assertEquals(listOf("c", "a", "b"), UpNext.playNow(q, "c"))
        assertEquals(listOf("d", "a", "b", "c"), UpNext.playNow(q, "d"))
        assertEquals(listOf("a", "c", "b"), UpNext.playNext(q, "c"))
        assertEquals(listOf("x"), UpNext.playNext(emptyList(), "x"))
        assertEquals(q, UpNext.add(q, "b"))
        assertEquals(q + "d", UpNext.add(q, "d"))
        assertEquals(listOf("a", "c"), UpNext.remove(q, "b"))
        assertEquals(listOf("b", "c", "a"), UpNext.move(q, 0, 2))
        assertEquals(q, UpNext.move(q, 0, 5))
    }

    @Test
    fun `episodes lists and progress`() {
        val day = 24 * 60 * 60 * 1000L
        val now = 100 * day
        val show = Show("s", "https://s/f", "S", followed = true)
        val other = Show("o", "https://o/f", "O", followed = false)
        val e1 = Episode("s:1", "s", "Recent", publishedAt = now - day, audioUrl = "u", durationMs = 600_000)
        val e2 = Episode("s:2", "s", "Old", publishedAt = now - 20 * day, audioUrl = "u")
        val e3 = Episode("s:3", "s", "Played", publishedAt = now - 2 * day, audioUrl = "u")
        val e4 = Episode("o:1", "o", "Not followed", publishedAt = now, audioUrl = "u")
        val lib =
            Library(shows = listOf(show, other))
                .withState("s:3") { it.copy(played = true) }
                .withState("s:1") { it.copy(positionMs = 150_000) }
        val new = Episodes.new(lib, mapOf("s" to listOf(e1, e2, e3), "o" to listOf(e4)), now)
        assertEquals(listOf("s:1"), new.map { it.id })
        assertEquals(450_000L, Episodes.remaining(e1, lib.state("s:1")))
        assertEquals(0.25f, Episodes.progress(e1, lib.state("s:1")), 0.001f)
        assertTrue(Episodes.nearEnd(590_000, 600_000))
        assertFalse(Episodes.nearEnd(500_000, 600_000))
        assertFalse(Episodes.nearEnd(500_000, 0))

        // A state back to defaults isn't kept.
        assertFalse("s:1" in lib.withState("s:1") { EpisodeState() }.states)
    }

    @Test
    fun ids() {
        val show = Ids.show("https://example.com/feed")
        assertEquals(12, show.length)
        assertEquals(show, Ids.showOf(Ids.episode(show, "guid")))
        assertTrue(Ids.episode(show, "a") != Ids.episode(show, "b"))
    }
}
