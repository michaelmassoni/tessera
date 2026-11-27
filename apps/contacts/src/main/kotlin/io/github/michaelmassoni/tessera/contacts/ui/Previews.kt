package io.github.michaelmassoni.tessera.contacts.ui

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.contacts.EditorState
import io.github.michaelmassoni.tessera.contacts.ListState
import io.github.michaelmassoni.tessera.contacts.engine.AccountRef
import io.github.michaelmassoni.tessera.contacts.engine.ContactDetail
import io.github.michaelmassoni.tessera.contacts.engine.ContactForm
import io.github.michaelmassoni.tessera.contacts.engine.ContactIndex
import io.github.michaelmassoni.tessera.contacts.engine.ContactQr
import io.github.michaelmassoni.tessera.contacts.engine.ContactSummary
import io.github.michaelmassoni.tessera.contacts.engine.Labeled
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import kotlinx.coroutines.flow.emptyFlow
import java.util.Locale

private val people =
    listOf(
        "Ada Lovelace" to true,
        "Alan Turing" to false,
        "Grace Hopper" to true,
        "Hedy Lamarr" to false,
        "Katherine Johnson" to false,
        "Linus Torvalds" to false,
        "Margaret Hamilton" to false,
        "Radia Perlman" to false,
    ).mapIndexed { i, (name, starred) -> ContactSummary(i.toLong(), "key$i", name, starred) }

private val sampleList = ListState(loaded = true, total = people.size, sections = ContactIndex.sections(people, Locale.ENGLISH))

private val sampleForm =
    ContactForm(
        givenName = "Grace",
        familyName = "Hopper",
        nameId = 1,
        phones = listOf(Labeled("+1 202 555 0143", Phone.TYPE_MOBILE, id = 2), Labeled("+1 202 555 0199", Phone.TYPE_WORK, id = 3)),
        emails = listOf(Labeled("grace@example.org", Email.TYPE_WORK, id = 4)),
        company = "US Navy",
        companyId = 5,
        birthday = "1906-12-09",
        birthdayId = 6,
    )

private val sampleDetail =
    ContactDetail(
        id = 2,
        lookupKey = "key2",
        name = "Grace Hopper",
        starred = true,
        photoUri = null,
        phones = sampleForm.phones,
        emails = sampleForm.emails,
        addresses = listOf(Labeled("1 Navy Way\nArlington VA", StructuredPostal.TYPE_WORK, id = 7)),
        events = listOf(Labeled("1906-12-09", Event.TYPE_BIRTHDAY, id = 6)),
        websites = emptyList(),
        company = "US Navy",
        title = "Rear Admiral",
        note = "Find the bug in the relay.",
        accounts = setOf(AccountRef.Device),
        rawContactId = 1,
        form = sampleForm,
    )

private object NoListActions : ListActions {
    override fun setSearch(text: String) = Unit

    override fun open(contact: ContactSummary) = Unit

    override fun create() = Unit

    override fun setAccountFilter(account: AccountRef?) = Unit

    override fun import() = Unit

    override fun export() = Unit

    override fun openSettings() = Unit
}

private object NoDetailActions : DetailActions {
    override fun back() = Unit

    override fun call(number: String) = Unit

    override fun message(number: String) = Unit

    override fun email(address: String) = Unit

    override fun map(address: String) = Unit

    override fun web(url: String) = Unit

    override fun toggleStarred() = Unit

    override fun edit() = Unit

    override fun share(detail: ContactDetail) = Unit

    override fun delete() = Unit
}

private object NoEditActions : EditActions {
    override fun change(form: ContactForm) = Unit

    override fun account(account: AccountRef) = Unit

    override fun save() = Unit

    override fun cancel() = Unit
}

@TesseraPreviews
@Composable
private fun ContactListPreview() {
    TesseraTheme(dynamicColor = false) {
        ContactsApp(sampleList, null, null, null, emptyFlow(), NoListActions, NoDetailActions, NoEditActions)
    }
}

@TesseraPreviews
@Composable
private fun ContactDetailPreview() {
    TesseraTheme(dynamicColor = false) {
        ContactsApp(sampleList, 2, sampleDetail, null, emptyFlow(), NoListActions, NoDetailActions, NoEditActions)
    }
}

@TesseraPreviews
@Composable
private fun ContactEditorPreview() {
    TesseraTheme(dynamicColor = false) {
        val editor =
            EditorState(
                null,
                null,
                ContactForm(),
                sampleForm.copy(nameId = null),
                AccountRef.Device,
                listOf(AccountRef.Device, AccountRef("me@example.org", "x")),
            )
        ContactsApp(sampleList, null, null, editor, emptyFlow(), NoListActions, NoDetailActions, NoEditActions)
    }
}

@TesseraPreviews
@Composable
private fun PermissionPreview() {
    TesseraTheme(dynamicColor = false) {
        PermissionScreen(denied = false, onGrant = {}, onSettings = {})
    }
}

@TesseraPreviews
@Composable
private fun QrCodePreview() {
    TesseraTheme(dynamicColor = false) {
        QrCodeDialog(sampleDetail.name, ContactQr.vcard(sampleDetail), onDismiss = {})
    }
}
