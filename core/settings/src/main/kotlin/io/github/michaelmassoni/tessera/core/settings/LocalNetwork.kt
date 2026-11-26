package io.github.michaelmassoni.tessera.core.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

/**
 * Android 17 (API 37) blocks apps from the local network until the person allows "Nearby
 * devices" (`ACCESS_LOCAL_NETWORK`). Apps ask only when the server the person uses is on their
 * network: at sign-in, and on launch for an account made before (docs/decisions.md).
 */
object LocalNetwork {
    // Manifest.permission.ACCESS_LOCAL_NETWORK, spelled out: the constant is API 37 and is only used from there.
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Whether this app may reach the local network (always, before Android 17). */
    fun granted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
            context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether [address] (a URL or a bare host) names a machine on the local network, judging by
     * the name alone. Loopback is not the network, so it's false for `localhost`.
     */
    fun isLocal(address: String): Boolean {
        val host = hostOf(address) ?: return false
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        if (h == "localhost" || h == "::1" || h.startsWith("127.")) return false
        return ServerAddress.isPrivateHost(h)
    }

    /** Whether reaching [address] needs the permission, which isn't granted (see [resolvesLocal]). */
    suspend fun blocks(
        context: Context,
        address: String?,
    ): Boolean = address != null && !granted(context) && withContext(Dispatchers.IO) { resolvesLocal(address) }

    /**
     * Like [isLocal], and also true when an internet-style name resolves to a local address: split
     * DNS (nas.example.com at home), or a reverse proxy whose public name points at a Tailscale
     * address. Blocking: call off the main thread.
     */
    fun resolvesLocal(address: String): Boolean {
        if (isLocal(address)) return true
        val host = hostOf(address) ?: return false
        val found = runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
        return found.any { isLocal(it) }
    }

    fun isLocal(address: InetAddress): Boolean {
        if (address.isLoopbackAddress) return false
        if (address.isSiteLocalAddress || address.isLinkLocalAddress) return true
        val b = address.address
        return if (address is Inet6Address) {
            (b[0].toInt() and 0xfe) == 0xfc // fc00::/7, unique local
        } else {
            // 100.64.0.0/10, shared address space (Tailscale and carrier NAT).
            (b[0].toInt() and 0xff) == 100 && (b[1].toInt() and 0xc0) == 64
        }
    }

    /** This app's page in system Settings, where a permission denied for good is allowed. */
    fun appSettings(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun hostOf(address: String): String? {
        val text = address.trim().ifEmpty { return null }
        val withScheme = if (text.contains("://")) text else "https://$text"
        return runCatching { URI(withScheme).host }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}

/** Asks for [LocalNetwork.PERMISSION] from a screen; see [rememberLocalNetworkAccess]. */
class LocalNetworkAccess internal constructor(
    private val context: Context,
) {
    internal var launcher: ActivityResultLauncher<String>? = null
    private var waiting: CompletableDeferred<Boolean>? = null

    /** Whether the permission is granted, kept current (it may be allowed in system Settings meanwhile). */
    var granted by mutableStateOf(LocalNetwork.granted(context))
        private set

    internal fun refresh() {
        granted = LocalNetwork.granted(context)
    }

    internal fun answer(granted: Boolean) {
        refresh()
        waiting?.complete(granted)
        waiting = null
    }

    /**
     * True when [address] can be reached: it isn't on the local network, the permission is
     * granted, or the person grants it now. False when they refuse (or refused for good).
     */
    suspend fun allow(address: String): Boolean {
        if (LocalNetwork.granted(context)) return true
        if (!withContext(Dispatchers.IO) { LocalNetwork.resolvesLocal(address) }) return true
        return ask()
    }

    /** Shows the system's question; true when granted. */
    suspend fun ask(): Boolean {
        if (LocalNetwork.granted(context)) return true
        val launch = launcher ?: return false
        val answer = CompletableDeferred<Boolean>()
        waiting?.complete(false)
        waiting = answer
        launch.launch(LocalNetwork.PERMISSION)
        return answer.await()
    }
}

/** Whether [address] is on the local network (see [LocalNetwork.resolvesLocal]); false until known. */
@Composable
fun rememberIsLocal(address: String?): Boolean {
    val local by produceState(false, address) {
        value = address != null && withContext(Dispatchers.IO) { LocalNetwork.resolvesLocal(address) }
    }
    return local
}

@Composable
fun rememberLocalNetworkAccess(): LocalNetworkAccess {
    val context = LocalContext.current
    val access = remember { LocalNetworkAccess(context.applicationContext) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { access.answer(it) }
    SideEffect { access.launcher = launcher }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { access.refresh() }
    return access
}

/**
 * For an account made before Android 17 asked: once per screen (not on rotation), asks for the
 * permission when [address], the server in use, is on the local network. [onGranted] runs when
 * it was granted just now (to sync again).
 */
@Composable
fun AskForLocalNetwork(
    address: String?,
    onGranted: () -> Unit = {},
) {
    val access = rememberLocalNetworkAccess()
    var asked by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(address) {
        if (address == null || asked || LocalNetwork.granted(context)) return@LaunchedEffect
        asked = true
        if (access.allow(address) && LocalNetwork.granted(context)) onGranted()
    }
}

/** Under a sign-in form: the permission was refused, with the way to allow it. */
@Composable
fun LocalNetworkDenied(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        Text(
            stringResource(R.string.local_network_denied),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        TextButton(onClick = { context.startActivity(LocalNetwork.appSettings(context)) }) {
            Text(stringResource(R.string.local_network_settings))
        }
    }
}
