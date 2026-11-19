package io.github.michaelmassoni.tessera.keyboard.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SuggesterTest {
    private val suggester = Suggester(dictionary)

    @Test
    fun correctsCommonTypos() {
        val fixes =
            mapOf(
                "teh" to "the",
                "recieve" to "receive",
                "helo" to "hello",
                "tomorow" to "tomorrow",
                "becuase" to "because",
                "definately" to "definitely",
                "dont" to "don't",
                "im" to "I'm",
                "i" to "I",
                "cant" to "can't",
                "thats" to "that's",
                "wierd" to "weird",
                "seperate" to "separate",
                "wiht" to "with",
                "abd" to "and",
                "sydney" to "Sydney",
                "Teh" to "The",
                "TEH" to "THE",
            )
        for ((typed, expected) in fixes) assertEquals(typed, expected, suggester.autocorrection(typed))
    }

    @Test
    fun leavesRealWordsAlone() {
        for (word in listOf(
            "colour",
            "color",
            "its",
            "it's",
            "were",
            "ill",
            "well",
            "the",
            "The",
            "realise",
            "organise",
            "arvo",
            "a",
            "OK",
        )) {
            assertNull(word, suggester.autocorrection(word))
        }
    }

    @Test
    fun leavesNonWordsAndShortUnknownsAlone() {
        for (word in listOf("x2", "abc123", "zq", "qz")) assertNull(word, suggester.autocorrection(word))
    }

    @Test
    fun learnedWordsAreNotCorrected() {
        assertEquals("the", Suggester(dictionary).autocorrection("teh"))
        assertNull(Suggester(dictionary, learned = { setOf("teh") }).autocorrection("teh"))
    }

    @Test
    fun stripShowsLiteralCorrectionAndAlternative() {
        val strip = suggester.strip("teh")
        assertEquals(Slot("teh", Slot.Kind.Literal), strip[0])
        assertEquals(Slot("the", Slot.Kind.Correction), strip[1])
        assertEquals(3, strip.size)
    }

    @Test
    fun stripForAKnownWordPutsItInTheMiddle() {
        val strip = suggester.strip("hel")
        assertTrue(strip.toString(), strip.any { it?.text == "hello" || it?.text == "help" })
        val known = suggester.strip("good")
        assertEquals(Slot("good", Slot.Kind.Word), known[1])
    }

    @Test
    fun completionsKeepCapitalisation() {
        assertTrue(suggester.strip("Tomor").any { it?.text == "Tomorrow" })
    }

    @Test
    fun offensiveWordsAreNotSuggestedUnlessTyped() {
        assertFalse(suggester.strip("fu").any { it != null && Dictionary.key(it.text) == "fuck" })
        assertNull(suggester.autocorrection("fuck"))
    }

    @Test
    fun neighboursAndDistance() {
        assertTrue(Dictionary.neighbours('h', 'n'))
        assertTrue(Dictionary.neighbours('q', 'w'))
        assertFalse(Dictionary.neighbours('q', 'p'))
        assertEquals(0.8f, Dictionary.distance("teh", "the", 2f), 0.001f)
        assertEquals(0.4f, Dictionary.distance("helo", "hello", 2f), 0.001f)
        assertEquals(Float.MAX_VALUE, Dictionary.distance("a", "abcd", 2f))
    }

    @Test
    fun isFastEnoughToRunPerKeystroke() {
        val words = listOf("teh", "recieve", "definately", "tomorow", "becuase", "helo", "wierd", "seperate")
        repeat(3) { words.forEach { suggester.strip(it) } }
        val start = System.nanoTime()
        repeat(5) { words.forEach { suggester.strip(it) } }
        val perWord = (System.nanoTime() - start) / 1_000_000.0 / (5 * words.size)
        println("suggestions: $perWord ms per word")
        assertTrue("$perWord ms per word", perWord < 25)
    }

    companion object {
        private val dictionary by lazy { File("src/main/assets/en.dict").useLines { Dictionary.parse(it) } }
    }
}
