package io.github.michaelmassoni.tessera.phone.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.phone.data.ContactNumber
import io.github.michaelmassoni.tessera.phone.engine.CallEntry
import io.github.michaelmassoni.tessera.phone.engine.CallGroup
import io.github.michaelmassoni.tessera.phone.engine.CallType
import java.time.LocalDate
import java.time.ZoneOffset

private object NoPhoneActions : PhoneActions {
    override fun call(number: String) = Unit

    override fun copy(number: String) = Unit

    override fun message(number: String) = Unit

    override fun createContact(number: String) = Unit

    override fun addToContact(number: String) = Unit

    override fun viewContact(contact: ContactNumber) = Unit

    override fun makeDefault() = Unit

    override fun grant() = Unit

    override fun openSettings() = Unit
}

/** Fixed past dates, so the screenshots don't change with today's date. */
private fun entry(
    id: Long,
    number: String,
    name: String?,
    type: CallType,
    day: LocalDate,
    hour: Int,
    seconds: Long = if (type == CallType.Missed || type == CallType.Rejected) 0 else 200,
) = CallEntry(id, number, name, type, day.atTime(hour, 15).toInstant(ZoneOffset.UTC).toEpochMilli(), seconds)

private val day1 = LocalDate.of(2024, 3, 2)
private val day2 = LocalDate.of(2024, 3, 1)

private val recents =
    listOf(
        day1 to
            listOf(
                CallGroup(entry(1, "+61412345678", "Ada Lovelace", CallType.Incoming, day1, 18), 2),
                CallGroup(entry(2, "0455000111", null, CallType.Missed, day1, 14), 1),
                CallGroup(entry(3, "+61400111222", "Grace Hopper", CallType.Outgoing, day1, 9, seconds = 3723), 1),
            ),
        day2 to
            listOf(
                CallGroup(entry(4, "131 450", null, CallType.Incoming, day2, 16, seconds = 42), 1),
                CallGroup(entry(5, "+61412345678", "Ada Lovelace", CallType.Rejected, day2, 11), 1),
            ),
    )

// Recents in both themes (TesseraPreviews includes dark), with named and unknown callers.
@TesseraPreviews
@Composable
private fun RecentsPreview() {
    TesseraTheme(dynamicColor = false) {
        PhoneApp(PhoneState(loaded = true, recents = recents, isDefault = true), NoPhoneActions)
    }
}

// The long-press options for a number that isn't a contact, in both themes.
@TesseraPreviews
@Composable
private fun RecentOptionsPreview() {
    TesseraTheme(dynamicColor = false) {
        Surface {
            RecentOptions(recents[0].second[1].latest, contact = null, actions = NoPhoneActions, onEdit = {}, done = {})
        }
    }
}

private val numbers =
    listOf(
        ContactNumber(1, "ada", "Ada Lovelace", "+61 412 345 678", "2|", starred = true, photoUri = null),
        ContactNumber(2, "bob", "Bob Brown", "02 9999 0000", "1|", starred = false, photoUri = null),
        ContactNumber(2, "bob", "Bob Brown", "0400 111 222", "2|", starred = false, photoUri = null),
        ContactNumber(3, "grace", "Grace Hopper", "+61 400 111 222", "3|", starred = false, photoUri = null),
        ContactNumber(4, "emile", "Émile Zola", "0455 000 111", "2|", starred = false, photoUri = null),
    )

// The Contacts tab: search bar, letter headers and the create button, in both themes.
@TesseraPreviews
@Composable
private fun ContactsPreview() {
    TesseraTheme(dynamicColor = false) {
        PhoneApp(PhoneState(loaded = true, numbers = numbers, isDefault = true), NoPhoneActions, initialTab = 2)
    }
}
