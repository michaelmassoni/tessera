package io.github.michaelmassoni.tessera.files.viewer

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michaelmassoni.tessera.core.settings.setTesseraContent
import io.github.michaelmassoni.tessera.files.R

/**
 * The PDF viewer (docs/files.md, "PDF viewer"). Opened by Files for PDFs, and by any app through
 * ACTION_VIEW: it needs no storage permission (the sender grants that one file) and skips Files'
 * first-run setup.
 */
class ViewerActivity : ComponentActivity() {
    private val viewModel: ViewerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }
        viewModel.open(uri)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        setTesseraContent {
            val load by viewModel.state.collectAsStateWithLifecycle()
            val name by viewModel.name.collectAsStateWithLifecycle()
            var darkPages by remember { mutableStateOf(prefs.getBoolean(KEY_DARK_PAGES, false)) }
            ViewerScreen(
                name = name,
                load = load,
                darkPages = darkPages,
                actions =
                    object : ViewerActions {
                        override fun onClose() = finish()

                        override fun onShare() = share()

                        override fun onOpenWith() = openWith()

                        override fun onPassword(password: String) {
                            viewModel.uri?.let { viewModel.open(it, password) }
                        }

                        override fun onDarkPages(dark: Boolean) {
                            prefs.edit { putBoolean(KEY_DARK_PAGES, dark) }
                            darkPages = dark
                        }
                    },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(viewModel::open)
    }

    private fun share() {
        val uri = viewModel.uri ?: return
        val send =
            Intent(Intent.ACTION_SEND)
                .setType(PDF)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, getString(R.string.action_share)))
    }

    /** Another app's viewer: the chooser leaves this one out. */
    private fun openWith() {
        val uri = viewModel.uri ?: return
        val view =
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, PDF)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser =
            Intent
                .createChooser(view, getString(R.string.viewer_open_with))
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(this, ViewerActivity::class.java)))
        try {
            startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            // Nothing else can open it; the chooser says so itself on current Android.
        }
    }

    companion object {
        const val PDF = "application/pdf"
        private const val PREFS = "viewer"
        private const val KEY_DARK_PAGES = "dark_pages"

        /** Opens [uri] in the viewer, from inside Files. */
        fun intent(
            context: Context,
            uri: Uri,
        ): Intent =
            Intent(context, ViewerActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setDataAndType(uri, PDF)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
