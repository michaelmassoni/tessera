package io.github.michaelmassoni.tessera.files.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchivesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun tree(): File {
        val dir = tmp.newFolder("Trip")
        File(dir, "notes.txt").writeText("hello")
        File(dir, "photos").mkdirs()
        File(dir, "photos/a.jpg").writeBytes(ByteArray(300_000) { it.toByte() })
        return dir
    }

    @Test
    fun `compress then extract gives back the same files`() {
        val dir = tree()
        val zip = Archives.compress(listOf(dir), tmp.root)
        assertEquals("Trip.zip", zip.name)
        val out = Archives.extract(zip)
        assertEquals("Trip (1)", out.name) // "Trip" exists beside it: never overwritten
        assertEquals("hello", File(out, "Trip/notes.txt").readText())
        assertTrue(File(out, "Trip/photos/a.jpg").readBytes().contentEquals(File(dir, "photos/a.jpg").readBytes()))
    }

    @Test
    fun `progress reaches the total`() {
        var last: Progress? = null
        Archives.compress(listOf(tree()), tmp.root) { last = it }
        val end = last!!
        assertEquals(end.bytesTotal, end.bytesDone)
        assertEquals(2, end.filesTotal)
    }

    @Test
    fun `an entry pointing outside the folder is refused and nothing is left`() {
        val zip = File(tmp.root, "evil.zip")
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry("ok.txt"))
            it.write(1)
            it.putNextEntry(ZipEntry("../escaped.txt"))
            it.write(2)
        }
        try {
            Archives.extract(zip)
            error("should have refused")
        } catch (_: Archives.Unsafe) {
        }
        assertFalse(File(tmp.root, "escaped.txt").exists())
        assertFalse(File(tmp.root, "evil").exists())
    }

    @Test
    fun `cancelling leaves no half-made zip`() {
        val dir = tree()
        try {
            Archives.compress(listOf(dir), tmp.root, AtomicBoolean(true))
            error("should have been cancelled")
        } catch (_: OperationException.Cancelled) {
        }
        assertFalse(File(tmp.root, "Trip.zip").exists())
    }

    @Test
    fun `a broken zip fails cleanly`() {
        val zip = File(tmp.root, "broken.zip").apply { writeText("not a zip") }
        try {
            Archives.extract(zip)
            error("should have failed")
        } catch (_: java.io.IOException) {
        }
        assertFalse(File(tmp.root, "broken").exists())
    }
}
