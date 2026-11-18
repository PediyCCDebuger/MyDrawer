package com.pediycc.mydrawer.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pediycc.mydrawer.data.AppInfo
import com.pediycc.mydrawer.data.AppRepository
import com.pediycc.mydrawer.data.BackupManager
import com.pediycc.mydrawer.data.BuiltInIcons
import com.pediycc.mydrawer.data.Categories
import com.pediycc.mydrawer.data.CategoryCatalog
import com.pediycc.mydrawer.data.Preferences
import com.pediycc.mydrawer.data.SortMode
import com.pediycc.mydrawer.data.Sorter
import com.pediycc.mydrawer.data.Updater
import com.pediycc.mydrawer.data.UsageStatsHelper
import com.pediycc.mydrawer.data.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DrawerUiState(
    val apps: List<AppInfo> = emptyList(),
    val selectedCategory: String = "all",
    val query: String = "",
    val prefs: UserPrefs = UserPrefs(),
    val loading: Boolean = true,
    val message: String? = null,
    /** 多选模式与已选包。 */
    val selectMode: Boolean = false,
    val selected: Set<String> = emptySet(),
    /** 系统使用统计（真实手机使用频率）；未授权时为空，回落到 app 内启动计数。 */
    val usageCounts: Map<String, Int> = emptyMap(),
)

class DrawerViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context = app.applicationContext
    private val prefs = Preferences(context)
    private val catalog = CategoryCatalog(context)
    private val repo = AppRepository(context, catalog, prefs)
    private val backup = BackupManager(context)
    private val iconStore = IconStore(context)

    private val _ui = MutableStateFlow(
        DrawerUiState(prefs = prefs.current, selectedCategory = resolveStartupCategory(prefs.current))
    )
    val ui: StateFlow<DrawerUiState> = _ui.asStateFlow()

    init { refresh() }

    /**
     * 启动时进入的分类：startupCategory 为空则沿用 lastCategory（上次使用的分类），
     * 否则固定进入指定入口；无效 / 已隐藏的入口最后在 refresh 里由 fallbackCategory 兜底。
     */
    private fun resolveStartupCategory(p: UserPrefs): String =
        if (p.startupCategory.isBlank()) p.lastCategory else p.startupCategory

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val apps = repo.loadApps()
            val usage = UsageStatsHelper.load(context)
            _ui.value = _ui.value.copy(
                apps = apps,
                prefs = prefs.current,
                loading = false,
                selectedCategory = fallbackCategory(_ui.value.selectedCategory, prefs.current, apps),
                selected = _ui.value.selected.intersect(apps.map { it.packageName }.toSet()),
                usageCounts = usage,
            )
        }
    }

    /** 从系统「使用情况访问权限」设置页返回后重新读取真实使用频率。 */
    fun reloadUsage() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(usageCounts = UsageStatsHelper.load(context))
        }
    }

    /** 关闭「全部」/「最常用」、删除分类或「未分类」变空后，停在已隐藏入口上的选中项回落到第一个可用分类。 */
    private fun fallbackCategory(current: String, p: UserPrefs, apps: List<AppInfo> = _ui.value.apps): String {
        if (current == "all") return if (p.allEnabled) "all" else firstCategory(p)
        if (current == Categories.FREQUENT && !p.frequentEnabled) return firstCategory(p)
        // 「未分类」已无应用会自动隐藏，若正停在上面则回落到第一个分类。
        if (current == Categories.OTHER && !otherHasApps(p, apps)) return firstCategory(p)
        // 分类被删除（内置或自建）后不再存在于可用列表，回落到第一个分类。
        if (current !in Categories.orderedFor(p).map { it.id }) return firstCategory(p)
        return current
    }

    fun firstCategory(p: UserPrefs = prefs.current): String =
        Categories.orderedFor(p).firstOrNull()?.id ?: "all"

    fun selectCategory(id: String) {
        prefs.update { it.copy(lastCategory = id) }
        _ui.value = _ui.value.copy(
            selectedCategory = id,
            prefs = prefs.current,
            selectMode = false,
            selected = emptySet(),
        )
    }

    /** 设置「启动时进入的分类」：空字符串表示每次沿用上次分类。 */
    fun setStartupCategory(id: String) {
        prefs.update { it.copy(startupCategory = id) }
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    fun setQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
    }

    // ---- 应用操作 ----

    /** 启动应用；成功返回 true，界面据此 finish 自身，不让抽屉留在后台。 */
    fun launch(packageName: String): Boolean {
        val intent = repo.getLaunchIntent(packageName)
        if (intent == null) {
            notify("该应用没有可启动入口")
            return false
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { context.startActivity(intent) }
            .onFailure { notify("无法启动该应用") }
            .isSuccess
        if (ok) {
            prefs.recordLaunch(packageName)
            _ui.value = _ui.value.copy(prefs = prefs.current)
        }
        return ok
    }

    fun toggleHidden(packageName: String) {
        prefs.toggleHidden(packageName)
        refresh()
        notify("已更新隐藏应用")
    }

    fun setCategoryOverride(packageName: String, categoryId: String) {
        prefs.overrideCategory(packageName, categoryId)
        refresh()
        notify("分类已更改")
    }

    fun renameApp(packageName: String, label: String) {
        prefs.renameApp(packageName, label.trim())
        refresh()
        notify("名称已更改")
    }

    fun restoreAppLabel(packageName: String) {
        prefs.update { p -> p.copy(appLabels = p.appLabels - packageName) }
        refresh()
        notify("已恢复原名称")
    }

    fun setAppIcon(packageName: String, key: String) {
        prefs.setAppIcon(packageName, key)
        _ui.value = _ui.value.copy(prefs = prefs.current)
        notify("图标已更改")
    }

    /** 从本机图库选图片作为应用 / 分类图标：复制到应用私有目录，避免 Uri 权限失效。 */
    fun setCustomIcon(uri: Uri, target: IconTarget) {
        viewModelScope.launch {
            val path = iconStore.copy(uri)
            if (path == null) {
                notify("图片读取失败")
                return@launch
            }
            val key = "file:$path"
            when (target) {
                is IconTarget.App -> prefs.setAppIcon(target.packageName, key)
                is IconTarget.Category -> prefs.setCategoryIcon(target.categoryId, key)
            }
            refresh()
            notify("图标已更改")
        }
    }

    /** 恢复所有应用与分类的默认图标。 */
    fun resetCustomIcons() {
        prefs.resetCustomIcons()
        refresh()
        notify("已恢复默认图标")
    }

    // ---- 分类操作 ----

    fun renameCategory(categoryId: String, label: String) {
        prefs.renameCategory(categoryId, label.trim())
        _ui.value = _ui.value.copy(prefs = prefs.current)
        notify("分类名称已更改")
    }

    fun setCategoryIcon(categoryId: String, key: String) {
        prefs.setCategoryIcon(categoryId, key)
        _ui.value = _ui.value.copy(prefs = prefs.current)
        notify("分类图标已更改")
    }

    fun setCategorySort(categoryId: String, mode: String) {
        prefs.setCategorySort(categoryId, mode)
        if (mode == SortMode.MANUAL) {
            // 首次切到手动排序：把当前可见顺序固化为初始值。
            val current = Sorter.manualOrderOf(_ui.value.apps, prefs.current, categoryId)
            prefs.setManualOrder(categoryId, current)
        }
        _ui.value = _ui.value.copy(prefs = prefs.current)
        notify("排序方式已更改")
    }

    fun moveManual(categoryId: String, packageName: String, delta: Int) {
        prefs.moveManual(categoryId, packageName, delta)
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    fun moveCategory(categoryId: String, delta: Int) {
        val ids = Categories.orderedFor(prefs.current)
            .map { it.id }.toMutableList()
        val from = ids.indexOf(categoryId)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, ids.lastIndex)
        if (to == from) return
        java.util.Collections.swap(ids, from, to)
        prefs.update { it.copy(categoryOrder = ids) }
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    /** 拖拽排序：把 [fromIndex] 处的分类移到 [toIndex]（均按 orderedFor 的完整列表下标）。 */
    fun reorderCategory(fromIndex: Int, toIndex: Int) {
        val ids = Categories.orderedFor(prefs.current).map { it.id }.toMutableList()
        if (fromIndex !in ids.indices || toIndex !in ids.indices || fromIndex == toIndex) return
        val id = ids.removeAt(fromIndex)
        ids.add(toIndex, id)
        prefs.update { it.copy(categoryOrder = ids) }
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    /** 拖拽排序：把某分类手动排序列表里 [fromIndex] 的应用移到 [toIndex]。 */
    fun reorderManual(categoryId: String, fromIndex: Int, toIndex: Int) {
        val list = (prefs.current.manualOrders[categoryId]
            ?: Sorter.manualOrderOf(_ui.value.apps, prefs.current, categoryId)).toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        val pkg = list.removeAt(fromIndex)
        list.add(toIndex, pkg)
        prefs.update { p -> p.copy(manualOrders = p.manualOrders + (categoryId to list)) }
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    /** 网格拖拽：在手动排序分类内把 [packageName] 相对移动 [delta] 个位置。 */
    fun reorderManualBy(categoryId: String, packageName: String, delta: Int) {
        val list = (prefs.current.manualOrders[categoryId]
            ?: Sorter.manualOrderOf(_ui.value.apps, prefs.current, categoryId)).toMutableList()
        val from = list.indexOf(packageName)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return
        list.removeAt(from)
        list.add(to, packageName)
        prefs.update { p -> p.copy(manualOrders = p.manualOrders + (categoryId to list)) }
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    /** 新建分类；若当前停在已删除分类上，创建后自动切到新分类。 */
    fun createCategory(label: String): String {
        val id = prefs.addCategory(label.trim())
        prefs.update { it.copy(lastCategory = id) }
        _ui.value = _ui.value.copy(prefs = prefs.current, selectedCategory = id)
        notify("分类「${label.trim()}」已创建")
        return id
    }

    /**
     * 删除分类。内置分类也允许删除，但「未分类」「最常用」保留；
     * 若正停在该分类，回落到第一个可用分类。原分类应用由 AppRepository 在下次刷新时归入「未分类」。
     */
    fun deleteCategory(categoryId: String) {
        if (categoryId == Categories.OTHER || categoryId == Categories.FREQUENT) return
        prefs.deleteCategory(categoryId)
        _ui.value = _ui.value.copy(
            prefs = prefs.current,
            selectedCategory = fallbackCategory(_ui.value.selectedCategory, prefs.current),
        )
        refresh()
        notify("分类已删除，其中的应用已移入「未分类」")
    }

    /** 取消某个应用的分类，使其进入「未分类」。 */
    fun clearCategory(packageName: String) {
        prefs.overrideCategory(packageName, Categories.OTHER)
        refresh()
        notify("已取消分类，应用移入「未分类」")
    }

    /** 多选批量取消分类：把选中的应用全部移入「未分类」。 */
    fun clearCategoryForSelected() {
        val ids = _ui.value.selected
        if (ids.isEmpty()) {
            notify("请先选择应用")
            return
        }
        prefs.overrideCategories(ids, Categories.OTHER)
        _ui.value = _ui.value.copy(selectMode = false, selected = emptySet())
        refresh()
        notify("已将 ${ids.size} 个应用移入「未分类」")
    }

    /**
     * 「未分类」在没有未分类应用时自动隐藏；只要有应用落入该分类就自动出现。
     * 判断基于全部可见应用（排除手动隐藏的），不受当前搜索词影响。
     */
    fun otherHasApps(p: UserPrefs = prefs.current, apps: List<AppInfo> = _ui.value.apps): Boolean {
        val visible = apps.filterNot { it.packageName in p.hiddenPackages }
        return visible.any { it.categoryId == Categories.OTHER }
    }

    /** 首次进入时的使用频率授权提示，标记后不再重复。 */
    fun markUsagePromptShown() {
        prefs.markUsagePromptShown()
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    // ---- 多选 ----

    fun setSelectMode(on: Boolean) {
        _ui.value = _ui.value.copy(selectMode = on, selected = if (on) emptySet() else emptySet())
    }

    fun toggleSelected(packageName: String) {
        val s = _ui.value
        val next = if (packageName in s.selected) s.selected - packageName else s.selected + packageName
        _ui.value = s.copy(selected = next)
    }

    fun selectAllVisible() {
        val ids = visibleGroups().flatMap { it.second }.map { it.packageName }.toSet()
        _ui.value = _ui.value.copy(selected = ids)
    }

    fun moveSelectedTo(categoryId: String) {
        val ids = _ui.value.selected
        if (ids.isEmpty()) {
            notify("请先选择应用")
            return
        }
        prefs.overrideCategories(ids, categoryId)
        _ui.value = _ui.value.copy(selectMode = false, selected = emptySet())
        refresh()
        notify("已将 ${ids.size} 个应用移动到${categoryLabel(categoryId)}")
    }

    // ---- 设置 ----

    fun setSortMode(mode: String) {
        prefs.update { it.copy(sortMode = mode) }
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    fun setGridColumns(columns: Int) {
        prefs.update { it.copy(gridColumns = columns.coerceIn(2, 8)) }
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    fun setLandscapeColumns(columns: Int) {
        prefs.update { it.copy(landscapeColumns = columns.coerceIn(2, 10)) }
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    fun setShowSystemApps(show: Boolean) {
        prefs.update { it.copy(showSystemApps = show) }
        refresh()
    }

    /** 分类栏「全部」入口开关；关闭时若正停在「全部」，自动落到第一个分类。 */
    fun setAllEnabled(on: Boolean) {
        prefs.update { it.copy(allEnabled = on) }
        _ui.value = _ui.value.copy(
            prefs = prefs.current,
            selectedCategory = fallbackCategory(_ui.value.selectedCategory, prefs.current),
        )
    }

    /** 「最常用」分类开关；关闭时若正停在该分类，自动落到第一个分类。 */
    fun setFrequentEnabled(on: Boolean) {
        prefs.update { it.copy(frequentEnabled = on) }
        _ui.value = _ui.value.copy(
            prefs = prefs.current,
            selectedCategory = fallbackCategory(_ui.value.selectedCategory, prefs.current),
        )
    }

    fun updatePrefs(transform: (UserPrefs) -> UserPrefs) {
        prefs.update(transform)
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    // ---- 备份 / 更新 ----

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val text = backup.export(prefs.current)
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }.onSuccess { notify("配置备份已导出") }
                .onFailure { notify("导出失败：${it.message}") }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (raw == null) {
                notify("读取失败")
                return@launch
            }
            runCatching { backup.import(raw) }
                .onSuccess { imported ->
                    prefs.replaceAll(imported)
                    refresh()
                    notify("配置备份已导入")
                }
                .onFailure { notify("导入失败：${it.message}") }
        }
    }

    fun checkUpdate(onResult: (Updater.ReleaseInfo?, String?) -> Unit) {
        viewModelScope.launch {
            val info = Updater.fetchLatest(prefs.current.updateProxy)
            onResult(info, if (info == null) "检查更新失败，可更换代理后重试" else null)
        }
    }

    /**
     * 先完整下载，再直接拉起系统安装器——下载前不做任何「安装未知应用」授权检查，
     * 也不跳转授权设置页。下载结果（APK 文件）回调给界面保留：
     * 即使系统安装界面被返回键取消，用户仍能一键「重新安装」这个已下载的包，无需再次下载。
     */
    fun downloadAndInstall(info: Updater.ReleaseInfo, onDone: (String, java.io.File?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            when (val result = Updater.download(ctx, info, prefs.current.updateProxy)) {
                is Updater.DownloadResult.Fail -> {
                    notify(result.reason)
                    onDone(result.reason, null)
                }
                is Updater.DownloadResult.Ok -> {
                    // 先下载已完成，这里才拉起系统安装器；不再检查 / 引导任何授权。
                    val msg = when (Updater.install(ctx, result.file)) {
                        Updater.InstallResult.STARTED -> "下载完成，请在系统安装界面确认安装"
                        Updater.InstallResult.FAILED -> "安装包已下载，但无法拉起安装器，可点「重新安装」重试"
                    }
                    notify(msg)
                    onDone(msg, result.file)
                }
            }
        }
    }

    /** 用已下载的安装包重新安装，不再下载，也不需要任何预授权。 */
    fun installDownloaded(file: java.io.File, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val msg = if (!file.exists()) {
                "安装包已失效，请重新下载"
            } else when (Updater.install(ctx, file)) {
                Updater.InstallResult.STARTED -> "请在系统安装界面确认安装"
                Updater.InstallResult.FAILED -> "无法拉起安装器，请重试或更换代理重新下载"
            }
            notify(msg)
            onDone(msg)
        }
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    private fun notify(msg: String) {
        _ui.value = _ui.value.copy(message = msg)
    }

    /** 当前抽屉可见应用：隐藏过滤 → 搜索词 → 分类过滤 → 按设置排序分组。 */
    fun visibleGroups(): List<Pair<String, List<AppInfo>>> {
        val s = _ui.value
        var apps = s.apps.filterNot { it.packageName in s.prefs.hiddenPackages }
        if (s.query.isNotBlank()) {
            val q = s.query.trim()
            apps = apps.filter {
                it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
            }
        }
        val counts = if (s.usageCounts.isNotEmpty()) s.usageCounts else s.prefs.launchCounts
        if (s.selectedCategory == Categories.FREQUENT) {
            // 「最常用」只显示这一个分组，不能再按应用自身分类重复列一遍（例如高德地图既出现在
            // 最常用，又在下面出现「网络工具 / 高德地图」）。
            apps = apps.filter { (counts[it.packageName] ?: 0) > 0 }
        } else if (s.selectedCategory != "all") {
            apps = apps.filter { it.categoryId == s.selectedCategory }
        }
        // 「最常用」只是分类栏里的独立入口；「全部」和各分类内部不再追加一段最常用。
        return Sorter.groupEx(apps, s.prefs, s.usageCounts).filter { (id, _) ->
            if (s.selectedCategory == Categories.FREQUENT) id == Categories.FREQUENT
            else id != Categories.FREQUENT
        }
    }

    fun categoryCount(categoryId: String): Int {
        val s = _ui.value
        val visible = s.apps.filterNot { it.packageName in s.prefs.hiddenPackages }
        val counts = if (s.usageCounts.isNotEmpty()) s.usageCounts else s.prefs.launchCounts
        return if (categoryId == "all") visible.size else visible.count {
            it.categoryId == categoryId ||
                (categoryId == Categories.FREQUENT && (counts[it.packageName] ?: 0) > 0)
        }
    }

    fun categoryLabel(id: String): String = _ui.value.prefs.categoryLabels[id] ?: Categories.labelOf(id)
    fun categoryIconKey(id: String): String = _ui.value.prefs.categoryIcons[id] ?: Categories.iconKeyOf(id)

    fun appLabel(app: AppInfo): String = app.label
    fun appIconKey(packageName: String): String? = _ui.value.prefs.appIcons[packageName]

    fun builtInIconLabel(key: String?): String = BuiltInIcons.labelOf(key)

    fun backupFileName() = backup.fileName()
}

sealed class IconTarget {
    data class App(val packageName: String) : IconTarget()
    data class Category(val categoryId: String) : IconTarget()
}
