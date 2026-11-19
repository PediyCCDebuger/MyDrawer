package com.pediycc.mydrawer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pediycc.mydrawer.BuildConfig
import com.pediycc.mydrawer.data.AppInfo
import com.pediycc.mydrawer.data.BuiltInIcons
import com.pediycc.mydrawer.data.Categories
import com.pediycc.mydrawer.data.CategoryDef
import kotlinx.coroutines.delay
import com.pediycc.mydrawer.data.DebugLog
import com.pediycc.mydrawer.data.SortMode
import com.pediycc.mydrawer.data.Updater
import com.pediycc.mydrawer.data.UserPrefs

/**
 * 应用长按菜单。2.6.1：从 ModalBottomSheet 改为限高可滚动的 ThemedDialog。
 * 原实现在「上滑展开到底 → 内部 verticalScroll 滚动」后，bottom sheet 的拖拽/滚动
 * 节点会吞掉菜单项的点击命中，导致所有项点不动；改成普通对话框后不再有该手势冲突。
 */
@Composable
fun AppMenuSheet(
    app: AppInfo,
    prefs: UserPrefs,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onDetails: () -> Unit,
    onUninstall: () -> Unit,
    onPinToHome: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleCategory: (String) -> Unit,
    onClearCategory: () -> Unit,
    onRename: () -> Unit,
    onIcon: () -> Unit,
    onMultiSelect: () -> Unit,
) {
    // 限高并允许滚动，短屏/横屏也不溢出；列表超出一屏时正常滚动，点击始终有效。
    val maxList = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    // 2.9.0 多分类：勾选状态 = 主分类或附加归属之一；切换后不关菜单，可连续勾选多个。
    val extras = prefs.categoryExtras[app.packageName] ?: emptySet()
    fun memberOf(catId: String) = app.categoryId == catId || catId in extras
    ThemedDialog(onDismissRequest = onDismiss) {
        Text(app.label, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = DrawerColors.DialogSecondary)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().heightIn(max = maxList).verticalScroll(rememberScrollState())) {
            MenuItem(Icons.Filled.Launch, "启动应用", onLaunch)
            MenuItem(Icons.Filled.Checklist, "多选", onMultiSelect)
            MenuItem(Icons.Filled.Info, "属性", onDetails)
            MenuItem(Icons.Filled.Delete, "卸载应用", onUninstall)
            MenuItem(Icons.Filled.AddToHomeScreen, "发送到桌面", onPinToHome)
            MenuItem(Icons.Filled.Edit, "重命名", onRename)
            MenuItem(Icons.Filled.PhotoCamera, "更改图标", onIcon)
            MenuItem(
                if (app.packageName in prefs.hiddenPackages) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                if (app.packageName in prefs.hiddenPackages) "取消隐藏" else "隐藏应用",
                onToggleHidden,
            )
            Spacer(Modifier.height(8.dp))
            Text("所属分类（可勾选多个）", style = MaterialTheme.typography.labelLarge, color = DrawerColors.Accent)
            // 2.9.0：勾选方式——勾上加进该分类、取消勾出，未全部取消时可多分类同时归属；
            // 「未分类」固定排最后。
            Categories.moveTargets(prefs).forEach { cat ->
                val checked = memberOf(cat.id)
                MenuItem(
                    if (checked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    prefs.categoryLabels[cat.id] ?: cat.label,
                    { onToggleCategory(cat.id) },
                )
            }
            if (app.categoryId != Categories.OTHER || extras.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                MenuItem(Icons.Filled.RemoveCircle, "取消全部分类（移入未分类）", onClearCategory)
            }
        }
    }
}

/**
 * 分类长按菜单。2.6.1：同 AppMenuSheet，从 ModalBottomSheet 改为限高可滚动的
 * ThemedDialog，消除上滑展开到底后菜单项点击失效的问题。
 */
@Composable
fun CategoryMenuSheet(
    categoryId: String,
    vm: DrawerViewModel,
    prefs: UserPrefs,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onIcon: () -> Unit,
    onNew: () -> Unit,
    onDelete: () -> Unit,
    onReorderSort: () -> Unit,
    onReorderApps: () -> Unit,
    onNest: () -> Unit,
    onReorderChildren: () -> Unit,
    onMultiSelect: () -> Unit,
) {
    val currentMode = prefs.categorySortModes[categoryId] ?: prefs.sortMode
    val deletable = categoryId != Categories.OTHER && categoryId != Categories.FREQUENT
    val nestable = categoryId != Categories.OTHER && categoryId != Categories.FREQUENT
    val hasChildren = Categories.childrenOf(categoryId, prefs).isNotEmpty()
    val maxList = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    ThemedDialog(onDismissRequest = onDismiss) {
        Text(
            vm.categoryLabel(categoryId),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().heightIn(max = maxList).verticalScroll(rememberScrollState())) {
            MenuItem(Icons.Filled.Add, "新建分类", onNew)
            MenuItem(Icons.Filled.Checklist, "多选分类", onMultiSelect)
            MenuItem(Icons.Filled.Edit, "重命名分类", onRename)
            MenuItem(Icons.Filled.PhotoCamera, "更改图标", onIcon)
            MenuItem(Icons.Filled.SwapVert, "拖动排序分类", onReorderSort)
            if (nestable) {
                MenuItem(Icons.Filled.Folder, "移动到其它分类", onNest)
            }
            if (hasChildren && currentMode == SortMode.MANUAL) {
                MenuItem(Icons.Filled.Reorder, "排序子分类", onReorderChildren)
            }
            if (deletable) {
                MenuItem(Icons.Filled.Delete, "删除分类", onDelete)
            }
            Spacer(Modifier.height(8.dp))
            Text("此分类内应用排序", style = MaterialTheme.typography.labelLarge, color = DrawerColors.Accent)
            SortMode.LABELS.forEach { (mode, label) ->
                MenuItem(
                    if (mode == currentMode) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    label,
                    {
                        vm.setCategorySort(categoryId, mode)
                        // 选「手动排序」时直接弹出独立的单列拖拽排序界面，不再内嵌在菜单里。
                        if (mode == SortMode.MANUAL) onReorderApps()
                    },
                )
            }
            if (currentMode == SortMode.MANUAL) {
                MenuItem(Icons.Filled.Reorder, "拖动排序此分类应用", onReorderApps)
            }
        }
    }
}

/**
 * 通用拖拽排序列表：按住行右侧的双横条直接上下拖动，越过相邻行即换位并即时生效。
 * 每行用 key(k) 固定身份 —— 之前重排后 Compose 按位置复用 slot，导致手势节点被销毁重建，
 * 一次拖动只能移动一格；加 key 后手势不中断，可一口气拖到任意位置。
 * [onReorder] 参数为移动前 / 移动后的下标。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> DragReorderList(
    items: List<T>,
    keyOf: (T) -> String,
    rowHeight: Dp,
    onReorder: (from: Int, to: Int) -> Unit,
    row: @Composable (T, Boolean) -> Unit,
) {
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    // 每次交换都会触发重组；用手势块读取最新的列表与回调，避免按下标算错位置。
    val latestItems by rememberUpdatedState(items)
    val latestKeyOf by rememberUpdatedState(keyOf)
    val latestOnReorder by rememberUpdatedState(onReorder)

    Column {
        items.forEach { item ->
            val k = keyOf(item)
            key(k) {
                val dragging = draggingKey == k
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .background(if (dragging) Color(0x33FFFFFF) else Color.Transparent),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 内容（图标 + 名称）在左，占满剩余空间；拖手统一由本组件放在最右侧。
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        row(item, dragging)
                    }
                    // 右侧加粗双横条拖手：按住直接拖动，不必长按。
                    Box(
                        Modifier
                            .width(48.dp)
                            .fillMaxHeight()
                            .pointerInput(k) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingKey = k
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (draggingKey != k) return@detectDragGestures
                                        dragOffset += dragAmount.y
                                        val list = latestItems
                                        val from = list.indexOfFirst { latestKeyOf(it) == k }
                                        if (from < 0 || rowPx <= 0f) return@detectDragGestures
                                        val shift = (dragOffset / rowPx).toInt()
                                        if (shift == 0) return@detectDragGestures
                                        val to = (from + shift).coerceIn(0, list.lastIndex)
                                        val applied = to - from
                                        if (applied != 0) {
                                            latestOnReorder(from, to)
                                            dragOffset -= applied * rowPx
                                        } else {
                                            dragOffset = 0f
                                        }
                                    },
                                    onDragEnd = {
                                        draggingKey = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragOffset = 0f
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        DragGrip(active = dragging)
                    }
                }
            }
        }
    }
}

/** 两条加粗圆角横条，仿原版排序界面右侧的拖动手柄。 */
@Composable
private fun DragGrip(active: Boolean) {
    Column(
        modifier = Modifier.width(30.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(2) {
            Box(
                Modifier
                    .size(width = 30.dp, height = 4.dp)
                    .background(
                        if (active) Color.White else Color(0xD0FFFFFF),
                        MaterialTheme.shapes.extraSmall,
                    )
            )
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
    selectedPackages: Set<String>,
    memberOf: (packageName: String, categoryId: String) -> Boolean,
    labelOf: (String) -> String,
    onDismiss: () -> Unit,
    onApply: (checkedIds: List<String>) -> Unit,
) {
    // 2.9.0 勾选式：勾选 = 加入所选应用的所属分类，取消勾选 = 从该分类移出；
    // 全部勾选状态按「所有选中应用都已归属」判断，确定后按勾选顺序精确写入。
    val initialChecked = categories
        .filter { selectedPackages.isNotEmpty() && selectedPackages.all { pkg -> memberOf(pkg, it.id) } }
        .map { it.id }
        .toSet()
    var checked by remember { mutableStateOf(initialChecked) }
    // 2.9.6：「未分类」是兜底项，与具体分类互斥——勾具体分类时自动取消它，
    // 勾它时清空其它分类（否则应用会既在工具里又留在未分类一份）。
    fun toggle(id: String) {
        checked = when {
            id in checked -> checked - id
            id == Categories.OTHER -> setOf(Categories.OTHER)
            else -> (checked - Categories.OTHER) + id
        }
    }
    ThemedDialog(onDismissRequest = onDismiss) {
        Text(
            "所属分类（已选 ${selectedPackages.size} 个应用）",
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "勾选加入、取消勾选移出；同一应用可同时归属多个分类。" +
                "勾选具体分类会自动取消「未分类」，勾选「未分类」则清空其它分类。",
            color = DrawerColors.DialogSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            // 「未分类」固定排最后：它是兜底项，新建分类不会插在它后面。
            categories
                .filter { it.id != Categories.FREQUENT }
                .sortedBy { if (it.id == Categories.OTHER) 1 else 0 }
                .forEach { cat ->
                    val on = cat.id in checked
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { toggle(cat.id) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = { toggle(cat.id) },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = DrawerColors.Accent,
                            uncheckedColor = Color(0x88FFFFFF),
                            checkmarkColor = Color.Black,
                        ))
                        Text(labelOf(cat.id), color = Color.White)
                    }
                }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消", color = DrawerColors.DialogText) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { onApply(categories.filter { it.id in checked }.map { it.id }) }) {
                Text("确定", color = DrawerColors.Accent)
            }
        }
    }
}

/** 分类拖拽排序对话框：按住右侧双横条上下拖动即可自由调整分类栏顺序。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryReorderDialog(vm: DrawerViewModel, prefs: UserPrefs, onDismiss: () -> Unit) {
    // 「未分类」固定在最后，不参与拖动：只列出可拖动的分类。
    val cats = remember(prefs) { Categories.orderedFor(prefs).filter { it.id != Categories.OTHER } }
    val otherLabel = remember(prefs) {
        Categories.orderedFor(prefs).firstOrNull { it.id == Categories.OTHER }?.let {
            prefs.categoryLabels[it.id] ?: it.label
        } ?: "未分类"
    }
    // 窗口加大：更宽的左右边距 + 按屏幕高度给出更大的列表上限（横屏/短屏自动收敛）。
    val reorderMax = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    ThemedDialog(onDismissRequest = onDismiss, horizontalPadding = 12.dp) {
        Text("拖动排序分类", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text("按住每行右侧的两条横条上下拖动，调整分类栏显示顺序。", color = DrawerColors.DialogSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = reorderMax).verticalScroll(rememberScrollState())) {
            DragReorderList(
                items = cats,
                keyOf = { it.id },
                rowHeight = 56.dp,
                onReorder = { from, to -> vm.reorderCategory(from, to) },
            ) { cat, _ ->
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DrawerIcon(
                        key = vm.categoryIconKey(cat.id),
                        fallbackVector = BuiltInIcons.vectorOf(cat.iconKey),
                        size = 30.dp,
                        contentDescription = null,
                        colorTint = BuiltInIcons.colorOf(vm.categoryIconKey(cat.id)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(vm.categoryLabel(cat.id), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                }
            }
            // 「未分类」固定末位：展示为不可拖动的一行。
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawerIcon(
                    key = vm.categoryIconKey(Categories.OTHER),
                    fallbackVector = BuiltInIcons.vectorOf(Categories.iconKeyOf(Categories.OTHER)),
                    size = 30.dp,
                    contentDescription = null,
                    colorTint = BuiltInIcons.colorOf(vm.categoryIconKey(Categories.OTHER)),
                )
                Spacer(Modifier.width(10.dp))
                Text(otherLabel, color = Color(0xFFBDBDBD), modifier = Modifier.weight(1f), maxLines = 1)
                Text("固定在最后", color = Color(0xFF8A8A8A), fontSize = 12.sp)
                Spacer(Modifier.width(48.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("完成", color = DrawerColors.Accent) }
        }
    }
}

/** 分类内应用拖拽排序对话框：独立的单列界面，按住右侧双横条拖到任意位置。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppReorderDialog(categoryId: String, vm: DrawerViewModel, prefs: UserPrefs, onDismiss: () -> Unit) {
    val pkgs = remember(prefs) { com.pediycc.mydrawer.data.Sorter.manualOrderOf(vm.ui.value.apps, prefs, categoryId) }
    val reorderMax = (LocalConfiguration.current.screenHeightDp * 0.82f).dp
    ThemedDialog(onDismissRequest = onDismiss, horizontalPadding = 12.dp) {
        Text("排序 · ${vm.categoryLabel(categoryId)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text("按住每行右侧的两条横条上下拖动，把应用放到任意位置。", color = DrawerColors.DialogSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = reorderMax).verticalScroll(rememberScrollState())) {
            DragReorderList(
                items = pkgs,
                keyOf = { it },
                rowHeight = 60.dp,
                onReorder = { from, to -> vm.reorderManual(categoryId, from, to) },
            ) { pkg, _ ->
                val app = vm.ui.value.apps.firstOrNull { it.packageName == pkg }
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DrawerIcon(
                        key = vm.appIconKey(pkg),
                        fallback = app,
                        size = 34.dp,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(app?.label ?: pkg, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("完成", color = DrawerColors.Accent) }
        }
    }
}

/** 分类移动对话框：把某分类挂到另一个分类下成为子分类，或移回顶层。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NestCategoryDialog(
    childId: String,
    vm: DrawerViewModel,
    prefs: UserPrefs,
    onDismiss: () -> Unit,
) {
    val candidates = remember(prefs) { Categories.parentCandidates(childId, prefs) }
    val currentParent = prefs.categoryParents[childId]
    ThemedDialog(onDismissRequest = onDismiss) {
        Text("移动到分类", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "选择目标分类后，该分类不再单独占用分类栏，而会出现在目标分类里成为子分类。",
            color = DrawerColors.DialogSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            if (!currentParent.isNullOrBlank()) {
                ListItem(
                    headlineContent = { Text("移回顶层（取消子分类）", color = Color(0xFFFFE082)) },
                    leadingContent = { Icon(Icons.Filled.ArrowUpward, null, tint = Color(0xFFFFE082)) },
                    modifier = Modifier.clickable { vm.setCategoryParent(childId, ""); onDismiss() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            candidates.forEach { cat ->
                ListItem(
                    headlineContent = { Text(vm.categoryLabel(cat.id), color = Color.White) },
                    leadingContent = { Icon(Icons.Filled.Folder, null, tint = Color(0xFFBFD4FF)) },
                    trailingContent = {
                        if (cat.id == currentParent) Icon(Icons.Filled.CheckCircle, null, tint = DrawerColors.Accent)
                    },
                    modifier = Modifier.clickable { vm.setCategoryParent(childId, cat.id); onDismiss() },
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

/** 分类多选：把选中的多个分类一起移入某个父分类，或一起移回顶层。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveCategoriesDialog(
    selectedIds: Set<String>,
    vm: DrawerViewModel,
    prefs: UserPrefs,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    // 只保留对所有选中分类都合法的父分类（排除自身 / 后代 / 「最常用」），避免移动后成环。
    val candidates = remember(prefs, selectedIds) {
        Categories.orderedFor(prefs).filter { c ->
            c.id != Categories.FREQUENT && c.id !in selectedIds &&
                selectedIds.all { child -> !Categories.isDescendant(c.id, child, prefs.categoryParents) }
        }
    }
    ThemedDialog(onDismissRequest = onDismiss) {
        Text("移动 ${selectedIds.size} 个分类", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "移入父分类后，这些分类不再单独占用分类栏，而会出现在父分类里。",
            color = DrawerColors.DialogSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("移回顶层（取消子分类）", color = Color(0xFFFFE082)) },
                leadingContent = { Icon(Icons.Filled.ArrowUpward, null, tint = Color(0xFFFFE082)) },
                modifier = Modifier.clickable { onPick("") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            candidates.forEach { cat ->
                ListItem(
                    headlineContent = { Text(vm.categoryLabel(cat.id), color = Color.White) },
                    leadingContent = { Icon(Icons.Filled.Folder, null, tint = Color(0xFFBFD4FF)) },
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

/** 父分类内「子分类」手动排序对话框：按住右侧双横条调整子分类之间的顺序。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NestedChildReorderDialog(
    parentId: String,
    vm: DrawerViewModel,
    prefs: UserPrefs,
    onDismiss: () -> Unit,
) {
    val children = remember(prefs) { Categories.childrenOf(parentId, prefs) }
    ThemedDialog(onDismissRequest = onDismiss, horizontalPadding = 12.dp) {
        Text("排序子分类 · ${vm.categoryLabel(parentId)}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text("按住右侧横条调整子分类顺序；仅在本分类手动排序时生效。", color = DrawerColors.DialogSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            DragReorderList(
                items = children,
                keyOf = { it.id },
                rowHeight = 56.dp,
                onReorder = { from, to -> vm.reorderNestedChild(parentId, from, to) },
            ) { cat, _ ->
                Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    DrawerIcon(
                        key = vm.categoryIconKey(cat.id),
                        fallbackVector = BuiltInIcons.vectorOf(vm.categoryIconKey(cat.id)),
                        size = 30.dp,
                        contentDescription = null,
                        colorTint = BuiltInIcons.colorOf(vm.categoryIconKey(cat.id)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(vm.categoryLabel(cat.id), color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("完成", color = DrawerColors.Accent) }
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

/**
 * 调试日志查看对话框（2.9.5）：滚动显示最近日志，可复制到剪贴板、
 * 保存为文本文件（系统「下载」目录）、清空。
 *
 * 2.9.5 改动：
 * - 操作按钮改为「清空 / 保存 / 复制」一行等宽 + 「关闭」单独一行，四个挤一行时
 *   「关闭」会被压成两行；
 * - 操作结果改为在对话框内部即时提示（原来走主界面 Snackbar，被对话框盖住，
 *   必须先关掉对话框才看得到）；
 * - 清空后列表立即刷新为「（暂无记录）」，不必重开对话框。
 */
@Composable
fun DebugLogDialog(
    logText: String,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val maxList = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    // 对话框内自己持有日志文本：清空后立即刷新，不依赖上层重组。
    var body by remember { mutableStateOf(logText) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusTick by remember { mutableStateOf(0) }

    // 每次操作后 3 秒自动收起提示，避免一直占着位置。
    LaunchedEffect(statusTick) {
        if (status != null) {
            delay(3000)
            status = null
        }
    }
    fun report(msg: String) {
        status = msg
        statusTick++
    }

    val saveLog: () -> Unit = {
        val path = DebugLog.saveToFile(ctx)
        when {
            path == null -> report("保存失败：无法写入下载目录")
            // 下载目录不可写时 DebugLog 会用「应用目录」兜底，提示里要说清楚。
            path.startsWith("Download/") -> report("已保存到：$path")
            else -> report("下载目录不可写，已存到应用目录：$path")
        }
    }
    // Android 9 及以下写公共「下载」目录需要存储权限；拒绝授权时 DebugLog 会自动回退到应用目录。
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { saveLog() }

    ThemedDialog(onDismissRequest = onDismiss) {
        Text("调试日志", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "记录应用启动、载入、卸载、更新、备份等关键链路，用于问题定位。开关关闭期间不记录。",
            color = DrawerColors.DialogSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Surface(
            color = Color(0xFF14161A),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                body,
                color = Color(0xFFCFD8DC),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .heightIn(max = maxList)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            )
        }
        AnimatedVisibility(
            visible = status != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                color = Color(0xFF1B2A20),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            ) {
                Text(
                    status.orEmpty(),
                    color = Color(0xFF9FE6B0),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    onClear()
                    body = "（暂无记录）"
                    report("已清空，进行中的记录会继续追加")
                },
                modifier = Modifier.weight(1f),
            ) { Text("清空", color = DrawerColors.DialogText) }
            TextButton(
                onClick = {
                    val needLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) != PackageManager.PERMISSION_GRANTED
                    if (needLegacyPermission) {
                        legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        saveLog()
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("保存", color = DrawerColors.Accent) }
            TextButton(
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("MyDrawer 调试日志", body))
                    report("已复制到剪贴板")
                },
                modifier = Modifier.weight(1f),
            ) { Text("复制", color = DrawerColors.Accent) }
        }
        HorizontalDivider(color = Color(0x1FFFFFFF))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("关闭", color = DrawerColors.Accent, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * 「最常用数量」输入对话框（2.9.1）：点设置行弹出，输入数字 + 「确定」才写入生效；
 * 对话框内直接提示允许的范围（上限 = 本机有使用记录的应用数）。
 */
@Composable
fun FrequentLimitDialog(
    value: Int,
    maxAllowed: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 1..maxAllowed
    ThemedDialog(onDismissRequest = onDismiss) {
        Text("最常用数量", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "「最常用」分类显示的应用个数（默认 10）。最多可输入 $maxAllowed（本机有使用记录的应用数）。",
            color = DrawerColors.DialogSecondary,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { raw -> text = raw.filter { it.isDigit() }.take(3) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Color.White),
            isError = !valid,
            supportingText = {
                Text(
                    if (valid || text.isBlank()) "可输入 1 – $maxAllowed"
                    else "请输入 1 – $maxAllowed 之间的数字",
                    color = if (valid) DrawerColors.DialogSecondary else Color(0xFFFFB4AB),
                    fontSize = 12.sp,
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = DrawerColors.Accent,
                unfocusedBorderColor = Color(0x88FFFFFF),
                cursorColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("取消", color = DrawerColors.DialogText) }
            Spacer(Modifier.width(8.dp))
            TextButton(
                enabled = valid,
                onClick = { parsed?.let { if (it in 1..maxAllowed) onConfirm(it) } },
            ) {
                Text("确定", color = if (valid) DrawerColors.Accent else DrawerColors.DialogSecondary)
            }
        }
    }
}

/**
 * 新建分类对话框：名称 + 内置图标网格一次选好，避免建完再单独改图标。
 */
@Composable
fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, iconKey: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var iconKey by remember { mutableStateOf("builtin:apps") }
    ThemedDialog(onDismissRequest = onDismiss) {
        // 内容（名称 + 图标网格）可能超出屏幕，限高并允许滚动，键盘弹出时按钮仍可达。
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.66f).dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("新建分类", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(14.dp))
            Text("分类图标", color = DrawerColors.Accent, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            LazyVerticalGrid(
                GridCells.Adaptive(52.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
            ) {
                items(BuiltInIcons.ALL) { entry ->
                    Box(
                        Modifier
                            .padding(3.dp)
                            .size(46.dp)
                            .background(
                                if (entry.key == iconKey) DrawerColors.RailSelected else Color.Transparent,
                                MaterialTheme.shapes.small,
                            )
                            .clickable { iconKey = entry.key },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            entry.vector,
                            entry.label,
                            tint = entry.color,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消", color = DrawerColors.DialogText) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim(), iconKey) }) {
                    Text("创建", color = DrawerColors.Accent)
                }
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
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        // 之前「应用原图标」5 个字被折成两行，和另外两个标签对不齐；
                        // 这里强制单行不换行并略缩小字号，三个标签高度一致。
                        text = {
                            Text(
                                title,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                    )
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
fun UpdateDialog(
    vm: DrawerViewModel,
    onDismiss: () -> Unit,
    /** 2.9.7：自动检查已经取到版本信息时直接显示，不再重复请求网络。 */
    prechecked: Updater.ReleaseInfo? = null,
) {
    val currentVersion = BuildConfig.VERSION_NAME
    var info by remember { mutableStateOf(prechecked) }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(prechecked == null) }
    var proxyExpanded by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    // 已下载的安装包保留在对话框里：系统安装界面被返回键取消后，可一键重装而不必重新下载。
    var downloadedApk by remember { mutableStateOf<java.io.File?>(null) }
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

    LaunchedEffect(Unit) { if (prechecked == null) check() }

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
                    downloadedApk = null
                    // 先下载完整安装包，再拉起系统安装器；下载完成即把文件交给界面保留。
                    vm.downloadAndInstall(newVersion) { msg, file ->
                        downloading = false
                        result = msg
                        downloadedApk = file
                    }
                }) {
                    Text("下载并安装", color = DrawerColors.Accent)
                }
            }
            if (downloadedApk != null && !downloading) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    result = null
                    vm.installDownloaded(downloadedApk!!) { msg -> result = msg }
                }) {
                    Text("重新安装", color = DrawerColors.Accent)
                }
            }
        }
    }
}
