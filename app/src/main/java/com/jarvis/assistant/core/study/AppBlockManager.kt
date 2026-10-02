package com.jarvis.assistant.core.study

enum class StrictnessLevel {
    GENTLE,
    FIRM,
    STRICT
}

enum class BlockActionType {
    WARN_ONLY,
    REDIRECT_TO_STUDY,
    ENFORCE_LOCK
}

data class BlockActionResult(
    val actionType: BlockActionType,
    val packageName: String,
    val appName: String,
    val warningMessage: String
)

/**
 * AppBlockManager manages entertainment/distraction app restrictions during
 * protected study periods across Gentle, Firm, and Strict levels.
 */
class AppBlockManager {
    var strictnessLevel: StrictnessLevel = StrictnessLevel.FIRM
    var isStudyLockActive: Boolean = false

    val defaultBlockedPackages = mutableSetOf(
        "com.google.android.youtube",
        "com.instagram.android",
        "com.netflix.mediaclient",
        "com.spotify.music",
        "com.facebook.katana",
        "com.zhiliaoapp.musically", // TikTok
        "com.snapchat.android"
    )

    val defaultAllowedPackages = mutableSetOf(
        "com.jarvis.assistant",
        "com.sec.android.app.popupcalculator",
        "com.google.android.calculator",
        "com.sec.android.app.camera",
        "com.android.chrome",
        "com.google.android.apps.docs",
        "com.google.android.apps.docs.editors.docs"
    )

    fun isAppRestricted(packageName: String): Boolean {
        if (!isStudyLockActive) return false
        if (defaultAllowedPackages.contains(packageName)) return false
        return defaultBlockedPackages.contains(packageName)
    }

    fun evaluateAppLaunch(packageName: String, appName: String, minutesRemaining: Int): BlockActionResult? {
        if (!isStudyLockActive) return null
        if (!isAppRestricted(packageName)) return null

        val warning = "You're currently in a protected study session. Your break begins in $minutesRemaining minutes."

        return when (strictnessLevel) {
            StrictnessLevel.GENTLE -> BlockActionResult(
                actionType = BlockActionType.WARN_ONLY,
                packageName = packageName,
                appName = appName,
                warningMessage = warning
            )
            StrictnessLevel.FIRM -> BlockActionResult(
                actionType = BlockActionType.REDIRECT_TO_STUDY,
                packageName = packageName,
                appName = appName,
                warningMessage = "$warning Redirecting back to JARVIS Study Mode."
            )
            StrictnessLevel.STRICT -> BlockActionResult(
                actionType = BlockActionType.ENFORCE_LOCK,
                packageName = packageName,
                appName = appName,
                warningMessage = "$warning Access restricted by Strict Study Policy."
            )
        }
    }
}
