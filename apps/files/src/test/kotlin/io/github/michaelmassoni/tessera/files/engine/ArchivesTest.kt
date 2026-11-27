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
            Archives.compress(listOf(dir), tmp.root, cancelled = AtomicBoolean(true))
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

    @Test
    fun `a chosen name and tar gz round trip`() {
        val dir = tree()
        val tgz = Archives.compress(listOf(dir), tmp.root, CompressOptions("Holiday", ArchiveFormat.TarGz))
        assertEquals("Holiday.tar.gz", tgz.name)
        // Same name again: numbered before the whole extension.
        assertEquals("Holiday (1).tar.gz", Archives.compress(listOf(dir), tmp.root, CompressOptions("Holiday", ArchiveFormat.TarGz)).name)
        val out = Archives.extract(tgz)
        assertEquals("Holiday", out.name)
        assertEquals("hello", File(out, "Trip/notes.txt").readText())
        assertTrue(File(out, "Trip/photos/a.jpg").readBytes().contentEquals(File(dir, "photos/a.jpg").readBytes()))
    }

    @Test
    fun `summaries count files and size for zip and tar gz`() {
        val dir = tree()
        for (format in listOf(ArchiveFormat.Zip, ArchiveFormat.TarGz)) {
            val a = Archives.compress(listOf(dir), tmp.root, CompressOptions("s", format))
            val s = Archives.summary(a)
            assertEquals(format.name, 2, s.files)
            assertEquals(format.name, 2, s.folders)
            assertEquals(format.name, 300_005L, s.bytes)
            assertEquals(format.name, "Trip/", s.sample.first())
        }
    }

    @Test
    fun `a tar entry pointing outside the folder is refused`() {
        val tar = File(tmp.root, "evil.tar")
        org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(tar.outputStream()).use { out ->
            val bytes = "x".toByteArray()
            out.putArchiveEntry(
                org.apache.commons.compress.archivers.tar.TarArchiveEntry("../escaped.txt").apply {
                    size =
                        bytes.size.toLong()
                },
            )
            out.write(bytes)
            out.closeArchiveEntry()
        }
        try {
            Archives.extract(tar)
            error("should have refused")
        } catch (_: Archives.Unsafe) {
        }
        assertFalse(File(tmp.root, "escaped.txt").exists())
        assertFalse(File(tmp.root, "evil").exists())
    }

    @Test
    fun `formats and base names come from the file name`() {
        assertEquals(ArchiveFormat.TarGz, ArchiveFormat.of(File("a.TGZ")))
        assertEquals(ArchiveFormat.Tar, ArchiveFormat.of(File("a.tar")))
        assertEquals(null, ArchiveFormat.of(File("a.gz")))
        assertEquals("photos", ArchiveFormat.baseName(File("photos.tar.gz")))
        assertEquals("x.y", ArchiveFormat.baseName(File("x.y.zip")))
    }

    @Test
    fun `a damaged archive is reported as unreadable`() {
        val bad = File(tmp.root, "bad.tar.gz").apply { writeText("not gzip") }
        try {
            Archives.extract(bad)
            error("should have failed")
        } catch (e: java.io.IOException) {
            assertTrue(e.message.orEmpty(), e !is Archives.Unsafe)
        }
        assertFalse(File(tmp.root, "bad").exists())
    }
}
