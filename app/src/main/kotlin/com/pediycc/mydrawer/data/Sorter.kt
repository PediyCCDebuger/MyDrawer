package com.pediycc.mydrawer.data

import java.text.Collator
import java.util.Locale

/** 应用排序：中文按拼音序，id 兜底保证稳定。 */
object Sorter {
    private val collator = Collator.getInstance(Locale.CHINA).apply {
        strength = Collator.SECONDARY
    }

    fun sort(apps: List<AppInfo>, mode: String): List<AppInfo> {
        val comparator = when (mode) {
            SortMode.BY_INSTALL_TIME -> compareBy<AppInfo> { it.packageName }
            SortMode.BY_CATEGORY -> compareBy<AppInfo> { it.categoryId }
            else -> Comparator<AppInfo> { a, b -> collator.compare(a.label, b.label) }
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
}
