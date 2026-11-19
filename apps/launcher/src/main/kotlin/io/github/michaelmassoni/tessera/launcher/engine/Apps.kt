package io.github.michaelmassoni.tessera.launcher.engine

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Identifies a launchable activity in a profile: "package/activity/userSerial". */
data class AppKey(
    val packageName: String,
    val activity: String,
    val userSerial: Long,
) {
    override fun toString() = "$packageName/$activity/$userSerial"

    companion object {
        fun parse(text: String): AppKey? {
            val parts = text.split('/')
            if (parts.size != 3) return null
            val serial = parts[2].toLongOrNull() ?: return null
            if (parts[0].isEmpty() || parts[1].isEmpty()) return null
            return AppKey(parts[0], parts[1], serial)
        }
    }
}

/** An app in the drawer. [work] marks a work-profile app. */
data class App(
    val key: AppKey,
    val label: String,
    val work: Boolean = false,
)

object AppIndex {
    private val DIACRITICS = Regex("\\p{Mn}+")

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase(Locale.ROOT)

    /** By label with [locale]'s collation; personal before work for the same label. */
    fun sorted(
        apps: List<App>,
        locale: Locale,
    ): List<App> {
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        return apps.sortedWith { a, b -> collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.work.compareTo(b.work) }
    }

    /**
     * Apps whose label has a word starting with [query] (ignoring case and accents), best first:
     * label starts with the query, then a later word does, then the query appears anywhere.
     */
    fun search(
        apps: List<App>,
        query: String,
    ): List<App> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return apps

        fun rank(app: App): Int {
            val label = normalize(app.label)
            return when {
                label.startsWith(q) -> 0
                label.split(Regex("[\\s\\-_.]+")).any { it.startsWith(q) } -> 1
                label.contains(q) -> 2
                else -> -1
            }
        }
        return apps
            .map { it to rank(it) }
            .filter { it.second >= 0 }
            .sortedBy { it.second }
            .map { it.first }
    }
}

/** The pinned apps on the home screen, in order. */
class Pins(
    keys: List<AppKey> = emptyList(),
) {
    val keys: List<AppKey> = keys.distinct().take(MAX)

    fun contains(key: AppKey) = key in keys

    fun add(key: AppKey): Pins = if (key in keys || keys.size >= MAX) this else Pins(keys + key)

    fun remove(key: AppKey): Pins = Pins(keys - key)

    /** Moves [key] by [delta] places, clamped to the ends. */
    fun move(
        key: AppKey,
        delta: Int,
    ): Pins {
        val from = keys.indexOf(key)
        if (from < 0) return this
        val to = (from + delta).coerceIn(0, keys.lastIndex)
        return Pins(keys.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Drops pins for apps that no longer exist. */
    fun retain(existing: Set<AppKey>): Pins = Pins(keys.filter { it in existing })

    fun serialize(): String = keys.joinToString("\n")

    override fun equals(other: Any?) = other is Pins && other.keys == keys

    override fun hashCode() = keys.hashCode()

    companion object {
        const val MAX = 20

        fun parse(text: String?): Pins = Pins(text.orEmpty().lines().mapNotNull(AppKey::parse))
    }
}
