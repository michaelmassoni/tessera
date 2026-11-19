package io.github.michaelmassoni.tessera.messages.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactNamesTest {
    private val contacts =
        listOf(
            "+61 412 345 678" to "Ada Lovelace",
            "(02) 9876 5432" to "Grace Hopper",
            "1234" to "Voicemail",
            "0400 000 000" to "",
        )

    @Test fun `numbers match however they're written`() {
        val names = Addresses.names(listOf("0412345678", "+61298765432", "1234", "0499999999", "0400000000"), contacts)
        assertEquals(
            mapOf("0412345678" to "Ada Lovelace", "+61298765432" to "Grace Hopper", "1234" to "Voicemail"),
            names,
        )
    }

    @Test fun `the first contact with a number wins`() {
        val names = Addresses.names(listOf("0412345678"), contacts + ("0412345678" to "Someone else"))
        assertEquals("Ada Lovelace", names["0412345678"])
    }

    @Test fun `keys agree with same`() {
        assertEquals(Addresses.key("+61 412 345 678"), Addresses.key("0412345678"))
        assertEquals("1234", Addresses.key("12-34"))
    }
}
