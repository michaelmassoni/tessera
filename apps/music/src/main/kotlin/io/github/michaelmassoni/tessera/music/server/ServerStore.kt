package io.github.michaelmassoni.tessera.music.server

import android.content.Context
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.core.settings.SecretBox
import io.github.michaelmassoni.tessera.music.engine.ServerAccount

/** Where the library comes from. */
enum class MusicSource { Local, Server }

/** How server songs are streamed: the original file, or transcoded to at most this bitrate. */
enum class StreamQuality(
    val kbps: Int,
) {
    Original(0),
    High(320),
    Medium(192),
    Low(128),
}

/**
 * The music source, the server account and the playback settings for it. Kept in
 * `music_server.xml`, which is left out of every backup; the password is encrypted with a key that
 * never leaves this phone's Keystore.
 */
class ServerStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var source: MusicSource
        get() = if (prefs.getString(KEY_SOURCE, null) == MusicSource.Server.name) MusicSource.Server else MusicSource.Local
        set(value) = prefs.edit { putString(KEY_SOURCE, value.name) }

    val account: ServerAccount?
        get() {
            val address = prefs.getString(KEY_ADDRESS, null) ?: return null
            val user = prefs.getString(KEY_USER, null) ?: return null
            return ServerAccount(address, user)
        }

    var quality: StreamQuality
        get() = StreamQuality.entries.firstOrNull { it.name == prefs.getString(KEY_QUALITY, null) } ?: StreamQuality.Original
        set(value) = prefs.edit { putString(KEY_QUALITY, value.name) }

    private val box = SecretBox(ALIAS)

    /** The decrypted password, or null when there is none or it can't be read (a restored Keystore). */
    fun password(): String? = prefs.getString(KEY_PASSWORD, null)?.let(box::open)

    /** Saves a checked account and switches to it. */
    fun signIn(
        account: ServerAccount,
        password: String,
    ) {
        prefs.edit {
            putString(KEY_ADDRESS, account.address)
            putString(KEY_USER, account.username)
            putString(KEY_PASSWORD, box.seal(password))
            putString(KEY_SOURCE, MusicSource.Server.name)
        }
    }

    companion object {
        const val FILE = "music_server"
        private const val KEY_SOURCE = "source"
        private const val KEY_ADDRESS = "address"
        private const val KEY_USER = "user"
        private const val KEY_PASSWORD = "password"
        private const val KEY_QUALITY = "quality"
        private const val ALIAS = "music_server_password"
    }
}
