# MyDrawer

一个轻量、独立的应用抽屉工具。首次启动自动载入全部已安装应用并按规则分类，无需替换桌面。

## 缘起

Smart Drawer（Smart Launcher Team，包名 `ginlemon.smartdrawer`）在 2021 年后停止更新，在新版 Android 上闪退、备份失效、分类错乱。MyDrawer 以它为**功能与界面参考**，从零重写，只保留抽屉核心，适配新版系统。

本项目是干净重写：逆向仅用于提取功能行为与数据（分类规则表），**未复制原版任何代码**，也不含商业授权、Google Analytics、应用内购买、广告、"在 Play 商店评分"等无关组件。

## 功能

- **自动载入 + 自动分类**：首次启动读取全部应用，用随包内置的 2000 条包名规则自动归类（游戏 / 上网 / 影音 / 通讯 / 工具 / 设置 / 其他）。
- **分类抽屉**：顶部分类栏 + 应用网格，A-Z/名称（拼音）排序，每行图标数可调。
- **搜索**：按应用名或包名实时过滤。
- **隐藏应用**：长按图标隐藏，设置里可显示系统应用。
- **手动改分类**：长按图标可把应用移动到任意分类，覆盖自动结果。
- **配置备份**：右上角导出/导入开放 JSON 备份（分类覆盖、隐藏列表、排序、外观），重装后一键恢复 —— 这正是原版最缺的一环。

## 技术

- Kotlin + Jetpack Compose（Material 3），minSdk 26 / targetSdk 37
- `QUERY_ALL_PACKAGES` 读取全部应用；SAF 导出/导入备份，适配分区存储
- 不申请任何废弃/危险权限，无网络权限，纯本地运行
- GitHub Actions 打 `vX.Y.Z` 标签触发 Release，产出 universal / arm64-v8a / armeabi-v7a 三个 APK + SHA256SUMS

## 与上游的关系

- 分类规则表 `app/src/main/assets/categories.json` 提取自 Smart Drawer 1.0.3 PRO 的 `res/raw/offline_list`（2000 条 `包名→分类` 数据）。
- 界面与交互参考 Smart Drawer 1.0.3 的布局结构，实现为全新的 Compose 代码。
- 启动图标为本项目原创（蓝色圆角方块 + 九宫格），未使用原版素材。
- 详细逆向规格见 `REVERSE_SPEC.md`。

## 构建

```bash
./gradlew assembleDebug       # 本地 debug 包
./gradlew testDebugUnitTest   # 单元测试
```

Release 由 CI 在打标签时构建。

## 许可

本项目为个人学习与自用。Smart Drawer 是 Smart Launcher Team 的产品，本项目与其无关联。
