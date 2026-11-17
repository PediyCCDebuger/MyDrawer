package com.pediycc.mydrawer.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B6FD4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4FF),
    secondary = Color(0xFF4A6572),
    surface = Color(0xFF1E2228),
    onSurface = Color(0xFFF2F4F7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB4FF),
    onPrimary = Color(0xFF0A2547),
    primaryContainer = Color(0xFF1B3A6B),
    secondary = Color(0xFF9AA7B0),
    surface = Color(0xFF1E2228),
    onSurface = Color(0xFFF2F4F7),
)

@Composable
fun MyDrawerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            // 真正透明：让抽屉半透明背景透出壁纸。
            window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),
            )
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}
