package io.github.michaelmassoni.tessera.podcasts.engine

/**
 * Up Next as a list of episode ids: the first is the episode playing (or paused), the rest the
 * queue. The player's playlist is kept in this order.
 */
object UpNext {
    /** [id] plays now: moved (or added) to the front. */
    fun playNow(
        queue: List<String>,
        id: String,
    ) = listOf(id) + queue.filterNot { it == id }

    /** [id] goes right after the episode playing. */
    fun playNext(
        queue: List<String>,
        id: String,
    ): List<String> {
        val rest = queue.filterNot { it == id }
        if (rest.isEmpty()) return listOf(id)
        return listOf(rest.first(), id) + rest.drop(1)
    }

    /** [id] goes at the end, unless it's queued already. */
    fun add(
        queue: List<String>,
        id: String,
    ) = if (id in queue) queue else queue + id

    fun remove(
        queue: List<String>,
        id: String,
    ) = queue.filterNot { it == id }

    /** Moves the item at [from] to [to] (indices into the whole list). */
    fun move(
        queue: List<String>,
        from: Int,
        to: Int,
    ): List<String> {
        if (from !in queue.indices || to !in queue.indices || from == to) return queue
        val list = queue.toMutableList()
        list.add(to, list.removeAt(from))
        return list
    }
}
