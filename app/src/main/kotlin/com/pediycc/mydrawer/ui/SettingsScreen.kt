package com.pediycc.mydrawer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pediycc.mydrawer.data.BuiltInIcons
import com.pediycc.mydrawer.data.Categories
import com.pediycc.mydrawer.data.SortMode
import com.pediycc.mydrawer.data.Updater
import com.pediycc.mydrawer.data.UserPrefs
import com.pediycc.mydrawer.BuildConfig

/**
 * 设置页：按原版 Smart Drawer「应用网格 / 安全 / 程式列表」分组复刻，全中文。
 * 英文原项在注释中保留，便于对照逆向规格。
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
    var hiddenManage by remember { mutableStateOf(false) }
    var columnsDialog by remember { mutableStateOf<Boolean?>(null) } // true=直式 false=横式
    var pinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.ArrowBack, "返回") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // ---- 应用网格（原 pref_drawer / App grid） ----
            item { SectionTitle("应用网格") }
            item {
                SwitchRow(
                    title = "图标包",
                    subtitle = "使用内置矢量图库（原版：Icon pack）",
                    icon = Icons.Filled.Apps,
                    checked = true,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
            item {
                StepperRow(
                    title = "图示大小",
                    subtitle = "原版：Icon Size",
                    value = prefs.iconSizePercent,
                    range = 60..140,
                    step = 10,
                    suffix = "%",
                    onValue = { v -> vm.updatePrefs { it.copy(iconSizePercent = v) } },
                )
            }
            item {
                SwitchRow(
                    title = "规范化图示",
                    subtitle = "自动调整大小以符合 Material 设计规范（原版：Normalize icons）",
                    icon = Icons.Filled.AutoAwesome,
                    checked = true,
                    enabled = false,
                    onCheckedChange = {},
                )
            }
            item {
                SettingsRow(
                    title = "直式列数",
                    subtitle = "原版：Portrait Columns · 当前 ${prefs.gridColumns} 列",
                    icon = Icons.Filled.ViewColumn,
                    onClick = { columnsDialog = true },
                )
            }
            item {
                SettingsRow(
                    title = "横式列数",
                    subtitle = "原版：Landscape Columns · 当前 ${prefs.landscapeColumns} 列",
                    icon = Icons.Filled.GridView,
                    onClick = { columnsDialog = false },
                )
            }
            item {
                SwitchRow(
                    title = "图标标签",
                    subtitle = "在应用图标旁显示名称（原版：Icons label Visibility）",
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

            // ---- 安全（原 prefSecurity / Security） ----
            item { SectionTitle("安全") }
            item {
                SettingsRow(
                    title = "管理隐藏应用",
                    subtitle = "已隐藏 ${prefs.hiddenPackages.size} 个（原版：Manage hidden apps）",
                    icon = Icons.Filled.VisibilityOff,
                    onClick = { hiddenManage = true },
                )
            }
            item {
                SettingsRow(
                    title = "显示系统应用",
                    subtitle = "是否在抽屉中列出系统应用",
                    icon = Icons.Filled.Android,
                    onClick = { vm.setShowSystemApps(!prefs.showSystemApps) },
                    trailing = {
                        Checkbox(
                            checked = prefs.showSystemApps,
                            onCheckedChange = { vm.setShowSystemApps(it) },
                        )
                    },
                )
            }
            item {
                SettingsRow(
                    title = "使用 PIN",
                    subtitle = "原版：Use PIN · 隐藏应用需输入密码（预留）",
                    icon = Icons.Filled.Pin,
                    onClick = { pinDialog = true },
                    trailing = { Text("预留", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }

            // ---- 程式列表（原 drawerTitle / Drawer） ----
            item { SectionTitle("程式列表") }
            item {
                SwitchRow(
                    title = "左手控制",
                    subtitle = "将分类列表显示于画面右方（原版：Left-handed）",
                    icon = Icons.Filled.SwapHoriz,
                    checked = prefs.leftHanded,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(leftHanded = v) } },
                )
            }
            item {
                SwitchRow(
                    title = "类别名称",
                    subtitle = "在分类图标附近显示名称（原版：Categories Label）",
                    icon = Icons.Filled.Category,
                    checked = prefs.showCategoryLabels,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(showCategoryLabels = v) } },
                )
            }
            item {
                SwitchRow(
                    title = "透明背景",
                    subtitle = "抽屉背景半透明，可透出壁纸",
                    icon = Icons.Filled.Wallpaper,
                    checked = prefs.transparentBackground,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(transparentBackground = v) } },
                )
            }
            item {
                SwitchRow(
                    title = "最常用分类",
                    subtitle = "按使用频率自动排列最常用的 10 个应用（原版：Promoted apps）",
                    icon = Icons.Filled.Star,
                    checked = prefs.frequentEnabled,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(frequentEnabled = v) } },
                )
            }
            item {
                SwitchRow(
                    title = "应用程序由底部堆起",
                    subtitle = "从底部开始显示应用（原版：Stack apps from bottom）",
                    icon = Icons.Filled.VerticalAlignBottom,
                    checked = prefs.stackFromBottom,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(stackFromBottom = v) } },
                )
            }
            item {
                SwitchRow(
                    title = "显示菜单按钮",
                    subtitle = "在右上角显示更新按钮（原版：Show menu button）",
                    icon = Icons.Filled.Menu,
                    checked = prefs.showMenuButton,
                    onCheckedChange = { v -> vm.updatePrefs { it.copy(showMenuButton = v) } },
                )
            }

            // ---- 备份 / 更新 ----
            item { SectionTitle("备份与恢复") }
            item {
                SettingsRow(
                    title = "导出配置备份",
                    subtitle = "对应右上角软盘图标 💾",
                    icon = Icons.Filled.Save,
                    onClick = onExport,
                )
            }
            item {
                SettingsRow(
                    title = "导入配置备份",
                    subtitle = "对应右上角时钟图标 🕘",
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
            item {
                Text(
                    "MyDrawer ${BuildConfig.VERSION_NAME} · 仿 Smart Drawer 1.0.3 界面与功能，独立重写，未复制原版代码。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    if (pinDialog) {
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text("使用 PIN") },
            text = { Text("此版本暂未启用 PIN 锁，功能预留。隐藏应用仍可通过长按菜单管理。") },
            confirmButton = { TextButton(onClick = { pinDialog = false }) { Text("知道了") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(icon, null) },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}

@Composable
private fun StepperRow(
    title: String,
    subtitle: String,
    value: Int,
    range: IntRange,
    step: Int,
    suffix: String,
    onValue: (Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PhotoSizeSelectSmall, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$value$suffix")
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(((it / step).toInt() * step).coerceIn(range.first, range.last)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
}

@Composable
private fun <T> ListSettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsRow(title, subtitle, icon, { expanded = true })
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun ColumnDialog(
    portrait: Boolean,
    prefs: UserPrefs,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val current = if (portrait) prefs.gridColumns else prefs.landscapeColumns
    val values = if (portrait) (2..8).toList() else (2..10).toList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (portrait) "直式列数" else "横式列数") },
        text = {
            Column {
                values.forEach { n ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(n) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == n, onClick = { onPick(n) })
                        Spacer(Modifier.width(8.dp)); Text("$n 列")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun HiddenAppsDialog(vm: DrawerViewModel, prefs: UserPrefs, onDismiss: () -> Unit) {
    val hidden = prefs.hiddenPackages.toList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理隐藏应用（${hidden.size}）") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                if (hidden.isEmpty()) {
                    Text("暂无隐藏应用。长按应用图标可隐藏。")
                } else {
                    hidden.forEach { pkg ->
                        val label = vm.ui.value.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                        ListItem(
                            headlineContent = { Text(label) },
                            supportingContent = { Text(pkg, style = MaterialTheme.typography.bodySmall) },
                            trailingContent = {
                                TextButton(onClick = { vm.toggleHidden(pkg) }) { Text("显示") }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
