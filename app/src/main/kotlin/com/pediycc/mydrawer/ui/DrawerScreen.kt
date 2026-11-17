package com.pediycc.mydrawer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pediycc.mydrawer.data.AppInfo
import com.pediycc.mydrawer.data.BuiltInIcons
import com.pediycc.mydrawer.data.Categories
import com.pediycc.mydrawer.data.SortMode
import com.pediycc.mydrawer.data.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerScreen(vm: DrawerViewModel) {
    val state by vm.ui.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var menuCategory by remember { mutableStateOf<String?>(null) }
    var moveCategoryOpen by remember { mutableStateOf(false) }
    var renameAppTarget by remember { mutableStateOf<AppInfo?>(null) }
    var renameCategoryTarget by remember { mutableStateOf<String?>(null) }
    var iconTarget by remember { mutableStateOf<IconTarget?>(null) }
    var showUpdate by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { vm.exportBackup(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.importBackup(it) } }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { u -> iconTarget?.let { vm.setCustomIcon(u, it) } } }

    val snackbarHostState = remember { SnackbarHostState() }
    state.message?.let { msg ->
        LaunchedEffect(msg) {
            snackbarHostState.showSnackbar(msg)
            vm.consumeMessage()
        }
    }

    val scrimColor = if (state.prefs.transparentBackground) Color(0xB3000000) else Color(0xFF101217)

    Box(Modifier.fillMaxSize().background(scrimColor)) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Text(
                            if (state.selectMode) "已选择 ${state.selected.size} 项"
                            else vm.categoryLabel(state.selectedCategory),
                            color = Color.White,
                        )
                    },
                    navigationIcon = {
                        if (state.selectMode) {
                            IconButton(onClick = { vm.setSelectMode(false) }) {
                                Icon(Icons.Filled.Close, "退出多选", tint = Color.White)
                            }
                        }
                    },
                    actions = {
                        if (state.selectMode) {
                            IconButton(onClick = { vm.selectAllVisible() }) {
                                Icon(Icons.Filled.SelectAll, "全选", tint = Color.White)
                            }
                            IconButton(onClick = { moveCategoryOpen = true }) {
                                Icon(Icons.Filled.DriveFileMove, "移动到分类", tint = Color.White)
                            }
                        } else {
                            IconButton(onClick = { vm.setSelectMode(true) }) {
                                Icon(Icons.Filled.Checklist, "多选", tint = Color.White)
                            }
                            IconButton(onClick = { exportLauncher.launch(vm.backupFileName()) }) {
                                Icon(Icons.Filled.Save, "导出配置备份（软盘图标）", tint = Color.White)
                            }
                            IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                                Icon(Icons.Filled.Restore, "导入配置备份（时钟图标）", tint = Color.White)
                            }
                            if (state.prefs.showMenuButton) {
                                IconButton(onClick = { showUpdate = true }) {
                                    Icon(Icons.Filled.SystemUpdate, "检查更新", tint = Color.White)
                                }
                            }
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Filled.Settings, "设置", tint = Color.White)
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                val categories = remember(state.prefs) {
                    Categories.ordered(state.prefs.categoryOrder, state.prefs.frequentEnabled)
                }
                val rail: @Composable () -> Unit = {
                    CategoryRail(
                        categories = categories,
                        selected = state.selectedCategory,
                        prefs = state.prefs,
                        vm = vm,
                        onSelect = { vm.selectCategory(it) },
                        onLongClick = { menuCategory = it },
                    )
                }
                if (state.prefs.leftHanded) {
                    Box(Modifier.weight(1f)) { AppArea(vm, state, onMenu = { menuApp = it }) }
                    rail()
                } else {
                    rail()
                    Box(Modifier.weight(1f)) { AppArea(vm, state, onMenu = { menuApp = it }) }
                }
            }
        }

        if (state.selectMode) {
            Surface(
                color = Color(0xE6202328),
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text("点击应用选择 · 点右上角箭头移动到分类", color = Color.White, modifier = Modifier.padding(12.dp))
            }
        }
    }

    if (showSettings) {
        SettingsScreen(
            vm = vm,
            prefs = state.prefs,
            onDismiss = { showSettings = false },
            onExport = { exportLauncher.launch(vm.backupFileName()) },
            onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
        )
    }

    if (showUpdate) {
        UpdateDialog(vm = vm, onDismiss = { showUpdate = false })
    }

    menuApp?.let { app ->
        AppMenuSheet(
            app = app,
            prefs = state.prefs,
            onDismiss = { menuApp = null },
            onLaunch = { vm.launch(app.packageName); menuApp = null },
            onToggleHidden = { vm.toggleHidden(app.packageName); menuApp = null },
            onSetCategory = { vm.setCategoryOverride(app.packageName, it); menuApp = null },
            onRename = { renameAppTarget = app; menuApp = null },
            onIcon = { iconTarget = IconTarget.App(app.packageName); menuApp = null },
        )
    }

    menuCategory?.let { id ->
        CategoryMenuSheet(
            categoryId = id,
            vm = vm,
            prefs = state.prefs,
            onDismiss = { menuCategory = null },
            onRename = { renameCategoryTarget = id; menuCategory = null },
            onIcon = { iconTarget = IconTarget.Category(id); menuCategory = null },
        )
    }

    if (moveCategoryOpen) {
        MoveDialog(
            categories = Categories.ordered(state.prefs.categoryOrder, state.prefs.frequentEnabled),
            labelOf = { vm.categoryLabel(it) },
            onDismiss = { moveCategoryOpen = false },
            onPick = { vm.moveSelectedTo(it); moveCategoryOpen = false },
        )
    }

    renameAppTarget?.let { app ->
        TextInputDialog(
            title = "重命名应用",
            initial = app.label,
            onDismiss = { renameAppTarget = null },
            onConfirm = { vm.renameApp(app.packageName, it); renameAppTarget = null },
        )
    }

    renameCategoryTarget?.let { id ->
        TextInputDialog(
            title = "重命名分类",
            initial = vm.categoryLabel(id),
            onDismiss = { renameCategoryTarget = null },
            onConfirm = { vm.renameCategory(id, it); renameCategoryTarget = null },
        )
    }

    iconTarget?.let { target ->
        IconPickerSheet(
            target = target,
            vm = vm,
            onDismiss = { iconTarget = null },
            onGallery = { galleryLauncher.launch(arrayOf("image/*")) },
        )
    }
}

@Composable
private fun AppArea(
    vm: DrawerViewModel,
    state: com.pediycc.mydrawer.ui.DrawerUiState,
    onMenu: (AppInfo) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { vm.setQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            placeholder = { Text("搜索应用", color = Color(0xFFB9C0C8)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color(0xFFB9C0C8)) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Filled.Close, "清除", tint = Color.White)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF8FB4FF),
                unfocusedBorderColor = Color(0xFF66707B),
            ),
        )
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            val groups = remember(state.apps, state.query, state.selectedCategory, state.prefs) {
                vm.visibleGroups()
            }
            AppGrid(
                vm = vm,
                state = state,
                groups = groups,
                onMenu = onMenu,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRail(
    categories: List<com.pediycc.mydrawer.data.CategoryDef>,
    selected: String,
    prefs: com.pediycc.mydrawer.data.UserPrefs,
    vm: DrawerViewModel,
    onSelect: (String) -> Unit,
    onLongClick: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(if (prefs.showCategoryLabels) 76.dp else 58.dp)
            .background(Color(0x33000000))
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(6.dp))
        // 「全部」入口
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onSelect("all") }, onLongClick = {}),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(
                        if (selected == "all") Color(0x668FB4FF) else Color(0x33FFFFFF),
                        MaterialTheme.shapes.small,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Apps, "全部", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            if (prefs.showCategoryLabels) {
                Text("全部", color = Color.White, fontSize = 11.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(4.dp))
        categories.forEach { cat ->
            val catSelected = selected == cat.id
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onSelect(cat.id) }, onLongClick = { onLongClick(cat.id) }),
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .background(
                            if (catSelected) Color(0x668FB4FF) else Color.Transparent,
                            MaterialTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    DrawerIcon(
                        key = vm.categoryIconKey(cat.id),
                        fallbackVector = BuiltInIcons.find(cat.iconKey)?.vector ?: Icons.Filled.Apps,
                        size = 28.dp,
                        contentDescription = cat.label,
                    )
                }
                if (prefs.showCategoryLabels) {
                    Text(
                        vm.categoryLabel(cat.id),
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGrid(
    vm: DrawerViewModel,
    state: com.pediycc.mydrawer.ui.DrawerUiState,
    groups: List<Pair<String, List<AppInfo>>>,
    onMenu: (AppInfo) -> Unit,
) {
    val columns = if (state.prefs.gridColumns > 0) state.prefs.gridColumns else 4
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        groups.forEach { (catId, apps) ->
            stickyHeader {
                Text(
                    vm.categoryLabel(catId),
                    color = Color(0xFFBFD4FF),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x88000000))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            val rows = apps.chunked(columns)
            items(rows) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    row.forEach { app ->
                        AppTile(
                            app = app,
                            vm = vm,
                            state = state,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (state.selectMode) vm.toggleSelected(app.packageName)
                                else vm.launch(app.packageName)
                            },
                            onLongClick = {
                                if (state.selectMode) vm.toggleSelected(app.packageName)
                                else onMenu(app)
                            },
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
    vm: DrawerViewModel,
    state: com.pediycc.mydrawer.ui.DrawerUiState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val selected = app.packageName in state.selected
    val iconSize = (40 * state.prefs.iconSizePercent / 100f).coerceIn(24f, 72f).dp
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .background(if (selected) Color(0x558FB4FF) else Color.Transparent, MaterialTheme.shapes.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DrawerIcon(
            key = vm.appIconKey(app.packageName),
            fallback = app,
            size = iconSize,
            contentDescription = app.label,
        )
        if (state.prefs.showAppLabels) {
            Spacer(Modifier.height(4.dp))
            Text(
                app.label,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
