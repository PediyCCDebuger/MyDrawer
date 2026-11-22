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
    const val VERSION = 2

    private const val DIR = "appcache"
    private const val TMP_DIR = "appcache_tmp"
    private const val BACKUP_DIR = "appcache_old"
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
        /** 最后更新时间：与 installTime 一起判断某个应用是被更新还是仅装/卸了别的应用。 */
        val lastUpdateTime: Long,
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

    /** 取签名里的语言部分，用于判断是否为语言切换（名称需全量更新，不能增量复用）。 */
    fun localePart(signature: String): String =
        signature.substringAfter("|locale=", "").substringBefore("|")

    /**
     * 某个包能否复用旧缓存：首次安装时间与最后更新时间都没变才算未变动。
     * 更新时间变化说明应用被更新（图标 / 名称可能变），必须现场重读。
     */
    fun canReuse(cached: CachedApp?, firstInstallTime: Long, lastUpdateTime: Long): Boolean =
        cached != null && cached.installTime == firstInstallTime && cached.lastUpdateTime == lastUpdateTime

    /** 读取缓存；签名 / 版本不符、内容为空或任何异常都返回 null（调用方回退全量扫描）。 */
    fun load(context: Context, signature: String): Snapshot? = try {
        val raw = File(baseDir(context), SNAPSHOT_FILE).readText()
        val snap = json.decodeFromString<Snapshot>(raw)
        if (snap.version == VERSION && snap.signature == signature && snap.apps.isNotEmpty()) snap else null
    } catch (e: Exception) {
        null
    }

    /**
     * 2.9.15：读取旧快照用于「增量复用」——只校验版本、不校验包签名。
     * 安装 / 卸载 / 更新任一应用都会让签名变化，若因此整份缓存作废，下次冷启动就要在前台
     * 对全部包逐个 loadLabel / loadIcon（几百毫秒到数秒，被用户感知为「启动很慢」）。
     * 实际上绝大多数包没变，可继续用这里缓存的名字 / 分类 / 图标，只重读少数变化的包。
     * 签名不同也能安全复用：具体哪个包变了，由 [AppRepository] 逐包比对时间戳决定。
     */
    fun loadForReuse(context: Context): Snapshot? = try {
        val raw = File(baseDir(context), SNAPSHOT_FILE).readText()
        val snap = json.decodeFromString<Snapshot>(raw)
        if (snap.version == VERSION && snap.apps.isNotEmpty()) snap else null
    } catch (e: Exception) {
        null
    }

    /**
     * 原子写入缓存：先全部写进临时目录，成功后再整体切换，避免写入中途被杀 / 断电
     * 留下「snapshot 是新的一批、图标只写了一半」的不一致缓存（那会让部分应用显示成默认图标）。
     * 步骤：写 tmp → 旧目录改名 old → tmp 改名正式 → 删 old。任何一步失败都保留旧缓存。
     */
    fun save(context: Context, snapshot: Snapshot, icons: Map<String, Drawable?>) {
        val filesDir = context.filesDir
        val tmp = File(filesDir, TMP_DIR)
        val old = File(filesDir, BACKUP_DIR)
        val live = File(filesDir, DIR)
        try {
            runCatching { tmp.deleteRecursively() }
            if (!tmp.mkdirs()) return
            val tmpIcons = File(tmp, ICON_DIR)
            if (!tmpIcons.mkdirs()) return
            icons.forEach { (pkg, drawable) ->
                if (drawable == null) return@forEach
                val bmp = toBitmap(drawable) ?: return@forEach
                runCatching {
                    File(tmpIcons, "$pkg.png").outputStream().use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            }
            File(tmp, SNAPSHOT_FILE).writeText(json.encodeToString(snapshot), Charsets.UTF_8)

            // 切换：先移走旧目录 → tmp 上位 → 删除旧目录。
            runCatching { old.deleteRecursively() }
            if (live.exists() && !live.renameTo(old)) return
            if (!tmp.renameTo(live)) {
                // 上位失败：把旧目录还原，保证正式目录仍可用。
                if (old.exists()) runCatching { old.renameTo(live) }
                return
            }
            runCatching { old.deleteRecursively() }
        } catch (e: Exception) {
            // 缓存写失败不影响本次结果；清理临时目录，下次重新全量扫描再写。
            runCatching { tmp.deleteRecursively() }
        }
    }

    /** 从磁盘读取单个图标；文件缺失 / 损坏返回 null，由调用方用系统图标兜底。 */
    fun loadBitmap(context: Context, pkg: String): Bitmap? = try {
        BitmapFactory.decodeFile(iconFile(context, pkg).absolutePath)
    } catch (e: Exception) {
        null
    }

    fun clear(context: Context) {
        val filesDir = context.filesDir
        listOf(DIR, TMP_DIR, BACKUP_DIR).forEach { name ->
            runCatching { File(filesDir, name).deleteRecursively() }
        }
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
