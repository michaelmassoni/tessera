package io.github.michaelmassoni.tessera.files.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

class FileOperationsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val ops = FileOperations(freeSpace = { it.usableSpace })

    private fun file(
        path: String,
        content: String = path,
    ) = File(tmp.root, path).apply {
        parentFile!!.mkdirs()
        writeText(content)
    }

    private fun dir(path: String) = File(tmp.root, path).apply { mkdirs() }

    @Test fun `copy a file and a folder tree`() {
        val a = file("src/a.txt", "hello")
        file("src/folder/b.txt", "b")
        file("src/folder/deep/c.txt", "c")
        val target = dir("dst")
        val result = ops.copy(listOf(a, File(tmp.root, "src/folder")), target)
        assertEquals(listOf("a.txt", "folder"), result.map { it.name })
        assertEquals("hello", File(target, "a.txt").readText())
        assertEquals("c", File(target, "folder/deep/c.txt").readText())
        assertTrue(a.exists()) // copy keeps the source
    }

    @Test fun `copy never overwrites, it renames`() {
        val a = file("src/a.txt", "new")
        file("dst/a.txt", "old")
        file("dst/a (1).txt", "older")
        val result = ops.copy(listOf(a), File(tmp.root, "dst"))
        assertEquals("a (2).txt", result.single().name)
        assertEquals("old", File(tmp.root, "dst/a.txt").readText())
    }

    @Test fun `copying into the same folder makes a numbered copy`() {
        val a = file("x/report.pdf", "pdf")
        assertEquals("report (1).pdf", ops.copy(listOf(a), a.parentFile!!).single().name)
    }

    @Test fun `can't copy or move a folder into itself`() {
        val folder = dir("folder")
        val inner = dir("folder/inner")
        assertThrows(OperationException.IntoItself::class.java) { ops.copy(listOf(folder), inner) }
        assertThrows(OperationException.IntoItself::class.java) { ops.move(listOf(folder), folder) }
    }

    @Test fun `not enough space is checked before writing anything`() {
        val big = file("src/big.bin", "x".repeat(1000))
        val target = dir("dst")
        val tiny = FileOperations(freeSpace = { 10 })
        assertThrows(OperationException.NotEnoughSpace::class.java) { tiny.copy(listOf(big), target) }
        assertTrue(target.list()!!.isEmpty())
    }

    @Test fun `cancelled copy leaves no partial file`() {
        val big = file("src/big.bin", "x".repeat(2_000_000))
        val target = dir("dst")
        val cancel = AtomicBoolean(false)
        assertThrows(OperationException.Cancelled::class.java) {
            ops.copy(listOf(big), target, cancel) { p -> if (p.bytesDone > 0) cancel.set(true) }
        }
        assertFalse(File(target, "big.bin").exists())
    }

    @Test fun `progress reaches the totals`() {
        file("src/a.txt", "aaaa")
        file("src/b.txt", "bb")
        var last: Progress? = null
        ops.copy(File(tmp.root, "src").listFiles()!!.toList(), dir("dst")) { last = it }
        assertEquals(2, last!!.filesDone)
        assertEquals(6L, last.bytesDone)
        assertEquals(6L, last.bytesTotal)
    }

    @Test fun `move within a volume renames and keeps contents`() {
        val a = file("src/a.txt", "data")
        file("src/sub/b.txt", "b")
        val target = dir("dst")
        val moved = ops.move(listOf(a, File(tmp.root, "src/sub")), target)
        assertEquals(listOf("a.txt", "sub"), moved.map { it.name })
        assertFalse(a.exists())
        assertEquals("b", File(target, "sub/b.txt").readText())
    }

    @Test fun `move into the folder it is already in is a no-op`() {
        val a = file("src/a.txt", "data")
        assertEquals(a, ops.move(listOf(a), a.parentFile!!).single())
        assertTrue(a.exists())
    }

    @Test fun `delete is recursive and does not follow links`() {
        val keep = file("outside/keep.txt", "precious")
        val folder = dir("victim")
        file("victim/a.txt")
        Files.createSymbolicLink(File(folder, "link").toPath(), keep.parentFile!!.toPath())
        ops.delete(listOf(folder))
        assertFalse(folder.exists())
        assertTrue("deleting a link must not delete its target", keep.exists())
    }

    @Test fun `links are copied as links`() {
        val target = file("t.txt", "t")
        val link = File(dir("src"), "l").also { Files.createSymbolicLink(it.toPath(), target.toPath()) }
        val copy = ops.copy(listOf(link), dir("dst")).single()
        assertTrue(Files.isSymbolicLink(copy.toPath()))
    }

    @Test fun `move across volumes copies then deletes the sources`() {
        val crossVolume = FileOperations(freeSpace = { it.usableSpace }, rename = { _, _ -> false })
        val a = file("src/a.txt", "data")
        file("src/sub/b.txt", "b")
        val moved = crossVolume.move(listOf(a, File(tmp.root, "src/sub")), dir("dst"))
        assertEquals(listOf("a.txt", "sub"), moved.map { it.name })
        assertFalse(a.exists())
        assertFalse(File(tmp.root, "src/sub").exists())
        assertEquals("b", File(tmp.root, "dst/sub/b.txt").readText())
    }

    @Test fun `failed cross-volume move keeps every source`() {
        val crossVolume = FileOperations(rename = { _, _ -> false }, freeSpace = { 1 })
        val a = file("src/a.txt", "data")
        assertThrows(OperationException.NotEnoughSpace::class.java) { crossVolume.move(listOf(a), dir("dst")) }
        assertTrue(a.exists())
    }
}
