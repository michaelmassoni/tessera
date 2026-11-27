package io.github.michaelmassoni.tessera.files.engine

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Archive kinds Files can open; [writable] ones are offered when compressing. */
enum class ArchiveFormat(
    val extension: String,
    val writable: Boolean,
) {
    Zip("zip", writable = true),
    TarGz("tar.gz", writable = true),
    Tar("tar", writable = false),
    ;

    companion object {
        /** The format of [file] by its name, or null if it isn't an archive Files can open. */
        fun of(file: File): ArchiveFormat? {
            val name = file.name.lowercase()
            return when {
                name.endsWith(".zip") -> Zip
                name.endsWith(".tar.gz") || name.endsWith(".tgz") -> TarGz
                name.endsWith(".tar") -> Tar
                else -> null
            }
        }

        /** [file]'s name without its archive extension ("photos.tar.gz" → "photos"). */
        fun baseName(file: File): String {
            val name = file.name
            val lower = name.lowercase()
            val ext = listOf(".tar.gz", ".tgz", ".tar", ".zip").firstOrNull { lower.endsWith(it) } ?: return name
            return name.dropLast(ext.length).ifBlank { name }
        }
    }
}

/** How to compress: the archive's name (without extension) and format. */
data class CompressOptions(
    val name: String,
    val format: ArchiveFormat = ArchiveFormat.Zip,
)

/** What an archive holds, for the dialog shown when you tap one. */
data class ArchiveSummary(
    val files: Int,
    val folders: Int,
    /** Uncompressed size of the files. */
    val bytes: Long,
    /** The first entries' paths, in archive order. */
    val sample: List<String>,
)

/**
 * Archives (docs/files.md, Archives): compress files and folders into a new .zip or .tar.gz, and
 * extract a .zip, .tar or .tar.gz into a new folder beside it. Never overwrites (names are made
 * unique, as for copies), never writes outside the target folder ("zip slip"), skips links in tar
 * files, and leaves nothing half-done when it fails or is cancelled. Unit tested.
 */
object Archives {
    private const val BUFFER_SIZE = 256 * 1024
    private const val SAMPLE = 8

    class Unsafe(
        name: String,
    ) : IOException("Unsafe path in archive: $name")

    /** The name suggested for compressing [sources] into [targetDir]: the item, or the folder. */
    fun suggestedName(
        sources: List<File>,
        targetDir: File,
    ): String =
        if (sources.size == 1) {
            sources.single().let { if (it.isDirectory) it.name else it.nameWithoutExtension.ifBlank { it.name } }
        } else {
            targetDir.name.ifBlank { "Archive" }
        }

    /** "[base].[extension]", or "[base] (2).[extension]" and so on if that's taken. */
    fun uniqueArchiveName(
        dir: File,
        base: String,
        extension: String,
    ): String {
        if (!File(dir, "$base.$extension").exists()) return "$base.$extension"
        var n = 1
        while (File(dir, "$base ($n).$extension").exists()) n++
        return "$base ($n).$extension"
    }

    /** Compresses [sources] into a new archive in [targetDir]. @return the archive. */
    fun compress(
        sources: List<File>,
        targetDir: File,
        options: CompressOptions? = null,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (Progress) -> Unit = {},
    ): File {
        require(sources.isNotEmpty())
        val format = options?.format?.takeIf { it.writable } ?: ArchiveFormat.Zip
        val base = options?.name?.trim()?.takeIf { it.isNotEmpty() } ?: suggestedName(sources, targetDir)
        val archive = File(targetDir, uniqueArchiveName(targetDir, base, format.extension))
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
        val buffer = ByteArray(BUFFER_SIZE)

        fun copy(
            file: File,
            out: OutputStream,
        ) {
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
        try {
            when (format) {
                ArchiveFormat.Zip -> {
                    ZipOutputStream(archive.outputStream().buffered()).use { out ->
                        for ((file, name) in entries) {
                            if (cancelled.get()) throw OperationException.Cancelled()
                            out.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
                            if (!name.endsWith("/")) copy(file, out)
                            out.closeEntry()
                        }
                    }
                }

                else -> {
                    TarArchiveOutputStream(GZIPOutputStream(archive.outputStream().buffered())).use { out ->
                        // Long paths and big files, as GNU tar and every desktop reads them.
                        out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        out.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                        for ((file, name) in entries) {
                            if (cancelled.get()) throw OperationException.Cancelled()
                            out.putArchiveEntry(TarArchiveEntry(file, name))
                            if (!name.endsWith("/")) copy(file, out)
                            out.closeArchiveEntry()
                        }
                    }
                }
            }
        } catch (e: IOException) {
            archive.delete()
            throw e
        }
        onProgress(Progress(files, files, total, total, archive.name))
        return archive
    }

    /** Extracts [archive] into a new folder beside it, named after it. @return the folder. */
    fun extract(
        archive: File,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (Progress) -> Unit = {},
    ): File {
        val format = ArchiveFormat.of(archive) ?: throw IOException("Not a readable archive: ${archive.name}")
        val parent = archive.absoluteFile.parentFile ?: throw IOException("No folder for ${archive.path}")
        val target = File(parent, Naming.uniqueName(parent, ArchiveFormat.baseName(archive)))
        try {
            if (!target.mkdirs()) throw IOException("Can't create ${target.path}")
            val root = target.canonicalFile
            val buffer = ByteArray(BUFFER_SIZE)

            fun destination(name: String): File {
                val out = File(target, name).canonicalFile
                if (out != root && !out.path.startsWith(root.path + File.separator)) throw Unsafe(name)
                return out
            }

            fun write(
                input: InputStream,
                out: File,
                progress: (String) -> Unit,
            ) {
                out.parentFile?.mkdirs()
                out.outputStream().use { output ->
                    while (true) {
                        if (cancelled.get()) throw OperationException.Cancelled()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        progress(out.name)
                    }
                }
            }
            when (format) {
                ArchiveFormat.Zip -> {
                    ZipFile(archive).use { zip ->
                        val list = zip.entries().toList()
                        val total = list.filter { !it.isDirectory }.sumOf { it.size.coerceAtLeast(0) }
                        val files = list.count { !it.isDirectory }
                        var done = 0
                        for (entry in list) {
                            if (cancelled.get()) throw OperationException.Cancelled()
                            val out = destination(entry.name)
                            if (entry.isDirectory) {
                                out.mkdirs()
                                continue
                            }
                            val counting = Counting(zip.getInputStream(entry))
                            var before = 0L
                            counting.use { input ->
                                write(input, out) { name ->
                                    onProgress(Progress(done, files, before + counting.count, total, name))
                                }
                            }
                            before += counting.count
                            if (entry.time > 0) out.setLastModified(entry.time)
                            done++
                        }
                    }
                }

                else -> {
                    // A tar has no index: progress is how much of the archive file has been read.
                    val total = archive.length()
                    val raw = Counting(archive.inputStream().buffered())
                    val stream = if (format == ArchiveFormat.TarGz) GZIPInputStream(raw) else raw
                    TarArchiveInputStream(stream).use { tar ->
                        var done = 0
                        while (true) {
                            val entry = tar.nextEntry ?: break
                            if (cancelled.get()) throw OperationException.Cancelled()
                            val out = destination(entry.name)
                            when {
                                entry.isDirectory -> {
                                    out.mkdirs()
                                }

                                // Links could point anywhere: left out.
                                entry.isFile -> {
                                    write(tar, out) { name -> onProgress(Progress(done, 0, raw.count, total, name)) }
                                    entry.modTime
                                        ?.time
                                        ?.takeIf { it > 0 }
                                        ?.let(out::setLastModified)
                                    done++
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            target.deleteRecursively()
            throw IOException("Not a readable archive: ${e.message}", e)
        } catch (e: IOException) {
            target.deleteRecursively()
            throw e
        } catch (e: IllegalArgumentException) {
            // commons-compress reports a corrupt tar header this way.
            target.deleteRecursively()
            throw IOException("Not a readable archive: ${e.message}", e)
        }
        return target
    }

    /** What [archive] holds, read from its index (zip) or headers (tar). */
    fun summary(archive: File): ArchiveSummary {
        val format = ArchiveFormat.of(archive) ?: throw IOException("Not a readable archive: ${archive.name}")
        var files = 0
        var folders = 0
        var bytes = 0L
        val sample = mutableListOf<String>()

        fun add(
            name: String,
            directory: Boolean,
            size: Long,
        ) {
            if (directory) folders++ else files++
            if (!directory) bytes += size.coerceAtLeast(0)
            if (sample.size < SAMPLE) sample += name
        }
        try {
            when (format) {
                ArchiveFormat.Zip -> {
                    ZipFile(archive).use { zip -> zip.entries().asSequence().forEach { add(it.name, it.isDirectory, it.size) } }
                }

                else -> {
                    val raw = archive.inputStream().buffered()
                    val stream = if (format == ArchiveFormat.TarGz) GZIPInputStream(raw) else raw
                    TarArchiveInputStream(stream).use { tar ->
                        while (true) {
                            val entry = tar.nextEntry ?: break
                            if (entry.isDirectory || entry.isFile) add(entry.name, entry.isDirectory, entry.size)
                        }
                    }
                }
            }
        } catch (e: IllegalArgumentException) {
            throw IOException("Not a readable archive: ${e.message}", e)
        }
        return ArchiveSummary(files, folders, bytes, sample)
    }

    fun isArchive(file: File) = file.isFile && ArchiveFormat.of(file) != null

    /** Counts the bytes read through it. */
    private class Counting(
        input: InputStream,
    ) : FilterInputStream(input) {
        var count = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count++ }

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = super.read(b, off, len).also { if (it > 0) count += it }
    }
}
