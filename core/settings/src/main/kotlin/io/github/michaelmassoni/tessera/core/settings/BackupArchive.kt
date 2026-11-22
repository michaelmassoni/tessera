package io.github.michaelmassoni.tessera.core.settings

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup is of. */
data class BackupManifest(
    val app: String,
    val label: String,
    val versionCode: Long,
    val created: Long,
    val format: Int = BackupArchive.FORMAT,
)

/**
 * The backup file format, in plain Kotlin so it's unit tested. A backup is a zip: `manifest`
 * first (a properties file), then the files of each storage root under its name
 * (`files/notes/a.json`, `prefs/settings.xml`…). A suite backup is a zip of app backups
 * (`apps/<package>.zip`) after its own manifest.
 */
object BackupArchive {
    const val FORMAT = 1
    private const val MANIFEST = "manifest"
    private const val APPS = "apps/"

    /** Files every backup leaves out: caches, temporary files and library state. */
    private val ALWAYS_EXCLUDED =
        listOf("files/datastore/", "files/profileInstalled", "files/profileinstaller", "files/coil", "prefs/androidx.work", "prefs/WebView")

    fun excluded(
        path: String,
        extra: List<String> = emptyList(),
    ): Boolean = path.endsWith(".tmp") || (ALWAYS_EXCLUDED + extra).any { path.startsWith(it) }

    fun write(
        out: OutputStream,
        manifest: BackupManifest,
        roots: Map<String, File>,
        exclude: List<String> = emptyList(),
    ) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(MANIFEST))
        manifest.toProperties().store(zip, null)
        zip.closeEntry()
        for ((name, root) in roots) {
            if (!root.isDirectory) continue
            root
                .walkTopDown()
                .filter { it.isFile }
                .sortedBy { it.path }
                .forEach { file ->
                    val path = "$name/" + file.relativeTo(root).invariantSeparatorsPath
                    if (excluded(path, exclude)) return@forEach
                    zip.putNextEntry(ZipEntry(path))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        zip.finish()
    }

    /** The manifest at the start of a backup, or null if it isn't one. */
    fun manifest(input: InputStream): BackupManifest? {
        val zip = ZipInputStream(input)
        val entry = zip.nextEntry ?: return null
        if (entry.name != MANIFEST) return null
        return Properties().apply { load(zip) }.toManifest()
    }

    /**
     * Replaces the data in [roots] with the backup in [input]: it's unpacked into [staging] first,
     * and only once it has all arrived are the roots emptied and filled. Paths that would leave a
     * root are refused. [check] sees the manifest first and can refuse the backup by throwing.
     */
    fun restore(
        input: InputStream,
        roots: Map<String, File>,
        staging: File,
        exclude: List<String> = emptyList(),
        check: (BackupManifest) -> Unit = {},
    ): BackupManifest {
        staging.deleteRecursively()
        staging.mkdirs()
        val zip = ZipInputStream(input)
        val first = zip.nextEntry ?: throw IOException("Empty backup")
        if (first.name != MANIFEST) throw IOException("Not a Tessera backup")
        val manifest = Properties().apply { load(zip) }.toManifest() ?: throw IOException("Unreadable manifest")
        if (manifest.format > FORMAT) throw IOException("Backup is from a newer format")
        check(manifest)
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory || excluded(entry.name, exclude)) continue
            val root = entry.name.substringBefore('/')
            if (root !in roots) continue
            val target = File(staging, entry.name).canonicalFile
            if (!target.path.startsWith(staging.canonicalPath + File.separator)) throw IOException("Bad path in backup: ${entry.name}")
            target.parentFile?.mkdirs()
            target.outputStream().use { zip.copyTo(it) }
        }
        for ((name, root) in roots) {
            // Clear what a backup would hold, leaving out what it never contains.
            root
                .walkBottomUp()
                .filter { it.isFile && !excluded("$name/" + it.relativeTo(root).invariantSeparatorsPath, exclude) }
                .forEach { it.delete() }
            File(staging, name).takeIf { it.isDirectory }?.copyRecursively(root, overwrite = true)
        }
        staging.deleteRecursively()
        return manifest
    }

    // ---- Suite backups ----

    fun writeSuite(
        out: OutputStream,
        manifest: BackupManifest,
        apps: List<Pair<String, (OutputStream) -> Unit>>,
    ): List<String> {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(MANIFEST))
        manifest.toProperties().store(zip, null)
        zip.closeEntry()
        val written = mutableListOf<String>()
        for ((pkg, write) in apps) {
            // Each app's backup is made in full first, so one that fails (not signed like this app,
            // an older version without backups) leaves no empty entry behind.
            val bytes =
                runCatching {
                    java.io
                        .ByteArrayOutputStream()
                        .also { write(it) }
                        .toByteArray()
                }.getOrNull()
            if (bytes == null || bytes.isEmpty()) continue
            zip.putNextEntry(ZipEntry("$APPS$pkg.zip"))
            zip.write(bytes)
            zip.closeEntry()
            written += pkg
        }
        zip.finish()
        return written
    }

    /** Calls [each] with every app backup in a suite backup, by package, as it streams past. */
    fun readSuite(
        input: InputStream,
        each: (String, InputStream) -> Unit,
    ): BackupManifest {
        val zip = ZipInputStream(input)
        val first = zip.nextEntry ?: throw IOException("Empty backup")
        if (first.name != MANIFEST) throw IOException("Not a Tessera backup")
        val manifest = Properties().apply { load(zip) }.toManifest() ?: throw IOException("Unreadable manifest")
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.name.startsWith(APPS) || !entry.name.endsWith(".zip")) continue
            each(entry.name.removePrefix(APPS).removeSuffix(".zip"), NoCloseIn(zip))
        }
        return manifest
    }

    /** The package a suite backup's manifest uses. */
    const val SUITE = "suite"

    private fun BackupManifest.toProperties() =
        Properties().apply {
            setProperty("app", app)
            setProperty("label", label)
            setProperty("versionCode", versionCode.toString())
            setProperty("created", created.toString())
            setProperty("format", format.toString())
        }

    private fun Properties.toManifest(): BackupManifest? {
        val app = getProperty("app") ?: return null
        return BackupManifest(
            app,
            getProperty("label").orEmpty(),
            getProperty("versionCode")?.toLongOrNull() ?: 0,
            getProperty("created")?.toLongOrNull() ?: 0,
            getProperty("format")?.toIntOrNull() ?: 0,
        )
    }

    private class NoCloseIn(
        private val input: InputStream,
    ) : InputStream() {
        override fun read() = input.read()

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ) = input.read(b, off, len)

        override fun close() = Unit
    }
}
