package io.github.michaelmassoni.tessera.files.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Locale

class SortingAndSearchTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun entry(
        name: String,
        dir: Boolean = false,
        size: Long = 0,
        modified: Long = 0,
    ) = FileEntry(File(name), dir, size, modified, if (dir) 0 else null)

    @Test fun `natural name order with folders first`() {
        val sorted =
            Sorting.sort(
                listOf(entry("file10.txt"), entry("File2.txt"), entry("zeta", dir = true), entry("file1.txt"), entry("Alpha", dir = true)),
                SortOrder(),
                Locale.US,
            )
        assertEquals(listOf("Alpha", "zeta", "file1.txt", "File2.txt", "file10.txt"), sorted.map { it.name })
    }

    @Test fun `size and date orders, descending keeps folders first`() {
        val entries = listOf(entry("small", size = 1, modified = 3), entry("big", size = 100, modified = 1), entry("d", dir = true))
        assertEquals(listOf("d", "big", "small"), Sorting.sort(entries, SortOrder(SortField.Size, ascending = false)).map { it.name })
        assertEquals(listOf("d", "big", "small"), Sorting.sort(entries, SortOrder(SortField.Modified)).map { it.name })
    }

    @Test fun `file types by extension`() {
        assertEquals(FileType.Image, FileType.of("IMG_001.JPG"))
        assertEquals(FileType.Pdf, FileType.of("a.pdf"))
        assertEquals(FileType.Apk, FileType.of("app.apk"))
        assertEquals(FileType.Other, FileType.of("Makefile"))
    }

    @Test fun `search is recursive, accent-insensitive and skips restricted folders`() {
        fun f(p: String) =
            File(tmp.root, p).apply {
                parentFile!!.mkdirs()
                writeText("")
            }
        f("Résumé.pdf")
        f("docs/old/resume-2019.docx")
        f("Android/data/com.app/resume.txt")
        f(".hidden/resume.txt")
        val found = mutableListOf<String>()
        Search.search(tmp.root, "RESUME", showHidden = false) { found += it.name }
        assertEquals(listOf("Résumé.pdf", "resume-2019.docx"), found)
        assertTrue(Search.isRestricted(File(tmp.root, "Android/obb")))
    }
}
