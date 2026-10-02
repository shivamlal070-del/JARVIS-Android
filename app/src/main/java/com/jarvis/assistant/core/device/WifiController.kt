package com.jarvis.assistant.core.device

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings

data class WifiStateInfo(
    val isEnabled: Boolean,
    val isConnected: Boolean,
    val ssid: String?,
    val linkSpeedMbps: Int,
    val signalStrengthDbm: Int
)

data class WifiActionResult(
    val success: Boolean,
    val openedSettings: Boolean,
    val message: String
)

/**
 * WifiController queries real Wi-Fi state.
 * On Android 10+ (API 29+) direct setWifiEnabled is restricted by Google policy for security;
 * dispatches directly to Settings.ACTION_WIFI_SETTINGS without faking direct toggling.
 */
class WifiController(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun getWifiState(): WifiStateInfo {
        if (wifiManager == null) return WifiStateInfo(false, false, null, 0, 0)

        val isEnabled = wifiManager.isWifiEnabled
        val connectionInfo: WifiInfo? = wifiManager.connectionInfo
        val isConnected = connectionInfo != null && connectionInfo.networkId != -1
        val rawSsid = connectionInfo?.ssid?.replace("\"", "")
        val ssid = if (rawSsid == "<unknown ssid>" || rawSsid.isNullOrBlank()) null else rawSsid
        val speed = connectionInfo?.linkSpeed ?: 0
        val rssi = connectionInfo?.rssi ?: -100

        return WifiStateInfo(isEnabled, isConnected, ssid, speed, rssi)
    }

    fun openWifiSettings(): WifiActionResult {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            WifiActionResult(true, true, "Opening Android Wi-Fi settings.")
        } catch (e: Exception) {
            WifiActionResult(false, false, "Unable to open Wi-Fi settings: ${e.localizedMessage}")
        }
    }

    fun getSpokenWifiStatus(): String {
        val state = getWifiState()
        return if (!state.isEnabled) {
            "Wi-Fi is currently turned off on your Galaxy Tab S5e."
        } else if (state.isConnected && state.ssid != null) {
            "Wi-Fi is connected to ${state.ssid} at ${state.linkSpeedMbps} Megabits per second."
        } else {
            "Wi-Fi is enabled but not connected to any network."
        }
    }
}
