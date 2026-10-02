package com.jarvis.assistant.core.device

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class AccessibilityController(private val context: Context) {
    private val tag = "AccessController"

    val isAccessibilityEnabled: Boolean
        get() = JarvisAccessibilityService.isServiceRunning

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun pressBack(): Boolean {
        val service = JarvisAccessibilityService.activeInstance ?: return false
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        val service = JarvisAccessibilityService.activeInstance ?: return false
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun extractVisibleScreenText(): String {
        val service = JarvisAccessibilityService.activeInstance ?: return ""
        return service.getAllScreenText().trim()
    }

    fun extractCurrentBrowserUrl(): String? {
        val service = JarvisAccessibilityService.activeInstance ?: return null
        val root = service.rootInActiveWindow ?: return null

        // Search for address bar in Chrome or Samsung Internet
        val urlNodes = root.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
        if (urlNodes.isNotEmpty()) {
            return urlNodes[0].text?.toString()
        }
        val samsungNodes = root.findAccessibilityNodeInfosByViewId("com.sec.android.app.sbrowser:id/location_bar_edit_text")
        if (samsungNodes.isNotEmpty()) {
            return samsungNodes[0].text?.toString()
        }
        return null
    }

    fun clickButtonWithText(label: String): Boolean {
        val service = JarvisAccessibilityService.activeInstance ?: return false
        val matchingNodes = service.findNodesByText(label)
        for (node in matchingNodes) {
            if (service.performClickOnNode(node)) {
                return true
            }
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val service = JarvisAccessibilityService.activeInstance ?: return false
        return service.typeTextIntoFocusedNode(text)
    }
}
