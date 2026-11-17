package com.pediycc.mydrawer.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 内置图库：参考 Smart Drawer 的分类图标语义，用 Material Icons 自绘矢量图标。
 * 不复制原版任何图片素材。图标键统一为 "builtin:<name>"。
 */
object BuiltInIcons {
    data class Entry(val key: String, val label: String, val vector: ImageVector)

    private fun e(name: String, label: String, vector: ImageVector) =
        Entry("builtin:$name", label, vector)

    val ALL: List<Entry> = listOf(
        // 分类图标（与原版分类一一对应）
        e("sports_esports", "游戏", Icons.Filled.SportsEsports),
        e("language", "上网", Icons.Filled.Language),
        e("movie", "影音", Icons.Filled.Movie),
        e("phone", "通讯", Icons.Filled.Phone),
        e("build", "工具", Icons.Filled.Build),
        e("settings", "设置", Icons.Filled.Settings),
        e("apps", "其他", Icons.Filled.Apps),
        e("star", "最常用", Icons.Filled.Star),
        // 通用 / 常用应用语义
        e("home", "主页", Icons.Filled.Home),
        e("search", "搜索", Icons.Filled.Search),
        e("favorite", "收藏", Icons.Filled.Favorite),
        e("chat", "聊天", Icons.Filled.Chat),
        e("email", "邮件", Icons.Filled.Email),
        e("camera", "相机", Icons.Filled.PhotoCamera),
        e("photo", "图片", Icons.Filled.PhotoLibrary),
        e("music", "音乐", Icons.Filled.MusicNote),
        e("video", "视频", Icons.Filled.Videocam),
        e("map", "地图", Icons.Filled.Map),
        e("navigation", "导航", Icons.Filled.Navigation),
        e("cloud", "天气", Icons.Filled.Cloud),
        e("calendar", "日历", Icons.Filled.CalendarMonth),
        e("schedule", "时钟", Icons.Filled.Schedule),
        e("compute", "计算器", Icons.Filled.Calculate),
        e("edit", "编辑", Icons.Filled.Edit),
        e("description", "文档", Icons.Filled.Description),
        e("folder", "文件", Icons.Filled.Folder),
        e("download", "下载", Icons.Filled.Download),
        e("shopping", "购物", Icons.Filled.ShoppingCart),
        e("payments", "支付", Icons.Filled.Payments),
        e("account", "账户", Icons.Filled.AccountCircle),
        e("people", "联系人", Icons.Filled.People),
        e("lock", "安全", Icons.Filled.Lock),
        e("shield", "防护", Icons.Filled.Shield),
        e("bolt", "快捷", Icons.Filled.Bolt),
        e("flashlight", "手电筒", Icons.Filled.FlashlightOn),
        e("battery", "电量", Icons.Filled.BatteryFull),
        e("wifi", "网络", Icons.Filled.Wifi),
        e("bluetooth", "蓝牙", Icons.Filled.Bluetooth),
        e("computer", "电脑", Icons.Filled.Computer),
        e("devices", "设备", Icons.Filled.Devices),
        e("print", "打印", Icons.Filled.Print),
        e("keyboard", "输入法", Icons.Filled.Keyboard),
        e("translate", "翻译", Icons.Filled.Translate),
        e("book", "阅读", Icons.Filled.MenuBook),
        e("school", "学习", Icons.Filled.School),
        e("work", "办公", Icons.Filled.Work),
        e("business", "商务", Icons.Filled.Business),
        e("health", "健康", Icons.Filled.HealthAndSafety),
        e("fitness", "运动", Icons.Filled.FitnessCenter),
        e("restaurant", "饮食", Icons.Filled.Restaurant),
        e("flight", "旅行", Icons.Filled.Flight),
        e("directions_car", "驾车", Icons.Filled.DirectionsCar),
        e("train", "铁路", Icons.Filled.Train),
        e("local_shipping", "物流", Icons.Filled.LocalShipping),
        e("pets", "宠物", Icons.Filled.Pets),
        e("nature", "自然", Icons.Filled.Nature),
        e("palette", "绘图", Icons.Filled.Palette),
        e("theater", "戏剧", Icons.Filled.TheaterComedy),
        e("sports", "体育", Icons.Filled.SportsBasketball),
        e("radio", "广播", Icons.Filled.Radio),
        e("podcasts", "播客", Icons.Filled.Podcasts),
        e("smartphone", "手机", Icons.Filled.Smartphone),
        e("tablet", "平板", Icons.Filled.Tablet),
        e("watch", "手表", Icons.Filled.Watch),
        e("tv", "电视", Icons.Filled.Tv),
    )

    private val byKey = ALL.associateBy { it.key }

    fun find(key: String?): Entry? = key?.let { byKey[it] }

    fun labelOf(key: String?): String = find(key)?.label ?: "默认"
}
