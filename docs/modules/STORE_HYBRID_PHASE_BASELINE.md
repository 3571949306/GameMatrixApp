# 模块商店混合架构改造 - 基线文档（P0）

> 本文档记录混合架构改造（P0/P1/P2）开始前的基线状态，用于回滚对照和验收基线比较。
> 创建时间：2026-07-20
> 创建分支：`feature/hybrid-module-store-phase1`（本地分支，不推送远端）

## 1. 版本基线

| 项 | 值 |
|---|---|
| 起始 commit | `877ecfa1c1fe17a74b4b78918b3c4628b06c9e59` |
| 起始 commit message | `feat(batch21): 模块商店改进 + 浏览器增强 + 错题本完善 + 下载基础设施` |
| 分支名 | `feature/hybrid-module-store-phase1` |
| versionCode | 589 |
| versionName | 1.4.1 |
| lastStableVersionCode | 465 |
| lastStableVersionName | 1.4.0 |
| AGP | 8.13.2 |
| Gradle | 8.13 |
| Kotlin | 2.0.21 |
| JDK | 17 |
| minSdk | 24 |
| targetSdk | 35 |
| compileSdk | 35 |
| ABI splits | arm64-v8a |

## 2. 模块基线

| 项 | 值 |
|---|---|
| assets/modules.json version | 21 |
| 模块总数 | 34（7 nav + 27 game） |
| 内置模块（builtIn=true） | 28（games_hall + browser + 26 个游戏） |
| 可下载模块（builtIn=false） | 6（tools / ai / wrongbook / tts_voice / vpn + 实际 wrongbook 已预装到 assets） |
| 商店分类数 | 6（硬编码：游戏/浏览器/工具箱/AI助手/VPN/已安装） |
| 子分类数 | 4（硬编码：全部/益智/休闲/经典，仅"游戏"分类显示） |
| 真机已安装模块数 | 30（含内置） |
| 商店"有更新"数 | 0 |

### modules.json 字段（24 个）

```
id, name, description, versionName, versionCode,
entryClass, type, activityClass,
fileName, fileSize, sha256, downloadUrl, fallbackUrl, githubUrl, iconUrl,
category, storeCategory, gameId, gameCategory, gameDesc,
minAppVersion, minAppVersionCode, depends,
builtIn, required, isBaseFramework, builtInVersionCode
```

### 已识别的设计债务

1. **`schemaVersion` 字段缺失**：顶层只有 `version` 字段表示目录版本号
2. **`categories` 数组缺失**：分类完全依赖 `ModuleStoreActivity.CATEGORIES` 硬编码
3. **`heroBanners` 数组缺失**：Banner 由 `ModuleStoreActivity.updateHeroBanner()` 动态计算
4. **`minAppVersionCode` 和 `required` 字段存在但 `ModuleManifest.fromJson()` 不解析**
5. **`ModuleManifest.fromJson()` 硬编码覆盖 `name`/`description`**：按 `id` 查本地映射表，命中即丢弃服务器值
6. **`iconUrl` 字段全程为空字符串**：图标完全靠本地 drawable `ic_<gameId>` 三级回退
7. **`storeCategory="wrongbook"` 和 `storeCategory="voice"` 在商店无对应 Tab**：只能通过搜索找到
8. **缺少新字段**：`shortDescription` / `screenshots` / `changelog` / `permissionsDescription` / `tags` / `sortOrder` / `featured` / `enabled`

## 3. 核心文件基线

### 3.1 ModuleStoreActivity.kt

- 路径：`app/src/main/java/com/gamecenter/app/modules/ModuleStoreActivity.kt`
- 行数：1154
- 主要职责（混合）：
  - 6 个硬编码分类 Tab + 子分类 Chip
  - Hero Banner 动态计算（最多 5 张，从未安装/有更新/已安装三组中筛选）
  - 三栏统计（总数 / 已安装 / 有更新）+ 一键更新
  - 搜索（防抖 300ms）+ 搜索历史（最多 5 条，SharedPreferences）
  - 筛选（多维：内置/已安装/有更新/可下载）
  - 排序（名称/版本/大小/下载量）
  - 卡片点击 → BottomSheet 详情
  - 按钮点击 → 下载/打开/卸载/启用
  - 直接调用 `ModuleManager.loadModuleList()` / `downloadModule()` / `isModuleInstalled()`

### 3.2 ModuleManager.kt

- 路径：`app/src/main/java/com/gamecenter/app/modules/ModuleManager.kt`
- 行数：544
- 类型：`object` 单例
- 主要职责（严重混合）：
  - 远程模块清单获取（OkHttp + ETag/304 + 版本对比 + 缓存写盘）
  - 本地缓存管理（SharedPreferences：installed_modules / module_version_ / modules_list_json / modules_list_etag / modules_list_version）
  - JSON 解析（parseModulesArray / parseModuleListVersion，兼容新旧格式）
  - 下载编排（委托 ModuleDownloader，转发回调）
  - 安装状态管理（markModuleInstalled / removeInstalledModule / isModuleInstalled / getInstalledVersionCode）
  - 加载委托（loadModule / startModule / unloadModule / getLoadedFeature，全部委托 ModuleLoader）
  - 游戏注册（registerInstalledGameModules / registerGameFromManifest，委托 GameRegistry）
  - 硬编码 VPN 兜底（registerLocalFallbackIfNeeded 中 VPN 模块的字段被硬编码）

### 3.3 ModuleDownloader.kt（主用）

- 路径：`app/src/main/java/com/gamecenter/app/modules/ModuleDownloader.kt`
- 行数：467
- 类型：`object` 单例，回调风格
- 关键能力（必须复用）：
  - 多源下载（downloadUrl + fallbackUrl + githubUrl + 自动 CDN fallback）
  - HTTPS 强制 + 断点续传（Range header）
  - 每个 URL 重试 2 次 + 线性退避（1s/2s）
  - SHA-256 校验（拒绝空 SHA）+ APK 签名校验（ModuleSignatureVerifier）
  - 下载指标收集（DownloadMetricsCollector）
  - 取消支持（activeDownloads[moduleId] = false）

### 3.4 ModuleLoader.kt（主用）

- 路径：`app/src/main/java/com/gamecenter/app/modules/ModuleLoader.kt`
- 行数：240
- 类型：`object` 单例
- 关键能力：
  - DexClassLoader + APK 签名 + SHA-256 + DEX magic 双重校验
  - 内置模块走 `loadBuiltInModule`（context.classLoader 反射）
  - 外置模块走 `DexClassLoader` + `ModuleResourceLoader`
  - 版本变更时自动 unload + reload

### 3.5 ModuleVerifier.kt（主用）

- 路径：`app/src/main/java/com/gamecenter/app/modules/ModuleVerifier.kt`
- 行数：75
- 关键能力：`computeSha256` / `verifySha256(file, expected, allowEmpty)` / `verifyDexFile`

### 3.6 ModuleManifest.kt（主用）

- 路径：`app/src/main/java/com/gamecenter/app/modules/ModuleManifest.kt`
- 行数：197
- 字段数：24
- 关键问题：`fromJson()` 硬编码覆盖 `name`/`description`，丢弃 `minAppVersionCode`/`required`

### 3.7 ModuleDetailBottomSheet.kt

- 路径：`app/src/main/java/com/gamecenter/app/modules/ModuleDetailBottomSheet.kt`
- 行数：317
- 关键能力：
  - 截图轮播（ModuleScreenshotAdapter，feature flag `MODULE_STORE_DETAIL_ENHANCE`）
  - 更新日志（按分类 mock 生成）
  - 权限说明（按分类动态生成：网络/存储/通知）

### 3.8 RecoveryActivity.kt

- 路径：`app/src/main/java/com/gamecenter/app/recovery/RecoveryActivity.kt`
- 行数：248
- 独立链路：使用 RecoveryDownloader / RecoveryInstaller / RecoveryVerifier，**不与模块商店共享下载器**

## 4. P0 编译与测试基线

### 4.1 编译

| 命令 | 结果 | 耗时 |
|---|---|---|
| `.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace` | ✅ BUILD SUCCESSFUL | 49s |
| `.\gradlew.bat :app:testDebugUnitTest -PautoBumpVersion=false` | ✅ BUILD SUCCESSFUL | 1m 2s |
| `.\gradlew.bat :app:lintDebug -PautoBumpVersion=false` | ✅ BUILD SUCCESSFUL | 1m 51s |

### 4.2 Lint 摘要

- 167 warnings + 1 error + 403 baseline filtered（lint-baseline.xml 已存在）
- 13 个 baseline 中记录的问题已修复（不再触发）
- lint 不阻断构建（abortOnError=false，见 DONT_DO_THIS.md）

### 4.3 真机基线验证（小米 ares M2012K10C）

设备 serial：`w4dm4dssby7xcunv`

| 验证项 | 结果 |
|---|---|
| APK 安装（`adb install -r -d`） | ✅ Success |
| 应用启动（monkey LAUNCHER） | ✅ MainActivity focused |
| 模块商店启动（ProfileFragment → btn_profile_module_store） | ✅ ModuleStoreActivity focused |
| 顶栏标题 | ✅ "模块商店" |
| Hero Banner | ✅ "最新上架" + "错题本" + "立即打开" |
| 统计栏 | ✅ 总模块 34（↑ 共 34 个）/ 已安装 30（88% 已装）/ 有更新 0 |
| 搜索栏 | ✅ "搜索模块/游戏" + 搜索历史 "2048" |
| 分类 Tab（6 个） | ✅ 游戏/浏览器/工具箱/AI助手/VPN/已安装 |
| 子分类（4 个） | ✅ 全部/益智/休闲/经典 |
| 列表首项 | ✅ "游戏大厅" + "内置" + "v1.0.0" |
| 详情弹窗（点击 tts_voice 卡片） | ✅ 显示：语音朗读 v1.0.1 / 模块截图 01 02 03 / 模块介绍 / 信息 / 最新版本 1.0.1 (101) / 文件大小 821.5 KB / 分类（fallback "游戏"）/ 当前状态 未安装 / 更新日志 / 权限说明 / 操作 |
| logcat FATAL EXCEPTION | ✅ 无 |

### 4.4 真机基线发现的问题（不修复，记录留档）

1. **tts_voice 详情弹窗"分类"显示"游戏"**：因为 `storeCategory="voice"` 不在硬编码 6 个分类中，`ModuleDetailBottomSheet` 用了 fallback 逻辑显示首分类
2. **截图是 mock 数据**：`ModuleScreenshotAdapter` 用 `module.id.hashCode() % 3` 生成 3 张占位（渐变背景 + 模块图标 + 序号 01/02/03）
3. **更新日志按分类 mock 生成**：不是从 manifest 读取
4. **权限说明按分类动态生成**：不是从 manifest 读取

## 5. P0 风险与隔离

### 5.1 不允许的操作（来自 AGENTS.md 和用户规则）

- 不得修改签名文件 `release-key.jks` / `keystore.properties`
- 不得提交密钥 / token / 密码
- 不得删除现有模块 APK
- 不得重置数据库
- 不得改变正式服务器地址
- 不得关闭现有安全校验
- 不得为了编译通过而删除测试
- 不得用空实现替代现有功能
- 不得大规模重写浏览器、错题本或游戏中心代码

### 5.2 已识别的孤立/重复实现

详见 `docs/FILES_TO_REVIEW_HYBRID_STORE.md`（P0.4 产物）。本轮**不删除**任何文件，仅记录。

## 6. 验收基线（用于回归对照）

完成 P1/P2 后必须仍满足：

- 应用启动正常，logcat 无 FATAL EXCEPTION
- 模块商店显示 34 个模块，统计栏数字正确
- 6 个分类 Tab 显示正常
- Hero Banner 显示正常
- 搜索 / 搜索历史 / 筛选 / 排序 功能正常
- 详情弹窗显示截图 / 更新日志 / 权限说明
- 模块下载、安装、卸载、打开 功能正常
- 已安装模块管理（InstalledModulesActivity）正常
- RecoveryActivity 启动正常（不共享下载器）
- 浏览器 / 错题本 / 工具箱 / AI助手 / VPN / TTS 模块可正常打开
- 内置 27 个游戏可正常启动
- :app:assembleDebug 编译成功
- :app:testDebugUnitTest 通过
- :app:lintDebug 不引入新的 error
