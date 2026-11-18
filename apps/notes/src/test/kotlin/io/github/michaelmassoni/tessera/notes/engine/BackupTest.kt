package io.github.michaelmassoni.tessera.notes.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    @Test fun `export and import round-trip with new ids, skipping trash`() {
        val notes =
            listOf(
                Note("a", title = "T", body = "B", labels = setOf("x"), pinned = true, created = 1, modified = 2),
                Note("b", checklist = listOf(ChecklistItem("i", checked = true)), created = 3, modified = 4),
                Note("c", body = "gone", trashedAt = 9),
            )
        var n = 0
        val imported = Backup.decode(Backup.encode(notes)) { "new${n++}" }
        assertEquals(listOf("new0", "new1"), imported.map { it.id })
        assertEquals(notes.take(2).map { it.copy(id = "") }, imported.map { it.copy(id = "") })
    }

    @Test fun `unknown fields are ignored but other files and newer versions are rejected`() {
        val extra = """{"format":"tessera-notes","version":1,"future":true,"notes":[{"id":"x","body":"hi","colour":"red"}]}"""
        assertEquals("hi", Backup.decode(extra) { "y" }.single().body)
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("""{"format":"other","notes":[]}""") { "" } }
        assertThrows(
            IllegalArgumentException::class.java,
        ) { Backup.decode("""{"format":"tessera-notes","version":99,"notes":[]}""") { "" } }
        assertThrows(IllegalArgumentException::class.java) { Backup.decode("not json") { "" } }
    }
}
