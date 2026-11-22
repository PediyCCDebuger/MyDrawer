package com.pediycc.mydrawer.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    @Volatile
    private var memorySignature: String? = null

    @Volatile
    private var memoryRaw: List<AppInfo>? = null

    /** 2.9.10：后台建缓存用的独立作用域；只写磁盘缓存，不持有界面引用。 */
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 手动重建缓存时连进程内那份一起失效，否则点完「重建」其实仍走内存缓存、磁盘缓存根本没重建。 */
    fun invalidateMemory() {
        memorySignature = null
        memoryRaw = null
    }

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
            when {
                snap != null -> {
                    // 签名完全一致：命中磁盘缓存，图标读 PNG，不再逐包 loadIcon（启动转圈的主因）。
                    raw = restoreFromSnapshot(snap)
                    fromCache = true
                }
                // 2.9.15：签名变了（装/卸/更新应用），但语言没变时，绝大多数包其实没动。
                // 复用旧缓存里未变动包的名字 / 分类 / 图标，只前台重读真正变化的少数包，
                // 避免每次装个应用就把整份缓存作废、冷启动在前台全量扫描。完整快照仍后台重建。
                else -> {
                    val reusable = AppCache.loadForReuse(context)
                    if (reusable != null && AppCache.localePart(reusable.signature) == AppCache.localePart(signature)) {
                        val inc = incrementalRebuild(packages, reusable)
                        raw = inc.raw
                        fromCache = true
                        scheduleCacheBuild(packages, signature, inc.cachedApps)
                    } else {
                        // 没有旧缓存，或切换了系统语言（所有名字都要更新）：首屏快扫 + 后台建缓存。
                        val fast = scanForUi(packages)
                        raw = fast.raw
                        fromCache = false
                        scheduleCacheBuild(packages, signature, fast.cachedApps)
                    }
                }
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
            // 首屏快扫没读隐藏的系统应用图标；用户中途开启「显示系统应用」时按需补，避免出现一片默认图标。
            val icon = if (app.icon == null) originalIcon(app.packageName) else app.icon
            result += app.copy(label = label, icon = icon, categoryId = categoryId)
        }

        DebugLog.log(
            context,
            "载入应用：${if (fromCache) "缓存" else "首次扫描（缓存后台建立）"}，列出 ${result.size}，" +
                "本机安装 ${packages.size}，无桌面入口未列出 ${unlisted.size}，" +
                "耗时 ${System.currentTimeMillis() - startMs}ms",
        )
        LoadResult(result, packages.size, unlisted.sortedWith(compareBy { it.label }))
    }

    private data class FastScanResult(
        val raw: List<AppInfo>,
        val cachedApps: List<AppCache.CachedApp>,
    )

    /** 从完整快照还原原始列表：图标读 PNG，缺失时用系统图标兜底。 */
    private fun restoreFromSnapshot(snap: AppCache.Snapshot): List<AppInfo> =
        snap.apps.map { c ->
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

    /**
     * 2.9.15：签名变化后的增量重建。语言未变时，逐个包比对 firstInstallTime / lastUpdateTime：
     * - 未变动的包（绝大多数）：名字 / 分类 / 系统标志 / 桌面入口直接取自旧快照，图标读旧 PNG，
     *   不做任何跨进程 loadLabel / loadIcon；
     * - 真正变动的包（新装 / 更新）：才现场读一次，数量通常个位数；
     * - 被卸载的包不在当前 packages 里，自然消失。
     * 返回完整列表（供本次显示）与新的元数据列表（供后台按新签名重写缓存）。
     */
    private fun incrementalRebuild(
        packages: List<PackageInfo>,
        old: AppCache.Snapshot,
    ): FastScanResult {
        val startMs = System.currentTimeMillis()
        val oldByPkg = old.apps.associateBy { it.packageName }
        val showSystem = prefs.current.showSystemApps
        val result = mutableListOf<AppInfo>()
        val cachedApps = mutableListOf<AppCache.CachedApp>()
        var reused = 0
        var rescanned = 0
        // 只有变动的包才需要判断桌面入口；复用包直接用快照里的 hasLauncher，省掉这次查询。
        var launcherPackages: Set<String>? = null
        for (pkg in packages) {
            val appInfo: ApplicationInfo = pkg.applicationInfo ?: continue
            val cached = oldByPkg[pkg.packageName]
            val unchanged = AppCache.canReuse(cached, pkg.firstInstallTime, pkg.lastUpdateTime)
            if (unchanged) {
                reused++
                val icon: Drawable? =
                    AppCache.loadBitmap(context, pkg.packageName)?.let { BitmapDrawable(context.resources, it) }
                        ?: runCatching { originalIcon(pkg.packageName) }.getOrNull()
                result += AppInfo(
                    packageName = cached!!.packageName,
                    label = cached.label,
                    icon = icon,
                    isSystem = cached.isSystem,
                    categoryId = cached.categoryId,
                    installTime = cached.installTime,
                    hasLauncher = cached.hasLauncher,
                )
                cachedApps += cached
            } else {
                rescanned++
                if (launcherPackages == null) {
                    launcherPackages = runCatching {
                        pm.queryIntentActivities(
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                            0,
                        ).mapNotNull { it.activityInfo?.packageName }.toSet()
                    }.getOrDefault(emptySet())
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(pkg.packageName)
                val hasLauncher = pkg.packageName in launcherPackages!!
                val categoryId = catalog.classify(pkg.packageName)
                val icon = if (hasLauncher || showSystem) {
                    runCatching { appInfo.loadIcon(pm) }.getOrNull()
                } else null
                cachedApps += AppCache.CachedApp(
                    packageName = pkg.packageName,
                    label = label,
                    isSystem = isSystem,
                    categoryId = categoryId,
                    installTime = pkg.firstInstallTime,
                    lastUpdateTime = pkg.lastUpdateTime,
                    hasLauncher = hasLauncher,
                )
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
        }
        DebugLog.log(
            context,
            "增量载入：复用 ${reused} 个，重读 ${rescanned} 个（装/卸/更新），" +
                "耗时 ${System.currentTimeMillis() - startMs}ms",
        )
        return FastScanResult(result, cachedApps)
    }

    /**
     * 2.9.10：缓存未命中时的首屏扫描。与 2.9.9 的「全量扫描 + 写缓存」区别：
     * - 所有包都读取元数据（名称 / 系统标志 / 桌面入口 / 安装时间 / 默认分类），
     *   这些是构建完整缓存也需要的，不重复做；
     * - 只有「当前会显示」的应用才读图标；无桌面入口且未开启「显示系统应用」的
     *   372 个包不再逐个 loadIcon，这正是首屏多花一两秒的地方；
     * - 不写 PNG、不搬目录，编码与落盘全部交给 [scheduleCacheBuild] 在后台完成。
     */
    private fun scanForUi(packages: List<PackageInfo>): FastScanResult {
        val scanStartMs = System.currentTimeMillis()
        // 系统抽屉里真正能看到的程序 = 声明了 MAIN + LAUNCHER 且处于启用状态的活动。
        // 不用 getLaunchIntentForPackage：它会先匹配 CATEGORY_INFO，于是「系统跟踪」「开关控制」
        // 这类只有设置页的系统应用也会被当成可见，比系统抽屉多出一堆条目。
        val launcherStartMs = System.currentTimeMillis()
        val launcherPackages: Set<String> = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0,
            ).mapNotNull { it.activityInfo?.packageName }.toSet()
        }.getOrDefault(emptySet())
        val launcherMs = System.currentTimeMillis() - launcherStartMs

        val showSystem = prefs.current.showSystemApps
        val result = mutableListOf<AppInfo>()
        val cachedApps = mutableListOf<AppCache.CachedApp>()
        var labelMs = 0L
        var iconMs = 0L
        for (pkg in packages) {
            val appInfo: ApplicationInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val labelStartMs = System.currentTimeMillis()
            val label = runCatching { appInfo.loadLabel(pm).toString() }
                .getOrDefault(pkg.packageName)
            labelMs += System.currentTimeMillis() - labelStartMs
            val hasLauncher = pkg.packageName in launcherPackages
            val categoryId = catalog.classify(pkg.packageName)
            cachedApps += AppCache.CachedApp(
                packageName = pkg.packageName,
                label = label,
                isSystem = isSystem,
                categoryId = categoryId,
                installTime = pkg.firstInstallTime,
                lastUpdateTime = pkg.lastUpdateTime,
                hasLauncher = hasLauncher,
            )
            // 只在应用会出现在抽屉里时读图标；其余留给后台建缓存，或打开「显示系统应用」时按需补。
            val icon = if (hasLauncher || showSystem) {
                val iconStartMs = System.currentTimeMillis()
                val value = runCatching { appInfo.loadIcon(pm) }.getOrNull()
                iconMs += System.currentTimeMillis() - iconStartMs
                value
            } else {
                null
            }
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
        val totalMs = System.currentTimeMillis() - scanStartMs
        DebugLog.log(
            context,
            "首次扫描明细：桌面入口 ${launcherMs}ms，读取名称 ${labelMs}ms，" +
                "可见应用图标 ${iconMs}ms，包信息 ${(totalMs - launcherMs - labelMs - iconMs).coerceAtLeast(0)}ms",
        )
        return FastScanResult(result, cachedApps)
    }

    /**
     * 2.9.10：后台把完整缓存写出来。首屏数据已经交给界面，这里再慢也不影响启动。
     * 图标写前 / 写后各校验一次包签名：期间发生了安装 / 卸载 / 更新 / 语言切换就放弃，
     * 避免把过期数据写成有效缓存（下一次启动会重新扫描）。
     */
    private fun scheduleCacheBuild(
        packages: List<PackageInfo>,
        signature: String,
        cachedApps: List<AppCache.CachedApp>,
    ) {
        cacheScope.launch {
            val startMs = System.currentTimeMillis()
            try {
                if (AppCache.signatureOf(pm.getInstalledPackages(0)) != signature) {
                    DebugLog.log(context, "后台建立缓存跳过：应用列表已变化")
                    return@launch
                }
                val icons = HashMap<String, Drawable?>(packages.size)
                for (pkg in packages) {
                    val appInfo = pkg.applicationInfo ?: continue
                    icons[pkg.packageName] = runCatching { appInfo.loadIcon(pm) }.getOrNull()
                }
                if (AppCache.signatureOf(pm.getInstalledPackages(0)) != signature) {
                    DebugLog.log(context, "后台建立缓存跳过：读取图标期间应用列表已变化")
                    return@launch
                }
                AppCache.save(
                    context,
                    AppCache.Snapshot(AppCache.VERSION, signature, cachedApps, packages.size),
                    icons,
                )
                DebugLog.log(
                    context,
                    "后台建立应用列表缓存：${icons.size} 个图标，耗时 ${System.currentTimeMillis() - startMs}ms",
                )
            } catch (e: Exception) {
                DebugLog.log(context, "后台建立缓存失败：${e.message}")
            }
        }
    }

    /**
     * 2.9.9：当前系统包签名是否与上次载入时不同。用于回到抽屉时快速判断是否发生了
     * 安装 / 卸载 / 更新 / 语言切换。调用方应放到 IO 线程。
     */
    fun signatureChanged(): Boolean {
        val last = memorySignature ?: return false  // 尚未载入过，交由 refresh 处理
        val current = AppCache.signatureOf(pm.getInstalledPackages(0))
        return current != last
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
