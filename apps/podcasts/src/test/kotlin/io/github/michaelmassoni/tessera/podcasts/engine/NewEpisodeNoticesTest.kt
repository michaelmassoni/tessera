package io.github.michaelmassoni.tessera.podcasts.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NewEpisodeNoticesTest {
    private val radio = Show("radio", "https://r/feed", "Radio Lab", followed = true)
    private val kept = Show("kept", "https://k/feed", "Kept Only", followed = false)

    private fun ep(
        show: Show,
        n: Int,
        at: Long,
    ) = Episode("${show.id}:$n", show.id, "Episode $n", publishedAt = at, audioUrl = "https://x/$n.mp3")

    @Test
    fun `one notice per followed show, newest first`() {
        val lib = Library(shows = listOf(radio, kept))
        val notices = NewEpisodeNotices.from(listOf(ep(radio, 1, 10), ep(radio, 2, 20), ep(kept, 1, 30)), lib)
        assertEquals(listOf("radio"), notices.map { it.show.id })
        assertEquals(listOf("radio:2", "radio:1"), notices.single().episodes.map { it.id })
    }

    @Test
    fun `episodes already played elsewhere aren't announced`() {
        val lib = Library(shows = listOf(radio), states = mapOf("radio:1" to EpisodeState(played = true)))
        assertEquals(emptyList<NewEpisodeNotices.Notice>(), NewEpisodeNotices.from(listOf(ep(radio, 1, 10)), lib))
    }
}
