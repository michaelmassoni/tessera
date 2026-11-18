package io.github.michaelmassoni.tessera.notes.engine

/**
 * Undo/redo of editor states. Typing is coalesced: a change within [coalesceMillis] of the
 * previous one, of the same kind, replaces the pending step instead of adding a new one, so undo
 * removes a burst of typing rather than one character.
 */
class UndoStack<T>(
    initial: T,
    private val coalesceMillis: Long = 1000,
    private val limit: Int = 100,
) {
    private val undo = ArrayDeque<T>()
    private val redo = ArrayDeque<T>()
    private var lastChangeAt = Long.MIN_VALUE
    private var lastKind: Any? = null

    var current: T = initial
        private set

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    /** Records [state] as the new current state. [kind] identifies edits that may coalesce (e.g. the field typed in). */
    fun push(
        state: T,
        now: Long,
        kind: Any? = null,
    ) {
        if (state == current) return
        val coalesce = kind != null && kind == lastKind && now - lastChangeAt <= coalesceMillis && undo.isNotEmpty()
        if (!coalesce) {
            undo.addLast(current)
            if (undo.size > limit) undo.removeFirst()
        }
        current = state
        redo.clear()
        lastChangeAt = now
        lastKind = kind
    }

    fun undo(): T? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        current = previous
        lastKind = null
        return previous
    }

    fun redo(): T? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        current = next
        lastKind = null
        return next
    }
}
