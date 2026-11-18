package com.pediycc.mydrawer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pediycc.mydrawer.BuildConfig
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
    onDetails: () -> Unit,
    onToggleHidden: () -> Unit,
    onSetCategory: (String) -> Unit,
    onRename: () -> Unit,
    onIcon: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF2A2D31)) {
        Column(Modifier.padding(16.dp).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = DrawerColors.DialogSecondary)
            Spacer(Modifier.height(12.dp))
            MenuItem(Icons.Filled.Launch, "启动应用", onLaunch)
            MenuItem(Icons.Filled.Info, "属性", onDetails)
            MenuItem(Icons.Filled.Edit, "重命名", onRename)
            MenuItem(Icons.Filled.PhotoCamera, "更改图标", onIcon)
            MenuItem(
                if (app.packageName in prefs.hiddenPackages) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                if (app.packageName in prefs.hiddenPackages) "取消隐藏" else "隐藏应用",
                onToggleHidden,
            )
            Spacer(Modifier.height(8.dp))
            Text("移动到分类", style = MaterialTheme.typography.labelLarge, color = DrawerColors.Accent)
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF2A2D31)) {
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
            Text("此分类内应用排序", style = MaterialTheme.typography.labelLarge, color = DrawerColors.Accent)
            SortMode.LABELS.forEach { (mode, label) ->
                MenuItem(
                    if (mode == currentMode) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    label,
                    { vm.setCategorySort(categoryId, mode) },
                )
            }
            if (currentMode == SortMode.MANUAL) {
                Spacer(Modifier.height(8.dp))
                Text("手动排序（点箭头调整）", style = MaterialTheme.typography.labelSmall, color = DrawerColors.DialogSecondary)
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
    ThemedDialog(onDismissRequest = onDismiss) {
        Text("移动到分类", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            categories.filter { it.id != Categories.FREQUENT }.forEach { cat ->
                ListItem(
                    headlineContent = { Text(labelOf(cat.id), color = Color.White) },
                    modifier = Modifier.clickable { onPick(cat.id) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消", color = DrawerColors.DialogText) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    ThemedDialog(onDismissRequest = onDismiss) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            label = { Text("名称", color = DrawerColors.DialogSecondary) },
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
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消", color = DrawerColors.DialogText) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text("确定", color = DrawerColors.Accent)
            }
        }
    }
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF2A2D31)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("更改图标", style = MaterialTheme.typography.titleMedium, color = Color.White)
            TabRow(
                selectedTabIndex = tab,
                containerColor = Color(0xFF2A2D31),
                contentColor = Color.White,
            ) {
                titles.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title, color = Color.White) })
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
                                Icon(entry.vector, entry.label, tint = entry.color, modifier = Modifier.size(34.dp))
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
                            OutlinedTextField(
                                value = pkg,
                                onValueChange = { pkg = it },
                                label = { Text("包名", color = DrawerColors.DialogSecondary) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = DrawerColors.Accent,
                                    unfocusedBorderColor = Color(0x88FFFFFF),
                                    cursorColor = Color.White,
                                ),
                            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDialog(vm: DrawerViewModel, onDismiss: () -> Unit) {
    val currentVersion = BuildConfig.VERSION_NAME
    var info by remember { mutableStateOf<Updater.ReleaseInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var proxyExpanded by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val state by vm.ui.collectAsState()

    fun check() {
        checking = true
        error = null
        vm.checkUpdate { release, err ->
            info = release
            error = err
            checking = false
        }
    }

    LaunchedEffect(Unit) { check() }

    // 只有远端版本确实更大才算「发现新版本」；相同版本显示已是最新。
    val newVersion = info?.takeIf { Updater.isNewer(it.tag, currentVersion) }

    ThemedDialog(onDismissRequest = onDismiss) {
        Text("检查更新", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text("当前版本 $currentVersion", color = DrawerColors.DialogText)
        Spacer(Modifier.height(8.dp))
        when {
            checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = DrawerColors.Accent)
                Spacer(Modifier.width(8.dp)); Text("正在检查…", color = DrawerColors.DialogText)
            }
            newVersion != null -> {
                Text("发现新版本：${newVersion.tag}", color = Color(0xFF9BE29F))
                Text(
                    newVersion.body.take(300).ifBlank { "暂无更新说明" },
                    fontSize = 12.sp,
                    color = DrawerColors.DialogSecondary,
                    modifier = Modifier.padding(top = 8.dp).heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                )
            }
            info != null -> Text("已是最新版本（$currentVersion）", color = DrawerColors.DialogText)
            else -> Text(error ?: "检查更新失败，可更换代理后重试", color = DrawerColors.DialogText)
        }

        if (downloading) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = DrawerColors.Accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在下载安装包…", color = DrawerColors.DialogText, fontSize = 13.sp)
            }
        }
        result?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Color(0xFFFFE082), fontSize = 13.sp)
        }

        Spacer(Modifier.height(8.dp))
        Text("下载代理：${state.prefs.updateProxy}", color = DrawerColors.DialogSecondary, fontSize = 12.sp)
        TextButton(onClick = { proxyExpanded = !proxyExpanded }) {
            Text(if (proxyExpanded) "收起代理列表" else "更换代理", color = DrawerColors.Accent)
        }
        // 不用 DropdownMenu：弹出层会套用系统浅色主题导致白底白字。
        if (proxyExpanded) {
            Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                Updater.PROXIES.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.updatePrefs { it.copy(updateProxy = p) }
                                proxyExpanded = false
                                result = null
                                check()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = p == state.prefs.updateProxy,
                            onClick = {},
                            colors = RadioButtonDefaults.colors(
                                selectedColor = DrawerColors.Accent,
                                unselectedColor = Color(0xFFBDBDBD),
                            ),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(p, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("关闭", color = DrawerColors.DialogText) }
            if (newVersion != null && !downloading) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    downloading = true
                    result = null
                    vm.downloadAndInstall(newVersion) { msg ->
                        downloading = false
                        result = msg
                    }
                }) {
                    Text("下载并安装", color = DrawerColors.Accent)
                }
            }
        }
    }
}
