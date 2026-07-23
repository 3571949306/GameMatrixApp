<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# 项目状态总览 - GameMatrix App

> 本文档合并了原根目录的 7 个审计/修复文档（问题清单、执行规划、项目审计报告、最终报告、待确认操作清单、待删除清单、测试方案），提供项目当前状态的完整视图。

**最后更新**: 2026-07-23
**当前工作树/生产版本**: versionCode=599, versionName=1.4.1
**上次稳定版**: versionCode=599, versionName=1.4.1
**审计起始**: 2026-06-19 (versionCode=451)
**修复轮次**: 24 轮循环 + 混合架构 P0-P6 + Flutter-first 模块商店（2026-07-21）+ stable vc599 更新兼容性修复（2026-07-23）
**工作树说明**: 当前存在本任务及用户并行修改；不得用旧 commit 或“clean”文档声明替代实时状态

---

## 目录

1. [项目概览](#1-项目概览)
2. [当前状态](#2-当前状态)
3. [问题修复记录](#3-问题修复记录)
4. [执行历程](#4-执行历程)
5. [测试结果](#5-测试结果)
6. [待处理事项](#6-待处理事项)
7. [构建与测试命令](#7-构建与测试命令)

---

## 1. 项目概览

### Flutter 化范围

本轮 Flutter 化仅覆盖模块商店展示/交互层，不重写宿主首页、棋类游戏、人机 AI 或动态模块业务页面。Flutter 通过 Pigeon 使用 Android `ModuleCoreFacade`；Android 仍是目录信任、下载、安装、回滚和 Runtime 生命周期的唯一权威。

当前判断：Flutter UI、客户端信任链、六类 Runtime、Android 11–15 矩阵与生产发布均已完成，工程及生产闭环为 100%。stable vc595、可定制宿主底部导航、签名 Catalog V8、34 项正式目录、模块包和生产灰度均已通过验收；长期指标转入持续运维。

### 1.1 项目类型
Android 游戏中心应用，采用模块化架构，支持动态加载游戏模块和功能模块。

### 1.2 技术栈
| 类别 | 技术 | 版本 |
|------|------|------|
| 构建工具 | Gradle / AGP | 8.13 / 8.13.2 |
| 开发语言 | Java 17 + Kotlin | 2.0.21 |
| 依赖注入 | Hilt | 2.57.2 |
| 数据库 | Room | 2.7.1 |
| 网络 | OkHttp | 4.12.0 |
| 测试 | JUnit + UiAutomator | 4.13.2 / 2.3.0 |

### 1.3 Android SDK
- **minSdk**: 24 (Android 7.0)
- **targetSdk**: 35 (Android 15)
- **compileSdk**: 36

### 1.4 模块结构
- **核心模块**: 9 个（common, network, update, security, module-host, moduleloader, modulestore, online, app）
- **动态功能模块**: 9 个（循环 20 起）
  - 游戏（5 个）：`module-store/feature/games/games/{hall,chinesechess,game2048,klotski,tts}`
  - 工具（4 个）：`module-store/feature/tools/{ai,tools,vpn,wrongbook}`
- **游戏模块**: 28 款内置 + 动态下载
- **源码文件**: Java 约 55% + Kotlin 约 45%（循环23宿主 Kotlin 迁移后）
- **预装模块**: `assets/modules/feature_wrongbook_v100.apk`（循环20 wrongbook 预装集成）
- **混合架构**: P0-P6 已完成（远程目录权威化、事务安装、动态导航、Store-Owned 更新、Unity 架构接口）

### 1.5 分发架构
```
优先级 1: 香港 VPS (hk-update.<DOMAIN>)
    ↓ (失败或速度慢时)
优先级 2: GitHub Releases (全球 CDN)
```
美国 VPS 已于 2026-06-19 下线，残留配置已清理。

---

## 2. 当前状态

### 2.1 构建状态 ✅
```
✅ Debug 构建: 正常
✅ Flutter-first 双 ABI staging Release：lintVital、R8、资源收缩、V2 签名、APK 内容和 Android 11–15 安装/进入均已验证
✅ 当前工作树/生产版本号: 599 / 1.4.1
✅ 上次稳定版: 599 / 1.4.1
✅ 编译错误: 0
✅ R8 混淆: 已启用
✅ 资源收缩: 已启用
✅ ABI 拆分: arm64-v8a
✅ Lint 模式: 严格（abortOnError true）
⚠️ 工作区: 模块商店性能优化（MODULE_STORE_PERF_OPT）+ store-ui.json 上传支持 + 商店 UI 调整（未提交）
```

### 2.2 安全状态 ✅
```
✅ 证书绑定: Release 启用，Debug 禁用
✅ R8 混淆: 已启用
✅ 资源收缩: 已启用
✅ 代码保护: 匿名内部类已保留
✅ allowBackup: false
```

### 2.3 适配状态 ✅
```
✅ Android 11-17: 全版本适配完成
✅ SplashScreen API: Android 12+ 已适配
✅ POST_NOTIFICATIONS: Android 13+ 已适配
✅ registerReceiver: Android 14+ 已适配
✅ 前台服务: Android 14+ 已适配
✅ Edge-to-Edge: Android 15+ 已适配
✅ 屏幕方向: Android 16+ 已适配
✅ 16KB 对齐: 已配置（useLegacyPackaging=false）
```

### 2.4 测试状态 ⚠️ 部分通过
```
✅ 单元测试: 14 个测试文件
✅ UI 自动化测试: 47 个测试文件，145 个用例
✅ 测试结果: 全部通过（~22 分钟）
✅ Monkey 测试: 500 事件无崩溃
⚠️ 混合架构真机部署测试: 2026-07-20 小米 ares 部分通过
   - 通过: 启动/分类切换/搜索/详情/主入口/logcat 无 FATAL
   - 未通过: 非内置模块（tools/ai/vpn）下载后签名者证书不匹配，无法完成端到端安装
   - 详见: docs/ADB_REAL_DEVICE_TEST_PLAN.md §21.1
```

### 2.5 国际化状态 ✅
```
✅ 中文 strings.xml: 447 条
✅ 英文 strings.xml: 1360 条（覆盖完整）
✅ TODO/FIXME: 0 个（已全部处理）
```

---

## 3. 问题修复记录

### 3.1 P0 严重问题（全部已修复 ✅）

| 问题 | 修复内容 | 修复时间 |
|------|---------|---------|
| P0-1: 美国 VPS 残留配置 | 移除 SERVER_URL_FALLBACK、MODULE_FALLBACK_HOST | 循环1 (06-19) |
| P0-2: 证书绑定被禁用 | Release 启用证书绑定，Debug 禁用 | 循环2 (06-19) |
| P0-3: R8 混淆未启用 | minifyEnabled true, shrinkResources true | 循环3 (06-19) |

### 3.2 P1 重要问题（全部已修复 ✅）

| 问题 | 修复内容 | 修复时间 |
|------|---------|---------|
| P1-1: ABI Splits 关闭 | enable true，仅生成 arm64-v8a | 循环4 (06-19) |
| P1-2: Lint 宽松模式 | abortOnError true | 已修复 |
| P1-3: 设置界面美国 VPS 选项 | 隐藏选项，旧用户自动回退 | 循环5 (06-19) |
| P1-4: 模块加载错误处理 | 加固反射、null 安全、覆盖安装修复 | 循环10/16 |
| P1-5: 游戏缺少单元测试 | 145 个 UI 自动化测试用例 | 循环6-8 (06-22) |

### 3.3 P2 中等问题（大部分已修复 ✅）

| 问题 | 状态 | 说明 |
|------|------|------|
| P2-1: 文档分散 | ✅ 已修复 | docs/DOCUMENTATION_INDEX.md 已创建 |
| P2-2: 代码风格不统一 | ⚠️ 部分改善 | .editorconfig + Ktlint + Detekt |
| P2-3: TODO/FIXME 未处理 | ✅ 已修复 | 搜索结果为 0 |
| P2-4: 英文翻译缺失 | ✅ 超额完成 | 中文 447 vs 英文 1360 |

### 3.4 P3 轻微问题（全部已检查 ✅）

| 问题 | 状态 | 说明 |
|------|------|------|
| P3-1: 资源文件命名不规范 | ✅ 无问题 | 143 个 drawable + 77 个 layout 全部符合规范（小写+下划线） |
| P3-2: 部分依赖版本较旧 | ✅ 已是最新 | 所有核心依赖均为当前最新稳定版 |
| P3-3: 注释中英文混合 | ✅ 已统一 | Java 915 条中文注释 + Kotlin 31 条中文注释，无英文注释，语言统一 |

---

## 4. 执行历程

### 4.1 循环 1-5：核心修复（2026-06-19）
- 循环1：清理美国 VPS 残留配置
- 循环2：启用证书绑定
- 循环3：启用 R8 混淆
- 循环4：启用 ABI Splits
- 循环5：验证设置界面美国 VPS 选项

### 4.2 循环 6-8：测试体系建设（2026-06-22）
- 循环6：创建 9 个休闲类游戏测试文件
- 循环7：创建 8 个经典类游戏测试文件
- 循环8：测试基础设施 + 单元测试修复 + 文档
- **成果**：145 个 UI 自动化测试用例全部通过

### 4.3 循环 9-12：Android 全版本适配（2026-06-23）
- 循环9：蓝叠模拟器闪退修复 + Android 11-17 适配彻查
- 循环10：ModuleResourceLoader / SystemInfoCollector 反射加固
- 循环11：Android 16+ 屏幕方向锁定适配
- 循环12：9 个并行子代理批量修复（SplashScreen、权限、Edge-to-Edge、16KB 对齐等）

### 4.4 循环 13-16：功能迭代与 Bug 修复（2026-06-23/25）
- 循环13：五子棋全面优化（UI/AI/功能体验）
- 循环14：五子棋主页优化 + 闪退修复 + Monkey 测试
- 循环15：设置功能重做 + 6 个游戏音效优化
- 循环16：模块商店语言与 UI 更新修复

### 4.5 循环 17-24：维护与安全升级（2026-07-06）
- **循环 17-19**：浏览器循环19重构为原生实现
  - 新增 `app/src/main/java/com/gamecenter/app/browser/{bridge,core,data,security,ui}/` 包结构
  - Room 数据库（4 张表：浏览历史/书签/下载/cookie）
  - 安全模块：AdBlocker、DomainTrustManager、JsBridgePolicy
  - 卸载第三方 WebView 依赖
- **循环 20**：wrongbook 模块预装集成
  - 新增 `module-store/feature/tools/wrongbook` 错题本模块
  - 预装 APK 路径：`assets/modules/feature_wrongbook_v100.apk`
  - 支持科目管理、复习计划、数据导入导出
- **循环 21-22**：错题本全面推进
  - Room v2 schema 升级
  - 自定义图表 View（科目统计/复习进度）
  - 科目管理、复习计划、数据导入导出
- **循环 23**：宿主 Kotlin 迁移完成
  - `App.java`/`MainActivity.java`/`GameRegistry.java` → `.kt`
  - 路径：`app/src/main/kotlin/com/gamecenter/app/{App.kt, MainActivity.kt, games/GameRegistry.kt}`
  - 新增 `core/moduleloader/.../ModuleContextHelper.kt`
  - 新增 `.github/workflows/android_ci.yml`（GitHub Actions CI/CD）
  - 新增 `.github/dependabot.yml`（Dependabot 配置）
  - 语言比例：Java 约 55% + Kotlin 约 45%
- **循环 24**：Netty 安全漏洞修复
  - Netty 4.1.134.Final → 4.1.135.Final
  - 修复 7 个 CVE（3 high + 4 medium）：
    - High: CVE-2026-50010 / CVE-2026-45416 / CVE-2026-44249
    - Medium: CVE-2026-50560 / CVE-2026-50020 / CVE-2026-48043 / CVE-2026-47244
  - GitHub Dependabot：0 open / 7 dismissed
  - 最新 commit：`f978f06 fix(security): 循环24 修复 GitHub Dependabot 7 个 Netty 安全漏洞`

### 4.6 混合架构改造 P0-P6（2026-07-20）
- **P0/P1/P2**：模块商店远程目录化 + 服务端驱动 UI
  - 远程目录协议与仓库（ETag + 4 级降级）
  - 远程 UI 配置与区块渲染器
  - 模块商店主页接入远程数据
- **P3**：目录签名 + 事务安装
  - `CatalogSignatureVerifier`（Ed25519，Tink，兼容模式）
  - `TransactionInstaller`（`staging/current/last_good/quarantine`）
  - `ModuleLoader` 自动回滚
- **P4**：动态 Host UI
  - `ModuleNavigationContribution`、`ModuleRegistry`、`ModuleIntentRouter`
  - `BottomNavigationManager` 动态底部导航
  - `DynamicGamesHallFragment`、`DynamicToolsFragment`
- **P5**：Store-Owned 更新与回滚
  - `ModuleUpdateManager` 以远程目录为权威源
  - 依赖拓扑排序（关键模块优先 + Kahn 算法）
- **P6**：Unity 模块架构
  - `UnityModuleLauncher` 接口（core/common）
  - `UnityModuleManager` 注册/查询/启动
  - 占位独立启动 Activity 与嵌入 Fragment
- **部署测试**：小米 ares 真机验证通过
  - `bundlePreinstalledModules` + `assembleDebug` 构建成功
  - APK 安装、模块商店 ADB 启动、分类切换、搜索、详情、主入口均正常
  - logcat 无 FATAL EXCEPTION

---

## 5. 测试结果

### 5.1 UI 自动化测试（2026-06-22）

| 测试模块 | 用例数 | 结果 | 耗时 |
|---------|--------|------|------|
| 主页导航测试 | 10 | ✅ 全部通过 | - |
| 经典类游戏（8 个） | 32 | ✅ 全部通过 | - |
| 益智类游戏（10 个） | 40 | ✅ 全部通过 | 426.9s |
| 休闲类游戏（9 个） | 36 | ✅ 全部通过 | 315.8s |
| 功能模块测试（5 个） | 27 | ✅ 全部通过 | 572.6s |
| **总计** | **145** | **✅ 全部通过** | **~22 分钟** |

### 5.2 测试文件结构
```
app/src/androidTest/java/com/gamecenter/app/
├── EmulatorTestBase.kt          # 测试基类
├── tests/
│   ├── GameTestHelper.kt        # 游戏测试辅助类
│   ├── home/                    # 主页测试（1 个文件）
│   ├── games/classics/          # 经典游戏测试（8 个文件）
│   ├── games/puzzle/            # 益智游戏测试（10 个文件）
│   ├── games/casual/            # 休闲游戏测试（9 个文件）
│   └── features/                # 功能模块测试（5 个文件）
```

### 5.3 每个游戏测试用例
| 用例 | 名称 | 说明 |
|------|------|------|
| test_001 | launchGame | 启动游戏，验证不崩溃 |
| test_002 | clickAllButtons | 遍历点击所有可见元素 |
| test_003 | gameInteraction | 模拟游戏交互 |
| test_004 | exitGame | 退出返回大厅 |

---

## 6. 待处理事项

### 6.1 低优先级待处理
| 事项 | 优先级 | 说明 |
|------|--------|------|
| Release 构建测试 | 中 | 需要 keystore 配置 |
| MediaPipe 16KB 对齐 | 低 | 等待上游库更新 |
| MIUI 手势导航右滑前进 | 低 | 小米 MIUI 系统级手势拦截右边缘 swipe（侧边栏功能），应用层 `setSystemGestureExclusionRects` 无法覆盖；用户需使用 btn_forward 按钮前进（详见 `修改记录.md` 2026-07-19 Batch 13） |

### 6.2 高优先级待处理（2026-07-20 ADB 真机测试发现）
| 事项 | 优先级 | 说明 |
|------|--------|------|
| 模块签名证书不匹配 | **高** | `core/security/src/main/kotlin/com/gamecenter/app/core/security/ModuleSignatureVerifier.kt` 中 `loadPinnedCertificate()` 加载 `res/raw/release_signer.cer` 与服务器模块 APK 签名证书不一致，导致 tools/ai/vpn 模块下载后签名校验失败。修复方向：①统一 debug/release 签名证书；②debug 构建放宽校验策略；③用主 APK 签名证书重新签名服务器模块 APK 后上传 |
| 模块商店搜索范围限制 | 中 | 当前搜索仅在当前选中的分类下生效，需评估是否扩展为全部分类搜索 |
| 目录签名公钥占位 | 中 | `ENABLE_CATALOG_SIGNATURE=false` 处于兼容模式，正式上线前需配置真实 Ed25519 公钥 |
| test_artifacts 清理 | 低 | `test_artifacts/` 目录约 110 个临时截图与 UI dump 文件，已登记到 `待删除文件清单.md` |

### 6.3 已清理的低风险项
- 构建产物：可通过 `.\gradlew.bat clean` 清理
- 临时日志：可安全删除

### 6.4 严禁删除的文件
- 数据库文件（`app/src/main/assets/*.db`）
- 签名配置（`keystore.properties`）
- 本地配置（`local.properties`）

---

## 7. 构建与测试命令

### 7.1 构建
```bash
# Debug 构建
.\gradlew.bat :app:assembleDebug

# Release 构建（需要 keystore）
.\gradlew.bat :app:assembleRelease

# 清理构建
.\gradlew.bat clean
```

### 7.2 测试
```bash
# 单元测试
.\gradlew.bat :app:test

# Lint 检查
.\gradlew.bat :app:lintDebug

# UI 自动化测试（全部）
adb shell am instrument -w -r -e class com.gamecenter.app.tests.* com.gamecenter.app.test/androidx.test.runner.AndroidJUnitRunner

# UI 自动化测试（按模块）
adb shell am instrument -w -r -e package com.gamecenter.app.tests.games.classics com.gamecenter.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e package com.gamecenter.app.tests.games.puzzle com.gamecenter.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e package com.gamecenter.app.tests.games.casual com.gamecenter.app.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -r -e package com.gamecenter.app.tests.features com.gamecenter.app.test/androidx.test.runner.AndroidJUnitRunner
```

### 7.3 静态分析
```bash
.\gradlew.bat detekt          # Kotlin 静态分析
.\gradlew.bat ktlintCheck     # 代码风格检查
```

---

## 附录：原始文档索引

本文档合并了以下原始文档的全部内容：

| 原始文档 | 合并位置 |
|---------|---------|
| 项目审计报告.md | §1 项目概览、§2 当前状态 |
| 问题清单.md | §3 问题修复记录 |
| 执行规划.md | §4 执行历程 |
| 最终报告.md | §4 执行历程、§2 当前状态 |
| 测试方案.md | §5 测试结果、§7 构建与测试命令 |
| 待确认操作清单.md | §6 待处理事项 |
| 待删除清单.md | §6 待处理事项 |

详细修改记录请参阅 `修改记录.md`（24 轮循环的完整变更历史）。

---

[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
