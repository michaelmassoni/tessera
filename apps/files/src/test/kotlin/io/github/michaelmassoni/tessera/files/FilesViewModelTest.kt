package io.github.michaelmassoni.tessera.files

import io.github.michaelmassoni.tessera.files.data.FilesSettings
import io.github.michaelmassoni.tessera.files.data.ViewOptions
import io.github.michaelmassoni.tessera.files.data.Volume
import io.github.michaelmassoni.tessera.files.engine.FileOperations
import io.github.michaelmassoni.tessera.files.engine.NameProblem
import io.github.michaelmassoni.tessera.files.engine.Trash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var root: File
    private lateinit var vm: FilesViewModel

    private class MemorySettings : FilesSettings {
        override var options = ViewOptions()
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        root = tmp.newFolder("storage")
        File(root, "Download").mkdirs()
        File(root, "Download/b.txt").writeText("b")
        File(root, "Download/a.txt").writeText("a")
        File(root, "Download/.hidden").writeText("h")
        File(root, "Download/folder").mkdirs()
        File(root, "Music").mkdirs()
        val volume = Volume(root, "Internal storage", isPrimary = true, isRemovable = false, totalBytes = 1, freeBytes = 1)
        vm =
            FilesViewModel(
                MemorySettings(),
                { listOf(volume) },
                dispatcher,
                FileOperations(freeSpace = { it.usableSpace }),
                Trash(File(tmp.root, "trash.json")),
            )
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private val names get() =
        vm.state.value.entries
            .map { it.name }

    @Test fun `lists folders first, sorted, without hidden files`() {
        vm.open(File(root, "Download"))
        assertEquals(listOf("folder", "a.txt", "b.txt"), names)
        vm.setOptions(
            vm.state.value.options
                .copy(showHidden = true),
        )
        assertEquals(listOf("folder", ".hidden", "a.txt", "b.txt"), names)
    }

    @Test fun `back goes up to the volume root, then home, then reports false`() {
        vm.open(File(root, "Download/folder"))
        assertTrue(vm.back())
        assertEquals(File(root, "Download"), vm.state.value.location)
        assertTrue(vm.back())
        assertEquals(root, vm.state.value.location)
        assertTrue(vm.back())
        assertNull(vm.state.value.location)
        assertFalse(vm.back())
    }

    @Test fun `back clears selection before navigating`() {
        vm.open(File(root, "Download"))
        vm.toggleSelection(File(root, "Download/a.txt"))
        assertTrue(vm.back())
        assertTrue(
            vm.state.value.selection
                .isEmpty(),
        )
        assertEquals(File(root, "Download"), vm.state.value.location)
    }

    @Test fun `copy then paste in another folder`() {
        vm.open(File(root, "Download"))
        vm.toggleSelection(File(root, "Download/a.txt"))
        vm.copySelection()
        assertEquals(
            ClipMode.Copy,
            vm.state.value.clipboard
                ?.mode,
        )
        vm.open(File(root, "Music"))
        vm.paste()
        assertTrue(File(root, "Music/a.txt").exists())
        assertTrue(File(root, "Download/a.txt").exists())
        assertNull(vm.state.value.clipboard)
        assertEquals(listOf("a.txt"), names)
    }

    @Test fun `move then paste removes the source`() {
        vm.open(File(root, "Download"))
        vm.toggleSelection(File(root, "Download/folder"))
        vm.moveSelection()
        vm.open(File(root, "Music"))
        vm.paste()
        assertTrue(File(root, "Music/folder").isDirectory)
        assertFalse(File(root, "Download/folder").exists())
    }

    @Test fun `rename validates and applies`() {
        vm.open(File(root, "Download"))
        val a = File(root, "Download/a.txt")
        assertEquals(NameProblem.Exists, vm.rename(a, "b.txt"))
        assertEquals(NameProblem.InvalidCharacter, vm.rename(a, "x/y"))
        assertNull(vm.rename(a, "renamed.txt"))
        assertTrue(File(root, "Download/renamed.txt").exists())
        assertNull(vm.rename(File(root, "Download/b.txt"), "B.txt")) // case-only rename is allowed
    }

    @Test fun `new folder and delete`() {
        vm.open(File(root, "Download"))
        assertNull(vm.createFolder("New"))
        assertTrue(File(root, "Download/New").isDirectory)
        vm.delete(listOf(File(root, "Download/New"), File(root, "Download/a.txt")))
        assertFalse(File(root, "Download/New").exists())
        assertFalse(File(root, "Download/a.txt").exists())
        assertEquals(listOf("folder", "b.txt"), names)
    }

    @Test fun `deleted files go to the trash on their volume and come back`() {
        vm.open(File(root, "Download"))
        vm.delete(listOf(File(root, "Download/a.txt")))
        assertTrue(File(root, ".Trash-tessera").isDirectory)
        vm.openTrash()
        val item =
            vm.state.value.trash!!
                .items
                .single()
        assertEquals("a.txt", item.name)
        vm.restore(listOf(item.id))
        assertTrue(File(root, "Download/a.txt").exists())
        assertTrue(
            vm.state.value.trash!!
                .items
                .isEmpty(),
        )
        vm.delete(listOf(File(root, "Download/b.txt")))
        vm.openTrash()
        vm.deleteForever(null)
        assertTrue(
            vm.state.value.trash!!
                .items
                .isEmpty(),
        )
        assertFalse(File(root, "Download/b.txt").exists())
        assertTrue(vm.back())
        assertNull(vm.state.value.trash)
    }

    @Test fun `search finds nested matches from the current folder`() {
        File(root, "Download/folder/report-a.txt").writeText("")
        vm.open(root)
        vm.openSearch()
        vm.search("REPORT")
        dispatcher.scheduler.advanceUntilIdle() // past the typing pause
        val search = vm.state.value.search!!
        assertFalse(search.running)
        assertEquals(listOf("report-a.txt"), search.results.map { it.name })
        vm.closeSearch()
        assertNull(vm.state.value.search)
    }
}
