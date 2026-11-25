package io.github.michaelmassoni.tessera.feeds.sync

import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import io.github.michaelmassoni.tessera.feeds.engine.ArticleState
import io.github.michaelmassoni.tessera.feeds.engine.FeedData
import io.github.michaelmassoni.tessera.feeds.engine.Library
import io.github.michaelmassoni.tessera.feeds.engine.Opml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Against a real yarr, when YARR_URL is set (docs/feeds.md has the recipe): `YARR_URL`,
 * `YARR_USER`, `YARR_PASSWORD`, and `YARR_FEED_SITE`, a site serving a page that links
 * /feed.xml (three items) and /atom.xml. Skipped otherwise.
 */
class YarrServerTest {
    private val url: String = System.getenv("YARR_URL").orEmpty()
    private val user = System.getenv("YARR_USER")
    private val password = System.getenv("YARR_PASSWORD")
    private val site: String = System.getenv("YARR_FEED_SITE").orEmpty()

    private fun client() = YarrClient(url, user, password)

    @Test
    fun roundTrip() {
        assumeTrue(url.isNotEmpty() && site.isNotEmpty())
        val client = client()
        client.check()

        // A wrong password is a wrong login.
        try {
            YarrClient(url, user, "nope").check()
            fail()
        } catch (e: SyncException) {
            assertEquals(SyncProblem.WrongLogin, e.problem)
        }

        // Start clean.
        client.feeds().forEach { client.deleteFeed(it.id) }
        client.folders().forEach { client.deleteFolder(it.id) }

        val folder = client.createFolder("Testing")
        // The site offers two feeds.
        val choices = client.addFeed("$site/", folder.id)
        assertTrue(choices.toString(), choices is AddResult.Choices && choices.choices.size == 2)
        val added = client.addFeed("$site/feed.xml", folder.id) as AddResult.Added
        assertEquals(folder.id, added.feed.folderId)

        // yarr fetches a new feed's items when it's added.
        val unread = waitForItems(client, 3)
        assertEquals(setOf("Hello from the test feed", "Second article", "Third article"), unread.map { it.title }.toSet())

        // Merge into an empty phone, then read one and star another there.
        val snap = {
            YarrSnapshot(client.folders(), client.feeds(), client.feedErrors(), all(client, "unread"), all(client, "starred"), emptyList())
        }
        var data = YarrMerge.merge(FeedData(), snap(), now = 1).data
        assertEquals(3, data.articles.count { it.unread })
        val first = data.articles.first { it.title == "Hello from the test feed" }
        val second = data.articles.first { it.title == "Second article" }
        data = Library.setState(data, setOf(first.id), ArticleState.Read, queue = true)
        data = Library.setState(data, setOf(second.id), ArticleState.Starred, queue = true)
        data.pending.forEach { (id, state) -> client.setStatus(data.article(id)!!.remoteId!!, YarrMerge.status(state)) }
        data = data.copy(pending = emptyMap())

        // The server agrees.
        assertEquals(1, all(client, "starred").size)
        assertEquals(1, all(client, "unread").size)

        // Read elsewhere: mark the last unread on the server, and the next merge reads it here.
        val third = data.articles.first { it.title == "Third article" }
        client.setStatus(third.remoteId!!, "read")
        data = YarrMerge.merge(data, snap(), now = 2).data
        assertEquals(ArticleState.Read, data.article(third.id)!!.state)
        assertEquals(ArticleState.Starred, data.article(second.id)!!.state)

        // Rename and move out of the folder; full article (refused locally).
        client.updateFeed(added.feed.id, title = "Renamed")
        client.updateFeed(added.feed.id, moveFolder = true, folderId = null)
        val feed = client.feeds().first { it.id == added.feed.id }
        assertEquals("Renamed", feed.title)
        assertEquals(null, feed.folderId)
        // yarr won't fetch pages on internal addresses (as the test site is): a clean failure.
        try {
            client.fullArticle(first.link)
            fail()
        } catch (e: SyncException) {
            assertEquals(SyncProblem.Other, e.problem)
        }

        // OPML import adds the Atom feed; the RSS one is already there.
        client.importOpml(
            Opml.write(
                emptyList(),
                listOf(
                    io.github.michaelmassoni.tessera.feeds.engine
                        .Feed("x", "Atom", "$site/atom.xml"),
                ),
            ),
        )
        assertEquals(2, client.feeds().size)
    }

    private fun all(
        client: YarrClient,
        status: String,
    ): List<YarrItem> {
        val out = mutableListOf<YarrItem>()
        var after: Long? = null
        while (true) {
            val page = client.items(status, after)
            out += page.list
            if (!page.hasMore || page.list.isEmpty()) return out
            after = page.list.last().id
        }
    }

    private fun waitForItems(
        client: YarrClient,
        count: Int,
    ): List<YarrItem> {
        repeat(30) {
            val items = all(client, "unread")
            if (items.size >= count) return items
            Thread.sleep(500)
        }
        fail("yarr didn't fetch the feed")
        return emptyList()
    }
}
