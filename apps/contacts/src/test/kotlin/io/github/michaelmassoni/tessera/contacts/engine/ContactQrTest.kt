package io.github.michaelmassoni.tessera.contacts.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ContactQrTest {
    private fun detail(
        name: String = "Ada Lovelace",
        phones: List<Labeled> = emptyList(),
        emails: List<Labeled> = emptyList(),
        addresses: List<Labeled> = emptyList(),
        company: String = "",
        form: ContactForm = ContactForm(givenName = "Ada", familyName = "Lovelace"),
    ) = ContactDetail(
        id = 1,
        lookupKey = "k",
        name = name,
        starred = false,
        photoUri = "content://photo",
        phones = phones,
        emails = emails,
        addresses = addresses,
        events = emptyList(),
        websites = emptyList(),
        company = company,
        title = "",
        note = "Private note",
        accounts = emptySet(),
        rawContactId = 1,
        form = form,
    )

    @Test
    fun writesNameNumbersAndEmails() {
        val card =
            ContactQr.vcard(
                detail(
                    phones = listOf(Labeled("0400 555 123", 2), Labeled("02 5550 1234", 7)),
                    emails = listOf(Labeled("ada@example.com", 2)),
                    company = "Engines, Ltd",
                ),
            )
        assertEquals(
            "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Lovelace;Ada;;;\r\nFN:Ada Lovelace\r\nTEL;TYPE=CELL:0400 555 123\r\n" +
                "TEL:02 5550 1234\r\nEMAIL;TYPE=WORK:ada@example.com\r\nORG:Engines\\, Ltd\r\nEND:VCARD\r\n",
            card,
        )
    }

    @Test
    fun leavesOutPhotoAndNote() {
        val card = ContactQr.vcard(detail())
        assertFalse(card.contains("PHOTO"))
        assertFalse(card.contains("NOTE"))
    }

    @Test
    fun noNameWithoutParts() {
        val card = ContactQr.vcard(detail(name = "0400 555 123", form = ContactForm()))
        assertFalse(card.contains("\r\nN:"))
        assertEquals(true, card.contains("FN:0400 555 123"))
    }

    @Test
    fun addressGoesInStreet() {
        val card = ContactQr.vcard(detail(addresses = listOf(Labeled("1 Example St\nSydney", 1))))
        assertEquals(true, card.contains("ADR:;;1 Example St\\nSydney;;;;\r\n"))
    }
}
