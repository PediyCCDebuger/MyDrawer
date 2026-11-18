package com.pediycc.mydrawer.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pediycc.mydrawer.data.AppInfo
import com.pediycc.mydrawer.data.BuiltInIcons
import com.pediycc.mydrawer.data.Categories
import com.pediycc.mydrawer.data.SortMode
import com.pediycc.mydrawer.data.Updater
import com.pediycc.mydrawer.data.UsageStatsHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerScreen(vm: DrawerViewModel) {
    val state by vm.ui.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    /** 启动成功就结束抽屉自身，不让它留在最近任务/后台等待。 */
    fun launchAndFinish(packageName: String) {
        if (vm.launch(packageName)) activity?.finish()
    }

    /** 跳系统应用详情页（长按菜单里的「属性」）。 */
    fun openAppDetails(packageName: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:$packageName"))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var menuCategory by remember { mutableStateOf<String?>(null) }
    var moveCategoryOpen by remember { mutableStateOf(false) }
    var renameAppTarget by remember { mutableStateOf<AppInfo?>(null) }
    var renameCategoryTarget by remember { mutableStateOf<String?>(null) }
    var iconTarget by remember { mutableStateOf<IconTarget?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    var newCategoryDialog by remember { mutableStateOf(false) }
    var showUsagePrompt by remember { mutableStateOf(false) }
    var categoryReorderOpen by remember { mutableStateOf(false) }
    var appReorderTarget by remember { mutableStateOf<String?>(null) }
    var nestCategoryTarget by remember { mutableStateOf<String?>(null) }
    var childReorderTarget by remember { mutableStateOf<String?>(null) }

    // 第一次进入且未授予「使用情况访问权限」时提示一次；用户选「暂不」后不再打扰。
    LaunchedEffect(Unit) {
        if (!state.prefs.usagePromptShown && !UsageStatsHelper.hasPermission(context)) {
            showUsagePrompt = true
        }
    }

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

    // 2.3.0：固定纯黑不透明背景，不再透壁纸（透壁纸会导致滚动时整片重影）。
    Box(Modifier.fillMaxSize().background(DrawerColors.AppBackground)) {
        // 顶栏标题后的数字：当前分类里实际显示的应用数量（跟随隐藏 / 搜索 / 分类切换变化）。
        val visibleCount = remember(
            state.apps, state.query, state.selectedCategory, state.prefs, state.usageCounts
        ) {
            vm.visibleGroups().sumOf { it.apps.size }
        }
        Scaffold(
            containerColor = DrawerColors.AppBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Text(
                            if (state.selectMode) "已选择 ${state.selected.size} 项"
                            else "${vm.categoryLabel(state.selectedCategory)}（$visibleCount）",
                            color = Color.White,
                            style = WallpaperTextShadowStyle,
                        )
                    },
                    navigationIcon = {
                        if (state.selectMode) {
                            IconButton(onClick = { vm.setSelectMode(false) }) {
                                Icon(Icons.Filled.Close, "退出多选", tint = Color.White)
                            }
                        } else if (state.categoryTrail.isNotEmpty()) {
                            // 从嵌套子分类返回上级分类，与返回键等价。
                            IconButton(onClick = { vm.backToParentCategory() }) {
                                Icon(Icons.Filled.ArrowBack, "返回上级分类", tint = Color.White)
                            }
                        }
                    },
                    actions = {
                        if (state.selectMode) {
                            IconButton(onClick = { vm.selectAllVisible() }) {
                                Icon(Icons.Filled.SelectAll, "全选", tint = Color.White)
                            }
                            IconButton(onClick = { vm.clearCategoryForSelected() }) {
                                Icon(Icons.Filled.RemoveCircle, "取消分类（移入未分类）", tint = Color.White)
                            }
                            IconButton(onClick = { vm.hideSelected() }) {
                                Icon(Icons.Filled.VisibilityOff, "批量隐藏", tint = Color.White)
                            }
                            IconButton(onClick = { moveCategoryOpen = true }) {
                                Icon(Icons.Filled.DriveFileMove, "移动到分类", tint = Color.White)
                            }
                        } else {
                            IconButton(onClick = {
                                searchOpen = !searchOpen
                                if (!searchOpen) vm.setQuery("")
                            }) {
                                Icon(Icons.Filled.Search, "搜索", tint = Color.White)
                            }
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
                val categories = remember(state.prefs, state.apps) {
                    // 2.7.0：只显示顶层分类；嵌套子分类不占分类栏，改在父分类内容里作为入口。
                    Categories.topLevel(state.prefs).filter { cat ->
                        // 「未分类」没有应用时自动隐藏，出现未分类应用时自动显示。
                        cat.id != Categories.OTHER || vm.otherHasApps(state.prefs, state.apps)
                    }
                }
                val rail: @Composable () -> Unit = {
                    CategoryRail(
                        categories = categories,
                        selected = state.selectedCategory,
                        prefs = state.prefs,
                        vm = vm,
                        onSelect = { vm.selectCategory(it) },
                        onLongClick = { menuCategory = it },
                        onNew = { newCategoryDialog = true },
                    )
                }
                if (state.prefs.leftHanded) {
                    Box(Modifier.weight(1f)) {
                        AppArea(vm, state, searchOpen = searchOpen, onSearchToggle = { searchOpen = !searchOpen }, onMenu = { menuApp = it }, onChildMenu = { menuCategory = it }, onLaunch = { pkg -> launchAndFinish(pkg) })
                    }
                    rail()
                } else {
                    rail()
                    Box(Modifier.weight(1f)) {
                        AppArea(vm, state, searchOpen = searchOpen, onSearchToggle = { searchOpen = !searchOpen }, onMenu = { menuApp = it }, onChildMenu = { menuCategory = it }, onLaunch = { pkg -> launchAndFinish(pkg) })
                    }
                }
            }
        }

        if (state.selectMode) {
            // 外层让开系统导航栏，内层才是提示条本身；否则提示条会被导航栏盖住/裁掉。
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Surface(
                    color = Color(0xE6202328),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        "点击应用选择 · 右上角箭头移动 / 减号取消分类 / 眼睛隐藏",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
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
            onLaunch = { launchAndFinish(app.packageName); menuApp = null },
            onDetails = { openAppDetails(app.packageName); menuApp = null },
            onPinToHome = { vm.pinToHome(app.packageName); menuApp = null },
            onToggleHidden = { vm.toggleHidden(app.packageName); menuApp = null },
            onSetCategory = { vm.setCategoryOverride(app.packageName, it); menuApp = null },
            onClearCategory = { vm.clearCategory(app.packageName); menuApp = null },
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
            onNew = { newCategoryDialog = true; menuCategory = null },
            onDelete = { vm.deleteCategory(id); menuCategory = null },
            onReorderSort = { categoryReorderOpen = true; menuCategory = null },
            onReorderApps = { appReorderTarget = id; menuCategory = null },
            onNest = { nestCategoryTarget = id; menuCategory = null },
            onReorderChildren = { childReorderTarget = id; menuCategory = null },
        )
    }

    if (categoryReorderOpen) {
        CategoryReorderDialog(
            vm = vm,
            prefs = state.prefs,
            onDismiss = { categoryReorderOpen = false },
        )
    }

    appReorderTarget?.let { id ->
        AppReorderDialog(
            categoryId = id,
            vm = vm,
            prefs = state.prefs,
            onDismiss = { appReorderTarget = null },
        )
    }

    nestCategoryTarget?.let { id ->
        NestCategoryDialog(
            childId = id,
            vm = vm,
            prefs = state.prefs,
            onDismiss = { nestCategoryTarget = null },
        )
    }

    childReorderTarget?.let { id ->
        NestedChildReorderDialog(
            parentId = id,
            vm = vm,
            prefs = state.prefs,
            onDismiss = { childReorderTarget = null },
        )
    }

    if (newCategoryDialog) {
        TextInputDialog(
            title = "新建分类",
            initial = "",
            onDismiss = { newCategoryDialog = false },
            onConfirm = { vm.createCategory(it); newCategoryDialog = false },
        )
    }

    if (showUsagePrompt) {
        ThemedDialog(onDismissRequest = {
            vm.markUsagePromptShown()
            showUsagePrompt = false
        }) {
            Text("开启使用频率权限", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "「最常用」分类需要读取本机应用使用情况，才能按真实使用频率排列。当前尚未获得该权限，授权后可获得更准确的常用应用；不授权也能正常使用抽屉。",
                color = DrawerColors.DialogText,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    vm.markUsagePromptShown()
                    showUsagePrompt = false
                }) { Text("暂不", color = DrawerColors.DialogText) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    vm.markUsagePromptShown()
                    UsageStatsHelper.openPermissionSettings(context)
                    showUsagePrompt = false
                }) { Text("去授权", color = DrawerColors.Accent) }
            }
        }
    }

    if (moveCategoryOpen) {
        MoveDialog(
            categories = Categories.orderedFor(state.prefs),
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

    // 弹窗/底部菜单自己处理返回键，这里不要抢；只有设置页和多选需要自己收尾。
    val overlayOpen = menuApp != null || menuCategory != null || moveCategoryOpen ||
        renameAppTarget != null || renameCategoryTarget != null || iconTarget != null || showUpdate ||
        newCategoryDialog || showUsagePrompt || categoryReorderOpen || appReorderTarget != null ||
        nestCategoryTarget != null || childReorderTarget != null
    // 设置页 / 多选 / 嵌套子分类都不是独立 Activity：返回键先退出当前这一层，而不是直接退出应用。
    // 放在最后注册，保证优先于主界面的默认返回行为。
    BackHandler(
        enabled = !overlayOpen && (showSettings || state.selectMode || state.categoryTrail.isNotEmpty())
    ) {
        when {
            showSettings -> showSettings = false
            state.selectMode -> vm.setSelectMode(false)
            // 在嵌套子分类里：退回上一级分类。
            else -> vm.backToParentCategory()
        }
    }
}

@Composable
private fun AppArea(
    vm: DrawerViewModel,
    state: com.pediycc.mydrawer.ui.DrawerUiState,
    searchOpen: Boolean,
    onSearchToggle: () -> Unit,
    onMenu: (AppInfo) -> Unit,
    onChildMenu: (String) -> Unit,
    onLaunch: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // 原版 searchBox 默认 gone，点顶部搜索按钮才显示；输入框本身无边框、透明背景。
        if (searchOpen) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { vm.setQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                placeholder = { Text("搜索应用", color = Color(0xE6FFFFFF), style = WallpaperTextShadowStyle) },
                singleLine = true,
                textStyle = WallpaperTextShadowStyle.copy(fontSize = 15.sp),
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Color.White) },
                trailingIcon = {
                    IconButton(onClick = { onSearchToggle(); vm.setQuery("") }) {
                        Icon(Icons.Filled.Close, "关闭搜索", tint = Color.White)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color(0x22000000),
                    unfocusedContainerColor = Color(0x22000000),
                    cursorColor = Color.White,
                ),
            )
        }
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            val groups = remember(
                state.apps, state.query, state.selectedCategory, state.prefs, state.usageCounts
            ) {
                vm.visibleGroups()
            }
            AppGrid(
                vm = vm,
                state = state,
                groups = groups,
                onMenu = onMenu,
                onLaunch = onLaunch,
                onChildCategory = { vm.openChildCategory(it) },
                onChildMenu = { onChildMenu(it) },
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
    onNew: () -> Unit,
) {
    // 分类图标尺寸独立可调，默认比应用图标大一档。
    val iconSize = (30 * prefs.categoryIconSizePercent / 100f).coerceIn(20f, 54f).dp
    val tileSize = (44 * prefs.categoryIconSizePercent / 100f).coerceIn(34f, 58f).dp
    // 分类栏只比纯黑略浅一档：既能和内容区区分，又不会像白底那样压住白色分类文字。
    Column(
        Modifier
            .fillMaxHeight()
            .width(if (prefs.showCategoryLabels) 76.dp else 64.dp)
            .background(DrawerColors.RailBackground)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(6.dp))
        // 「全部」入口，可在设置里关闭
        if (prefs.allEnabled) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onSelect("all") }, onLongClick = {}),
            ) {
                Box(
                    Modifier
                        .size(tileSize)
                        .background(
                            if (selected == "all") DrawerColors.RailSelected else Color.Transparent,
                            MaterialTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Apps,
                        "全部",
                        tint = DrawerColors.AllIcon,
                        modifier = Modifier.size(iconSize),
                    )
                }
                if (prefs.showCategoryLabels) {
                    Text(
                        "全部",
                        color = if (selected == "all") Color.White else Color(0xFFE0E0E0),
                        fontSize = 11.sp,
                        maxLines = 1,
                        style = WallpaperTextShadowStyle,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
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
                        .size(tileSize)
                        .background(
                            if (catSelected) DrawerColors.RailSelected else Color.Transparent,
                            MaterialTheme.shapes.small,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    // 内置图标按图库配色显示为彩色；自定义图片 / 指定应用图标原样显示。
                    DrawerIcon(
                        key = vm.categoryIconKey(cat.id),
                        fallbackVector = BuiltInIcons.vectorOf(cat.iconKey),
                        size = iconSize,
                        contentDescription = cat.label,
                        colorTint = BuiltInIcons.colorOf(vm.categoryIconKey(cat.id)),
                    )
                }
                if (prefs.showCategoryLabels) {
                    Text(
                        vm.categoryLabel(cat.id),
                        color = if (catSelected) Color.White else Color(0xFFE0E0E0),
                        fontSize = 11.sp,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = WallpaperTextShadowStyle,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        // 新建分类入口：常驻分类栏底部，长按任一分类也有「新建分类 / 删除分类」。
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onNew, onLongClick = onNew),
        ) {
            Box(
                Modifier
                    .size(tileSize)
                    .background(Color.Transparent, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    "新建分类",
                    tint = DrawerColors.AllIcon,
                    modifier = Modifier.size(iconSize),
                )
            }
            if (prefs.showCategoryLabels) {
                Text(
                    "新建",
                    color = Color(0xFFE0E0E0),
                    fontSize = 11.sp,
                    maxLines = 1,
                    style = WallpaperTextShadowStyle,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGrid(
    vm: DrawerViewModel,
    state: com.pediycc.mydrawer.ui.DrawerUiState,
    groups: List<com.pediycc.mydrawer.data.Sorter.NestedGroup>,
    onMenu: (AppInfo) -> Unit,
    onLaunch: (String) -> Unit,
    onChildCategory: (String) -> Unit,
    onChildMenu: (String) -> Unit,
) {
    // 横屏 / 竖屏分别使用各自的列数设置。
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val configured = if (landscape) state.prefs.landscapeColumns else state.prefs.gridColumns
    val columns = if (configured > 0) configured else if (landscape) 6 else 4
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        groups.forEach { group ->
            if (group.isChildEntry) {
                // 嵌套子分类：画成一个可点进、可长按管理的分类入口条。
                item(key = "child:${group.categoryId}") {
                    ChildCategoryTile(
                        label = vm.categoryLabel(group.categoryId),
                        vm = vm,
                        categoryId = group.categoryId,
                        onClick = { onChildCategory(group.categoryId) },
                        onLongClick = { onChildMenu(group.categoryId) },
                    )
                }
            } else {
                stickyHeader(key = "header:${group.categoryId}") {
                    Text(
                        vm.categoryLabel(group.categoryId),
                        color = Color.White,
                        fontSize = 14.sp,
                        style = WallpaperTextShadowStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DrawerColors.GroupHeader)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                val rows = group.apps.chunked(columns)
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
                                    else onLaunch(app.packageName)
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
}

/** 嵌套子分类入口条：带分类图标 + 名称，点进入该分类，长按弹出分类管理菜单。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChildCategoryTile(
    label: String,
    vm: DrawerViewModel,
    categoryId: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(DrawerColors.GroupHeader, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DrawerIcon(
            key = vm.categoryIconKey(categoryId),
            fallbackVector = BuiltInIcons.vectorOf(vm.categoryIconKey(categoryId)),
            size = 30.dp,
            contentDescription = null,
            colorTint = BuiltInIcons.colorOf(vm.categoryIconKey(categoryId)),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 14.sp, style = WallpaperTextShadowStyle)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowRight, "进入子分类", tint = DrawerColors.DialogSecondary)
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
            .background(if (selected) Color(0x998FB4FF) else Color.Transparent, MaterialTheme.shapes.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 「图标规范化」开启时给第三方图标留内边距，避免异形图标顶满格子。
        Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
            DrawerIcon(
                key = vm.appIconKey(app.packageName),
                fallback = app,
                size = if (state.prefs.normalizeIcons) (iconSize.value * 0.88f).dp else iconSize,
                contentDescription = app.label,
            )
        }
        if (state.prefs.showAppLabels) {
            Spacer(Modifier.height(4.dp))
            Text(
                app.label,
                color = Color.White,
                fontSize = 12.sp,
                style = WallpaperTextShadowStyle,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
