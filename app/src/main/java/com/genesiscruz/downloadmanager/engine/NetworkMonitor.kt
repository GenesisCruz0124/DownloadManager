package com.genesiscruz.downloadmanager.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkState(val connected: Boolean, val unmetered: Boolean)

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val state: Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                trySend(capabilities.toState())
            }

            override fun onLost(network: Network) {
                trySend(NetworkState(connected = false, unmetered = false))
            }
        }
        trySend(currentState())
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback
        )
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    fun currentState(): NetworkState {
        val capabilities = connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
            ?: return NetworkState(connected = false, unmetered = false)
        return capabilities.toState()
    }

    private fun NetworkCapabilities.toState() = NetworkState(
        connected = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        unmetered = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    )
}
