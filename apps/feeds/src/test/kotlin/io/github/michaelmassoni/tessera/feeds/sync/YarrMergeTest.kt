package io.github.michaelmassoni.tessera.feeds.sync

import io.github.michaelmassoni.tessera.feeds.engine.Article
import io.github.michaelmassoni.tessera.feeds.engine.ArticleState
import io.github.michaelmassoni.tessera.feeds.engine.Feed
import io.github.michaelmassoni.tessera.feeds.engine.FeedData
import org.junit.Assert.assertEquals
import org.junit.Test

class YarrMergeTest {
    private fun item(
        id: Long,
        feed: Long = 1,
        status: String = "unread",
    ) = YarrItem(id, feed, "Item $id", "https://s/$id", "<p>Text $id</p>", "2026-11-2${id % 10}T10:00:00Z", status)

    private val snap =
        YarrSnapshot(
            folders = listOf(YarrFolder(7, "Tech")),
            feeds =
                listOf(
                    YarrFeed(1, 7, "One", "https://one/", "https://one/feed"),
                    YarrFeed(2, null, "Two", feedLink = "https://two/rss"),
                ),
            errors = mapOf(2L to "timeout"),
            unread = listOf(item(10), item(11, feed = 2)),
            starred = listOf(item(5, status = "starred")),
            recent = listOf(item(12, status = "read")),
        )

    @Test
    fun firstSyncTakesEverything() {
        val m = YarrMerge.merge(FeedData(), snap, now = 99)
        assertEquals(listOf("f7"), m.data.folders.map { it.id })
        assertEquals(
            listOf(
                Feed("y1", "One", "https://one/feed", "https://one/", "f7", 1),
                Feed("y2", "Two", "https://two/rss", "", null, 2, error = "timeout"),
            ),
            m.data.feeds,
        )
        assertEquals(listOf("y5", "y10", "y11", "y12"), m.added.map { it.first.id })
        assertEquals(ArticleState.Starred, m.data.article("y5")!!.state)
        assertEquals(ArticleState.Read, m.data.article("y12")!!.state)
        assertEquals("<p>Text 10</p>", m.added.first { it.first.id == "y10" }.second)
        assertEquals(99L, m.data.article("y10")!!.added)
    }

    @Test
    fun readElsewhereAndPendingChanges() {
        val local =
            FeedData(
                feeds = listOf(Feed("y1", "One", "https://one/feed", remoteId = 1, notify = true)),
                articles =
                    listOf(
                        // Read on the server since: no longer listed as unread.
                        Article("y3", "y1", "Three", remoteId = 3),
                        // Starred on the phone, not yet sent: the phone's change wins.
                        Article("y10", "y1", "Ten", state = ArticleState.Starred, remoteId = 10),
                        // A feed the server dropped.
                        Article("y99", "y9", "Gone", remoteId = 99),
                    ),
                pending = mapOf("y10" to ArticleState.Starred),
            )
        val m = YarrMerge.merge(local, snap, now = 1)
        assertEquals(ArticleState.Read, m.data.article("y3")!!.state)
        assertEquals(ArticleState.Starred, m.data.article("y10")!!.state)
        assertEquals(null, m.data.article("y99"))
        // y10 was already here, so it isn't new.
        assertEquals(listOf("y5", "y11", "y12"), m.added.map { it.first.id })
        // Notify choices survive.
        assertEquals(true, m.data.feed("y1")!!.notify)
        assertEquals(mapOf("y10" to ArticleState.Starred), m.data.pending)
    }
}
