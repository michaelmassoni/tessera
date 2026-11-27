package io.github.michaelmassoni.tessera.music.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the phone has an internet connection, for greying out songs that can't play offline. */
object Connectivity {
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _unmetered = MutableStateFlow(false)

    /** On Wi-Fi (or another network that isn't charged by the byte). */
    val unmetered: StateFlow<Boolean> = _unmetered.asStateFlow()

    @Volatile private var started = false

    /** Starts watching (once per process). */
    fun start(context: Context) {
        if (started) return
        started = true
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        _online.value =
            manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _unmetered.value =
            manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
        manager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    _online.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    _unmetered.value = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }

                override fun onLost(network: Network) {
                    _online.value = false
                    _unmetered.value = false
                }
            },
        )
    }
}
