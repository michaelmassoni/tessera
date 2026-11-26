package io.github.michaelmassoni.tessera.core.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * The settings activity of a Tessera app. An app subclasses it, says what its launcher icon looks
 * like, and lists its own categories of settings, which come before the shared ones (Appearance,
 * Backup, About):
 *
 * ```
 * class SettingsActivity : TesseraSettingsActivity() {
 *     override val launcherIcon = LauncherIcon.of(R.drawable.ic_launcher_foreground, ...)
 *     @Composable override fun categories() = listOf(
 *         SettingsCategory("sync", stringResource(R.string.sync), DesignR.drawable.ic_sync, summary) { ... },
 *     )
 * }
 * ```
 *
 * Start it with [SettingsCategory.intent] to open one category directly; back then leaves.
 *
 * Declare it in the manifest with an `android.intent.action.APPLICATION_PREFERENCES` filter so
 * system Settings > Apps links to it.
 */
abstract class TesseraSettingsActivity : ComponentActivity() {
    protected abstract val launcherIcon: LauncherIcon

    // ---- Backup ----

    private val backups by lazy { BackupFlows(this) }
    private var pendingRestore by mutableStateOf<PendingRestore?>(null)
    private var working by mutableStateOf(false)
    private val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    private fun busy(block: suspend () -> Unit) {
        working = true
        lifecycleScope.launch {
            try {
                block()
            } finally {
                working = false
            }
        }
    }

    private val createBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument(ZIP)) { uri ->
            uri ?: return@registerForActivityResult
            busy { messages.emit(getString(if (backups.backUp(uri)) R.string.backup_done else R.string.backup_failed)) }
        }

    private val createSuiteBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument(ZIP)) { uri ->
            uri ?: return@registerForActivityResult
            busy {
                val count = backups.backUpAll(uri)
                messages.emit(
                    if (count <
                        0
                    ) {
                        getString(R.string.backup_failed)
                    } else {
                        resources.getQuantityString(R.plurals.backup_all_done, count, count)
                    },
                )
            }
        }

    private val openBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            busy {
                runCatching { backups.inspect(uri) }
                    .onSuccess { pendingRestore = it }
                    .onFailure { messages.emit(backups.message(it)) }
            }
        }

    private fun confirmRestore(pending: PendingRestore) {
        pendingRestore = null
        busy {
            when (pending) {
                is PendingRestore.One -> {
                    runCatching { backups.restore(pending.uri) }
                        .onSuccess { AppBackup.relaunch(applicationContext) }
                        .onFailure { messages.emit(backups.message(it)) }
                }

                is PendingRestore.All -> {
                    val (count, self) = backups.restoreAll(pending.uri)
                    if (self) {
                        AppBackup.relaunch(applicationContext)
                    } else {
                        messages.emit(resources.getQuantityString(R.plurals.restore_all_done, count, count))
                    }
                }
            }
        }
    }

    /** The app's own categories, listed before Appearance, Backup and About. */
    @Composable
    protected open fun categories(): List<SettingsCategory> = emptyList()

    /**
     * Import and export in the app's own formats (OPML, JSON…), shown on the Backup page under
     * "Import & export". Null when the app has none.
     */
    protected open val importExport: (@Composable ColumnScope.() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = TesseraSettings.get(this)
        val icons = AppIcons(this, launcherIcon)
        val (defaultBg, defaultFg) = launcherIcon.colors.getValue(IconStyle.Default)
        val appForeground = getColor(defaultFg)
        val appBackground = getColor(defaultBg)
        val resolveIcon = { i: IconSettings -> i.withAppColors(appForeground, appBackground) }
        val about = AboutInfo(versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty())
        val start = intent.getStringExtra(SettingsCategory.EXTRA)
        setTesseraContent {
            val shared by settings.shared.collectAsStateWithLifecycle()
            val storedIcon by settings.icon.collectAsStateWithLifecycle()
            val icon = resolveIcon(storedIcon)
            var pinned by remember { mutableStateOf(false) }
            LifecycleResumeEffect(Unit) {
                pinned = icons.hasPinnedCustom()
                onPauseOrDispose { }
            }
            var category by rememberSaveable { mutableStateOf(start) }
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) { messages.collect { snackbar.showSnackbar(it) } }
            pendingRestore?.let { pending ->
                RestoreDialog(pending, applicationInfo.loadLabel(packageManager).toString(), { confirmRestore(pending) }, {
                    pendingRestore =
                        null
                })
            }
            if (working) WorkingDialog()
            val actions =
                remember {
                    Actions(settings, icons, resolveIcon) { message -> scope.launch { snackbar.showSnackbar(getString(message)) } }
                }
            SettingsScreen(
                shared = shared,
                icon = icon,
                launcherIcon = launcherIcon,
                customPinned = pinned,
                about = about,
                actions = actions,
                category = category,
                onOpen = { category = it },
                // Opened on one category from elsewhere: back returns there.
                onClose = { if (start != null) finish() else category = null },
                onBack = ::finish,
                snackbar = snackbar,
                categories = categories(),
                importExport = importExport,
            )
        }
    }

    private inner class Actions(
        private val settings: TesseraSettings,
        private val icons: AppIcons,
        private val resolve: (IconSettings) -> IconSettings,
        private val message: (Int) -> Unit,
    ) : SettingsActions {
        override fun setTheme(theme: ThemeSettings) = settings.setTheme(theme)

        override fun setSyncAll(enabled: Boolean) = settings.setSyncAll(enabled)

        override fun setIconStyle(style: IconStyle) {
            // The launcher alias switches when the app goes to the background; see setTesseraContent.
            settings.setIcon(settings.icon.value.copy(style = style))
        }

        override fun setCustomIcon(
            foreground: Int,
            background: Int,
        ) {
            settings.setIcon(settings.icon.value.copy(foreground = foreground, background = background, colorsChosen = true))
            // An icon already on the home screen follows the new colours straight away.
            if (icons.hasPinnedCustom()) pinCustomIcon()
        }

        override fun pinCustomIcon() {
            val icon = resolve(settings.icon.value)
            val result = icons.pinCustom(icon.foreground, icon.background, applicationInfo.loadLabel(packageManager))
            message(
                when (result) {
                    AppIcons.PinResult.Requested -> return
                    AppIcons.PinResult.Updated -> R.string.settings_icon_updated
                    AppIcons.PinResult.Unsupported -> R.string.settings_icon_unsupported
                },
            )
        }

        override fun backUp() = createBackup.launch(backups.fileName(all = false))

        override fun restore() = openBackup.launch(arrayOf(ZIP, "application/octet-stream"))

        override fun backUpAll() = createSuiteBackup.launch(backups.fileName(all = true))

        override fun restoreAll() = openBackup.launch(arrayOf(ZIP, "application/octet-stream"))

        override fun openSource() {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this@TesseraSettingsActivity, R.string.settings_no_browser, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private companion object {
        const val SOURCE_URL = "https://github.com/michaelmassoni/tessera"
        const val ZIP = "application/zip"
    }
}
