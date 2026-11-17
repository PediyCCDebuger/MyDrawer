package com.pediycc.mydrawer.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 负责读取全部已安装应用并结合规则表/用户覆盖给出分类。 */
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
            // 系统应用默认不展示（用户可在设置里打开）
            if (isSystem && !prefs.current.showSystemApps) continue

            val label = runCatching { appInfo.loadLabel(pm).toString() }
                .getOrDefault(pkg.packageName)
            val icon = runCatching { appInfo.loadIcon(pm) }.getOrNull()
            val categoryId = prefs.current.categoryOverrides[pkg.packageName]
                ?: catalog.classify(pkg.packageName)

            result += AppInfo(
                packageName = pkg.packageName,
                label = label,
                icon = icon,
                isSystem = isSystem,
                categoryId = categoryId,
            )
        }
        result
    }

    fun getLaunchIntent(packageName: String) = pm.getLaunchIntentForPackage(packageName)
}
