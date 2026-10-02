package com.jarvis.assistant.core.sync

import android.content.Context
import android.os.Build
import java.util.UUID

enum class DevicePlatform {
    ANDROID_TABLET,
    WINDOWS_DESKTOP,
    WEB_DASHBOARD
}

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val platform: DevicePlatform,
    val osVersion: String,
    val isOnline: Boolean = true,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val capabilities: List<String> = listOf("voice", "screen", "accessibility", "camera")
)

class DeviceIdentityManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("jarvis_device_identity", Context.MODE_PRIVATE)

    val currentDeviceId: String
        get() {
            var id = prefs.getString("device_id", null)
            if (id == null) {
                id = "android_tab_" + UUID.randomUUID().toString().take(8)
                prefs.edit().putString("device_id", id).apply()
            }
            return id
        }

    val currentDeviceName: String
        get() = prefs.getString("device_name", "Samsung Galaxy Tab S5e") ?: "Samsung Galaxy Tab S5e"

    val currentDeviceInfo: DeviceInfo
        get() = DeviceInfo(
            deviceId = currentDeviceId,
            deviceName = currentDeviceName,
            platform = DevicePlatform.ANDROID_TABLET,
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            isOnline = true,
            capabilities = listOf("voice", "accessibility", "app_launcher", "camera", "media_projection", "bluetooth")
        )
}
