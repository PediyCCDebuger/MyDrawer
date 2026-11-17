package com.pediycc.mydrawer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SorterTest {
    private fun app(pkg: String, label: String, cat: String = "utility") =
        AppInfo(pkg, label, null, false, cat)

    @Test
    fun `sort by name uses pinyin order for Chinese`() {
        val apps = listOf(
            app("p", "中国", "other"),
            app("a", "安全", "other"),
            app("b", "北极", "other"),
        )
        val sorted = Sorter.sort(apps, SortMode.BY_NAME)
        assertEquals(listOf("安全", "北极", "中国"), sorted.map { it.label })
    }

    @Test
    fun `sort is stable via package name tiebreak`() {
        val apps = listOf(
            app("z.pkg", "同名"),
            app("a.pkg", "同名"),
        )
        val sorted = Sorter.sort(apps, SortMode.BY_NAME)
        assertEquals(listOf("a.pkg", "z.pkg"), sorted.map { it.packageName })
    }

    @Test
    fun `group keeps category order and drops empty ones`() {
        val apps = listOf(
            app("g1", "游戏A", "games"),
            app("u1", "工具A", "utility"),
        )
        val groups = Sorter.group(apps, SortMode.BY_NAME)
        assertEquals(listOf("games", "utility"), groups.map { it.first })
    }
}
