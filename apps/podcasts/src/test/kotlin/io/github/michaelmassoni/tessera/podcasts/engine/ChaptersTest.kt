package io.github.michaelmassoni.tessera.podcasts.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ChaptersTest {
    @Test
    fun `reads a Podcasting 2_0 chapters file, sorted, without the hidden ones`() {
        val text =
            """
            {"version":"1.2.0","chapters":[
              {"startTime":754.5,"title":"Listener mail","img":"https://x/mail.jpg"},
              {"startTime":0,"title":"Intro"},
              {"startTime":120,"title":"Art only","toc":false},
              {"startTime":300,"title":"News","url":"https://x/news"},
              {"startTime":300,"title":"Duplicate start"},
              {"title":"No start"}
            ]}
            """.trimIndent()
        assertEquals(
            listOf(
                Chapter(0, "Intro"),
                Chapter(300_000, "News", url = "https://x/news"),
                Chapter(754_500, "Listener mail", imageUrl = "https://x/mail.jpg"),
            ),
            Chapters.parseJson(text),
        )
    }

    @Test
    fun `nonsense gives no chapters`() {
        assertEquals(emptyList<Chapter>(), Chapters.parseJson("not json"))
        assertEquals(emptyList<Chapter>(), Chapters.parseJson("""{"chapters":"no"}"""))
        assertEquals(emptyList<Chapter>(), Chapters.parseJson("[]"))
    }

    @Test
    fun `the chapter playing and the marks on the seek bar`() {
        val chapters = listOf(Chapter(5_000, "A"), Chapter(60_000, "B"), Chapter(120_000, "C"))
        assertEquals(-1, Chapters.current(chapters, 1_000))
        assertEquals(0, Chapters.current(chapters, 5_000))
        assertEquals(1, Chapters.current(chapters, 119_999))
        assertEquals(2, Chapters.current(chapters, 500_000))
        assertEquals(listOf(0.025f, 0.3f, 0.6f), Chapters.marks(chapters, 200_000))
        assertEquals(emptyList<Float>(), Chapters.marks(chapters, 0))
    }
}
