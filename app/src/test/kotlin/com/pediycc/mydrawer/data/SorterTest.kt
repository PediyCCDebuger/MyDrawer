package com.pediycc.mydrawer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `move targets put uncategorized last and drop frequent`() {
        val prefs = UserPrefs(
            frequentEnabled = true,
            customCategories = listOf("custom_1"),
            categoryLabels = mapOf("custom_1" to "新分类"),
            // 用户把新建分类排到了「未分类」后面，移动到分类列表仍应把「未分类」固定在末尾。
            categoryOrder = listOf(Categories.FREQUENT, "games", "custom_1", Categories.OTHER),
        )
        val targets = Categories.moveTargets(prefs)
        assertEquals(Categories.OTHER, targets.last().id)
        val customIndex = targets.indexOfFirst { it.id == "custom_1" }
        assertEquals(true, customIndex in 0 until targets.lastIndex)
        assertEquals(false, targets.any { it.id == Categories.FREQUENT })
    }

    @Test
    fun `frequent group stays capped at ten even with explicit category sort`() {
        val apps = (1..15).map { app("p$it", "应用$it", "games") }
        val prefs = UserPrefs(
            frequentEnabled = true,
            launchCounts = apps.associate { it.packageName to it.packageName.removePrefix("p").toInt() },
            // 给「最常用」单独设过排序方式：旧实现会跳过截断，把全部有使用记录的应用列出来。
            categorySortModes = mapOf(Categories.FREQUENT to SortMode.BY_NAME),
        )
        val group = Sorter.groupsWithNested(Categories.FREQUENT, apps, prefs).single()
        assertEquals(Sorter.FREQUENT_LIMIT, group.apps.size)
        // 仍应保留频次最高的那批（按名称序展示，但成员只来自前 10 高频）。
        val topTen = apps.sortedByDescending { prefs.launchCounts[it.packageName] ?: 0 }
            .take(10).map { it.packageName }.toSet()
        assertEquals(topTen, group.apps.map { it.packageName }.toSet())
    }

    @Test
    fun `usage stats take precedence over in-app launch counts`() {
        val apps = listOf(app("a", "A", "games"), app("b", "B", "tools"))
        val prefs = UserPrefs(
            frequentEnabled = true,
            launchCounts = mapOf("a" to 99, "b" to 1),
        )
        val realUsage = mapOf("a" to 1, "b" to 50)
        val frequent = Sorter.groupEx(apps, prefs, realUsage)
            .first { it.first == Categories.FREQUENT }.second
        assertEquals("b", frequent.first().packageName)
        assertEquals(listOf("b", "a"), frequent.map { it.packageName })
    }

    @Test
    fun `old backup prefs still decode with new fields defaulted`() {
        val raw = """{"format":"mydrawer-backup","version":1,"prefs":{"hiddenPackages":["x"],"gridColumns":5}}"""
        val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<BackupFile>(raw)
        assertEquals(setOf("x"), parsed.prefs.hiddenPackages)
        assertEquals(5, parsed.prefs.gridColumns)
        assertEquals(true, parsed.prefs.frequentEnabled)
        assertEquals(true, parsed.prefs.allEnabled)
        assertEquals(130, parsed.prefs.categoryIconSizePercent)
        assertEquals(true, parsed.prefs.normalizeIcons)
        assertEquals(Updater.PROXY_DIRECT, parsed.prefs.updateProxy)
        assertEquals(false, parsed.prefs.usagePromptShown)
        assertEquals(emptyList<String>(), parsed.prefs.customCategories)
        assertEquals(emptySet<String>(), parsed.prefs.deletedCategories)
        assertEquals("", parsed.prefs.hiddenAppsPasswordHash)
        assertEquals("", parsed.prefs.startupCategory)
        assertEquals("all", parsed.prefs.lastCategory)
    }

    @Test
    fun `password hash verifies correctly and never stores plaintext`() {
        val hash = PasswordHash.hash("myPass123")
        assertEquals(true, PasswordHash.verify("myPass123", hash))
        assertEquals(false, PasswordHash.verify("wrong", hash))
        assertEquals(false, PasswordHash.verify("myPass123", ""))
        assert(!hash.contains("myPass123"))
        assert(hash != "myPass123")
        // 加盐后同一口令的摘要固定，可跨进程校验（存进备份再导入仍可验证）。
        assertEquals(hash, PasswordHash.hash("myPass123"))
    }

    @Test
    fun `same version is not an update`() {
        assertEquals(false, Updater.isNewer("v2.3.0", "2.3.0"))
        assertEquals(false, Updater.isNewer("2.3.0", "v2.3.0"))
    }

    @Test
    fun `higher version is an update`() {
        assertEquals(true, Updater.isNewer("v2.3.1", "2.3.0"))
        assertEquals(true, Updater.isNewer("v2.4.0", "2.3.9"))
        assertEquals(true, Updater.isNewer("v3.0", "2.9.9"))
        assertEquals(false, Updater.isNewer("v2.2.0", "2.3.0"))
    }

    @Test
    fun `frequent category is first by default and can be turned off`() {
        assertEquals(Categories.FREQUENT, Categories.ordered(emptyList(), true).first().id)
        assertEquals("games", Categories.ordered(emptyList(), false).first().id)
        assertEquals(
            false,
            Categories.ordered(emptyList(), false).any { it.id == Categories.FREQUENT },
        )
    }

    @Test
    fun `custom categories appear and deleted ones disappear from ordered list`() {
        val p = UserPrefs(
            customCategories = listOf("custom_a"),
            categoryLabels = mapOf("custom_a" to "我的分类"),
            deletedCategories = setOf("games"),
        )
        val ids = Categories.orderedFor(p).map { it.id }
        assert(ids.contains("custom_a"))
        assert(!ids.contains("games"))
        assertEquals("我的分类", Categories.orderedFor(p).first { it.id == "custom_a" }.label)
    }

    @Test
    fun `groupEx includes custom category groups`() {
        val apps = listOf(app("c1", "自定义应用", "custom_a"))
        val prefs = UserPrefs(
            customCategories = listOf("custom_a"),
            deletedCategories = setOf("games"),
        )
        val groups = Sorter.groupEx(apps, prefs)
        assertEquals(listOf("custom_a"), groups.map { it.first })
    }

    @Test
    fun `version patch digit is compared numerically`() {
        assertEquals(true, Updater.isNewer("v2.4.1", "2.4.0"))
        assertEquals(false, Updater.isNewer("2.4.1", "2.4.1"))
    }

    @Test
    fun `other category is labeled unclassified`() {
        assertEquals("未分类", Categories.labelOf(Categories.OTHER))
        assertEquals("未分类", Categories.ALL.first { it.id == Categories.OTHER }.label)
    }

    @Test
    fun `new backup fields default sensibly for old backups`() {
        val raw = """{"format":"mydrawer-backup","version":1,"prefs":{"gridColumns":5}}"""
        val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<BackupFile>(raw)
        assertEquals("all", parsed.prefs.lastCategory)
        assertEquals("", parsed.prefs.startupCategory)
    }

    @Test
    fun `startup category and last category survive backup round trip`() {
        val p = UserPrefs(lastCategory = "games", startupCategory = "utility")
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<BackupFile>(json.encodeToString(BackupFile(prefs = p))).prefs
        assertEquals("games", decoded.lastCategory)
        assertEquals("utility", decoded.startupCategory)
    }

    @Test
    fun `minor version is compared numerically`() {
        assertEquals(true, Updater.isNewer("v2.5.0", "2.4.1"))
        assertEquals(false, Updater.isNewer("2.5.0", "2.5.0"))
    }

    // ---- 搜索 ----
    private val fakePinyin: (String) -> String = { text ->
        mapOf("微信" to "wei xin", "设置" to "she zhi", "相机" to "xiang ji")
            .getOrElse(text) { "" }
    }

    @Test
    fun `pinyin full and initials match Chinese label`() {
        assert(SearchMatcher.matches("微信", "com.tencent.mm", "weixin", fakePinyin))
        assert(SearchMatcher.matches("微信", "com.tencent.mm", "wx", fakePinyin))
        assert(SearchMatcher.matches("设置", "com.android.settings", "sz", fakePinyin))
        assert(!SearchMatcher.matches("微信", "com.tencent.mm", "xj", fakePinyin))
    }

    @Test
    fun `latin search uses word prefix not substring`() {
        // ser 只应命中以 ser 开头的单词/段，不能把含 ser 子串的无关项翻出来
        assert(SearchMatcher.matches("Service", "com.example.service", "ser", fakePinyin))
        assert(SearchMatcher.matches("User", "com.userapp", "us", fakePinyin))
        // 这些词里 ser 只是子串（U-ser、con-ser-vice），不应命中
        assert(!SearchMatcher.matches("User Guide", "com.foo.tools", "ser", fakePinyin))
        assert(!SearchMatcher.matches("Console", "com.foo.tools", "ser", fakePinyin))
    }

    @Test
    fun `Chinese query still matches by substring`() {
        assert(SearchMatcher.matches("微信支付", "com.tencent.mm", "支付", fakePinyin))
        assert(!SearchMatcher.matches("微信", "com.tencent.mm", "支付宝", fakePinyin))
    }

    // ---- 分类嵌套 ----
    @Test
    fun `nested categories are hidden from top level`() {
        val p = UserPrefs(
            customCategories = listOf("c_child"),
            categoryLabels = mapOf("c_child" to "系统程序"),
            categoryParents = mapOf("c_child" to "utility"),
        )
        assert(!Categories.topLevel(p).any { it.id == "c_child" })
        assert(Categories.topLevel(p).any { it.id == "utility" })
        assertEquals(listOf("c_child"), Categories.childrenOf("utility", p).map { it.id })
    }

    @Test
    fun `cycle descendants detected correctly`() {
        // parents 表示 子->父：b 的父是 a，c 的父是 b，即 a 是 b、c 的祖先
        val parents = mapOf("b" to "a", "c" to "b")
        assert(Categories.isDescendant("c", "a", parents)) // c 是 a 的后代
        assert(Categories.isDescendant("b", "a", parents)) // b 是 a 的后代
        assert(!Categories.isDescendant("c", "c", parents)) // 自己不是自己的后代
        assert(!Categories.isDescendant("a", "c", parents)) // a 是祖先，不是 c 的后代
    }

    @Test
    fun `nested child entries appear before apps by default`() {
        val p = UserPrefs(
            customCategories = listOf("c_child"),
            categoryLabels = mapOf("c_child" to "系统程序"),
            categoryParents = mapOf("c_child" to "utility"),
        )
        val apps = listOf(
            app("u1", "工具A", "utility"),
            app("s1", "浏览器", "c_child"),
        )
        val groups = Sorter.groupsWithNested("utility", apps, p)
        assertEquals("c_child", groups.first().categoryId)
        assert(groups.first().isChildEntry)
        assertEquals("utility", groups.last().categoryId)
        assertEquals(listOf("u1"), groups.last().apps.map { it.packageName })
    }

    @Test
    fun `nested child mode back places children after apps`() {
        val p = UserPrefs(
            customCategories = listOf("c_child"),
            categoryLabels = mapOf("c_child" to "系统程序"),
            categoryParents = mapOf("c_child" to "utility"),
            nestedChildMode = NestedChildMode.BACK,
        )
        val apps = listOf(app("u1", "工具A", "utility"), app("s1", "浏览器", "c_child"))
        val groups = Sorter.groupsWithNested("utility", apps, p)
        assertEquals("utility", groups.first().categoryId)
        assertEquals("c_child", groups.last().categoryId)
    }

    @Test
    fun `nesting and search fields survive backup round trip`() {
        val p = UserPrefs(
            categoryParents = mapOf("c1" to "utility"),
            nestedChildMode = NestedChildMode.BACK,
            nestedChildOrders = mapOf("utility" to listOf("c1")),
        )
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<BackupFile>(json.encodeToString(BackupFile(prefs = p))).prefs
        assertEquals(mapOf("c1" to "utility"), decoded.categoryParents)
        assertEquals(NestedChildMode.BACK, decoded.nestedChildMode)
        assertEquals(listOf("c1"), decoded.nestedChildOrders["utility"])
    }

    // ---- 嵌套子分类的返回栈 ----
    @Test
    fun `category trail pushes parent and pops back to it`() {
        // 从「工具」点进子分类「系统程序」
        var trail = Categories.pushTrail(emptyList(), "utility", "c_child")
        assertEquals(listOf("utility"), trail)

        // 再点进孙子分类，栈变成 [utility, c_child]
        trail = Categories.pushTrail(trail, "c_child", "c_grand")
        assertEquals(listOf("utility", "c_child"), trail)

        // 返回键先退回 c_child，再退回 utility，最后为空
        val first = Categories.popTrail(trail)!!
        assertEquals("c_child", first.first)
        assertEquals(listOf("utility"), first.second)
        val second = Categories.popTrail(first.second)!!
        assertEquals("utility", second.first)
        assertNull(Categories.popTrail(second.second))
    }

    @Test
    fun `category trail does not push the same category twice`() {
        assertEquals(listOf("utility"), Categories.pushTrail(listOf("utility"), "utility", "utility"))
    }

    @Test
    fun `deleted categories are dropped from the trail`() {
        val trail = Categories.sanitizeTrail(listOf("utility", "gone"), setOf("utility", "all"))
        assertEquals(listOf("utility"), trail)
    }

    @Test
    fun `category whose parent was deleted counts as top level`() {
        val p = UserPrefs(
            customCategories = listOf("c_child"),
            categoryLabels = mapOf("c_child" to "系统程序"),
            categoryParents = mapOf("c_child" to "utility"),
            deletedCategories = setOf("utility"),
        )
        assert(Categories.topLevel(p).any { it.id == "c_child" })
    }

    // ---- 2.9.0 多分类 / 未分类固定末位 ----

    @Test
    fun `checking a category from other makes it the primary`() {
        val (primary, extras) = Categories.toggleMembership("other", emptySet(), "games")
        assertEquals("games", primary)
        assertEquals(emptySet<String>(), extras)
    }

    @Test
    fun `checking an extra category keeps the primary`() {
        val (primary, extras) = Categories.toggleMembership("utility", emptySet(), "internet")
        assertEquals("utility", primary)
        assertEquals(setOf("internet"), extras)
    }

    @Test
    fun `unchecking the primary falls back to an extra then other`() {
        val (p1, e1) = Categories.toggleMembership("utility", setOf("internet"), "utility")
        assertEquals("internet", p1)
        assertEquals(emptySet<String>(), e1)
        val (p2, e2) = Categories.toggleMembership("utility", emptySet(), "utility")
        assertEquals("other", p2)
        assertEquals(emptySet<String>(), e2)
    }

    @Test
    fun `unchecking an extra keeps the primary`() {
        val (primary, extras) = Categories.toggleMembership("utility", setOf("internet", "media"), "internet")
        assertEquals("utility", primary)
        assertEquals(setOf("media"), extras)
    }

    @Test
    fun `app counts as member of both primary and extra categories`() {
        val a = app("store", "商店", "utility")
        assert(a.inCategory("utility", mapOf("store" to setOf("internet"))))
        assert(a.inCategory("internet", mapOf("store" to setOf("internet"))))
        assert(!a.inCategory("games", mapOf("store" to setOf("internet"))))
    }

    @Test
    fun `grouping shows an app in every category it belongs to`() {
        val p = UserPrefs(categoryExtras = mapOf("store" to setOf("internet")))
        val apps = listOf(app("store", "商店", "utility"))
        val ids = Sorter.groupsWithNested("all", apps, p).map { it.categoryId }
        assertEquals(listOf("internet", "utility"), ids.sorted())
    }

    @Test
    fun `batch memberships keep the primary when still checked`() {
        val (primary, extras) = Categories.withMemberships("utility", listOf("internet", "utility", "media"))
        assertEquals("utility", primary)
        assertEquals(setOf("internet", "media"), extras)
        val (p2, e2) = Categories.withMemberships("utility", listOf("internet", "games"))
        assertEquals("internet", p2)
        assertEquals(setOf("games"), e2)
        val (p3, e3) = Categories.withMemberships("utility", emptyList())
        assertEquals("other", p3)
        assertEquals(emptySet<String>(), e3)
    }

    @Test
    fun `reorder keeps uncategorized pinned last`() {
        val ids = listOf("games", "utility", "other", "media")
        // from/to 是去掉「未分类」后的下标：把下标 1（utility）拖到末尾（media 位置）。
        val next = Categories.reorderKeepingLast(ids, 1, 2)
        assertEquals(listOf("games", "media", "utility", "other"), next)
        // 原地不动 / 越界返回原样。
        assertEquals(ids, Categories.reorderKeepingLast(ids, 1, 1))
        assertEquals(ids, Categories.reorderKeepingLast(ids, 9, 0))
    }

    @Test
    fun `frequent limit follows the user preference`() {
        val p = UserPrefs(launchCounts = mapOf("a" to 5, "b" to 4, "c" to 3, "d" to 2), frequentLimit = 2)
        val apps = listOf(app("a", "A"), app("b", "B"), app("c", "C"), app("d", "D"))
        val group = Sorter.groupsWithNested("frequent", apps, p).first()
        assertEquals(listOf("a", "b"), group.apps.map { it.packageName })
    }
}
