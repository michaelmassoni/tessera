package io.github.michaelmassoni.tessera.contacts.engine

/** The account a raw contact is stored in. Both null means the device ("Phone") account. */
data class AccountRef(
    val name: String?,
    val type: String?,
) {
    val isDevice: Boolean get() = name == null && type == null

    companion object {
        val Device = AccountRef(null, null)
    }
}

/** One row of the contact list: enough to show, sort and search it. */
data class ContactSummary(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val starred: Boolean = false,
    val photoUri: String? = null,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val accounts: Set<AccountRef> = emptySet(),
)

/** A typed value such as a phone number. [type] is the ContactsContract TYPE constant; [label] is used when it is TYPE_CUSTOM. */
data class Labeled(
    val value: String,
    val type: Int,
    val label: String? = null,
    /** The data row's id, or null for a value added in the editor. */
    val id: Long? = null,
)

/**
 * The editable fields of a contact. Single-valued fields carry the id of the row they came
 * from, so an edit updates that row instead of adding another.
 */
data class ContactForm(
    val givenName: String = "",
    val familyName: String = "",
    val nameId: Long? = null,
    val phones: List<Labeled> = emptyList(),
    val emails: List<Labeled> = emptyList(),
    val addresses: List<Labeled> = emptyList(),
    val company: String = "",
    val companyId: Long? = null,
    /** ISO date ("1990-04-23") or "--04-23" without a year, as stored by ContactsContract. */
    val birthday: String = "",
    val birthdayId: Long? = null,
    val note: String = "",
    val noteId: Long? = null,
) {
    val isEmpty: Boolean
        get() =
            givenName.isBlank() && familyName.isBlank() && company.isBlank() &&
                phones.all { it.value.isBlank() } && emails.all { it.value.isBlank() }
}

/** Everything the detail screen shows. */
data class ContactDetail(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val starred: Boolean,
    val photoUri: String?,
    val phones: List<Labeled>,
    val emails: List<Labeled>,
    val addresses: List<Labeled>,
    val events: List<Labeled>,
    val websites: List<String>,
    val company: String,
    val title: String,
    val note: String,
    val accounts: Set<AccountRef>,
    /** The raw contact edits go to (the first writable one). */
    val rawContactId: Long,
    val form: ContactForm,
)
