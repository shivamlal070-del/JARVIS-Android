package com.jarvis.assistant.core.device

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var activeInstance: JarvisAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = activeInstance != null
    }

    private val tag = "JarvisAccessService"

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        Log.i(tag, "JARVIS Accessibility Service successfully connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitored for window transitions and interactive UI updates
    }

    override fun onInterrupt() {
        Log.w(tag, "Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance == this) {
            activeInstance = null
        }
    }

    // Accessible Actions Execution
    fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        return false
    }

    fun typeTextIntoFocusedNode(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun findNodesByText(text: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        return root.findAccessibilityNodeInfosByText(text)
    }

    fun getAllScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val stringBuilder = StringBuilder()
        collectTextRecursively(root, stringBuilder)
        return stringBuilder.toString()
    }

    private fun collectTextRecursively(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (!node.text.isNullOrBlank()) {
            sb.append(node.text).append(" ")
        } else if (!node.contentDescription.isNullOrBlank()) {
            sb.append(node.contentDescription).append(" ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectTextRecursively(child, sb)
            }
        }
    }
}
