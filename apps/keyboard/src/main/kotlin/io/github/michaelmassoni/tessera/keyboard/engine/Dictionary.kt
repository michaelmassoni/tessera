package io.github.michaelmassoni.tessera.keyboard.engine

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min

/**
 * A word list with frequencies (see `scripts/keyboard-dictionary.sh`). Words are matched by
 * their key: lower case without apostrophes, so "dont" finds "don't" and "michael" finds
 * "Michael".
 */
class Dictionary private constructor(
    private val words: Array<String>,
    private val keys: Array<String>,
    private val weights: FloatArray,
) {
    /** Entry indices grouped by key length, for the typo search. */
    private val byLength: Array<IntArray> =
        run {
            val longest = keys.maxOfOrNull { it.length } ?: 0
            val groups = Array(longest + 1) { mutableListOf<Int>() }
            keys.forEachIndexed { i, k -> groups[k.length] += i }
            Array(groups.size) { groups[it].toIntArray() }
        }

    val size get() = words.size

    /**
     * True if [typed] is a word as written. Lower-case entries accept any capitalisation
     * ("The"); capitalised ones ("Michael", "I") must be typed with their capital.
     */
    fun contains(typed: String): Boolean =
        range(key(typed)).any { i ->
            val word = words[i]
            word.equals(typed, ignoreCase = true) && (word == word.lowercase() || word == typed || typed == typed.uppercase())
        }

    /** Entries whose key is within [maxCost] edits of [typed]'s key, best first. */
    fun corrections(
        typed: String,
        maxCost: Float,
        limit: Int,
    ): List<Candidate> {
        val key = key(typed)
        if (key.isEmpty()) return emptyList()
        val found = mutableListOf<Candidate>()
        val reach = maxCost.toInt()
        val buffers = Array(3) { FloatArray(key.length + reach + 2) }
        // People rarely get the first letter wrong; checking only words that start with it, a
        // neighbouring key, or the second letter (swapped) keeps this fast enough per keystroke.
        val first = key[0]
        val second = key.getOrNull(1)
        for (length in (key.length - reach).coerceAtLeast(1)..(key.length + reach)) {
            for (i in byLength.getOrNull(length) ?: continue) {
                val k = keys[i]
                val c = k[0]
                if (c != first && c != second && !neighbours(c, first)) continue
                val cost = if (k == key) 0f else distance(key, k, maxCost, buffers)
                if (cost <= maxCost) found += Candidate(words[i], cost, weights[i] - COST_WEIGHT * cost)
            }
        }
        return found.sortedWith(compareBy<Candidate> { it.cost != 0f }.thenByDescending { it.score }).take(limit)
    }

    /** The most frequent words that start with [typed] and are longer than it. */
    fun completions(
        typed: String,
        limit: Int,
    ): List<Candidate> {
        val key = key(typed)
        if (key.isEmpty()) return emptyList()
        val from = lowerBound(key)
        val found = mutableListOf<Candidate>()
        var i = from
        while (i < keys.size && keys[i].startsWith(key)) {
            if (keys[i].length > key.length) found += Candidate(words[i], 0f, weights[i] - COMPLETION_PENALTY)
            i++
        }
        return found.sortedByDescending { it.score }.take(limit)
    }

    private fun range(key: String): IntRange {
        val from = lowerBound(key)
        var to = from
        while (to < keys.size && keys[to] == key) to++
        return from until to
    }

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    data class Candidate(
        val word: String,
        /** Edit cost from the typed key; 0 means the same key (only case or apostrophes differ). */
        val cost: Float,
        val score: Float,
    )

    companion object {
        /** How much one full edit counts against a word, in log-frequency units (about ×150). */
        private const val COST_WEIGHT = 5f

        /** Completions rank a little below a correction of the same frequency. */
        private const val COMPLETION_PENALTY = 1.5f

        /** Parses "word<TAB>count" lines. */
        fun parse(lines: Sequence<String>): Dictionary {
            val entries =
                lines
                    .mapNotNull { line ->
                        val tab = line.indexOf('\t')
                        if (tab <= 0) return@mapNotNull null
                        val word = line.substring(0, tab)
                        Triple(key(word), word, ln((line.substring(tab + 1).toIntOrNull() ?: 1).coerceAtLeast(1).toFloat()))
                    }.sortedBy { it.first }
                    .toList()
            return Dictionary(
                Array(entries.size) { entries[it].second },
                Array(entries.size) { entries[it].first },
                FloatArray(entries.size) { entries[it].third },
            )
        }

        fun key(word: String): String =
            buildString(word.length) {
                for (c in word) if (c != '\'' && c != '’') append(c.lowercaseChar())
            }

        /**
         * Weighted Damerau–Levenshtein distance between two keys, or a value above [max] once
         * it can't be reached. Typing mistakes cost less: a neighbouring key, swapped letters,
         * and a missing or extra copy of a doubled letter ("helo", "helllo").
         */
        internal fun distance(
            a: String,
            b: String,
            max: Float,
            buffers: Array<FloatArray> = Array(3) { FloatArray(b.length + 1) },
        ): Float {
            if (abs(a.length - b.length) > max) return Float.MAX_VALUE
            val m = b.length
            var before = buffers[0]
            var previous = buffers[1]
            var current = buffers[2]
            previous[0] = 0f
            for (j in 1..m) previous[j] = previous[j - 1] + indel(b, j - 1)
            for (i in 1..a.length) {
                current[0] = previous[0] + indel(a, i - 1)
                var rowMin = current[0]
                for (j in 1..m) {
                    val ca = a[i - 1]
                    val cb = b[j - 1]
                    var best =
                        min(
                            previous[j - 1] + substitution(ca, cb),
                            min(previous[j] + indel(a, i - 1), current[j - 1] + indel(b, j - 1)),
                        )
                    if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb && ca != cb) best = min(best, before[j - 2] + TRANSPOSE)
                    current[j] = best
                    if (best < rowMin) rowMin = best
                }
                if (rowMin > max) return Float.MAX_VALUE
                val recycled = before
                before = previous
                previous = current
                current = recycled
            }
            return previous[m]
        }

        private const val TRANSPOSE = 0.8f
        private const val NEIGHBOUR = 0.7f
        private const val DOUBLED = 0.4f

        /** Inserting or deleting s[i] costs less when it's one of a doubled pair. */
        private fun indel(
            s: String,
            i: Int,
        ): Float = if ((i > 0 && s[i] == s[i - 1]) || (i + 1 < s.length && s[i] == s[i + 1])) DOUBLED else 1f

        private fun substitution(
            a: Char,
            b: Char,
        ): Float =
            when {
                a == b -> 0f
                neighbours(a, b) -> NEIGHBOUR
                else -> 1f
            }

        private val rows = listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)

        private val positions: Map<Char, Pair<Float, Float>> =
            buildMap {
                rows.forEachIndexed { y, (letters, offset) ->
                    letters.forEachIndexed { x, c -> put(c, x + offset to y.toFloat()) }
                }
            }

        /** Keys that touch on a QWERTY layout. */
        internal fun neighbours(
            a: Char,
            b: Char,
        ): Boolean {
            if (a == b) return false
            val pa = positions[a] ?: return false
            val pb = positions[b] ?: return false
            return hypot(pa.first - pb.first, pa.second - pb.second) < 1.5f
        }
    }
}
