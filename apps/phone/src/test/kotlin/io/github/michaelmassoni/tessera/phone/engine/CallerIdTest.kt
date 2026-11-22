package io.github.michaelmassoni.tessera.phone.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CallerIdTest {
    private val au = CallerLocation("AU", Locale.ENGLISH)

    @Test fun `landlines give their area, overseas numbers city and country, the rest nothing`() {
        assertEquals("Melbourne", au.describe("03 9200 0000"))
        assertEquals("Perth", au.describe("08 9200 0000"))
        // The data only says "Australia" for some areas: that's no news for a call in Australia.
        assertNull(au.describe("07 3000 0000"))
        assertEquals("London, United Kingdom", au.describe("+44 20 7946 0000"))
        assertEquals("Mountain View, CA, United States", au.describe("+1 650 253 0000"))
        assertNull(au.describe("0412 345 678"))
        assertNull(au.describe("not a number"))
    }

    @Test fun `reported numbers match however they're written`() {
        val reported = setOfNotNull(SpamNumbers.key("0455 000 111", "AU"))
        assertEquals(setOf("+61455000111"), reported)
        assertTrue(SpamNumbers.isReported("+61 455 000 111", reported, "AU"))
        assertFalse(SpamNumbers.isReported("0455 000 112", reported, "AU"))
    }
}
