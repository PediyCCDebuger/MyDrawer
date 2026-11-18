package com.pediycc.mydrawer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pediycc.mydrawer.data.AppInfo
import com.pediycc.mydrawer.data.AppRepository
import com.pediycc.mydrawer.data.BuiltInIcons
import com.pediycc.mydrawer.data.CategoryCatalog
import com.pediycc.mydrawer.data.Preferences

/**
 * 统一图标渲染：
 * - builtin:xxx → 内置矢量图库
 * - file:xxx → 用户从本机图库选择的图片
 * - app:<pkg> → 指定包的原图标
 * - null → 应用自身图标 / 分类默认图标
 */
@Composable
fun DrawerIcon(
    key: String?,
    fallback: AppInfo? = null,
    fallbackVector: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Apps,
    size: Dp = 44.dp,
    contentDescription: String? = null,
    colorTint: androidx.compose.ui.graphics.Color? = null,
) {
    val context = LocalContext.current
    val store = remember { IconStore(context) }
    when {
        key != null && key.startsWith("builtin:") -> {
            val entry = BuiltInIcons.find(key)
            if (entry != null) {
                Icon(
                    imageVector = entry.vector,
                    contentDescription = contentDescription ?: entry.label,
                    modifier = Modifier.size(size),
                    // 默认用图库自带彩色，黑底上不再是灰白一片。
                    tint = colorTint ?: entry.color,
                )
            } else {
                Icon(fallbackVector, contentDescription, Modifier.size(size), tint = Color(0xFFB0BEC5))
            }
        }
        key != null && key.startsWith("file:") -> {
            val bitmap = remember(key) { store.load(key) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )
            } else {
                Icon(fallbackVector, contentDescription, Modifier.size(size))
            }
        }
        key != null && key.startsWith("app:") -> {
            val pkg = key.removePrefix("app:")
            val repo = remember { AppRepositoryHolder.get(context) }
            val drawable = remember(pkg) { repo?.originalApp(pkg)?.second }
            if (drawable != null) {
                Image(
                    bitmap = remember(drawable) { drawable.toBitmapSafe() }.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )
            } else {
                Icon(fallbackVector, contentDescription, Modifier.size(size))
            }
        }
        fallback?.icon != null -> {
            Image(
                bitmap = remember(fallback.packageName + (key ?: "")) {
                    fallback.icon.toBitmapSafe()
                }.asImageBitmap(),
                contentDescription = contentDescription ?: fallback.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
            )
        }
        else -> Icon(fallbackVector, contentDescription, Modifier.size(size), tint = Color(0xFFB0BEC5))
    }
}

private fun android.graphics.drawable.Drawable.toBitmapSafe(): android.graphics.Bitmap =
    try {
        val out = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        setBounds(0, 0, 128, 128)
        draw(canvas)
        out
    } catch (e: Exception) {
        android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
    }

/** 延迟持有仓库，避免 Composition 里重复构造。 */
object AppRepositoryHolder {
    @Volatile
    private var instance: AppRepository? = null

    fun get(context: android.content.Context): AppRepository? = instance ?: synchronized(this) {
        instance ?: try {
            AppRepository(
                context.applicationContext,
                CategoryCatalog(context.applicationContext),
                Preferences(context.applicationContext),
            ).also { instance = it }
        } catch (e: Exception) {
            null
        }
    }
}
