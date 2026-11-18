package io.github.michaelmassoni.tessera.files.engine

import java.io.File

enum class NameProblem { Empty, Reserved, InvalidCharacter, TooLong, Exists }

object Naming {
    /** Extensions that belong together, so "x.tar.gz" becomes "x (1).tar.gz", not "x.tar (1).gz". */
    private val COMPOUND = listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst")

    /** Splits "photo.jpg" into ("photo", ".jpg"). Hidden files like ".bashrc" have no extension. */
    fun split(name: String): Pair<String, String> {
        COMPOUND.firstOrNull { name.length > it.length && name.endsWith(it, ignoreCase = true) }?.let {
            return name.dropLast(it.length) to name.takeLast(it.length)
        }
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name to "" else name.substring(0, dot) to name.substring(dot)
    }

    /** [name] if free in [dir], otherwise "name (1).ext", "name (2).ext", ... */
    fun uniqueName(
        dir: File,
        name: String,
    ): String {
        if (!File(dir, name).exists()) return name
        val isDir = File(dir, name).isDirectory
        val (base, ext) = if (isDir) name to "" else split(name)
        // Continue an existing counter: "a (1).txt" -> "a (2).txt", not "a (1) (1).txt".
        val match = Regex("""^(.*) \((\d+)\)$""").find(base)
        val stem = match?.groupValues?.get(1) ?: base
        var n =
            match
                ?.groupValues
                ?.get(2)
                ?.toIntOrNull()
                ?.plus(1) ?: 1
        while (File(dir, "$stem ($n)$ext").exists()) n++
        return "$stem ($n)$ext"
    }

    fun validate(
        name: String,
        dir: File? = null,
    ): NameProblem? =
        when {
            name.isBlank() -> NameProblem.Empty
            name == "." || name == ".." -> NameProblem.Reserved
            name.any { it == '/' || it == '\u0000' } -> NameProblem.InvalidCharacter
            name.toByteArray().size > 255 -> NameProblem.TooLong
            dir != null && File(dir, name).exists() -> NameProblem.Exists
            else -> null
        }

    /** True if [child] is [parent] or somewhere below it (by canonical path). */
    fun isInside(
        child: File,
        parent: File,
    ): Boolean {
        val c = child.canonicalFile.path
        val p = parent.canonicalFile.path
        return c == p || c.startsWith(p.trimEnd('/') + "/")
    }
}
