package com.pediycc.mydrawer.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pediycc.mydrawer.data.AppInfo
import com.pediycc.mydrawer.data.AppRepository
import com.pediycc.mydrawer.data.BackupManager
import com.pediycc.mydrawer.data.Categories
import com.pediycc.mydrawer.data.CategoryCatalog
import com.pediycc.mydrawer.data.Preferences
import com.pediycc.mydrawer.data.SortMode
import com.pediycc.mydrawer.data.Sorter
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
)

class DrawerViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context = app.applicationContext
    private val prefs = Preferences(context)
    private val catalog = CategoryCatalog(context)
    private val repo = AppRepository(context, catalog, prefs)
    private val backup = BackupManager(context)

    private val _ui = MutableStateFlow(DrawerUiState(prefs = prefs.current))
    val ui: StateFlow<DrawerUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val apps = repo.loadApps()
            _ui.value = _ui.value.copy(apps = apps, prefs = prefs.current, loading = false)
        }
    }

    fun selectCategory(id: String) {
        _ui.value = _ui.value.copy(selectedCategory = id)
    }

    fun setQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
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

    fun setSortMode(mode: String) {
        prefs.update { it.copy(sortMode = mode) }
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    fun setGridColumns(columns: Int) {
        prefs.update { it.copy(gridColumns = columns.coerceIn(3, 6)) }
        _ui.value = _ui.value.copy(prefs = prefs.current)
    }

    fun setShowSystemApps(show: Boolean) {
        prefs.update { it.copy(showSystemApps = show) }
        refresh()
    }

    fun launch(packageName: String) {
        val intent = repo.getLaunchIntent(packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure { notify("无法启动该应用") }
        } else {
            notify("该应用没有可启动入口")
        }
    }

    /** 当前抽屉可见应用：先按隐藏过滤，再按搜索词与分类过滤，最后排序。 */
    fun visibleGroups(): List<Pair<String, List<AppInfo>>> {
        val s = _ui.value
        var apps = s.apps.filterNot { it.packageName in s.prefs.hiddenPackages }
        if (s.query.isNotBlank()) {
            val q = s.query.trim()
            apps = apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        }
        if (s.selectedCategory != "all") {
            apps = apps.filter { it.categoryId == s.selectedCategory }
        }
        return Sorter.group(apps, s.prefs.sortMode)
    }

    fun categoryCount(categoryId: String): Int {
        val s = _ui.value
        val visible = s.apps.filterNot { it.packageName in s.prefs.hiddenPackages }
        return if (categoryId == "all") visible.size else visible.count { it.categoryId == categoryId }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val text = backup.export(prefs.current)
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            }.onSuccess { notify("备份已导出") }
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
                    notify("备份已导入")
                }
                .onFailure { notify("导入失败：${it.message}") }
        }
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    private fun notify(msg: String) {
        _ui.value = _ui.value.copy(message = msg)
    }

    val categories get() = Categories.ALL
    val sortModes get() = listOf(SortMode.BY_NAME, SortMode.BY_INSTALL_TIME, SortMode.BY_CATEGORY)
    fun backupFileName() = backup.fileName()
}
