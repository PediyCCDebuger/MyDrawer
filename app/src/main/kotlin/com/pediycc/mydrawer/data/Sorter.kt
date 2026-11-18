package com.pediycc.mydrawer.data

import java.text.Collator
import java.util.Locale

/** 应用排序：中文按拼音序，包名兜底保证稳定。 */
object Sorter {
    /** 「最常用」默认只列前 10 个高频应用（与原版一致）；2.9.0 起数量可在设置里自定义。 */
    const val FREQUENT_LIMIT = 10

    private val collator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.SECONDARY
    }

    /** 按使用频次降序取前 [limit] 个；同频按名称、包名保证稳定。 */
    private fun topByUsage(list: List<AppInfo>, counts: Map<String, Int>, limit: Int): List<AppInfo> =
        list.sortedWith(
            compareByDescending<AppInfo> { counts[it.packageName] ?: 0 }
                .then { a, b -> collator.compare(a.label, b.label) }
                .thenBy { it.packageName },
        ).take(limit.coerceAtLeast(1))

    fun sort(
        apps: List<AppInfo>,
        mode: String,
        launchCounts: Map<String, Int> = emptyMap(),
        manualOrder: List<String> = emptyList(),
    ): List<AppInfo> {
        val byName = Comparator<AppInfo> { a, b -> collator.compare(a.label, b.label) }
        val comparator: Comparator<AppInfo> = when (mode) {
            SortMode.BY_INSTALL_TIME -> compareByDescending<AppInfo> { it.installTime }
                .then(byName)
            SortMode.BY_USAGE -> compareByDescending<AppInfo> { launchCounts[it.packageName] ?: 0 }
                .then(byName)
            SortMode.BY_CATEGORY -> compareBy<AppInfo> { it.categoryId }.then(byName)
            SortMode.MANUAL -> {
                val index = manualOrder.withIndex().associate { it.value to it.index }
                compareBy<AppInfo> { index[it.packageName] ?: Int.MAX_VALUE }.then(byName)
            }
            else -> byName
        }
        return apps.sortedWith(comparator.thenBy { it.packageName })
    }

    /** 分类栏 + 组内排序，返回 (分类id, 该分类应用列表) 的有序序列。 */
    fun group(apps: List<AppInfo>, mode: String): List<Pair<String, List<AppInfo>>> {
        val sorted = sort(apps, mode)
        return Categories.ALL.mapNotNull { cat ->
            val inCat = sorted.filter { it.categoryId == cat.id }
            if (inCat.isEmpty()) null else cat.id to inCat
        }
    }

    /** 2.4：按用户分类顺序 + 每分类独立排序 + 最常用分类，组成完整分组。 */
    fun groupEx(
        apps: List<AppInfo>,
        prefs: UserPrefs,
        /** 系统使用统计（真实手机使用频率）；为空时回落到应用内启动计数。 */
        usageCounts: Map<String, Int> = emptyMap(),
    ): List<Pair<String, List<AppInfo>>> {
        // 有系统统计就以系统为准；没授权/没数据才用 MyDrawer 自己的启动次数。
        val counts = if (usageCounts.isNotEmpty()) usageCounts else prefs.launchCounts
        val categories = Categories.orderedFor(prefs)
        val limit = prefs.frequentLimit
        return categories.mapNotNull { cat ->
            val inCat: List<AppInfo> = if (cat.id == Categories.FREQUENT) {
                topByUsage(apps.filter { (counts[it.packageName] ?: 0) > 0 }, counts, limit)
            } else {
                // 2.9.0 多分类：附加归属也计入该分类分组。
                apps.filter { it.inCategory(cat.id, prefs.categoryExtras) }
            }
            if (inCat.isEmpty()) null else if (cat.id == Categories.FREQUENT) {
                // 最常用分类本身已按使用频次排好并截取前 10，不能再被全局排序方式打乱。
                // 仅当用户显式为它选了排序方式时才重排。
                val explicit = prefs.categorySortModes[cat.id]
                if (explicit == null) {
                    cat.id to inCat
                } else {
                    cat.id to sort(inCat, explicit, counts, prefs.manualOrders[cat.id] ?: emptyList())
                }
            } else {
                val mode = prefs.categorySortModes[cat.id] ?: prefs.sortMode
                cat.id to sort(inCat, mode, counts, prefs.manualOrders[cat.id] ?: emptyList())
            }
        }
    }

    /**
     * 2.7.0 分类嵌套：返回某个分类视图里真正要显示的分组序列。
     *
     * - [categoryId] 是用户当前选中的分类（顶层或 "all" / "frequent"）。
     * - 某个分类的直接子分类会作为「子分组」出现在它的应用之前 / 之后（[prefs.nestedChildMode]）。
     * - 子分类只显示属于它自己的应用，且用该子分类自己的排序方式；这与父分类应用的排序相互独立。
     * - 支持多层嵌套：子分类的子分类递归展开。
     *
     * 返回 (分组id, 该分组应用列表, 是否为「分类入口」占位) —— 分类入口本身没有应用，
     * 由界面画成一个可点进的分类卡片，因此这里用 [NestedGroup] 表达更清晰。
     */
    data class NestedGroup(
        val categoryId: String,
        val apps: List<AppInfo>,
        /** true 表示这是一个嵌套子分类的入口（即使没有应用也要显示）。 */
        val isChildEntry: Boolean = false,
    )

    fun groupsWithNested(
        categoryId: String,
        apps: List<AppInfo>,
        prefs: UserPrefs,
        usageCounts: Map<String, Int> = emptyMap(),
    ): List<NestedGroup> {
        val counts = if (usageCounts.isNotEmpty()) usageCounts else prefs.launchCounts

        fun sortOf(catId: String, list: List<AppInfo>): List<AppInfo> {
            // 最常用先截到前 N（可在设置自定义），再决定展示顺序：即使给「最常用」单独选过排序方式，
            // 也不能把全部有使用记录的应用列出来（旧实现漏了这一步，会列出上百个）。
            val base = if (catId == Categories.FREQUENT) {
                topByUsage(list, counts, prefs.frequentLimit)
            } else {
                list
            }
            val explicit = prefs.categorySortModes[catId]
            if (catId == Categories.FREQUENT && explicit == null) return base
            val mode = explicit ?: prefs.sortMode
            return sort(base, mode, counts, prefs.manualOrders[catId] ?: emptyList())
        }

        return when (categoryId) {
            Categories.FREQUENT -> {
                val hot = apps.filter { (counts[it.packageName] ?: 0) > 0 }
                listOf(NestedGroup(Categories.FREQUENT, sortOf(Categories.FREQUENT, hot)))
            }
            "all" -> {
                // 「全部」：每个分类各成一组，按分类顺序；嵌套子分类也单独成组便于查看。
                // 2.9.0 多分类：同一应用归属多个分类时会在各组里都出现。
                Categories.orderedFor(prefs).flatMap { cat ->
                    val inCat = apps.filter { it.inCategory(cat.id, prefs.categoryExtras) }
                    if (inCat.isNotEmpty()) listOf(NestedGroup(cat.id, sortOf(cat.id, inCat))) else emptyList()
                }
            }
            else -> {
                // 某个分类：直接子分类作为「入口块」按规则排序后，放在应用之前 / 之后；
                // 应用用本分类自己的排序规则，二者互不影响。点进子分类再按子分类规则显示其内部。
                val children = Categories.childrenOf(categoryId, prefs)
                    .map { NestedGroup(it.id, emptyList(), isChildEntry = true) }
                val ownApps = apps.filter { it.inCategory(categoryId, prefs.categoryExtras) }
                val own = if (ownApps.isNotEmpty()) listOf(NestedGroup(categoryId, sortOf(categoryId, ownApps))) else emptyList()
                if (prefs.nestedChildMode == NestedChildMode.BACK) own + children else children + own
            }
        }
    }

    /** 把某分类当前顺序固化为手动排序（供「手动排序」入口初始化）。 */
    fun manualOrderOf(apps: List<AppInfo>, prefs: UserPrefs, categoryId: String): List<String> {
        val inCat = if (categoryId == Categories.FREQUENT) {
            apps.filter { (prefs.launchCounts[it.packageName] ?: 0) > 0 }
                .sortedByDescending { prefs.launchCounts[it.packageName] ?: 0 }
                .take(prefs.frequentLimit.coerceAtLeast(1))
        } else {
            apps.filter { it.inCategory(categoryId, prefs.categoryExtras) }
        }
        return sort(
            inCat,
            prefs.categorySortModes[categoryId] ?: prefs.sortMode,
            prefs.launchCounts,
            prefs.manualOrders[categoryId] ?: emptyList(),
        ).map { it.packageName }
    }
}
