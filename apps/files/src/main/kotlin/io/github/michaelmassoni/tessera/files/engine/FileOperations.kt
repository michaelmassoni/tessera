package io.github.michaelmassoni.tessera.files.engine

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

/** Progress of a copy or move: files and bytes done so far, and the totals. */
data class Progress(
    val filesDone: Int,
    val filesTotal: Int,
    val bytesDone: Long,
    val bytesTotal: Long,
    val current: String,
)

sealed class OperationException(
    message: String,
) : IOException(message) {
    class IntoItself(
        val source: File,
    ) : OperationException("Can't copy ${source.name} into itself")

    class NotEnoughSpace(
        val needed: Long,
        val available: Long,
    ) : OperationException("Need $needed bytes, $available available")

    class Cancelled : OperationException("Cancelled")
}

/**
 * Copy, move and delete on plain files (docs/files.md, "Engine"). Never overwrites: name clashes
 * get "name (1).ext". Symbolic links are copied as links and never followed when deleting.
 * Cancellation is checked between buffers; a cancelled or failed copy removes its partial file.
 */
class FileOperations(
    /** Bytes that can be written under a folder. On Android, see [io.github.michaelmassoni.tessera.files.data.AndroidFreeSpace]. */
    private val freeSpace: (File) -> Long,
    /** Same-volume rename; fails (returns false) across volumes. Injectable for tests. */
    private val rename: (File, File) -> Boolean = { from, to ->
        runCatching { Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE) }.isSuccess
    },
) {
    fun copy(
        sources: List<File>,
        targetDir: File,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (Progress) -> Unit = {},
    ): List<File> {
        check(sources, targetDir)
        val (files, bytes) = measure(sources)
        val available = freeSpace(targetDir)
        if (bytes > available) throw OperationException.NotEnoughSpace(bytes, available)
        val tracker = Tracker(files, bytes, onProgress)
        return sources.map { source ->
            val dest = File(targetDir, Naming.uniqueName(targetDir, source.name))
            copyRecursive(source, dest, cancelled, tracker)
            dest
        }
    }

    fun move(
        sources: List<File>,
        targetDir: File,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (Progress) -> Unit = {},
    ): List<File> {
        check(sources, targetDir)
        val results = mutableListOf<File>()
        val needCopy = mutableListOf<File>()
        for (source in sources) {
            if (source.parentFile?.canonicalFile == targetDir.canonicalFile) {
                results += source // already there
                continue
            }
            val dest = File(targetDir, Naming.uniqueName(targetDir, source.name))
            // Same volume: an atomic rename, instant regardless of size.
            if (rename(source, dest)) {
                results += dest
            } else {
                needCopy += source
            }
        }
        if (needCopy.isNotEmpty()) {
            // Across volumes: copy everything first, delete sources only after all copies succeed.
            val copies = copy(needCopy, targetDir, cancelled, onProgress)
            delete(needCopy)
            results += copies
        }
        return results
    }

    /** Deletes files and folders recursively. Links are removed, never followed. */
    fun delete(paths: List<File>) {
        for (path in paths) deleteRecursive(path)
    }

    /** Total file count and bytes, for progress and the free-space check. Links count as one file. */
    fun measure(paths: List<File>): Pair<Int, Long> {
        var count = 0
        var bytes = 0L

        fun walk(f: File) {
            if (isLink(f) || !f.isDirectory) {
                count++
                if (!isLink(f)) bytes += f.length()
                return
            }
            f.listFiles()?.forEach(::walk)
        }
        paths.forEach(::walk)
        return count to bytes
    }

    private fun check(
        sources: List<File>,
        targetDir: File,
    ) {
        require(targetDir.isDirectory) { "${targetDir.path} is not a folder" }
        for (source in sources) {
            if (source.isDirectory && !isLink(source) && Naming.isInside(targetDir, source)) {
                throw OperationException.IntoItself(source)
            }
        }
    }

    private fun copyRecursive(
        source: File,
        dest: File,
        cancelled: AtomicBoolean,
        tracker: Tracker,
    ) {
        if (cancelled.get()) throw OperationException.Cancelled()
        when {
            isLink(source) -> {
                Files.copy(source.toPath(), dest.toPath(), LinkOption.NOFOLLOW_LINKS)
                tracker.fileDone(source.name, 0)
            }

            source.isDirectory -> {
                if (!dest.mkdirs() && !dest.isDirectory) throw IOException("Can't create ${dest.path}")
                source.listFiles()?.sortedBy { it.name }?.forEach { child ->
                    copyRecursive(child, File(dest, child.name), cancelled, tracker)
                }
                dest.setLastModified(source.lastModified())
            }

            else -> {
                copyFile(source, dest, cancelled, tracker)
            }
        }
    }

    private fun copyFile(
        source: File,
        dest: File,
        cancelled: AtomicBoolean,
        tracker: Tracker,
    ) {
        try {
            source.inputStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (cancelled.get()) throw OperationException.Cancelled()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        tracker.bytes(source.name, read.toLong())
                    }
                    output.fd.sync()
                }
            }
            dest.setLastModified(source.lastModified())
            tracker.fileDone(source.name, 0)
        } catch (e: IOException) {
            dest.delete() // never leave a partial file behind
            throw e
        }
    }

    private fun deleteRecursive(f: File) {
        if (!isLink(f) && f.isDirectory) f.listFiles()?.forEach(::deleteRecursive)
        if (!f.delete() && (f.exists() || isLink(f))) throw IOException("Can't delete ${f.path}")
    }

    private fun isLink(f: File) = Files.isSymbolicLink(f.toPath())

    private class Tracker(
        private val filesTotal: Int,
        private val bytesTotal: Long,
        private val onProgress: (Progress) -> Unit,
    ) {
        private var files = 0
        private var bytes = 0L

        fun bytes(
            name: String,
            n: Long,
        ) {
            bytes += n
            onProgress(Progress(files, filesTotal, bytes, bytesTotal, name))
        }

        fun fileDone(
            name: String,
            n: Long,
        ) {
            files++
            bytes += n
            onProgress(Progress(files, filesTotal, bytes, bytesTotal, name))
        }
    }

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
    }
}
