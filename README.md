# MyDrawer

一个轻量、独立的应用抽屉工具。首次启动自动载入全部已安装应用并按规则分类，无需替换桌面。

## 缘起

Smart Drawer（Smart Launcher Team，包名 `ginlemon.smartdrawer`）在 2021 年后停止更新，在新版 Android 上闪退、备份失效、分类错乱。MyDrawer 以它为**功能与界面参考**，从零重写，只保留抽屉核心，适配新版系统。

本项目是干净重写：逆向仅用于提取功能行为与数据（分类规则表），**未复制原版任何代码**，也不含商业授权、Google Analytics、应用内购买、广告、"在 Play 商店评分"等无关组件。

**当前版本 2.4.0（`versionCode` 6）**。功能与界面按原版 Smart Drawer 1.0.3 重做，界面固定纯黑不透明底，详见 [CHANGELOG.md](CHANGELOG.md)。

## 功能

- **自动载入 + 自动分类**：首次启动读取全部应用，用随包内置的 2000 条包名规则自动归类（游戏 / 上网 / 影音 / 通讯 / 工具 / 设置 / 其他）。
- **纵列分类抽屉（仿原版）**：左侧竖排分类图标栏（略浅于纯黑的深色底），彩色分类图标大小可独立调节，分类文字、应用文字可设置；支持左手模式（分类栏放右侧）。「全部」与「最常用」两个入口都可在设置里单独关闭，「最常用」默认排在分类栏最前。
- **多选批量移动**：跨分类勾选多个应用，一键移动到目标分类。
- **深度定制**：长按应用可重命名 / 换图标（内置矢量图库 / 本机图库 / 应用原图标）；长按分类可重命名 / 换图标 / 排序 / 上下移动，分类内支持按名称 / 安装时间 / 使用频率 / 手动排序。
- **最常用分类**：在系统「使用情况访问权限」授权后，按最近 30 天本机真实使用频率排前 10；未授权时回落到应用内启动次数，可在设置里开关并前往授权。
- **搜索**：按应用名或包名实时过滤。
- **隐藏应用**：长按图标隐藏，设置里可显示系统应用。
- **配置备份**：右上角软盘图标 = 导出，时钟图标 = 导入；开放 JSON 备份（分类覆盖、隐藏列表、排序、图标、外观），v1 旧备份仍可导入。
- **自动更新**：默认直连检测 GitHub Release（按版本号比较，不会把同版本误判为更新），可选公共反向代理（gh-proxy.org / gh-proxy.com / ghfast.top / gh.acmsz.top / github.chenc.dev / gh.monlor.com）下载升级；下载后校验安装包，缺「安装未知应用」授权会引导到系统设置；不含本机代理。

## 技术

- Kotlin + Jetpack Compose（Material 3），minSdk 26 / targetSdk 37
- `QUERY_ALL_PACKAGES` 读取全部应用；SAF 导出/导入备份，适配分区存储
- 仅申请 `QUERY_ALL_PACKAGES`、`INTERNET`（自动更新）与 `REQUEST_INSTALL_PACKAGES`（拉起系统安装器），不申请危险权限
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

Release 由 CI 在打标签时构建，使用仓库内置的固定签名（`.github/signing/release.keystore.b64`），确保各版本签名一致、可原地覆盖升级。

**注意**：v1.0.0 / v2.0.0 使用过临时签名，与 v2.1.0 起的固定签名不同。若设备上已安装旧版，需先卸载旧版再安装 v2.1.0，之后的升级即可直接覆盖。升级前可用右上角软盘图标导出配置备份，装好后用时钟图标导入。

## 许可

本项目为个人学习与自用。Smart Drawer 是 Smart Launcher Team 的产品，本项目与其无关联。
