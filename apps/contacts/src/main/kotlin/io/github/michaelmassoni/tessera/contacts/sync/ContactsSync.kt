package io.github.michaelmassoni.tessera.contacts.sync

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import io.github.michaelmassoni.tessera.contacts.engine.CardContact
import io.github.michaelmassoni.tessera.contacts.engine.CardGroup
import io.github.michaelmassoni.tessera.contacts.engine.CardMapping
import io.github.michaelmassoni.tessera.contacts.engine.CardTypes
import io.github.michaelmassoni.tessera.contacts.engine.PostalAddress
import io.github.michaelmassoni.tessera.contacts.engine.Typed
import io.github.michaelmassoni.tessera.core.dav.Component
import io.github.michaelmassoni.tessera.core.dav.Dav
import io.github.michaelmassoni.tessera.core.dav.DavClient
import io.github.michaelmassoni.tessera.core.dav.DavKind
import io.github.michaelmassoni.tessera.core.dav.SyncException
import io.github.michaelmassoni.tessera.core.dav.SyncProblem
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID

/**
 * Syncs one address book (one Android account) with the server: local changes go up first
 * (contacts, then groups), then the server's changes come down. On a conflict the server wins.
 *
 * A raw contact's resource name is its SOURCE_ID, its ETag SYNC1, its vCard UID SYNC2 and a hash
 * of its photo as last synced SYNC3. The server's last text of each resource is kept in the app's
 * own storage (vCards with photos are too big for a column), so writing back changes only what
 * changed. Groups: SOURCE_ID, SYNC1 and SYNC2 the same, for the "separate vCards" method.
 */
class ContactsSync(
    private val context: Context,
    private val account: Account,
    private val client: DavClient,
    private val method: GroupMethod,
) {
    private val resolver: ContentResolver = context.contentResolver
    private val href = ContactsAccounts.href(context, account) ?: throw SyncException(SyncProblem.Other, "no address book")
    private val rawUri = ContactsAccounts.asSyncAdapter(RawContacts.CONTENT_URI, account)
    private val dataUri = ContactsAccounts.asSyncAdapter(Data.CONTENT_URI, account)
    private val groupsUri = ContactsAccounts.asSyncAdapter(Groups.CONTENT_URI, account)
    private val store = File(context.filesDir, "cards/" + hash(account.name.toByteArray()).take(16)).apply { mkdirs() }

    fun sync() {
        if (method == GroupMethod.Categories) markMembersOfChangedGroups()
        val refetch = uploadContacts()
        if (method == GroupMethod.SeparateVCards) refetch += uploadGroups()
        download(refetch)
    }

    /** Wipes this address book from the phone (to read it again, after a group method change). */
    fun wipe() {
        resolver.delete(rawUri, "${RawContacts.ACCOUNT_NAME} = ? AND ${RawContacts.ACCOUNT_TYPE} = ?", arrayOf(account.name, account.type))
        resolver.delete(groupsUri, "${Groups.ACCOUNT_NAME} = ? AND ${Groups.ACCOUNT_TYPE} = ?", arrayOf(account.name, account.type))
        store.listFiles()?.forEach { it.delete() }
        ContactsAccounts.setToken(context, account, null)
    }

    // ---- local rows ----

    private data class LocalRaw(
        val id: Long,
        val name: String?,
        val etag: String?,
        val uid: String?,
        val photoHash: String?,
        val dirty: Boolean,
        val deleted: Boolean,
    )

    private fun raws(
        selection: String? = null,
        args: Array<String>? = null,
    ): List<LocalRaw> {
        val out = mutableListOf<LocalRaw>()
        val where = listOfNotNull("${RawContacts.ACCOUNT_NAME} = ? AND ${RawContacts.ACCOUNT_TYPE} = ?", selection).joinToString(" AND ")
        resolver
            .query(
                rawUri,
                arrayOf(
                    RawContacts._ID,
                    RawContacts.SOURCE_ID,
                    RawContacts.SYNC1,
                    RawContacts.SYNC2,
                    RawContacts.SYNC3,
                    RawContacts.DIRTY,
                    RawContacts.DELETED,
                ),
                where,
                arrayOf(account.name, account.type) + args.orEmpty(),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out +=
                        LocalRaw(
                            c.getLong(0),
                            c.getString(1),
                            c.getString(2),
                            c.getString(3),
                            c.getString(4),
                            c.getInt(5) != 0,
                            c.getInt(6) != 0,
                        )
                }
            }
        return out
    }

    private data class LocalGroup(
        val id: Long,
        val name: String?,
        val etag: String?,
        val uid: String?,
        val title: String,
        val dirty: Boolean,
        val deleted: Boolean,
    )

    private fun groups(): List<LocalGroup> {
        val out = mutableListOf<LocalGroup>()
        resolver
            .query(
                groupsUri,
                arrayOf(Groups._ID, Groups.SOURCE_ID, Groups.SYNC1, Groups.SYNC2, Groups.TITLE, Groups.DIRTY, Groups.DELETED),
                "${Groups.ACCOUNT_NAME} = ? AND ${Groups.ACCOUNT_TYPE} = ?",
                arrayOf(account.name, account.type),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    out +=
                        LocalGroup(
                            c.getLong(0),
                            c.getString(1),
                            c.getString(2),
                            c.getString(3),
                            c.getString(4).orEmpty(),
                            c.getInt(5) != 0,
                            c.getInt(6) != 0,
                        )
                }
            }
        return out
    }

    // ---- upload ----

    /** Categories method: a renamed or deleted group changes its members' vCards. */
    private fun markMembersOfChangedGroups() {
        for (g in groups().filter { it.dirty || it.deleted }) {
            members(g.id).forEach { raw -> setRaw(raw, ContentValues().apply { put(RawContacts.DIRTY, 1) }) }
            if (g.deleted) {
                resolver.delete(ContentUris.withAppendedId(groupsUri, g.id), null, null)
            } else {
                resolver.update(ContentUris.withAppendedId(groupsUri, g.id), ContentValues().apply { put(Groups.DIRTY, 0) }, null, null)
            }
        }
    }

    private fun uploadContacts(): MutableSet<String> {
        val refetch = mutableSetOf<String>()
        for (raw in raws("${RawContacts.DIRTY} = 1")) {
            if (raw.deleted) {
                raw.name?.let { client.delete(resource(it), null) }
                resolver.delete(ContentUris.withAppendedId(rawUri, raw.id), null, null)
                raw.name?.let { file(it).delete() }
                continue
            }
            val uid = raw.uid ?: UUID.randomUUID().toString()
            val name = raw.name ?: (URLEncoder.encode(uid, "UTF-8") + ".vcf")
            val previous = raw.name?.let(::saved)
            val contact = readContact(raw.id, uid)
            var text = CardMapping.writeContact(contact, previous)
            // The photo: sent again only when it changed here (the server's full-size one stays).
            val thumb = thumbnail(raw.id)
            val thumbHash = thumb?.let(::hash)
            if (thumbHash != raw.photoHash) text = CardMapping.withPhoto(text, thumb?.let { displayPhoto(raw.id) ?: it })
            val reply = client.put(resource(name), text, raw.name?.let { raw.etag }, DavKind.CardDav)
            when {
                reply.code in 200..299 -> {
                    setRaw(
                        raw.id,
                        ContentValues().apply {
                            put(RawContacts.SOURCE_ID, name)
                            put(RawContacts.SYNC1, reply.etag)
                            put(RawContacts.SYNC2, uid)
                            put(RawContacts.SYNC3, thumbHash)
                            put(RawContacts.DIRTY, 0)
                        },
                    )
                    file(name).writeText(text)
                    if (reply.etag == null) refetch += name
                }

                reply.code == 412 || reply.code == 409 || reply.code == 403 -> {
                    refetch += name
                }

                else -> {
                    throw SyncException(SyncProblem.Other, "PUT ${reply.code}")
                }
            }
        }
        return refetch
    }

    /** Separate-vCards method: groups renamed, made, deleted here, or whose members changed. */
    private fun uploadGroups(): Set<String> {
        val refetch = mutableSetOf<String>()
        val uids = raws().associate { it.id to it.uid }
        for (g in groups()) {
            if (g.deleted) {
                g.name?.let { client.delete(resource(it), null) }
                resolver.delete(ContentUris.withAppendedId(groupsUri, g.id), null, null)
                g.name?.let { file(it).delete() }
                continue
            }
            val members = members(g.id).mapNotNull { uids[it] }.sorted()
            val previous = g.name?.let(::saved)
            val before = previous?.let(CardMapping::readGroup)
            if (!g.dirty && before != null && before.name == g.title && before.members.sorted() == members) continue
            val uid = g.uid ?: UUID.randomUUID().toString()
            val name = g.name ?: (URLEncoder.encode(uid, "UTF-8") + ".vcf")
            val text = CardMapping.writeGroup(CardGroup(uid, g.title, members), previous)
            val reply = client.put(resource(name), text, g.name?.let { g.etag }, DavKind.CardDav)
            if (reply.code in 200..299) {
                resolver.update(
                    ContentUris.withAppendedId(groupsUri, g.id),
                    ContentValues().apply {
                        put(Groups.SOURCE_ID, name)
                        put(Groups.SYNC1, reply.etag)
                        put(Groups.SYNC2, uid)
                        put(Groups.DIRTY, 0)
                    },
                    null,
                    null,
                )
                file(name).writeText(text)
                if (reply.etag == null) refetch += name
            } else if (reply.code == 412 || reply.code == 409 || reply.code == 403) {
                refetch += name
            } else {
                throw SyncException(SyncProblem.Other, "PUT ${reply.code}")
            }
        }
        return refetch
    }

    // ---- download ----

    private fun download(refetch: Set<String>) {
        val localRaws = raws().filter { it.name != null }.associateBy { it.name!! }
        val localGroups = groups().filter { it.name != null }.associateBy { it.name!! }
        val etags = localRaws.mapValues { it.value.etag } + localGroups.mapValues { it.value.etag }
        val changed = mutableSetOf<String>()
        val gone = mutableSetOf<String>()
        val token = ContactsAccounts.token(context, account)
        var newToken: String? = token
        val changes = runCatching { client.changes(href, token) }.getOrNull()
        if (changes != null) {
            for (r in changes.responses) {
                val name = nameOf(r.href)
                if (r.status == 404) {
                    gone += name
                } else if (etags[name] != r.etag || r.etag == null) {
                    changed += name
                }
            }
            newToken = changes.syncToken
        } else {
            val onServer = client.etags(href, DavKind.CardDav).associate { nameOf(it.href) to it.etag }
            onServer.forEach { (name, etag) -> if (etags[name] != etag || etag == null) changed += name }
            gone += etags.keys - onServer.keys
            newToken = null
        }
        changed += refetch
        changed -= gone

        val groupsDownloaded = mutableListOf<Pair<Long, CardGroup>>()
        if (changed.isNotEmpty()) {
            for (r in client.fetch(href, changed.map(::resource), DavKind.CardDav)) {
                val name = nameOf(r.href)
                val text = r.data ?: continue
                val card = CardMapping.parse(text) ?: continue
                if (CardMapping.isGroup(card)) {
                    if (method != GroupMethod.SeparateVCards) continue
                    val group = CardMapping.readGroup(card) ?: continue
                    val id = storeGroup(name, r.etag, group, localGroups[name])
                    file(name).writeText(text)
                    groupsDownloaded += id to group
                } else {
                    val contact = CardMapping.readContact(card) ?: continue
                    storeContact(name, r.etag, contact, card, localRaws[name])
                    file(name).writeText(text)
                }
            }
        }
        for (name in gone) {
            localRaws[name]?.let { resolver.delete(ContentUris.withAppendedId(rawUri, it.id), null, null) }
            localGroups[name]?.let { resolver.delete(ContentUris.withAppendedId(groupsUri, it.id), null, null) }
            file(name).delete()
        }
        // Group members by UID, now every contact is here.
        if (groupsDownloaded.isNotEmpty()) {
            val byUid = raws().filter { it.uid != null }.associate { it.uid!! to it.id }
            for ((groupId, group) in groupsDownloaded) setMembers(groupId, group.members.mapNotNull { byUid[it] }.toSet())
        }
        if (newToken != token) ContactsAccounts.setToken(context, account, newToken)
    }

    private fun storeGroup(
        name: String,
        etag: String?,
        group: CardGroup,
        existing: LocalGroup?,
    ): Long {
        val values =
            ContentValues().apply {
                put(Groups.ACCOUNT_NAME, account.name)
                put(Groups.ACCOUNT_TYPE, account.type)
                put(Groups.SOURCE_ID, name)
                put(Groups.SYNC1, etag)
                put(Groups.SYNC2, group.uid)
                put(Groups.TITLE, group.name)
                put(Groups.GROUP_VISIBLE, 1)
                put(Groups.DIRTY, 0)
            }
        return if (existing != null) {
            resolver.update(ContentUris.withAppendedId(groupsUri, existing.id), values, null, null)
            existing.id
        } else {
            ContentUris.parseId(resolver.insert(groupsUri, values) ?: throw SyncException(SyncProblem.Other, "group insert"))
        }
    }

    private fun storeContact(
        name: String,
        etag: String?,
        contact: CardContact,
        card: Component,
        existing: LocalRaw?,
    ) {
        val id =
            if (existing != null) {
                resolver.delete(
                    dataUri,
                    "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} != ?",
                    arrayOf(existing.id.toString(), Photo.CONTENT_ITEM_TYPE),
                )
                existing.id
            } else {
                ContentUris.parseId(
                    resolver.insert(
                        rawUri,
                        ContentValues().apply {
                            put(RawContacts.ACCOUNT_NAME, account.name)
                            put(RawContacts.ACCOUNT_TYPE, account.type)
                        },
                    ) ?: throw SyncException(SyncProblem.Other, "contact insert"),
                )
            }
        writeData(id, contact)
        if (method == GroupMethod.Categories) setCategories(id, contact.categories)
        // The photo, full size; the provider makes the thumbnail.
        val photo = CardMapping.photo(card)
        if (photo != null) {
            writePhoto(id, photo)
        } else {
            resolver.delete(dataUri, "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?", arrayOf(id.toString(), Photo.CONTENT_ITEM_TYPE))
        }
        setRaw(
            id,
            ContentValues().apply {
                put(RawContacts.SOURCE_ID, name)
                put(RawContacts.SYNC1, etag)
                put(RawContacts.SYNC2, contact.uid)
                put(RawContacts.SYNC3, thumbnail(id)?.let(::hash))
                put(RawContacts.DIRTY, 0)
            },
        )
    }

    // ---- contact rows ----

    private fun insert(
        raw: Long,
        mime: String,
        values: ContentValues,
    ) {
        values.put(Data.RAW_CONTACT_ID, raw)
        values.put(Data.MIMETYPE, mime)
        resolver.insert(dataUri, values)
    }

    private fun writeData(
        raw: Long,
        c: CardContact,
    ) {
        insert(
            raw,
            StructuredName.CONTENT_ITEM_TYPE,
            ContentValues().apply {
                put(StructuredName.DISPLAY_NAME, c.formattedName.ifBlank { CardMapping.displayName(c) })
                put(StructuredName.GIVEN_NAME, c.given)
                put(StructuredName.FAMILY_NAME, c.family)
                put(StructuredName.MIDDLE_NAME, c.middle)
                put(StructuredName.PREFIX, c.prefix)
                put(StructuredName.SUFFIX, c.suffix)
            },
        )
        if (c.nickname.isNotBlank()) insert(raw, Nickname.CONTENT_ITEM_TYPE, ContentValues().apply { put(Nickname.NAME, c.nickname) })
        c.phones.forEach { insert(raw, Phone.CONTENT_ITEM_TYPE, typed(Phone.NUMBER, it)) }
        c.emails.forEach { insert(raw, Email.CONTENT_ITEM_TYPE, typed(Email.ADDRESS, it)) }
        c.addresses.forEach { a ->
            insert(
                raw,
                StructuredPostal.CONTENT_ITEM_TYPE,
                ContentValues().apply {
                    put(StructuredPostal.STREET, a.street)
                    put(StructuredPostal.POBOX, a.poBox)
                    put(StructuredPostal.CITY, a.city)
                    put(StructuredPostal.REGION, a.region)
                    put(StructuredPostal.POSTCODE, a.postcode)
                    put(StructuredPostal.COUNTRY, a.country)
                    put(StructuredPostal.TYPE, a.type)
                    put(StructuredPostal.LABEL, a.label)
                },
            )
        }
        if (c.organization.isNotBlank() || c.title.isNotBlank() || c.department.isNotBlank()) {
            insert(
                raw,
                Organization.CONTENT_ITEM_TYPE,
                ContentValues().apply {
                    put(Organization.COMPANY, c.organization)
                    put(Organization.DEPARTMENT, c.department)
                    put(Organization.TITLE, c.title)
                },
            )
        }
        if (c.note.isNotBlank()) insert(raw, Note.CONTENT_ITEM_TYPE, ContentValues().apply { put(Note.NOTE, c.note) })
        c.websites.forEach { insert(raw, Website.CONTENT_ITEM_TYPE, ContentValues().apply { put(Website.URL, it) }) }
        c.birthday?.let { insert(raw, Event.CONTENT_ITEM_TYPE, event(it, Event.TYPE_BIRTHDAY)) }
        c.anniversary?.let { insert(raw, Event.CONTENT_ITEM_TYPE, event(it, Event.TYPE_ANNIVERSARY)) }
        c.relations.forEach { insert(raw, Relation.CONTENT_ITEM_TYPE, typed(Relation.NAME, it)) }
        c.sip.forEach { insert(raw, SIP_MIME, ContentValues().apply { put(Data.DATA1, it) }) }
    }

    private fun typed(
        column: String,
        t: Typed,
    ) = ContentValues().apply {
        put(column, t.value)
        put(Data.DATA2, t.type)
        put(Data.DATA3, t.label)
    }

    private fun event(
        date: String,
        type: Int,
    ) = ContentValues().apply {
        put(Event.START_DATE, date)
        put(Event.TYPE, type)
    }

    /** The contact's data rows as a [CardContact] (groups as categories, for that method). */
    private fun readContact(
        raw: Long,
        uid: String,
    ): CardContact {
        var c = CardContact(uid)
        val phones = mutableListOf<Typed>()
        val emails = mutableListOf<Typed>()
        val addresses = mutableListOf<PostalAddress>()
        val websites = mutableListOf<String>()
        val relations = mutableListOf<Typed>()
        val sip = mutableListOf<String>()
        val groupIds = mutableListOf<Long>()
        resolver
            .query(
                dataUri,
                arrayOf(
                    Data.MIMETYPE,
                    Data.DATA1,
                    Data.DATA2,
                    Data.DATA3,
                    Data.DATA4,
                    Data.DATA5,
                    Data.DATA6,
                    Data.DATA7,
                    Data.DATA8,
                    Data.DATA9,
                    Data.DATA10,
                ),
                "${Data.RAW_CONTACT_ID} = ?",
                arrayOf(raw.toString()),
                null,
            )?.use { r ->
                fun s(i: Int) = r.getString(i).orEmpty()

                fun typed() = Typed(s(1), r.getInt(2), r.getString(3)?.ifBlank { null })
                while (r.moveToNext()) {
                    when (r.getString(0)) {
                        StructuredName.CONTENT_ITEM_TYPE -> {
                            // DATA1 display, 2 given, 3 family, 4 prefix, 5 middle, 6 suffix.
                            c = c.copy(formattedName = s(1), given = s(2), family = s(3), prefix = s(4), middle = s(5), suffix = s(6))
                        }

                        Nickname.CONTENT_ITEM_TYPE -> {
                            c = c.copy(nickname = s(1))
                        }

                        Phone.CONTENT_ITEM_TYPE -> {
                            phones += typed()
                        }

                        Email.CONTENT_ITEM_TYPE -> {
                            emails += typed()
                        }

                        StructuredPostal.CONTENT_ITEM_TYPE -> {
                            // DATA4 street, 5 pobox, 7 city, 8 region, 9 postcode, 10 country.
                            addresses += PostalAddress(s(4), s(5), s(7), s(8), s(9), s(10), r.getInt(2), r.getString(3)?.ifBlank { null })
                        }

                        Organization.CONTENT_ITEM_TYPE -> {
                            // DATA1 company, 4 title, 5 department.
                            c = c.copy(organization = s(1), title = s(4), department = s(5))
                        }

                        Note.CONTENT_ITEM_TYPE -> {
                            c = c.copy(note = s(1))
                        }

                        Website.CONTENT_ITEM_TYPE -> {
                            websites += s(1)
                        }

                        Event.CONTENT_ITEM_TYPE -> {
                            when (r.getInt(2)) {
                                Event.TYPE_BIRTHDAY -> c = c.copy(birthday = s(1))
                                Event.TYPE_ANNIVERSARY -> c = c.copy(anniversary = s(1))
                            }
                        }

                        Relation.CONTENT_ITEM_TYPE -> {
                            relations += typed()
                        }

                        SIP_MIME -> {
                            sip += s(1)
                        }

                        GroupMembership.CONTENT_ITEM_TYPE -> {
                            r.getString(1)?.toLongOrNull()?.let { groupIds += it }
                        }
                    }
                }
            }
        val categories =
            if (method == GroupMethod.Categories) {
                val titles = groups().associate { it.id to it.title }
                groupIds.mapNotNull { titles[it] }
            } else {
                emptyList()
            }
        return c.copy(
            phones = phones,
            emails = emails,
            addresses = addresses,
            websites = websites,
            relations = relations,
            sip = sip,
            categories = categories,
            formattedName = c.formattedName.ifBlank { CardMapping.displayName(c) },
        )
    }

    // ---- groups ----

    private fun members(groupId: Long): List<Long> {
        val out = mutableListOf<Long>()
        resolver
            .query(
                dataUri,
                arrayOf(Data.RAW_CONTACT_ID),
                "${Data.MIMETYPE} = ? AND ${GroupMembership.GROUP_ROW_ID} = ?",
                arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()),
                null,
            )?.use { c -> while (c.moveToNext()) out += c.getLong(0) }
        return out
    }

    private fun setMembers(
        groupId: Long,
        rawIds: Set<Long>,
    ) {
        val now = members(groupId).toSet()
        (now - rawIds).forEach { raw ->
            resolver.delete(
                dataUri,
                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${GroupMembership.GROUP_ROW_ID} = ?",
                arrayOf(raw.toString(), GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()),
            )
        }
        (rawIds - now).forEach { raw ->
            insert(raw, GroupMembership.CONTENT_ITEM_TYPE, ContentValues().apply { put(GroupMembership.GROUP_ROW_ID, groupId) })
        }
    }

    /** Categories method: the contact is in exactly the groups named, made here as needed. */
    private fun setCategories(
        raw: Long,
        titles: List<String>,
    ) {
        resolver.delete(
            dataUri,
            "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
            arrayOf(raw.toString(), GroupMembership.CONTENT_ITEM_TYPE),
        )
        val existing = groups().filter { !it.deleted }.associateBy { it.title }
        for (title in titles) {
            val id =
                existing[title]?.id ?: ContentUris.parseId(
                    resolver.insert(
                        groupsUri,
                        ContentValues().apply {
                            put(Groups.ACCOUNT_NAME, account.name)
                            put(Groups.ACCOUNT_TYPE, account.type)
                            put(Groups.TITLE, title)
                            put(Groups.GROUP_VISIBLE, 1)
                        },
                    ) ?: continue,
                )
            insert(raw, GroupMembership.CONTENT_ITEM_TYPE, ContentValues().apply { put(GroupMembership.GROUP_ROW_ID, id) })
        }
    }

    // ---- photos ----

    private fun displayPhotoUri(raw: Long): Uri =
        ContactsAccounts.asSyncAdapter(
            Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI, raw), RawContacts.DisplayPhoto.CONTENT_DIRECTORY),
            account,
        )

    private fun writePhoto(
        raw: Long,
        bytes: ByteArray,
    ) {
        runCatching {
            resolver.openAssetFileDescriptor(displayPhotoUri(raw), "rw")?.use { fd -> fd.createOutputStream().use { it.write(bytes) } }
        }
    }

    private fun displayPhoto(raw: Long): ByteArray? =
        runCatching {
            resolver.openAssetFileDescriptor(displayPhotoUri(raw), "r")?.use { fd -> fd.createInputStream().use { it.readBytes() } }
        }.getOrNull()

    /** The small photo the provider keeps in the photo row (changes whenever the photo does). */
    private fun thumbnail(raw: Long): ByteArray? =
        resolver
            .query(
                dataUri,
                arrayOf(Photo.PHOTO),
                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                arrayOf(raw.toString(), Photo.CONTENT_ITEM_TYPE),
                null,
            )?.use { c -> if (c.moveToFirst()) c.getBlob(0) else null }

    // ---- helpers ----

    private fun setRaw(
        id: Long,
        values: ContentValues,
    ) = resolver.update(ContentUris.withAppendedId(rawUri, id), values, null, null)

    private fun resource(name: String) = Dav.resolve(if (href.endsWith("/")) href else "$href/", name)

    private fun nameOf(h: String) = URI(h).rawPath.trimEnd('/').substringAfterLast('/')

    private fun file(name: String) = File(store, hash(name.toByteArray()).take(32) + ".vcf")

    private fun saved(name: String): Component? =
        file(name)
            .takeIf {
                it.exists()
            }?.let { runCatching { CardMapping.parse(it.readText()) }.getOrNull() }

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        // SipAddress is deprecated in API 37; its rows are still stored and read as this type.
        const val SIP_MIME = "vnd.android.cursor.item/sip_address"
    }
}
