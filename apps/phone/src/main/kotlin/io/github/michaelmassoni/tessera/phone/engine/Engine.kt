package io.github.michaelmassoni.tessera.phone.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object Dialpad {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

    private val letters =
        mapOf(
            "2" to "ABC",
            "3" to "DEF",
            "4" to "GHI",
            "5" to "JKL",
            "6" to "MNO",
            "7" to "PQRS",
            "8" to "TUV",
            "9" to "WXYZ",
            "0" to "+",
        )

    fun letters(key: String): String = letters[key].orEmpty()

    /** What a long press types: + for 0, otherwise the key itself. */
    fun longPress(key: String): String = if (key == "0") "+" else key

    fun delete(number: String): String = number.dropLast(1)

    /**
     * [number] with pasted [text] added: only what can be dialled (digits, * and #, and a + at
     * the very start) is kept, so "Ph: (03) 9876-5432" pastes as 0398765432.
     */
    fun paste(
        number: String,
        text: String,
    ): String {
        val kept =
            text.trim().filterIndexed { i, c ->
                c.isDigit() || c == '*' || c == '#' ||
                    (c == '+' && i == text.trim().indexOfFirst { it == '+' || it.isDigit() })
            }
        val dialable = if (number.isEmpty()) kept else kept.replace("+", "")
        return (number + dialable).take(MAX_DIGITS)
    }

    private const val MAX_DIGITS = 40
}

/** Digits only, for matching however numbers are formatted. */
fun digits(text: String): String = text.filter { it.isDigit() }

object NumberMatch {
    /** True if [typed] (at least 2 digits) appears in [number], comparing digits only. */
    fun matches(
        typed: String,
        number: String,
    ): Boolean {
        val t = digits(typed)
        return t.length >= 2 && digits(number).contains(t)
    }
}

enum class CallType { Incoming, Outgoing, Missed, Rejected, Blocked, Voicemail, Other }

/** One row of the call log. Times are epoch ms. */
data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val type: CallType,
    val date: Long,
    val durationSeconds: Long,
)

/** Consecutive calls from the same number and of the same kind, shown as one row. */
data class CallGroup(
    val latest: CallEntry,
    val count: Int,
    /** Every call in the group, newest first. */
    val ids: List<Long> = listOf(latest.id),
)

object CallLogGrouping {
    /**
     * Day sections (newest first). Within a day, consecutive calls with the same number and the
     * same missed/not-missed kind collapse into one group, like the system dialer.
     */
    fun sections(
        calls: List<CallEntry>,
        zone: ZoneId,
    ): List<Pair<LocalDate, List<CallGroup>>> =
        calls
            .sortedByDescending { it.date }
            .groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
            .map { (day, list) ->
                val groups = mutableListOf<CallGroup>()
                for (call in list) {
                    val last = groups.lastOrNull()
                    if (last != null && digits(last.latest.number) == digits(call.number) &&
                        (last.latest.type == CallType.Missed) == (call.type == CallType.Missed)
                    ) {
                        groups[groups.lastIndex] = last.copy(count = last.count + 1, ids = last.ids + call.id)
                    } else {
                        groups += CallGroup(call, 1)
                    }
                }
                day to groups
            }
}

/** "0:42", "1:02:03" for call timers. */
fun formatTimer(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

enum class DurationUnit { Hours, Minutes, Seconds }

/**
 * A finished call's length as the recents list shows it: hours and minutes, minutes and seconds,
 * or seconds, leaving out a zero part ("3 min", not "3 min, 0 sec"). Empty for an unanswered call.
 */
fun callDuration(seconds: Long): List<Pair<Long, DurationUnit>> {
    if (seconds <= 0) return emptyList()
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    val parts =
        if (h > 0) {
            listOf(h to DurationUnit.Hours, m to DurationUnit.Minutes)
        } else {
            listOf(m to DurationUnit.Minutes, s to DurationUnit.Seconds)
        }
    return parts.filter { it.first > 0 }
}
