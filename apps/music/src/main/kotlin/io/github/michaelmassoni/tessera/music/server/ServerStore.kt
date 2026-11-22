package io.github.michaelmassoni.tessera.music.server

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.music.engine.ServerAccount
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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

/** Offline cache sizes offered in Settings. */
enum class CacheSize(
    val bytes: Long,
) {
    // Decimal, as Android shows file sizes: "500 MB", "1 GB".
    Small(500_000_000L),
    Medium(1_000_000_000L),
    Large(2_000_000_000L),
    Huge(5_000_000_000L),
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

    var cacheSize: CacheSize
        get() = CacheSize.entries.firstOrNull { it.name == prefs.getString(KEY_CACHE, null) } ?: CacheSize.Medium
        set(value) = prefs.edit { putString(KEY_CACHE, value.name) }

    /** The decrypted password, or null when there is none or it can't be read (a restored Keystore). */
    fun password(): String? {
        val stored = prefs.getString(KEY_PASSWORD, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE))
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Saves a checked account and switches to it. */
    fun signIn(
        account: ServerAccount,
        password: String,
    ) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(password.toByteArray())
        prefs.edit {
            putString(KEY_ADDRESS, account.address)
            putString(KEY_USER, account.username)
            putString(KEY_PASSWORD, Base64.encodeToString(sealed, Base64.NO_WRAP))
            putString(KEY_SOURCE, MusicSource.Server.name)
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build(),
                )
            }.generateKey()
    }

    companion object {
        const val FILE = "music_server"
        private const val KEY_SOURCE = "source"
        private const val KEY_ADDRESS = "address"
        private const val KEY_USER = "user"
        private const val KEY_PASSWORD = "password"
        private const val KEY_QUALITY = "quality"
        private const val KEY_CACHE = "cache_size"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "music_server_password"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
