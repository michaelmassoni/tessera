package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    private val lrc =
        """
        [ar:Someone]
        [ti:A song]
        [00:01.50]First line
        [00:05.00][00:20.00]Chorus
        [00:10.123]Third
        [00:12]
        """.trimIndent()

    @Test
    fun `timed lines in order, repeats expanded, tags skipped`() {
        val l = LyricsParser.parse(lrc, LyricsSource.Server)!!
        assertTrue(l.synced)
        assertEquals(listOf(1500L, 5000L, 10123L, 12000L, 20000L), l.lines.map { it.timeMs })
        assertEquals(listOf("First line", "Chorus", "Third", "", "Chorus"), l.lines.map { it.text })
    }

    @Test
    fun `current line follows the position`() {
        val l = LyricsParser.parse(lrc, LyricsSource.Server)!!
        assertEquals(-1, l.current(1000))
        assertEquals(0, l.current(1500))
        assertEquals(1, l.current(9999))
        assertEquals(2, l.current(10123))
        assertEquals(4, l.current(60_000))
    }

    @Test
    fun `offset moves lines earlier`() {
        val l = LyricsParser.parse("[offset:500]\n[00:02.00]Hi", LyricsSource.LrcLib)!!
        assertEquals(1500L, l.lines.single().timeMs)
    }

    @Test
    fun `plain text is unsynced`() {
        val l = LyricsParser.parse("\nOne\n\nTwo\n", LyricsSource.Server)!!
        assertFalse(l.synced)
        assertEquals(listOf("One", "", "Two"), l.lines.map { it.text })
        assertEquals(-1, l.current(5000))
    }

    @Test
    fun `nothing to show is null`() {
        assertNull(LyricsParser.parse("  \n[ar:x]\n", LyricsSource.Server))
    }
}

class LyricsSourcesTest {
    @Test
    fun `server structured lyrics prefer synced and apply offset`() {
        val reply =
            Subsonic.response(
                """
                {"subsonic-response":{"status":"ok","lyricsList":{"structuredLyrics":[
                  {"synced":false,"line":[{"value":"plain"}]},
                  {"synced":true,"offset":100,"line":[{"start":1000,"value":"one"},{"start":3000,"value":"two"}]}
                ]}}}
                """.trimIndent(),
            )
        val l = Subsonic.structuredLyrics(reply)!!
        assertTrue(l.synced)
        assertEquals(listOf(900L, 2900L), l.lines.map { it.timeMs })
        assertNull(Subsonic.structuredLyrics(Subsonic.response("""{"subsonic-response":{"status":"ok","lyricsList":{}}}""")))
    }

    @Test
    fun `lrclib picks synced lyrics close in length`() {
        val body =
            """
            [{"duration":300,"syncedLyrics":"[00:01.00]far"},
             {"duration":181,"plainLyrics":"plain near"},
             {"duration":182,"syncedLyrics":"[00:01.00]synced near"}]
            """.trimIndent()
        assertEquals(
            "synced near",
            LrcLib
                .bestOf(body, 180)!!
                .lines
                .single()
                .text,
        )
        assertNull(LrcLib.bestOf("""[{"duration":300,"plainLyrics":"x"}]""", 180))
        assertNull(LrcLib.get("""{"instrumental":true}"""))
        assertEquals(LyricsSource.LrcLib, LrcLib.get("""{"plainLyrics":"la la"}""")!!.source)
    }

    @Test
    fun `lrclib urls are encoded`() {
        assertEquals(
            "https://lrclib.net/api/get?artist_name=Ariana+Grande&track_name=kiss+me&album_name=petal&duration=219",
            LrcLib.getUrl("Ariana Grande", "kiss me", "petal", 219),
        )
    }
}
