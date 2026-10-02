package com.jarvis.assistant.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.jarvis.assistant.core.device.JarvisAccessibilityService

data class PermissionDetail(
    val title: String,
    val permissionKey: String,
    val purpose: String,
    val whatHappensIfNotGranted: String,
    val isGranted: Boolean,
    val isSpecialAccess: Boolean = false
)

class PermissionManager(private val context: Context) {

    fun getPermissionsStatus(): List<PermissionDetail> {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val hasAccessibility = JarvisAccessibilityService.isServiceRunning

        return listOf(
            PermissionDetail(
                title = "Microphone Access",
                permissionKey = Manifest.permission.RECORD_AUDIO,
                purpose = "Allows hands-free voice commands, wake-word detection, and real-time conversation.",
                whatHappensIfNotGranted = "Voice commands cannot be heard; you will only be able to type or inspect.",
                isGranted = hasMic
            ),
            PermissionDetail(
                title = "Android Accessibility Service",
                permissionKey = "android.permission.BIND_ACCESSIBILITY_SERVICE",
                purpose = "Allows JARVIS to inspect interactive buttons, read screen text upon your command, and click approved controls.",
                whatHappensIfNotGranted = "JARVIS cannot control external apps (like YouTube, WhatsApp, or Settings) or read your screen.",
                isGranted = hasAccessibility,
                isSpecialAccess = true
            ),
            PermissionDetail(
                title = "Camera Access",
                permissionKey = Manifest.permission.CAMERA,
                purpose = "Allows photographing Class 11 DPP problems, textbook diagrams, and notebook calculations for Gemini analysis.",
                whatHappensIfNotGranted = "You cannot photograph DPP homework problems for step-by-step checking.",
                isGranted = hasCamera
            )
        )
    }
}
