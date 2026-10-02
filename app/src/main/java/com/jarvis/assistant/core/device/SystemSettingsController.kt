package com.jarvis.assistant.core.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * SystemSettingsController dispatches intents to legitimate Android system settings pages.
 * Never fakes system changes when Android requires user interaction.
 */
class SystemSettingsController(private val context: Context) {

    fun openAccessibilitySettings(): Boolean = launchIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    fun openWifiSettings(): Boolean = launchIntent(Settings.ACTION_WIFI_SETTINGS)
    fun openBluetoothSettings(): Boolean = launchIntent(Settings.ACTION_BLUETOOTH_SETTINGS)
    fun openSoundSettings(): Boolean = launchIntent(Settings.ACTION_SOUND_SETTINGS)
    fun openDisplaySettings(): Boolean = launchIntent(Settings.ACTION_DISPLAY_SETTINGS)
    fun openApplicationDetailsSettings(): Boolean = launchIntent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    fun openWriteSettingsPermission(): Boolean = launchIntent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    fun openNotificationListenerSettings(): Boolean = launchIntent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    fun openDeviceAdminSettings(): Boolean = launchIntent(Settings.ACTION_SECURITY_SETTINGS)
    fun openMainSettings(): Boolean = launchIntent(Settings.ACTION_SETTINGS)

    private fun launchIntent(action: String, data: Uri? = null): Boolean {
        return try {
            val intent = Intent(action).apply {
                if (data != null) this.data = data
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
