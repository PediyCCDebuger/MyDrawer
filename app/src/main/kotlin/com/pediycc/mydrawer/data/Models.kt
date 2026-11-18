package com.pediycc.mydrawer.data

import android.graphics.drawable.Drawable

/** 一个已安装应用的展示模型。 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean,
    val categoryId: String,
    /** 首次安装时间，用于「按安装时间」排序；0 表示读取失败。 */
    val installTime: Long = 0L,
    /**
     * 是否有桌面启动入口。系统应用里有启动入口的（浏览器 / 应用商店 / 相机等）
     * 在原生系统抽屉里本就可见，应作为普通应用始终列出；只有无入口的纯系统进程
     * 才由「显示系统应用」开关控制。默认 true 保持旧调用 / 旧测试行为不变。
     */
    val hasLauncher: Boolean = true,
)

/** 自动分类规则表中的一条：包名 -> 分类 id。 */
data class CategoryRule(
    val packageName: String,
    val categoryId: String,
)

/** 分类定义。id 与原版 fC 值保持一致，便于复用规则表；iconKey 为内置图库键。 */
data class CategoryDef(
    val id: String,
    val label: String,
    val iconKey: String = "",
)

object Categories {
    // 顺序即抽屉中的默认分类顺序；OTHER 必须放最后作为兜底。
    val ALL = listOf(
        CategoryDef("games", "游戏", "builtin:sports_esports"),
        CategoryDef("internet", "上网", "builtin:language"),
        CategoryDef("media", "影音", "builtin:movie"),
        CategoryDef("phone", "通讯", "builtin:phone"),
        CategoryDef("utility", "工具", "builtin:build"),
        CategoryDef("settings", "设置", "builtin:settings"),
        CategoryDef("other", "未分类", "builtin:apps"),
    )
    const val OTHER = "other"
    const val FREQUENT = "frequent"
    val FREQUENT_DEF = CategoryDef(FREQUENT, "最常用", "builtin:star")

    private val labelById = ALL.associateBy { it.id }

    fun labelOf(id: String): String =
        if (id == FREQUENT) FREQUENT_DEF.label else labelById[id]?.label ?: "未分类"

    fun iconKeyOf(id: String): String =
        labelById[id]?.iconKey ?: if (id == FREQUENT) FREQUENT_DEF.iconKey else "builtin:apps"

    fun isValid(id: String): Boolean = labelById.containsKey(id) || id == FREQUENT

    /**
     * 按用户顺序返回分类；缺省时「最常用」排最前，缺漏的分类补在后面。
     *
     * 2.4.1：[custom] 是用户自建分类，[deleted] 中的内置/自建分类不再出现；
     * 这两个参数有默认值，旧调用与旧备份的行为完全不变。
     */
    fun ordered(
        order: List<String>,
        frequentEnabled: Boolean,
        custom: List<CategoryDef> = emptyList(),
        deleted: Set<String> = emptySet(),
    ): List<CategoryDef> {
        val base = (ALL + custom).filterNot { it.id in deleted }
        val remaining = base.toMutableList()
        val out = mutableListOf<CategoryDef>()
        val sequence: List<String> = when {
            order.isEmpty() ->
                if (frequentEnabled) listOf(FREQUENT) + base.map { it.id } else base.map { it.id }
            // 用户调整过顺序：只有开启「最常用」时才保留它的位置，未记录则补到最前。
            frequentEnabled -> if (FREQUENT in order) order else listOf(FREQUENT) + order
            else -> order.filter { it != FREQUENT }
        }
        for (id in sequence) {
            if (id == FREQUENT) {
                if (frequentEnabled) out += FREQUENT_DEF
            } else {
                remaining.firstOrNull { it.id == id }?.let {
                    out += it
                    remaining.remove(it)
                }
            }
        }
        out += remaining
        return out
    }

    /** 结合用户设置解析出当前可见分类（含自建、去掉已删除）。 */
    fun orderedFor(prefs: UserPrefs): List<CategoryDef> {
        val custom = prefs.customCategories.map { id ->
            CategoryDef(
                id = id,
                label = prefs.categoryLabels[id] ?: "新分类",
                iconKey = prefs.categoryIcons[id] ?: "builtin:apps",
            )
        }
        return ordered(prefs.categoryOrder, prefs.frequentEnabled, custom, prefs.deletedCategories)
    }

    // ---- 2.7.0 分类嵌套 ----

    /** [id] 是否是 [ancestorId] 的后代（沿 parents 链向上找，用于阻止嵌套成环）。 */
    fun isDescendant(
        id: String,
        ancestorId: String,
        parents: Map<String, String>,
    ): Boolean {
        var cur = parents[id]
        val seen = mutableSetOf<String>()
        while (cur != null && cur.isNotBlank() && cur !in seen) {
            if (cur == ancestorId) return true
            seen += cur
            cur = parents[cur]
        }
        return false
    }

    /** 顶层分类：没有父级、或父级已被删除（视为自动移回顶层）的分类。 */
    fun topLevel(prefs: UserPrefs): List<CategoryDef> =
        orderedFor(prefs).filter { c ->
            val parent = prefs.categoryParents[c.id]
            parent.isNullOrBlank() || parent in prefs.deletedCategories
        }

    /**
     * 某父分类下的直接子分类。手动排序时按 nestedChildOrders 固定顺序；
     * 其它排序方式按名称（拼音）排。分类只有名称可用，故安装时间/使用频率等也退化为名称序。
     */
    fun childrenOf(parentId: String, prefs: UserPrefs): List<CategoryDef> {
        val all = orderedFor(prefs)
        val byId = all.associateBy { it.id }
        val children = all.filter { prefs.categoryParents[it.id] == parentId }
        val mode = prefs.categorySortModes[parentId] ?: prefs.sortMode
        return if (mode == SortMode.MANUAL) {
            val fixed = prefs.nestedChildOrders[parentId].orEmpty().mapNotNull { byId[it] }
                .filter { prefs.categoryParents[it.id] == parentId }
            val fixedIds = fixed.map { it.id }.toSet()
            fixed + children.filterNot { it.id in fixedIds }
        } else {
            val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
            children.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
    }

    /** 可作为 [childId] 父级的分类：排除自身、后代、以及「最常用」这种虚拟入口。 */
    fun parentCandidates(childId: String, prefs: UserPrefs): List<CategoryDef> =
        orderedFor(prefs).filter { c ->
            c.id != FREQUENT && c.id != childId && !isDescendant(c.id, childId, prefs.categoryParents)
        }
}
