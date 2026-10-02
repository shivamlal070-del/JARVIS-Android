package com.jarvis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    secondary = JarvisNeonBlue,
    tertiary = JarvisElectricTeal,
    background = JarvisDeepBlack,
    surface = JarvisSurfaceDark,
    onPrimary = JarvisDeepBlack,
    onSecondary = JarvisTextPrimary,
    onBackground = JarvisTextPrimary,
    onSurface = JarvisTextPrimary,
    error = JarvisErrorRed
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
