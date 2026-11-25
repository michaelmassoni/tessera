package io.github.michaelmassoni.tessera.feeds.sync

import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class YarrClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() = server.close()

    private fun reply(
        code: Int = 200,
        body: String = "",
        vararg headers: Pair<String, String>,
    ) = MockResponse
        .Builder()
        .code(code)
        .body(body)
        .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
        .build()

    private fun address() = server.url("/yarr/").toString()

    @Test
    fun signsInOn401AndKeepsTheCookie() {
        var saved: String? = null
        server.enqueue(reply(401))
        server.enqueue(reply(200, "", "Set-Cookie" to "auth=me:123:abc; Path=/; HttpOnly; Secure"))
        server.enqueue(reply(200, """{"running":0,"stats":[]}"""))
        val client = YarrClient(address(), "me", "pw", onCookie = { saved = it })
        client.check()
        assertEquals("me:123:abc", saved)
        val first = server.takeRequest()
        assertEquals("/yarr/api/status", first.url.encodedPath)
        assertNull(first.headers["Cookie"])
        val login = server.takeRequest()
        assertEquals("POST", login.method)
        assertEquals("/yarr/login", login.url.encodedPath)
        assertEquals("username=me&password=pw", login.body!!.utf8())
        assertEquals("auth=me:123:abc", server.takeRequest().headers["Cookie"])
    }

    @Test
    fun noLoginServerSays401IsAWrongLogin() {
        server.enqueue(reply(401))
        try {
            YarrClient(address(), null, null).check()
            fail()
        } catch (e: SyncException) {
            assertEquals(SyncProblem.WrongLogin, e.problem)
        }
    }

    @Test
    fun aWebPageIsNotYarr() {
        server.enqueue(reply(200, "<html>nope</html>"))
        try {
            YarrClient(address(), null, null).check()
            fail()
        } catch (e: SyncException) {
            assertEquals(SyncProblem.NotCalDav, e.problem)
        }
    }

    @Test
    fun itemsFeedsAndStatus() {
        server.enqueue(
            reply(
                200,
                """{"list":[{"id":9,"guid":"g","feed_id":2,"title":"T","link":"https://x/9","content":"<p>c</p>","date":"2026-11-25T10:00:00Z","status":"starred","media_links":null}],"has_more":true}""",
            ),
        )
        server.enqueue(
            reply(
                200,
                """[{"id":2,"folder_id":null,"title":"F","description":"","link":"https://x/","feed_link":"https://x/feed","icon":"data:image/png;base64,iVBORw0KGgo="},{"id":3,"folder_id":4,"title":"Old","link":"","feed_link":"https://o/rss","has_icon":true}]""",
            ),
        )
        server.enqueue(reply(200))
        val client = YarrClient(address(), null, null)
        val page = client.items("unread", after = 40)
        assertEquals(true, page.hasMore)
        assertEquals(YarrItem(9, 2, "T", "https://x/9", "<p>c</p>", "2026-11-25T10:00:00Z", "starred"), page.list[0])
        assertEquals("/yarr/api/items?status=unread&after=40", server.takeRequest().target)
        val feeds = client.feeds()
        assertTrue(feeds[0].icon!!.startsWith("data:image/png"))
        assertEquals(4L, feeds[1].folderId)
        assertEquals(true, feeds[1].hasIcon)
        server.takeRequest()
        client.setStatus(9, "read")
        val put = server.takeRequest()
        assertEquals("PUT", put.method)
        assertEquals("/yarr/api/items/9", put.url.encodedPath)
        assertEquals("""{"status":"read"}""", put.body!!.utf8())
    }

    @Test
    fun addingAFeed() {
        server.enqueue(
            reply(200, """{"status":"multiple","choice":[{"url":"https://s/a.xml","title":"A"},{"url":"https://s/b.xml","title":"B"}]}"""),
        )
        server.enqueue(
            reply(
                200,
                """{"status":"success","feed":{"id":5,"folder_id":1,"title":"S","link":"https://s/","feed_link":"https://s/a.xml"}}""",
            ),
        )
        server.enqueue(reply(500))
        val client = YarrClient(address(), null, null)
        assertEquals(AddResult.Choices(listOf("https://s/a.xml" to "A", "https://s/b.xml" to "B")), client.addFeed("https://s/", 1))
        assertEquals("""{"url":"https://s/","folder_id":1}""", server.takeRequest().body!!.utf8())
        val added = client.addFeed("https://s/a.xml", 1) as AddResult.Added
        assertEquals(5L, added.feed.id)
        server.takeRequest()
        assertEquals(AddResult.NotFound, client.addFeed("https://nothing/", null))
    }

    @Test
    fun movingOutOfAFolderSendsNull() {
        server.enqueue(reply(200))
        YarrClient(address(), null, null).updateFeed(3, moveFolder = true, folderId = null)
        assertEquals("""{"folder_id":null}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun unreachable() {
        val dead = address()
        server.close()
        try {
            YarrClient(dead, null, null).folders()
            fail()
        } catch (e: SyncException) {
            assertEquals(SyncProblem.Unreachable, e.problem)
        }
    }
}
