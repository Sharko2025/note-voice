package com.sitpro.notevoice.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

/** Whether the phone has a validated connection; drives the offline banner and "will send when online". */
class NetworkMonitor(context: Context, scope: CoroutineScope) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    private fun isOnlineNow(): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    val online: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            }

            override fun onLost(network: Network) {
                // The default network is gone; asking the system right now can still report the old one.
                trySend(false)
            }
        }
        trySend(isOnlineNow())
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.stateIn(scope, SharingStarted.Eagerly, isOnlineNow())
}
