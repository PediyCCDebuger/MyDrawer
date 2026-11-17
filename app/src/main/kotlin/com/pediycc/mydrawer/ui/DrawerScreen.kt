package com.pediycc.mydrawer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.pediycc.mydrawer.data.AppInfo
import com.pediycc.mydrawer.data.Categories
import com.pediycc.mydrawer.data.SortMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerScreen(vm: DrawerViewModel) {
    val state by vm.ui.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportBackup(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importBackup(it) } }

    val snackbarHostState = remember { SnackbarHostState() }
    state.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("MyDrawer") },
                actions = {
                    IconButton(onClick = { exportLauncher.launch(vm.backupFileName()) }) {
                        Icon(Icons.Filled.Save, contentDescription = "导出备份")
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Icon(Icons.Filled.Restore, contentDescription = "导入备份")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 搜索栏
            OutlinedTextField(
                value = state.query,
                onValueChange = { vm.setQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("搜索应用") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "清除")
                        }
                    }
                },
            )

            // 分类栏
            CategoryBar(
                selected = state.selectedCategory,
                counts = { id -> vm.categoryCount(id) },
                onSelect = { vm.selectCategory(it) },
            )

            // 应用网格
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val groups = remember(state.apps, state.query, state.selectedCategory, state.prefs) {
                    vm.visibleGroups()
                }
                AppGrid(
                    groups = groups,
                    columns = state.prefs.gridColumns,
                    onLaunch = { vm.launch(it.packageName) },
                    onLongClick = { menuApp = it },
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            prefs = state.prefs,
            onDismiss = { showSettings = false },
            onSortMode = { vm.setSortMode(it) },
            onColumns = { vm.setGridColumns(it) },
            onShowSystem = { vm.setShowSystemApps(it) },
        )
    }

    menuApp?.let { app ->
        AppMenuSheet(
            app = app,
            categories = Categories.ALL,
            currentCategory = app.categoryId,
            isHidden = app.packageName in state.prefs.hiddenPackages,
            onDismiss = { menuApp = null },
            onLaunch = { vm.launch(app.packageName); menuApp = null },
            onToggleHidden = { vm.toggleHidden(app.packageName); menuApp = null },
            onSetCategory = { vm.setCategoryOverride(app.packageName, it); menuApp = null },
        )
    }
}

@Composable
private fun CategoryBar(
    selected: String,
    counts: (String) -> Int,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == "all",
                onClick = { onSelect("all") },
                label = { Text("全部 (${counts("all")})") },
                leadingIcon = { Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
        items(Categories.ALL) { cat ->
            FilterChip(
                selected = selected == cat.id,
                onClick = { onSelect(cat.id) },
                label = { Text("${cat.label} (${counts(cat.id)})") },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGrid(
    groups: List<Pair<String, List<AppInfo>>>,
    columns: Int,
    onLaunch: (AppInfo) -> Unit,
    onLongClick: (AppInfo) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        groups.forEach { (catId, apps) ->
            stickyHeader {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = Categories.labelOf(catId),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            val rows = apps.chunked(columns)
            items(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { app ->
                        AppTile(
                            app = app,
                            onClick = { onLaunch(app) },
                            onLongClick = { onLongClick(app) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bitmap = remember(app.packageName) { app.icon?.toBitmap(96, 96) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium),
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium),
            ) {
                Box(Alignment.Center) {
                    Text(app.label.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppMenuSheet(
    app: AppInfo,
    categories: List<com.pediycc.mydrawer.data.CategoryDef>,
    currentCategory: String,
    isHidden: Boolean,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onToggleHidden: () -> Unit,
    onSetCategory: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text(app.label, style = MaterialTheme.typography.titleMedium)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            ListItem(headlineContent = { Text("启动应用") },
                leadingContent = { Icon(Icons.Filled.Launch, null) },
                modifier = Modifier.clickable(onClick = onLaunch))
            ListItem(
                headlineContent = { Text(if (isHidden) "取消隐藏" else "隐藏应用") },
                leadingContent = { Icon(if (isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null) },
                modifier = Modifier.clickable(onClick = onToggleHidden))
            Spacer(Modifier.height(8.dp))
            Text("移动到分类", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            categories.forEach { cat ->
                ListItem(
                    headlineContent = { Text(cat.label) },
                    leadingContent = {
                        if (cat.id == currentCategory) Icon(Icons.Filled.CheckCircle, null)
                        else Spacer(Modifier.size(24.dp))
                    },
                    modifier = Modifier.clickable { onSetCategory(cat.id) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    prefs: com.pediycc.mydrawer.data.UserPrefs,
    onDismiss: () -> Unit,
    onSortMode: (String) -> Unit,
    onColumns: (Int) -> Unit,
    onShowSystem: (Boolean) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text("设置", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            Text("排序方式", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            listOf(
                SortMode.BY_NAME to "按名称（拼音）",
                SortMode.BY_INSTALL_TIME to "按包名",
                SortMode.BY_CATEGORY to "按分类",
            ).forEach { (id, label) ->
                ListItem(
                    headlineContent = { Text(label) },
                    leadingContent = {
                        RadioButton(selected = prefs.sortMode == id, onClick = { onSortMode(id) })
                    },
                    modifier = Modifier.clickable { onSortMode(id) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Text("每行图标数：${prefs.gridColumns}", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (c in 3..6) {
                    FilterChip(
                        selected = prefs.gridColumns == c,
                        onClick = { onColumns(c) },
                        label = { Text("$c") },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("显示系统应用") },
                leadingContent = {
                    Checkbox(checked = prefs.showSystemApps, onCheckedChange = { onShowSystem(it) })
                },
                modifier = Modifier.clickable { onShowSystem(!prefs.showSystemApps) },
            )

            Text("提示：长按应用图标可隐藏或改分类；右上角可导出/导入配置备份。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(16.dp))
        }
    }
}
