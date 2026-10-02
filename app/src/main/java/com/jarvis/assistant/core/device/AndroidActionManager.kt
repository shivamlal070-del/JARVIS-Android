package com.jarvis.assistant.core.device

import android.content.Context

class AndroidActionManager(private val context: Context) {
    val appLauncher = AppLauncher(context)
    val accessibilityController = AccessibilityController(context)
    val timerManager = TimerManager(context)
    val batteryMonitor = BatteryMonitor(context)
    val dataUsageMonitor = DataUsageMonitor(context)
    val cameraManager = CameraManager(context)
    val screenCaptureManager = ScreenCaptureManager(context)
}
