package io.github.michaelmassoni.tessera.phone.data

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import io.github.michaelmassoni.tessera.phone.engine.CallEntry
import io.github.michaelmassoni.tessera.phone.engine.CallType

/** A phone number of a contact. */
data class ContactNumber(
    val contactId: Long,
    val lookupKey: String,
    val name: String,
    val number: String,
    val label: String,
    val starred: Boolean,
    val photoUri: String?,
)

/** Call log and contact numbers. Blocking: call off the main thread. Each call needs its permission. */
class PhoneRepository(
    private val resolver: ContentResolver,
) {
    fun callLog(limit: Int = 500): List<CallEntry> {
        val calls = mutableListOf<CallEntry>()
        resolver
            .query(
                CallLog.Calls.CONTENT_URI
                    .buildUpon()
                    .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString())
                    .build(),
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) {
                    calls +=
                        CallEntry(
                            id = c.getLong(0),
                            number = c.getString(1).orEmpty(),
                            name = c.getString(2)?.takeIf { it.isNotBlank() },
                            type =
                                when (c.getInt(3)) {
                                    CallLog.Calls.INCOMING_TYPE -> CallType.Incoming
                                    CallLog.Calls.OUTGOING_TYPE -> CallType.Outgoing
                                    CallLog.Calls.MISSED_TYPE -> CallType.Missed
                                    CallLog.Calls.REJECTED_TYPE -> CallType.Rejected
                                    CallLog.Calls.BLOCKED_TYPE -> CallType.Blocked
                                    CallLog.Calls.VOICEMAIL_TYPE -> CallType.Voicemail
                                    else -> CallType.Other
                                },
                            date = c.getLong(4),
                            durationSeconds = c.getLong(5),
                        )
                }
            }
        return calls
    }

    /** Every phone number of every contact, sorted by name. */
    fun numbers(): List<ContactNumber> {
        val list = mutableListOf<ContactNumber>()
        resolver
            .query(
                Phone.CONTENT_URI,
                arrayOf(
                    Phone.CONTACT_ID,
                    Phone.LOOKUP_KEY,
                    Phone.DISPLAY_NAME_PRIMARY,
                    Phone.NUMBER,
                    Phone.TYPE,
                    Phone.LABEL,
                    Phone.STARRED,
                    Phone.PHOTO_THUMBNAIL_URI,
                ),
                null,
                null,
                "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    list +=
                        ContactNumber(
                            contactId = c.getLong(0),
                            lookupKey = c.getString(1).orEmpty(),
                            name = c.getString(2).orEmpty(),
                            number = c.getString(3).orEmpty(),
                            label = c.getInt(4).toString() + "|" + c.getString(5).orEmpty(),
                            starred = c.getInt(6) != 0,
                            photoUri = c.getString(7),
                        )
                }
            }
        return list
    }

    /** Blocked numbers (only readable by the default phone or SMS app). */
    fun blockedNumbers(): List<String> {
        val list = mutableListOf<String>()
        resolver
            .query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null,
                null,
                null,
            )?.use { c -> while (c.moveToNext()) c.getString(0)?.let(list::add) }
        return list
    }

    fun block(number: String) {
        resolver.insert(
            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
            ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) },
        )
    }

    /** Removes calls from the call log (needs WRITE_CALL_LOG). */
    fun deleteCalls(ids: List<Long>) {
        if (ids.isEmpty()) return
        resolver.delete(
            CallLog.Calls.CONTENT_URI,
            "${CallLog.Calls._ID} IN (${ids.joinToString(",") { "?" }})",
            ids
                .map {
                    it.toString()
                }.toTypedArray(),
        )
    }

    /** The contact name for [number], if any (used for incoming calls). */
    fun nameFor(number: String): Pair<String, String?>? {
        if (number.isBlank()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            resolver
                .query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI),
                    null,
                    null,
                    null,
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
                }
        }.getOrNull()
    }
}
