package io.github.michaelmassoni.tessera.music.engine

import kotlinx.serialization.Serializable

/** Where the queue came from, for "Playing from" on the player. */
@Serializable
data class PlayingFrom(
    val kind: Kind,
    val name: String = "",
) {
    enum class Kind { Songs, Album, Artist, Playlist, Search }
}
