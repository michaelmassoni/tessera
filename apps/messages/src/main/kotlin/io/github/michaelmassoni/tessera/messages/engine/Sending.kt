package io.github.michaelmassoni.tessera.messages.engine

import kotlin.math.abs

/**
 * Messages shown the moment you tap Send, before the system has stored them. When a reload
 * brings the stored copy, it takes over the shown bubble's key, so the list sees the same item.
 */
object Sending {
    private const val PREFIX = "sending-"

    /** How long a shown message waits for its stored copy before it's dropped. */
    const val TIMEOUT_MILLIS = 2 * 60_000L

    fun message(
        text: String,
        attachments: List<Attachment>,
        now: Long,
    ) = Message(id = "$PREFIX$now", body = text, date = now, outgoing = true, pending = true, attachments = attachments)

    fun isLocal(message: Message) = message.id.startsWith(PREFIX)

    /**
     * [loaded] from the database, plus the shown messages from [current] that it doesn't have yet.
     * A stored message matches a shown one by being outgoing with the same text, close in time.
     */
    fun merge(
        current: List<Message>,
        loaded: List<Message>,
        now: Long,
    ): List<Message> {
        val local = current.filter(::isLocal)
        if (local.isEmpty()) return loaded
        val out = loaded.toMutableList()
        val taken = mutableSetOf<Int>()
        local.forEach { shown ->
            val i =
                out.indices.firstOrNull { i ->
                    i !in taken && out[i].outgoing && out[i].body == shown.body &&
                        abs(out[i].date - shown.date) < TIMEOUT_MILLIS && !isLocal(out[i])
                }
            if (i != null) {
                taken += i
                out[i] = out[i].copy(key = shown.key)
            } else if (now - shown.date < TIMEOUT_MILLIS) {
                out += shown
            }
        }
        return out.sortedBy { it.date }
    }
}
