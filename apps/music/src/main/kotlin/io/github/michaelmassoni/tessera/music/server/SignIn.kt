package io.github.michaelmassoni.tessera.music.server

import android.content.Context
import io.github.michaelmassoni.tessera.music.engine.ServerAccount
import io.github.michaelmassoni.tessera.music.engine.ServerException
import io.github.michaelmassoni.tessera.music.engine.ServerProblem
import io.github.michaelmassoni.tessera.music.engine.Subsonic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks an address and login against the server and, if they work, saves them and switches to
 * the server. Returns what went wrong, or null.
 */
suspend fun signIn(
    context: Context,
    address: String,
    username: String,
    password: String,
): ServerProblem? =
    withContext(Dispatchers.IO) {
        try {
            val account = ServerAccount(Subsonic.normalizeAddress(address), username.trim())
            SubsonicClient(account, password).ping()
            val store = ServerStore(context)
            // Another server or account: the saved library and art were someone else's.
            if (store.account != account) {
                ServerLibrary.clear(context)
                context.cacheDir.resolve("art").deleteRecursively()
            }
            store.signIn(account, password)
            null
        } catch (e: ServerException) {
            e.problem
        }
    }
