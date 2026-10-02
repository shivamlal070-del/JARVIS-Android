package com.jarvis.assistant.core.device

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraManager handles CameraX lifecycle binding, capture triggers,
 * and base64 image serialization for Gemini DPP visual inspection.
 */
class CameraManager(private val context: Context) {

    private val tag = "CameraManager"
    private var imageCapture: ImageCapture? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null
    private var isTorchOn = false

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        onBound: (Camera) -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()
                    .also {
                        it.setSurfaceProvider(surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                onBound(camera)
                Log.d(tag, "CameraX successfully bound to lifecycle.")
            } catch (e: Exception) {
                Log.e(tag, "Failed to bind CameraX lifecycle: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun takePicture(
        onImageCaptured: (Bitmap, String) -> Unit,
        onError: (ImageCaptureException) -> Unit
    ) {
        val capture = imageCapture ?: run {
            Log.e(tag, "ImageCapture is not bound.")
            return
        }

        val photoFile = File(
            context.cacheDir,
            "JARVIS_DPP_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                        val base64 = convertBitmapToBase64(bitmap)
                        onImageCaptured(bitmap, base64)
                    } catch (e: Exception) {
                        Log.e(tag, "Error processing captured photo: ${e.message}", e)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(tag, "Photo capture failed: ${exception.message}", exception)
                    onError(exception)
                }
            }
        )
    }

    fun toggleTorch(): Boolean {
        cameraControl?.let { control ->
            isTorchOn = !isTorchOn
            control.enableTorch(isTorchOn)
            return isTorchOn
        }
        return false
    }

    fun convertUriToBase64(imageUri: Uri): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            convertBitmapToBase64(bitmap)
        } catch (e: Exception) {
            null
        }
    }

    fun convertBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}
