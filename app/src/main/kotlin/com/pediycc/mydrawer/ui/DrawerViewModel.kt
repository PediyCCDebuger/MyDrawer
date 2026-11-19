package com.pediycc.mydrawer.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pediycc.mydrawer.BuildConfig
import com.pediycc.mydrawer.data.AppCache
import com.pediycc.mydrawer.data.AppInfo
import com.pediycc.mydrawer.data.AppRepository
import com.pediycc.mydrawer.data.BackupManager
import com.pediycc.mydrawer.data.BuiltInIcons
import com.pediycc.mydrawer.data.Categories
import com.pediycc.mydrawer.data.CategoryCatalog
import com.pediycc.mydrawer.data.DebugLog
import com.pediycc.mydrawer.data.Pinyin
import com.pediycc.mydrawer.data.Preferences
import com.pediycc.mydrawer.data.SearchMatcher
import com.pediycc.mydrawer.data.SortMode
import com.pediycc.mydrawer.data.Sorter
import com.pediycc.mydrawer.data.Updater
import com.pediycc.mydrawer.data.UsageStatsHelper
import com.pediycc.mydrawer.data.UserPrefs
import com.pediycc.mydrawer.data.inCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** 分类多选：批量删除 / 移动分类（与应用多选互斥）。 */
    val categorySelectMode: Boolean = false,
    val selectedCategories: Set<String> = emptySet(),
    /** 系统使用统计（真实手机使用频率）；未授权时为空，回落到 app 内启动计数。 */
    val usageCounts: Map<String, Int> = emptyMap(),
    /** 本机安装包总数（含因无桌面入口而未列出的），供设置页核对载入数量。 */
    val totalInstalled: Int = 0,
    val unlistedApps: List<AppRepository.UnlistedApp> = emptyList(),
    /**
     * 2.7.1 新增：进入嵌套子分类的返回栈。栈顶是上一级分类，
     * 返回键优先出栈回到上级分类，栈空才退出抽屉。
     */
    val categoryTrail: List<String> = emptyList(),
    /** 2.9.7：自动检查发现新版本时，交给界面弹出更新对话框（只提示一次）。 */
    val autoUpdateRelease: Updater.ReleaseInfo? = null,
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

    /** 2.9.9：动态注册的系统变化监听，退出时注销。 */
    private val packageWatchers = mutableListOf<BroadcastReceiver>()
    private var lastPackageEventMs = 0L

    init {
        refresh()
        // 2.9.9：应用安装 / 卸载 / 更新、系统语言切换时立即刷新，避免停留在旧图标 / 漏掉新应用。
        // 抽屉应用使用时在前台，动态注册即可；签名机制会自动让旧缓存失效并全量重建。
        val pkgReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                onSystemAppChange(intent.action ?: "package")
            }
        }
        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        runCatching {
            context.registerReceiver(pkgReceiver, pkgFilter)
            packageWatchers += pkgReceiver
        }
        val localeReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { onSystemAppChange("locale") }
        }
        runCatching {
            context.registerReceiver(localeReceiver, IntentFilter(Intent.ACTION_LOCALE_CHANGED))
            packageWatchers += localeReceiver
        }
    }

    /**
     * 一次安装 / 卸载可能连发多个广播，600ms 内合并为一次刷新，避免重复全量扫描。
     */
    private fun onSystemAppChange(action: String) {
        val now = System.currentTimeMillis()
        if (now - lastPackageEventMs < 600L) return
        lastPackageEventMs = now
        DebugLog.log(context, "检测到系统变化（$action），刷新应用列表")
        refresh()
    }

    /** 回到抽屉时用签名校验应用是否有过变化（覆盖广播未送达 / 进程被杀期间的变化）。 */
    fun refreshIfPackageChanged() {
        viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) { repo.signatureChanged() }
            if (changed) {
                DebugLog.log(context, "返回抽屉：检测到应用列表变化，刷新")
                refresh()
            }
        }
    }

    override fun onCleared() {
        packageWatchers.forEach { receiver -> runCatching { context.unregisterReceiver(receiver) } }
        packageWatchers.clear()
        super.onCleared()
    }

    /**
     * 启动时进入的分类：startupCategory 为空则沿用 lastCategory（上次使用的分类），
     * 否则固定进入指定入口；无效 / 已隐藏的入口最后在 refresh 里由 fallbackCategory 兜底。
     */
    private fun resolveStartupCategory(p: UserPrefs): String =
        if (p.startupCategory.isBlank()) p.lastCategory else p.startupCategory

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            // 2.9.9：应用列表与使用统计并行读取，且 usage 挪到 IO 线程（原本在主线程阻塞）。
            // 后者需要查询系统使用情况数据库，可能较慢；两者无依赖，并行可缩短启动等待。
            val appsDeferred = async(Dispatchers.IO) { repo.loadApps() }
            val usageDeferred = async(Dispatchers.IO) { UsageStatsHelper.load(context) }
            val loaded = appsDeferred.await()
            val apps = loaded.apps
            val usage = usageDeferred.await()
            DebugLog.log(
                context,
                "载入应用：当前列出 ${apps.size}，本机安装 ${loaded.totalInstalled}，无桌面入口未列出 ${loaded.unlisted.size}，" +
                    "使用统计 ${usage.size} 条${if (usage.isEmpty()) "（未授权）" else ""}",
            )
            // 分类可能被删除/改名，返回栈里失效的入口要剔掉，否则返回键会退到空白视图。
            val validIds = Categories.orderedFor(prefs.current).map { it.id }.toSet() + "all"
            _ui.value = _ui.value.copy(
                apps = apps,
                prefs = prefs.current,
                loading = false,
                selectedCategory = fallbackCategory(_ui.value.selectedCategory, prefs.current, apps),
                selected = _ui.value.selected.intersect(apps.map { it.packageName }.toSet()),
                selectedCategories = _ui.value.selectedCategories.intersect(validIds - "all"),
                usageCounts = usage,
                totalInstalled = loaded.totalInstalled,
                unlistedApps = loaded.unlisted,
                categoryTrail = Categories.sanitizeTrail(_ui.value.categoryTrail, validIds),
            )
        }
    }

    /**
     * 2.9.8：清空应用列表缓存并重建。安装/卸载应用会自动让缓存失效，
     * 这里给用户在图标异常、名称从不正确的旧缓存还原等情况下一个手动强制刷新的入口。
     */
    fun rebuildAppCache() {
        viewModelScope.launch {
            AppCache.clear(context)
            DebugLog.log(context, "手动清空应用列表缓存，开始全量重建")
            refresh()
            notify("应用列表缓存已重建")
        }
    }

    /** 从系统「使用情况访问权限」设置页返回后重新读取真实使用频率。 */
    fun reloadUsage() {
        viewModelScope.launch {
            val usage = withContext(Dispatchers.IO) { UsageStatsHelper.load(context) }
            _ui.value = _ui.value.copy(usageCounts = usage)
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

    /** 分类栏点选：清空嵌套返回栈，回到顶层视图。 */
    fun selectCategory(id: String) {
        prefs.update { it.copy(lastCategory = id) }
        _ui.value = _ui.value.copy(
            selectedCategory = id,
            prefs = prefs.current,
            selectMode = false,
            selected = emptySet(),
            categorySelectMode = false,
            selectedCategories = emptySet(),
            categoryTrail = emptyList(),
        )
    }

    /**
     * 从父分类内容里点进嵌套子分类：把当前分类压入返回栈，
     * 这样返回键能退回上级分类，而不是直接退出抽屉。
     */
    fun openChildCategory(id: String) {
        val cur = _ui.value.selectedCategory
        _ui.value = _ui.value.copy(
            selectedCategory = id,
            selectMode = false,
            selected = emptySet(),
            categorySelectMode = false,
            selectedCategories = emptySet(),
            categoryTrail = Categories.pushTrail(_ui.value.categoryTrail, cur, id),
        )
    }

    /** 返回上一级分类；返回 false 表示已经在顶层，交给系统处理返回键。 */
    fun backToParentCategory(): Boolean {
        val (parent, trail) = Categories.popTrail(_ui.value.categoryTrail) ?: return false
        _ui.value = _ui.value.copy(
            selectedCategory = parent,
            categoryTrail = trail,
            selectMode = false,
            selected = emptySet(),
        )
        // 回到顶层分类时同步「上次使用的分类」；嵌套子分类不写，避免下次启动停在没有入口的子分类里。
        val topIds = Categories.topLevel(prefs.current).map { it.id }
        if (parent == "all" || parent in topIds) {
            prefs.update { it.copy(lastCategory = parent) }
        }
        return true
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

    /**
     * 长按菜单「发送到桌面」：把应用作为固定快捷方式请求系统桌面创建。
     * 用 ShortcutManager.requestPinShortcut（API 26+），图标与名称沿用当前抽屉里的显示；
     * 桌面不支持固定快捷方式时给出提示，不静默失败。
     */
    fun pinToHome(packageName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notify("当前系统版本不支持发送到桌面")
            return
        }
        val ctx = getApplication<Application>()
        val sm = ctx.getSystemService(ShortcutManager::class.java)
        if (sm == null || !sm.isRequestPinShortcutSupported) {
            notify("当前桌面不支持固定快捷方式，无法发送到桌面")
            return
        }
        val intent = repo.getLaunchIntent(packageName)
        if (intent == null) {
            notify("该应用没有可启动入口")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val app = _ui.value.apps.firstOrNull { it.packageName == packageName }
        val label = (app?.label ?: packageName).take(20)
        val builder = ShortcutInfo.Builder(ctx, "mydrawer-pin-$packageName")
            .setShortLabel(label)
            .setIntent(intent)
        // 优先用抽屉里当前显示的图标（含用户自定义图标），失败再退回应用自带图标。
        val bitmapIcon = app?.icon?.let { d ->
            runCatching { Icon.createWithBitmap(d.toBitmap(192, 192)) }.getOrNull()
        }
        if (bitmapIcon != null) {
            builder.setIcon(bitmapIcon)
        } else {
            val res = runCatching { ctx.packageManager.getApplicationInfo(packageName, 0).icon }
                .getOrDefault(0)
            if (res != 0) builder.setIcon(Icon.createWithResource(packageName, res))
        }
        val ok = runCatching { sm.requestPinShortcut(builder.build(), null) }.getOrDefault(false)
        notify(if (ok) "已发送到桌面，请在系统提示里确认添加" else "发送失败，桌面可能不支持该功能")
    }

    /** 管理隐藏应用：批量恢复（多选）选中的应用，重新出现在抽屉里。 */
    fun restoreHidden(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        prefs.unhidePackages(packageNames)
        refresh()
        notify("已恢复 ${packageNames.size} 个应用")
    }

    // ---- 管理隐藏应用密码 ----

    fun hasPassword(): Boolean = prefs.hasPassword()

    /** 校验口令，正确返回 true；错误提示由调用它的对话框内联显示。 */
    fun checkPassword(raw: String): Boolean = prefs.verifyPassword(raw)

    fun setPassword(raw: String) {
        prefs.setPassword(raw)
        _ui.value = _ui.value.copy(prefs = prefs.current)
        notify(if (raw.isEmpty()) "密码已清除" else "密码已设置")
    }

    fun clearPassword() {
        prefs.clearPassword()
        _ui.value = _ui.value.copy(prefs = prefs.current)
        notify("密码已清除")
    }

    fun setCategoryOverride(packageName: String, categoryId: String) {
        prefs.overrideCategory(packageName, categoryId)
        refresh()
        notify("分类已更改")
    }

    /**
     * 2.9.0 勾选式多分类：切换应用在某分类里的归属（勾上加入、取消移出）。
     * 主分类缺失时顺延附加分类，全部取消则归入「未分类」；同一个应用可在多个分类中出现。
     */
    fun toggleAppCategory(packageName: String, catId: String) {
        val app = _ui.value.apps.firstOrNull { it.packageName == packageName } ?: return
        prefs.toggleAppCategory(packageName, app.categoryId, catId)
        refresh()
    }

    /** 2.9.0 批量勾选（多选模式）：把选中应用的归属精确设为 [checked]（按勾选顺序）。 */
    fun applyCategoriesToSelected(checked: List<String>) {
        val ids = _ui.value.selected
        if (ids.isEmpty()) {
            notify("请先选择应用")
            return
        }
        prefs.setAppMemberships(ids, checked)
        _ui.value = _ui.value.copy(selectMode = false, selected = emptySet())
        refresh()
        notify(
            if (checked.isEmpty()) "已将 ${ids.size} 个应用移入「未分类」"
            else "已更新 ${ids.size} 个应用的所属分类"
        )
    }

    /**
     * 卸载逻辑已上移到 DrawerScreen（2.9.2）：需要按路径逐条尝试并汇总诊断信息，
     * 直接在界面层用 Activity / Application 两种上下文完成，ViewModel 不再参与。
     */

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

    /** 拖拽排序：把 [fromIndex] 处的分类移到 [toIndex]；「未分类」固定排在最后，不参与换位。 */
    fun reorderCategory(fromIndex: Int, toIndex: Int) {
        val ids = Categories.orderedFor(prefs.current).map { it.id }
        val next = Categories.reorderKeepingLast(ids, fromIndex, toIndex)
        if (next == ids) return
        prefs.update { it.copy(categoryOrder = next) }
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

    /** 新建分类；可同时指定内置图标。[iconKey] 为空时沿用默认图标。 */
    fun createCategory(label: String, iconKey: String? = null): String {
        val id = prefs.addCategory(label.trim())
        if (!iconKey.isNullOrBlank()) prefs.setCategoryIcon(id, iconKey)
        prefs.update { it.copy(lastCategory = id) }
        _ui.value = _ui.value.copy(
            prefs = prefs.current,
            selectedCategory = id,
            categorySelectMode = false,
            selectedCategories = emptySet(),
        )
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

    // ---- 2.7.0 分类嵌套 ----

    /** 把 [childId] 挂到 [parentId] 下；parentId 为空则移回顶层。 */
    fun setCategoryParent(childId: String, parentId: String) {
        val ok = prefs.setCategoryParent(childId, parentId)
        if (!ok) {
            notify("不能把分类移到自己或它的子分类里")
            return
        }
        // 若当前正停在被子分类占据的位置，回落到稳定入口。
        if (childId == _ui.value.selectedCategory) {
            _ui.value = _ui.value.copy(
                prefs = prefs.current,
                selectedCategory = fallbackCategory(_ui.value.selectedCategory, prefs.current),
            )
        } else {
            _ui.value = _ui.value.copy(prefs = prefs.current)
        }
        notify(if (parentId.isEmpty()) "已移回顶层分类" else "已移入「${categoryLabel(parentId)}」")
    }

    /** 设置父分类内子分类相对应用的位置。 */
    fun setNestedChildMode(mode: String) {
        prefs.setNestedChildMode(mode)
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    /** 手动排序：拖动调整某父分类下子分类的顺序。 */
    fun reorderNestedChild(parentId: String, from: Int, to: Int) {
        val list = Categories.childrenOf(parentId, prefs.current).map { it.id }.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val id = list.removeAt(from)
        list.add(to, id)
        prefs.setNestedChildOrder(parentId, list)
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    /** 取消某个应用的分类，使其进入「未分类」。 */
    fun clearCategory(packageName: String) {
        prefs.overrideCategory(packageName, Categories.OTHER)
        refresh()
        notify("已取消分类，应用移入「未分类」")
    }

    /** 多选批量隐藏：把选中的应用加入隐藏列表并退出多选。 */
    fun hideSelected() {
        val ids = _ui.value.selected
        if (ids.isEmpty()) {
            notify("请先选择应用")
            return
        }
        prefs.hidePackages(ids)
        _ui.value = _ui.value.copy(selectMode = false, selected = emptySet())
        refresh()
        notify("已隐藏 ${ids.size} 个应用")
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
        return visible.any { it.inCategory(Categories.OTHER, p.categoryExtras) }
    }

    /** 首次进入时的使用频率授权提示，标记后不再重复。 */
    fun markUsagePromptShown() {
        prefs.markUsagePromptShown()
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    // ---- 多选 ----

    fun setSelectMode(on: Boolean) {
        _ui.value = _ui.value.copy(
            selectMode = on,
            selected = emptySet(),
            categorySelectMode = false,
            selectedCategories = emptySet(),
        )
    }

    fun toggleSelected(packageName: String) {
        val s = _ui.value
        val next = if (packageName in s.selected) s.selected - packageName else s.selected + packageName
        _ui.value = s.copy(selected = next)
    }

    /** 应用长按菜单里的「多选」：直接进入多选模式，并预先勾选这个应用。 */
    fun startSelectFor(packageName: String) {
        _ui.value = _ui.value.copy(
            selectMode = true,
            selected = setOf(packageName),
            categorySelectMode = false,
            selectedCategories = emptySet(),
        )
    }

    /**
     * 分类长按菜单里的「多选」：进入多选模式，并把该分类当前显示的应用全部勾选，
     * 便于整类批量隐藏 / 移动 / 取消分类；之后再点其它应用可继续增减。
     */
    fun startSelectForCategory(categoryId: String) {
        val ids = visibleGroups()
            .filter { it.categoryId == categoryId }
            .flatMap { it.apps }
            .map { it.packageName }
            .toSet()
        if (ids.isEmpty()) {
            notify("该分类里没有可选择的应​用")
            return
        }
        _ui.value = _ui.value.copy(
            selectMode = true,
            selected = ids,
            categorySelectMode = false,
            selectedCategories = emptySet(),
        )
    }

    // ---- 分类多选（批量删除 / 移动分类） ----

    /** 长按分类菜单里的「多选」：进入分类多选，并勾选当前分类。 */
    fun startCategorySelect(categoryId: String) {
        _ui.value = _ui.value.copy(
            categorySelectMode = true,
            selectedCategories = setOf(categoryId),
            selectMode = false,
            selected = emptySet(),
            categoryTrail = emptyList(),
        )
    }

    fun toggleCategorySelected(categoryId: String) {
        val s = _ui.value
        val next = if (categoryId in s.selectedCategories) {
            s.selectedCategories - categoryId
        } else {
            s.selectedCategories + categoryId
        }
        _ui.value = s.copy(selectedCategories = next)
    }

    fun exitCategorySelect() {
        _ui.value = _ui.value.copy(categorySelectMode = false, selectedCategories = emptySet())
    }

    /**
     * 批量删除选中的分类。「未分类」「最常用」是兜底 / 虚拟入口，不参与删除；
     * 其中的应用由 Repository 在下次刷新时归入「未分类」。
     */
    fun deleteSelectedCategories() {
        val s = _ui.value
        val deletable = s.selectedCategories.filter { it != Categories.OTHER && it != Categories.FREQUENT }
        if (deletable.isEmpty()) {
            notify("「未分类」「最常用」不能删除")
            return
        }
        deletable.forEach { prefs.deleteCategory(it) }
        _ui.value = _ui.value.copy(
            prefs = prefs.current,
            categorySelectMode = false,
            selectedCategories = emptySet(),
            selectedCategory = fallbackCategory(s.selectedCategory, prefs.current),
            categoryTrail = emptyList(),
        )
        refresh()
        notify("已删除 ${deletable.size} 个分类，其中的应用已移入「未分类」")
    }

    /**
     * 批量移动选中的分类到 [parentId] 下（空字符串表示移回顶层）。
     * 冲突项（会成环 / 目标是自己）跳过，最后汇总提示。
     */
    fun moveSelectedCategories(parentId: String) {
        val s = _ui.value
        val ids = s.selectedCategories.toList()
        if (ids.isEmpty()) {
            notify("请先选择分类")
            return
        }
        var moved = 0
        var skipped = 0
        ids.forEach { id ->
            if (prefs.setCategoryParent(id, parentId)) moved++ else skipped++
        }
        // 当前停留的分类被移进别的分类后，分类栏里已没有它的入口，回到目标父分类。
        val cur = s.selectedCategory
        val nextCur = if (cur in ids && parentId.isNotEmpty()) parentId else cur
        _ui.value = _ui.value.copy(
            prefs = prefs.current,
            categorySelectMode = false,
            selectedCategories = emptySet(),
            selectedCategory = nextCur,
            categoryTrail = emptyList(),
        )
        refresh()
        notify(
            when {
                moved == 0 -> "没有可移动的分类"
                skipped > 0 -> "已移动 $moved 个分类，$skipped 个因嵌套关系冲突被跳过"
                parentId.isEmpty() -> "已将 $moved 个分类移回顶层"
                else -> "已将 $moved 个分类移入「${categoryLabel(parentId)}」"
            },
        )
    }

    fun selectAllVisible() {
        val ids = visibleGroups().flatMap { it.apps }.map { it.packageName }.toSet()
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
            }.onSuccess {
                DebugLog.log(context, "配置备份导出成功：${text.length} 字节")
                notify("配置备份已导出")
            }.onFailure {
                DebugLog.log(context, "配置备份导出失败：${it.message}")
                notify("导出失败：${it.message}")
            }
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
                    DebugLog.log(context, "配置备份导入成功，重载应用列表")
                    prefs.replaceAll(imported)
                    refresh()
                    notify("配置备份已导入")
                }
                .onFailure {
                    DebugLog.log(context, "配置备份导入失败：${it.message}")
                    notify("导入失败：${it.message}")
                }
        }
    }

    fun checkUpdate(onResult: (Updater.ReleaseInfo?, String?) -> Unit) {
        viewModelScope.launch {
            val info = Updater.fetchLatest(prefs.current.updateProxy)
            DebugLog.log(
                context,
                if (info == null) "检查更新失败（代理=${prefs.current.updateProxy}）"
                else "检查更新成功：远端 ${info.tag}",
            )
            onResult(info, if (info == null) "检查更新失败，可更换代理后重试" else null)
        }
    }

    /**
     * 2.9.7 自动检查更新：开关默认关闭，开启后进入前台时每 24 小时最多检查一次；
     * 刚打开开关时用 [force] 立即检查一次。只有确实发现新版本才弹对话框，
     * 已是最新或网络失败不打扰用户；全过程写调试日志便于排查。
     */
    fun checkAutoUpdate(force: Boolean = false) {
        val p = prefs.current
        if (!p.autoUpdate) return
        val now = System.currentTimeMillis()
        if (!force && !Updater.shouldAutoCheck(true, p.lastAutoUpdateCheck, now)) return
        // 先记时间戳：即使网络失败，本次会话也不会反复请求。
        prefs.update { it.copy(lastAutoUpdateCheck = now) }
        _ui.value = _ui.value.copy(prefs = prefs.current)
        DebugLog.log(
            context,
            "自动检查更新：已发起（间隔 ${Updater.AUTO_CHECK_INTERVAL_MS / 3_600_000} 小时" +
                "${if (force) "，开关刚开启" else ""}，代理=${p.updateProxy}）",
        )
        viewModelScope.launch {
            val info = Updater.fetchLatest(prefs.current.updateProxy)
            if (info == null) {
                DebugLog.log(context, "自动检查更新失败，静默跳过")
                return@launch
            }
            DebugLog.log(context, "自动检查更新完成：远端 ${info.tag}")
            if (Updater.isNewer(info.tag, BuildConfig.VERSION_NAME)) {
                DebugLog.log(context, "发现新版本 ${info.tag}，弹出更新对话框")
                _ui.value = _ui.value.copy(autoUpdateRelease = info)
            }
        }
    }

    /** 用户关掉自动检查弹出的更新对话框。 */
    fun dismissAutoUpdate() {
        _ui.value = _ui.value.copy(autoUpdateRelease = null)
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
                    DebugLog.log(ctx, "更新下载失败：${result.reason}")
                    notify(result.reason)
                    onDone(result.reason, null)
                }
                is Updater.DownloadResult.Ok -> {
                    // 先下载已完成，这里才拉起系统安装器；不再检查 / 引导任何授权。
                    val msg = when (Updater.install(ctx, result.file)) {
                        Updater.InstallResult.STARTED -> {
                            DebugLog.log(ctx, "更新包下载完成（${result.file.length()} 字节），已拉起系统安装器")
                            "下载完成，请在系统安装界面确认安装"
                        }
                        Updater.InstallResult.FAILED -> {
                            DebugLog.log(ctx, "已下载安装包但拉起系统安装器失败：${result.file.name}")
                            "安装包已下载，但无法拉起安装器，可点「重新安装」重试"
                        }
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

    /** 供界面层主动下发提示（如卸载诊断信息）。 */
    fun notifyUser(msg: String) = notify(msg)

    /** 当前抽屉可见内容：隐藏过滤 → 搜索词 → 分类过滤 → 按设置排序分组（含嵌套子分类入口）。 */
    fun visibleGroups(): List<Sorter.NestedGroup> {
        val s = _ui.value
        var apps = s.apps.filterNot { it.packageName in s.prefs.hiddenPackages }
        if (s.query.isNotBlank()) {
            val q = s.query.trim()
            apps = apps.filter { SearchMatcher.matches(it.label, it.packageName, q) { Pinyin.romanize(it) } }
        }
        val counts = if (s.usageCounts.isNotEmpty()) s.usageCounts else s.prefs.launchCounts
        if (s.selectedCategory == Categories.FREQUENT) {
            apps = apps.filter { (counts[it.packageName] ?: 0) > 0 }
        } else if (s.selectedCategory != "all") {
            // 选中某个分类时，子分类的应用不算它自己的，点进子分类才显示。
            // 2.9.0 多分类：附加归属也算该分类的成员。
            apps = apps.filter { it.inCategory(s.selectedCategory, s.prefs.categoryExtras) }
        }
        return Sorter.groupsWithNested(s.selectedCategory, apps, s.prefs, s.usageCounts)
            .filter { it.apps.isNotEmpty() || it.isChildEntry }
            .filter { g ->
                // 「最常用」只是分类栏里的独立入口；其它视图不追加这一段。
                if (s.selectedCategory == Categories.FREQUENT) g.categoryId == Categories.FREQUENT
                else g.categoryId != Categories.FREQUENT
            }
    }

    fun categoryCount(categoryId: String): Int {
        val s = _ui.value
        val visible = s.apps.filterNot { it.packageName in s.prefs.hiddenPackages }
        val counts = if (s.usageCounts.isNotEmpty()) s.usageCounts else s.prefs.launchCounts
        return when (categoryId) {
            "all" -> visible.size
            // 「最常用」只列前 N（可在设置自定义），计数不能跟着全量使用记录走。
            Categories.FREQUENT -> visible.count { (counts[it.packageName] ?: 0) > 0 }
                .coerceAtMost(s.prefs.frequentLimit.coerceAtLeast(1))
            else -> visible.count { it.inCategory(categoryId, s.prefs.categoryExtras) }
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
