package io.github.michaelmassoni.tessera.music.server

import android.content.Context
import androidx.media3.common.Player
import io.github.michaelmassoni.tessera.music.engine.Subsonic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Tells the server what's playing, and that a song was played once half of it (or four minutes)
 * has, as Last.fm counts plays. Only for server songs; failures are ignored.
 */
class Scrobbler(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var current: String? = null
    private var submitted = false

    fun started(player: Player) {
        val id = songId(player)
        current = id
        submitted = false
        if (id != null && player.playWhenReady) send(id, submission = false)
    }

    fun progress(player: Player) {
        val id = songId(player) ?: return
        if (id != current) started(player)
        if (submitted) return
        val duration = player.duration.takeIf { it > 0 } ?: return
        if (player.currentPosition >= minOf(duration / 2, FOUR_MINUTES_MS)) {
            submitted = true
            send(id, submission = true)
        }
    }

    private fun songId(player: Player): String? {
        val item = player.currentMediaItem ?: return null
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: return null
        return Subsonic.songId(uri.toString())
    }

    private fun send(
        id: String,
        submission: Boolean,
    ) {
        scope.launch(Dispatchers.IO) {
            val store = ServerStore(context)
            val account = store.account ?: return@launch
            val password = store.password() ?: return@launch
            SubsonicClient(account, password).scrobble(id, submission)
        }
    }

    private companion object {
        const val FOUR_MINUTES_MS = 240_000L
    }
}
