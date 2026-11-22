package io.github.michaelmassoni.tessera.tasks.data

import android.content.Context
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.core.settings.SecretBox

/** A CalDAV account: where the server is, who signs in, and where its task lists live. */
data class CalDavAccount(
    /** What the person typed, tidied (https://cloud.example.com). */
    val address: String,
    val username: String,
    /** The calendar home (calendar-home-set), where lists are found and made. */
    val home: String,
)

/**
 * The sync account, in `caldav.xml`, which is left out of every backup; the password is encrypted
 * with a Keystore key (as Music's server password).
 */
class AccountStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val box = SecretBox("tasks_caldav_password")

    val account: CalDavAccount?
        get() {
            val address = prefs.getString("address", null) ?: return null
            val user = prefs.getString("user", null) ?: return null
            val home = prefs.getString("home", null) ?: return null
            return CalDavAccount(address, user, home)
        }

    fun password(): String? = prefs.getString("password", null)?.let(box::open)

    fun save(
        account: CalDavAccount,
        password: String,
    ) = prefs.edit {
        putString("address", account.address)
        putString("user", account.username)
        putString("home", account.home)
        putString("password", box.seal(password))
    }

    fun signOut() = prefs.edit { clear() }

    /** When the last sync finished, and whether it failed (and why). */
    var lastSync: Long
        get() = prefs.getLong("last_sync", 0)
        set(value) = prefs.edit { putLong("last_sync", value) }

    var lastProblem: String?
        get() = prefs.getString("last_problem", null)
        set(value) = prefs.edit { putString("last_problem", value) }

    companion object {
        const val FILE = "caldav"
    }
}
