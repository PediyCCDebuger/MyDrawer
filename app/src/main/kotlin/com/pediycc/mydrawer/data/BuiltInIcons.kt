package com.pediycc.mydrawer.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 内置图库：参考 Smart Drawer 的分类图标语义，用 Material Icons 自绘矢量图标。
 * 不复制原版任何图片素材。图标键统一为 "builtin:<name>"。
 *
 * 2.3.0 起每个内置图标都带固定彩色（原版分类图标本身就是彩色的），
 * 不再是清一色白图标。
 */
object BuiltInIcons {
    data class Entry(val key: String, val label: String, val vector: ImageVector, val color: Color)

    /** 分类图标固定配色，跟原版彩色分类图标观感一致。 */
    private val categoryColors = mapOf(
        "sports_esports" to Color(0xFFB388FF), // 游戏 紫
        "language" to Color(0xFF42A5F5),       // 上网 蓝
        "movie" to Color(0xFFFF7043),          // 影音 橙
        "phone" to Color(0xFF66BB6A),          // 通讯 绿
        "build" to Color(0xFFFFB300),          // 工具 琥珀
        "settings" to Color(0xFF90A4AE),       // 设置 蓝灰
        "apps" to Color(0xFF4DD0E1),           // 其他 青
        "star" to Color(0xFFFFCA28),           // 最常用 金
    )

    /** 图库其余图标按顺序循环取色，保证每个都是彩色且相邻不重复。 */
    private val palette = listOf(
        Color(0xFFEF5350), Color(0xFFAB47BC), Color(0xFF5C6BC0), Color(0xFF29B6F6),
        Color(0xFF26A69A), Color(0xFF9CCC65), Color(0xFFFFA726), Color(0xFFFF7043),
        Color(0xFFEC407A), Color(0xFF7E57C2), Color(0xFF26C6DA), Color(0xFFD4E157),
    )

    private fun e(name: String, label: String, vector: ImageVector, color: Color) =
        Entry("builtin:$name", label, vector, color)

    private data class Def(val name: String, val label: String, val vector: ImageVector)

    private val defs: List<Def> = listOf(
        // 分类图标（与原版分类一一对应）
        Def("sports_esports", "游戏", Icons.Filled.SportsEsports),
        Def("language", "上网", Icons.Filled.Language),
        Def("movie", "影音", Icons.Filled.Movie),
        Def("phone", "通讯", Icons.Filled.Phone),
        Def("build", "工具", Icons.Filled.Build),
        Def("settings", "设置", Icons.Filled.Settings),
        Def("apps", "其他", Icons.Filled.Apps),
        Def("star", "最常用", Icons.Filled.Star),
        // 通用 / 常用应用语义
        Def("home", "主页", Icons.Filled.Home),
        Def("search", "搜索", Icons.Filled.Search),
        Def("favorite", "收藏", Icons.Filled.Favorite),
        Def("chat", "聊天", Icons.Filled.Chat),
        Def("email", "邮件", Icons.Filled.Email),
        Def("camera", "相机", Icons.Filled.PhotoCamera),
        Def("photo", "图片", Icons.Filled.PhotoLibrary),
        Def("music", "音乐", Icons.Filled.MusicNote),
        Def("video", "视频", Icons.Filled.Videocam),
        Def("map", "地图", Icons.Filled.Map),
        Def("navigation", "导航", Icons.Filled.Navigation),
        Def("cloud", "天气", Icons.Filled.Cloud),
        Def("calendar", "日历", Icons.Filled.CalendarMonth),
        Def("schedule", "时钟", Icons.Filled.Schedule),
        Def("compute", "计算器", Icons.Filled.Calculate),
        Def("edit", "编辑", Icons.Filled.Edit),
        Def("description", "文档", Icons.Filled.Description),
        Def("folder", "文件", Icons.Filled.Folder),
        Def("download", "下载", Icons.Filled.Download),
        Def("shopping", "购物", Icons.Filled.ShoppingCart),
        Def("payments", "支付", Icons.Filled.Payments),
        Def("account", "账户", Icons.Filled.AccountCircle),
        Def("people", "联系人", Icons.Filled.People),
        Def("lock", "安全", Icons.Filled.Lock),
        Def("shield", "防护", Icons.Filled.Shield),
        Def("bolt", "快捷", Icons.Filled.Bolt),
        Def("flashlight", "手电筒", Icons.Filled.FlashlightOn),
        Def("battery", "电量", Icons.Filled.BatteryFull),
        Def("wifi", "网络", Icons.Filled.Wifi),
        Def("bluetooth", "蓝牙", Icons.Filled.Bluetooth),
        Def("computer", "电脑", Icons.Filled.Computer),
        Def("devices", "设备", Icons.Filled.Devices),
        Def("print", "打印", Icons.Filled.Print),
        Def("keyboard", "输入法", Icons.Filled.Keyboard),
        Def("translate", "翻译", Icons.Filled.Translate),
        Def("book", "阅读", Icons.Filled.MenuBook),
        Def("school", "学习", Icons.Filled.School),
        Def("work", "办公", Icons.Filled.Work),
        Def("business", "商务", Icons.Filled.Business),
        Def("health", "健康", Icons.Filled.HealthAndSafety),
        Def("fitness", "运动", Icons.Filled.FitnessCenter),
        Def("restaurant", "饮食", Icons.Filled.Restaurant),
        Def("flight", "旅行", Icons.Filled.Flight),
        Def("directions_car", "驾车", Icons.Filled.DirectionsCar),
        Def("train", "铁路", Icons.Filled.Train),
        Def("local_shipping", "物流", Icons.Filled.LocalShipping),
        Def("pets", "宠物", Icons.Filled.Pets),
        Def("nature", "自然", Icons.Filled.Nature),
        Def("palette", "绘图", Icons.Filled.Palette),
        Def("theater", "戏剧", Icons.Filled.TheaterComedy),
        Def("sports", "体育", Icons.Filled.SportsBasketball),
        Def("radio", "广播", Icons.Filled.Radio),
        Def("podcasts", "播客", Icons.Filled.Podcasts),
        Def("smartphone", "手机", Icons.Filled.Smartphone),
        Def("tablet", "平板", Icons.Filled.Tablet),
        Def("watch", "手表", Icons.Filled.Watch),
        Def("tv", "电视", Icons.Filled.Tv),
    )

    val ALL: List<Entry> = defs.mapIndexed { index, d ->
        e(d.name, d.label, d.vector, categoryColors[d.name] ?: palette[index % palette.size])
    }

    private val byKey = ALL.associateBy { it.key }

    fun find(key: String?): Entry? = key?.let { byKey[it] }

    fun labelOf(key: String?): String = find(key)?.label ?: "默认"

    /** 内置图标自带颜色；找不到时给一个中性浅色，避免黑底上看不见。 */
    fun colorOf(key: String?): Color = find(key)?.color ?: Color(0xFFB0BEC5)

    fun vectorOf(key: String?): ImageVector = find(key)?.vector ?: Icons.Filled.Apps
}
