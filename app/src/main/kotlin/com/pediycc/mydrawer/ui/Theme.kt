package com.pediycc.mydrawer.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * 抽屉和设置页的文字都直接叠在壁纸上。
 * 原版 drawer.xml 的标题/标签为 #FFFFFFFF，靠黑色投影在任意壁纸上保持可读。
 */
val WallpaperTextShadowStyle = TextStyle(
    shadow = Shadow(
        color = Color(0xCC000000),
        offset = Offset(0f, 1f),
        blurRadius = 4f,
    ),
)

/** 与原版 mtrl_dark_dialog_bg（深色圆角对话框）保持一致的统一配色。 */
object DrawerColors {
    val DialogContainer = Color(0xFF303034)
    val DialogTitle = Color(0xFFFFFFFF)
    val DialogText = Color(0xFFF0F0F0)
    val DialogSecondary = Color(0xFFBDBDBD)
    val DialogDivider = Color(0x33FFFFFF)
    val Accent = Color(0xFF81C784) // 原版 Holo 深色主题复选框为绿色
    val ScrimOverWallpaper = Color(0x1F000000)
}

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
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            window.statusBarColor = AndroidColor.TRANSPARENT
            window.navigationBarColor = AndroidColor.TRANSPARENT
            // 关键：通知 WindowManager 在窗口后绘制壁纸。
            // 对应原版 @android:style/Theme.Holo.Wallpaper 的 windowShowWallpaper=true。
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}

/**
 * 统一对话框容器：直接照原版 mtrl_dark_dialog_bg 的深度与圆角自己画 Surface，
 * 不依赖 Material3 AlertDialog 各版本间不稳定的 colors / 参数签名。
 */
@Composable
fun ThemedDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DrawerColors.DialogContainer,
            contentColor = DrawerColors.DialogText,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}
