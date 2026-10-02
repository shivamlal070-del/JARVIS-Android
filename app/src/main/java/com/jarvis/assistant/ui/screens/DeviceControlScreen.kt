package com.jarvis.assistant.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.device.AndroidActionManager
import com.jarvis.assistant.ui.components.CameraXPreviewView
import com.jarvis.assistant.ui.theme.*

data class AppLaunchShortcut(val name: String, val icon: ImageVector)

@Composable
fun DeviceControlScreen(
    actionManager: AndroidActionManager,
    onLaunchApp: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onAnalyzeImage: ((Bitmap, String) -> Unit)? = null
) {
    val isAccessibilityOn = actionManager.accessibilityController.isAccessibilityEnabled
    val batteryPct = actionManager.batteryMonitor.getBatteryPercentage()
    val isCharging = actionManager.batteryMonitor.isCharging()
    val isWifi = actionManager.dataUsageMonitor.isConnectedToWifi()

    var isCameraPreviewVisible by remember { mutableStateOf(false) }
    var lastCapturedBase64 by remember { mutableStateOf<String?>(null) }

    val apps = listOf(
        AppLaunchShortcut("YouTube", Icons.Default.PlayArrow),
        AppLaunchShortcut("ChatGPT", Icons.Default.Chat),
        AppLaunchShortcut("Gemini", Icons.Default.AutoAwesome),
        AppLaunchShortcut("WhatsApp", Icons.Default.Message),
        AppLaunchShortcut("Chrome", Icons.Default.Language),
        AppLaunchShortcut("Google Maps", Icons.Default.Place),
        AppLaunchShortcut("Google Drive", Icons.Default.CloudQueue),
        AppLaunchShortcut("Google Docs", Icons.Default.Description),
        AppLaunchShortcut("Settings", Icons.Default.Settings),
        AppLaunchShortcut("Clock", Icons.Default.Schedule),
        AppLaunchShortcut("Calculator", Icons.Default.Calculate),
        AppLaunchShortcut("Files", Icons.Default.Folder)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack)
            .padding(16.dp)
    ) {
        // Accessibility Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (isAccessibilityOn) JarvisBorderCyan else JarvisWarningAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAccessibilityOn) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isAccessibilityOn) JarvisCyan else JarvisWarningAmber
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Accessibility Service", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isAccessibilityOn) "Connected — Cross-app control active" else "Disabled — Click to enable in Samsung Settings",
                            color = JarvisTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                if (!isAccessibilityOn) {
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        colors = ButtonDefaults.buttonColors(containerColor = JarvisWarningAmber)
                    ) {
                        Text("Enable", color = JarvisDeepBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // CameraX Live Preview Overlay Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = JarvisCyan)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("CameraX Live Vision & DPP Analysis", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Real-time optical stream with Gemini visual problem solver", color = JarvisTextSecondary, fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = { isCameraPreviewVisible = !isCameraPreviewVisible },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCameraPreviewVisible) JarvisWarningAmber else JarvisNeonBlue
                        )
                    ) {
                        Text(
                            if (isCameraPreviewVisible) "Hide Camera" else "Live CameraX",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isCameraPreviewVisible,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        CameraXPreviewView(
                            cameraManager = actionManager.cameraManager,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            onImageCaptured = { bmp, b64 ->
                                lastCapturedBase64 = b64
                                onAnalyzeImage?.invoke(bmp, b64)
                            },
                            onClose = { isCameraPreviewVisible = false }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Device Status Indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, JarvisBorderCyan, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = JarvisCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Battery", color = JarvisTextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("$batteryPct% ${if (isCharging) "(Charging)" else ""}", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, JarvisBorderCyan, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (isWifi) Icons.Default.Wifi else Icons.Default.SignalCellularAlt, contentDescription = null, tint = JarvisElectricTeal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Network", color = JarvisTextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(if (isWifi) "Wi-Fi Connected" else "Mobile Data", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { actionManager.accessibilityController.pressBack() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Press Back")
            }

            OutlinedButton(
                onClick = { actionManager.accessibilityController.pressHome() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan)
            ) {
                Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Press Home")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("SUPPORTED APPLICATIONS", color = JarvisTextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(6.dp))

        // App Launch Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(apps) { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, JarvisBorderCyan, RoundedCornerShape(10.dp))
                        .clickable { onLaunchApp(app.name) },
                    colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(JarvisNeonBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(app.icon, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(app.name, color = JarvisTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Launch App", color = JarvisTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
