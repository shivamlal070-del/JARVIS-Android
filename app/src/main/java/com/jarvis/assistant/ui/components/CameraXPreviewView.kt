package com.jarvis.assistant.ui.components

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jarvis.assistant.core.device.CameraManager
import com.jarvis.assistant.ui.theme.*

@Composable
fun CameraXPreviewView(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier,
    onImageCaptured: (Bitmap, String) -> Unit,
    onClose: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isTorchActive by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(2.dp, JarvisBorderCyan, RoundedCornerShape(16.dp))
    ) {
        // CameraX Surface View
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    cameraManager.bindCamera(
                        lifecycleOwner = lifecycleOwner,
                        surfaceProvider = surfaceProvider,
                        lensFacing = lensFacing
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Futuristic Optical Reticle & Telemetry Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top HUD Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(8.dp))
                    .background(JarvisDeepBlack.copy(alpha = 0.75f))
                    .border(1.dp, JarvisBorderCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(JarvisSuccessGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "CameraX 1080p • 60 FPS",
                        color = JarvisCyan,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            isTorchActive = cameraManager.toggleTorch()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isTorchActive) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle Torch",
                            tint = if (isTorchActive) JarvisWarningAmber else JarvisTextSecondary
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Camera", tint = JarvisTextPrimary)
                    }
                }
            }

            // Center Framing Brackets / Target Crosshairs
            Box(
                modifier = Modifier
                    .size(220.dp, 160.dp)
                    .align(Alignment.Center)
                    .border(1.dp, JarvisCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            ) {
                Text(
                    "ALIGN DPP QUESTION / NOTEBOOK",
                    color = JarvisCyan.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                )
            }

            // Bottom Shutter & Analyze Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (!isCapturing) {
                            isCapturing = true
                            cameraManager.takePicture(
                                onImageCaptured = { bmp, b64 ->
                                    isCapturing = false
                                    onImageCaptured(bmp, b64)
                                },
                                onError = {
                                    isCapturing = false
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisNeonBlue),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            color = JarvisCyan,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Capturing...", color = Color.White)
                    } else {
                        Icon(Icons.Default.Camera, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Capture & Analyze", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
