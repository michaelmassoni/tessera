package io.github.michaelmassoni.tessera.contacts.data

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import io.github.michaelmassoni.tessera.contacts.engine.AccountRef
import io.github.michaelmassoni.tessera.contacts.engine.ContactDetail
import io.github.michaelmassoni.tessera.contacts.engine.ContactForm
import io.github.michaelmassoni.tessera.contacts.engine.ContactSummary
import io.github.michaelmassoni.tessera.contacts.engine.EditDiff
import io.github.michaelmassoni.tessera.contacts.engine.Labeled
import io.github.michaelmassoni.tessera.contacts.engine.RowKind
import io.github.michaelmassoni.tessera.contacts.engine.RowOp
import io.github.michaelmassoni.tessera.contacts.engine.VCardContact

/**
 * Reads and writes the system contacts database. Blocking: call off the main thread.
 * Queries ask only for the columns they use, and every cursor is closed.
 */
class ContactsRepository(
    private val resolver: ContentResolver,
) {
    fun loadAll(): List<ContactSummary> {
        val phones = mutableMapOf<Long, MutableList<String>>()
        val emails = mutableMapOf<Long, MutableList<String>>()
        resolver
            .query(
                Data.CONTENT_URI,
                arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1),
                "${Data.MIMETYPE} IN (?, ?)",
                arrayOf(Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val value = c.getString(2) ?: continue
                    if (c.getString(1) ==
                        Phone.CONTENT_ITEM_TYPE
                    ) {
                        phones.getOrPut(id) { mutableListOf() } += value
                    } else {
                        emails.getOrPut(id) { mutableListOf() } +=
                            value
                    }
                }
            }
        val accounts = mutableMapOf<Long, MutableSet<AccountRef>>()
        resolver
            .query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts.CONTACT_ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
                "${RawContacts.DELETED} = 0",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) accounts.getOrPut(c.getLong(0)) { mutableSetOf() } += AccountRef(c.getString(1), c.getString(2))
            }
        val contacts = mutableListOf<ContactSummary>()
        resolver
            .query(
                Contacts.CONTENT_URI,
                arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED, Contacts.PHOTO_THUMBNAIL_URI),
                null,
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    contacts +=
                        ContactSummary(
                            id = id,
                            lookupKey = c.getString(1).orEmpty(),
                            name = c.getString(2).orEmpty(),
                            starred = c.getInt(3) != 0,
                            photoUri = c.getString(4),
                            phones = phones[id].orEmpty(),
                            emails = emails[id].orEmpty(),
                            accounts = accounts[id].orEmpty(),
                        )
                }
            }
        return contacts
    }

    /** The contact for a lookup or content URI handed over by another app, if it exists. */
    fun resolveId(uri: Uri): Long? {
        val lookup = runCatching { Contacts.lookupContact(resolver, uri) }.getOrNull() ?: uri
        return resolver.query(lookup, arrayOf(Contacts._ID), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }

    fun loadDetail(id: Long): ContactDetail? {
        val head =
            resolver
                .query(
                    ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
                    arrayOf(Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED, Contacts.PHOTO_URI),
                    null,
                    null,
                    null,
                )?.use { c ->
                    if (c.moveToFirst()) arrayOf(c.getString(0), c.getString(1), c.getInt(2).toString(), c.getString(3)) else null
                }
                ?: return null
        val rawAccounts = mutableMapOf<Long, AccountRef>()
        resolver
            .query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
                "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0",
                arrayOf(id.toString()),
                null,
            )?.use { c -> while (c.moveToNext()) rawAccounts[c.getLong(0)] = AccountRef(c.getString(1), c.getString(2)) }

        val phones = mutableListOf<Labeled>()
        val emails = mutableListOf<Labeled>()
        val addresses = mutableListOf<Labeled>()
        val events = mutableListOf<Labeled>()
        val websites = mutableListOf<String>()
        var form = ContactForm()
        var company = ""
        var title = ""
        var note = ""
        var nameRaw: Long? = null
        val rowRaws = mutableMapOf<Long, Long>()
        resolver
            .query(
                Data.CONTENT_URI,
                arrayOf(Data._ID, Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4),
                "${Data.CONTACT_ID} = ?",
                arrayOf(id.toString()),
                "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID}",
            )?.use { c ->
                while (c.moveToNext()) {
                    val rowId = c.getLong(0)
                    val raw = c.getLong(1)
                    rowRaws[rowId] = raw
                    val d1 = c.getString(3).orEmpty()
                    val type = c.getInt(4)
                    val label = c.getString(5)
                    when (c.getString(2)) {
                        StructuredName.CONTENT_ITEM_TYPE -> {
                            if (form.nameId == null) {
                                form =
                                    form.copy(givenName = c.getString(4).orEmpty(), familyName = c.getString(5).orEmpty(), nameId = rowId)
                                nameRaw = raw
                            }
                        }

                        Phone.CONTENT_ITEM_TYPE -> {
                            if (d1.isNotBlank()) phones += Labeled(d1, type, label, rowId)
                        }

                        Email.CONTENT_ITEM_TYPE -> {
                            if (d1.isNotBlank()) emails += Labeled(d1, type, label, rowId)
                        }

                        StructuredPostal.CONTENT_ITEM_TYPE -> {
                            if (d1.isNotBlank()) addresses += Labeled(d1, type, label, rowId)
                        }

                        Event.CONTENT_ITEM_TYPE -> {
                            if (d1.isNotBlank()) {
                                events += Labeled(d1, type, label, rowId)
                                if (type == Event.TYPE_BIRTHDAY &&
                                    form.birthdayId == null
                                ) {
                                    form = form.copy(birthday = d1, birthdayId = rowId)
                                }
                            }
                        }

                        Organization.CONTENT_ITEM_TYPE -> {
                            if (form.companyId == null) {
                                company = d1
                                title = c.getString(6).orEmpty()
                                form = form.copy(company = d1, companyId = rowId)
                            }
                        }

                        Note.CONTENT_ITEM_TYPE -> {
                            if (form.noteId == null && d1.isNotBlank()) {
                                note = d1
                                form = form.copy(note = d1, noteId = rowId)
                            }
                        }

                        Website.CONTENT_ITEM_TYPE -> {
                            if (d1.isNotBlank()) websites += d1
                        }
                    }
                }
            }
        // The editor only edits rows of one raw contact; rows from other raw contacts are shown but read-only in the form.
        val editRaw = nameRaw ?: rawAccounts.keys.firstOrNull() ?: return null
        form =
            form.copy(
                phones = phones.filter { rowRaws[it.id] == editRaw },
                emails = emails.filter { rowRaws[it.id] == editRaw },
                address = addresses.firstOrNull { rowRaws[it.id] == editRaw },
            )
        return ContactDetail(
            id = id,
            lookupKey = head[0].orEmpty(),
            name = head[1].orEmpty(),
            starred = head[2] == "1",
            photoUri = head[3],
            phones = phones,
            emails = emails,
            addresses = addresses,
            events = events,
            websites = websites,
            company = company,
            title = title,
            note = note,
            accounts = rawAccounts.values.toSet(),
            rawContactId = editRaw,
            form = form,
        )
    }

    /** Accounts that already hold contacts, plus the device account, device first. */
    fun accounts(): List<AccountRef> {
        val found = linkedSetOf(AccountRef.Device)
        resolver
            .query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
                "${RawContacts.DELETED} = 0",
                null,
                null,
            )?.use { c -> while (c.moveToNext()) found += AccountRef(c.getString(0), c.getString(1)) }
        return found.toList()
    }

    /** Saves an edit of an existing contact. */
    fun update(
        rawContactId: Long,
        original: ContactForm,
        edited: ContactForm,
    ) {
        val ops = EditDiff.diff(original, edited).map { op -> op.toOperation { withValue(Data.RAW_CONTACT_ID, rawContactId) } }
        if (ops.isNotEmpty()) resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
    }

    /** Creates a contact in [account]. @return its contact id, if the provider reports it. */
    fun create(
        account: AccountRef,
        form: ContactForm,
    ): Long? {
        val ops = arrayListOf(rawContactInsert(account))
        EditDiff.diff(ContactForm(), form).forEach { op -> ops += op.toOperation { withValueBackReference(Data.RAW_CONTACT_ID, 0) } }
        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        val rawUri = results.firstOrNull()?.uri ?: return null
        return resolver.query(rawUri, arrayOf(RawContacts.CONTACT_ID), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }
    }

    fun setStarred(
        id: Long,
        starred: Boolean,
    ) {
        resolver.update(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
            android.content.ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) },
            null,
            null,
        )
    }

    fun delete(id: Long) {
        resolver.delete(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), null, null)
    }

    /** Imports vCards into [account], one batch per contact. @return how many were created. */
    fun import(
        account: AccountRef,
        cards: List<VCardContact>,
    ): Int =
        cards.count { card ->
            val (given, family) = card.names()
            val form =
                ContactForm(
                    givenName = given,
                    familyName = family,
                    phones = card.phones.map { (number, types) -> Labeled(number, VCardTypes.phone(types)) },
                    emails = card.emails.map { (address, types) -> Labeled(address, VCardTypes.email(types)) },
                    address = card.addresses.firstOrNull()?.let { (text, types) -> Labeled(text, VCardTypes.postal(types)) },
                    company = card.organization,
                    birthday = card.birthday,
                    note = card.note,
                )
            !form.isEmpty && runCatching { create(account, form) }.isSuccess
        }

    /** A vCard URI covering [lookupKeys] (the system writes the file). */
    fun vcardUri(lookupKeys: List<String>): Uri =
        if (lookupKeys.size == 1) {
            Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKeys[0])
        } else {
            Uri.withAppendedPath(Contacts.CONTENT_MULTI_VCARD_URI, Uri.encode(lookupKeys.joinToString(":")))
        }

    private fun rawContactInsert(account: AccountRef): ContentProviderOperation =
        ContentProviderOperation
            .newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_NAME, account.name)
            .withValue(RawContacts.ACCOUNT_TYPE, account.type)
            .build()

    private fun RowOp.toOperation(
        attach: ContentProviderOperation.Builder.() -> ContentProviderOperation.Builder,
    ): ContentProviderOperation =
        when (this) {
            is RowOp.Insert -> {
                ContentProviderOperation
                    .newInsert(Data.CONTENT_URI)
                    .attach()
                    .withValue(Data.MIMETYPE, kind.mime())
                    .apply { values.forEach { (k, v) -> withValue(k, v) } }
                    .build()
            }

            is RowOp.Update -> {
                ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, id))
                    .apply { values.forEach { (k, v) -> withValue(k, v) } }
                    .build()
            }

            is RowOp.Delete -> {
                ContentProviderOperation.newDelete(ContentUris.withAppendedId(Data.CONTENT_URI, id)).build()
            }
        }

    private fun RowKind.mime(): String =
        when (this) {
            RowKind.Name -> StructuredName.CONTENT_ITEM_TYPE
            RowKind.Phone -> Phone.CONTENT_ITEM_TYPE
            RowKind.Email -> Email.CONTENT_ITEM_TYPE
            RowKind.Address -> StructuredPostal.CONTENT_ITEM_TYPE
            RowKind.Organization -> Organization.CONTENT_ITEM_TYPE
            RowKind.Birthday -> Event.CONTENT_ITEM_TYPE
            RowKind.Note -> Note.CONTENT_ITEM_TYPE
        }

    init {
        check(EditDiff.DATA1 == Data.DATA1 && EditDiff.DATA2 == Data.DATA2 && EditDiff.DATA3 == Data.DATA3)
        check(EditDiff.EVENT_TYPE_BIRTHDAY == Event.TYPE_BIRTHDAY)
    }
}

/** vCard TYPE values → ContactsContract type constants. */
object VCardTypes {
    fun phone(types: Set<String>): Int =
        when {
            "CELL" in types && "WORK" in types -> Phone.TYPE_WORK_MOBILE
            "CELL" in types -> Phone.TYPE_MOBILE
            "FAX" in types && "WORK" in types -> Phone.TYPE_FAX_WORK
            "FAX" in types -> Phone.TYPE_FAX_HOME
            "PAGER" in types -> Phone.TYPE_PAGER
            "WORK" in types -> Phone.TYPE_WORK
            "HOME" in types -> Phone.TYPE_HOME
            "MAIN" in types -> Phone.TYPE_MAIN
            else -> Phone.TYPE_MOBILE
        }

    fun email(types: Set<String>): Int =
        when {
            "WORK" in types -> Email.TYPE_WORK
            "HOME" in types -> Email.TYPE_HOME
            else -> Email.TYPE_OTHER
        }

    fun postal(types: Set<String>): Int =
        when {
            "WORK" in types -> StructuredPostal.TYPE_WORK
            "HOME" in types -> StructuredPostal.TYPE_HOME
            else -> StructuredPostal.TYPE_OTHER
        }
}
