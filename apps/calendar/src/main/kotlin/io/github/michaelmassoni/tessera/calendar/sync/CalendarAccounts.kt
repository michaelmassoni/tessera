package io.github.michaelmassoni.tessera.calendar.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import androidx.core.content.edit
import io.github.michaelmassoni.tessera.core.dav.Dav
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
import java.util.TimeZone

/** Calendar's server account: `dav.xml` (left out of every backup), password encrypted by Keystore. */
class CalendarDavStore(
    context: Context,
) : DavAccountStore(context, "dav", "calendar_dav_password")

/**
 * Signing in to a CalDAV server, which calendars to sync, and signing out. Synced calendars live
 * in the system calendar database under an Android account of [ACCOUNT_TYPE] (docs/sync.md).
 */
object CalendarAccounts {
    const val ACCOUNT_TYPE = "io.github.michaelmassoni.tessera.calendar"
    private const val PREFS = "calendar_sync"

    /** The Android account, if signed in. */
    fun account(context: Context): Account? = AccountManager.get(context).getAccountsByType(ACCOUNT_TYPE).firstOrNull()

    /** Sync-adapter access to [account]'s rows in the calendar database. */
    fun asSyncAdapter(
        uri: android.net.Uri,
        account: Account,
    ): android.net.Uri =
        uri
            .buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, account.name)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, account.type)
            .build()

    /** Checks the login, saves it, adds the Android account and returns the server's event calendars. */
    suspend fun signIn(
        context: Context,
        address: String,
        username: String,
        password: String,
    ): Result<List<RemoteCollection>> =
        withContext(Dispatchers.IO) {
            DavSignIn.discover(address, username, password, DavKind.CalDav).mapCatching { dav ->
                val calendars =
                    try {
                        eventCalendars(DavClient(dav.username, password), dav.home)
                    } catch (e: SyncException) {
                        throw SignInError(DavSignIn.problemOf(e.problem))
                    }
                CalendarDavStore(context).save(dav, password)
                val host = runCatching { URI(dav.address).host }.getOrNull() ?: dav.address
                val account = Account("${dav.username}@$host", ACCOUNT_TYPE)
                account(context)?.takeIf { it != account }?.let { AccountManager.get(context).removeAccountExplicitly(it) }
                AccountManager.get(context).addAccountExplicitly(account, null, null)
                ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
                // Periodic syncs are WorkManager's (Sync settings), not the framework's.
                ContentResolver.getPeriodicSyncs(account, CalendarContract.AUTHORITY).forEach {
                    ContentResolver.removePeriodicSync(account, CalendarContract.AUTHORITY, it.extras)
                }
                applyAutoSync(context)
                calendars
            }
        }

    private fun eventCalendars(
        client: DavClient,
        home: String,
    ) = client.calendars(home, "VEVENT").map {
        RemoteCollection(it.href, it.displayName ?: it.href.trimEnd('/').substringAfterLast('/'), Dav.color(it.color), it.writable != false)
    }

    /** The server's calendars now (for choosing more later). */
    suspend fun remoteCalendars(context: Context): List<RemoteCollection> =
        withContext(Dispatchers.IO) {
            val store = CalendarDavStore(context)
            val dav = store.account ?: return@withContext emptyList()
            eventCalendars(store.client() ?: return@withContext emptyList(), dav.home)
        }

    /** The hrefs of the calendars syncing now. */
    fun syncedHrefs(context: Context): Set<String> {
        val account = account(context) ?: return emptySet()
        val out = mutableSetOf<String>()
        // Without calendar access there's nothing to read (the app asks for it at first run).
        if (context.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return out
        }
        context.contentResolver
            .query(
                Calendars.CONTENT_URI,
                arrayOf(Calendars.NAME),
                "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
                arrayOf(account.name, account.type),
                null,
            )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { out += it } }
        return out
    }

    /**
     * Syncs [chosen] (adding calendars not here yet) and stops syncing the rest: their events
     * leave this phone (they stay on the server). Then syncs now.
     */
    fun useCalendars(
        context: Context,
        chosen: List<RemoteCollection>,
    ) {
        val account = account(context) ?: return
        val resolver = context.contentResolver
        val have = syncedHrefs(context)
        val uri = asSyncAdapter(Calendars.CONTENT_URI, account)
        for (c in chosen.filter { it.href !in have }) {
            resolver.insert(
                uri,
                ContentValues().apply {
                    put(Calendars.ACCOUNT_NAME, account.name)
                    put(Calendars.ACCOUNT_TYPE, account.type)
                    put(Calendars.NAME, c.href)
                    put(Calendars._SYNC_ID, c.href)
                    put(Calendars.CALENDAR_DISPLAY_NAME, c.name)
                    put(Calendars.CALENDAR_COLOR, c.color ?: DEFAULT_COLOR)
                    put(Calendars.CALENDAR_ACCESS_LEVEL, if (c.writable) Calendars.CAL_ACCESS_OWNER else Calendars.CAL_ACCESS_READ)
                    put(Calendars.OWNER_ACCOUNT, account.name)
                    put(Calendars.VISIBLE, 1)
                    put(Calendars.SYNC_EVENTS, 1)
                    put(Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
                    put(
                        Calendars.ALLOWED_REMINDERS,
                        "${CalendarContract.Reminders.METHOD_DEFAULT},${CalendarContract.Reminders.METHOD_ALERT}",
                    )
                    put(
                        Calendars.ALLOWED_AVAILABILITY,
                        "${CalendarContract.Events.AVAILABILITY_BUSY},${CalendarContract.Events.AVAILABILITY_FREE}",
                    )
                },
            )
        }
        val keep = chosen.map { it.href }.toSet()
        (have - keep).forEach { href ->
            resolver.delete(uri, "${Calendars.NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?", arrayOf(href, account.type))
        }
        CalendarSyncWorker.schedule.apply(context)
        CalendarSyncWorker.schedule.soon(context, delaySeconds = 0)
    }

    /** Makes a calendar on the server and syncs it; false if the server refused. */
    suspend fun createRemoteCalendar(
        context: Context,
        name: String,
        color: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val store = CalendarDavStore(context)
            val dav = store.account ?: return@withContext false
            val client = store.client() ?: return@withContext false
            runCatching {
                val href =
                    client.makeCalendar(
                        dav.home,
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                        name,
                        "VEVENT",
                        color,
                    )
                val synced = syncedHrefs(context)
                val all = eventCalendars(client, dav.home)
                useCalendars(context, all.filter { it.href in synced || it.href == href })
            }.isSuccess
        }

    /** "Immediately on local changes": the framework's content trigger for the account. */
    fun applyAutoSync(context: Context) {
        val account = account(context) ?: return
        ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, SyncPrefs.load(context).onLocalChange)
    }

    /** Signs out: the account and its calendars leave this phone (they stay on the server). */
    fun signOut(context: Context) {
        account(context)?.let { AccountManager.get(context).removeAccountExplicitly(it) }
        CalendarDavStore(context).signOut()
        CalendarSyncWorker.schedule.cancel(context)
    }

    /** "Ignore events older than": days, or 0 to keep all. Default 90, as DAVx5. */
    fun pastDays(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("past_days", 90)

    fun setPastDays(
        context: Context,
        days: Int,
    ) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt("past_days", days) }

    /** A manual sync request through the framework (shows in system Settings > Accounts). */
    fun requestSync(context: Context) {
        val account = account(context) ?: return
        ContentResolver.requestSync(
            account,
            CalendarContract.AUTHORITY,
            Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            },
        )
    }

    /**
     * Calendars another app syncs from the same server: their collection path to that app's
     * account type. DAVx5 keeps each calendar's URL in Calendars.NAME.
     */
    fun syncedElsewhere(context: Context): Map<String, String> {
        val out = mutableMapOf<String, String>()
        runCatching {
            context.contentResolver
                .query(
                    Calendars.CONTENT_URI,
                    arrayOf(Calendars.NAME, Calendars.ACCOUNT_TYPE),
                    "${Calendars.ACCOUNT_TYPE} != ?",
                    arrayOf(ACCOUNT_TYPE),
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val name = c.getString(0) ?: continue
                        if (!name.startsWith("http")) continue
                        runCatching { URI(name).path.trimEnd('/') }.getOrNull()?.let { out[it] = c.getString(1).orEmpty() }
                    }
                }
        }
        return out
    }

    /** The account type DAVx5 syncs under. */
    const val DAVX5 = "bitfire.at.davdroid"

    private const val DEFAULT_COLOR = 0xFF1A73A8.toInt()
}
