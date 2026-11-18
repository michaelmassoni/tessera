package io.github.michaelmassoni.tessera.files.engine

import java.io.File

/** A listed file or folder. Read once from disk so sorting and drawing never touch storage. */
data class FileEntry(
    val file: File,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val childCount: Int?,
) {
    val name: String get() = file.name
    val isHidden: Boolean get() = name.startsWith(".")
    val type: FileType get() = if (isDirectory) FileType.Folder else FileType.of(name)

    companion object {
        fun of(file: File): FileEntry {
            val dir = file.isDirectory
            return FileEntry(
                file = file,
                isDirectory = dir,
                size = if (dir) 0 else file.length(),
                lastModified = file.lastModified(),
                childCount = if (dir) file.list()?.size else null,
            )
        }

        fun list(
            dir: File,
            showHidden: Boolean,
        ): List<FileEntry>? =
            dir
                .listFiles()
                ?.asSequence()
                ?.filter { showHidden || !it.name.startsWith(".") }
                ?.map(::of)
                ?.toList()
    }
}

enum class FileType {
    Folder,
    Image,
    Video,
    Audio,
    Pdf,
    Document,
    Archive,
    Apk,
    Other,
    ;

    companion object {
        private val byExtension: Map<String, FileType> =
            buildMap {
                listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif", "dng", "svg").forEach { put(it, Image) }
                listOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts").forEach { put(it, Video) }
                listOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav", "amr", "mid", "midi").forEach { put(it, Audio) }
                put("pdf", Pdf)
                listOf(
                    "txt",
                    "md",
                    "doc",
                    "docx",
                    "odt",
                    "rtf",
                    "xls",
                    "xlsx",
                    "ods",
                    "csv",
                    "ppt",
                    "pptx",
                    "odp",
                    "epub",
                    "html",
                    "json",
                    "xml",
                ).forEach { put(it, Document) }
                listOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "zst").forEach { put(it, Archive) }
                listOf("apk", "apks", "xapk").forEach { put(it, Apk) }
            }

        fun of(name: String): FileType = byExtension[name.substringAfterLast('.', "").lowercase()] ?: Other
    }
}
