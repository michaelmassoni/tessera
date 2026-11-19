package io.github.michaelmassoni.tessera.phone.ui

import androidx.compose.runtime.Composable
import io.github.michaelmassoni.tessera.core.design.TesseraPreviews
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import io.github.michaelmassoni.tessera.phone.engine.CallEntry
import io.github.michaelmassoni.tessera.phone.engine.CallGroup
import io.github.michaelmassoni.tessera.phone.engine.CallType
import java.time.LocalDate
import java.time.ZoneOffset

private object NoPhoneActions : PhoneActions {
    override fun call(number: String) = Unit

    override fun copy(number: String) = Unit

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
) = CallEntry(id, number, name, type, day.atTime(hour, 15).toInstant(ZoneOffset.UTC).toEpochMilli(), 120)

private val day1 = LocalDate.of(2024, 3, 2)
private val day2 = LocalDate.of(2024, 3, 1)

private val recents =
    listOf(
        day1 to
            listOf(
                CallGroup(entry(1, "+61412345678", "Ada Lovelace", CallType.Incoming, day1, 18), 2),
                CallGroup(entry(2, "0455000111", null, CallType.Missed, day1, 14), 1),
                CallGroup(entry(3, "+61400111222", "Grace Hopper", CallType.Outgoing, day1, 9), 1),
            ),
        day2 to
            listOf(
                CallGroup(entry(4, "131 450", null, CallType.Incoming, day2, 16), 1),
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
