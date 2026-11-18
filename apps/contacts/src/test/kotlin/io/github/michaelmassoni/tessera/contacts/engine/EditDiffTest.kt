package io.github.michaelmassoni.tessera.contacts.engine

import io.github.michaelmassoni.tessera.contacts.engine.EditDiff.DATA1
import io.github.michaelmassoni.tessera.contacts.engine.EditDiff.DATA2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditDiffTest {
    private val original =
        ContactForm(
            givenName = "Ada",
            familyName = "Lovelace",
            nameId = 1,
            phones = listOf(Labeled("111", 2, id = 10), Labeled("222", 1, id = 11)),
            emails = listOf(Labeled("a@x.org", 1, id = 20)),
            company = "AE",
            companyId = 30,
            note = "hi",
            noteId = 40,
        )

    @Test fun `no change writes nothing`() {
        assertTrue(EditDiff.diff(original, original).isEmpty())
    }

    @Test fun `changes, removals and additions map to the right rows`() {
        val edited =
            original.copy(
                familyName = "King",
                phones = listOf(Labeled("111", 3, id = 10), Labeled("333", 2)),
                emails = listOf(Labeled("", 1, id = 20)),
                company = "",
                birthday = "1815-12-10",
            )
        val ops = EditDiff.diff(original, edited)
        assertEquals(
            setOf(
                RowOp.Update(RowKind.Name, 1, mapOf(EditDiff.DATA2 to "Ada", EditDiff.DATA3 to "King", DATA1 to "Ada King")),
                RowOp.Delete(RowKind.Organization, 30),
                RowOp.Insert(RowKind.Birthday, mapOf(DATA1 to "1815-12-10", DATA2 to EditDiff.EVENT_TYPE_BIRTHDAY)),
                RowOp.Update(RowKind.Phone, 10, mapOf(DATA1 to "111", DATA2 to 3, EditDiff.DATA3 to null)),
                RowOp.Insert(RowKind.Phone, mapOf(DATA1 to "333", DATA2 to 2, EditDiff.DATA3 to null)),
                RowOp.Delete(RowKind.Phone, 11),
                RowOp.Delete(RowKind.Email, 20),
            ),
            ops.toSet(),
        )
    }

    @Test fun `a new contact inserts every filled field and skips blanks`() {
        val form = ContactForm(givenName = "New", phones = listOf(Labeled("5", 2), Labeled(" ", 2)), note = "")
        val ops = EditDiff.diff(ContactForm(), form)
        assertEquals(listOf(RowKind.Name, RowKind.Phone), ops.map { it.kind })
        assertTrue(ops.all { it is RowOp.Insert })
    }
}
