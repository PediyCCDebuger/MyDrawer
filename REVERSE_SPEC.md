# Smart Drawer 1.0.3 逆向参考规格

> 来源：`Smart_Drawer_v1_0_3_PRO.apk`（包名 `ginlemon.smartdrawer`，v1.0.3 / code 461）
> 用途：MyDrawer 重写版的功能与数据结构参考。仅提取行为与数据，不复制原版代码。
> 分析工具：androguard 4.1.4；纯原生 Java、单 dex、未加固、核心类明文。

## 1. 原始应用画像

| 项 | 值 |
|---|---|
| 包名 | `ginlemon.smartdrawer` |
| 版本 | 1.0.3 (versionCode 461) |
| minSdk / targetSdk | 15 / 29（compileSdk 28，Android 9 时代） |
| 技术栈 | 原生 Java + AndroidX + SQLite，无 Flutter/RN/so，无加固 |
| 类总数 | 2320（ginlemon 自有约 612，核心 drawer 包 62） |
| 新版闪退根因 | targetSdk 29 + 大量已废弃权限（WRITE_SETTINGS / GET_TASKS / READ_PHONE_STATE / SYSTEM_ALERT_WINDOW） |

## 2. 核心包与类（核心逻辑明文，可行为参考）

- `ginlemon.flower.drawer.Drawer` — 抽屉主容器
- `ginlemon.flower.drawer.CategoryList` — 分类栏（横跨顶部的分类标签）
- `ginlemon.flower.drawer.IconGrid` — 应用图标网格
- `ginlemon.flower.drawer.SearchPanel` — 搜索面板
- `ginlemon.flower.drawer.DrawerGridCoordinator` — 分类与网格联动
- `ginlemon.flower.drawer.InfoPanel` / `InfoPanelButton` — 长按应用的信息面板
- `ginlemon.flower.drawer.p` / `.o` — SQLite 数据库访问层（drawer 表）
- `ginlemon.flower.recovery.Recovery` — 备份恢复入口
- `ginlemon.flower.preferences.ExportedActivities$RestoreBackup` — 恢复备份 Activity
- `ginlemon.flower.preferences.q` — 备份读写核心逻辑

## 3. 自动分类规则表（已提取，可直接复用）

- 原始文件：`res/raw/offline_list`，已导出到 `assets/categories.json`
- 结构：JSON 数组，每项 `{"n": "<包名>", "fC": "<分类>"}`
- 条目数：2000
- 分类取值与分布：
  - `settings` 493
  - `utility` 394
  - `media` 369
  - `games` 301
  - `internet` 296
  - `phone` 134
  - 无分类 13
- 算法推断：首次启动读全部已安装包 → 按包名精确匹配 `n` → 得到 `fC` 分类；未命中的进「其他」。规则表随用随补即可。

## 4. 备份/恢复格式（重写的兼容目标）

- 备份目录：`./.smartlauncher/backups/`
- 备份文件：`backup.slbk`（"SmartLauncherBackup"）
- 编码：gzip 压缩 + JSON preferences（`create json preferences failed` / `Failed to gzip content`）
- 偏好文件名：`ginlemon.flowerfree.xml` / `ginlemon.flowerpro.xml`
- 分类数据：SQLite 数据库（`UPDATE drawer SET category = "utility" ... WHERE category = "..."`，flags 位运算管理隐藏/收藏）
- 重写决策：新版用**开放 JSON 格式**导出分类覆盖、隐藏列表、排序与外观设置；不追求直接读旧 `.slbk`（闭源格式、无必要），但保留同目录语义方便用户识别。

## 5. 界面资源（可复用清单）

- 211 个 layout XML（`drawer.xml`、`folder_layout.xml`、`list_item_*` 等）—— 布局结构参考
- 240 张 drawable 图片、5 套字体（assets/*.ttf）
- 13 套设置页（`pref_01_global_settings.xml` … `pref_11_developers.xml`）
- 原版启动图标为默认 Android 机器人，无复用价值 → 新项目重新设计

## 5.1 主题与透明背景（2026-11-17 jadx 复验）

- `AndroidManifest.xml`：application 主题 `@style/Theme.MyTheme`；主 Activity `ginlemon.flower.HomeScreen`；设置页 `ginlemon.flower.preferences.PrefMain` 未覆盖主题，因此同样继承该主题。
- `res/values/styles.xml`：`Theme.MyTheme` 的 parent 是 `@android:style/Theme.Holo.Wallpaper.NoTitleBar`。关键点：该父主题设置 `android:windowShowWallpaper=true` 且 `windowBackground` 为空，系统才会在 Activity 窗口后绘制壁纸。仅把窗口背景设为透明（MyDrawer 2.1.0 的做法）仍会呈现黑底。
- `res/drawable/catlist_bg.xml`：分类栏背景为 `#20FFFFFF` 白色磨砂；与白字 + 黑色投影配合（`res/layout/drawer.xml` 中 `action_title`/`searchBox` 文字为 `#fff`）。
- 深色对话框：`MaterialDarkDialog` 的 `windowBackground` 为 `@drawable/mtrl_dark_dialog_bg`（约 `#303030` 的深色圆角 9-patch）。`ginlemon.library.p.g()` 在 API≥21 用 `AlertDialog.Builder(context, 5)`（Material 深色系）创建对话框。
- MyDrawer 2.2.0 据此在 `themes.xml` 与窗口 flag 两处开启 `windowShowWallpaper`；所有弹窗统一为约 `#303030` 深色圆角 + 白字；设置页改为半透明磨砂列表。

## 6. 重写取舍

保留：首次启动自动载入全部应用、按规则自动分类、分类抽屉网格、多选批量、隐藏应用、配置备份导出导入、A-Z/搜索。
剔除：商业授权(Pro/内购)、Firebase/GMS/Analytics/FCM、广告、`ads_panel.xml`、"在 Play 商店评分"、壁纸/锁屏/小部件/主题商店/云同步等与"轻量抽屉"无关的功能。
适配：minSdk 26 / targetSdk 随最新；`QUERY_ALL_PACKAGES` 读取全部应用；分区存储下用 SAF 导出备份；不申请任何废弃/危险权限。
