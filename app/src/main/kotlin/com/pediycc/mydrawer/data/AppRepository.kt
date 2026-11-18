package com.pediycc.mydrawer.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 负责读取全部已安装应用并结合规则表/用户覆盖给出分类与显示名。 */
class AppRepository(
    private val context: Context,
    private val catalog: CategoryCatalog,
    private val prefs: Preferences,
) {
    private val pm: PackageManager = context.packageManager

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val packages = pm.getInstalledPackages(0)
        // 系统抽屉里真正能看到的程序 = 声明了 MAIN + LAUNCHER 且处于启用状态的活动。
        // 注意不能用 getLaunchIntentForPackage：它会先匹配 CATEGORY_INFO，
        // 于是「系统跟踪」「开关控制」这类没有桌面入口、只有设置页的系统应用也会被当成可见，
        // 结果就是 MyDrawer 里多出一堆系统抽屉里根本没有的条目。
        val launcherPackages: Set<String> = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0,
            ).mapNotNull { it.activityInfo?.packageName }.toSet()
        }.getOrDefault(emptySet())
        val result = mutableListOf<AppInfo>()
        for (pkg in packages) {
            val appInfo: ApplicationInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            // 与系统抽屉一致：有桌面入口的（浏览器 / 应用商店 / 相机等）始终列出；
            // 没有入口的（系统跟踪、开关控制等纯系统进程）默认隐藏，由「显示系统应用」开关控制。
            val hasLauncher = pkg.packageName in launcherPackages
            if (!hasLauncher && !prefs.current.showSystemApps) continue

            val label = prefs.current.appLabels[pkg.packageName]
                ?: runCatching { appInfo.loadLabel(pm).toString() }
                    .getOrDefault(pkg.packageName)
            val icon = runCatching { appInfo.loadIcon(pm) }.getOrNull()
            val classified = prefs.current.categoryOverrides[pkg.packageName]
                ?: catalog.classify(pkg.packageName)
            // 分类被删除后，原本落在其中的应用统一回落到「未分类」，避免变成看不见的孤儿应用。
            val categoryId = if (classified in prefs.current.deletedCategories) Categories.OTHER else classified

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
        result
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
