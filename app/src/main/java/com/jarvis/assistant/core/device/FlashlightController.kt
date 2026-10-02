package com.jarvis.assistant.core.device

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build

data class TorchActionResult(
    val success: Boolean,
    val isTorchOn: Boolean,
    val message: String
)

/**
 * FlashlightController toggles physical rear camera torch using CameraManager.
 * Transparently reports if hardware torch is unavailable.
 */
class FlashlightController(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    var isFlashlightOn: Boolean = false
        private set

    init {
        // Register torch callback to track external/physical toggles
        try {
            cameraManager?.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    super.onTorchModeChanged(cameraId, enabled)
                    isFlashlightOn = enabled
                }

                override fun onTorchModeUnavailable(cameraId: String) {
                    super.onTorchModeUnavailable(cameraId)
                    isFlashlightOn = false
                }
            }, null)
        } catch (e: Exception) {
            // Callback registration fallback
        }
    }

    fun setTorchMode(enabled: Boolean): TorchActionResult {
        if (cameraManager == null) {
            return TorchActionResult(false, false, "CameraManager is unavailable on this tablet.")
        }

        try {
            var rearCameraIdWithFlash: String? = null
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    rearCameraIdWithFlash = id
                    break
                }
            }

            if (rearCameraIdWithFlash == null) {
                // Fallback to first available camera with flash
                for (id in cameraManager.cameraIdList) {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                        rearCameraIdWithFlash = id
                        break
                    }
                }
            }

            if (rearCameraIdWithFlash == null) {
                return TorchActionResult(false, false, "This tablet does not expose a controllable camera flashlight.")
            }

            cameraManager.setTorchMode(rearCameraIdWithFlash, enabled)
            isFlashlightOn = enabled
            val msg = if (enabled) "Flashlight is now ON." else "Flashlight is now OFF."
            return TorchActionResult(true, enabled, msg)

        } catch (e: CameraAccessException) {
            return TorchActionResult(false, isFlashlightOn, "Camera access denied or camera in active use: ${e.localizedMessage}")
        } catch (e: Exception) {
            return TorchActionResult(false, isFlashlightOn, "Unable to control flashlight: ${e.localizedMessage}")
        }
    }

    fun toggleTorch(): TorchActionResult {
        return setTorchMode(!isFlashlightOn)
    }
}
