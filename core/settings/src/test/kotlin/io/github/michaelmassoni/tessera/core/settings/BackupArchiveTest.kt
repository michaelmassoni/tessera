package io.github.michaelmassoni.tessera.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {
    @get:Rule val tmp = TemporaryFolder()

    private val manifest = BackupManifest("io.example.notes", "Notes", 5, 1_000)

    private fun roots(name: String): Map<String, File> =
        mapOf("files" to tmp.newFolder("$name-files"), "prefs" to tmp.newFolder("$name-prefs"))

    @Test fun `backs up and restores, replacing data and keeping excluded files`() {
        val from = roots("a")
        File(from.getValue("files"), "notes").mkdirs()
        File(from.getValue("files"), "notes/1.json").writeText("one")
        File(from.getValue("files"), "forecasts").mkdirs()
        File(from.getValue("files"), "forecasts/cache.json").writeText("cache")
        File(from.getValue("prefs"), "settings.xml").writeText("<map/>")
        val bytes =
            ByteArrayOutputStream()
                .also {
                    BackupArchive.write(
                        it,
                        manifest,
                        from,
                        exclude = listOf("files/forecasts/"),
                    )
                }.toByteArray()
        assertEquals(manifest, BackupArchive.manifest(ByteArrayInputStream(bytes)))

        val to = roots("b")
        File(to.getValue("files"), "notes").mkdirs()
        File(to.getValue("files"), "notes/old.json").writeText("old")
        File(to.getValue("files"), "forecasts").mkdirs()
        File(to.getValue("files"), "forecasts/keep.json").writeText("keep")
        BackupArchive.restore(ByteArrayInputStream(bytes), to, tmp.newFolder("staging"), exclude = listOf("files/forecasts/"))
        assertEquals("one", File(to.getValue("files"), "notes/1.json").readText())
        assertFalse(File(to.getValue("files"), "notes/old.json").exists())
        assertTrue(File(to.getValue("files"), "forecasts/keep.json").exists())
        assertEquals("<map/>", File(to.getValue("prefs"), "settings.xml").readText())
    }

    @Test fun `a refused or hostile backup changes nothing`() {
        val from = roots("c")
        File(from.getValue("files"), "x.json").writeText("x")
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, manifest, from) }.toByteArray()
        val to = roots("d")
        File(to.getValue("files"), "mine.json").writeText("mine")
        runCatching {
            BackupArchive.restore(ByteArrayInputStream(bytes), to, tmp.newFolder("s1")) { throw IOException("other app") }
        }
        assertTrue(File(to.getValue("files"), "mine.json").exists())

        val evil =
            ByteArrayOutputStream().also { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest"))
                    zip.write("app=io.example.notes\nformat=1\n".toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("files/../../escape.txt"))
                    zip.write("no".toByteArray())
                    zip.closeEntry()
                }
            }
        assertTrue(runCatching { BackupArchive.restore(ByteArrayInputStream(evil.toByteArray()), to, tmp.newFolder("s2")) }.isFailure)
        assertTrue(File(to.getValue("files"), "mine.json").exists())
    }

    @Test fun `suite backups carry each app's backup`() {
        val a = roots("e")
        File(a.getValue("files"), "a.json").writeText("a")
        val suite =
            ByteArrayOutputStream()
                .also { out ->
                    val written =
                        BackupArchive.writeSuite(
                            out,
                            BackupManifest(BackupArchive.SUITE, "Tessera", 0, 1),
                            listOf(
                                "io.example.notes" to { o -> BackupArchive.write(o, manifest, a) },
                                "io.example.broken" to { _ -> throw IOException("not signed like us") },
                            ),
                        )
                    assertEquals(listOf("io.example.notes"), written)
                }.toByteArray()
        val seen = mutableListOf<String>()
        BackupArchive.readSuite(ByteArrayInputStream(suite)) { pkg, input ->
            seen += pkg
            if (pkg == "io.example.notes") assertEquals("Notes", BackupArchive.manifest(input)?.label)
        }
        assertEquals(listOf("io.example.notes"), seen)
    }
}
