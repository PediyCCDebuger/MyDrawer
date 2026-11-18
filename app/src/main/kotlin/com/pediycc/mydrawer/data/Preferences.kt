package com.pediycc.mydrawer.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class UserPrefs(
    val hiddenPackages: Set<String> = emptySet(),
    val categoryOverrides: Map<String, String> = emptyMap(),
    val sortMode: String = SortMode.BY_NAME,
    val gridColumns: Int = 4,
    val landscapeColumns: Int = 6,
    val showSystemApps: Boolean = false,
    // 2.0 新增：界面 / 展示
    val showCategoryLabels: Boolean = true,
    val showAppLabels: Boolean = true,
    /** 2.3.0 起界面固定纯黑不透明，该字段仅为兼容旧备份保留，不再影响绘制。 */
    val transparentBackground: Boolean = false,
    val leftHanded: Boolean = false,
    val stackFromBottom: Boolean = false,
    val showMenuButton: Boolean = true,
    val iconSizePercent: Int = 100,
    val frequentEnabled: Boolean = true,
    /** 是否在分类栏显示「全部」入口。 */
    val allEnabled: Boolean = true,
    // 2.0 新增：图标 / 名称覆盖与分类定制
    val categoryOrder: List<String> = emptyList(),
    val categoryLabels: Map<String, String> = emptyMap(),
    val categoryIcons: Map<String, String> = emptyMap(),
    val appLabels: Map<String, String> = emptyMap(),
    val appIcons: Map<String, String> = emptyMap(),
    /** 每个分类独立的排序方式，缺省时用全局 sortMode。 */
    val categorySortModes: Map<String, String> = emptyMap(),
    /** 手动排序：每个分类下包名顺序。 */
    val manualOrders: Map<String, List<String>> = emptyMap(),
    /** 使用频次：包名 -> 启动次数，用于「最常用」分类与按频率排序。 */
    val launchCounts: Map<String, Int> = emptyMap(),
    // 更新代理：默认直连 GitHub；可选公共反向代理（不含本机代理）
    val updateProxy: String = Updater.PROXY_DIRECT,
)

object SortMode {
    const val BY_NAME = "by_name"
    const val BY_INSTALL_TIME = "by_install_time"
    const val BY_USAGE = "by_usage"
    const val BY_CATEGORY = "by_category"
    const val MANUAL = "manual"

    /** 设置页展示顺序与中文名。 */
    val LABELS = listOf(
        BY_NAME to "按名称",
        BY_INSTALL_TIME to "按安装时间",
        BY_USAGE to "按使用频率",
        BY_CATEGORY to "按分类",
        MANUAL to "手动排序",
    )

    fun labelOf(mode: String): String = LABELS.firstOrNull { it.first == mode }?.second ?: "按名称"
}

/** 用 SharedPreferences 存储用户设置。旧备份缺失的新字段由 kotlinx.serialization 补默认值。 */
class Preferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mydrawer_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var current: UserPrefs = load()
        private set

    private fun load(): UserPrefs {
        val raw = prefs.getString(KEY_PREFS, null) ?: return UserPrefs()
        val loaded = try {
            json.decodeFromString<UserPrefs>(raw)
        } catch (_: Exception) {
            UserPrefs()
        }
        // 2.1 迁移：移除旧版内置的本机代理 127.0.0.1:1080（那是开发用，普通设备无此服务）。
        return if (loaded.updateProxy !in Updater.PROXIES) {
            loaded.copy(updateProxy = Updater.PROXY_DIRECT)
        } else loaded
    }

    private fun save() {
        prefs.edit().putString(KEY_PREFS, json.encodeToString(current)).apply()
    }

    fun update(transform: (UserPrefs) -> UserPrefs) {
        current = transform(current)
        save()
    }

    fun isHidden(packageName: String): Boolean = packageName in current.hiddenPackages

    fun toggleHidden(packageName: String) {
        update { p ->
            val next = p.hiddenPackages.toMutableSet()
            if (!next.add(packageName)) next.remove(packageName)
            p.copy(hiddenPackages = next)
        }
    }

    fun overrideCategory(packageName: String, categoryId: String) {
        update { p -> p.copy(categoryOverrides = p.categoryOverrides + (packageName to categoryId)) }
    }

    /** 批量移动：覆盖多个包的分类。 */
    fun overrideCategories(packageNames: Collection<String>, categoryId: String) {
        update { p ->
            val next = p.categoryOverrides.toMutableMap()
            packageNames.forEach { next[it] = categoryId }
            p.copy(categoryOverrides = next)
        }
    }

    fun renameApp(packageName: String, label: String) {
        update { p -> p.copy(appLabels = p.appLabels + (packageName to label)) }
    }

    fun setAppIcon(packageName: String, key: String) {
        update { p -> p.copy(appIcons = p.appIcons + (packageName to key)) }
    }

    fun renameCategory(categoryId: String, label: String) {
        update { p -> p.copy(categoryLabels = p.categoryLabels + (categoryId to label)) }
    }

    fun setCategoryIcon(categoryId: String, key: String) {
        update { p -> p.copy(categoryIcons = p.categoryIcons + (categoryId to key)) }
    }

    fun setCategorySort(categoryId: String, mode: String) {
        update { p -> p.copy(categorySortModes = p.categorySortModes + (categoryId to mode)) }
    }

    fun setManualOrder(categoryId: String, order: List<String>) {
        update { p -> p.copy(manualOrders = p.manualOrders + (categoryId to order)) }
    }

    /** 在手动排序里把某个应用上移 / 下移一位。 */
    fun moveManual(categoryId: String, packageName: String, delta: Int) {
        update { p ->
            val list = (p.manualOrders[categoryId] ?: emptyList()).toMutableList()
            val from = list.indexOf(packageName)
            if (from < 0) return@update p
            val to = (from + delta).coerceIn(0, list.lastIndex)
            if (to == from) return@update p
            java.util.Collections.swap(list, from, to)
            p.copy(manualOrders = p.manualOrders + (categoryId to list))
        }
    }

    fun recordLaunch(packageName: String) {
        update { p ->
            p.copy(launchCounts = p.launchCounts + (packageName to (p.launchCounts[packageName] ?: 0) + 1))
        }
    }

    fun replaceAll(prefs: UserPrefs) {
        current = prefs
        save()
    }

    companion object {
        private const val KEY_PREFS = "user_prefs_v1"
    }
}
