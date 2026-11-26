package io.github.michaelmassoni.tessera.feeds.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val day = Library.DAY_MS
    private val now = 1_000 * day

    private val data =
        FeedData(
            folders = listOf(Folder("tech", "Tech")),
            feeds = listOf(Feed("a", "A", "https://a/feed", folderId = "tech"), Feed("b", "B", "https://b/feed")),
            articles =
                listOf(
                    Article("1", "a", "Rust news", date = now - 1 * day, snippet = "compiler"),
                    Article("2", "a", "Old read", date = now - 60 * day, state = ArticleState.Read),
                    Article("3", "b", "Starred thing", date = now - 90 * day, state = ArticleState.Starred),
                    Article("4", "b", "Newest", date = now),
                ),
        )

    @Test
    fun selectionsAndSearch() {
        assertEquals(listOf("4", "1"), Library.articles(data, Selection.Unread).map { it.id })
        assertEquals(listOf("3"), Library.articles(data, Selection.Starred).map { it.id })
        assertEquals(listOf("4", "1", "2", "3"), Library.articles(data, Selection.All).map { it.id })
        assertEquals(listOf("1", "2"), Library.articles(data, Selection.InFolder("tech")).map { it.id })
        assertEquals(listOf("4", "3"), Library.articles(data, Selection.OfFeed("b")).map { it.id })
        assertEquals(listOf("1"), Library.articles(data, Selection.All, "COMPILER rust").map { it.id })
        assertEquals(listOf("4", "1", "2"), Library.articles(data, Selection.Unread, also = setOf("2")).map { it.id })
    }

    @Test
    fun counts() {
        val byFeed = Library.unreadByFeed(data)
        assertEquals(mapOf("a" to 1, "b" to 1), byFeed)
        assertEquals(1, Library.unreadInFolder(data, "tech"))
    }

    @Test
    fun readingDoesNotUnstarAndQueuesForTheServer() {
        val read = Library.setState(data, setOf("1", "3"), ArticleState.Read, queue = true)
        assertEquals(ArticleState.Read, read.article("1")!!.state)
        assertEquals(ArticleState.Starred, read.article("3")!!.state)
        assertEquals(mapOf("1" to ArticleState.Read), read.pending)
        val local = Library.setState(data, setOf("1"), ArticleState.Starred, queue = false)
        assertTrue(local.pending.isEmpty())
        assertEquals(ArticleState.Starred, local.article("1")!!.state)
        // Marking read leaves a starred one starred.
        val stillStarred = Library.setState(data, setOf("3"), ArticleState.Read, queue = false)
        assertEquals(ArticleState.Starred, stillStarred.article("3")!!.state)
    }

    @Test
    fun unstarringMakesItReadAndQueuesIt() {
        val unstarred = Library.unstar(data, setOf("1", "3"), queue = true)
        assertEquals(ArticleState.Read, unstarred.article("3")!!.state)
        // Only starred ones change.
        assertEquals(data.article("1"), unstarred.article("1"))
        assertEquals(mapOf("3" to ArticleState.Read), unstarred.pending)
        assertEquals(data, Library.unstar(data, setOf("1"), queue = true))
    }

    @Test
    fun pruneKeepsUnreadStarredAndPending() {
        val pending = data.copy(pending = mapOf("2" to ArticleState.Read))
        assertEquals(4, Library.prune(pending, now, 30).articles.size)
        val pruned = Library.prune(data, now, 30)
        assertEquals(listOf("1", "3", "4"), pruned.articles.map { it.id })
        // Articles of a feed that's gone go too.
        val gone = Library.prune(data.copy(feeds = data.feeds.take(1)), now, 365)
        assertEquals(listOf("1", "2"), gone.articles.map { it.id })
    }

    @Test
    fun addEntriesAddsOnlyNewOnes() {
        val parsed =
            ParsedFeed(
                "B title",
                "https://b/",
                listOf(
                    ParsedEntry("e1", "Hello", "https://b/1", "", now, "<p>Body <img src=\"/i.png\"></p>", null),
                    ParsedEntry("e2", "", "https://b/2", "", now - 400 * day, "<p>Too old to add</p>", null),
                    ParsedEntry("e1", "Dup", "https://b/1", "", now, "", null),
                ),
            )
        val (next, added) = Library.addEntries(data, "b", parsed, now, 30)
        assertEquals(1, added.size)
        val a = added[0].first
        assertEquals(Library.articleId("b", "e1"), a.id)
        assertEquals("Body", a.snippet)
        assertEquals("https://b/i.png", a.image)
        assertEquals(ArticleState.Unread, a.state)
        assertEquals(5, next.articles.size)
        // Fetching again adds nothing.
        val (_, again) = Library.addEntries(next, "b", parsed, now, 30)
        assertTrue(again.isEmpty())
        // The site comes from the feed when the feed had none.
        assertEquals("https://b/", next.feed("b")!!.siteUrl)
    }
}
