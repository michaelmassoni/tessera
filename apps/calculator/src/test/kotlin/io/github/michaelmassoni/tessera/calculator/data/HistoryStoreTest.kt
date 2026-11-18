package io.github.michaelmassoni.tessera.calculator.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store() = HistoryStore(folder.root.resolve("history.tsv"))

    @Test fun `missing file loads as empty`() {
        assertEquals(emptyList<HistoryEntry>(), store().load())
    }

    @Test fun `round trip keeps order and content`() {
        val entries =
            listOf(
                HistoryEntry("0.1+0.2", "0.3", 3),
                HistoryEntry("sin⁻¹(1)", "90", 2),
                HistoryEntry("1÷3", "0.33333333333333333333333333333333", 1),
            )
        store().save(entries)
        assertEquals(entries, store().load())
    }

    @Test fun `corrupt lines are skipped`() {
        folder.root.resolve("history.tsv").writeText("garbage\n5\t2+2\t4\nnot-a-time\t1\t1\n7\t\t3\n")
        assertEquals(listOf(HistoryEntry("2+2", "4", 5)), store().load())
    }

    @Test fun `prepend keeps the newest 100`() {
        var entries = emptyList<HistoryEntry>()
        repeat(150) { entries = HistoryStore.prepend(entries, HistoryEntry("$it+0", "$it", it.toLong())) }
        assertEquals(HistoryStore.MAX_ENTRIES, entries.size)
        assertEquals("149", entries.first().result)
        assertEquals("50", entries.last().result)
    }

    @Test fun `save overwrites atomically and leaves no temp file`() {
        store().save(listOf(HistoryEntry("1+1", "2", 1)))
        store().save(listOf(HistoryEntry("2+2", "4", 2)))
        assertEquals(listOf(HistoryEntry("2+2", "4", 2)), store().load())
        assertEquals(listOf("history.tsv"), folder.root.list()!!.toList())
    }
}
