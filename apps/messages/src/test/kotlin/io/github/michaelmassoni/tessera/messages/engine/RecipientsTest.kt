package io.github.michaelmassoni.tessera.messages.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipientsTest {
    /** Stand-in for libphonenumber: Australian mobiles only. */
    private val au: (String) -> String? = { d ->
        when {
            d.length == 10 && d.startsWith("04") -> "${d.take(4)} ${d.substring(4, 7)} ${d.substring(7)}"
            d.length == 12 && d.startsWith("+614") -> "+61 ${d.substring(3, 6)} ${d.substring(6, 9)} ${d.substring(9)}"
            else -> null
        }
    }

    @Test fun `numbers and names are told apart`() {
        assertTrue(Recipients.looksLikeNumber("0412 345 678"))
        assertTrue(Recipients.looksLikeNumber("+61 (4) 12-345.678"))
        assertFalse(Recipients.looksLikeNumber("Mum"))
        assertFalse(Recipients.looksLikeNumber("Agent 47"))
        assertFalse(Recipients.looksLikeNumber("  "))
        assertFalse(Recipients.looksLikeNumber("+"))
    }

    @Test fun `dialable keeps digits and a leading plus`() {
        assertEquals("0412345678", Recipients.dialable("0412 345-678"))
        assertEquals("+61412345678", Recipients.dialable("+61 (412) 345 678"))
        assertEquals("1234", Recipients.dialable("12+34"))
    }

    @Test fun `short codes are never checked`() {
        assertTrue(Recipients.isShortCode("1234"))
        assertFalse(Recipients.shouldCheck("123456"))
        assertFalse(Recipients.shouldCheck("0412345"))
        assertTrue(Recipients.shouldCheck("04123456"))
        assertTrue(Recipients.shouldCheck("+61412345"))
    }

    @Test fun `display formats complete numbers and leaves the rest alone`() {
        assertEquals("0412 345 678", Recipients.display("0412345678", au))
        assertEquals("+61 412 345 678", Recipients.display("+61412345678", au))
        assertEquals("04123", Recipients.display("04123", au))
        // A formatter that changes the digits is ignored.
        assertEquals("0412345678", Recipients.display("0412345678") { "+61 412 345 678" })
    }

    @Test fun `cursor offsets skip the spaces`() {
        val (toDisplay, toDialable) = Recipients.offsets("0412345678", "0412 345 678")
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 5, 6, 7, 9, 10, 11, 12), toDisplay)
        assertEquals(4, toDialable[4])
        assertEquals(4, toDialable[5])
        assertEquals(10, toDialable[12])
    }
}
