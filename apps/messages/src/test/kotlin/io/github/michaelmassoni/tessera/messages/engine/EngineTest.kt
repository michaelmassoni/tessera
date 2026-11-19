package io.github.michaelmassoni.tessera.messages.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class EngineTest {
    @Test fun `sms segments for gsm and unicode`() {
        assertEquals(SmsLength.Info(1, 155, false), SmsLength.info("Hello"))
        assertEquals(1, SmsLength.info("a".repeat(160)).segments)
        assertEquals(SmsLength.Info(2, 145, false), SmsLength.info("a".repeat(161)))
        assertEquals(SmsLength.Info(1, 158, false), SmsLength.info("€"))
        assertTrue(SmsLength.info("Hi 😀").unicode)
        assertEquals(SmsLength.Info(2, 62, true), SmsLength.info("ü😀" + "x".repeat(69)))
    }

    @Test fun `addresses normalise and match`() {
        assertEquals("+61412345678", Addresses.normalize("+61 412 345 678"))
        assertTrue(Addresses.same("+61 412 345 678", "0412 345 678"))
        assertFalse(Addresses.same("0412345678", "0412345679"))
        assertTrue(Addresses.same("123", "123"))
        assertTrue(Addresses.same("Telstra", "telstra"))
    }

    private fun at(
        d: Int,
        h: Int,
        m: Int,
    ) = LocalDateTime.of(2026, 11, d, h, m).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun `rows have day separators and group bubbles`() {
        val msgs =
            listOf(
                Message("1", "hi", at(18, 9, 0), outgoing = false),
                Message("2", "there", at(18, 9, 1), outgoing = false),
                Message("3", "hey", at(18, 9, 2), outgoing = true),
                Message("4", "later", at(19, 8, 0), outgoing = true),
            )
        val rows = Grouping.rows(msgs, ZoneOffset.UTC)
        assertEquals(6, rows.size)
        assertTrue(rows[0] is Row.Day)
        assertEquals(listOf(false, true, true), rows.subList(1, 4).map { (it as Row.Bubble).lastInGroup })
        assertTrue(rows[4] is Row.Day)
    }
}
