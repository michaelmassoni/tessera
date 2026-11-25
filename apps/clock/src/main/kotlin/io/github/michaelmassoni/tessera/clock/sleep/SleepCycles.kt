package io.github.michaelmassoni.tessera.clock.sleep

import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * One way to spend the night: in bed at [bedtime], asleep [fallAsleepMinutes] later, and up at
 * [wake] after [cycles] full sleep cycles.
 */
data class SleepPlan(
    val bedtime: ZonedDateTime,
    val wake: ZonedDateTime,
    val cycles: Int,
    val sleepMinutes: Int,
) {
    /** 5 or 6 cycles: 7.5 to 9 hours with 90-minute cycles, what adults are advised to get. */
    val recommended: Boolean get() = cycles >= RECOMMENDED_CYCLES

    companion object {
        const val RECOMMENDED_CYCLES = 5
    }
}

/**
 * The sleep calculator (docs/clock.md, "Sleep calculator"). Pure, so it is unit tested. Times are
 * instants in a zone: adding hours across a daylight-saving change gives the real amount of sleep.
 */
object SleepCycles {
    /** How many cycles are suggested: 4.5 to 9 hours with 90-minute cycles. */
    val CYCLES = 3..6

    /** Wake-up times for going to bed at [bedtime], earliest first. */
    fun wakeTimes(
        bedtime: ZonedDateTime,
        cycleMinutes: Int,
        fallAsleepMinutes: Int,
    ): List<SleepPlan> {
        val bed = bedtime.truncatedTo(ChronoUnit.MINUTES)
        return CYCLES.map { n ->
            SleepPlan(bed, bed.plusMinutes(fallAsleepMinutes + n.toLong() * cycleMinutes), n, n * cycleMinutes)
        }
    }

    /**
     * Bedtimes for waking at [wake], earliest (most sleep) first. Those before [now] are left out:
     * it's too late for them.
     */
    fun bedtimes(
        wake: ZonedDateTime,
        now: ZonedDateTime,
        cycleMinutes: Int,
        fallAsleepMinutes: Int,
    ): List<SleepPlan> {
        val up = wake.truncatedTo(ChronoUnit.MINUTES)
        val earliest = now.truncatedTo(ChronoUnit.MINUTES)
        return CYCLES.reversed().mapNotNull { n ->
            val bed = up.minusMinutes(fallAsleepMinutes + n.toLong() * cycleMinutes)
            if (bed.isBefore(earliest)) null else SleepPlan(bed, up, n, n * cycleMinutes)
        }
    }

    /** The next time the clock shows [time] after [now] (today if still to come, else tomorrow). */
    fun next(
        time: LocalTime,
        now: ZonedDateTime,
    ): ZonedDateTime {
        val today = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
        return if (today.isAfter(now)) today else ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
    }

    /**
     * Whether a one-off alarm at [wake]'s time would ring at [wake]: an alarm rings at the next
     * occurrence of its time, so a wake-up time more than a day away can't be one.
     */
    fun canBeAlarm(
        wake: ZonedDateTime,
        now: ZonedDateTime,
    ): Boolean = next(wake.toLocalTime().truncatedTo(ChronoUnit.MINUTES), now).toInstant() == wake.toInstant()
}
