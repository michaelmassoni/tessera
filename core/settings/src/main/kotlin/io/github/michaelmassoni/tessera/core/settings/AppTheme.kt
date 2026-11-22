package io.github.michaelmassoni.tessera.core.settings

import android.content.pm.ApplicationInfo
import android.os.StrictMode
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
 * Debug builds only: logs disk reads and writes, network use and slow calls on the main thread,
 * and leaked closeables, so main-thread I/O shows up while developing (`adb logcat -s StrictMode`).
 * Release builds are never affected.
 */
private fun strictMode() {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy
            .Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .detectCustomSlowCalls()
            .penaltyLog()
            .build(),
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy
            .Builder()
            .detectLeakedClosableObjects()
            .detectLeakedRegistrationObjects()
            .penaltyLog()
            .build(),
    )
}

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
        if (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) strictMode()
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
