package com.jarvis.assistant.core.device

import android.content.Context
import android.media.AudioManager
import android.os.Build

data class VolumeStreamInfo(
    val currentVolume: Int,
    val maxVolume: Int,
    val percentage: Int,
    val isMuted: Boolean
)

data class AudioActionResult(
    val success: Boolean,
    val message: String,
    val streamType: Int,
    val newPercentage: Int
)

/**
 * AudioController manages Android audio streams (Media, Alarm, Notification, Voice Call).
 * Controls only streams permitted by Android and respects system ringer constraints.
 */
class AudioController(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun getStreamInfo(streamType: Int = AudioManager.STREAM_MUSIC): VolumeStreamInfo {
        if (audioManager == null) return VolumeStreamInfo(0, 100, 0, false)

        val current = audioManager.getStreamVolume(streamType)
        val max = audioManager.getStreamMaxVolume(streamType)
        val percent = if (max > 0) ((current / max.toFloat()) * 100).toInt() else 0
        val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.isStreamMute(streamType)
        } else {
            current == 0
        }

        return VolumeStreamInfo(current, max, percent, isMuted)
    }

    fun setVolumePercentage(percent: Int, streamType: Int = AudioManager.STREAM_MUSIC): AudioActionResult {
        if (audioManager == null) return AudioActionResult(false, "AudioManager unavailable.", streamType, 0)

        val clamped = percent.coerceIn(0, 100)
        val max = audioManager.getStreamMaxVolume(streamType)
        val targetIndex = ((clamped / 100f) * max).toInt().coerceIn(0, max)

        return try {
            audioManager.setStreamVolume(streamType, targetIndex, AudioManager.FLAG_SHOW_UI)
            val streamName = getStreamName(streamType)
            AudioActionResult(true, "$streamName volume set to $clamped percent.", streamType, clamped)
        } catch (e: Exception) {
            AudioActionResult(false, "Unable to adjust volume: ${e.localizedMessage}", streamType, 0)
        }
    }

    fun adjustVolume(direction: Int, streamType: Int = AudioManager.STREAM_MUSIC): AudioActionResult {
        if (audioManager == null) return AudioActionResult(false, "AudioManager unavailable.", streamType, 0)

        return try {
            audioManager.adjustStreamVolume(streamType, direction, AudioManager.FLAG_SHOW_UI)
            val info = getStreamInfo(streamType)
            val streamName = getStreamName(streamType)
            AudioActionResult(true, "$streamName volume is now at ${info.percentage} percent.", streamType, info.percentage)
        } catch (e: Exception) {
            AudioActionResult(false, "Volume adjustment restricted by system: ${e.localizedMessage}", streamType, 0)
        }
    }

    fun mute(streamType: Int = AudioManager.STREAM_MUSIC): AudioActionResult {
        return setVolumePercentage(0, streamType)
    }

    fun unmute(streamType: Int = AudioManager.STREAM_MUSIC, defaultPercent: Int = 40): AudioActionResult {
        return setVolumePercentage(defaultPercent, streamType)
    }

    private fun getStreamName(streamType: Int): String {
        return when (streamType) {
            AudioManager.STREAM_MUSIC -> "Media"
            AudioManager.STREAM_ALARM -> "Alarm"
            AudioManager.STREAM_NOTIFICATION -> "Notification"
            AudioManager.STREAM_RING -> "Ringtone"
            AudioManager.STREAM_VOICE_CALL -> "Call"
            else -> "Audio"
        }
    }
}
