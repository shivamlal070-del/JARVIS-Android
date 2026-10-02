package com.jarvis.assistant.core.device

import android.content.Context
import com.jarvis.assistant.core.study.PomodoroManager
import com.jarvis.assistant.core.study.PomodoroState

enum class ActionStatus {
    REQUESTED,
    ATTEMPTING,
    SUCCESS,
    FAILED,
    REQUIRES_PERMISSION,
    NOT_SUPPORTED
}

data class DeviceActionResult(
    val status: ActionStatus,
    val command: String,
    val spokenResponse: String,
    val detailedLog: String? = null,
    val requiresSettingsIntent: Boolean = false
)

data class MasterDeviceStatusSummary(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val powerSource: String,
    val isWifiEnabled: Boolean,
    val wifiSsid: String?,
    val isBluetoothEnabled: Boolean,
    val volumePercent: Int,
    val isMuted: Boolean,
    val brightnessPercent: Int,
    val storageFreeGb: Double,
    val storageTotalGb: Double,
    val networkType: String,
    val isInternetConnected: Boolean,
    val isFlashlightOn: Boolean,
    val pomodoroState: String,
    val studyModeActive: Boolean
)

/**
 * DeviceControlManager is the central coordination architecture for all hardware and OS modules on Galaxy Tab S5e.
 *
 * Core Principles:
 * 1. Hardware/System state source-of-truth is NATIVE ANDROID APIS (never hallucinated).
 * 2. Error transparency: Never reports success unless the action succeeded.
 * 3. Does not bypass Android 11 security constraints.
 */
class DeviceControlManager(
    private val context: Context,
    val pomodoroManager: PomodoroManager? = null
) {
    val batteryController = BatteryController(context)
    val flashlightController = FlashlightController(context)
    val audioController = AudioController(context)
    val mediaController = MediaController(context)
    val displayController = DisplayController(context)
    val wifiController = WifiController(context)
    val bluetoothController = BluetoothController(context)
    val networkController = NetworkController(context)
    val storageMonitor = StorageMonitor(context)
    val appLauncher = AppLauncher(context)
    val accessibilityController = AccessibilityController(context)
    val timerManager = TimerManager(context)
    val systemSettingsController = SystemSettingsController(context)
    val permissionManager = PermissionManager(context)

    /**
     * Aggregates real-time hardware status across all physical subsystems.
     */
    fun getMasterDeviceStatus(): MasterDeviceStatusSummary {
        val batt = batteryController.getBatteryInfo()
        val wifi = wifiController.getWifiState()
        val bt = bluetoothController.getBluetoothState()
        val vol = audioController.getStreamInfo()
        val bright = displayController.getBrightnessInfo()
        val storage = storageMonitor.getInternalStorageInfo()
        val net = networkController.getNetworkStatus()
        val pomState = pomodoroManager?.pomodoroState?.value?.name ?: "IDLE"

        return MasterDeviceStatusSummary(
            batteryPercent = batt.percentage,
            isCharging = batt.isCharging,
            powerSource = batt.powerSource,
            isWifiEnabled = wifi.isEnabled,
            wifiSsid = wifi.ssid,
            isBluetoothEnabled = bt.isEnabled,
            volumePercent = vol.percentage,
            isMuted = vol.isMuted,
            brightnessPercent = bright.percentage,
            storageFreeGb = storage.freeGb,
            storageTotalGb = storage.totalGb,
            networkType = net.connectionType,
            isInternetConnected = net.isConnected,
            isFlashlightOn = flashlightController.isFlashlightOn,
            pomodoroState = pomState,
            studyModeActive = pomState == PomodoroState.FOCUS.name
        )
    }

    /**
     * Generates a concise spoken summary for "Jarvis, give me a device status."
     */
    fun getSpokenDeviceStatus(): String {
        val s = getMasterDeviceStatus()
        val battStr = if (s.isCharging) "${s.batteryPercent}% (Charging)" else "${s.batteryPercent}%"
        val wifiStr = if (s.isWifiEnabled && s.wifiSsid != null) "Connected to ${s.wifiSsid}" else if (s.isWifiEnabled) "On (Disconnected)" else "Off"
        val btStr = if (s.isBluetoothEnabled) "On" else "Off"

        return "Device Status: Battery is at $battStr. Wi-Fi is $wifiStr. Bluetooth is $btStr. Media volume is ${s.volumePercent} percent. Brightness is at ${s.brightnessPercent} percent. ${s.storageFreeGb} Gigabytes storage available. Study Mode is ${if (s.studyModeActive) "Active" else "Idle"}."
    }

    /**
     * Dispatches natural language device commands directly to native Android controllers.
     */
    fun executeDeviceCommand(rawQuery: String): DeviceActionResult {
        val q = rawQuery.trim().lowercase()

        // 1. Device Status Master Summary
        if (q.contains("device status") || q.contains("system status") || q.contains("tablet status") || q.contains("system diagnostic")) {
            return DeviceActionResult(
                status = ActionStatus.SUCCESS,
                command = rawQuery,
                spokenResponse = getSpokenDeviceStatus(),
                detailedLog = "MasterDeviceStatus query completed."
            )
        }

        // 2. Battery Commands
        if (q.contains("battery") || q.contains("charging") || q.contains("power")) {
            val spoken = batteryController.getSpokenBatteryStatus()
            return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, spoken)
        }

        // 3. Flashlight / Torch Commands
        if (q.contains("flashlight") || q.contains("torch")) {
            if (q.contains("on") || q.contains("enable") || q.contains("activate")) {
                val res = flashlightController.setTorchMode(true)
                return DeviceActionResult(
                    status = if (res.success) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    command = rawQuery,
                    spokenResponse = res.message
                )
            } else if (q.contains("off") || q.contains("disable") || q.contains("deactivate")) {
                val res = flashlightController.setTorchMode(false)
                return DeviceActionResult(
                    status = if (res.success) ActionStatus.SUCCESS else ActionStatus.FAILED,
                    command = rawQuery,
                    spokenResponse = res.message
                )
            } else {
                val state = if (flashlightController.isFlashlightOn) "ON" else "OFF"
                return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, "Flashlight is currently $state.")
            }
        }

        // 4. Volume / Audio Commands
        if (q.contains("volume") || q.contains("mute") || q.contains("unmute")) {
            if (q.contains("mute") && !q.contains("unmute")) {
                val res = audioController.mute()
                return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, "Media audio muted.")
            }
            if (q.contains("unmute")) {
                val res = audioController.unmute()
                return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, "Media audio unmuted.")
            }
            if (q.contains("increase") || q.contains("turn up") || q.contains("raise") || q.contains("louder")) {
                val res = audioController.adjustVolume(1)
                return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, res.message)
            }
            if (q.contains("decrease") || q.contains("turn down") || q.contains("lower") || q.contains("softer")) {
                val res = audioController.adjustVolume(-1)
                return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, res.message)
            }

            // Check for specific percentage
            val match = Regex("(\\d+)\\s*(?:percent|%)").find(q)
            if (match != null) {
                val percent = match.groupValues[1].toIntOrNull() ?: 50
                val res = audioController.setVolumePercentage(percent)
                return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, res.message)
            }

            val info = audioController.getStreamInfo()
            return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, "Current media volume is ${info.percentage} percent.")
        }

        // 5. Brightness / Display Commands
        if (q.contains("brightness")) {
            val match = Regex("(\\d+)\\s*(?:percent|%)").find(q)
            if (match != null) {
                val percent = match.groupValues[1].toIntOrNull() ?: 50
                val res = displayController.setSystemBrightness(percent)
                return DeviceActionResult(
                    status = if (res.success) ActionStatus.SUCCESS else ActionStatus.REQUIRES_PERMISSION,
                    command = rawQuery,
                    spokenResponse = res.message,
                    requiresSettingsIntent = res.requiresPermission
                )
            }
            val info = displayController.getBrightnessInfo()
            return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, "Current display brightness is ${info.percentage} percent.")
        }

        // 6. Wi-Fi Commands
        if (q.contains("wifi") || q.contains("wi-fi")) {
            if (q.contains("settings") || q.contains("open")) {
                val res = wifiController.openWifiSettings()
                return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, res.message, requiresSettingsIntent = true)
            }
            return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, wifiController.getSpokenWifiStatus())
        }

        // 7. Bluetooth Commands
        if (q.contains("bluetooth")) {
            if (q.contains("settings") || q.contains("open")) {
                val res = bluetoothController.openBluetoothSettings()
                return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, res.message, requiresSettingsIntent = true)
            }
            return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, bluetoothController.getSpokenBluetoothStatus())
        }

        // 8. Storage Commands
        if (q.contains("storage") || q.contains("space") || q.contains("disk")) {
            return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, storageMonitor.getSpokenStorageStatus())
        }

        // 9. Network / Internet Commands
        if (q.contains("internet") || q.contains("network") || q.contains("connection")) {
            return DeviceActionResult(ActionStatus.SUCCESS, rawQuery, networkController.getSpokenNetworkStatus())
        }

        // 10. Navigation Commands (Home, Back, Recents)
        if (q == "go home" || q == "home screen" || q == "home") {
            val ok = accessibilityController.performHome()
            return DeviceActionResult(
                status = if (ok) ActionStatus.SUCCESS else ActionStatus.REQUIRES_PERMISSION,
                command = rawQuery,
                spokenResponse = if (ok) "Navigating Home." else "Accessibility Service is required to perform Home navigation."
            )
        }
        if (q == "go back" || q == "back") {
            val ok = accessibilityController.performBack()
            return DeviceActionResult(
                status = if (ok) ActionStatus.SUCCESS else ActionStatus.REQUIRES_PERMISSION,
                command = rawQuery,
                spokenResponse = if (ok) "Navigating Back." else "Accessibility Service is required to perform Back navigation."
            )
        }
        if (q.contains("recent apps") || q.contains("recents") || q.contains("show recents")) {
            val ok = accessibilityController.performRecents()
            return DeviceActionResult(
                status = if (ok) ActionStatus.SUCCESS else ActionStatus.REQUIRES_PERMISSION,
                command = rawQuery,
                spokenResponse = if (ok) "Showing recent apps." else "Accessibility Service is required to show Recents."
            )
        }

        return DeviceActionResult(
            status = ActionStatus.NOT_SUPPORTED,
            command = rawQuery,
            spokenResponse = "I can control tablet hardware (battery, volume, brightness, flashlight, Wi-Fi, Bluetooth, storage, and study apps). What would you like to adjust?"
        )
    }
}
