package com.pediycc.mydrawer.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import com.pediycc.mydrawer.data.DebugLog
import com.pediycc.mydrawer.data.SortMode
import com.pediycc.mydrawer.data.Updater
import com.pediycc.mydrawer.data.UsageStatsHelper
import com.pediycc.mydrawer.data.inCategory

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

    /**
     * 长按菜单「卸载应用」：四条路径逐条尝试（2.9.4 起过程只写调试日志，不再弹提示条）。
     * 成功发出请求 → 静默；全部失败 → 提示最后的异常。详细过程在
     * 「设置 → 调试日志」里查看，便于定位系统侧静默拦截类问题（如 ColorOS 禁卸）。
     */
    fun uninstallApp(app: AppInfo) {
        val pkg = app.packageName
        val uri = android.net.Uri.parse("package:$pkg")
        val activityCtx = context
        val appCtx = context.applicationContext
        data class Attempt(val label: String, val run: () -> Unit)
        val attempts = listOf(
            Attempt("界面上下文 ACTION_DELETE") {
                activityCtx.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_DELETE).setData(uri),
                )
            },
            Attempt("显式系统卸载器 UninstallerActivity") {
                activityCtx.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_DELETE)
                        .setData(uri)
                        .setClassName(
                            "com.android.packageinstaller",
                            "com.android.packageinstaller.UninstallerActivity",
                        ),
                )
            },
            Attempt("应用上下文 ACTION_DELETE") {
                appCtx.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_DELETE)
                        .setData(uri)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
            @Suppress("DEPRECATION")
            Attempt("应用上下文 ACTION_UNINSTALL_PACKAGE") {
                appCtx.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_UNINSTALL_PACKAGE, uri)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
        DebugLog.log(context, "请求卸载：${app.label}（$pkg）")
        var lastError: String? = null
        for (attempt in attempts) {
            val result = runCatching { attempt.run() }
            if (result.isSuccess) {
                DebugLog.log(context, "卸载请求已发出（${attempt.label}）。若无系统确认页，可能是系统对该应用的禁卸保护")
                return
            }
            val e = result.exceptionOrNull()!!
            lastError = e.javaClass.simpleName + (e.message?.let { "($it)" } ?: "")
            DebugLog.log(context, "卸载路径【${attempt.label}】失败：$lastError")
        }
        DebugLog.log(context, "所有卸载路径均失败")
        vm.notifyUser("卸载界面启动失败：$lastError；可长按该应用 →「属性」在系统详情页卸载")
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
    var moveCategoriesOpen by remember { mutableStateOf(false) }
    var confirmDeleteCategories by remember { mutableStateOf(false) }

    // 第一次进入且未授予「使用情况访问权限」时提示一次；用户选「暂不」后不再打扰。
    LaunchedEffect(Unit) {
        if (!state.prefs.usagePromptShown && !UsageStatsHelper.hasPermission(context)) {
            showUsagePrompt = true
        }
    }

    // 2.9.7：回到前台时自动检查更新；开关关闭或距上次检查不足 24 小时，VM 会直接跳过。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.checkAutoUpdate()
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

    // 2.9.14：提示统一走系统 Toast（见 DrawerViewModel.notify），不再用主界面 Snackbar——
    // 后者会被设置页 / 对话框 / 长按菜单等覆盖层挡住，必须关掉覆盖层才看得到。

    // 2.3.0：固定纯黑不透明背景，不再透壁纸（透壁纸会导致滚动时整片重影）。
    Box(Modifier.fillMaxSize().background(DrawerColors.AppBackground)) {
        Scaffold(
            containerColor = DrawerColors.AppBackground,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        // 2.7.2：分类名与数量跟随右侧应用列表上方的分组标题显示，
                        // 顶栏不再重复一遍；只有多选时才在这里显示选中数量。
                        when {
                            state.selectMode -> Text(
                                "已选择 ${state.selected.size} 项",
                                color = Color.White,
                                style = WallpaperTextShadowStyle,
                            )
                            state.categorySelectMode -> Text(
                                "已选择 ${state.selectedCategories.size} 个分类",
                                color = Color.White,
                                style = WallpaperTextShadowStyle,
                            )
                        }
                    },
                    navigationIcon = {
                        when {
                            state.selectMode -> IconButton(onClick = { vm.setSelectMode(false) }) {
                                Icon(Icons.Filled.Close, "退出多选", tint = Color.White)
                            }
                            state.categorySelectMode -> IconButton(onClick = { vm.exitCategorySelect() }) {
                                Icon(Icons.Filled.Close, "退出分类多选", tint = Color.White)
                            }
                            state.categoryTrail.isNotEmpty() -> {
                                // 从嵌套子分类返回上级分类，与返回键等价。
                                IconButton(onClick = { vm.backToParentCategory() }) {
                                    Icon(Icons.Filled.ArrowBack, "返回上级分类", tint = Color.White)
                                }
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
                        } else if (state.categorySelectMode) {
                            // 分类多选：批量删除 / 批量移动（嵌套到某个分类或移回顶层）。
                            IconButton(onClick = { confirmDeleteCategories = true }) {
                                Icon(Icons.Filled.Delete, "删除选中分类", tint = Color.White)
                            }
                            IconButton(onClick = { moveCategoriesOpen = true }) {
                                Icon(Icons.Filled.DriveFileMove, "移动选中分类", tint = Color.White)
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
                        selectMode = state.categorySelectMode,
                        selectedCategories = state.selectedCategories,
                        hintVisible = state.selectMode || state.categorySelectMode,
                        onSelect = { vm.selectCategory(it) },
                        onToggle = { vm.toggleCategorySelected(it) },
                        onLongClick = { menuCategory = it },
                        onNew = { newCategoryDialog = true },
                    )
                }
                // 分类栏与应用区之间的细分隔线，进一步明确两区边界。
                val railDivider: @Composable () -> Unit = {
                    Spacer(
                        Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(DrawerColors.RailDivider),
                    )
                }
                if (state.prefs.leftHanded) {
                    Box(Modifier.weight(1f)) {
                        AppArea(vm, state, searchOpen = searchOpen, onSearchToggle = { searchOpen = !searchOpen }, onMenu = { menuApp = it }, onChildMenu = { menuCategory = it }, onLaunch = { pkg -> launchAndFinish(pkg) })
                    }
                    railDivider()
                    rail()
                } else {
                    rail()
                    railDivider()
                    Box(Modifier.weight(1f)) {
                        AppArea(vm, state, searchOpen = searchOpen, onSearchToggle = { searchOpen = !searchOpen }, onMenu = { menuApp = it }, onChildMenu = { menuCategory = it }, onLaunch = { pkg -> launchAndFinish(pkg) })
                    }
                }
            }
        }

        if (state.selectMode || state.categorySelectMode) {
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
                        if (state.categorySelectMode) {
                            "点击分类选择 · 右上角移动 / 删除"
                        } else {
                            "点击应用选择 · 右上角移动 / 取消分类 / 隐藏"
                        },
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
    } else {
        // 自动检查发现新版本：沿用同一个对话框，直接显示已经取到的版本信息，不再请求网络。
        state.autoUpdateRelease?.let { release ->
            UpdateDialog(
                vm = vm,
                prechecked = release,
                onDismiss = { vm.dismissAutoUpdate() },
            )
        }
    }

    menuApp?.let { captured ->
        // 勾选分类后 refresh 会更新应用列表，用最新数据重建条目，勾选状态才不会停留在旧值。
        val app = state.apps.firstOrNull { it.packageName == captured.packageName } ?: captured
        AppMenuSheet(
            app = app,
            prefs = state.prefs,
            onDismiss = { menuApp = null },
            onLaunch = { launchAndFinish(app.packageName); menuApp = null },
            onDetails = { openAppDetails(app.packageName); menuApp = null },
            onUninstall = { uninstallApp(app); menuApp = null },
            onPinToHome = { vm.pinToHome(app.packageName); menuApp = null },
            onToggleHidden = { vm.toggleHidden(app.packageName); menuApp = null },
            onToggleCategory = { catId -> vm.toggleAppCategory(app.packageName, catId) },
            onClearCategory = { vm.clearCategory(app.packageName); menuApp = null },
            onRename = { renameAppTarget = app; menuApp = null },
            onIcon = { iconTarget = IconTarget.App(app.packageName); menuApp = null },
            onMultiSelect = { vm.startSelectFor(app.packageName); menuApp = null },
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
            onMultiSelect = { vm.startCategorySelect(id); menuCategory = null },
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
        // 新建时直接选好内置图标，避免建完再单独改。
        NewCategoryDialog(
            onDismiss = { newCategoryDialog = false },
            onConfirm = { label, iconKey ->
                vm.createCategory(label, iconKey)
                newCategoryDialog = false
            },
        )
    }

    if (confirmDeleteCategories) {
        ThemedDialog(onDismissRequest = { confirmDeleteCategories = false }) {
            Text("删除分类", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "将删除选中的 ${state.selectedCategories.size} 个分类（「未分类」「最常用」会保留），" +
                    "其中的应用会移入「未分类」。此操作不可撤销，但可通过导入配置备份恢复。",
                color = DrawerColors.DialogText,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmDeleteCategories = false }) {
                    Text("取消", color = DrawerColors.DialogText)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    vm.deleteSelectedCategories()
                    confirmDeleteCategories = false
                }) { Text("删除", color = Color(0xFFEF9A9A)) }
            }
        }
    }

    if (moveCategoriesOpen) {
        MoveCategoriesDialog(
            selectedIds = state.selectedCategories,
            vm = vm,
            prefs = state.prefs,
            onDismiss = { moveCategoriesOpen = false },
            onPick = { parentId ->
                vm.moveSelectedCategories(parentId)
                moveCategoriesOpen = false
            },
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
            categories = Categories.moveTargets(state.prefs),
            selectedPackages = state.selected,
            memberOf = { pkg, catId ->
                state.apps.firstOrNull { it.packageName == pkg }?.inCategory(catId, state.prefs.categoryExtras) == true
            },
            labelOf = { vm.categoryLabel(it) },
            onDismiss = { moveCategoryOpen = false },
            onApply = { vm.applyCategoriesToSelected(it); moveCategoryOpen = false },
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
        nestCategoryTarget != null || childReorderTarget != null || moveCategoriesOpen ||
        confirmDeleteCategories
    // 设置页 / 多选 / 嵌套子分类都不是独立 Activity：返回键先退出当前这一层，而不是直接退出应用。
    // 放在最后注册，保证优先于主界面的默认返回行为。
    BackHandler(
        enabled = !overlayOpen && (
            showSettings || state.selectMode || state.categorySelectMode ||
                state.categoryTrail.isNotEmpty()
            )
    ) {
        when {
            showSettings -> showSettings = false
            state.selectMode -> vm.setSelectMode(false)
            state.categorySelectMode -> vm.exitCategorySelect()
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
    selectMode: Boolean,
    selectedCategories: Set<String>,
    hintVisible: Boolean,
    onSelect: (String) -> Unit,
    onToggle: (String) -> Unit,
    onLongClick: (String) -> Unit,
    onNew: () -> Unit,
) {
    // 分类图标尺寸独立可调，范围 80%–240%；上限放宽到能容纳 240%，不再截断。
    val iconSize = (30 * prefs.categoryIconSizePercent / 100f).coerceIn(20f, 80f).dp
    val tileSize = (44 * prefs.categoryIconSizePercent / 100f).coerceIn(34f, 110f).dp
    // 栏宽随图标放大自适应，避免 240% 时大图标被裁掉；带文字时至少留出文字所需宽度。
    val railWidth = maxOf(if (prefs.showCategoryLabels) 76f else 64f, tileSize.value + 6f).dp
    // 略带冷灰蓝的深底，与应用区纯黑拉开，分类里应用多时也能一眼分出边界。
    Column(
        Modifier
            .fillMaxHeight()
            .width(railWidth)
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
                    // 分类多选时「全部」不是可选分类，点击不再切换视图。
                    .combinedClickable(onClick = { if (!selectMode) onSelect("all") }, onLongClick = {}),
            ) {
                Box(
                    Modifier
                        .size(tileSize)
                        .background(
                            if (!selectMode && selected == "all") DrawerColors.RailSelected else Color.Transparent,
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
                        color = if (!selectMode && selected == "all") Color.White else Color(0xFFE0E0E0),
                        fontSize = 11.sp,
                        maxLines = 1,
                        style = WallpaperTextShadowStyle,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        categories.forEach { cat ->
            // 分类多选时用 selectedCategories 决定高亮，点击改为勾选 / 取消勾选。
            val catSelected = if (selectMode) cat.id in selectedCategories else selected == cat.id
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { if (selectMode) onToggle(cat.id) else onSelect(cat.id) },
                        onLongClick = { if (!selectMode) onLongClick(cat.id) },
                    ),
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
                    if (selectMode) {
                        // 右上角勾选角标，明确当前是否已选中。
                        Icon(
                            if (catSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                            if (catSelected) "已选中" else "未选中",
                            tint = if (catSelected) DrawerColors.Accent else Color(0x88FFFFFF),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(16.dp),
                        )
                    }
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
        // 分类多选进行中时不响应，避免误触打断批量操作。
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (!selectMode) onNew() },
                    onLongClick = { if (!selectMode) onNew() },
                ),
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
        // 多选提示条会横跨屏幕底部，分类栏底部同样留出空间，避免「新建」被压住点不到。
        Spacer(Modifier.height(if (hintVisible) 108.dp else 6.dp))
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
    // 多选时底部悬浮提示条会盖住列表最后一行，点击命中会被它吃掉；
    // 这里给列表底部留出 108dp（提示条 + 导航栏余量），最后一行可以正常滚出来并点选。
    val bottomPad = if (state.selectMode || state.categorySelectMode) 108.dp else 6.dp
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = bottomPad),
        // 2.9.0「应用从底部排列」之前只存了开关没接到界面：内容不足一屏时把列表整体贴底显示。
        verticalArrangement = if (state.prefs.stackFromBottom) Arrangement.Bottom else Arrangement.Top,
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
                        // 2.7.2：数量显示在应用列表上方的分类标题后面（每个分组各自的可见数量）。
                        "${vm.categoryLabel(group.categoryId)}（${group.apps.size}）",
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
                                    when {
                                        // 分类多选时应用区只作展示，避免误启动应用。
                                        state.categorySelectMode -> Unit
                                        state.selectMode -> vm.toggleSelected(app.packageName)
                                        else -> onLaunch(app.packageName)
                                    }
                                },
                                onLongClick = {
                                    when {
                                        state.categorySelectMode -> Unit
                                        state.selectMode -> vm.toggleSelected(app.packageName)
                                        else -> onMenu(app)
                                    }
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
    val iconSize = (40 * state.prefs.iconSizePercent / 100f).coerceIn(24f, 88f).dp
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
            // 2.9.0：名称超过 8 个字也能完整显示 —— 用自适应字号在两行内自动缩小，
            // 而不是固定 12sp 每行只装 4 个字就截断。
            BasicText(
                app.label,
                style = WallpaperTextShadowStyle.copy(
                    fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color.White,
                    textAlign = TextAlign.Center,
                ),
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = 12.sp,
                    stepSize = 0.5.sp,
                ),
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
