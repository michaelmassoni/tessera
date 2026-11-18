package io.github.michaelmassoni.tessera.files.data

import android.content.Context
import android.os.storage.StorageManager
import java.io.File
import java.io.IOException

/**
 * Free space as Android sees it: includes cached data the system will clear on demand, which
 * File.usableSpace doesn't count. Falls back to usableSpace for volumes StorageManager can't map.
 */
class AndroidFreeSpace(
    context: Context,
) : (File) -> Long {
    private val storage = context.getSystemService(StorageManager::class.java)

    override fun invoke(dir: File): Long =
        try {
            storage.getAllocatableBytes(storage.getUuidForPath(dir))
        } catch (e: IOException) {
            @Suppress("UsableSpace") // documented fallback
            dir.usableSpace
        }
}
