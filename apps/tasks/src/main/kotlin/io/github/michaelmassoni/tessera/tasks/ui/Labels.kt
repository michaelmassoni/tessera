package io.github.michaelmassoni.tessera.tasks.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.michaelmassoni.tessera.tasks.R
import io.github.michaelmassoni.tessera.tasks.engine.Due
import io.github.michaelmassoni.tessera.tasks.engine.Repeat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** "Today", "Tomorrow", "Wed", "24 Nov", "24 Nov 2027", with the time after a comma. */
@Composable
fun dueLabel(
    due: Due,
    today: LocalDate,
): String {
    val locale = LocalConfiguration.current.locales[0]
    val date = due.localDate
    val day =
        when {
            date == today -> stringResource(R.string.today)
            date == today.plusDays(1) -> stringResource(R.string.tomorrow)
            date == today.minusDays(1) -> stringResource(R.string.yesterday)
            date.isAfter(today) && date.isBefore(today.plusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM", locale))
            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
    val time = due.localTime ?: return day
    return day + ", " + time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

/** "Daily", "Every 3 days", "Weekly on Mon, Thu", "Every 8 hours after done". */
@Composable
fun repeatLabel(repeat: Repeat): String {
    val locale = LocalConfiguration.current.locales[0]
    val n = repeat.interval.coerceAtLeast(1)
    val base =
        when (repeat.frequency) {
            Repeat.Frequency.Hourly -> pluralStringResource(R.plurals.every_hours, n, n)
            Repeat.Frequency.Daily -> pluralStringResource(R.plurals.every_days, n, n)
            Repeat.Frequency.Weekly -> pluralStringResource(R.plurals.every_weeks, n, n)
            Repeat.Frequency.Monthly -> pluralStringResource(R.plurals.every_months, n, n)
            Repeat.Frequency.Yearly -> pluralStringResource(R.plurals.every_years, n, n)
        }
    val days =
        if (repeat.frequency == Repeat.Frequency.Weekly && repeat.weekdays.isNotEmpty()) {
            " " +
                stringResource(
                    R.string.repeat_on,
                    repeat.weekdays.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale) },
                )
        } else {
            ""
        }
    val after = if (repeat.fromCompletion) " " + stringResource(R.string.repeat_after_done) else ""
    return base + days + after
}
