package io.github.michaelmassoni.tessera.files.viewer

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.pdf.PdfDocument
import androidx.pdf.PdfPasswordException
import androidx.pdf.SandboxedPdfLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Where opening the PDF has got to. */
sealed interface PdfLoad {
    data object Loading : PdfLoad

    /** Password-protected; [wrong] once a password has been tried. */
    data class NeedsPassword(
        val wrong: Boolean,
    ) : PdfLoad

    data object Failed : PdfLoad

    data class Ready(
        val document: PdfDocument,
    ) : PdfLoad
}

/**
 * Holds the open document, so rotating the screen doesn't reload it. Parsing happens in the
 * library's isolated process ([SandboxedPdfLoader]): a hostile file can't reach the app's data.
 */
class ViewerViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val loader = SandboxedPdfLoader(app, Dispatchers.IO)
    private val load = MutableStateFlow<PdfLoad>(PdfLoad.Loading)
    val state: StateFlow<PdfLoad> = load.asStateFlow()

    private val title = MutableStateFlow("")
    val name: StateFlow<String> = title.asStateFlow()

    var uri: Uri? = null
        private set

    /** Opens [uri] (with [password] if it needs one). Does nothing if it's already open. */
    fun open(
        uri: Uri,
        password: String? = null,
    ) {
        if (uri == this.uri && password == null && load.value !is PdfLoad.Failed) return
        if (uri != this.uri) close()
        this.uri = uri
        load.value = PdfLoad.Loading
        viewModelScope.launch {
            title.value = withContext(Dispatchers.IO) { displayName(uri) }
            load.value =
                try {
                    PdfLoad.Ready(loader.openDocument(uri, password))
                } catch (_: PdfPasswordException) {
                    PdfLoad.NeedsPassword(wrong = password != null)
                } catch (_: IOException) {
                    PdfLoad.Failed
                } catch (_: SecurityException) {
                    PdfLoad.Failed // the sender's grant was revoked
                } catch (_: IllegalArgumentException) {
                    PdfLoad.Failed
                } catch (_: IllegalStateException) {
                    PdfLoad.Failed
                }
        }
    }

    private fun displayName(uri: Uri): String =
        try {
            getApplication<Application>()
                .contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } ?: uri.lastPathSegment.orEmpty()

    private fun close() {
        (load.value as? PdfLoad.Ready)?.document?.close()
    }

    override fun onCleared() = close()
}
