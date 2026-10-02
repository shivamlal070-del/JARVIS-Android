package com.jarvis.assistant.core.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionState(
    val name: String,
    val permissionKey: String,
    val isGranted: Boolean,
    val isRequired: Boolean,
    val purpose: String
)

data class MasterPermissionAudit(
    val allRequiredGranted: Boolean,
    val permissions: List<PermissionState>
)

/**
 * PermissionManager audits actual runtime and system permissions without silently faking grants.
 */
class PermissionManager(private val context: Context) {

    fun auditPermissions(): MasterPermissionAudit {
        val list = mutableListOf<PermissionState>()

        // 1. Microphone
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        list.add(PermissionState("Microphone", Manifest.permission.RECORD_AUDIO, micGranted, true, "Hands-free voice recognition and wake-phrase detection."))

        // 2. Camera
        val camGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        list.add(PermissionState("Camera", Manifest.permission.CAMERA, camGranted, true, "CameraX scanning of Class 11 DPP questions and notebook derivations."))

        // 3. Accessibility Service
        val accGranted = isAccessibilityServiceEnabled()
        list.add(PermissionState("Accessibility Service", "BIND_ACCESSIBILITY_SERVICE", accGranted, false, "Cross-application automation and Strict Study Mode redirection."))

        // 4. Modify System Settings
        val writeSettingsGranted = Settings.System.canWrite(context)
        list.add(PermissionState("Modify System Settings", "WRITE_SETTINGS", writeSettingsGranted, false, "Direct hardware display brightness control."))

        // 5. System Alert Window (Overlay)
        val overlayGranted = Settings.canDrawOverlays(context)
        list.add(PermissionState("Display Over Other Apps", "SYSTEM_ALERT_WINDOW", overlayGranted, false, "Floating JARVIS HUD pill during background operation."))

        val requiredGranted = micGranted && camGranted
        return MasterPermissionAudit(requiredGranted, list)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "${context.packageName}/${JarvisAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { it.equals(expectedService, ignoreCase = true) || it.contains(context.packageName) }
    }
}
