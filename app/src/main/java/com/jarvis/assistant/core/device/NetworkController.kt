package com.jarvis.assistant.core.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

data class NetworkStatusInfo(
    val isConnected: Boolean,
    val connectionType: String, // WI-FI, CELLULAR, ETHERNET, NONE
    val hasInternetCapability: Boolean,
    val isMetered: Boolean,
    val isGeminiReachable: Boolean
)

/**
 * NetworkController queries legitimate Android ConnectivityManager for connection type,
 * metered bandwidth state, and online reachability.
 */
class NetworkController(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun getNetworkStatus(): NetworkStatusInfo {
        if (connectivityManager == null) {
            return NetworkStatusInfo(false, "NONE", false, false, false)
        }

        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)

        if (caps == null) {
            return NetworkStatusInfo(false, "NONE", false, false, false)
        }

        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WI-FI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR (4G/5G)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }

        return NetworkStatusInfo(
            isConnected = hasInternet && isValidated,
            connectionType = type,
            hasInternetCapability = hasInternet,
            isMetered = isMetered,
            isGeminiReachable = hasInternet && isValidated
        )
    }

    fun getSpokenNetworkStatus(): String {
        val status = getNetworkStatus()
        return if (status.isConnected) {
            "You are connected to the internet via ${status.connectionType}. Gemini AI reasoning is active and reachable."
        } else {
            "Internet connection is currently unavailable. Operating in local offline voice mode."
        }
    }
}
