package com.pediycc.mydrawer.data

import android.content.Context
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/**
 * 应用清单的本地磁盘缓存（2.9.8）：加快启动速度。
 *
 * 未缓存前，每次 refresh() 都要对 ~190 个包逐个调用 loadLabel / loadIcon（跨进程 IPC +
 * 资源解析），冷启动会转圈。这里把这些「只取决于系统安装状态、与用户设置无关」的原始
 * 事实缓存下来：
 * - 元数据（包名 / 原始名称 / 系统标志 / 安装时间 / 是否有桌面入口）→ snapshot.json
 * - 图标 → icons/<包名>.png
 *
 * 缓存有效性由「包签名」判定：任一应用安装 / 卸载 / 更新（firstInstallTime、lastUpdateTime
 * 变化）、系统语言变化或缓存结构升级，签名都会变，触发一次全量重建。用户自身的设置
 * （分类、改名、隐藏、自定义图标等）不进签名，由 AppRepository 在读取缓存后用当前
 * prefs 重新套用，因此改设置无需重新扫描系统。
 *
 * 所有读写都吞异常：缓存只是加速手段，任何失败都能回退到全量扫描，绝不影响功能。
 */
object AppCache {
    /** 缓存结构版本：字段或图标规格变化时 +1，让旧缓存自动失效。 */
    const val VERSION = 1

    private const val DIR = "appcache"
    private const val ICON_DIR = "icons"
    private const val SNAPSHOT_FILE = "snapshot.json"
    private const val ICON_SIZE = 192
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class CachedApp(
        val packageName: String,
        /** 系统提供的原始名称（用户改名在读取时用 prefs 覆盖，恢复默认也靠它）。 */
        val label: String,
        val isSystem: Boolean,
        /** 分类规则表给出的默认分类（用户覆盖 / 删除分类在读取时用 prefs 修正）。 */
        val categoryId: String,
        val installTime: Long,
        val hasLauncher: Boolean,
    )

    @Serializable
    data class Snapshot(
        val version: Int,
        val signature: String,
        val apps: List<CachedApp>,
        val totalInstalled: Int,
    )

    private fun baseDir(context: Context) = File(context.filesDir, DIR)
    private fun iconsDir(context: Context) = File(baseDir(context), ICON_DIR)
    private fun iconFile(context: Context, pkg: String) = File(iconsDir(context), "$pkg.png")

    /**
     * 包签名：包名 + 首次安装时间 + 最后更新时间，排序后拼接，再附上语言与缓存版本。
     * 用拼接字符串整体比较（不做哈希），避免 Extremely unlikely 的哈希碰撞导致漏刷新。
     */
    fun signatureOf(packages: List<PackageInfo>): String {
        val sb = StringBuilder()
        packages.sortedBy { it.packageName }.forEach { p ->
            sb.append(p.packageName).append(':')
                .append(p.firstInstallTime).append(':')
                .append(p.lastUpdateTime).append(';')
        }
        sb.append("|locale=").append(Locale.getDefault().toString())
        sb.append("|cacheVer=").append(VERSION)
        return sb.toString()
    }

    /** 读取缓存；签名 / 版本不符、内容为空或任何异常都返回 null（调用方回退全量扫描）。 */
    fun load(context: Context, signature: String): Snapshot? = try {
        val raw = File(baseDir(context), SNAPSHOT_FILE).readText()
        val snap = json.decodeFromString<Snapshot>(raw)
        if (snap.version == VERSION && snap.signature == signature && snap.apps.isNotEmpty()) snap else null
    } catch (e: Exception) {
        null
    }

    /** 写入缓存：先重建图标目录（清掉已卸载应用的残留 png），再写元数据。 */
    fun save(context: Context, snapshot: Snapshot, icons: Map<String, Drawable?>) {
        try {
            val dir = baseDir(context)
            if (!dir.exists()) dir.mkdirs()
            val iDir = iconsDir(context)
            if (iDir.exists()) iDir.listFiles()?.forEach { runCatching { it.delete() } }
            if (!iDir.exists()) iDir.mkdirs()
            icons.forEach { (pkg, drawable) ->
                if (drawable == null) return@forEach
                val bmp = toBitmap(drawable) ?: return@forEach
                runCatching {
                    iconFile(context, pkg).outputStream().use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            }
            File(dir, SNAPSHOT_FILE).writeText(json.encodeToString(snapshot), Charsets.UTF_8)
        } catch (e: Exception) {
            // 缓存写失败不影响本次结果。
        }
    }

    /** 从磁盘读取单个图标；文件缺失 / 损坏返回 null，由调用方用系统图标兜底。 */
    fun loadBitmap(context: Context, pkg: String): Bitmap? = try {
        BitmapFactory.decodeFile(iconFile(context, pkg).absolutePath)
    } catch (e: Exception) {
        null
    }

    fun clear(context: Context) {
        runCatching { baseDir(context).deleteRecursively() }
    }

    /** Drawable → 定长画布位图，兼容自适应图标 / 矢量图；失败返回 null。 */
    fun toBitmap(drawable: Drawable): Bitmap? = try {
        val bmp = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
        drawable.draw(canvas)
        bmp
    } catch (e: Exception) {
        null
    }
}
