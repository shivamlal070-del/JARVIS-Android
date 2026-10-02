package com.jarvis.assistant.core.device

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager

data class BrightnessInfo(
    val percentage: Int,
    val rawValue: Int, // 0 - 255
    val isAutoBrightness: Boolean,
    val hasWriteSettingsPermission: Boolean
)

data class DisplayActionResult(
    val success: Boolean,
    val requiresPermission: Boolean,
    val message: String
)

/**
 * DisplayController manages screen brightness, wake state (WakeLock), and screen lock.
 * If system-level brightness modification requires WRITE_SETTINGS, guides user to the settings screen.
 */
class DisplayController(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    fun getBrightnessInfo(): BrightnessInfo {
        val hasPermission = Settings.System.canWrite(context)
        val raw = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            128
        }
        val mode = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        } catch (e: Exception) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        }
        val isAuto = mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        val percent = ((raw / 255f) * 100).toInt().coerceIn(0, 100)

        return BrightnessInfo(percent, raw, isAuto, hasPermission)
    }

    fun setSystemBrightness(percentage: Int): DisplayActionResult {
        val clamped = percentage.coerceIn(0, 100)
        val raw = ((clamped / 100f) * 255).toInt().coerceIn(1, 255)

        if (!Settings.System.canWrite(context)) {
            // Open legitimate system permission intent
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return DisplayActionResult(
                success = false,
                requiresPermission = true,
                message = "Android requires 'Modify System Settings' permission. Opening Settings now."
            )
        }

        return try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw)
            DisplayActionResult(true, false, "Screen brightness set to $clamped percent.")
        } catch (e: Exception) {
            DisplayActionResult(false, false, "Unable to adjust brightness: ${e.localizedMessage}")
        }
    }

    fun keepScreenAwake(keepAwake: Boolean, timeoutMs: Long = 30 * 60 * 1000L): DisplayActionResult {
        if (powerManager == null) return DisplayActionResult(false, false, "PowerManager is unavailable.")

        return try {
            if (keepAwake) {
                if (wakeLock == null) {
                    wakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "JARVIS:ScreenAwakeLock"
                    )
                }
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(timeoutMs)
                }
                DisplayActionResult(true, false, "Screen will remain awake during study.")
            } else {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
                wakeLock = null
                DisplayActionResult(true, false, "Screen keep-awake released.")
            }
        } catch (e: Exception) {
            DisplayActionResult(false, false, "WakeLock operation failed: ${e.localizedMessage}")
        }
    }
}
