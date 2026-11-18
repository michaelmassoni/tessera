package io.github.michaelmassoni.tessera.core.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * The settings activity of a Tessera app. An app subclasses it, says what its launcher icon looks
 * like, and adds its own settings above the shared ones:
 *
 * ```
 * class SettingsActivity : TesseraSettingsActivity() {
 *     override val launcherIcon = LauncherIcon.of(R.drawable.ic_launcher_foreground, ...)
 *     @Composable override fun ColumnScope.AppSettings() { ... }
 * }
 * ```
 *
 * Declare it in the manifest with an `android.intent.action.APPLICATION_PREFERENCES` filter so
 * system Settings > Apps links to it.
 */
abstract class TesseraSettingsActivity : ComponentActivity() {
    protected abstract val launcherIcon: LauncherIcon

    /** The app's own settings, shown first. */
    @Composable
    protected open fun ColumnScope.AppSettings() = Unit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = TesseraSettings.get(this)
        val icons = AppIcons(this, launcherIcon)
        val (defaultBg, defaultFg) = launcherIcon.colors.getValue(IconStyle.Default)
        val appForeground = getColor(defaultFg)
        val appBackground = getColor(defaultBg)
        val resolveIcon = { i: IconSettings -> i.withAppColors(appForeground, appBackground) }
        val about = AboutInfo(versionName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty())
        setTesseraContent {
            val shared by settings.shared.collectAsStateWithLifecycle()
            val storedIcon by settings.icon.collectAsStateWithLifecycle()
            val icon = resolveIcon(storedIcon)
            var pinned by remember { mutableStateOf(false) }
            LifecycleResumeEffect(Unit) {
                pinned = icons.hasPinnedCustom()
                onPauseOrDispose { }
            }
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
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
                onBack = ::finish,
                snackbar = snackbar,
                appSettings = { AppSettings() },
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
    }
}
