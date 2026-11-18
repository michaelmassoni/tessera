package io.github.michaelmassoni.tessera.notes.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistTest {
    private fun items(vararg t: String) = t.map { ChecklistItem(it) }

    @Test fun `text lines become items and markers are understood`() {
        val list = Checklist.fromText("Milk\n\n- Eggs\n[x] Bread\n☑ Jam\n[ ] Tea\n• Rice")
        assertEquals(listOf("Milk", "Eggs", "Bread", "Jam", "Tea", "Rice"), list.map { it.text })
        assertEquals(listOf(false, false, true, true, false, false), list.map { it.checked })
    }

    @Test fun `empty text gives one empty item, and items convert back to lines`() {
        assertEquals(items(""), Checklist.fromText("  \n"))
        assertEquals("a\nb", Checklist.toText(items("a", "", "b")))
    }

    @Test fun `enter splits an item at the cursor`() {
        assertEquals(items("Mi", "lk", "Eggs"), Checklist.split(items("Milk", "Eggs"), 0, 2))
        assertEquals(items("Milk", "", "Eggs"), Checklist.split(items("Milk", "Eggs"), 0, 99))
    }

    @Test fun `backspace merges with the item above and reports the cursor`() {
        val (list, cursor) = Checklist.mergeWithPrevious(items("Milk", "shake"), 1)!!
        assertEquals(items("Milkshake"), list)
        assertEquals(4, cursor)
        assertNull(Checklist.mergeWithPrevious(items("Milk"), 0))
    }

    @Test fun `toggle, move and display order`() {
        val list = Checklist.toggle(items("a", "b", "c"), 0)
        assertEquals(listOf(true, false, false), list.map { it.checked })
        assertEquals(listOf(1, 2, 0), Checklist.displayOrder(list, checkedLast = true))
        assertEquals(listOf(0, 1, 2), Checklist.displayOrder(list, checkedLast = false))
        assertEquals(items("b", "c", "a"), Checklist.move(items("a", "b", "c"), 0, 2))
    }
}
