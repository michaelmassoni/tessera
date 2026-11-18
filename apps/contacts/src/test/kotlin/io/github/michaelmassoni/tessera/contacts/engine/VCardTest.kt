package io.github.michaelmassoni.tessera.contacts.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class VCardTest {
    @Test fun `vCard 3 with folding, escapes, types and several contacts`() {
        val text =
            """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ada Lovelace
            N:Lovelace;Ada;King;;
            TEL;TYPE=CELL,VOICE:+44 20 7946 0000
            TEL;TYPE=WORK:020 7946 0001
            EMAIL;TYPE=INTERNET:ada@example.org
            ADR;TYPE=HOME:;;12 St James\, Square;London;;SW1Y 4JH;UK
            ORG:Analytical Engines Ltd;Research
            BDAY:1815-12-10
            NOTE:First line\nSecond line with a very long text that is folded onto the
              next line
            END:VCARD
            BEGIN:VCARD
            VERSION:3.0
            FN:Charles Babbage
            END:VCARD
            """.trimIndent()
        val (ada, charles) = VCard.parse(text)
        assertEquals("Ada King" to "Lovelace", ada.names())
        assertEquals(listOf("+44 20 7946 0000" to setOf("CELL", "VOICE"), "020 7946 0001" to setOf("WORK")), ada.phones)
        assertEquals("ada@example.org", ada.emails.single().first)
        assertEquals("12 St James, Square\nLondon SW1Y 4JH\nUK", ada.addresses.single().first)
        assertEquals("Analytical Engines Ltd, Research", ada.organization)
        assertEquals("1815-12-10", ada.birthday)
        assertEquals("First line\nSecond line with a very long text that is folded onto the next line", ada.note)
        assertEquals("Charles" to "Babbage", charles.names())
    }

    @Test fun `vCard 2_1 bare types and quoted-printable UTF-8`() {
        val text =
            "BEGIN:VCARD\r\nVERSION:2.1\r\nN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:M=C3=BCller;J=C3=BC=\r\nrgen\r\n" +
                "TEL;CELL;PREF:0412345678\r\nitem1.EMAIL;HOME:j@example.de\r\nEND:VCARD\r\n"
        val c = VCard.parse(text).single()
        assertEquals("Jürgen" to "Müller", c.names())
        assertEquals("0412345678" to setOf("CELL", "PREF"), c.phones.single())
        assertEquals("j@example.de", c.emails.single().first)
    }

    @Test fun `vCard 4 uri values and dates without a year`() {
        val text = "BEGIN:VCARD\nVERSION:4.0\nFN:X\nTEL;VALUE=uri;TYPE=\"voice,home\":tel:+1-555-0100\nBDAY:--0423\nEND:VCARD"
        val c = VCard.parse(text).single()
        assertEquals("+1-555-0100" to setOf("VOICE", "HOME"), c.phones.single())
        assertEquals("--04-23", c.birthday)
        assertEquals("1990-04-23", VCard.normalizeDate("19900423"))
    }

    @Test fun `garbage and empty input give no contacts`() {
        assertEquals(emptyList<VCardContact>(), VCard.parse(""))
        assertEquals(emptyList<VCardContact>(), VCard.parse("hello\nworld"))
    }
}
