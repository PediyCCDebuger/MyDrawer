package com.pediycc.mydrawer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.pediycc.mydrawer.BuildConfig
import com.pediycc.mydrawer.data.Categories
import com.pediycc.mydrawer.data.DebugLog
import com.pediycc.mydrawer.data.NestedChildMode
import com.pediycc.mydrawer.data.SortMode
import com.pediycc.mydrawer.data.Updater
import com.pediycc.mydrawer.data.UsageStatsHelper
import com.pediycc.mydrawer.data.UserPrefs

@Composable
private fun drawerSwitchColors() = SwitchDefaults.colors(
    checkedTrackColor = DrawerColors.Accent,
    uncheckedTrackColor = Color(0x66FFFFFF),
    checkedThumbColor = Color.White,
    uncheckedThumbColor = Color(0xEEEEEE),
)

@Composable
private fun drawerCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = DrawerColors.Accent,
    uncheckedColor = Color.White,
    checkmarkColor = Color.Black,
)

@Composable
private fun drawerSliderColors() = SliderDefaults.colors(
    thumbColor = DrawerColors.Accent,
    activeTrackColor = DrawerColors.Accent,
    inactiveTrackColor = Color(0x66FFFFFF),
)

/**
 * 设置页：2.3.0 起固定纯黑不透明底（不再透出抽屉内容，避免看不清），
 * 列表行比背景略浅一点做区分，文字统一白字，视觉与原版深色设置页一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: DrawerViewModel,
    prefs: UserPrefs,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsState()
    var hiddenManage by remember { mutableStateOf(false) }
    var columnsDialog by remember { mutableStateOf<Boolean?>(null) } // true=竖屏 false=横屏
    var passwordDialog by remember { mutableStateOf(false) }
    var frequentLimitDialog by remember { mutableStateOf(false) }
    var unlockHiddenDialog by remember { mutableStateOf(false) }
    var iconManageDialog by remember { mutableStateOf(false) }
    var unlistedDialog by remember { mutableStateOf(false) }
    var debugEnabled by remember { mutableStateOf(DebugLog.isDebug(context)) }
    var debugLogDialog by remember { mutableStateOf(false) }
    val usageGranted = UsageStatsHelper.hasPermission(context)

    Scaffold(
        containerColor = DrawerColors.AppBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DrawerColors.AppBackground,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                title = { Text("设置") },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DrawerColors.AppBackground),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // ---- 应用网格（原 pref_drawer / App grid） ----
            item { SectionTitle("应用网格") }
            item {
                SettingsRow(
                    title = "自定义图标",
                    subtitle = "管理已修改的应用与分类图标",
                    icon = Icons.Filled.Apps,
                    onClick = { iconManageDialog = true },
                )
            }
            item {
                PercentRow(
                    title = "应用图标大小",
                    subtitle = "调整抽屉里应用图标的显示大小（80%–200%）",
                    value = prefs.iconSizePercent,
                    range = 80..200,
                    onValue = { v -> vm.updatePrefs { it.copy(iconSizePercent = v) } },
                )
            }
            item {
                PercentRow(
                    title = "分类图标大小",
                    subtitle = "调整左侧分类栏图标的显示大小（80%–240%）",
                    value = prefs.categoryIconSizePercent,
                    range = 80..240,
                    onValue = { v -> vm.updatePrefs { it.copy(categoryIconSizePercent = v) } },
                )
            }
            item {
                SwitchRow(
                    title = "图标规范化",
                    subtitle = "给应用图标留边，避免异形图标撑满格子",
                    icon = Icons.Filled.AutoAwesome,
                    checked = prefs.normalizeIcons,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(normalizeIcons = v) } },
                )
            }
            item {
                SettingsRow(
                    title = "竖屏列数",
                    subtitle = "当前 ${prefs.gridColumns} 列，竖屏时生效",
                    icon = Icons.Filled.ViewColumn,
                    onClick = { columnsDialog = true },
                )
            }
            item {
                SettingsRow(
                    title = "横屏列数",
                    subtitle = "当前 ${prefs.landscapeColumns} 列，横屏时生效",
                    icon = Icons.Filled.GridView,
                    onClick = { columnsDialog = false },
                )
            }
            item {
                SwitchRow(
                    title = "显示图标名称",
                    subtitle = "在应用图标旁显示名称",
                    icon = Icons.Filled.Label,
                    checked = prefs.showAppLabels,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(showAppLabels = v) } },
                )
            }
            item {
                ListSettingsRow(
                    title = "应用排序方式",
                    subtitle = SortMode.labelOf(prefs.sortMode),
                    icon = Icons.Filled.Sort,
                    options = SortMode.LABELS,
                    selected = prefs.sortMode,
                    onSelect = { vm.setSortMode(it) },
                )
            }

            // ---- 安全 ----
            item { SectionTitle("安全") }
            item {
                SettingsRow(
                    title = "管理隐藏应用",
                    subtitle = "已隐藏 ${prefs.hiddenPackages.size} 个",
                    icon = Icons.Filled.VisibilityOff,
                    onClick = {
                        // 设了密码就必须先验证；没设则直接进入。
                        if (vm.hasPassword()) unlockHiddenDialog = true else hiddenManage = true
                    },
                )
            }
            item {
                SettingsRow(
                    title = "显示系统应用",
                    subtitle = if (prefs.showSystemApps) {
                        "已显示系统抽屉里看不到的程序（系统跟踪、开关控制等无桌面入口的）"
                    } else {
                        "默认与系统抽屉一致：只列出有桌面图标的程序；打开后才显示无入口的系统程序"
                    },
                    icon = Icons.Filled.Android,
                    onClick = { vm.setShowSystemApps(!prefs.showSystemApps) },
                    trailing = {
                        Checkbox(
                            checked = prefs.showSystemApps,
                            onCheckedChange = { vm.setShowSystemApps(it) },
                            colors = drawerCheckboxColors(),
                        )
                    },
                )
            }
            item {
                // 载入数量核对：本机安装总数 vs 抽屉实际载入数，差额点开可看到具体是哪几个。
                val loadedCount = ui.apps.size
                val totalCount = ui.totalInstalled
                val unlisted = ui.unlistedApps
                SettingsRow(
                    title = "应用载入统计",
                    subtitle = when {
                        totalCount <= 0 -> "正在读取本机应用列表…"
                        unlisted.isEmpty() -> "已载入 $loadedCount 个，本机安装 $totalCount 个（全部已载入）"
                        else -> "已载入 $loadedCount 个 / 本机安装 $totalCount 个；" +
                            "${unlisted.size} 个没有桌面入口未列出，点此查看"
                    },
                    icon = Icons.Filled.Apps,
                    onClick = { if (unlisted.isNotEmpty()) unlistedDialog = true },
                    trailing = if (unlisted.isNotEmpty()) {
                        {
                            Icon(
                                Icons.Filled.ChevronRight,
                                null,
                                tint = DrawerColors.DialogSecondary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            item {
                val hasPwd = prefs.hiddenAppsPasswordHash.isNotBlank()
                SettingsRow(
                    title = "使用密码（管理隐藏应用）",
                    subtitle = if (hasPwd) {
                        "进入「管理隐藏应用」时需输入密码，点此修改或清除"
                    } else {
                        "未设置：进入「管理隐藏应用」无需验证，点此设置"
                    },
                    icon = Icons.Filled.Lock,
                    onClick = { passwordDialog = true },
                    trailing = { Text(if (hasPwd) "已设置" else "未设置", color = DrawerColors.DialogSecondary) },
                )
            }

            // ---- 程序列表 ----
            item { SectionTitle("程序列表") }
            item {
                // 「上次使用的分类」用空字符串表示，其余为固定入口 id。
                val startupOptions = buildList {
                    add("" to "上次使用的分类")
                    if (prefs.allEnabled) add("all" to "全部")
                    Categories.orderedFor(prefs)
                        .filter { it.id != Categories.OTHER || vm.otherHasApps(prefs) }
                        .forEach { add(it.id to (prefs.categoryLabels[it.id] ?: it.label)) }
                }
                ListSettingsRow(
                    title = "启动时进入",
                    subtitle = when {
                        prefs.startupCategory.isBlank() -> "上次使用的分类（${vm.categoryLabel(prefs.lastCategory)}）"
                        else -> vm.categoryLabel(prefs.startupCategory)
                    },
                    icon = Icons.Filled.Login,
                    options = startupOptions,
                    selected = prefs.startupCategory,
                    onSelect = { vm.setStartupCategory(it) },
                )
            }
            item {
                ListSettingsRow(
                    title = "子分类位置",
                    subtitle = NestedChildMode.labelOf(prefs.nestedChildMode),
                    icon = Icons.Filled.Folder,
                    options = NestedChildMode.LABELS,
                    selected = prefs.nestedChildMode,
                    onSelect = { vm.setNestedChildMode(it) },
                )
            }
            item {
                SwitchRow(
                    title = "左手模式",
                    subtitle = "将分类列表显示于画面右方",
                    icon = Icons.Filled.SwapHoriz,
                    checked = prefs.leftHanded,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(leftHanded = v) } },
                )
            }
            item {
                SwitchRow(
                    title = "分类名称",
                    subtitle = "在分类图标附近显示名称",
                    icon = Icons.Filled.Category,
                    checked = prefs.showCategoryLabels,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(showCategoryLabels = v) } },
                )
            }
            item {
                SwitchRow(
                    title = "显示「全部」分类",
                    subtitle = "在分类栏顶部显示「全部」入口，关闭后只看各分类",
                    icon = Icons.Filled.Apps,
                    checked = prefs.allEnabled,
                    onCheckedChange = { v -> vm.setAllEnabled(v) },
                )
            }
            item {
                SwitchRow(
                    title = "最常用分类",
                    subtitle = "按本机真实使用频率排列最常用的应用（数量可在下一行自定义），默认排在分类栏最前",
                    icon = Icons.Filled.Star,
                    checked = prefs.frequentEnabled,
                    onCheckedChange = { v -> vm.setFrequentEnabled(v) },
                )
            }
            item {
                // 2.9.0：「最常用」显示数量可手动输入；上限按本机有使用记录的应用数确定，
                // 数量再大也只是截断到有记录的应用，故提示里直接给出当前系统下的最大值。
                val counts = if (ui.usageCounts.isNotEmpty()) ui.usageCounts else prefs.launchCounts
                val maxAllowed = counts.size.coerceIn(1, 200)
                FrequentLimitRow(
                    value = prefs.frequentLimit,
                    maxAllowed = maxAllowed,
                    onClick = { frequentLimitDialog = true },
                )
            }
            item {
                SettingsRow(
                    title = "使用频率权限",
                    subtitle = if (usageGranted) "已授权，最常用来自系统使用统计" else "未授权，点击前往系统设置授权",
                    icon = Icons.Filled.QueryStats,
                    onClick = { UsageStatsHelper.openPermissionSettings(context) },
                )
            }
            item {
                SwitchRow(
                    title = "应用从底部排列",
                    subtitle = "从底部开始显示应用",
                    icon = Icons.Filled.VerticalAlignBottom,
                    checked = prefs.stackFromBottom,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(stackFromBottom = v) } },
                )
            }
            item {
                SwitchRow(
                    title = "显示更新按钮",
                    subtitle = "在右上角显示更新按钮",
                    icon = Icons.Filled.Menu,
                    checked = prefs.showMenuButton,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(showMenuButton = v) } },
                )
            }
            item {
                // 2.9.9：从调试区移到常规「程序列表」区，所有用户都能找到。
                // 正常情况下装/卸/更新应用或切换语言会自动重建，这里只作显示异常时的手动兜底。
                SettingsRow(
                    title = "重建应用列表缓存",
                    subtitle = "图标或名称显示异常时点此强制重扫本机应用，缓存随后在后台重建；平时无需使用，应用变化会自动更新",
                    icon = Icons.Filled.Cached,
                    onClick = { vm.rebuildAppCache() },
                )
            }

            // ---- 备份 / 更新 ----
            item { SectionTitle("备份与更新") }
            item {
                // 2.9.7：默认关闭；开启时立即检查一次，之后进入应用时每 24 小时最多一次。
                SwitchRow(
                    title = "自动检查更新",
                    subtitle = "开启后每 24 小时检查一次，只有发现新版本才弹出提示；默认关闭",
                    icon = Icons.Filled.Autorenew,
                    checked = prefs.autoUpdate,
                    onCheckedChange = { v ->
                        vm.updatePrefs { it.copy(autoUpdate = v) }
                        if (v) vm.checkAutoUpdate(force = true)
                    },
                )
            }
            item {
                SettingsRow(
                    title = "导出配置备份",
                    subtitle = "对应右上角软盘图标",
                    icon = Icons.Filled.Save,
                    onClick = onExport,
                )
            }
            item {
                SettingsRow(
                    title = "导入配置备份",
                    subtitle = "对应右上角时钟图标",
                    icon = Icons.Filled.Restore,
                    onClick = onImport,
                )
            }
            item {
                ListSettingsRow(
                    title = "更新下载代理",
                    subtitle = prefs.updateProxy,
                    icon = Icons.Filled.Download,
                    options = Updater.PROXIES.map { it to it },
                    selected = prefs.updateProxy,
                    onSelect = { vm.updatePrefs { p -> p.copy(updateProxy = it) } },
                )
            }
            item { SectionTitle("调试") }
            item {
                // 2.9.4：调试开关独立于 UserPrefs 存 SharedPreferences（不随备份导入导出），
                // 与 Reminder（AddinTimer 复刻版）的「调试」开关同模式。
                SwitchRow(
                    title = "调试日志",
                    subtitle = "开启后记录启动、载入、卸载、更新、备份等关键链路，用于问题定位；不会上传，只保存在本机",
                    icon = Icons.Filled.BugReport,
                    checked = debugEnabled,
                    onCheckedChange = { on ->
                        DebugLog.setDebug(context, on)
                        debugEnabled = on
                        if (on) DebugLog.log(context, "调试日志已开启（versionCode ${BuildConfig.VERSION_CODE}）")
                    },
                )
            }
            if (debugEnabled) {
                item {
                    SettingsRow(
                        title = "查看调试日志",
                        subtitle = "最近 300 条记录；可复制、保存到「下载/MyDrawer」或清空",
                        icon = Icons.Filled.ReceiptLong,
                        onClick = { debugLogDialog = true },
                    )
                }
            }
            item {
                Text(
                    "MyDrawer ${BuildConfig.VERSION_NAME} · 独立重写，无广告、无统计、无内购。",
                    color = Color(0xEEFFFFFF),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (hiddenManage) {
        HiddenAppsDialog(vm = vm, prefs = prefs, onDismiss = { hiddenManage = false })
    }
    columnsDialog?.let { portrait ->
        ColumnDialog(
            portrait = portrait,
            prefs = prefs,
            onDismiss = { columnsDialog = null },
            onPick = { n ->
                if (portrait) vm.setGridColumns(n) else vm.setLandscapeColumns(n)
                columnsDialog = null
            },
        )
    }
    if (iconManageDialog) {
        ThemedDialog(onDismissRequest = { iconManageDialog = false }) {
            Text("自定义图标", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "可长按应用或分类单独更换图标。这里可以一次性清除所有已修改的图标，恢复系统应用图标与内置分类图标。",
                color = DrawerColors.DialogText,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { iconManageDialog = false }) { Text("取消", color = DrawerColors.DialogText) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    vm.resetCustomIcons()
                    iconManageDialog = false
                }) { Text("恢复默认图标", color = DrawerColors.Accent) }
            }
        }
    }
    if (unlistedDialog) {
        ThemedDialog(onDismissRequest = { unlistedDialog = false }) {
            Text("未列出的应用", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "下面这些应用没有桌面启动入口（系统跟踪、开关控制这类），与系统应用抽屉保持一致，默认不列入。" +
                    "需要时打开「显示系统应用」即可显示。",
                color = DrawerColors.DialogText,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                ui.unlistedApps.forEach { app ->
                    ListItem(
                        headlineContent = { Text(app.label, color = Color.White) },
                        supportingContent = {
                            Text(app.packageName, color = DrawerColors.DialogSecondary, fontSize = 12.sp)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { unlistedDialog = false }) {
                    Text("关闭", color = DrawerColors.Accent)
                }
            }
        }
    }
    if (passwordDialog) {
        PasswordSetupDialog(
            vm = vm,
            hasPassword = prefs.hiddenAppsPasswordHash.isNotBlank(),
            onDismiss = { passwordDialog = false },
        )
    }
    if (frequentLimitDialog) {
        // 上限按本机有使用记录的应用数确定，与行内提示一致。
        val counts = if (ui.usageCounts.isNotEmpty()) ui.usageCounts else prefs.launchCounts
        FrequentLimitDialog(
            value = prefs.frequentLimit,
            maxAllowed = counts.size.coerceIn(1, 200),
            onDismiss = { frequentLimitDialog = false },
            onConfirm = { v ->
                vm.updatePrefs { it.copy(frequentLimit = v) }
                frequentLimitDialog = false
            },
        )
    }
    if (debugLogDialog) {
        DebugLogDialog(
            logText = DebugLog.text(context),
            onClear = { DebugLog.clear(context) },
            onDismiss = { debugLogDialog = false },
        )
    }
    if (unlockHiddenDialog) {
        PasswordPromptDialog(
            vm = vm,
            onDismiss = { unlockHiddenDialog = false },
            onUnlocked = { unlockHiddenDialog = false; hiddenManage = true },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        color = DrawerColors.Accent,
        style = WallpaperTextShadowStyle,
        modifier = Modifier
            .fillMaxWidth()
            .background(DrawerColors.SettingsHeader)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().background(DrawerColors.SettingsRow)) {
        ListItem(
            headlineContent = { Text(title, color = Color.White, style = WallpaperTextShadowStyle) },
            supportingContent = {
                Text(
                    subtitle,
                    style = WallpaperTextShadowStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    color = Color(0xFFBDBDBD),
                )
            },
            leadingContent = { Icon(icon, null, tint = Color(0xFF8FB4FF)) },
            trailingContent = trailing,
            modifier = Modifier.clickable(onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    HorizontalDivider(color = DrawerColors.SettingsDivider)
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Box(Modifier.fillMaxWidth().background(DrawerColors.SettingsRow)) {
        ListItem(
            headlineContent = { Text(title, color = Color.White, style = WallpaperTextShadowStyle) },
            supportingContent = {
                Text(
                    subtitle,
                    style = WallpaperTextShadowStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    color = Color(0xFFBDBDBD),
                )
            },
            leadingContent = { Icon(icon, null, tint = Color(0xFF8FB4FF)) },
            trailingContent = {
                Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange, colors = drawerSwitchColors())
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    HorizontalDivider(color = DrawerColors.SettingsDivider)
}

/**
 * 百分比设置行：滑杆无极调整（steps = 0），旁边可输入精确数字。
 * 输入框与滑杆共用同一个 value：输入时只接收范围内的整数，留空/超范围时不改值，
 * 失焦时把文本复位到当前值，避免出现「显示 999 但实际 100%」的不一致。
 */
@Composable
private fun PercentRow(
    title: String,
    subtitle: String,
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Column(Modifier.fillMaxWidth().background(DrawerColors.SettingsRow).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PhotoSizeSelectSmall, null, tint = Color(0xFF8FB4FF))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, style = WallpaperTextShadowStyle)
                Text(
                    subtitle,
                    style = WallpaperTextShadowStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    color = Color(0xFFBDBDBD),
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }
                    text = digits
                    digits.toIntOrNull()?.let { n -> if (n in range) onValue(n.coerceIn(range)) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                suffix = { Text("%", color = DrawerColors.DialogSecondary) },
                modifier = Modifier.width(90.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = DrawerColors.Accent,
                    unfocusedBorderColor = Color(0x88FFFFFF),
                    focusedLabelColor = DrawerColors.Accent,
                    unfocusedLabelColor = DrawerColors.DialogSecondary,
                    cursorColor = Color.White,
                ),
            )
        }
        Slider(
            value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
            onValueChange = { onValue(it.toInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = 0,
            colors = drawerSliderColors(),
        )
    }
    HorizontalDivider(color = DrawerColors.SettingsDivider)
}

/**
 * 「最常用」数量行（2.9.1 改交互）：不再直接内联输入（之前无确认、看不出是否生效），
 * 改为点击弹出输入对话框，「确定」后才写入；副标题实时显示当前值与允许的最大值。
 */
@Composable
private fun FrequentLimitRow(
    value: Int,
    maxAllowed: Int,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(DrawerColors.SettingsRow)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = Color(0xFF8FB4FF))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("最常用数量", color = Color.White, style = WallpaperTextShadowStyle)
                Text(
                    "「最常用」分类显示的应用个数，当前 $value，最多 $maxAllowed（本机有使用记录的应用数）；点击修改",
                    style = WallpaperTextShadowStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    color = Color(0xFFBDBDBD),
                )
            }
            Icon(Icons.Filled.Edit, null, tint = Color(0xFF9E9E9E))
        }
    }
    HorizontalDivider(color = DrawerColors.SettingsDivider)
}

/** 用深色对话框代替 DropdownMenu，避免弹出层套用系统浅色主题造成白底白字。 */
@Composable
private fun <T> ListSettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    SettingsRow(title, subtitle, icon, { show = true })
    if (show) {
        ThemedDialog(onDismissRequest = { show = false }) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value); show = false }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value); show = false })
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { show = false }) { Text("取消", color = DrawerColors.DialogText) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnDialog(
    portrait: Boolean,
    prefs: UserPrefs,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val current = if (portrait) prefs.gridColumns else prefs.landscapeColumns
    val values = if (portrait) (2..8).toList() else (2..10).toList()
    ThemedDialog(onDismissRequest = onDismiss) {
        Text(if (portrait) "竖屏列数" else "横屏列数", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        // 横屏时可用高度很小，必须限制高度并允许滚动，否则下面的选项和取消按钮会被裁掉。
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            values.forEach { n ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(n) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = current == n,
                        onClick = { onPick(n) },
                        colors = RadioButtonDefaults.colors(selectedColor = DrawerColors.Accent, unselectedColor = Color.White),
                    )
                    Spacer(Modifier.width(8.dp)); Text("$n 列", color = Color.White)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消", color = DrawerColors.DialogText) }
        }
    }
}

/** 设置 / 修改 / 清除「管理隐藏应用」密码。已设密码时，改密码需先验证旧密码；清除同理。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordSetupDialog(vm: DrawerViewModel, hasPassword: Boolean, onDismiss: () -> Unit) {
    var oldPwd by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = DrawerColors.Accent,
        unfocusedBorderColor = Color(0x88FFFFFF),
        focusedLabelColor = DrawerColors.Accent,
        unfocusedLabelColor = DrawerColors.DialogSecondary,
        cursorColor = Color.White,
    )

    ThemedDialog(onDismissRequest = onDismiss) {
        Text(if (hasPassword) "修改密码" else "设置密码", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            "设置后，进入「管理隐藏应用」需要输入密码；清除后不再需要。",
            color = DrawerColors.DialogText,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        if (hasPassword) {
            OutlinedTextField(
                value = oldPwd,
                onValueChange = { oldPwd = it; error = null },
                label = { Text("当前密码", color = DrawerColors.DialogSecondary) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                textStyle = TextStyle(color = Color.White),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = newPwd,
            onValueChange = { newPwd = it; error = null },
            label = { Text(if (hasPassword) "新密码" else "密码", color = DrawerColors.DialogSecondary) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            textStyle = TextStyle(color = Color.White),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            label = { Text("确认密码", color = DrawerColors.DialogSecondary) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            textStyle = TextStyle(color = Color.White),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消", color = DrawerColors.DialogText) }
            if (hasPassword) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = {
                    if (!vm.checkPassword(oldPwd)) {
                        error = "当前密码错误，无法清除"
                    } else {
                        vm.clearPassword()
                        onDismiss()
                    }
                }) { Text("清除密码", color = Color(0xFFFF8A80)) }
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = {
                if (hasPassword && !vm.checkPassword(oldPwd)) {
                    error = "当前密码错误"
                } else if (newPwd.isBlank()) {
                    error = "密码不能为空"
                } else if (newPwd != confirm) {
                    error = "两次输入的密码不一致"
                } else {
                    vm.setPassword(newPwd)
                    onDismiss()
                }
            }) { Text("确定", color = DrawerColors.Accent) }
        }
    }
}

/** 进入「管理隐藏应用」前的密码校验框。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordPromptDialog(vm: DrawerViewModel, onDismiss: () -> Unit, onUnlocked: () -> Unit) {
    var pwd by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    ThemedDialog(onDismissRequest = onDismiss) {
        Text("输入密码", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pwd,
            onValueChange = { pwd = it; error = null },
            label = { Text("密码", color = DrawerColors.DialogSecondary) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            textStyle = TextStyle(color = Color.White),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = DrawerColors.Accent,
                unfocusedBorderColor = Color(0x88FFFFFF),
                focusedLabelColor = DrawerColors.Accent,
                unfocusedLabelColor = DrawerColors.DialogSecondary,
                cursorColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消", color = DrawerColors.DialogText) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                if (vm.checkPassword(pwd)) onUnlocked() else error = "密码错误"
            }) { Text("确定", color = DrawerColors.Accent) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenAppsDialog(vm: DrawerViewModel, prefs: UserPrefs, onDismiss: () -> Unit) {
    val hidden = prefs.hiddenPackages.toList()
    // 多选恢复：点行切换勾选，底部一次性恢复；单行右侧仍保留「恢复」快捷入口。
    var selected by remember { mutableStateOf(emptySet<String>()) }
    // 恢复后列表会变短，勾选集合里已不在隐藏列表的项自动失效。
    val picked = selected.intersect(hidden.toSet())
    ThemedDialog(onDismissRequest = onDismiss) {
        Text("管理隐藏应用（${hidden.size}）", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "点击应用可多选，再点「恢复选中」一次性放回抽屉。",
            color = DrawerColors.DialogSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (hidden.isEmpty()) {
                Text("暂无隐藏应用。长按应用图标可隐藏。", color = DrawerColors.DialogText)
            } else {
                hidden.forEach { pkg ->
                    val label = vm.ui.value.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                    val checked = pkg in picked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selected = if (checked) picked - pkg else picked + pkg }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { selected = if (checked) picked - pkg else picked + pkg },
                            colors = drawerCheckboxColors(),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(label, color = Color.White)
                            Text(
                                pkg,
                                style = WallpaperTextShadowStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                color = DrawerColors.DialogSecondary,
                            )
                        }
                        TextButton(onClick = {
                            vm.restoreHidden(listOf(pkg))
                            selected = picked - pkg
                        }) { Text("恢复", color = DrawerColors.Accent) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (hidden.isNotEmpty()) {
                TextButton(onClick = {
                    selected = if (picked.size == hidden.size) emptySet() else hidden.toSet()
                }) {
                    Text(if (picked.size == hidden.size) "取消全选" else "全选", color = DrawerColors.DialogText)
                }
                TextButton(
                    enabled = picked.isNotEmpty(),
                    onClick = {
                        vm.restoreHidden(picked)
                        selected = emptySet()
                    },
                ) {
                    Text(
                        if (picked.isEmpty()) "恢复选中" else "恢复选中（${picked.size}）",
                        color = if (picked.isEmpty()) DrawerColors.DialogSecondary else DrawerColors.Accent,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("关闭", color = DrawerColors.DialogText) }
        }
    }
}
