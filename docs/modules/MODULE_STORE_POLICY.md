<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# 模块市场当前策略

## 2026-07-21 Flutter-first 现行策略

- Flutter 负责模块商店 UI、路由、搜索/筛选和 UI 偏好；Android `ModuleCoreFacade`/`ModuleManager` 负责全部可信业务状态。
- 功能开关 `ENABLE_FLUTTER_MODULE_STORE` 默认关闭；关闭或初始化失败时继续使用本文件后文描述的原生商店。
- Catalog V2 必须显式声明 `runtimeType`/`deliveryType`；正式非内置包在进入下载前必须映射到权威 `ModuleManifest`。
- 六类 Runtime 为 `flutter/web/asset/android/native_service/unity`。Flutter Runtime 只允许宿主已编译 route，不下载 Dart 源码。
- 客户端 Ed25519 注入、双 ABI Release、Android 11–15、签名 Catalog V8、正式模块包与生产灰度均已完成；stable vc595 已启用 Flutter 商店，源码默认值继续提供安全回退。
- 本文件后续 2026-05/07 的入口、分类和 VPS 描述属于旧商店/既有发布链，不能覆盖 `/docs/flutter-store/` 的当前状态。

更新日期�?026-05-24

## 展示入口

- 模块市场默认打开"游戏"分类
- 顶部分类不再提供"全部"选项，只保留游戏、浏览器、工具箱、AI 助手、VPN、错题本
- 游戏分类内也不显示"全部"子分类；默认展示全部游戏，可再切换益智、休闲、经典
- 模块市场右上角保留两个显式操作：刷新当前列表、进入已下载模块列表；最右侧为已下载列表按钮
- 已下载模块列表按全部、游戏、浏览器、工具箱、AI 助手、VPN、错题本分类过滤
- 模块卡片的下载/打开按钮下方显示"卸载"快捷按钮，仅对已下载模块显示
## 游戏大厅边界

- 新安装包的游戏大厅只展示五子棋和斗地主�?- 商店下载的游戏安装成功后必须动态注册回游戏大厅�?- 已安装商店游戏既可以从模块市场打开，也可以从游戏大厅打开�?- `GamesFragment.onResume()` 需要调�?`ModuleManager.loadModuleList()` �?`ModuleManager.registerInstalledGameModules()`，保证用户从商店返回大厅后立即看到新安装的游戏�?- 模块卸载后必须从 `GameRegistry` 动态移除，避免游戏大厅继续显示已删除游戏�?
## VPS 文件位置

- 模块清单本地源文件：`deploy/modules.json`
- APK 内置兜底清单：`app/src/main/assets/modules.json`
- VPS 实际服务目录：`/var/www/modules/modules/`
- VPS 兼容备份目录：`/var/www/update/modules/`
- Nginx �?`/modules/` 路由代理�?`127.0.0.1:9001`，该服务根目录是 `/var/www/modules`�?
## 发布要求

- 修改模块清单后，同时更新 `deploy/modules.json` �?`app/src/main/assets/modules.json`�?- 所有非 builtIn 模块必须有真实存在的下载文件，并保证 `fileSize`、`sha256` �?VPS 文件一致�?- 上传后至少校验：
  - `https://your-server.example.com/modules.json`
  - 一�?APK 模块，例�?`https://your-server.example.com/modules/feature_game2048_v100.apk`
  - 一个普通游戏模块，例如 `https://your-server.example.com/modules/game_sudoku_v100.zip`

## 2026-05-24 文档同步
- 游戏视觉美化：斗地主（径向渐变桌�?菱形花纹卡牌）、五子棋（木纹渐变棋�?3D棋子）、华容道（深色渐�?金色边框+脉冲动画）、中国象棋（木纹渐变+四角角标+楚河汉界波浪线）
- 中国象棋提示功能改进：棋盘可视化提示（蓝色脉冲光�?箭头指引）替代坐标文本，状态栏显示中文棋谱描述
- 华容道和中国象棋模块商店上架：创建独�?APK 模块（feature/games/klotski、feature/games/chinesechess），v2.0.0，已上传 VPS
- 底部导航切换闪退修复：创�?KeepStateNavigator 自定义导航器，使�?add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日�?- 内存泄漏全面修复：移�?WeakReference callback、Fragment 回调安全检查、视图引用彻底清�?- 压力测试通过：10轮快速Tab切换无崩溃

## 错题本模块（wrongbook）展示与预安装策略

更新日期：2026-07-06

### 模块信息

| 属性 | 值 |
|------|-----|
| 模块 ID | wrongbook |
| 模块商店分类 | wrongbook |
| 入口类 | `com.gamecenter.app.wrongbook.WrongBookModuleEntryPoint` |
| Feature Flag | `BuildConfig.ENABLE_WRONGBOOK`（默认 true） |
| 模块路径 | `module-store/feature/tools/wrongbook/` |
| 预装 APK | `app/src/main/assets/modules/feature_wrongbook_v100.apk` |
| 当前版本 | v1.0.0 |

### 展示策略

- 顶部分类新增"错题本"分类，与游戏、浏览器、工具箱、AI 助手、VPN 并列
- 已下载模块列表支持按"错题本"分类过滤
- 模块卡片提供下载/打开/卸载操作，与其他动态模块一致

### 预安装策略

- 错题本模块通过 `bundlePreinstalledModules` 任务预装进主 APK 的 `assets/modules/feature_wrongbook_v100.apk`
- 首次启动时宿主自动从 assets 解包并加载，实现免下载即用
- `MainActivity` 根据 `BuildConfig.ENABLE_WRONGBOOK` 与模块安装状态动态注册"错题本"导航 Tab
- Feature Flag 关闭时不注册导航 Tab，模块商店中仍可展示但不启用入口

### 数据存放

- 错题本 Room 数据库（`wrongbook.db`，version=2）存放在宿主 App 私有目录
- 模块卸载后数据保留，重新安装模块可恢复历史错题数据
