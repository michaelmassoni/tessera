package io.github.michaelmassoni.tessera.core.dav

import android.content.Context
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.core.settings.SecretBox
import io.github.michaelmassoni.tessera.core.settings.ServerAddress

/** A server account: where the server is, who signs in, and where its collections live. */
data class DavAccount(
    /** What the person typed, tidied (https://cloud.example.com). */
    val address: String,
    val username: String,
    /** The calendar or address book home, where collections are found and made. */
    val home: String,
)

/**
 * The sync account, in its own preferences file ([file]), which apps leave out of every backup;
 * the password is encrypted with a Keystore key ([keyAlias]), as Music's server password.
 */
open class DavAccountStore(
    context: Context,
    file: String,
    keyAlias: String,
) {
    private val prefs = context.applicationContext.getSharedPreferences(file, Context.MODE_PRIVATE)
    private val box = SecretBox(keyAlias)

    val account: DavAccount?
        get() {
            val address = prefs.getString("address", null) ?: return null
            val user = prefs.getString("user", null) ?: return null
            val home = prefs.getString("home", null) ?: return null
            return DavAccount(address, user, home)
        }

    fun password(): String? = prefs.getString("password", null)?.let(box::open)

    /** A client signed in as the account, or null when signed out. */
    fun client(): DavClient? {
        val a = account ?: return null
        return DavClient(a.username, password() ?: return null)
    }

    fun save(
        account: DavAccount,
        password: String,
    ) = prefs.edit {
        putString("address", account.address)
        putString("user", account.username)
        putString("home", account.home)
        putString("password", box.seal(password))
    }

    fun signOut() = prefs.edit { clear() }

    /** When the last sync finished. */
    var lastSync: Long
        get() = prefs.getLong("last_sync", 0)
        set(value) = prefs.edit { putLong("last_sync", value) }

    /** Why the last sync failed ([SyncProblem] name), or null. */
    var lastProblem: String?
        get() = prefs.getString("last_problem", null)
        set(value) = prefs.edit { putString("last_problem", value) }
}

/** What went wrong signing in. [LocalNetwork]: the server is on the local network and the person refused Nearby devices. */
enum class SignInProblem { BadAddress, Insecure, Unreachable, WrongLogin, NotCalDav, LocalNetwork, Other }

class SignInError(
    val problem: SignInProblem,
) : Exception(problem.name)

/** Checks an address and login and finds the home for [DavKind]. Blocking: call off the main thread. */
object DavSignIn {
    /** The tidied address and the home, or a [SignInError]. */
    fun discover(
        address: String,
        username: String,
        password: String,
        kind: DavKind,
    ): Result<DavAccount> =
        try {
            val base = ServerAddress.normalize(address)
            val home = DavClient(username.trim(), password).findHome(base, kind)
            Result.success(DavAccount(base, username.trim(), home))
        } catch (e: ServerAddress.Invalid) {
            Result.failure(
                SignInError(
                    if (e.problem ==
                        ServerAddress.Problem.Insecure
                    ) {
                        SignInProblem.Insecure
                    } else {
                        SignInProblem.BadAddress
                    },
                ),
            )
        } catch (e: SyncException) {
            Result.failure(SignInError(problemOf(e.problem)))
        }

    fun problemOf(problem: SyncProblem): SignInProblem =
        when (problem) {
            SyncProblem.Unreachable -> SignInProblem.Unreachable
            SyncProblem.WrongLogin -> SignInProblem.WrongLogin
            SyncProblem.NotCalDav -> SignInProblem.NotCalDav
            SyncProblem.Other -> SignInProblem.Other
        }
}
