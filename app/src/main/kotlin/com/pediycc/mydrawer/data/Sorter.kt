package com.pediycc.mydrawer.data

import java.text.Collator
import java.util.Locale

/** 应用排序：中文按拼音序，包名兜底保证稳定。 */
object Sorter {
    private val collator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.SECONDARY
    }

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

    /** 2.0：按用户分类顺序 + 每分类独立排序 + 最常用分类，组成完整分组。 */
    fun groupEx(
        apps: List<AppInfo>,
        prefs: UserPrefs,
    ): List<Pair<String, List<AppInfo>>> {
        val categories = Categories.ordered(prefs.categoryOrder, prefs.frequentEnabled)
        return categories.mapNotNull { cat ->
            val inCat: List<AppInfo> = if (cat.id == Categories.FREQUENT) {
                apps
                    .filter { (prefs.launchCounts[it.packageName] ?: 0) > 0 }
                    .sortedWith(
                        compareByDescending<AppInfo> { prefs.launchCounts[it.packageName] ?: 0 }
                            .then { a, b -> collator.compare(a.label, b.label) }
                            .thenBy { it.packageName },
                    )
                    .take(10)
            } else {
                apps.filter { it.categoryId == cat.id }
            }
            if (inCat.isEmpty()) null else {
                val mode = prefs.categorySortModes[cat.id] ?: prefs.sortMode
                cat.id to sort(inCat, mode, prefs.launchCounts, prefs.manualOrders[cat.id] ?: emptyList())
            }
        }
    }

    /** 把某分类当前顺序固化为手动排序（供「手动排序」入口初始化）。 */
    fun manualOrderOf(apps: List<AppInfo>, prefs: UserPrefs, categoryId: String): List<String> {
        val inCat = if (categoryId == Categories.FREQUENT) {
            apps.filter { (prefs.launchCounts[it.packageName] ?: 0) > 0 }
                .sortedByDescending { prefs.launchCounts[it.packageName] ?: 0 }
                .take(10)
        } else {
            apps.filter { it.categoryId == categoryId }
        }
        return sort(
            inCat,
            prefs.categorySortModes[categoryId] ?: prefs.sortMode,
            prefs.launchCounts,
            prefs.manualOrders[categoryId] ?: emptyList(),
        ).map { it.packageName }
    }
}
