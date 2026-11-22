package io.github.michaelmassoni.tessera.music.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class QueueOrderTest {
    private fun <T> apply(
        list: List<T>,
        moves: List<Pair<Int, Int>>,
    ): List<T> {
        val m = list.toMutableList()
        moves.forEach { (from, to) -> m.add(to, m.removeAt(from)) }
        return m
    }

    @Test
    fun `shuffle keeps the playing song first and every song once`() {
        val ids = (0 until 20).map { "s$it" }
        val order = QueueOrder.shuffled(ids, 7, Random(1))
        assertEquals(7, order.first())
        assertEquals(ids.indices.toSet(), order.toSet())
        assertEquals(20, order.size)
    }

    @Test
    fun `moves produce the requested order`() {
        val list = listOf("a", "b", "c", "d", "e")
        val order = listOf(3, 0, 4, 1, 2)
        assertEquals(order.map { list[it] }, apply(list, QueueOrder.moves(order)))
        assertEquals(emptyList<Pair<Int, Int>>(), QueueOrder.moves(listOf(0, 1, 2)))
    }

    @Test
    fun `unshuffle restores the original order, repeats in turn, new songs last`() {
        val original = listOf("a", "b", "a", "c")
        val now = listOf("c", "a", "x", "b", "a")
        val order = QueueOrder.restored(now, original)
        assertEquals(listOf("a", "b", "a", "c", "x"), order.map { now[it] })
    }

    @Test
    fun `songs removed while shuffled are just gone`() {
        assertEquals(listOf("a", "c"), QueueOrder.restored(listOf("c", "a"), listOf("a", "b", "c")).map { listOf("c", "a")[it] })
    }
}
