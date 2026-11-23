package io.github.michaelmassoni.tessera.contacts.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.core.dav.DavAccountStore
import io.github.michaelmassoni.tessera.core.dav.DavClient
import io.github.michaelmassoni.tessera.core.dav.DavKind
import io.github.michaelmassoni.tessera.core.dav.DavSignIn
import io.github.michaelmassoni.tessera.core.dav.RemoteCollection
import io.github.michaelmassoni.tessera.core.dav.SignInError
import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.core.dav.SyncPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/** Contacts' server account: `dav.xml` (left out of every backup), password encrypted by Keystore. */
class ContactsDavStore(
    context: Context,
) : DavAccountStore(context, "dav", "contacts_dav_password")

/** How groups are kept on the server (docs/sync.md), as DAVx5 offers. */
enum class GroupMethod { SeparateVCards, Categories }

/**
 * Signing in to a CardDAV server, which address books to sync, the group method, and signing out.
 * Each synced address book is an Android account of [ACCOUNT_TYPE] (as DAVx5 does), so contacts
 * live in the system contacts database and the editor's account choice says where a new one goes.
 */
object ContactsAccounts {
    const val ACCOUNT_TYPE = "io.github.michaelmassoni.tessera.contacts"
    private const val KEY_HREF = "href"
    private const val PREFS = "contacts_sync"

    fun accounts(context: Context): List<Account> = AccountManager.get(context).getAccountsByType(ACCOUNT_TYPE).toList()

    fun href(
        context: Context,
        account: Account,
    ): String? = AccountManager.get(context).getUserData(account, KEY_HREF)

    fun token(
        context: Context,
        account: Account,
    ): String? = AccountManager.get(context).getUserData(account, "token")

    fun setToken(
        context: Context,
        account: Account,
        token: String?,
    ) = AccountManager.get(context).setUserData(account, "token", token)

    /** Sync-adapter access to [account]'s rows in the contacts database. */
    fun asSyncAdapter(
        uri: Uri,
        account: Account,
    ): Uri =
        uri
            .buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
            .build()

    /** Checks the login, saves it and returns the server's address books. */
    suspend fun signIn(
        context: Context,
        address: String,
        username: String,
        password: String,
    ): Result<List<RemoteCollection>> =
        withContext(Dispatchers.IO) {
            DavSignIn.discover(address, username, password, DavKind.CardDav).mapCatching { dav ->
                val books =
                    try {
                        addressBooks(DavClient(dav.username, password), dav.home)
                    } catch (e: SyncException) {
                        throw SignInError(DavSignIn.problemOf(e.problem))
                    }
                ContactsDavStore(context).save(dav, password)
                books
            }
        }

    private fun addressBooks(
        client: DavClient,
        home: String,
    ) = client.addressBooks(home).map {
        RemoteCollection(it.href, it.displayName ?: it.href.trimEnd('/').substringAfterLast('/'), null, it.writable != false)
    }

    suspend fun remoteAddressBooks(context: Context): List<RemoteCollection> =
        withContext(Dispatchers.IO) {
            val store = ContactsDavStore(context)
            val dav = store.account ?: return@withContext emptyList()
            addressBooks(store.client() ?: return@withContext emptyList(), dav.home)
        }

    fun syncedHrefs(context: Context): Set<String> = accounts(context).mapNotNull { href(context, it) }.toSet()

    /**
     * Syncs [chosen] address books (each its own account) and stops syncing the rest: their
     * contacts leave this phone (they stay on the server). Then syncs now.
     */
    fun useAddressBooks(
        context: Context,
        chosen: List<RemoteCollection>,
    ) {
        val manager = AccountManager.get(context)
        val dav = ContactsDavStore(context).account ?: return
        val host = runCatching { URI(dav.address).host }.getOrNull() ?: dav.address
        val existing = accounts(context).associateBy { href(context, it) }
        for (book in chosen) {
            if (book.href in existing) continue
            val taken = accounts(context).map { it.name }.toSet()
            var name = "${book.name} (${dav.username}@$host)"
            var n = 2
            while (name in taken) name = "${book.name} ${n++} (${dav.username}@$host)"
            val account = Account(name, ACCOUNT_TYPE)
            manager.addAccountExplicitly(account, null, Bundle().apply { putString(KEY_HREF, book.href) })
            ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)
            ContentResolver.getPeriodicSyncs(account, ContactsContract.AUTHORITY).forEach {
                ContentResolver.removePeriodicSync(account, ContactsContract.AUTHORITY, it.extras)
            }
            // Contacts outside any group are shown (apps that honour visibility would hide them).
            runCatching {
                context.contentResolver.insert(
                    asSyncAdapter(ContactsContract.Settings.CONTENT_URI, account),
                    ContentValues().apply {
                        put(ContactsContract.Settings.ACCOUNT_NAME, account.name)
                        put(ContactsContract.Settings.ACCOUNT_TYPE, account.type)
                        put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
                        put(ContactsContract.Settings.SHOULD_SYNC, 1)
                    },
                )
            }
        }
        val keep = chosen.map { it.href }.toSet()
        existing.filterKeys { it !in keep }.values.forEach { manager.removeAccountExplicitly(it) }
        applyAutoSync(context)
        ContactsSyncWorker.schedule.apply(context)
        ContactsSyncWorker.schedule.soon(context, delaySeconds = 0)
    }

    fun applyAutoSync(context: Context) {
        val on = SyncPrefs.load(context).onLocalChange
        accounts(context).forEach { ContentResolver.setSyncAutomatically(it, ContactsContract.AUTHORITY, on) }
    }

    /** Signs out: the address books' contacts leave this phone (they stay on the server). */
    fun signOut(context: Context) {
        val manager = AccountManager.get(context)
        accounts(context).forEach { manager.removeAccountExplicitly(it) }
        ContactsDavStore(context).signOut()
        ContactsSyncWorker.schedule.cancel(context)
    }

    fun groupMethod(context: Context): GroupMethod =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("group_method", null)
            ?.let { n -> GroupMethod.entries.firstOrNull { it.name == n } } ?: GroupMethod.SeparateVCards

    /** The method the next sync switches to (after sending local changes the old way), or null. */
    fun pendingGroupMethod(context: Context): GroupMethod? =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("group_method_next", null)
            ?.let { n -> GroupMethod.entries.firstOrNull { it.name == n } }

    /**
     * Changes the group method: the next sync sends local changes the old way, then reads the
     * address books again from the server the new way (as DAVx5 does).
     */
    fun setGroupMethod(
        context: Context,
        method: GroupMethod,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (accounts(context).isEmpty()) {
            prefs.edit { putString("group_method", method.name).remove("group_method_next") }
            return
        }
        if (method == groupMethod(context)) {
            prefs.edit { remove("group_method_next") }
            return
        }
        prefs.edit { putString("group_method_next", method.name) }
        ContactsSyncWorker.schedule.soon(context, delaySeconds = 0)
    }

    /** After the switch: the new method is the current one. */
    fun completeGroupMethod(
        context: Context,
        method: GroupMethod,
    ) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
        putString("group_method", method.name).remove("group_method_next")
    }
}
