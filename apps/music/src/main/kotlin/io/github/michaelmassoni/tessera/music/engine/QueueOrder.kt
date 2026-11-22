package io.github.michaelmassoni.tessera.music.engine

import kotlin.random.Random

/**
 * Shuffling the queue for real: the song playing stays first and the rest follow in a random
 * order, so the queue on screen is the order songs play in. Turning shuffle off puts the queue
 * back in the order it had, with songs added meanwhile at the end. Pure, so it's unit tested.
 */
object QueueOrder {
    /** The new order, as indices into [ids], with [current] first. */
    fun shuffled(
        ids: List<String>,
        current: Int,
        random: Random = Random.Default,
    ): List<Int> {
        if (ids.isEmpty()) return emptyList()
        val c = current.coerceIn(0, ids.lastIndex)
        return listOf(c) + ids.indices.filter { it != c }.shuffled(random)
    }

    /**
     * The order that puts [ids] back as [original] had them, as indices into [ids]. Songs
     * repeated in the queue are matched in turn; songs not in [original] keep their relative order
     * at the end.
     */
    fun restored(
        ids: List<String>,
        original: List<String>,
    ): List<Int> {
        val unused = ids.indices.groupBy { ids[it] }.mapValues { ArrayDeque(it.value) }
        val order = original.mapNotNull { unused[it]?.removeFirstOrNull() }
        val rest = ids.indices.filter { it !in order.toSet() }
        return order + rest
    }
}
