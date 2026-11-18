package io.github.michaelmassoni.tessera.notes.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The export file: every note, with a format version so later versions can read old files. */
object Backup {
    const val VERSION = 1

    @Serializable
    private data class File(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val notes: List<Note>,
    )

    private const val FORMAT = "tessera-notes"
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    fun encode(notes: Collection<Note>): String = json.encodeToString(File.serializer(), File(notes = notes.sortedBy { it.created }))

    /**
     * Notes from an export file, with fresh ids from [newId] so an import never overwrites
     * existing notes. Trashed notes are skipped.
     * @throws IllegalArgumentException if the file isn't a Tessera notes export or is from a newer version.
     */
    fun decode(
        text: String,
        newId: () -> String,
    ): List<Note> {
        val file =
            try {
                json.decodeFromString(File.serializer(), text)
            } catch (e: SerializationException) {
                throw IllegalArgumentException("Not a Tessera notes export", e)
            }
        require(file.format == FORMAT) { "Not a Tessera notes export" }
        require(file.version <= VERSION) { "Exported by a newer version of Notes" }
        return file.notes.filterNot { it.isTrashed }.map { it.copy(id = newId()) }
    }
}
