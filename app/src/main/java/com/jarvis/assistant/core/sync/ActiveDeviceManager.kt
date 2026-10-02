package com.jarvis.assistant.core.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class DeviceRegistry(private val context: Context) {

    private val _knownDevices = MutableStateFlow<List<DeviceInfo>>(
        listOf(
            DeviceInfo(
                deviceId = "tablet_s5e",
                deviceName = "Samsung Galaxy Tab S5e",
                platform = DevicePlatform.ANDROID_TABLET,
                osVersion = "Android 11 (One UI 3.1)",
                isOnline = true,
                capabilities = listOf("voice", "accessibility", "camera", "touch", "battery")
            ),
            DeviceInfo(
                deviceId = "windows_pc_01",
                deviceName = "Windows 10 Desktop",
                platform = DevicePlatform.WINDOWS_DESKTOP,
                osVersion = "Windows 10 Pro (x64)",
                isOnline = false, // Set to true when connected via sync hub
                capabilities = listOf("powershell", "app_launcher", "filesystem", "browser", "media", "keyboard_mouse")
            )
        )
    )
    val knownDevices: StateFlow<List<DeviceInfo>> = _knownDevices.asStateFlow()

    fun updateDeviceStatus(deviceId: String, isOnline: Boolean) {
        val list = _knownDevices.value.map {
            if (it.deviceId == deviceId) it.copy(isOnline = isOnline, lastSeenTimestamp = System.currentTimeMillis())
            else it
        }
        _knownDevices.value = list
    }

    fun getDevice(platform: DevicePlatform): DeviceInfo? {
        return _knownDevices.value.find { it.platform == platform }
    }
}

class ActiveDeviceManager(
    private val context: Context,
    private val deviceRegistry: DeviceRegistry
) {
    private val _activeTargetPlatform = MutableStateFlow(DevicePlatform.ANDROID_TABLET)
    val activeTargetPlatform: StateFlow<DevicePlatform> = _activeTargetPlatform.asStateFlow()

    val activeDeviceName: String
        get() = when (_activeTargetPlatform.value) {
            DevicePlatform.ANDROID_TABLET -> "Samsung Galaxy Tab S5e"
            DevicePlatform.WINDOWS_DESKTOP -> "Windows 10 Desktop"
            DevicePlatform.WEB_DASHBOARD -> "Web Workbench"
        }

    fun isTargetDesktop(): Boolean = _activeTargetPlatform.value == DevicePlatform.WINDOWS_DESKTOP

    fun isTargetTablet(): Boolean = _activeTargetPlatform.value == DevicePlatform.ANDROID_TABLET

    /**
     * Checks if a natural language query is a request to switch active device target.
     * Returns spoken confirmation if handled, or null if not a switch command.
     */
    fun checkAndHandleDeviceSwitchCommand(query: String): String? {
        val q = query.lowercase(Locale.ROOT).trim()

        // Commands to target Tablet
        val tabletPatterns = listOf(
            "work on the tablet", "work on tablet", "switch to tablet", "switch to the tablet",
            "use the tab", "use the tablet", "control the tablet", "tablet mode", "on the tablet"
        )
        if (tabletPatterns.any { q.contains(it) }) {
            _activeTargetPlatform.value = DevicePlatform.ANDROID_TABLET
            return "Tablet selected as active target."
        }

        // Commands to target Windows Desktop
        val desktopPatterns = listOf(
            "work on the desktop", "work on desktop", "switch to desktop", "switch to the desktop",
            "use the computer", "use the pc", "switch to pc", "control the desktop", "desktop mode", "on the desktop"
        )
        if (desktopPatterns.any { q.contains(it) }) {
            _activeTargetPlatform.value = DevicePlatform.WINDOWS_DESKTOP
            val desktop = deviceRegistry.getDevice(DevicePlatform.WINDOWS_DESKTOP)
            return if (desktop?.isOnline == true) {
                "Desktop selected. Subsequent system actions will execute on your Windows 10 PC."
            } else {
                "Desktop selected. Note: Windows client is currently reporting offline in the sync registry."
            }
        }

        return null
    }

    fun setTarget(platform: DevicePlatform) {
        _activeTargetPlatform.value = platform
    }
}
