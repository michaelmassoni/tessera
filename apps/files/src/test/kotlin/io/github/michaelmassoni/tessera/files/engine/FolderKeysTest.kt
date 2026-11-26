package io.github.michaelmassoni.tessera.files.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderKeysTest {
    private val kept = listOf("/storage/emulated/0", "/storage/emulated/0/Music", "/storage/emulated/0/Music/Live")

    @Test
    fun `going up forgets only the folders left`() {
        assertEquals(listOf("/storage/emulated/0/Music/Live"), FolderKeys.stale(kept, "/storage/emulated/0/Music"))
        assertEquals(kept.drop(1), FolderKeys.stale(kept, "/storage/emulated/0"))
    }

    @Test
    fun `going deeper forgets nothing`() {
        assertEquals(emptyList<String>(), FolderKeys.stale(kept, "/storage/emulated/0/Music/Live/2019"))
    }

    @Test
    fun `a sibling with the same prefix is not inside`() {
        assertEquals(kept.drop(1), FolderKeys.stale(kept, "/storage/emulated/0/Musicals"))
    }

    @Test
    fun `home forgets every folder`() {
        assertEquals(kept, FolderKeys.stale(kept, null))
    }

    @Test
    fun `everything is inside the root`() {
        assertEquals(emptyList<String>(), FolderKeys.stale(listOf("/"), "/sdcard"))
    }
}
