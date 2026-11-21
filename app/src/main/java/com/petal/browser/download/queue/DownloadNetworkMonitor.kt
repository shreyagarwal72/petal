package com.petal.browser.download.queue

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tracks the default network (online / metered) with a [ConnectivityManager.NetworkCallback]. */
object DownloadNetworkMonitor {
    private const val TAG = "DownloadNetworkMonitor"

    private val _state = MutableStateFlow(QueueScheduler.Network(online = true, metered = false))
    val state: StateFlow<QueueScheduler.Network> = _state.asStateFlow()

    @Volatile private var registered = false

    @JvmStatic
    @Synchronized
    fun start(context: Context) {
        if (registered) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            _state.value = snapshot(cm)
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _state.value = snapshot(cm) }
                override fun onLost(network: Network) { _state.value = snapshot(cm).let { if (cm.activeNetwork == null) it.copy(online = false) else it } }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { _state.value = snapshot(cm) }
            })
            registered = true
        } catch (t: Throwable) {
            Log.w(TAG, "Network callback unavailable: ${t.message}")
        }
    }

    private fun snapshot(cm: ConnectivityManager): QueueScheduler.Network {
        val caps = try { cm.getNetworkCapabilities(cm.activeNetwork) } catch (_: Throwable) { null }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) != true
        return QueueScheduler.Network(online, metered)
    }
}
