package io.github.michaelmassoni.tessera.files.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NamingTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun split() {
        assertEquals("photo" to ".jpg", Naming.split("photo.jpg"))
        assertEquals(".bashrc" to "", Naming.split(".bashrc"))
        assertEquals("backup" to ".tar.gz", Naming.split("backup.tar.gz"))
        assertEquals("README" to "", Naming.split("README"))
        assertEquals("a.b" to ".c", Naming.split("a.b.c"))
    }

    @Test fun `unique names`() {
        val d = tmp.root
        assertEquals("a.txt", Naming.uniqueName(d, "a.txt"))
        File(d, "a.txt").writeText("")
        assertEquals("a (1).txt", Naming.uniqueName(d, "a.txt"))
        File(d, "a (1).txt").writeText("")
        assertEquals("a (2).txt", Naming.uniqueName(d, "a.txt"))
        assertEquals("a (2).txt", Naming.uniqueName(d, "a (1).txt"))
        File(d, "x.tar.gz").writeText("")
        assertEquals("x (1).tar.gz", Naming.uniqueName(d, "x.tar.gz"))
        File(d, "dir.v2").mkdir()
        assertEquals("dir.v2 (1)", Naming.uniqueName(d, "dir.v2")) // folders keep their dots
    }

    @Test fun validation() {
        assertEquals(NameProblem.Empty, Naming.validate("  "))
        assertEquals(NameProblem.Reserved, Naming.validate(".."))
        assertEquals(NameProblem.InvalidCharacter, Naming.validate("a/b"))
        assertEquals(NameProblem.TooLong, Naming.validate("é".repeat(200)))
        File(tmp.root, "taken").writeText("")
        assertEquals(NameProblem.Exists, Naming.validate("taken", tmp.root))
        assertNull(Naming.validate("Holiday photos (2026)"))
    }

    @Test fun inside() {
        val a = File(tmp.root, "a").apply { mkdirs() }
        assertTrue(Naming.isInside(File(a, "b/c"), a))
        assertTrue(Naming.isInside(a, a))
        assertFalse(Naming.isInside(File(tmp.root, "ab"), a)) // prefix but not inside
    }
}
