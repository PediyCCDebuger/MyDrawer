package com.pediycc.mydrawer.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 滚动式应用内调试日志（2.9.4）。
 *
 * 参考 Reminder（AddinTimer 复刻版）的 AtkDiag：很多问题（如 ColorOS 卸载静默拦截）
 * 从外部无法诊断，只有应用自己把关键链路逐步记下来，截图 / 导出日志才能定位断点。
 *
 * 设计：
 * - 持久化到 SharedPreferences，重启不丢（排查「上次为什么没弹窗」必需）；
 * - 只保留最近 [MAX_LINES] 行，避免无限增长；
 * - 「调试日志」开关存独立的 SP，不进 UserPrefs（不随配置备份，避免干扰他人导入）；
 * - 所有写入都吞异常——诊断永远不能影响主功能。
 */
object DebugLog {
    private const val PREF = "mydrawer_debug_log"
    private const val KEY = "lines"
    private const val SETTINGS = "mydrawer_settings"
    private const val DEBUG_KEY = "debug_enabled"
    private const val MAX_LINES = 300

    /** 系统「下载」目录下的子文件夹名。 */
    private const val FOLDER = "MyDrawer"

    /** 「设置 → 调试日志」开关。关闭时 log() 直接丢弃，零开销。 */
    fun isDebug(context: Context): Boolean =
        try {
            context.applicationContext.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
                .getBoolean(DEBUG_KEY, false)
        } catch (e: Exception) {
            false
        }

    fun setDebug(context: Context, on: Boolean) {
        try {
            context.applicationContext.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
                .edit().putBoolean(DEBUG_KEY, on).apply()
        } catch (e: Exception) {
        }
    }

    fun log(context: Context, message: String) {
        if (!isDebug(context)) return
        try {
            val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date())
            val line = "$stamp  $message"
            Log.i("MyDrawerDebug", line)
            val sp = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val old = sp.getString(KEY, "").orEmpty()
            val lines = (old.split('\n').filter { it.isNotBlank() } + line).takeLast(MAX_LINES)
            sp.edit().putString(KEY, lines.joinToString("\n")).apply()
        } catch (e: Exception) {
            // 诊断日志自身出错时静默，绝不影响主功能。
        }
    }

    fun text(context: Context): String = try {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
            .ifBlank { "（暂无记录）" }
    } catch (e: Exception) {
        ""
    }

    fun clear(context: Context) {
        try {
            context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
        } catch (e: Exception) {
        }
    }

    /**
     * 保存为文本文件到系统「下载」目录（2.9.5）：
     * /storage/emulated/0/Download/MyDrawer/MyDrawer-debug-时间戳.txt
     *
     * 之前存在 /Android/data/<包名>/files/logs/ 里，Android 11 起文件管理器基本进不去，
     * 手动取出来很麻烦，改到公共下载目录：Android 10+ 走 MediaStore（无需存储权限），
     * Android 9 及以下直接写公共目录，失败再退回应用专属目录。
     *
     * @return 便于阅读的保存位置（如 Download/MyDrawer/xxx.txt）；失败返回 null。
     */
    fun saveToFile(context: Context): String? = try {
        val body = text(context)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        val name = "MyDrawer-debug-$stamp.txt"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveToDownloadsMediaStore(context, name, body) ?: saveToAppDir(context, name, body)
        } else {
            saveToDownloadsLegacy(name, body) ?: saveToAppDir(context, name, body)
        }
    } catch (e: Exception) {
        null
    }

    /** Android 10+：MediaStore 写入公共「下载」目录，不需要任何存储权限。 */
    private fun saveToDownloadsMediaStore(context: Context, name: String, body: String): String? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            null
        } ?: return null
        val written = try {
            resolver.openOutputStream(uri)?.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
                out.flush()
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        // 写入完成后清除 pending，文件才会出现在「下载」里对用户可见。
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        runCatching { resolver.update(uri, values, null, null) }
        return "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$name"
    }

    /** Android 9 及以下：直接写公共「下载」目录（需要存储权限，失败返回 null 由调用方回退）。 */
    @Suppress("DEPRECATION")
    private fun saveToDownloadsLegacy(name: String, body: String): String? = try {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER,
        )
        if (!dir.exists() && !dir.mkdirs()) return null
        File(dir, name).writeText(body, Charsets.UTF_8)
        "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/$name"
    } catch (e: Exception) {
        null
    }

    /** 兜底：应用专属外部目录（无需权限），保证任何机型都能导出。 */
    private fun saveToAppDir(context: Context, name: String, body: String): String? = try {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        File(dir, name).writeText(body, Charsets.UTF_8)
        "${dir.absolutePath}/$name"
    } catch (e: Exception) {
        null
    }
}
