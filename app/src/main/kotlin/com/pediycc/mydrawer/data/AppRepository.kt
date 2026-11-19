package com.pediycc.mydrawer.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 负责读取全部已安装应用并结合规则表/用户覆盖给出分类与显示名。 */
class AppRepository(
    private val context: Context,
    private val catalog: CategoryCatalog,
    private val prefs: Preferences,
) {
    private val pm: PackageManager = context.packageManager

    /** 因没有桌面入口、且未开启「显示系统应用」而未进入抽屉的应用。 */
    data class UnlistedApp(val packageName: String, val label: String)

    /**
     * 载入结果：除了应用列表本身，还带上「本机安装包总数」和被过滤掉的应用，
     * 供设置页核对数量差异（例如系统显示 192 个而抽屉列出 190 个时，能看清少的是哪两个）。
     */
    data class LoadResult(
        val apps: List<AppInfo>,
        val totalInstalled: Int,
        val unlisted: List<UnlistedApp>,
    )

    /**
     * 2.9.8：包签名 → 已还原的「原始列表」，进程内复用，避免同一次使用里反复读盘解码图标。
     * 原始列表只含与用户设置无关的事实（包名 / 系统原名 / 分类规则 / 安装时间 / 图标 /
     * 有无桌面入口），每次 refresh 再套用当前 prefs，故改分类、改名都能即时生效。
     * 签名变化（装/卸/更新应用、切语言）时作废重建。仅内存，不跨进程。
     */
    private var memorySignature: String? = null
    private var memoryRaw: List<AppInfo>? = null

    suspend fun loadApps(): LoadResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val packages = pm.getInstalledPackages(0)
        val signature = AppCache.signatureOf(packages)

        val raw: List<AppInfo>
        val fromCache: Boolean
        if (memorySignature == signature && memoryRaw != null) {
            raw = memoryRaw!!
            fromCache = true
        } else {
            val snap = AppCache.load(context, signature)
            if (snap != null) {
                // 命中磁盘缓存：图标读 PNG，不再逐包 loadIcon（这是启动转圈的主因）。
                raw = snap.apps.map { c ->
                    val icon: Drawable? =
                        AppCache.loadBitmap(context, c.packageName)?.let { BitmapDrawable(context.resources, it) }
                            ?: runCatching { originalIcon(c.packageName) }.getOrNull()
                    AppInfo(
                        packageName = c.packageName,
                        label = c.label,
                        icon = icon,
                        isSystem = c.isSystem,
                        // 缓存里存的是分类规则结果；删除分类在下方按 prefs 兜底到「未分类」。
                        categoryId = c.categoryId,
                        installTime = c.installTime,
                        hasLauncher = c.hasLauncher,
                    )
                }
                fromCache = true
            } else {
                // 未命中：全量扫描系统并写缓存，只在首次启动 / 应用有增删时走一次。
                raw = scanAndCache(packages, signature)
                fromCache = false
            }
            memorySignature = signature
            memoryRaw = raw
        }

        // 套用用户设置：重命名、分类覆盖 / 删除分类兜底、无桌面入口过滤。
        // 这些放在缓存还原之后，改设置无需作废缓存。
        val p = prefs.current
        val result = mutableListOf<AppInfo>()
        val unlisted = mutableListOf<UnlistedApp>()
        for (app in raw) {
            val label = p.appLabels[app.packageName] ?: app.label
            if (!app.hasLauncher && !p.showSystemApps) {
                unlisted += UnlistedApp(app.packageName, label)
                continue
            }
            val classified = p.categoryOverrides[app.packageName] ?: app.categoryId
            val categoryId = if (classified in p.deletedCategories) Categories.OTHER else classified
            result += app.copy(label = label, categoryId = categoryId)
        }

        DebugLog.log(
            context,
            "载入应用：${if (fromCache) "缓存" else "全量扫描"}，列出 ${result.size}，" +
                "本机安装 ${packages.size}，无桌面入口未列出 ${unlisted.size}，" +
                "耗时 ${System.currentTimeMillis() - startMs}ms",
        )
        LoadResult(result, packages.size, unlisted.sortedWith(compareBy { it.label }))
    }

    /**
     * 全量扫描 + 写缓存。逐包 loadLabel / loadIcon 的慢路径，只在没有可用缓存时执行。
     * 返回「全部包」的原始 AppInfo（含无桌面入口的），过滤交给调用方按 prefs 完成。
     */
    private fun scanAndCache(
        packages: List<PackageInfo>,
        signature: String,
    ): List<AppInfo> {
        // 系统抽屉里真正能看到的程序 = 声明了 MAIN + LAUNCHER 且处于启用状态的活动。
        // 不用 getLaunchIntentForPackage：它会先匹配 CATEGORY_INFO，于是「系统跟踪」「开关控制」
        // 这类只有设置页的系统应用也会被当成可见，比系统抽屉多出一堆条目。
        val launcherPackages: Set<String> = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0,
            ).mapNotNull { it.activityInfo?.packageName }.toSet()
        }.getOrDefault(emptySet())

        val result = mutableListOf<AppInfo>()
        val cachedApps = mutableListOf<AppCache.CachedApp>()
        val icons = mutableMapOf<String, Drawable?>()
        for (pkg in packages) {
            val appInfo: ApplicationInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val label = runCatching { appInfo.loadLabel(pm).toString() }
                .getOrDefault(pkg.packageName)
            val hasLauncher = pkg.packageName in launcherPackages
            val categoryId = catalog.classify(pkg.packageName)
            cachedApps += AppCache.CachedApp(
                packageName = pkg.packageName,
                label = label,
                isSystem = isSystem,
                categoryId = categoryId,
                installTime = pkg.firstInstallTime,
                hasLauncher = hasLauncher,
            )
            val icon = runCatching { appInfo.loadIcon(pm) }.getOrNull()
            icons[pkg.packageName] = icon
            result += AppInfo(
                packageName = pkg.packageName,
                label = label,
                icon = icon,
                isSystem = isSystem,
                categoryId = categoryId,
                installTime = pkg.firstInstallTime,
                hasLauncher = hasLauncher,
            )
        }
        AppCache.save(
            context,
            AppCache.Snapshot(AppCache.VERSION, signature, cachedApps, packages.size),
            icons,
        )
        return result
    }

    /** 缓存图标文件丢失时，用系统图标兜底单个应用（不会退化成全量扫描）。 */
    private fun originalIcon(packageName: String): Drawable? = try {
        pm.getApplicationInfo(packageName, 0).loadIcon(pm)
    } catch (e: Exception) {
        null
    }

    /** 返回某个应用原始（系统提供的）名称与图标，供「恢复默认 / 首选原图标」使用。 */
    fun originalApp(packageName: String): Pair<String, android.graphics.drawable.Drawable?>? = try {
        val info = pm.getApplicationInfo(packageName, 0)
        info.loadLabel(pm).toString() to runCatching { info.loadIcon(pm) }.getOrNull()
    } catch (e: Exception) {
        null
    }

    fun getLaunchIntent(packageName: String) = pm.getLaunchIntentForPackage(packageName)
}
