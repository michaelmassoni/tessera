package io.github.michaelmassoni.tessera.files.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashTest {
    @get:Rule val tmp = TemporaryFolder()

    private var time = 1_000_000L

    private fun setup(): Triple<File, Trash, File> {
        val root = tmp.newFolder("volume")
        val trash = Trash(File(tmp.root, "app/trash.json")) { time }
        return Triple(root, trash, File(root, "Documents").apply { mkdirs() })
    }

    @Test fun `moving to the trash hides the file and restoring puts it back`() {
        val (root, trash, docs) = setup()
        val file = File(docs, "a.txt").apply { writeText("hello") }
        val items = trash.move(listOf(file)) { root }
        assertFalse(file.exists())
        assertTrue(File(root, ".Trash-tessera/.nomedia").exists())
        assertEquals("a.txt", items.single().name)
        assertEquals(5L, items.single().size)
        assertEquals(listOf(file), trash.restore(items.map { it.id }))
        assertEquals("hello", file.readText())
        assertTrue(trash.items().isEmpty())
    }

    @Test fun `restoring over a new file of the same name renames it`() {
        val (root, trash, docs) = setup()
        val file = File(docs, "a.txt").apply { writeText("old") }
        val item = trash.move(listOf(file)) { root }.single()
        file.writeText("new")
        val restored = trash.restore(listOf(item.id)).single()
        assertEquals("a (1).txt", restored.name)
        assertEquals("old", restored.readText())
        assertEquals("new", file.readText())
    }

    @Test fun `folders go whole, and delete, empty and purge remove them for good`() {
        val (root, trash, docs) = setup()
        val folder = File(docs, "Trip").apply { mkdirs() }
        File(folder, "1.jpg").writeText("12345")
        val other = File(docs, "b.txt").apply { writeText("b") }
        val (trip, b) = trash.move(listOf(folder, other)) { root }
        assertTrue(trip.isDirectory)
        assertEquals(5L, trip.size)
        trash.delete(listOf(b.id))
        assertFalse(File(b.trashedPath).exists())
        assertEquals(listOf(trip.id), trash.items().map { it.id })
        assertEquals(0, trash.purge())
        time += Trash.KEEP_MILLIS
        assertEquals(1, trash.purge())
        assertFalse(File(trip.trashedPath).exists())
        assertTrue(trash.items().isEmpty())
    }
}
