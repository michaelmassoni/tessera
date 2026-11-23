package io.github.michaelmassoni.tessera.contacts.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardMappingTest {
    private fun card(lines: String) =
        CardMapping.parse("BEGIN:VCARD\r\n$lines\r\nEND:VCARD\r\n".replace("\n", "\r\n").replace("\r\r", "\r"))!!

    private val apple =
        card(
            """
            VERSION:3.0
            PRODID:-//Apple Inc.//iPhone OS 18//EN
            UID:sam-1
            N:Smith;Sam;J;Dr;Jr
            FN:Dr Sam J Smith Jr
            NICKNAME:Sammy
            ORG:Acme;R&D;Labs
            TITLE:Engineer
            item1.TEL;type=CELL;type=VOICE;type=pref:+61 400 111 222
            TEL;type=WORK;type=VOICE:03 9000 0000
            item2.TEL:0400 999 888
            item2.X-ABLabel:Holiday house
            EMAIL;type=INTERNET;type=HOME:sam@example.com
            item3.ADR;type=HOME:;;1 Main St\nUnit 2;Melbourne;VIC;3000;Australia
            URL:https://sam.example.com
            BDAY;X-APPLE-OMIT-YEAR=1604:1604-04-23
            item4.X-ABRELATEDNAMES:Alex
            item4.X-ABLabel:_${'$'}!<Spouse>!${'$'}_
            NOTE:Met at work\, 2019
            CATEGORIES:Friends,Work
            X-SOCIALPROFILE;type=twitter:x.com/sam
            END:VCARD
            """.trimIndent().removeSuffix("\nEND:VCARD").removePrefix(""),
        )

    @Test
    fun `reads an Apple vCard 3`() {
        val c = CardMapping.readContact(apple)!!
        assertEquals("sam-1", c.uid)
        assertEquals(listOf("Sam", "Smith", "J", "Dr", "Jr"), listOf(c.given, c.family, c.middle, c.prefix, c.suffix))
        assertEquals("Sammy", c.nickname)
        assertEquals("Acme", c.organization)
        assertEquals("R&D, Labs", c.department)
        assertEquals(Typed("+61 400 111 222", CardTypes.PHONE_MOBILE), c.phones[0])
        assertEquals(Typed("03 9000 0000", CardTypes.PHONE_WORK), c.phones[1])
        assertEquals(Typed("0400 999 888", CardTypes.CUSTOM, "Holiday house"), c.phones[2])
        assertEquals(Typed("sam@example.com", CardTypes.EMAIL_HOME), c.emails.single())
        val a = c.addresses.single()
        assertEquals("1 Main St\nUnit 2", a.street)
        assertEquals("Melbourne", a.city)
        assertEquals("3000", a.postcode)
        assertEquals("--04-23", c.birthday)
        assertEquals(Typed("Alex", CardTypes.RELATION_SPOUSE), c.relations.single())
        assertEquals("Met at work, 2019", c.note)
        assertEquals(listOf("Friends", "Work"), c.categories)
        assertEquals(listOf("https://sam.example.com"), c.websites)
    }

    @Test
    fun `reads vCard 4 dates and anniversaries`() {
        val c =
            CardMapping.readContact(
                card("VERSION:4.0\nUID:urn:uuid:x-2\nFN:X\nBDAY:19900423\nANNIVERSARY:--0612\nTEL;VALUE=uri;TYPE=work:tel:+61-3-9000"),
            )!!
        assertEquals("x-2", c.uid)
        assertEquals("1990-04-23", c.birthday)
        assertEquals("--06-12", c.anniversary)
        assertEquals(Typed("+61-3-9000", CardTypes.PHONE_WORK), c.phones.single())
    }

    @Test
    fun `writing back keeps unmapped properties and unchanged ones`() {
        val c = CardMapping.readContact(apple)!!
        val out = CardMapping.writeContact(c.copy(title = "Lead engineer"), apple)
        assertTrue(out.contains("TITLE:Lead engineer"))
        assertTrue(out.contains("X-SOCIALPROFILE;TYPE=twitter:x.com/sam"))
        assertTrue(out.contains("PRODID:-//Apple Inc.//iPhone OS 18//EN"))
        // Phones didn't change: Apple's grouped lines stay as they were (names are case-insensitive;
        // the parser writes them in capitals).
        assertTrue(out.contains("ITEM2.X-ABLABEL:Holiday house"))
        assertTrue(out.contains("ITEM1.TEL;TYPE=CELL;TYPE=VOICE;TYPE=pref:+61 400 111 222"))
        assertEquals(c.copy(title = "Lead engineer"), CardMapping.readContact(CardMapping.parse(out)!!))
    }

    @Test
    fun `changed lists are rewritten with labels`() {
        val c = CardMapping.readContact(apple)!!
        val phones = listOf(Typed("111", CardTypes.PHONE_HOME), Typed("222", CardTypes.CUSTOM, "Boat"))
        val out = CardMapping.writeContact(c.copy(phones = phones, categories = listOf("Family")), apple)
        assertFalse(out.contains("+61 400 111 222"))
        assertFalse(out.contains("Holiday house"))
        val back = CardMapping.readContact(CardMapping.parse(out)!!)!!
        assertEquals(phones, back.phones)
        assertEquals(listOf("Family"), back.categories)
        // The relation's Apple label is still there and still reads as a spouse.
        assertEquals(Typed("Alex", CardTypes.RELATION_SPOUSE), back.relations.single())
    }

    @Test
    fun `a new contact is a complete vCard 3`() {
        val c =
            CardContact(
                uid = "new-1",
                given = "Jo",
                family = "Lee; Jr",
                phones = listOf(Typed("0400 000 000", CardTypes.PHONE_MOBILE)),
                addresses = listOf(PostalAddress(street = "2 High St", city = "Ballarat", type = CardTypes.POSTAL_WORK)),
                birthday = "--12-01",
            )
        val out = CardMapping.writeContact(c, null)
        assertTrue(out.startsWith("BEGIN:VCARD\r\nVERSION:3.0\r\n"))
        assertTrue(out.contains("FN:Jo Lee\\; Jr"))
        assertTrue(out.contains("N:Lee\\; Jr;Jo;;;"))
        assertTrue(out.contains("TEL;TYPE=CELL:0400 000 000"))
        assertTrue(out.contains("BDAY;X-APPLE-OMIT-YEAR=1604:1604-12-01"))
        val back = CardMapping.readContact(CardMapping.parse(out)!!)!!
        assertEquals(c.copy(formattedName = "Jo Lee; Jr"), back)
    }

    @Test
    fun `groups in both styles`() {
        val v4 = card("VERSION:4.0\nUID:g-1\nKIND:group\nFN:Family\nMEMBER:urn:uuid:sam-1\nMEMBER:urn:uuid:x-2")
        assertTrue(CardMapping.isGroup(v4))
        assertEquals(CardGroup("g-1", "Family", listOf("sam-1", "x-2")), CardMapping.readGroup(v4))
        val out = CardMapping.writeGroup(CardGroup("g-1", "Family", listOf("sam-1")), v4)
        val back = CardMapping.parse(out)!!
        assertTrue(CardMapping.isGroup(back))
        assertEquals(CardGroup("g-1", "Family", listOf("sam-1")), CardMapping.readGroup(back))
        assertTrue(out.contains("X-ADDRESSBOOKSERVER-MEMBER:urn:uuid:sam-1"))
        assertFalse(CardMapping.isGroup(apple))
    }

    @Test
    fun `photos in, out and removed`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val text = CardMapping.withPhoto(CardMapping.writeContact(CardContact("p-1", given = "P"), null), bytes)
        assertArrayEquals(bytes, CardMapping.photo(CardMapping.parse(text)!!))
        assertNull(CardMapping.photo(CardMapping.parse(CardMapping.withPhoto(text, null))!!))
        assertArrayEquals(bytes, CardMapping.photo(card("VERSION:4.0\nUID:u\nPHOTO:data:image/jpeg;base64,AQIDBAU=")))
    }

    @Test
    fun `structured values split on unescaped semicolons`() {
        assertEquals(listOf("a;b", "c", ""), CardMapping.components("a\\;b;c;"))
    }
}
