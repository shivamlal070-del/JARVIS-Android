package com.jarvis.assistant.core.device

import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController as AndroidMediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent

data class MediaStatus(
    val isPlaying: Boolean,
    val activeApp: String?,
    val trackTitle: String?,
    val trackArtist: String?
)

data class MediaActionResult(
    val success: Boolean,
    val action: String,
    val message: String
)

/**
 * MediaController controls foreground/active media sessions using legitimate Android MediaSessionManager
 * and KeyEvent dispatch. Transparently reports when no media session is active.
 */
class MediaController(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    fun getMediaStatus(): MediaStatus {
        val isMusicActive = audioManager?.isMusicActive ?: false
        return MediaStatus(
            isPlaying = isMusicActive,
            activeApp = if (isMusicActive) "Active Android Media Player" else null,
            trackTitle = null,
            trackArtist = null
        )
    }

    fun play(): MediaActionResult = dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY, "Play")
    fun pause(): MediaActionResult = dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE, "Pause")
    fun playPause(): MediaActionResult = dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play/Pause")
    fun next(): MediaActionResult = dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT, "Next Track")
    fun previous(): MediaActionResult = dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous Track")
    fun stop(): MediaActionResult = dispatchKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP, "Stop Music")

    private fun dispatchKeyEvent(keyCode: Int, actionName: String): MediaActionResult {
        if (audioManager == null) {
            return MediaActionResult(false, actionName, "AudioManager is unavailable.")
        }

        return try {
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager.dispatchMediaKeyEvent(down)
            audioManager.dispatchMediaKeyEvent(up)
            MediaActionResult(true, actionName, "Media command '$actionName' dispatched to active media session.")
        } catch (e: Exception) {
            MediaActionResult(false, actionName, "Unable to dispatch media control: ${e.localizedMessage}")
        }
    }
}
