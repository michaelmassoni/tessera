package io.github.michaelmassoni.tessera.keyboard.engine

/** One entry in the suggestion strip. */
data class Slot(
    val text: String,
    val kind: Kind,
) {
    enum class Kind {
        /** A dictionary word: picking it replaces the word being typed. */
        Word,

        /** Exactly what was typed, kept as is; picking it also learns it. */
        Literal,

        /** The word autocorrect will use on the next space or punctuation. */
        Correction,

        /** The word before the last autocorrection; picking it undoes the correction. */
        Undo,
    }
}

/**
 * Decides suggestions and autocorrections for the word being typed. [learned] are words the
 * user kept (by undoing a correction or picking what they typed); they're never corrected.
 */
class Suggester(
    private val dictionary: Dictionary,
    private val learned: () -> Set<String> = { emptySet() },
    private val blockOffensive: () -> Boolean = { true },
) {
    /** True if [word] is something to suggest for or correct: letters, maybe apostrophes. */
    fun isWord(word: String): Boolean = word.any { it.isLetter() } && word.all { it.isLetter() || it == '\'' || it == '’' }

    fun isKnown(word: String): Boolean = dictionary.contains(word) || Dictionary.key(word) in learned()

    /** What [typed] should become when the user ends the word, or null to leave it. */
    fun autocorrection(typed: String): String? = if (isWord(typed)) correction(typed, candidates(typed, LIMIT)) else null

    private fun correction(
        typed: String,
        candidates: List<Dictionary.Candidate>,
    ): String? {
        val known = isKnown(typed) && Dictionary.key(typed) !in CONTRACTIONS_FIRST
        if (known) return null
        // Skip the typed word itself (a rare word like "cant" can tie with "can't").
        val best =
            candidates.firstOrNull { it.word != typed && !(it.word == it.word.lowercase() && it.word.equals(typed, ignoreCase = true)) }
                ?: return null
        // Only case or apostrophes differ ("im" → "I'm", "sydney" → "Sydney"): always fix.
        if (best.cost == 0f) return withCase(typed, best.word)
        if (typed.length < MIN_CORRECTED_LENGTH || best.score < MIN_SCORE) return null
        return withCase(typed, best.word)
    }

    /** The strip for [typed]: left, middle and right slots (null where empty), or none. */
    fun strip(typed: String): List<Slot?> {
        if (!isWord(typed)) return emptyList()
        val candidates = candidates(typed, LIMIT)
        val correction = correction(typed, candidates)
        val known = isKnown(typed)
        val others =
            (candidates + completions(typed))
                .sortedByDescending { it.score }
                .map { withCase(typed, it.word) }
                .distinct()
                .filter { it != typed && it != correction }
        return when {
            correction != null -> {
                listOf(
                    Slot(typed, Slot.Kind.Literal),
                    Slot(correction, Slot.Kind.Correction),
                    others.getOrNull(0)?.let {
                        Slot(it, Slot.Kind.Word)
                    },
                )
            }

            known -> {
                listOf(
                    others.getOrNull(0)?.let {
                        Slot(it, Slot.Kind.Word)
                    },
                    Slot(typed, Slot.Kind.Word),
                    others.getOrNull(1)?.let { Slot(it, Slot.Kind.Word) },
                )
            }

            else -> {
                listOf(
                    Slot(typed, Slot.Kind.Literal),
                    others.getOrNull(0)?.let {
                        Slot(it, Slot.Kind.Word)
                    },
                    others.getOrNull(1)?.let { Slot(it, Slot.Kind.Word) },
                )
            }
        }
    }

    private fun candidates(
        typed: String,
        limit: Int,
    ): List<Dictionary.Candidate> =
        dictionary
            .corrections(typed, maxCost(Dictionary.key(typed).length), limit + BLOCK_MARGIN)
            .filter { allowed(it.word, typed) }
            .take(limit)

    private fun completions(typed: String): List<Dictionary.Candidate> =
        if (typed.length < MIN_COMPLETION_PREFIX) {
            emptyList()
        } else {
            dictionary.completions(typed, COMPLETIONS + BLOCK_MARGIN).filter { allowed(it.word, typed) }.take(COMPLETIONS)
        }

    /** Offensive words are only offered when they're exactly what was typed. */
    private fun allowed(
        word: String,
        typed: String,
    ) = !blockOffensive() || Dictionary.key(word) !in OFFENSIVE || Dictionary.key(word) == Dictionary.key(typed)

    companion object {
        private const val MIN_CORRECTED_LENGTH = 2
        private const val LIMIT = 6

        /** Real but rare words that are almost always a missing apostrophe. */
        private val CONTRACTIONS_FIRST = setOf("cant", "wont")
        private const val MIN_COMPLETION_PREFIX = 2
        private const val COMPLETIONS = 4
        private const val BLOCK_MARGIN = 4

        /** A correction must be a reasonably common word: log-frequency after the edit cost. */
        private const val MIN_SCORE = 2f

        /** How many edits a word of [length] letters may be away from what was typed. */
        fun maxCost(length: Int): Float =
            when {
                length <= 2 -> 0f
                length <= 4 -> 1f
                length <= 7 -> 1.6f
                else -> 2f
            }

        /** Applies the capitalisation of [typed] to [word], keeping the word's own capitals. */
        fun withCase(
            typed: String,
            word: String,
        ): String {
            val letters = typed.filter { it.isLetter() }
            return when {
                letters.length > 1 && letters.all { it.isUpperCase() } -> word.uppercase()
                typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.uppercaseChar() }
                else -> word
            }
        }

        /** Never suggested unless typed in full (keys: lower case, no apostrophes). */
        private val OFFENSIVE =
            setOf(
                "fuck",
                "fucking",
                "fucked",
                "fucker",
                "shit",
                "shitty",
                "cunt",
                "cock",
                "dick",
                "bitch",
                "bastard",
                "piss",
                "pissed",
                "wank",
                "wanker",
                "twat",
                "slut",
                "whore",
                "fag",
                "faggot",
                "nigger",
                "retard",
            )
    }
}
