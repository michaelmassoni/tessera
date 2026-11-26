package io.github.michaelmassoni.tessera.files.engine

/**
 * Which folders keep their scroll position. A folder keeps it while you're inside it (in it or in
 * a folder under it), so going back up lands where you were; once you've left it for a folder
 * outside it, it's forgotten and opening it again starts at the top.
 */
object FolderKeys {
    /** Of the folders in [kept], the ones to forget now that [now] is shown (null = home). */
    fun stale(
        kept: List<String>,
        now: String?,
    ): List<String> = kept.filterNot { now != null && isWithin(now, it) }

    private fun isWithin(
        path: String,
        folder: String,
    ): Boolean = path == folder || path.startsWith(folder.trimEnd('/') + "/")
}
