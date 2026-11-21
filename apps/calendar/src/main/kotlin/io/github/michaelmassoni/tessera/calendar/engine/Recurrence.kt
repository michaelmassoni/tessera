package io.github.michaelmassoni.tessera.calendar.engine

/** The repeat choices the editor offers. */
enum class Repeat(
    val rrule: String?,
) {
    None(null),
    Daily("FREQ=DAILY"),
    Weekly("FREQ=WEEKLY"),
    Monthly("FREQ=MONTHLY"),
    Yearly("FREQ=YEARLY"),
    ;

    companion object {
        /** The choice matching [rrule], or null when it is something the editor can't show (it is then kept as is). */
        fun of(rrule: String?): Repeat? {
            if (rrule.isNullOrBlank()) return None
            val parts = parse(rrule)
            if (parts.keys != setOf("FREQ")) return null
            return entries.firstOrNull { it.rrule == "FREQ=${parts["FREQ"]}" }
        }
    }
}

/** Words for an RRULE, filled in by the UI from string resources. */
data class RecurrenceWords(
    val daily: String,
    val weekly: String,
    val monthly: String,
    val yearly: String,
    /** "Every %d days", with the interval. */
    val everyNDays: (Int) -> String,
    val everyNWeeks: (Int) -> String,
    val everyNMonths: (Int) -> String,
    val everyNYears: (Int) -> String,
    val custom: String,
)

fun parse(rrule: String): Map<String, String> =
    rrule
        .removePrefix("RRULE:")
        .split(';')
        .filter { it.contains('=') }
        .associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }

/** "Every week", "Every 2 days", or a generic "Repeats" for rules with BYDAY, COUNT etc. */
fun describe(
    rrule: String,
    words: RecurrenceWords,
): String {
    val p = parse(rrule)
    val interval = p["INTERVAL"]?.toIntOrNull() ?: 1
    if (p.keys.any { it !in setOf("FREQ", "INTERVAL", "WKST") }) return words.custom
    return when (p["FREQ"]) {
        "DAILY" -> if (interval == 1) words.daily else words.everyNDays(interval)
        "WEEKLY" -> if (interval == 1) words.weekly else words.everyNWeeks(interval)
        "MONTHLY" -> if (interval == 1) words.monthly else words.everyNMonths(interval)
        "YEARLY" -> if (interval == 1) words.yearly else words.everyNYears(interval)
        else -> words.custom
    }
}

/** Which events a change to one occurrence of a repeating event applies to. */
enum class Scope { This, Following, All }

/**
 * Splitting a repeating event at one occurrence, for "this and following events": the old series
 * ends just before it and a new series starts with it.
 */
object SeriesSplit {
    private val UTC_STAMP =
        java.time.format.DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(java.time.ZoneOffset.UTC)
    private val DATE = java.time.format.DateTimeFormatter.BASIC_ISO_DATE

    /**
     * The old series' rule, ending before the occurrence at [instanceBegin] (epoch ms): UNTIL is the
     * second before it (the day before, for all-day events), and COUNT is dropped.
     */
    fun endBefore(
        rrule: String,
        instanceBegin: Long,
        allDay: Boolean,
    ): String {
        val parts = LinkedHashMap(parse(rrule))
        parts.remove("COUNT")
        parts["UNTIL"] =
            if (allDay) {
                java.time.Instant
                    .ofEpochMilli(instanceBegin)
                    .atZone(java.time.ZoneOffset.UTC)
                    .toLocalDate()
                    .minusDays(1)
                    .format(DATE)
            } else {
                UTC_STAMP.format(java.time.Instant.ofEpochMilli(instanceBegin - 1000))
            }
        return format(parts)
    }

    /**
     * The new series' rule. A COUNT carries on with what's left after the [before] occurrences that
     * stay in the old series; an UNTIL stays as it was.
     */
    fun continueFrom(
        rrule: String,
        before: Int,
    ): String {
        val parts = LinkedHashMap(parse(rrule))
        parts["COUNT"]?.toIntOrNull()?.let { count -> parts["COUNT"] = (count - before).coerceAtLeast(1).toString() }
        return format(parts)
    }

    /**
     * [exdate] with the occurrence at [instanceBegin] added, as a UTC stamp ("20261125T030000Z"),
     * which is how the provider stores EXDATE and matches it against occurrences.
     */
    fun exclude(
        exdate: String?,
        instanceBegin: Long,
    ): String {
        val stamp = UTC_STAMP.format(java.time.Instant.ofEpochMilli(instanceBegin))
        return (
            exdate
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() } + stamp
        ).distinct().joinToString(",")
    }

    private fun format(parts: Map<String, String>) = parts.entries.joinToString(";") { "${it.key}=${it.value}" }
}
