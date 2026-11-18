package io.github.michaelmassoni.tessera.notes.data

import io.github.michaelmassoni.tessera.notes.engine.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NoteRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private var clock = 1_000L

    private fun repo() = NoteRepository(File(tmp.root, "notes"), now = { clock }).apply { load() }

    @Test fun `notes survive a reload and saving stamps the modified time`() {
        val r = repo()
        val note = r.create().copy(title = "Hello")
        clock = 5_000
        r.save(note)
        val reloaded = repo().get(note.id)!!
        assertEquals("Hello", reloaded.title)
        assertEquals(5_000, reloaded.modified)
        assertEquals(1_000, reloaded.created)
    }

    @Test fun `blank notes are not kept`() {
        val r = repo()
        val note = r.save(r.create().copy(title = "x"))!!
        assertNull(r.save(note.copy(title = " ")))
        assertNull(repo().get(note.id))
        assertNull(r.save(r.create(checklist = true)))
    }

    @Test fun `a corrupt file is skipped, not fatal, and no temp files are left behind`() {
        val r = repo()
        r.save(r.create().copy(body = "ok"))
        File(tmp.root, "notes/broken.json").writeText("{not json")
        assertEquals(1, repo().notes.value.size)
        assertFalse(File(tmp.root, "notes").listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test fun `trash, restore, purge after seven days and empty trash`() {
        val r = repo()
        val a = r.save(r.create().copy(body = "a", pinned = true))!!
        val b = r.save(r.create().copy(body = "b"))!!
        r.trash(listOf(a.id))
        assertTrue(r.get(a.id)!!.isTrashed)
        assertFalse(r.get(a.id)!!.pinned)
        r.restore(listOf(a.id))
        assertFalse(r.get(a.id)!!.isTrashed)
        r.trash(listOf(a.id, b.id))
        clock += 6 * 86_400_000L
        r.purgeTrash()
        assertEquals(2, r.notes.value.size)
        clock += 2 * 86_400_000L
        r.purgeTrash()
        assertTrue(r.notes.value.isEmpty())
        assertTrue(repo().notes.value.isEmpty())
    }

    @Test fun `empty trash keeps other notes`() {
        val r = repo()
        val keep = r.save(Note(r.newId(), body = "keep"))!!
        val drop = r.save(Note(r.newId(), body = "drop"))!!
        r.trash(listOf(drop.id))
        r.emptyTrash()
        assertEquals(setOf(keep.id), r.notes.value.keys)
    }
}
