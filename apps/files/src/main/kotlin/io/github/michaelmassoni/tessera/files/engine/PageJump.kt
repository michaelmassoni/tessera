package io.github.michaelmassoni.tessera.files.engine

/** "Go to page" in the PDF viewer. */
object PageJump {
    /** The 0-based page for what was typed ("12"), or null if it isn't a page of a [count]-page document. */
    fun parse(
        text: String,
        count: Int,
    ): Int? =
        text
            .trim()
            .toIntOrNull()
            ?.takeIf { it in 1..count }
            ?.minus(1)
}

/** Which page the reader is on, for "Page 3 of 12". */
object VisiblePage {
    /**
     * The page across the middle of the view: of the visible pages (from [first], with their
     * [tops] in view pixels), the last one that starts above [middle]. The first visible page
     * alone is wrong after a jump, which leaves the end of the page before still showing.
     */
    fun current(
        first: Int,
        tops: List<Float>,
        middle: Float,
    ): Int {
        val index = tops.indexOfLast { it <= middle }
        return first + index.coerceAtLeast(0)
    }
}
