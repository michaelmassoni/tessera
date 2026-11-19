package io.github.michaelmassoni.tessera.core.settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.michaelmassoni.tessera.core.design.TesseraTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * [TesseraTheme] with the user's appearance settings. Light/dark comes from the configuration,
 * which follows the per-app night mode set through `UiModeManager`, so the window background,
 * splash screen and system bars agree with the content.
 */
@Composable
fun TesseraAppTheme(content: @Composable () -> Unit) {
    val settings = TesseraSettings.get(LocalContext.current)
    val shared by settings.shared.collectAsStateWithLifecycle()
    TesseraTheme(
        dynamicColor = shared.theme.dynamicColor,
        pureBlack = shared.theme.pureBlack,
        content = content,
    )
}

private val started = AtomicBoolean(false)

/**
 * The standard start of a Tessera activity: edge-to-edge and the app theme. Once per process it
 * also picks up a shared theme from the other Tessera apps and arranges for a newly chosen
 * launcher icon to be switched in when the app goes to the background. Call from `onCreate`
 * in place of `enableEdgeToEdge()` + `setContent { TesseraTheme { ... } }`.
 */
fun ComponentActivity.setTesseraContent(content: @Composable () -> Unit) {
    enableEdgeToEdge()
    if (started.compareAndSet(false, true)) {
        val app = applicationContext
        val settings = TesseraSettings.get(app)
        thread(name = "tessera-settings-pull") {
            val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode }.getOrDefault(-1L)
            if (settings.needsPull(version)) settings.pullFromPeers(version)
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) applyLauncherAlias(app, settings.icon.value.style)
            },
        )
    }
    setContent { TesseraAppTheme(content) }
}
