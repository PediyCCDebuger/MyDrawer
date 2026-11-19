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

/** 应用是否属于某分类：主分类，或 2.9.0 起勾选的附加分类（一个应用可出现在多个分类里）。 */
fun AppInfo.inCategory(categoryId: String, extras: Map<String, Set<String>>): Boolean =
    this.categoryId == categoryId || categoryId in (extras[packageName] ?: emptySet())

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

    /**
     * 「移动到分类」可选的目标分类：「最常用」是虚拟入口不能做目标，
     * 「未分类」固定排最后 —— 它是兜底项，新建的分类不应排在它后面。
     */
    fun moveTargets(prefs: UserPrefs): List<CategoryDef> =
        orderedFor(prefs)
            .filter { it.id != FREQUENT }
            .sortedBy { if (it.id == OTHER) 1 else 0 }

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

    // ---- 嵌套子分类的返回栈（纯函数，便于单测） ----

    /** 进入子分类：把当前分类压入返回栈。[current] 与 [target] 相同时不重复压栈。 */
    fun pushTrail(trail: List<String>, current: String, target: String): List<String> =
        if (current == target) trail else trail + current

    /** 返回上一级：返回 (上一级分类, 新返回栈)；栈空返回 null。 */
    fun popTrail(trail: List<String>): Pair<String, List<String>>? =
        if (trail.isEmpty()) null else trail.last() to trail.dropLast(1)

    /** 剔除返回栈里已经失效（被删除）的分类，避免退回到空白视图。 */
    fun sanitizeTrail(trail: List<String>, validIds: Set<String>): List<String> =
        trail.filter { it in validIds }

    // ---- 2.9.0 多分类 / 固定末位（纯函数，便于单测） ----

    /**
     * 勾选 / 取消勾选某分类后的成员集合，返回新的 (主分类, 附加分类)。
     * 「未分类」只是兜底，不能与具体分类共存（2.9.6）：
     * - 从未分类勾选具体分类 → 该分类直接转为主分类，应用随之从未分类里消失；
     * - 勾选「未分类」→ 清空全部分类归属（等价于移回未分类）；
     * - 取消主分类时，若有附加分类则顺延一个为主分类，否则回落到「未分类」。
     */
    fun toggleMembership(
        primary: String,
        extras: Set<String>,
        catId: String,
    ): Pair<String, Set<String>> {
        // 历史数据里可能把「未分类」混进了附加归属，这里统一剔除。
        val current = extras - OTHER
        val isMember = primary == catId || catId in current
        return if (isMember) {
            if (primary == catId) {
                val rest = current - catId
                val np = rest.firstOrNull() ?: OTHER
                np to (rest - np)
            } else {
                primary to (current - catId)
            }
        } else {
            when {
                catId == OTHER -> OTHER to emptySet()
                primary == OTHER -> catId to current
                else -> primary to (current + catId)
            }
        }
    }

    /**
     * 批量勾选：把成员集合精确设为 [checked]（按勾选顺序）。
     * 「未分类」不能与具体分类共存（2.9.6）：只要勾了具体分类就忽略「未分类」，
     * 否则会出现「已经在工具里、却还留在未分类一份」的情况。
     * 原主分类还在生效集合里就保持不动，否则顺延第一个；没勾任何分类则归入「未分类」。
     */
    fun withMemberships(
        primary: String,
        checked: List<String>,
    ): Pair<String, Set<String>> {
        val concrete = checked.filter { it != OTHER }.distinct()
        val effective = if (concrete.isNotEmpty()) concrete else checked.distinct()
        if (effective.isEmpty()) return OTHER to emptySet()
        val newPrimary = if (primary in effective) primary else effective.first()
        return newPrimary to effective.filter { it != newPrimary }.toSet()
    }

    /**
     * 规范化多分类归属（2.9.6 启动迁移）：清掉附加归属里的「未分类」，
     * 并把「主分类是未分类、却挂着具体分类」的旧数据提升为主分类——
     * 这些数据会让应用分类后仍留在「未分类」里多一份。
     */
    fun sanitizeMemberships(
        overrides: Map<String, String>,
        extras: Map<String, Set<String>>,
    ): Pair<Map<String, String>, Map<String, Set<String>>> {
        if (extras.isEmpty()) return overrides to extras
        val newOverrides = overrides.toMutableMap()
        val newExtras = linkedMapOf<String, Set<String>>()
        extras.forEach { (pkg, raw) ->
            val cleaned = raw.filter { it != OTHER }.distinct()
            var primary = newOverrides[pkg] ?: OTHER
            if (primary == OTHER && cleaned.isNotEmpty()) {
                primary = cleaned.first()
                newOverrides[pkg] = primary
            }
            val rest = cleaned.filter { it != primary }
            if (rest.isNotEmpty()) newExtras[pkg] = rest.toSet()
        }
        return newOverrides to newExtras
    }

    /**
     * 拖动排序后把固定项（「未分类」）放回最后：它只从 [ids] 里挪动其余项，
     * [from]/[to] 是去掉固定项后的下标；越界或原地不动时返回原样。
     */
    fun reorderKeepingLast(
        ids: List<String>,
        from: Int,
        to: Int,
        pinnedId: String = OTHER,
    ): List<String> {
        val rest = ids.filterNot { it == pinnedId }
        if (from !in rest.indices || to !in rest.indices || from == to) return ids
        val out = rest.toMutableList()
        val moved = out.removeAt(from)
        out.add(to, moved)
        val pinned = ids.filter { it == pinnedId }
        return if (pinned.isEmpty()) out else out + pinned
    }
}
