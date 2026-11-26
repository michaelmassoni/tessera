package io.github.michaelmassoni.tessera.music.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Finds cast devices on the Wi-Fi (mDNS `_googlecast._tcp`) while [start]ed. Each one found is
 * resolved for its address and its TXT record: `id`, `fn` (its name), `md` (model) and `ca`
 * (capabilities; bit 0 is video out).
 */
class CastDiscovery(
    context: Context,
) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val executor = ContextCompat.getMainExecutor(context)
    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private var listener: NsdManager.DiscoveryListener? = null
    private val callbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()

    // Before Android 14 one resolve runs at a time; the rest wait here.
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    /** Device name by mDNS service name, to forget a device when its service goes. */
    private val byService = mutableMapOf<String, String>()

    fun start() {
        if (listener != null) return
        val l =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) {
                    listener = null
                }

                override fun onStopDiscoveryFailed(
                    serviceType: String,
                    errorCode: Int,
                ) = Unit

                override fun onServiceFound(info: NsdServiceInfo) = executor.execute { resolve(info) }

                override fun onServiceLost(info: NsdServiceInfo) = executor.execute { lost(info.serviceName) }
            }
        listener = l
        try {
            nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (_: IllegalArgumentException) {
            listener = null
        } catch (_: SecurityException) {
            listener = null // no local network permission
        }
    }

    fun stop() {
        listener?.let { l -> runCatching { nsd.stopServiceDiscovery(l) } }
        listener = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            callbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }
        callbacks.clear()
        pending.clear()
        // What was found stays listed for the next look, which refreshes it.
    }

    private fun resolve(info: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            watch(info)
        } else {
            pending.addLast(info)
            resolveNext()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun watch(info: NsdServiceInfo) {
        if (info.serviceName in callbacks) return
        val callback =
            object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    callbacks.remove(info.serviceName)
                }

                override fun onServiceUpdated(updated: NsdServiceInfo) {
                    val address = updated.hostAddresses.firstOrNull { it is Inet4Address } ?: updated.hostAddresses.firstOrNull()
                    address?.let { found(updated, it) }
                }

                override fun onServiceLost() = lost(info.serviceName)

                override fun onServiceInfoCallbackUnregistered() = Unit
            }
        callbacks[info.serviceName] = callback
        try {
            nsd.registerServiceInfoCallback(info, executor, callback)
        } catch (_: IllegalArgumentException) {
            callbacks.remove(info.serviceName)
        }
    }

    // NsdManager.resolveService and NsdServiceInfo.host are deprecated from Android 14, where
    // [watch] is used instead; before it they're the only way.
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving) return
        val info = pending.removeFirstOrNull() ?: return
        resolving = true
        nsd.resolveService(
            info,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo,
                    errorCode: Int,
                ) = executor.execute {
                    resolving = false
                    resolveNext()
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) =
                    executor.execute {
                        resolving = false
                        resolved.host?.let { found(resolved, it) }
                        resolveNext()
                    }
            },
        )
    }

    private fun found(
        info: NsdServiceInfo,
        address: InetAddress,
    ) {
        val txt = info.attributes

        fun attr(key: String) = txt[key]?.let { String(it, Charsets.UTF_8) }?.takeIf { it.isNotBlank() }
        val id = attr("id") ?: info.serviceName
        val device =
            CastDevice(
                id = id,
                name = attr("fn") ?: info.serviceName,
                model = attr("md").orEmpty(),
                host = address,
                port = info.port.takeIf { it > 0 } ?: DEFAULT_PORT,
                video = (attr("ca")?.toIntOrNull() ?: 1) and 1 != 0,
            )
        byService[info.serviceName] = id
        _devices.update { list -> (list.filterNot { it.id == id } + device).sortedBy { it.name.lowercase() } }
    }

    private fun lost(serviceName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            callbacks.remove(serviceName)?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }
        val id = byService.remove(serviceName) ?: return
        _devices.update { list -> list.filterNot { it.id == id } }
    }

    private companion object {
        const val SERVICE = "_googlecast._tcp"
        const val DEFAULT_PORT = 8009
    }
}
