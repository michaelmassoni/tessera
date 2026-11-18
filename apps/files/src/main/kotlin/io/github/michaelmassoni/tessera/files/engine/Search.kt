package io.github.michaelmassoni.tessera.files.engine

import java.io.File
import java.nio.file.Files
import java.text.Normalizer
import java.util.Locale

/** Recursive name search, streamed (docs/files.md, "Search"). */
object Search {
    const val MAX_RESULTS = 1000
    private val marks = Regex("\\p{M}+")

    fun fold(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").lowercase(Locale.ROOT)

    /**
     * Walks [root] breadth-first so nearby matches come first. Skips Android/data and
     * Android/obb (blocked by the OS) and doesn't follow symbolic links (no loops).
     */
    fun search(
        root: File,
        query: String,
        showHidden: Boolean,
        isCancelled: () -> Boolean = { false },
        onResult: (File) -> Unit,
    ): Int {
        val needle = fold(query.trim())
        if (needle.isEmpty()) return 0
        var found = 0
        val queue = ArrayDeque<File>().apply { add(root) }
        while (queue.isNotEmpty() && found < MAX_RESULTS && !isCancelled()) {
            val dir = queue.removeFirst()
            val children = dir.listFiles()?.sortedBy { it.name } ?: continue
            for (child in children) {
                if (!showHidden && child.name.startsWith(".")) continue
                if (fold(child.name).contains(needle)) {
                    onResult(child)
                    if (++found >= MAX_RESULTS) break
                }
                if (child.isDirectory && !Files.isSymbolicLink(child.toPath()) && !isRestricted(child)) queue.add(child)
            }
        }
        return found
    }

    /** Folders Android blocks even with all-files access. */
    fun isRestricted(dir: File): Boolean {
        val parent = dir.parentFile ?: return false
        return parent.name == "Android" && (dir.name == "data" || dir.name == "obb")
    }
}
