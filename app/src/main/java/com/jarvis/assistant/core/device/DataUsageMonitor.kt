package com.jarvis.assistant.core.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class DataUsageMonitor(private val context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isConnectedToWifi(): Boolean {
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isConnectedToCellular(): Boolean {
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun getNetworkStatusSpoken(): String {
        return when {
            isConnectedToWifi() -> "You are connected to Wi-Fi."
            isConnectedToCellular() -> "You are currently on mobile data. Large downloads may consume your mobile plan."
            else -> "Your tablet is currently offline."
        }
    }
}
