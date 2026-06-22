# GameMatrix App

[![Android](https://img.shields.io/badge/Android-API%2024%2B-green?logo=android)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.4.1-blue)](CHANGELOG.md)

一个集成模块商店，支持按需扩展小游戏、工具箱、浏览器、AI 助手和科学上网（VPN）的 Android 游戏中心，支持单机 AI、局域网联机和云联机对战。

An Android game center with a modular marketplace for on-demand expansion of games, tools, browser, AI assistant, and VPN, supporting single-player AI, LAN multiplayer, and cloud multiplayer.

---

## 2026-06-22 v1.4.1 更新逻辑优化版

### 🚀 更新逻辑优化
- **OptimizedUpdateManager**：新增缓存、重试、MD5预检查
- **超时时间优化**：主源 2s/3s（原 3s/5s），备用 5s/15s（原 15s/30s）
- **速度阈值降低**：30KB/s（原 50KB/s）
- **占位符URL检测**：自动跳过无效URL（避免卡住）
- **本地APK预检查**：MD5匹配时跳过下载

### 📦 版本信息
- `versionCode`: 466
- `versionName`: 1.4.1
- 包名: `com.gamecenter.app`
- 分发: HK VPS（主）+ GitHub Releases（备）

---

## 2026-06-21 v1.4.0 正式版发布

### 🎮 内嵌所有游戏
- **28个游戏内置到主app**：无需下载即可使用，开箱即用
- 保留模块市场更新能力：内置游戏可通过模块市场检查更新
- 游戏分类：经典（8款）、益智（10款）、休闲（9款）

### ⚡ 线程架构优化
- **总线程数：~75 → ~17（-77%）**
- OkHttp线程池：64 → 8（-87%）
- 新增统一线程管理器 `AppExecutors`（IO/Compute/AI/Background）
- 为未来融合计划和协程迁移预留架构

### 🎨 UI优化
- **华容道UI升级**：渐变色方块、阴影效果、动画过渡
- **难度选择面板优化**：休闲游戏（2048、贪吃蛇等）不再显示难度选择，直接启动
- 只有有AI对手的游戏（五子棋、象棋、斗地主等）才显示难度选择

### 🔧 关键修复
- 修复 `BaseGameActivity` 类缺失导致的崩溃
- 修复 `GameStartDialog` 类缺失导致的游戏点击崩溃
- 修复游戏图标显示问题（28个游戏图标全部注册）
- 修复 modules.json 中 builtIn 标记不一致问题

### 📦 版本信息
- `versionCode`: 465
- `versionName`: 1.4.0
- 包名: `com.gamecenter.app`
- Gradle 工具链: AGP 8.13.2, Kotlin 2.0.21, Hilt 2.57.2

### 🧪 自动化测试
- 新增自动化测试框架（50+测试用例）
- 支持ADB连接模拟器进行功能测试
- 自动生成HTML/JSON测试报告

---

## 2026-05-26 Maintenance Snapshot

- **Module Store Expansion**: Upgraded `modules.json` from version 10 to version 11. Added 23 new game modules (blackjack, breakout, brotato, checkers, dice, flappy, go, guess, knife, match, memory, minesweeper, pipeline, plane, reaction, rock, snake, sokoban, sudoku, tetris, tic, tiles, whack), bringing the total to 29 game modules and 33 modules overall.
- **VPS Deployment**: All game ZIP files uploaded to VPS at `/var/www/modules/` and `/var/www/update/modules/`.
- **Compatibility Fixes**: Certificate pinning temporarily disabled in `SecureOkHttpFactory` for emulator SIGSEGV compatibility. R8 minify disabled for debug builds.
- **Bug Fixes**: `games_hall` module corrected to show as `builtIn` in the initial APK. Module download SHA-256 verification fixed -- all VPS files now match `modules.json`.
- **Version info**: `modules.json` version 11.

## 2026-05-29 Documentation Sync

- **文档版本同步**: 更新6个核心文档（README.md, PROJECT_CONTEXT.md, CHANGELOG.md, AI_ONBOARDING.md, 项目改进建议书.md, game_center_app_ai_roadmap.md）使版本号与 `version.properties` 同步
- **版本信息**: `versionCode=400`, `versionName=1.4.0`, 包名 `com.gamecenter.app`
- **Gradle 工具链**: AGP 8.13.2, Kotlin 2.0.21, Hilt 2.57.2
- **路线图更新**: 标记阶段5（本地模型）为已完成
- **测试覆盖核实**: 11个测试文件，100+个测试用例

## 2026-05-27 Maintenance Snapshot

- **版本号更新**: `versionCode` 从 343 升至 400，`versionName` 保持 `1.4.0`。
- **Gradle 工具链升级**: AGP 从 8.7.3 升级至 8.13.2，Kotlin 保持 2.0.21，Hilt 升级至 2.57.2。
- **模块系统增强**: 模块加载框架持续优化，支持更多游戏模块的动态加载。

## 2026-05-25 Maintenance Snapshot

- **Modularization and Dynamic Loading**: Separated Chinese Chess (`chinesechess`) and Klotski (`klotski`) games into standalone dynamic APK modules. Implemented `ModuleResourceLoader` in `ModuleLoader` to load assets and drawables inside dynamic modules, fixing crashes caused by local dynamic resource inflation.
- **Built-In Architecture Fixes**: Corrected the `builtIn` flag design flaw where browser, tools, and AI modules were hardcoded as built-in and couldn't be dynamically downloaded or disabled. They are now standard dynamic APK modules (`builtIn: false`) fully served from modules store.
- **Module Store Search & Directory Layout**: Added real-time module filtering via `etModuleSearch` inside `ModuleStoreActivity`. Restructured module repository directories: created root folder `模块商店/` to house game ZIP packages (`压缩模块/`) and standalone modules codebase (`功能模块/`), and registered updated paths in `settings.gradle`.
- **One-Click Deploy & ADB Setup**: Synced 8 module APKs and updated `modules.json` (Version 8) to VPS using Python script, and set up `install_app.ps1` to detect emulator devices and launch the host app automatically.
- **Module Framework Overhaul**: Fixed three critical module store issues: (1) SHA-256 verification errors after update — added pre-download cleanup of old files and temp files, try-finally resource leak fix in `ModuleVerifier`; (2) modules won't open after download — `ModuleLoader` now version-aware, auto-unloads old instances and clears DEX optimization cache before reloading; (3) store UI doesn't reflect update — `ModuleAdapter` now shows orange "Update" button with version diff when installed version is behind remote, `ModuleStoreActivity` tracks installed versions in real-time.
- **Version info**: `versionCode 343`, `versionName 1.4.0`.

## 2026-05-21 Maintenance Snapshot

- Formal release target: `v1.3.29` / `versionCode 267`.
- Current beta release: `v1.3.30-beta.1` / `versionCode 268`.
- Main UI now applies system bar insets so top text is no longer covered by phone status bars.
- AI local model selection persists full local model metadata and routes downloaded local LLM files without incorrectly requiring a cloud model.
- Gomoku and Chinese Chess difficulty controls are now direct buttons with four profiles: low, medium, high, master. Medium uses a lower search budget than before.
- Gomoku and Chinese Chess in-game controls are arranged in two rows so hint, undo, restart, and tutorial remain visible on narrow screens.
- Update module now downloads APK to public Download directory by default, shows download speed in notification, and adds cancel button.

- GitHub remote branch policy: keep `main` as the only maintained branch.
- Dependabot open alerts: `0` after dependency security constraints were applied in the root Gradle build.
- Local GitHub upload path: Git is configured to use the local v2rayN/xray HTTP proxy `http://127.0.0.1:10808` only for `https://github.com`, so `git push` does not require xray TUN/virtual adapter mode.
- Local network recovery guide: see `docs/LOCAL_GITHUB_NETWORK.md`; helper script: `tools/network/Configure-GitHubProxy.ps1`.

## Documentation Map

- Primary doc index: `docs/DOCUMENTATION_INDEX.md`
- Maintainer handoff and repo constraints: `PROJECT_CONTEXT.md`
- Code structure reference: `CODE_WIKI.md`
- Ongoing maintenance and governance notes: `项目改进建议书.md`
- Publishing guide: `docs/PUBLISH_GUIDE.md`
- Archived historical docs: `docs/archive/`

---

## 快速导航 / Quick Navigation

- 🎮 [功能列表](#功能列表--feature-list) — 全部游戏列表与联机支持
- 🏗 [技术架构](#技术架构--tech-stack) — 开发环境与依赖
- 🌐 [更新分发架构](#更新分发架构--update-distribution-architecture) — 两级下载源 + 自动换源
- 🕹 [联机架构](#联机架构--multiplayer-architecture) — 多游戏云联机支持
- 📁 [目录结构](#目录结构--directory-structure) — 项目文件组织
- 🛠 [构建与部署](#构建与部署--build--deployment) — 编译、打包、发布
- 📋 [更新日志](#更新日志--changelog) — 版本历史记录

---

## 功能列表 / Feature List

### 🛒 模块商店 / Module Store
- **按需扩展**：宿主包内置了经典小游戏入口，其他游戏（ZIP 格式）以及各种核心功能模块（浏览器、工具箱、AI、VPN）均通过模块商店进行动态下载安装，极大地减小了初始 APK 体积。模块商店现包含 29 款游戏模块和 4 个功能模块，共 33 个可下载模块。
- **动态插件机制**：对于 Browser, Tools, AI, VPN, Chinese Chess, Klotski, Knife，通过将其设置为 `builtIn: false` 的动态 APK 插件，运行时通过 `DexClassLoader` 动态装载并使用反射或 Hook 对资源进行重装载，防止内置依赖导致的资源冲突与崩溃。
- **实时搜索框**：在 `ModuleStoreActivity` 顶部增加了按关键词快速搜索模块的过滤机制。
- **卸载与更新**：提供已下载模块管理入口，支持快捷卸载 and 秒级热更新。

### 🎲 经典游戏（内置） / Classics (Built-in)

| 游戏 | 单机 AI | 局域网 | 云联机 |
|------|:-------:|:------:|:------:|
| 五子棋 Gomoku | ✅ | ❌ | ✅ WebSocket |
| 围棋 Go | ✅ | ❌ | ✅ WebSocket |
| 贪吃蛇 Snake | ✅ | ❌ | ❌ |
| 俄罗斯方块 Tetris | ✅ | ❌ | ❌ |
| 斗地主 DouDiZhu | ✅ | ✅ | ✅ WebSocket |
| Brotato | ✅ | ❌ | ❌ |

### 🧩 扩展游戏与独立功能模块（市场下载） / Market Downloads

| 模块名 | 模块类型 | 加载格式 | 动态注册大厅/Tab |
|------|:-------:|:------:|:------:|
| 中国象棋 Chinese Chess | 独立功能模块 (APK) | APK 动态插件 | ✅ 注册回大厅且支持云联机 |
| 华容道 Klotski | 独立功能模块 (APK) | APK 动态插件 | ✅ 注册回大厅 |
| 飞刀大师 Knife | 独立功能模块 (APK) | APK 动态插件 | ✅ 注册回大厅 |
| 浏览器 Browser | 独立功能模块 (APK) | APK 动态插件 | ✅ 启用后新增“浏览器”导航 Tab |
| 工具箱 Tools | 独立功能模块 (APK) | APK 动态插件 | ✅ 启用后新增“工具箱”导航 Tab |
| AI 智能助手 AI | 独立功能模块 (APK) | APK 动态插件 | ✅ 启用后新增“AI助手”导航 Tab |
| 科学上网 VPN | 独立功能模块 (APK) | APK 动态插件 | ✅ 启用后提供全局 VPN 服务支持 |
| 2048 等 23 款益智/休闲游戏 | 扩展小游戏 (ZIP) | ZIP 压缩资源包 | ✅ 自动解压并加载注册至大厅 |

> **联机说明 / Multiplayer Note**：**斗地主、五子棋、围棋、中国象棋、石头剪刀布** 均支持 WebSocket 云联机对战，联机游戏支持内联聊天功能。其余游戏均为单机模式。

### 🛠 工具箱 / Tools

26+ 实用工具，包括：
- 网络体检、DNS 查询、局域网设备扫描、端口扫描
- 二维码生成与识别（支持 WiFi/名片/图片）
- 编码/解码（URL/Base64）、JSON 格式化、时间戳转换
- 文件哈希计算（MD5/SHA-1/SHA-256）
- 颜色取色器（支持 WCAG 对比度检测）
- 诊断报告导出、电池信息、设备信息
- **AI 智能助手**（文本总结、翻译、润色、问答、代码解释；支持 DeepSeek、OpenAI、阿里云通义、硅基流动、智谱 AI、零一万物、月之暗面 Kimi 等模型；本地模型按低端/中端/高端机分档，云端输出上限提升到 2048-4096 tokens）

### ⚙️ 通用设置 / General Settings

- **音效与震动**：设置中可开关音效和震动反馈
- **测试版更新**：可选择是否接收 Beta 版本更新通知
- **权限管理**：首次启动时展示权限使用说明对话框（位置、相机、存储权限），支持在设置中随时查看和管理权限

---

## 技术架构 / Tech Stack

### 开发环境 / Development Environment

| 项目 | 版本 |
|------|------|
| 开发语言 | Java 17 + Kotlin |
| 最低 Android 版本 | API 24 (Android 7.0) |
| 目标 SDK | API 35 (Android 15) |
| 编译 SDK | API 35 |
| Gradle 插件 | 8.x |

### 主要依赖 / Dependencies

| 库 | 版本 | 用途 |
|----|------|------|
| androidx.appcompat | 1.7.1 | AppCompat 基础支持 |
| com.google.android.material | 1.12.0 | Material Design 组件（含 MaterialCardView） |
| androidx.constraintlayout | 2.2.1 | ConstraintLayout 布局 |
| androidx.recyclerview | 1.4.0 | 游戏列表 RecyclerView |
| com.google.code.gson | 2.11.0 | JSON 序列化/反序列化 |
| com.google.zxing:core | 3.5.3 | 二维码生成与识别 |
| com.squareup.okhttp3:okhttp | 4.12.0 | WebSocket 客户端 |
| com.github.bumptech.glide:glide | 4.16.0 | 图片懒加载与缓存 |
| junit:junit | 4.13.2 | 单元测试 |

> **已移除依赖**：`androidx.cardview:cardview:1.0.0` — 已由 `MaterialCardView`（来自 Material Components）替代。

---

## 更新分发架构 / Update Distribution Architecture

### 两级下载源 / Two-Level Download Sources

App 下载更新时自动尝试以下下载源，优先级从高到低：

```
┌─────────┐  优先级 1  ┌──────────────────┐
│   App   │ ─────────► │  香港 VPS         │
│         │            │  hk-update       │
│         │  优先级 2  ┌┴─────────────────┐│
│         │ ─────────► │  GitHub Releases ││
│         │            │  (全球 CDN)       ││
└─────────┘            └──────────────────┘┘
```

> **2026-06-19 变更**：美国 VPS 已下线，分发渠道精简为两级（香港 VPS → GitHub Releases）。

### VPS 职责划分 / VPS Responsibility

| VPS / 源 | 主要职责 | 说明 |
|-----|----------|------|
| **香港 VPS** | 更新服务 + 游戏联机 | 主更新源、WebSocket Relay、HTTP Relay、反馈服务 |
| **GitHub Releases** | 备用更新源 | 全球 CDN，作为香港 VPS 的备用下载源 |

### 自动换源机制 / Auto-Switch Mechanism

- **速度检测**：下载开始后 3 秒检测下载速度
- **换源阈值**：低于 50 KB/s 自动切换到下一个下载源
- **无缝切换**：切换时自动删除不完整的临时文件

### 版本分发策略 / Version Distribution Strategy

| 版本类型 | 上传目标 | 说明 |
|----------|----------|------|
| **Beta 测试版** | 香港 VPS | 仅供开启"接收测试版"的用户下载 |
| **Stable 正式版** | 香港 VPS + GitHub Releases | 所有用户均可下载 |

### 双版本分发架构 / Dual Version Distribution Architecture

#### VPS 文件结构

VPS 上同时维护两个通道的文件，互不覆盖：

```
/var/www/update/app/
├── app-beta.apk         # 测试版安装包
├── version-beta.json     # 测试版元数据
├── app-release.apk      # 正式版安装包
└── version-release.json  # 正式版元数据
```

#### APP 更新逻辑

**用户开启"接收测试版"**：
1. APP 检查 `/version-beta.json`
2. 如果有更高版本 → 提供更新
3. 否则检查 `/version-release.json`

**用户关闭"接收测试版"**：
1. APP 只检查 `/version-release.json`
2. 不显示测试版更新提示
3. 如果检测到有更新的测试版，会提示用户开启测试版以获取更新

**旧版 APP 兼容性**：
- 使用 `/api/update/check` 旧 API
- 服务器端自动比较 `versionCode`
- 只要 `versionCode` 更低 → 提示更新

#### 服务器端 API

| 端点 | 用途 |
|------|------|
| `/version-beta.json` | 测试版元数据 |
| `/version-release.json` | 正式版元数据 |
| `/api/update/check` | 旧版 API（兼容旧 APP） |
| `/app-beta.apk` | 测试版安装包 |
| `/app-release.apk` | 正式版安装包 |

---

## 性能优化 / Performance Optimization

### R8/ProGuard 代码混淆 / Code Obfuscation

- **代码混淆优化**：启用 R8 代码混淆，APK 体积减小约 30%
- **资源压缩**：自动移除未使用的代码和资源
- **规则配置**：针对游戏模块和网络库定制混淆规则

### 资源优化 / Resource Optimization

- **删除重复音频资源**：移除 `res/raw/doudizhu_archive/` 目录下 96 个重复文件
- **依赖清理**：移除未使用的 `androidx.webkit` 依赖

### 代码重构 / Code Refactoring

- **斗地主联机核心逻辑拆分**：将斗地主联机代码拆分为 6 个独立管理器类
  - `DouDiZhuUIController`：UI 控制器
  - `DouDiZhuRuleEngine`：规则引擎
  - `DouDiZhuAIHelper`：AI 辅助
  - `DouDiZhuNetworkHandler`：网络处理
  - `DouDiZhuSeatManager`：座位管理
  - `DouDiZhuSyncManager`：状态同步
- **斗地主农民 AI 增强**：联机 AI 决策会读取地主座位、上次出牌者、队友与地主剩余牌数，农民默认不压队友，地主临近跑完时才启用更强拦截。
- **斗地主语音音效增强**：出牌、叫地主、不出、炸弹、火箭、飞机、顺子、连对等事件按座位复用男女声与多段 pass 素材。
- **棋类单机提示增强**：五子棋、围棋、中国象棋单机人机模式新增提示按钮，复用当前 AI/规则评估给玩家推荐下一手。
- **围棋终局判定增强**：围棋连续虚手后会按吃子、地盘与 6.5 贴目计算胜负，并在状态栏与终局遮罩显示比分。
- **UpdateViewModel 替代 UpdatePresenter**：@HiltViewModel + LiveData，生命周期安全；协程化（viewModelScope + suspendCancellableCoroutine），CheckResult/DownloadResult 密封类替代布尔标志
- **第一阶段模块化落地**：新增 `:core:common`、`:core:network`、`:core:update`，先把通用设置/结果类型、基础网络、更新子系统从 `:app` 壳层拆出
- **OnlineRoomManager 组合式复用**：替代 BaseOnlineActivity 继承，各游戏通过组合复用联机逻辑
- **GameRegistry 双轨注册**：静态硬编码 + @GameEntry 注解 + register() 动态注册
- **@Inject 构造函数迁移**：SettingsManager/OkHttpClientProvider/UpdateManager/SaveManager
- **统一错误模型**：AppError（密封类）+ NetworkResult（类型安全结果封装）
- **类型安全枚举**：TaskStatus 替代 AiTask.status 字符串，AiErrorCode 替代 AiResult.errorCode 裸字符串
- **空 catch 块修复**：16 处空 catch 块已补日志记录
- **国际化推进**：OnlineRoomManager + AppSettingsDialog 硬编码文案提取到 strings.xml（48 个资源）
- **Java/Kotlin 混合边界规范**：CODE_WIKI.md 新增第 10 章
- **安全加固**：`allowBackup=false`，新增 `backup_rules.xml` 和 `data_extraction_rules.xml`，存储权限迁移（`READ_MEDIA_IMAGES`、`maxSdkVersion` 限制）
- **MaterialCardView 替代 CardView**：移除 `androidx.cardview:cardview:1.0.0` 依赖

### 图片加载优化 / Image Loading

- **Glide 图片缓存**：游戏列表图标使用 Glide 库进行懒加载，支持内存和磁盘缓存
- **DiskCacheStrategy.ALL**：缓存原始图片和转换后的图片，减少重复解码开销

### 网络优化 / Network Optimization

- **OkHttp 统一管理**：通过 `OkHttpClientProvider` 单例管理所有 HTTP/WebSocket 连接
- **HTTP 缓存**：50MB 磁盘缓存，减少重复网络请求
- **自动重试**：网络请求失败时自动重试 3 次，指数退避延迟
- **连接复用**：所有网络模块共享 OkHttpClient 实例，减少内存占用

### 内存优化 / Memory Optimization

- **资源及时释放**：所有游戏 Activity 在 `onDestroy` 中正确释放 Handler、ExecutorService 等资源
- **Handler 回调清理**：游戏暂停/销毁时移除所有待执行的回调，防止内存泄漏
- **线程池管理**：AI 计算使用独立的 ExecutorService，销毁时调用 `shutdownNow()`
- **内存泄漏检测**：Debug 版集成 LeakCanary 2.14，自动检测并报告内存泄漏问题

### Lint 严格模式 / Strict Lint Mode

- **Release 构建严格检查**：启用 `abortOnError` 和 `warningsAsErrors`，确保代码质量
- **问题早发现**：编译时强制检查潜在问题，减少运行时错误

### 国际化支持 / Internationalization

- **中英文双语**：`values-en/strings.xml` 提供完整英文资源
- **自动语言切换**：根据系统语言自动选择对应语言

### 网络错误统一处理 / Unified Network Error Handling

- **NetworkErrorHandler 集中管理**：统一处理所有网络异常
- **用户体验优化**：提供友好的错误提示和重试机制

### CI/CD 自动化 / CI/CD Automation

- **GitHub Actions**：实现自动构建、测试、上传产物
- **持续集成**：每次提交自动运行测试和代码检查
- **CI 质量门**：APK 大小报告、测试结果报告、Android Lint 执行和 Lint 问题报告

---

## 测试覆盖 / Test Coverage

### 单元测试统计 / Unit Test Statistics

| 游戏 | 测试文件 | 测试用例数 | 覆盖内容 |
|------|----------|-----------|----------|
| 五子棋 | GomokuGameTest | 12 | 初始状态、落子、横竖斜胜利、重置 |
| 围棋 | GoGameTest | 13 | 初始状态、落子、提子、跳过、重置、终局计分 |
| 华容道 | KlotskiGameTest | 3 | 初始棋盘、提示系统、解题路径 |
| 井字棋 | TicGameTest | 9 | 初始状态、落子、AI对战、胜负判定 |
| 2048 | Game2048GameTest | 10 | 初始状态、四方向移动、合并计分 |
| 贪吃蛇 | SnakeGameTest | 10 | 初始状态、方向控制、移动、撞墙判定 |
| 记忆翻牌 | MemoryGameTest | 11 | 初始状态、翻牌、配对、全部配对判定 |
| 中国象棋 | ChineseChessGameTest | 10 | 初始棋盘、棋子走法、悔棋、深拷贝 |
| 猜数字 | GuessGameTest | 9 | 初始状态、猜测判定、难度切换 |
| 掷骰子 | DiceGameTest | 10 | 初始状态、骰子类型判定、豹子顺子对子 |
| 斗地主规则 | DouDiZhuRuleEngineTest | 40+ | 出牌验证、叫地主决策、清台判定 |
| 斗地主牌型 | GameRuleUtilTest | 60+ | 牌型识别、出牌比较、洗牌发牌 |
| 斗地主 AI | AIBotTest | 3 | 农民不压队友、最小可管牌、地主残牌炸弹拦截 |
| 更新逻辑 | UpdateManagerLogicTest | 40+ | URL处理、版本比较、Beta策略 |
| AI API 客户端 | AiApiClientTest | 8 | MockWebServer：成功/HTTP错误/连接失败/畸形JSON |
| 更新信息模型 | UpdateInfoTest | 17 | JSON解析：全部字段/Beta渠道/版本回退 |
| **总计** | **15+ 个测试文件** | **437+ 个测试用例** | |

### 运行测试 / Run Tests

```bash
# 运行所有单元测试
.\gradlew.bat :app:test

# 运行特定游戏测试
.\gradlew.bat :app:testDebugUnitTest --tests "com.GameMatrix.app.games.gomoku.GomokuGameTest"
```

---

## 用户体验优化 / User Experience Optimization

### 交互式教程系统 / Interactive Tutorial System

- **多页滑动教程**：支持 ViewPager2 多页滑动查看，带圆点指示器
- **分步引导**：将复杂游戏规则拆分为多个页面，降低学习门槛
- **动画效果**：页面切换带有平滑过渡动画
- **主要游戏支持**：五子棋、中国象棋、围棋、贪吃蛇、俄罗斯方块、2048、数独

### 音效反馈系统 / Sound Feedback System

- **SoundManager 统一管理**：通用音效管理器，支持音效池和背景音乐
- **音效类型**：点击音效、胜利音效、失败音效、游戏特定音效
- **用户控制**：设置中可开关音效和震动反馈
- **斗地主音效**：96 个专业音效资源，包含出牌、叫地主、胜利等

### 动画效果 / Animation Effects

- **页面过渡动画**：fade_in、fade_out、slide_in_right、slide_out_left
- **交互反馈动画**：button_press 按钮点击动画
- **胜利庆祝动画**：win_celebrate 缩放旋转动画
- **scale_up 弹出动画**：用于卡片和对话框显示

### 游戏基类 / Base Game Activity

- **BaseGameActivity**：所有游戏的基类，集成音效、震动、动画功能
- **统一接口**：playClickSound()、vibrateShort()、animateView() 等便捷方法
- **生命周期管理**：自动管理音效资源的加载和释放

---

## 联机架构 / Multiplayer Architecture

### 公共网络模块 / Shared Network Module

所有联机游戏共享 `com.GameMatrix.app.network` 包中的网络基础设施：

| 模块 | 用途 |
|------|------|
| `RelayHttpClient` | HTTP Relay 通信 + WebSocket URL 生成 |
| `GameSocketServer` | 房主权威服务器（WebSocket 模式） |
| `GameSocketClient` | 客户端连接管理（WebSocket 模式） |
| `OnlineRoomManager` | 联机房间管理器（组合式复用，替代 BaseOnlineActivity 继承） |
| `LANManager` | 局域网 NSD 服务发现 |
| `RemoteP2PUtil` | 房间码生成与解析工具 |

### 云联机游戏 / Cloud Multiplayer Games

| 游戏 | OnlineActivity | 协议前缀 | P2P_PREFS | 玩家数 |
|------|---------------|---------|-----------|--------|
| 斗地主 | `DouDiZhuOnlineActivity` | `DDZ://` | `doudizhu_p2p` | 2-3 |
| 五子棋 | `GomokuOnlineActivity` | `GMK://` | `gomoku_p2p` | 2 |
| 围棋 | `GoOnlineActivity` | `GO://` | `go_p2p` | 2 |
| 中国象棋 | `ChineseChessOnlineActivity` | `XQ://` | `xiangqi_p2p` | 2 |
| 石头剪刀布 | `RockOnlineActivity` | `ROCK://` | `rock_p2p` | 2 |

### WebSocket 同步机制 / WebSocket Sync Mechanism

- **主机权威性**：房主验证所有操作，客户端只负责发送输入
- **3 次冗余广播**：立即 + 180ms + 600ms
- **STATE_ACK 确认**：客户端收到 SYNC_STATE 后回复版本确认
- **自动重连**：心跳 10s，超时 45s，容忍 2 次丢包
- **消息缓冲**：断线期间消息自动缓冲，重连后补发
- **状态版本**：防止重复处理和消息乱序

### 联机数据流 / Multiplayer Data Flow

```
┌──────────────┐    WSS (wss://hk-ws.<YOUR_DOMAIN>/ddz-ws)     ┌──────────────┐
│   房主端      │ ──────────────────────────────────────────────► │   Relay      │
│  (Android)   │ ◄────────────────────────────────────────────── │  (Node.js)   │
└──────────────┘         消息转发 (基于 room 参数路由)           └──────────────┘
        ▲                                                                │
        │  操作请求 (MOVE/PLACE_STONE/THROW)                             │
        │                                                                ▼
┌──────────────┐         WSS (wss://hk-ws.<YOUR_DOMAIN>/ddz-ws)    ┌──────────────┐
│   客户端      │ ─────────────────────────────────────────────────► │   nginx      │
│  (Android)   │ ◄───────────────────────────────────────────────── │  (WSS代理)   │
└──────────────┘         SYNC_STATE / GAME_OVER                     └──────────────┘
```

---

## 目录结构 / Directory Structure

```
GameMatrixApp/
├── app/
│   ├── build.gradle                          # 壳应用构建配置（版本管理、上传任务、聚合模块依赖）
│   └── src/main/
│       ├── AndroidManifest.xml               # 应用清单
│       ├── assets/
│       │   └── version.json                  # 内置版本信息（自动生成）
│       ├── java/com/GameMatrix/app/
│       │   ├── App.java                      # 应用入口，全局初始化
│       │   ├── MainActivity.java             # 主界面（底部导航 + 更新检查）
│       │   ├── ColorSchemeManager.java       # 主题配色管理
│       │   ├── PermissionManager.java        # 权限管理（位置/相机/存储权限）
│       │   ├── fragments/                    # 三个主页面 Fragment
│       │   │   ├── GamesFragment.java        # 游戏大厅（搜索/收藏/最近）
│       │   │   ├── ToolsFragment.java        # 工具箱（26+ 工具）
│       │   │   └── BrowserFragment.java      # 内置浏览器
│       │   ├── network/                      # 联机业务协调层
│       │   │   ├── GameSocketServer.java     # 房主权威服务器
│       │   │   ├── GameSocketClient.java     # 客户端连接管理
│       │   │   ├── OnlineRoomManager.java    # 联机房间管理器（组合式复用）
│       │   │   ├── LANManager.java           # 局域网服务发现
│       │   │   └── WebSocket*Helper.java     # WebSocket 联机辅助
│       │   ├── games/                        # 26 款游戏模块
│       │   │   ├── GameRegistry.java         # 游戏注册中心（双轨制：静态+@GameEntry+动态注册）
│       │   │   ├── GameEntry.java            # @GameEntry 注解（游戏自声明元数据）
│       │   │   ├── GameUsageStore.java       # 使用记录存储
│       │   │   ├── GameTutorialHelper.java   # 教程弹窗管理
│       │   │   ├── doudizhu/                 # 斗地主（三模联机）
│       │   │   ├── rock/                     # 石头剪刀布 + RockOnlineActivity
│       │   │   ├── gomoku/                   # 五子棋 + GomokuOnlineActivity
│       │   │   ├── chinesechess/              # 中国象棋 + ChineseChessOnlineActivity
│       │   │   ├── go/                       # 围棋 + GoOnlineActivity
│       │   │   └── [其他 23 款单机游戏]
│       │   ├── tools/                        # 工具箱实现（26+ 工具）
│       │   │   ├── ToolSectionStore.java     # 工具分类与排序
│       │   │   ├── AdvancedToolBinders.java  # 高级工具绑定
│       │   │   └── *ToolBinder.java         # 各种工具绑定器
│       │   ├── utils/                        # 通用工具
│       │   ├── views/                        # 自定义 View
│       │   └── settings/                     # 设置弹窗
│       ├── res/                              # 资源文件
│       │   ├── layout/                       # 布局文件
│       │   ├── drawable/                     # 图标与形状
│       │   ├── values/                       # 字符串、颜色、主题
│       │   ├── raw/                          # 音效资源（斗地主 96 个音频）
│       │   └── xml/                          # 配置文件
│       └── ...
├── core/
│   ├── common/                               # 通用基础模块
│   │   └── src/main/
│   │       ├── java/com/GameMatrix/app/
│   │       │   └── SettingsManager.java      # 设置管理（SharedPreferences）
│   │       └── kotlin/com/GameMatrix/app/util/
│   │           ├── AppResult.kt              # 通用结果模型
│   │           ├── AppError.kt               # 错误模型
│   │           ├── NetworkResult.kt          # 网络结果模型
│   │           └── Extensions/Lazy/Memory/Accessibility helpers
│   ├── network/                              # 基础网络模块
│   │   └── src/main/java/com/GameMatrix/app/
│   │       ├── network/
│   │       │   ├── OkHttpClientProvider.java # HTTP/WebSocket 客户端
│   │       │   ├── RelayHttpClient.java      # Relay HTTP + WebSocket URL
│   │       │   ├── RemoteP2PUtil.java        # 房间码工具类
│   │       │   └── NetworkLogger.java        # 网络日志
│   │       └── utils/NetworkErrorHandler.java
│   └── update/                               # 更新子系统模块
│       └── src/main/
│           ├── java/com/GameMatrix/app/update/
│           │   ├── UpdateManager.java        # 更新检查、下载、安装入口
│           │   ├── UpdateChecker.java        # 版本检查策略
│           │   ├── UpdateDownloader.java     # APK 下载与校验
│           │   ├── UpdateInstaller.java      # 安装与目录打开
│           │   └── UpdateInfo.java           # 版本信息数据模型
│           └── kotlin/com/GameMatrix/app/update/
│               └── UpdateViewModel.kt        # 生命周期安全的更新 ViewModel
├── tools/
│   ├── upload_to_vps.py                      # 上传 APK 到 VPS
│   ├── upload_to_github_release.py           # 上传 APK 到 GitHub Releases
│   ├── check_vps_nginx.py                    # VPS nginx 配置检查
│   ├── verify_vps.py                         # VPS 验证脚本
│   ├── publish-all.py                         # 一键发布脚本
│   └── archive/                              # 临时脚本存档
├── vps/                                      # VPS 部署模板
│   ├── var_www_update/                       # 更新服务模板
│   │   ├── update_server.py                  # 更新服务
│   │   ├── feedback/                         # 反馈服务模板
│   │   └── ddz_relay/                        # Relay 服务模板
│   ├── ddz_ws_relay/                         # WebSocket Relay 服务
│   └── nginx/                                # nginx 配置模板
├── gradle/wrapper/                           # Gradle Wrapper
├── docs/                                     # 技术文档
├── .github/workflows/                        # GitHub Actions CI/CD
├── .gitignore                                # Git 忽略规则
├── local.properties.template                 # 本地配置模板
├── .editorconfig                             # 编辑器配置
├── README.md                                 # 本文件
├── CHANGELOG.md                              # 版本更新日志
└── PROJECT_CONTEXT.md                        # 项目上下文文档
```

---

## 构建与部署 / Build & Deployment

### 首次构建 / First Build

```bash
# 1. 克隆项目 / Clone the repository
git clone https://github.com/3571949306/GameMatrixApp.git
cd GameMatrixApp

# 2. 配置本地服务器地址 / Configure server addresses
# 复制配置模板 / Copy configuration template
cp local.properties.template local.properties
# 编辑 local.properties，将 <YOUR_*> 占位符替换为实际地址

# 3. 编译调试版 / Build debug APK
.\gradlew.bat :app:assembleDebug

# 4. 安装到设备 / Install to device
adb install app/build/outputs/apk/debug/app-debug.apk
```

### local.properties 配置 / Configuration

```properties
# ============ 主 VPS（必填）============
# 更新检查服务器地址 / Update server URL
server.url=https://hk-update.<YOUR_DOMAIN>

# WebSocket Relay 服务器地址 / WebSocket Relay URL
ws.url=wss://hk-ws.<YOUR_DOMAIN>/ddz-ws

# HTTP Relay 服务器地址 / HTTP Relay URL
relay.url=https://hk-relay.<YOUR_DOMAIN>/api/ddz-relay

# ============ 备用更新源（已废弃）============
# 2026-06-19: 美国 VPS 已下线，备用更新源改由 GitHub Releases 提供
# server.url.fallback 配置项已废弃，保留空值向后兼容
# server.url.fallback=

# 反馈服务器地址（可选）/ Feedback server URL (optional)
feedback.url=https://<YOUR_DOMAIN>/api/feedback
```

| 配置项 | 用途 | 缺少后果 |
|--------|------|----------|
| `server.url` | 应用更新检查 | 无法获取新版本 |
| `ws.url` | WebSocket 云联机 | 无法使用 WebSocket 联机 |
| `relay.url` | HTTP Relay 云联机 | 只能局域网对战 |
| `server.url.fallback` | ~~备用更新源~~（已废弃） | 无影响，备用源改由 GitHub Releases 提供 |
| `feedback.url` | 用户反馈提交 | 反馈功能不可用 |

> **注意 / Note**：修改 `local.properties` 后必须执行 **Build → Clean Project → Rebuild Project**，否则 `BuildConfig` 不会更新。

### 发布流程 / Release Workflow

| 场景 | 命令 | 说明 |
|------|------|------|
| 日常编译 | `.\gradlew.bat :app:assembleDebug` | 仅编译，versionCode 自动递增 |
| Beta 发布 | `.\gradlew.bat uploadReleaseArtifactsToVps` | 上传到 VPS，仅测试版用户可用 |
| 正式发布 | `.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable` | 同时上传到 VPS 和 GitHub Releases |

> 如果 AGP 8.13 的 `lintVitalReportRelease` 出现路径变量序列化异常，可临时加入 `-PskipReleaseLint=true` 构建正式包；默认 release lint 仍保持开启。

**重要更新（2026-05-12）**：
- ✅ 双版本分发架构重构：测试版/正式版完全分离
- ✅ 修复版本检查显示"已是最新版本"问题
- ✅ 修复自定义更新源切换失效问题

### APK 签名配置 / APK Signing Configuration

**签名问题已修复！** 现在 APK 会自动签名。

1. 创建密钥库（首次）：
```bash
keytool -genkey -v -keystore GameMatrix.keystore -alias GameMatrix -keyalg RSA -keysize 2048 -validity 10000 -storepass "<your-store-password>" -keypass "<your-key-password>" -dname "CN=GameMatrix, OU=Development, O=GameMatrixApp, L=Shenzhen, ST=Guangdong, C=CN"
```

2. 创建 `keystore.properties`（不提交 Git）：
```properties
STORE_FILE=GameMatrix.keystore
STORE_PASSWORD=<your-store-password>
KEY_ALIAS=GameMatrix
KEY_PASSWORD=<your-key-password>
```

3. Gradle 自动读取配置并签名 Release APK

**验证签名**：
```bash
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk
# 输出：jar 已验证 ✅
```

> **注意**：`GameMatrix.keystore` 和 `keystore.properties` 已添加到 `.gitignore`，切勿提交到版本控制。

### GitHub Token 配置 / GitHub Token Setup

正式发布需要配置 GitHub Token：

1. 创建 Token：**GitHub → Settings → Developer settings → Personal access tokens (classic) → Generate new token**
2. 权限要求：`repo` (完整仓库控制)
3. 保存到 `local_private/github/token.txt`（自动被 `.gitignore` 排除）

---

## 更新日志 / Changelog

完整版本历史请查看 [CHANGELOG.md](CHANGELOG.md)。

### 最新版本 / Latest Version

| 版本 | 日期 | 类型 | 主要更新 |
|------|------|------|----------|
| v341 (1.4.0) | 2026-05-26 | Stable | modules.json v11：23款新游戏模块、证书绑定临时关闭、R8 Debug关闭、SHA-256校验修复 |
| v224 (1.3.19) | 2026-05-12 | Stable | 双版本分发架构重构、版本检查问题修复、自定义更新源切换修复 |
| v217 (1.3.18) | 2026-05-12 | Stable | APK 签名配置、敏感文件排除、发布流程完善 |
| v26 (1.11.0) | 2026-05-11 | Stable | Lint 严格模式、网络错误处理、国际化、内存泄漏检测 |
| v25 (1.10.3) | 2026-05-11 | Beta | 权限使用说明、R8 混淆优化、斗地主逻辑拆分、删除重复资源 |
| v24 (1.3.10 beta) | 2026-05-10 | Beta | 4 个游戏新增云联机、公共网络模块抽取 |
| v23 (1.3.9 beta) | 2026-05-10 | Beta | 修复 beta 用户检查更新问题 |

---

## License

MIT License

Copyright (c) 2024-2026 GameMatrix App Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平台 Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题。
- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言。
- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项。
- 发布前检查需覆盖中文/英文两种语言、深色/浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮。
## 2026-05-15 文档同步：Dependabot 与 CI 修复

- Dependabot 安全告警已处理：升级 Android Gradle Plugin 到 8.13.2、Gradle Wrapper 到 8.13、Kotlin 到 2.2.21、Hilt 到 2.57.2。
- 构建 classpath 已强制解析到安全版本：Netty 4.1.133.Final、BouncyCastle 1.84、commons-compress 1.26.0、jose4j 0.9.6、jdom2 2.0.6.1。
- GitHub Actions 已改为验证型 CI：使用 JDK 21，执行 debug 构建与单元测试，不在云端构建 release 包，避免暴露或依赖 release 签名文件。
- CI 命令统一添加 `-PautoBumpVersion=false`，避免自动修改 `version.properties`。
- `.gitignore` 的 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/GameMatrix/app/ai/data/` 源码。
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 VPS/GitHub Release 发布仍以本机发布流程为准。
## 2026-05-18 文档同步：架构优化

- UpdateViewModel（@HiltViewModel + LiveData）替代 UpdatePresenter，密封类 UpdateCheckState/DownloadState 建模状态。
- OnlineRoomManager 组合式复用联机房间逻辑，替代 BaseOnlineActivity 继承。
- GameRegistry 双轨注册：静态硬编码 + @GameEntry 注解自动发现 + register() 动态注册。
- SaveManager 从 Java 迁移到 Kotlin（@Singleton + @Inject constructor），旧 Java 文件已删除。
- SettingsManager/OkHttpClientProvider/UpdateManager/SaveManager 添加 @Inject 构造函数，getInstance() 标记 @Deprecated。
- 新增 AppError（密封类，10 种错误类型）+ NetworkResult（类型安全结果封装）。
- 测试用例从 96 增至 411+，版本号更新至 1.3.26 (versionCode=260)。
## 2026-05-18 文档同步：低优先级代码质量

- Result.kt 重命名为 AppResult，消除与 kotlin.Result 标准库命名冲突。
- AiTask.status 引入 TaskStatus 枚举替代字符串，AiResult.errorCode 新增 AiErrorCode 常量类。
- 16 处空 catch 块已补日志记录（Log.w/Log.d），保留原有注释说明忽略原因。
- OnlineRoomManager（35 个）+ AppSettingsDialog（13 个）共 48 个硬编码中文字符串提取到 strings.xml。
- CODE_WIKI.md 新增第 10 章"Java/Kotlin 混合边界规范"，文档化文件放置、跨语言调用注意事项、迁移优先级和同名类冲突规则。
## 2026-05-19 文档同步：战略优化

- UpdateViewModel 协程化：`viewModelScope.launch` + `suspendCancellableCoroutine` 包装 Java 回调为 suspend 函数，`CheckResult`/`DownloadResult` 密封类替代布尔标志。
- 网络层测试：新增 `AiApiClientTest`（MockWebServer，8 个方法）和 `UpdateInfoTest`（JSON 解析，17 个方法），测试用例总数从 411+ 增至 436+。
- CI 质量门：APK 大小报告、测试结果报告、Android Lint 执行和 Lint 问题报告。
- 安全加固：`allowBackup=false`，新增 `backup_rules.xml` 和 `data_extraction_rules.xml`，存储权限迁移（`READ_MEDIA_IMAGES`、`maxSdkVersion` 限制）。
- 构建优化：`MaterialCardView` 替代 `androidx.cardview.widget.CardView`，移除 `cardview:1.0.0` 依赖。
- 版本号更新：versionCode=262, versionName=1.3.26。

---

## Configuration / 配置

### MiMo TTS API Key

TTS 功能需要小米 MiMo TTS API Key。Clone 后需要创建 `local.properties`（已列入 `.gitignore`，不会提交）：

```properties
# local.properties
mimo.api.key=你的key
```

未配置时编译仍能成功，但 TTS 调用会返回 401。

---

## 开发环境搭建 / Development Setup

### 1. 必备工具

- **JDK 17+**（推荐 Microsoft OpenJDK 17 或 Temurin 17）
- **Android Studio Ladybug | 2024.2.1+**（自带 JBR 21 也可）
- **Android SDK**：compileSdk 35，build-tools 35.0.0+
- **Git 2.30+**

### 2. Clone 后第一次准备

```bash
# Clone
git clone https://github.com/3571949306/GameMatrixApp.git
cd GameMatrixApp

# 拷贝 local.properties 模板（按需修改）
cp local.properties.template local.properties 2>/dev/null || true
# 编辑 local.properties，至少填一个 mimo.api.key

# 验证 build
./gradlew :app:assembleDebug
```

### 3. 常用命令

| 命令 | 用途 |
|---|---|
| `./gradlew :app:assembleDebug` | 编译 debug APK |
| `./gradlew :app:assembleRelease` | 编译 release APK（需 keystore） |
| `./gradlew :app:test` | 跑单元测试 |
| `./gradlew :app:lintDebug` | 跑 Android Lint |
| `./gradlew :app:bumpVersion` | 手动 bump versionCode |
| `./gradlew :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable` | 完整发布流程（需 keystore + VPS 凭据） |

### 4. 加新模块的步骤

1. **位置**：在 `module-store/feature/` 下选个合适子目录（如 `games/` 或 `tools/`）
2. **创建 module**：在 `settings.gradle` 加 `include ':module-store:feature:games:新名字'`
3. **build.gradle**：参考 `module-store/feature/games/klotski/build.gradle` 复制结构
4. **AndroidManifest.xml**：声明 `applicationId` 和启动 Activity（如果要）
5. **核心类**：
   - 实现 `IModuleEntry`（动态加载）或标准 `Application`+`Activity`（内置）
   - 在 `core/modulestore` 注册模块元数据
6. **测试**：在 module 自己的 `src/test/` 写单元测试
7. **打包**：跑 `./gradlew :module-store:feature:games:新名字:assembleDebug` 验证

详见 `docs/MODULE_DEVELOPMENT.md`（如果存在的话）。

### 5. 提交前必做

- [ ] 跑 `./gradlew :app:lintDebug` 没过不能提交
- [ ] 跑 `./gradlew :app:test` 所有测试绿
- [ ] 跑 `gitleaks detect --source .` 没命中（防密钥泄露）
- [ ] 更新 `CHANGELOG.md`
- [ ] 不要把 `local.properties` / `*.jks` / `keystore.properties` 提交（已被 `.gitignore` 排除）

