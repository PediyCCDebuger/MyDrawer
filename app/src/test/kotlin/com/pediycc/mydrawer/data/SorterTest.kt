package com.pediycc.mydrawer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SorterTest {
    private fun app(pkg: String, label: String, cat: String = "utility", install: Long = 0) =
        AppInfo(pkg, label, null, false, cat, install)

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

    @Test
    fun `sort by install time is newest first`() {
        val apps = listOf(
            app("old", "旧", install = 100),
            app("new", "新", install = 900),
        )
        val sorted = Sorter.sort(apps, SortMode.BY_INSTALL_TIME)
        assertEquals(listOf("new", "old"), sorted.map { it.packageName })
    }

    @Test
    fun `sort by usage respects launch counts`() {
        val apps = listOf(app("a", "A"), app("b", "B"), app("c", "C"))
        val counts = mapOf("a" to 2, "b" to 9, "c" to 5)
        val sorted = Sorter.sort(apps, SortMode.BY_USAGE, counts)
        assertEquals(listOf("b", "c", "a"), sorted.map { it.packageName })
    }

    @Test
    fun `manual sort follows stored order`() {
        val apps = listOf(app("a", "A"), app("b", "B"), app("c", "C"))
        val sorted = Sorter.sort(apps, SortMode.MANUAL, emptyMap(), listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), sorted.map { it.packageName })
    }

    @Test
    fun `frequent group takes top ten by usage`() {
        val apps = (1..15).map { app("p$it", "应用$it", "games") }
        val prefs = UserPrefs(
            frequentEnabled = true,
            launchCounts = apps.associate { it.packageName to it.packageName.removePrefix("p").toInt() },
        )
        val frequent = Sorter.groupEx(apps, prefs).first { it.first == Categories.FREQUENT }.second
        assertEquals(10, frequent.size)
        assertEquals("p15", frequent.first().packageName)
        assertEquals("p6", frequent.last().packageName)
    }

    @Test
    fun `old backup prefs still decode with new fields defaulted`() {
        val raw = """{"format":"mydrawer-backup","version":1,"prefs":{"hiddenPackages":["x"],"gridColumns":5}}"""
        val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<BackupFile>(raw)
        assertEquals(setOf("x"), parsed.prefs.hiddenPackages)
        assertEquals(5, parsed.prefs.gridColumns)
        assertEquals(true, parsed.prefs.frequentEnabled)
        assertEquals(Updater.PROXY_LOCAL, parsed.prefs.updateProxy)
    }
}
