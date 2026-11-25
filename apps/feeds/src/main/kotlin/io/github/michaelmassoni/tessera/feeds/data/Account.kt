package io.github.michaelmassoni.tessera.feeds.data

import android.content.Context
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.core.settings.SecretBox
import io.github.michaelmassoni.tessera.feeds.sync.YarrClient

/** Where the feeds come from. */
sealed interface Source {
    /** The phone fetches each feed. */
    data object Phone : Source

    /** A yarr server; [username] is null when it has no login. */
    data class Yarr(
        val address: String,
        val username: String?,
    ) : Source
}

/**
 * The source and the yarr sign-in, in `feeds_account.xml`, which is left out of every backup; the
 * password and the auth cookie are encrypted with a Keystore key (as Music's server password).
 */
class AccountStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val box = SecretBox("feeds_yarr_password")

    val source: Source
        get() {
            val address = prefs.getString("address", null) ?: return Source.Phone
            return Source.Yarr(address, prefs.getString("user", null))
        }

    val yarr: Source.Yarr? get() = source as? Source.Yarr

    fun password(): String? = prefs.getString("password", null)?.let(box::open)

    fun saveYarr(
        address: String,
        username: String?,
        password: String?,
        cookie: String?,
    ) = prefs.edit {
        clear()
        putString("address", address)
        username?.let { putString("user", it) }
        password?.let { putString("password", box.seal(it)) }
        cookie?.let { putString("cookie", box.seal(it)) }
    }

    fun usePhone() = prefs.edit { clear() }

    /** A client for the yarr server, or null on this phone. */
    fun client(): YarrClient? {
        val y = yarr ?: return null
        return YarrClient(
            y.address,
            y.username,
            password(),
            cookie = prefs.getString("cookie", null)?.let(box::open),
            onCookie = { c -> prefs.edit { if (c == null) remove("cookie") else putString("cookie", box.seal(c)) } },
        )
    }

    var lastSync: Long
        get() = prefs.getLong("last_sync", 0)
        set(value) = prefs.edit { putLong("last_sync", value) }

    /** Why the last sync failed (a SyncProblem name), or null. */
    var lastProblem: String?
        get() = prefs.getString("last_problem", null)
        set(value) = prefs.edit { putString("last_problem", value) }

    companion object {
        const val FILE = "feeds_account"
    }
}

/** Feeds' own settings, in `settings.xml` (backed up). */
object FeedsPrefs {
    private const val FILE = "settings"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Keep read articles this many days (7, 30 or 90). Starred and unread are always kept. */
    fun keepReadDays(context: Context) = prefs(context).getInt("keep_read_days", 30)

    fun setKeepReadDays(
        context: Context,
        days: Int,
    ) = prefs(context).edit { putInt("keep_read_days", days) }

    /** Article text size, as a factor of the system's (0.85 to 1.4). */
    fun textScale(context: Context) = prefs(context).getFloat("text_scale", 1f)

    fun setTextScale(
        context: Context,
        scale: Float,
    ) = prefs(context).edit { putFloat("text_scale", scale) }

    val KEEP_CHOICES = listOf(7, 30, 90)
    val TEXT_SCALES = listOf(0.85f, 1f, 1.15f, 1.3f)
}
