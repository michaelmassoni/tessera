package io.github.michaelmassoni.tessera.notes.engine

/** Converting notes between text and checklist, and editing checklist items. */
object Checklist {
    private val MARKER = Regex("^\\s*(?:[-*•]|\\[[ xX]?]|[☐☑✓✔])\\s*")
    private val CHECKED_MARKER = Regex("^\\s*(?:\\[[xX]]|[☑✓✔])")

    /** Each non-blank line becomes an item. "[x]", "☑" or "✓" at the start marks it checked. */
    fun fromText(text: String): List<ChecklistItem> =
        text
            .lines()
            .filter { it.isNotBlank() }
            .map { line -> ChecklistItem(line.replace(MARKER, "").trimEnd(), checked = CHECKED_MARKER.containsMatchIn(line)) }
            .ifEmpty { listOf(ChecklistItem("")) }

    /** One line per item, unchecked first; checked state is dropped (it has no plain-text form). */
    fun toText(items: List<ChecklistItem>): String = items.map { it.text }.filter { it.isNotBlank() }.joinToString("\n")

    /** Enter at [cursor] in item [index]: the text after the cursor moves to a new item below. */
    fun split(
        items: List<ChecklistItem>,
        index: Int,
        cursor: Int,
    ): List<ChecklistItem> {
        val item = items[index]
        val at = cursor.coerceIn(0, item.text.length)
        return items.toMutableList().apply {
            set(index, item.copy(text = item.text.substring(0, at)))
            add(index + 1, ChecklistItem(item.text.substring(at)))
        }
    }

    /**
     * Backspace at the start of item [index]: its text joins the item above. Returns the new
     * list and the cursor position in the merged item, or null at the first item.
     */
    fun mergeWithPrevious(
        items: List<ChecklistItem>,
        index: Int,
    ): Pair<List<ChecklistItem>, Int>? {
        if (index <= 0 || index >= items.size) return null
        val previous = items[index - 1]
        val merged = previous.copy(text = previous.text + items[index].text)
        return items.toMutableList().apply {
            set(index - 1, merged)
            removeAt(index)
        } to previous.text.length
    }

    fun toggle(
        items: List<ChecklistItem>,
        index: Int,
    ): List<ChecklistItem> = items.mapIndexed { i, it -> if (i == index) it.copy(checked = !it.checked) else it }

    fun move(
        items: List<ChecklistItem>,
        from: Int,
        to: Int,
    ): List<ChecklistItem> {
        if (from == to || from !in items.indices || to !in items.indices) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Indices in display order: unchecked items first when [checkedLast], each group in its own order. */
    fun displayOrder(
        items: List<ChecklistItem>,
        checkedLast: Boolean,
    ): List<Int> =
        if (!checkedLast) {
            items.indices.toList()
        } else {
            items.indices.filter { !items[it].checked } + items.indices.filter { items[it].checked }
        }
}
