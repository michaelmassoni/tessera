package io.github.michaelmassoni.tessera.phone.engine

/**
 * Name search from the keypad: 2-6-6 finds "Ann", because each key stands for its letters.
 * Accents are folded first, so "É" is on 3.
 */
object T9 {
    private val keyFor: Map<Char, Char> =
        mapOf('2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl", '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz")
            .flatMap { (key, letters) -> letters.map { it to key } }
            .toMap()

    /** [name] typed on a keypad: letters become their key, digits stay, everything else breaks words. */
    fun keys(name: String): List<String> =
        ContactSections
            .normalize(name)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
            .map { word -> word.map { c -> keyFor[c] ?: c }.joinToString("") }

    /**
     * True if [typed] (at least 2 keys, only 2–9) starts a word of [name], or runs on across words
     * from the start of one ("ann lee" from 2665).
     */
    fun matches(
        typed: String,
        name: String,
    ): Boolean {
        if (typed.length < 2 || typed.any { it !in '2'..'9' }) return false
        val words = keys(name)
        return words.indices.any { i -> words.drop(i).joinToString("").startsWith(typed) }
    }
}
