package io.github.michaelmassoni.tessera.files.engine

import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Zip archives (docs/files.md, Zip): compress files and folders into a new .zip, and extract one
 * into a new folder beside it. Never overwrites (names are made unique, as for copies), never
 * writes outside the target folder ("zip slip"), and leaves nothing half-done when it fails or is
 * cancelled. Plain java.util.zip, so it's unit tested.
 */
object Archives {
    private const val BUFFER_SIZE = 256 * 1024

    class Unsafe(
        name: String,
    ) : IOException("Unsafe path in archive: $name")

    /** Compresses [sources] into a new zip in [targetDir], named after the first source. @return the zip. */
    fun compress(
        sources: List<File>,
        targetDir: File,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (Progress) -> Unit = {},
    ): File {
        require(sources.isNotEmpty())
        val base = if (sources.size == 1) sources.single().nameWithoutExtensionOrName() else targetDir.name.ifBlank { "Archive" }
        val zip = File(targetDir, Naming.uniqueName(targetDir, "$base.zip"))
        val entries = mutableListOf<Pair<File, String>>()

        fun walk(
            f: File,
            path: String,
        ) {
            if (f.isDirectory) {
                entries += f to "$path/"
                f.listFiles()?.sortedBy { it.name }?.forEach { walk(it, "$path/${it.name}") }
            } else {
                entries += f to path
            }
        }
        sources.forEach { walk(it, it.name) }
        val total = entries.filter { !it.second.endsWith("/") }.sumOf { it.first.length() }
        val files = entries.count { !it.second.endsWith("/") }
        var doneBytes = 0L
        var doneFiles = 0
        try {
            ZipOutputStream(zip.outputStream().buffered()).use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                for ((file, name) in entries) {
                    if (cancelled.get()) throw OperationException.Cancelled()
                    out.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
                    if (!name.endsWith("/")) {
                        file.inputStream().use { input ->
                            while (true) {
                                if (cancelled.get()) throw OperationException.Cancelled()
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                doneBytes += read
                                onProgress(Progress(doneFiles, files, doneBytes, total, file.name))
                            }
                        }
                        doneFiles++
                    }
                    out.closeEntry()
                }
            }
        } catch (e: IOException) {
            zip.delete()
            throw e
        }
        onProgress(Progress(files, files, total, total, zip.name))
        return zip
    }

    /** Extracts [zip] into a new folder beside it, named after it. @return the folder. */
    fun extract(
        zip: File,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (Progress) -> Unit = {},
    ): File {
        val parent = zip.absoluteFile.parentFile ?: throw IOException("No folder for ${zip.path}")
        val target = File(parent, Naming.uniqueName(parent, zip.nameWithoutExtensionOrName()))
        try {
            ZipFile(zip).use { archive ->
                val list = archive.entries().toList()
                val total = list.filter { !it.isDirectory }.sumOf { it.size.coerceAtLeast(0) }
                val files = list.count { !it.isDirectory }
                var doneBytes = 0L
                var doneFiles = 0
                if (!target.mkdirs()) throw IOException("Can't create ${target.path}")
                val root = target.canonicalFile
                val buffer = ByteArray(BUFFER_SIZE)
                for (entry in list) {
                    if (cancelled.get()) throw OperationException.Cancelled()
                    val out = File(target, entry.name).canonicalFile
                    if (out != root && !out.path.startsWith(root.path + File.separator)) throw Unsafe(entry.name)
                    if (entry.isDirectory) {
                        out.mkdirs()
                        continue
                    }
                    out.parentFile?.mkdirs()
                    archive.getInputStream(entry).use { input ->
                        out.outputStream().use { output ->
                            while (true) {
                                if (cancelled.get()) throw OperationException.Cancelled()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                doneBytes += read
                                onProgress(Progress(doneFiles, files, doneBytes, total, out.name))
                            }
                        }
                    }
                    if (entry.time > 0) out.setLastModified(entry.time)
                    doneFiles++
                }
            }
        } catch (e: ZipException) {
            target.deleteRecursively()
            throw IOException("Not a readable zip: ${e.message}", e)
        } catch (e: IOException) {
            target.deleteRecursively()
            throw e
        }
        return target
    }

    fun isZip(file: File) = file.isFile && file.extension.equals("zip", ignoreCase = true)

    private fun File.nameWithoutExtensionOrName() = if (isDirectory) name else nameWithoutExtension.ifBlank { name }
}
