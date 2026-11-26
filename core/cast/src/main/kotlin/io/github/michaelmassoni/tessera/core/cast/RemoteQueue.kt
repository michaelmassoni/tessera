package io.github.michaelmassoni.tessera.core.cast

import androidx.media3.common.Player

/** What the cast device plays once a song ends: the next, the same (repeat one), the first (repeat all) or nothing. */
object RemoteQueue {
    fun next(
        index: Int,
        count: Int,
        repeatMode: Int,
    ): Int? =
        when {
            count <= 0 -> null
            repeatMode == Player.REPEAT_MODE_ONE -> index.coerceIn(0, count - 1)
            index + 1 < count -> index + 1
            repeatMode == Player.REPEAT_MODE_ALL -> 0
            else -> null
        }
}
