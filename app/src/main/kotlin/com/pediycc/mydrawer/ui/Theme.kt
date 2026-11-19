package com.pediycc.mydrawer.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * 应用文字阴影：黑底上仍保留一层轻微投影，贴在彩色图标下时更清楚。
 * 对应原版 drawer.xml 的白色文字 + 阴影写法。
 */
val WallpaperTextShadowStyle = TextStyle(
    shadow = Shadow(
        color = Color(0xCC000000),
        offset = Offset(0f, 1f),
        blurRadius = 4f,
    ),
)

/** 2.3.0 统一配色：全局纯黑背景，分类栏略浅一档区分，弹窗深灰圆角。 */
object DrawerColors {
    /** 主界面 / 设置页背景：纯黑，不透明。 */
    val AppBackground = Color(0xFF000000)

    /**
     * 左侧分类栏：略带冷灰蓝的深底，和应用区纯黑拉开一档，
     * 分类里应用多时也能一眼分出分类栏和内容区；色相克制、不抢图标。
     */
    val RailBackground = Color(0xFF1E242B)
    /** 分类栏当前选中项：同色系再亮一档。 */
    val RailSelected = Color(0xFF333F4B)
    /** 分类栏与应用区之间的 1dp 分界线，进一步明确边界。 */
    val RailDivider = Color(0xFF2B3239)

    /** 设置页列表行与分组标题。 */
    val SettingsRow = Color(0xFF101010)
    val SettingsHeader = Color(0xFF1C1C1C)
    val SettingsDivider = Color(0xFF262626)

    /** 抽屉里分组标题条的底色。 */
    val GroupHeader = Color(0xFF111111)

    val DialogContainer = Color(0xFF2C2C30)
    val DialogTitle = Color(0xFFFFFFFF)
    val DialogText = Color(0xFFF0F0F0)
    val DialogSecondary = Color(0xFFBDBDBD)
    val DialogDivider = Color(0x33FFFFFF)
    val Accent = Color(0xFF81C784) // 原版 Holo 深色主题复选框为绿色
    /** 「全部」分类图标用的高亮蓝。 */
    val AllIcon = Color(0xFF4FC3F7)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB4FF),
    onPrimary = Color(0xFF0A2547),
    primaryContainer = Color(0xFF1B3A6B),
    secondary = Color(0xFF9AA7B0),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F4F7),
    surface = Color(0xFF101010),
    onSurface = Color(0xFFF2F4F7),
    surfaceContainer = Color(0xFF161616),
)

@Composable
fun MyDrawerTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            // 不透明黑底：不再透壁纸，也不会在滚动时出现重影。
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.BLACK))
            window.statusBarColor = AndroidColor.BLACK
            window.navigationBarColor = AndroidColor.BLACK
        }
    }
    // 界面固定深色，不跟随系统浅色主题，避免白色控件叠在黑色背景上。
    MaterialTheme(colorScheme = DarkColors, content = content)
}

/**
 * 统一对话框容器：直接照原版 mtrl_dark_dialog_bg 的深度与圆角自己画 Surface，
 * 不依赖 Material3 AlertDialog 各版本间不稳定的 colors / 参数签名。
 */
@Composable
fun ThemedDialog(
    onDismissRequest: () -> Unit,
    horizontalPadding: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = DrawerColors.DialogContainer,
            contentColor = DrawerColors.DialogText,
            shadowElevation = 8.dp,
            // 输入框获得焦点时让对话框整体避开软键盘，避免「确定 / 创建」按钮被键盘盖住。
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).imePadding(),
        ) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}
