package com.smartmotionrecorder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeonPurple = Color(0xFF7C4DFF)
private val NeonBlue = Color(0xFF00E5FF)
private val NeonPink = Color(0xFFFF2D92)
private val DeepBackground = Color(0xFF0D111C)
private val SurfaceGlass = Color(0x33FFFFFF)

private val ColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    secondary = NeonBlue,
    onSecondary = Color.Black,
    background = DeepBackground,
    surface = SurfaceGlass,
    onSurface = Color.White,
    error = NeonPink
)

@Composable
fun MotionRecorderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
