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
