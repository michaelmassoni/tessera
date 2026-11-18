package io.github.michaelmassoni.tessera.files.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

data class Volume(
    val root: File,
    val description: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
) {
    val usedFraction: Float get() = if (totalBytes <= 0) 0f else ((totalBytes - freeBytes).toFloat() / totalBytes).coerceIn(0f, 1f)
}

/** Mounted storage volumes (internal storage, SD cards, USB drives). */
object StorageVolumes {
    fun list(context: Context): List<Volume> {
        val manager = context.getSystemService(StorageManager::class.java)
        return manager.storageVolumes
            .filter { it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY }
            .mapNotNull { volume ->
                val dir = volume.directory ?: return@mapNotNull null
                val stat = runCatching { StatFs(dir.path) }.getOrNull()
                Volume(
                    root = dir,
                    description = volume.getDescription(context),
                    isPrimary = volume.isPrimary,
                    isRemovable = volume.isRemovable,
                    totalBytes = stat?.totalBytes ?: 0,
                    freeBytes = stat?.availableBytes ?: 0,
                )
            }
    }

    /** The volume [file] lives on, for breadcrumbs that start at the volume name. */
    fun volumeOf(
        file: File,
        volumes: List<Volume>,
    ): Volume? =
        volumes.filter { file.path == it.root.path || file.path.startsWith(it.root.path + "/") }.maxByOrNull { it.root.path.length }
}
