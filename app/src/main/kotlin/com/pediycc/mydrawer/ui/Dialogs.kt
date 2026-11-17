package com.pediycc.mydrawer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pediycc.mydrawer.data.AppInfo
import com.pediycc.mydrawer.data.BuiltInIcons
import com.pediycc.mydrawer.data.Categories
import com.pediycc.mydrawer.data.CategoryDef
import com.pediycc.mydrawer.data.SortMode
import com.pediycc.mydrawer.data.Updater
import com.pediycc.mydrawer.data.UserPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppMenuSheet(
    app: AppInfo,
    prefs: UserPrefs,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onToggleHidden: () -> Unit,
    onSetCategory: (String) -> Unit,
    onRename: () -> Unit,
    onIcon: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1E2228)) {
        Column(Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9AA3AD))
            Spacer(Modifier.height(12.dp))
            MenuItem(Icons.Filled.Launch, "启动应用", onLaunch)
            MenuItem(Icons.Filled.Edit, "重命名", onRename)
            MenuItem(Icons.Filled.PhotoCamera, "更改图标", onIcon)
            MenuItem(
                if (app.packageName in prefs.hiddenPackages) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                if (app.packageName in prefs.hiddenPackages) "取消隐藏" else "隐藏应用",
                onToggleHidden,
            )
            Spacer(Modifier.height(8.dp))
            Text("移动到分类", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8FB4FF))
            Categories.ALL.forEach { cat ->
                MenuItem(
                    if (cat.id == app.categoryId) Icons.Filled.CheckCircle else Icons.Filled.ArrowForward,
                    cat.label,
                    { onSetCategory(cat.id) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryMenuSheet(
    categoryId: String,
    vm: DrawerViewModel,
    prefs: UserPrefs,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onIcon: () -> Unit,
) {
    val currentMode = prefs.categorySortModes[categoryId] ?: prefs.sortMode
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1E2228)) {
        Column(Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                vm.categoryLabel(categoryId),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            MenuItem(Icons.Filled.Edit, "重命名分类", onRename)
            MenuItem(Icons.Filled.PhotoCamera, "更改图标", onIcon)
            MenuItem(Icons.Filled.ArrowUpward, "分类上移", { vm.moveCategory(categoryId, -1) })
            MenuItem(Icons.Filled.ArrowDownward, "分类下移", { vm.moveCategory(categoryId, 1) })
            Spacer(Modifier.height(8.dp))
            Text("此分类内应用排序", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8FB4FF))
            SortMode.LABELS.forEach { (mode, label) ->
                MenuItem(
                    if (mode == currentMode) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    label,
                    { vm.setCategorySort(categoryId, mode) },
                )
            }
            if (currentMode == SortMode.MANUAL) {
                Spacer(Modifier.height(8.dp))
                Text("手动排序（点箭头调整）", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9AA3AD))
                val apps = remember(prefs) { com.pediycc.mydrawer.data.Sorter.manualOrderOf(vm.ui.value.apps, prefs, categoryId) }
                apps.forEachIndexed { index, pkg ->
                    val label = vm.ui.value.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}. $label", color = Color.White, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.moveManual(categoryId, pkg, -1) }) {
                            Icon(Icons.Filled.KeyboardArrowUp, "上移", tint = Color.White)
                        }
                        IconButton(onClick = { vm.moveManual(categoryId, pkg, 1) }) {
                            Icon(Icons.Filled.KeyboardArrowDown, "下移", tint = Color.White)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text, color = Color.White) },
        leadingContent = { Icon(icon, null, tint = Color(0xFFBFD4FF)) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveDialog(
    categories: List<CategoryDef>,
    labelOf: (String) -> String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到分类") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                categories.filter { it.id != Categories.FREQUENT }.forEach { cat ->
                    ListItem(
                        headlineContent = { Text(labelOf(cat.id)) },
                        modifier = Modifier.clickable { onPick(cat.id) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("名称") },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(
    target: IconTarget,
    vm: DrawerViewModel,
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("内置图库", "本机图库", "应用原图标")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF1E2228)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("更改图标", style = MaterialTheme.typography.titleMedium, color = Color.White)
            TabRow(selectedTabIndex = tab) {
                titles.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
            when (tab) {
                0 -> {
                    LazyVerticalGrid(GridCells.Adaptive(64.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                        items(BuiltInIcons.ALL) { entry ->
                            Column(
                                Modifier.padding(6.dp).clickable {
                                    when (target) {
                                        is IconTarget.App -> vm.setAppIcon(target.packageName, entry.key)
                                        is IconTarget.Category -> vm.setCategoryIcon(target.categoryId, entry.key)
                                    }
                                    onDismiss()
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(entry.vector, entry.label, tint = Color.White, modifier = Modifier.size(34.dp))
                                Text(entry.label, color = Color(0xFFB9C0C8), fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
                1 -> {
                    Column(Modifier.padding(vertical = 24.dp)) {
                        Text("从本机图库选择图片，选择后自动裁剪为正方形并保存到应用内。", color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onGallery) { Text("打开本机图库") }
                    }
                }
                2 -> {
                    if (target is IconTarget.App) {
                        Column(Modifier.padding(vertical = 24.dp)) {
                            Text("恢复为该应用自带的图标", color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                vm.setAppIcon(target.packageName, "app:${target.packageName}")
                                onDismiss()
                            }) { Text("使用原应用图标") }
                        }
                    } else {
                        Column(Modifier.padding(vertical = 24.dp)) {
                            Text("可输入应用包名，使用该应用的图标作为分类图标。", color = Color.White)
                            var pkg by remember { mutableStateOf("") }
                            OutlinedTextField(value = pkg, onValueChange = { pkg = it }, label = { Text("包名") })
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {
                                if (target is IconTarget.Category && pkg.isNotBlank()) {
                                    vm.setCategoryIcon(target.categoryId, "app:$pkg")
                                    onDismiss()
                                }
                            }) { Text("使用") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateDialog(vm: DrawerViewModel, onDismiss: () -> Unit) {
    var info by remember { mutableStateOf<Updater.ReleaseInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    val prefs by vm.ui.collectAsState()
    LaunchedEffect(Unit) {
        vm.checkUpdate { release, err ->
            info = release
            error = err
            checking = false
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("检查更新") },
        text = {
            Column {
                Text("当前版本 2.0.0")
                Spacer(Modifier.height(8.dp))
                when {
                    checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp)); Text("正在检查…")
                    }
                    info != null -> {
                        Text("发现新版本：${info!!.tag}")
                        Text(
                            info!!.body.take(300).ifBlank { "暂无更新说明" },
                            fontSize = 12.sp,
                            color = Color(0xFF9AA3AD),
                            modifier = Modifier.padding(top = 8.dp).heightIn(max = 180.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                    else -> Text(error ?: "暂未发现更新")
                }
                Spacer(Modifier.height(8.dp))
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Button(onClick = { expanded = true }) { Text("代理：${prefs.prefs.updateProxy}") }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        Updater.PROXIES.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    vm.updatePrefs { it.copy(updateProxy = p) }
                                    expanded = false
                                    checking = true
                                    vm.checkUpdate { release, err ->
                                        info = release; error = err; checking = false
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (info != null) {
                TextButton(onClick = { vm.downloadAndInstall(info!!); onDismiss() }) { Text("下载并安装") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
