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
    /** 分类栏图标大小，默认比应用图标略大。 */
    val categoryIconSizePercent: Int = 130,
    /** 图标规范化：给第三方应用图标留一圈内边距，避免异形图标撑满格子。 */
    val normalizeIcons: Boolean = true,
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
    // 2.4.1 新增：首次进入且未授予使用情况访问权限时提示一次
    val usagePromptShown: Boolean = false,
    /** 2.4.1 新增：用户自建分类的 id 列表（名称/图标复用 categoryLabels/categoryIcons）。 */
    val customCategories: List<String> = emptyList(),
    /** 2.4.1 新增：被删除的内置分类 id；这些入口不再显示，其下应用回落到「未分类」。 */
    val deletedCategories: Set<String> = emptySet(),
    /** 2.5.0 新增：上次离开时停留的分类，用于「启动时进入上次分类」。 */
    val lastCategory: String = "all",
    /**
     * 2.5.0 新增：启动 APP 时进入的分类。
     * 空字符串 = 进入上次使用的分类（[lastCategory]）；否则固定进入该分类 id / "all"。
     */
    val startupCategory: String = "",
    /**
     * 2.6.0 新增：管理隐藏应用前需要输入的口令摘要（SHA-256 + 固定盐）。
     * 空字符串表示未启用密码；只存摘要，不存明文。
     */
    val hiddenAppsPasswordHash: String = "",
    // 2.7.0 新增：分类嵌套。childId -> parentId；没有映射即为顶层分类。
    /** 分类的父级：[子分类 id] -> [父分类 id]。嵌套分类不再单独占分类栏位置，而在父分类内容里显示。 */
    val categoryParents: Map<String, String> = emptyMap(),
    /** 父分类里「子分类」相对应用的位置：front=排在所有应用之前，back=排在所有应用之后。 */
    val nestedChildMode: String = NestedChildMode.FRONT,
    /** 手动排序时，父分类里子分类之间的顺序：parentId -> 子分类 id 顺序。 */
    val nestedChildOrders: Map<String, List<String>> = emptyMap(),
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

/** 父分类内「子分类」相对应用的位置。 */
object NestedChildMode {
    const val FRONT = "front" // 子分类排在全部应用之前
    const val BACK = "back"   // 子分类排在全部应用之后
    val LABELS = listOf(
        FRONT to "子分类排在应用前",
        BACK to "子分类排在应用后",
    )
    fun labelOf(mode: String): String = LABELS.firstOrNull { it.first == mode }?.second ?: "子分类排在应用前"
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

    /** 是否已设置「管理隐藏应用」密码。 */
    fun hasPassword(): Boolean = current.hiddenAppsPasswordHash.isNotBlank()

    /** 校验输入口令是否正确。 */
    fun verifyPassword(raw: String): Boolean = PasswordHash.verify(raw, current.hiddenAppsPasswordHash)

    /** 设置 / 修改口令；空字符串等同清除。 */
    fun setPassword(raw: String) {
        update { it.copy(hiddenAppsPasswordHash = if (raw.isEmpty()) "" else PasswordHash.hash(raw)) }
    }

    /** 清除口令。 */
    fun clearPassword() {
        update { it.copy(hiddenAppsPasswordHash = "") }
    }

    fun toggleHidden(packageName: String) {
        update { p ->
            val next = p.hiddenPackages.toMutableSet()
            if (!next.add(packageName)) next.remove(packageName)
            p.copy(hiddenPackages = next)
        }
    }

    /** 批量隐藏：把多个包加入隐藏集合（已隐藏的保持不变）。 */
    fun hidePackages(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        update { p -> p.copy(hiddenPackages = p.hiddenPackages + packageNames) }
    }

    /** 批量恢复：把多个包从隐藏集合里移除。 */
    fun unhidePackages(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        update { p -> p.copy(hiddenPackages = p.hiddenPackages - packageNames) }
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

    /** 清除所有自定义应用 / 分类图标，回到内置图库与系统图标。 */
    fun resetCustomIcons() {
        update { it.copy(appIcons = emptyMap(), categoryIcons = emptyMap()) }
    }

    fun recordLaunch(packageName: String) {
        update { p ->
            p.copy(launchCounts = p.launchCounts + (packageName to (p.launchCounts[packageName] ?: 0) + 1))
        }
    }

    /** 首次进入时的使用频率授权提示只弹一次。 */
    fun markUsagePromptShown() {
        update { it.copy(usagePromptShown = true) }
    }

    /** 新建自定义分类，返回其 id；名称/图标复用 categoryLabels / categoryIcons。 */
    fun addCategory(label: String): String {
        val id = "custom_" + System.currentTimeMillis()
        update { p ->
            p.copy(
                customCategories = p.customCategories + id,
                categoryLabels = p.categoryLabels + (id to label),
                categoryIcons = p.categoryIcons + (id to "builtin:apps"),
                categoryOrder = p.categoryOrder + id,
            )
        }
        return id
    }

    /** 删除分类：内置与自定义都可删；「未分类」和「最常用」是兜底/特殊入口，保留。 */
    fun deleteCategory(id: String) {
        if (id == Categories.OTHER || id == Categories.FREQUENT) return
        update { p ->
            p.copy(
                deletedCategories = p.deletedCategories + id,
                customCategories = p.customCategories.filter { it != id },
                categoryOrder = p.categoryOrder.filter { it != id },
                categoryLabels = p.categoryLabels - id,
                categoryIcons = p.categoryIcons - id,
                categorySortModes = p.categorySortModes - id,
                manualOrders = p.manualOrders - id,
            )
        }
    }

    /** 把子分类挂到父分类下；传空 parentId 表示移回顶层。防止自挂与成环。 */
    fun setCategoryParent(childId: String, parentId: String): Boolean {
        if (childId == parentId) return false
        if (Categories.isDescendant(parentId, childId, current.categoryParents)) return false // 父是子的后代会成环
        update { p ->
            val next = if (parentId.isEmpty()) p.categoryParents - childId
            else p.categoryParents + (childId to parentId)
            p.copy(categoryParents = next)
        }
        return true
    }

    /** 设置某父分类下子分类的手动顺序。 */
    fun setNestedChildOrder(parentId: String, order: List<String>) {
        update { p -> p.copy(nestedChildOrders = p.nestedChildOrders + (parentId to order)) }
    }

    fun setNestedChildMode(mode: String) {
        update { it.copy(nestedChildMode = mode) }
    }

    fun replaceAll(prefs: UserPrefs) {
        current = prefs
        save()
    }

    companion object {
        private const val KEY_PREFS = "user_prefs_v1"
    }
}
