package io.github.michaelmassoni.tessera.files.data

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/** Opening and sharing through FileProvider, so other apps get a temporary grant, not a path. */
object FileIntents {
    fun mimeType(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"

    private fun uri(
        context: Context,
        file: File,
    ) = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    /** @return false if no app can open it. */
    fun open(
        context: Context,
        file: File,
    ): Boolean {
        val intent =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri(context, file), mimeType(file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: IllegalArgumentException) {
            false // path outside FileProvider roots
        }
    }

    fun share(
        context: Context,
        files: List<File>,
        chooserTitle: String,
    ) {
        if (files.isEmpty()) return
        val uris = files.map { uri(context, it) }
        val types = files.map(::mimeType).distinct()
        val type =
            types.singleOrNull() ?: (
                types
                    .map { it.substringBefore('/') }
                    .distinct()
                    .singleOrNull()
                    ?.let { "$it/*" } ?: "*/*"
            )
        val intent =
            if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
