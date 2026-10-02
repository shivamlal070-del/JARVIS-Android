package com.jarvis.assistant.core.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

class AppLauncher(private val context: Context) {
    private val tag = "AppLauncher"

    // Common app aliases mapped to package names
    private val packageMap = mapOf(
        "youtube" to "com.google.android.youtube",
        "chatgpt" to "com.openai.chatgpt",
        "gemini" to "com.google.android.apps.bard",
        "whatsapp" to "com.whatsapp",
        "chrome" to "com.android.chrome",
        "google" to "com.google.android.googlequicksearchbox",
        "settings" to "com.android.settings",
        "calculator" to "com.sec.android.app.popupcalculator", // Samsung Calculator
        "clock" to "com.sec.android.app.clockpackage",       // Samsung Clock
        "calendar" to "com.samsung.android.calendar",
        "files" to "com.sec.android.app.myfiles"
    )

    fun launchAppByName(appName: String): Boolean {
        val cleanName = appName.trim().lowercase()
        val targetPackage = packageMap[cleanName]

        if (targetPackage != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(targetPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            }
        }

        // Try fuzzy matching installed application labels
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label.contains(cleanName) || cleanName.contains(label)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    return true
                }
            }
        }

        Log.w(tag, "App '$appName' could not be resolved or launched.")
        return false
    }

    fun getLastLaunchFailureReason(appName: String): String {
        return "$appName is not installed on this device or does not provide a standard launcher interface."
    }

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
