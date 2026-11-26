package io.github.michaelmassoni.tessera.contacts.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class RawChoiceTest {
    private val signal = AccountRef("Signal", "org.thoughtcrime.securesms")
    private val dav = AccountRef("me@cloud", "io.github.michaelmassoni.tessera.contacts.dav")

    @Test
    fun `edits go to the person's own copy, not an app's`() {
        val raws = linkedMapOf(1L to signal, 2L to dav)
        assertEquals(2L, RawChoice.editable(raws, appTypes = setOf("org.thoughtcrime.securesms")))
        assertEquals(3L, RawChoice.editable(linkedMapOf(1L to signal, 3L to AccountRef.Device), setOf("org.thoughtcrime.securesms")))
        // Only an app's copy: that's all there is.
        assertEquals(1L, RawChoice.editable(linkedMapOf(1L to signal), setOf("org.thoughtcrime.securesms")))
    }

    @Test
    fun `each number and email once, the own copy's row kept`() {
        val raw = mapOf(10L to 1L, 11L to 1L, 20L to 2L, 21L to 2L, 22L to 2L)
        val phones = listOf(Labeled("0408 853 468", 2, id = 10), Labeled("+61 408 853 468", 2, id = 20), Labeled("0408853468", 2, id = 21))
        assertEquals(listOf(20L), RawChoice.distinct(phones, { raw[it.id] }, preferredRaw = 2L, key = RawChoice::phoneKey).map { it.id })
        val emails =
            listOf(Labeled("Dom@Greenpet.com.au", 2, id = 11), Labeled("dom@greenpet.com.au", 2, id = 22), Labeled("x@y.org", 1, id = 23))
        assertEquals(
            listOf(22L, 23L),
            RawChoice.distinct(emails, { raw[it.id] }, preferredRaw = 2L, key = RawChoice::emailKey).map { it.id },
        )
    }
}
