package com.jarvis.assistant.core.device

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

class ScreenCaptureManager(private val context: Context) {

    private val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    var activeMediaProjection: MediaProjection? = null
        private set

    val isScreenCaptureApproved: Boolean
        get() = activeMediaProjection != null

    fun createScreenCaptureIntent(): Intent {
        return mediaProjectionManager.createScreenCaptureIntent()
    }

    fun onScreenCaptureConsentReceived(resultCode: Int, data: Intent) {
        if (resultCode == Activity.RESULT_OK) {
            activeMediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        }
    }

    fun stopScreenCapture() {
        activeMediaProjection?.stop()
        activeMediaProjection = null
    }
}
