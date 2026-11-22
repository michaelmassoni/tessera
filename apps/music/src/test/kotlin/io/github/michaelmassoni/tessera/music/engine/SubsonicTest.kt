package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicTest {
    private fun problem(block: () -> Unit): ServerProblem? =
        try {
            block()
            null
        } catch (e: ServerException) {
            e.problem
        }

    @Test
    fun `addresses get https and lose trailing parts`() {
        assertEquals("https://music.example.com", Subsonic.normalizeAddress("music.example.com"))
        assertEquals("https://music.example.com", Subsonic.normalizeAddress(" https://Music.Example.com/ "))
        assertEquals("https://example.com/navidrome", Subsonic.normalizeAddress("https://example.com/navidrome/rest/ping.view?u=x"))
        assertEquals("http://192.168.1.5:4533", Subsonic.normalizeAddress("http://192.168.1.5:4533"))
    }

    @Test
    fun `plain http only on private networks`() {
        assertEquals(ServerProblem.Insecure, problem { Subsonic.normalizeAddress("http://music.example.com") })
        assertEquals(ServerProblem.Insecure, problem { Subsonic.normalizeAddress("http://8.8.8.8:4533") })
        listOf(
            "10.0.0.20",
            "172.20.1.1",
            "192.168.0.2",
            "100.101.102.103",
            "127.0.0.1",
            "nas",
            "nas.local",
            "nas.tail1234.ts.net",
            "[fd00::1]",
        ).forEach { assertNull(it, problem { Subsonic.normalizeAddress("http://$it:4533") }) }
        assertFalse(Subsonic.isPrivateHost("172.32.0.1"))
        assertFalse(Subsonic.isPrivateHost("100.128.0.1"))
        assertEquals(ServerProblem.BadAddress, problem { Subsonic.normalizeAddress("ftp://example.com") })
        assertEquals(ServerProblem.BadAddress, problem { Subsonic.normalizeAddress("") })
    }

    @Test
    fun `token is md5 of password and salt`() {
        // The example from the Subsonic API documentation.
        assertEquals("26719a1196d2a940705a59634eb18eab", Subsonic.token("sesame", "c19b2d"))
    }

    @Test
    fun `urls carry auth and encoded parameters`() {
        val url = Subsonic.url(ServerAccount("https://m.example.com", "ann b"), "sesame", "c19b2d", "search3", listOf("query" to ""))
        assertEquals(
            "https://m.example.com/rest/search3?u=ann+b&t=26719a1196d2a940705a59634eb18eab&s=c19b2d&v=1.16.1&c=Tessera&f=json&query=",
            url,
        )
    }

    @Test
    fun `errors become problems`() {
        assertEquals(
            ServerProblem.WrongLogin,
            problem {
                Subsonic.response("""{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":40,"message":"Wrong"}}}""")
            },
        )
        assertEquals(
            ServerProblem.TooOld,
            problem { Subsonic.response("""{"subsonic-response":{"status":"failed","error":{"code":30}}}""") },
        )
        assertEquals(ServerProblem.NotSubsonic, problem { Subsonic.response("<html>Login</html>") })
        assertEquals(ServerProblem.NotSubsonic, problem { Subsonic.response("""{"hello":1}""") })
        assertNull(problem { Subsonic.response("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""") })
    }

    @Test
    fun `search results become songs`() {
        val reply =
            Subsonic.response(
                """
                {"subsonic-response":{"status":"ok","searchResult3":{"song":[
                  {"id":"s1","isDir":false,"title":"Song A","album":"Album","albumId":"al1","artist":"Band",
                   "artistId":"ar1","track":3,"discNumber":2,"year":2020,"duration":185,"coverArt":"al-al1"},
                  {"id":"d1","isDir":true,"title":"Folder"}
                ]}}}
                """.trimIndent(),
            )
        val songs = Subsonic.searchSongs(reply)
        assertEquals(1, songs.size)
        val s = songs.single()
        assertEquals("Song A", s.title)
        assertEquals(2003, s.track)
        assertEquals(185_000L, s.durationMillis)
        assertEquals("al-al1", s.art)
        assertEquals("s1", Subsonic.songId(s.uri))
        assertEquals(Subsonic.idOf("album", "al1"), s.albumId)
        assertTrue(Subsonic.isServerUri(s.uri))
    }

    @Test
    fun `a single result object is a list of one`() {
        val reply = Subsonic.response("""{"subsonic-response":{"status":"ok","album":{"song":{"id":"x","title":"Only"}}}}""")
        assertEquals(listOf("Only"), Subsonic.albumSongs(reply).map { it.title })
    }

    @Test
    fun `ids are stable, positive and kind-specific`() {
        assertEquals(Subsonic.idOf("song", "abc"), Subsonic.idOf("song", "abc"))
        assertNotEquals(Subsonic.idOf("song", "abc"), Subsonic.idOf("album", "abc"))
        assertTrue(Subsonic.idOf("song", "abc") > 0)
        assertNull(Subsonic.songId("content://media/external/audio/media/5"))
    }
}

class SubsonicPlaylistTest {
    @Test
    fun `smart and other people's playlists are read-only`() {
        val reply =
            Subsonic.response(
                """
                {"subsonic-response":{"status":"ok","playlists":{"playlist":[
                  {"id":"p1","name":"All Juniper","owner":"tester","readonly":true},
                  {"id":"p2","name":"Road trip","owner":"tester","readonly":false},
                  {"id":"p3","name":"Shared","owner":"someone"},
                  {"id":"p4","name":"Old server"}
                ]}}}
                """.trimIndent(),
            )
        val lists = Subsonic.playlists(reply, "Tester")
        assertEquals(listOf("p1", "p2", "p3", "p4"), lists.map { it.id })
        assertEquals(listOf(true, false, true, false), lists.map { it.readOnly })
    }

    @Test
    fun `playlist entries keep their order`() {
        val reply =
            Subsonic.response(
                """{"subsonic-response":{"status":"ok","playlist":{"id":"p","entry":[{"id":"b"},{"id":"a"},{"id":"b"}]}}}""",
            )
        assertEquals(listOf("b", "a", "b"), Subsonic.playlistSongIds(reply))
        assertEquals("p", Subsonic.createdPlaylistId(reply))
    }
}

class SubsonicFieldsTest {
    @Test
    fun `album artist, added and played are read`() {
        val s =
            Subsonic.song(
                Subsonic
                    .response(
                        """{"subsonic-response":{"status":"ok","song":{"id":"x","title":"T","displayAlbumArtist":"Juniper",
                        "created":"2026-11-22T17:01:20.75194736+11:00","played":"2026-11-22T07:38:43Z"}}}""",
                    )["song"]!!,
            )!!
        assertEquals("Juniper", s.albumArtist)
        assertEquals(1795327280751L, s.addedAt)
        assertEquals(1795333123000L, s.playedAt)
        assertEquals(0L, Subsonic.time("not a date"))
    }
}
