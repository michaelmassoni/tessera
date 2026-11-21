package io.github.michaelmassoni.tessera.files.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/** Something in the trash: where it came from and where it is now. */
@Serializable
data class TrashedItem(
    val id: String,
    val originalPath: String,
    val trashedPath: String,
    val deletedAt: Long,
    val isDirectory: Boolean,
    val size: Long,
) {
    val name: String get() = File(originalPath).name

    /** When it will be deleted for good. */
    fun expiresAt(): Long = deletedAt + Trash.KEEP_MILLIS
}

/**
 * A recycle bin for the file browser. Deleting moves an item into a hidden `.Trash-tessera`
 * folder at the root of its own volume (a rename, so it's instant and needs no extra space) and
 * records it in [index]. A `.nomedia` file there keeps trashed photos out of galleries. Items are
 * restored to where they were (with a new name if that's taken) or deleted for good after
 * [KEEP_MILLIS]. Thread-safe (each call is synchronized).
 */
class Trash(
    private val index: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(TrashedItem.serializer())

    @Synchronized
    fun items(): List<TrashedItem> =
        try {
            if (index.exists()) json.decodeFromString(serializer, index.readText()) else emptyList()
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IOException) {
            emptyList()
        }.sortedByDescending { it.deletedAt }

    private fun save(items: List<TrashedItem>) {
        index.parentFile?.mkdirs()
        val temp = File(index.path + ".tmp")
        temp.writeText(json.encodeToString(serializer, items))
        if (!temp.renameTo(index)) throw IOException("Couldn't save the trash index")
    }

    /**
     * Moves [files] to the trash of the volume each is on ([rootOf]). Stops at the first failure,
     * keeping what was already moved. @return the new items.
     */
    @Synchronized
    fun move(
        files: List<File>,
        rootOf: (File) -> File,
    ): List<TrashedItem> {
        val added = mutableListOf<TrashedItem>()
        try {
            for (file in files) {
                val dir = File(rootOf(file), DIR_NAME)
                if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Can't create $dir")
                File(dir, ".nomedia").takeUnless { it.exists() }?.createNewFile()
                val id = UUID.randomUUID().toString()
                val target = File(dir, "$id-${file.name}")
                val size = if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
                val directory = file.isDirectory
                if (!file.renameTo(target)) throw IOException("Can't move ${file.path} to the trash")
                added += TrashedItem(id, file.absolutePath, target.absolutePath, now(), directory, size)
            }
        } finally {
            if (added.isNotEmpty()) save(items() + added)
        }
        return added
    }

    /** Puts items back where they were. @return the restored files (renamed if their place was taken). */
    @Synchronized
    fun restore(ids: Collection<String>): List<File> {
        val all = items()
        val restored = mutableListOf<File>()
        val kept =
            all.filter { item ->
                if (item.id !in ids) return@filter true
                val original = File(item.originalPath)
                val parent = original.parentFile ?: return@filter true
                parent.mkdirs()
                val target =
                    if (original.exists()) File(parent, Naming.uniqueName(parent, original.name)) else original
                if (File(item.trashedPath).renameTo(target)) {
                    restored += target
                    false
                } else {
                    true
                }
            }
        save(kept)
        return restored
    }

    /** Deletes items for good. */
    @Synchronized
    fun delete(ids: Collection<String>) {
        val all = items()
        all.filter { it.id in ids }.forEach { File(it.trashedPath).deleteRecursively() }
        save(all.filter { it.id !in ids })
    }

    @Synchronized
    fun empty() = delete(items().map { it.id })

    /** Deletes items older than [KEEP_MILLIS], and forgets ones that are already gone. @return how many went. */
    @Synchronized
    fun purge(): Int {
        val all = items()
        val (expired, kept) = all.partition { it.expiresAt() <= now() || !File(it.trashedPath).exists() }
        if (expired.isEmpty()) return 0
        expired.forEach { File(it.trashedPath).deleteRecursively() }
        save(kept)
        return expired.size
    }

    companion object {
        const val DIR_NAME = ".Trash-tessera"
        const val KEEP_DAYS = 30
        const val KEEP_MILLIS = KEEP_DAYS * 24 * 60 * 60 * 1000L
    }
}
