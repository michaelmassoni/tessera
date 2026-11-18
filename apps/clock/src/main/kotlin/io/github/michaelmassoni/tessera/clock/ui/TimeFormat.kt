package io.github.michaelmassoni.tessera.clock.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats times the way the system does: 12/24-hour setting and locale. */
class ClockFormatter(
    context: Context,
    locale: Locale,
) {
    private val is24 = DateFormat.is24HourFormat(context)
    private val time =
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hm"),
            locale,
        )
    private val hourMinuteOnly =
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hm").replace("a", "").trim(),
            locale,
        )
    private val amPm = DateTimeFormatter.ofPattern("a", locale)
    private val date = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)

    val uses24Hour: Boolean get() = is24

    fun time(t: LocalTime): String = time.format(t)

    fun time(t: ZonedDateTime): String = time.format(t)

    /** "7:30" without the AM/PM marker, for the big alarm time. */
    fun digits(t: LocalTime): String = hourMinuteOnly.format(t)

    fun amPm(t: LocalTime): String? = if (is24) null else amPm.format(t)

    fun date(t: ZonedDateTime): String = date.format(t)
}

@Composable
fun rememberClockFormatter(): ClockFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(context, locale) { ClockFormatter(context, locale) }
}

@Composable
fun formatClockTime(
    hour: Int,
    minute: Int,
): String = rememberClockFormatter().time(LocalTime.of(hour, minute))

/** A value that updates every [periodMillis] while the caller is composed (i.e. visible). */
@Composable
fun rememberTicker(
    periodMillis: Long,
    read: () -> Long,
): State<Long> {
    val state = remember { mutableLongStateOf(read()) }
    LaunchedEffect(periodMillis) {
        while (true) {
            state.longValue = read()
            // Align to the period so minute-based clocks flip exactly on the minute.
            delay(periodMillis - (System.currentTimeMillis() % periodMillis).coerceAtMost(periodMillis - 1))
        }
    }
    return state
}
