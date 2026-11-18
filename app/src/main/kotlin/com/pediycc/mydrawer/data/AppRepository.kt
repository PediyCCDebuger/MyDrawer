package com.pediycc.mydrawer.data

import android.content.Context
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
        val result = mutableListOf<AppInfo>()
        for (pkg in packages) {
            val appInfo: ApplicationInfo = pkg.applicationInfo ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            // 系统应用里「有启动入口」的（浏览器 / 应用商店 / 相机等）在原生系统抽屉里本就可见，
            // 属于普通应用，始终列出；「显示系统应用」只控制那些无入口的纯系统进程。
            val hasLauncher = pm.getLaunchIntentForPackage(pkg.packageName) != null
            if (isSystem && !hasLauncher && !prefs.current.showSystemApps) continue

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
