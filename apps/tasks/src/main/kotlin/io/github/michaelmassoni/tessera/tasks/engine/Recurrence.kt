package io.github.michaelmassoni.tessera.tasks.engine

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Repeats: the next due date, and reading and writing RRULEs. Pure, so it's unit tested. */
object Recurrence {
    /**
     * The due date after [due] for [repeat]: counted from the due date, or from [completedAt] for
     * "after done" repeats. It's always after the completion (timed tasks) or after that day (all-day
     * tasks), so a repeat missed for days doesn't leave a string of overdue copies, and ticking one
     * early moves it to its next date as Google Tasks does. Null when the repeat has ended.
     */
    fun next(
        due: Due,
        repeat: Repeat,
        completedAt: Long,
        zone: ZoneId,
    ): Due? {
        val done = LocalDateTime.ofInstant(Instant.ofEpochMilli(completedAt), zone).withSecond(0).withNano(0)
        val time = due.localTime
        val base =
            when {
                !repeat.fromCompletion -> due.dateTime
                repeat.frequency == Repeat.Frequency.Hourly -> done
                time != null -> done.toLocalDate().atTime(time)
                else -> done.toLocalDate().atStartOfDay()
            }
        var next = step(base, repeat)
        while (if (time != null) !next.isAfter(done) else !next.toLocalDate().isAfter(done.toLocalDate())) {
            next = step(next, repeat)
        }
        if (repeat.until != null && next.toLocalDate().isAfter(LocalDate.parse(repeat.until))) return null
        return Due.of(next.toLocalDate(), if (time != null || repeat.frequency == Repeat.Frequency.Hourly) next.toLocalTime() else null)
    }

    private fun step(
        from: LocalDateTime,
        repeat: Repeat,
    ): LocalDateTime {
        val n = repeat.interval.coerceAtLeast(1).toLong()
        return when (repeat.frequency) {
            Repeat.Frequency.Hourly -> {
                from.plusHours(n)
            }

            Repeat.Frequency.Daily -> {
                from.plusDays(n)
            }

            Repeat.Frequency.Monthly -> {
                from.plusMonths(n)
            }

            Repeat.Frequency.Yearly -> {
                from.plusYears(n)
            }

            Repeat.Frequency.Weekly -> {
                val days = repeat.weekdays.sorted()
                if (days.isEmpty()) {
                    from.plusWeeks(n)
                } else {
                    // The next chosen weekday this week, else the first one n weeks on.
                    val today = from.dayOfWeek.value
                    val later = days.firstOrNull { it > today }
                    if (later != null) {
                        from.plusDays((later - today).toLong())
                    } else {
                        val weekStart = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(n)
                        weekStart.plusDays((days.first() - 1).toLong())
                    }
                }
            }
        }
    }

    private val dayCodes = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

    /** An RRULE value: FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH. */
    fun format(repeat: Repeat): String =
        buildList {
            add("FREQ=${repeat.frequency.name.uppercase()}")
            if (repeat.interval > 1) add("INTERVAL=${repeat.interval}")
            if (repeat.frequency == Repeat.Frequency.Weekly && repeat.weekdays.isNotEmpty()) {
                add("BYDAY=" + repeat.weekdays.sorted().joinToString(",") { dayCodes[it - 1] })
            }
            repeat.until?.let { add("UNTIL=" + it.replace("-", "")) }
        }.joinToString(";")

    /**
     * Reads an RRULE value. Parts Tessera doesn't model (BYMONTHDAY, COUNT …) are left out of the
     * [Repeat]; the server's RRULE is kept as written until the repeat is edited here.
     */
    fun parse(
        rule: String,
        fromCompletion: Boolean = false,
    ): Repeat? {
        val parts =
            rule
                .split(';')
                .mapNotNull { p ->
                    p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] }
                }.toMap()
        val frequency =
            when (parts["FREQ"]?.uppercase()) {
                "HOURLY" -> Repeat.Frequency.Hourly
                "DAILY" -> Repeat.Frequency.Daily
                "WEEKLY" -> Repeat.Frequency.Weekly
                "MONTHLY" -> Repeat.Frequency.Monthly
                "YEARLY" -> Repeat.Frequency.Yearly
                else -> return null
            }
        val days =
            parts["BYDAY"]
                ?.split(',')
                ?.mapNotNull { code -> dayCodes.indexOf(code.takeLast(2).uppercase()).takeIf { it >= 0 }?.plus(1) }
                ?.toSet()
                .orEmpty()
        val until =
            parts["UNTIL"]
                ?.take(
                    8,
                )?.takeIf { it.length == 8 }
                ?.let { "${it.substring(0, 4)}-${it.substring(4, 6)}-${it.substring(6, 8)}" }
        return Repeat(frequency, parts["INTERVAL"]?.toIntOrNull() ?: 1, days, fromCompletion, until)
    }
}
