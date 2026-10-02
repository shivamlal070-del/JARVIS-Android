package com.jarvis.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.core.permissions.PermissionDetail
import com.jarvis.assistant.core.permissions.PermissionManager
import com.jarvis.assistant.ui.theme.*

@Composable
fun PermissionsScreen(
    permissionManager: PermissionManager,
    onRequestPermission: (PermissionDetail) -> Unit
) {
    val permissions = permissionManager.getPermissionsStatus()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisDeepBlack)
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Security, contentDescription = null, tint = JarvisCyan, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("TRANSPARENT PERMISSIONS SETUP", color = JarvisTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Galaxy Tab S5e • Android 11 Compliant Access", color = JarvisTextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, JarvisBorderCyan, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = JarvisCardBg)
        ) {
            Text(
                text = "JARVIS operates exclusively using legitimate Android APIs. We never secretly request permissions or bypass Android security boundaries. You have granular control over each capability.",
                color = JarvisTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(14.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(permissions) { perm ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (perm.isGranted) JarvisBorderCyan else JarvisWarningAmber.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = JarvisSurfaceDark)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = perm.title,
                                color = JarvisTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (perm.isGranted) {
                                Surface(
                                    color = JarvisSuccessGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = JarvisSuccessGreen, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Granted", color = JarvisSuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { onRequestPermission(perm) },
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                                ) {
                                    Text("Grant Permission", color = JarvisDeepBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text("What it does:", color = JarvisCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(perm.purpose, color = JarvisTextSecondary, fontSize = 12.sp)

                        Spacer(modifier = Modifier.height(6.dp))

                        Text("If denied:", color = JarvisWarningAmber, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(perm.whatHappensIfNotGranted, color = JarvisTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
