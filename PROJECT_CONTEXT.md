# GameCenterApp 项目上下文

> 给 AI 开发助手和维护者的快速入口。优先以仓库当前代码为准，本文档用于减少查找成本和避免常见误判。

## 0. 最近更新

| 版本 | 变更内容 |
|------|----------|
| **当前工作区** | **战略优化：UpdateViewModel 协程化（viewModelScope + suspendCancellableCoroutine + CheckResult/DownloadResult 密封类）、网络层测试（AiApiClientTest + UpdateInfoTest）、CI 质量门（APK 大小/测试结果/Lint 报告）、安全加固（allowBackup=false + backup_rules + data_extraction_rules + 存储权限迁移）、构建优化（MaterialCardView 替代 CardView）** |
| **当前工作区** | **低优先级代码质量：AppResult 重命名、TaskStatus 枚举、AiErrorCode 常量类、空 catch 块补日志、硬编码文案提取到 strings.xml（48 个）、Java/Kotlin 混合边界规范文档化** |
| **当前工作区** | **架构优化：UpdateViewModel（@HiltViewModel + LiveData）替代 UpdatePresenter、OnlineRoomManager 组合式复用、GameRegistry 双轨注册（@GameEntry + 动态注册）、SaveManager Kotlin 迁移、@Inject 构造函数迁移、AppError/NetworkResult 统一错误模型** |
| **当前工作区** | **架构优化：DouDiZhuOnlineActivity 拆分（UIController + RuleEngine + AIHelper + NetworkHandler）、Room 数据库恢复（KSP1）、ErrorReporter 统一错误上报、262 个单元测试 + 4 个集成测试类** |
| **当前工作区** | **Gemma 本地推理接入：MediaPipe LLM Inference、下载前 Gemma Notice、启用后本地优先路由** |
| **当前工作区** | **AI 阶段 4 完成：模板、历史搜索、收藏、导出；发布脚本统一签名 R8 release 包** |
| **v1.3.21-beta** | **AI 智能助手接入：新增 AiFragment、AiTaskRouter、LocalAiProcessor，7 种 AI 任务，独立底部导航接入** |
| **v1.3.20** | **依赖升级（Kotlin 2.1.10、Hilt 2.55、AndroidX 等）、Gson 替换手工 JSON、代码清理** 🔧 |
| **v1.3.19** | **双版本分发架构重构：测试版/正式版分离、更新逻辑优化、上传脚本修复** 🚀 |
| **v1.3.17** | **修复 APK 签名配置、自动更新源选择逻辑、构建系统问题** 🔥 |
| v1.3.16 | APK 签名配置、敏感文件排除、自动化发布流程 |
| v1.11.0 | Lint 严格模式、网络错误处理、国际化、LeakCanary、CI/CD |

## 快速入口

### 关键文件

| 文件 | 用途 |
|------|------|
| `app/src/main/java/com/gamecenter/app/PermissionHelper.java` | 权限管理辅助 |
| `app/src/main/java/com/gamecenter/app/utils/NetworkErrorHandler.java` | 网络错误统一处理 |
| `app/src/main/java/com/gamecenter/app/utils/I18nHelper.java` | 国际化辅助 |
| `app/src/main/java/com/gamecenter/app/ai/AiTaskRouter.java` | AI 任务调度（本地优先 → 云端 fallback） |
| `app/src/main/java/com/gamecenter/app/ai/ui/AiFragment.java` | AI 助手聊天页面 |
| `app/src/main/java/com/gamecenter/app/ai/local/MediaPipeLocalLlmEngine.java` | Gemma `.task` 本地推理封装 |
| `app/src/main/java/com/gamecenter/app/ai/legal/AiLegalNotices.java` | Gemma 条款、本地 AI 风险提示与下载前确认文本 |
| `app/src/main/java/com/gamecenter/app/ai/template/AiTemplateManager.java` | AI 常用模板管理 |
| `app/src/main/java/com/gamecenter/app/games/doudizhu/DouDiZhuUIController.java` | 斗地主 UI 控制器（视图引用、UI 更新、对话框） |
| `app/src/main/java/com/gamecenter/app/games/doudizhu/DouDiZhuRuleEngine.java` | 斗地主规则引擎（出牌验证、叫地主评估、清台判断） |
| `app/src/main/java/com/gamecenter/app/games/doudizhu/DouDiZhuAIHelper.java` | 斗地主 AI 辅助（AI 决策调度、出牌/叫地主逻辑） |
| `app/src/main/java/com/gamecenter/app/games/doudizhu/DouDiZhuNetworkHandler.java` | 斗地主网络处理（消息路由、客户端意图管理、重连补发） |
| `app/src/main/kotlin/com/gamecenter/app/database/AppDatabase.kt` | Room 数据库入口（单例、提供 DAO） |
| `app/src/main/java/com/gamecenter/app/utils/ErrorReporter.java` | 统一错误上报（VPS + 本地回退、限流） |
| `app/src/main/kotlin/com/gamecenter/app/update/UpdateViewModel.kt` | 更新流程 ViewModel（@HiltViewModel + LiveData，替代 UpdatePresenter） |
| `app/src/main/kotlin/com/gamecenter/app/util/AppError.kt` | 统一错误模型（密封类，10 种错误类型） |
| `app/src/main/kotlin/com/gamecenter/app/util/NetworkResult.kt` | 网络请求结果封装（基于 AppError） |
| `app/src/main/java/com/gamecenter/app/network/OnlineRoomManager.java` | 联机房间管理器（组合式复用，替代 BaseOnlineActivity 继承） |
| `app/src/main/java/com/gamecenter/app/games/GameEntry.java` | @GameEntry 注解（游戏自声明元数据） |
| `app/src/main/kotlin/com/gamecenter/app/SaveManager.kt` | 存档管理器（Kotlin 迁移，@Singleton + @Inject） |
| `app/src/main/java/com/gamecenter/app/ai/data/TaskStatus.java` | AI 任务状态枚举（PENDING/RUNNING/COMPLETED/FAILED） |
| `app/src/main/java/com/gamecenter/app/ai/data/AiErrorCode.java` | AI 错误码常量类（7 个常量，消除魔法字符串） |
| `app/src/main/kotlin/com/gamecenter/app/util/AppResult.kt` | 通用结果封装（重命名自 Result.kt，避免与 kotlin.Result 冲突） |
| `app/src/test/java/com/gamecenter/app/ai/cloud/AiApiClientTest.java` | AI API 客户端 MockWebServer 测试（8 个方法） |
| `app/src/test/java/com/gamecenter/app/update/UpdateInfoTest.java` | 更新信息 JSON 解析测试（17 个方法） |
| `app/src/main/res/xml/backup_rules.xml` | Auto Backup 排除规则（sharedpref/database/update/） |
| `app/src/main/res/xml/data_extraction_rules.xml` | D2D 迁移和云备份排除规则 |
| `.github/workflows/ci.yml` | GitHub Actions CI/CD 工作流 |
| `keystore.properties` | APK 签名凭证配置（不提交 Git） |
| `app/gamecenter.keystore` | APK 签名密钥库（不提交 Git） |
| `auto-publish.bat` | 一键发布脚本（Windows） |

## 1. 项目概览

| 项目 | 说明 |
| --- | --- |
| 名称 | GameCenterApp |
| 类型 | Android 单模块应用 |
| 包名 | `com.gamecenter.app` |
| 定位 | 集成经典小游戏、网络/设备工具、内置浏览器和应用自更新能力的工具娱乐应用 |
| 语言 | Java 17 + Kotlin（数据层/工具层/ViewModel） |
| 构建 | Gradle Wrapper + Android Gradle Plugin 8.7.3 |
| SDK | `minSdk 24`, `targetSdk 35`, `compileSdk 35` |
| 当前版本来源 | 根目录 `version.properties` |
| 服务器架构 | 双 VPS：主节点（hk-*）+ 备用旧服务保留 |

## 1.1 服务器架构

本项目采用**双 VPS 架构**：

| VPS | IP | 定位 | 域名前缀 |
|-----|----|------|----------|
| 主 VPS | `<YOUR_VPS_IP>` | 主业务节点（更新、WebSocket、Relay） | `hk-*` |
| 备用 VPS | `<YOUR_VPS_IP>` | 旧服务保留 | 无前缀 |

**主 VPS 服务清单：**

| 服务 | 内部端口 | 域名 | Cloudflare SSL |
|------|----------|------|----------------|
| APK 更新服务 | 127.0.0.1:9000 | hk-update.`<YOUR_DOMAIN>` | Flexible |
| WebSocket Relay | 127.0.0.1:18080 | hk-ws.`<YOUR_DOMAIN>` | Flexible |
| HTTP Relay | 127.0.0.1:9012 | hk-relay.`<YOUR_DOMAIN>` | Flexible |
| OpenWebUI | Docker 127.0.0.1:3000 | hk-ai.`<YOUR_DOMAIN>` | Flexible |
| x-ui 面板 | 127.0.0.1:41370 | hk-xui.`<YOUR_DOMAIN>` | Flexible |
| 静态网站 | 127.0.0.1:8080 | hk-site.`<YOUR_DOMAIN>` | Flexible |

> **Cloudflare 使用 Flexible SSL 模式**，nginx 同时监听 80 和 443 端口。不能改为其他模式。

## 1.2 更新分发架构 / Update Distribution Architecture

本项目采用**三级下载源**架构，App 下载更新时按以下优先级依次尝试：

| 优先级 | 下载源 | 地址 | 超时配置 |
|--------|--------|------|----------|
| 1 | GitHub Releases | `https://github.com/3571949306/GameCenterApp/releases/latest` | 连接 5s / 读取 10s |
| 2 | 主更新源（香港 VPS） | `https://hk-update.<YOUR_DOMAIN>` | 连接 3s / 读取 5s |
| 3 | 备用更新源（美国 VPS） | `https://<YOUR_FALLBACK_DOMAIN>` | 连接 15s / 读取 30s |

**自动换源机制**：下载开始后 3 秒检测速度，如果低于 50 KB/s，自动删除临时文件并切换到下一个下载源。

**Beta 测试版仅上传到 VPS（不上传 GitHub Releases），正式版同时上传到 VPS 和 GitHub Releases。**

## 1.3 联机架构

本项目采用**房主权威**联机模型，所有联机游戏共享 `com.gamecenter.app.network` 公共模块：

| 模块 | 用途 |
|------|------|
| `GameSocketServer` | 房主权威服务器（TCP + HTTP Relay + WebSocket 三模） |
| `GameSocketClient` | 客户端连接管理（TCP + HTTP Relay + WebSocket 三模） |
| `RelayHttpClient` | HTTP Relay 通信 + WebSocket URL 生成 |
| `LANManager` | 局域网 NSD 服务发现 |
| `RemoteP2PUtil` | 房间码生成与解析工具 |
| `OnlineChatHelper` | 可复用联机聊天组件（支持内联模式和弹窗模式） |
| `OnlineRoomManager` | 联机房间管理器（组合式复用，替代 BaseOnlineActivity 继承） |

**支持联机的游戏：**

| 游戏 | OnlineActivity | 协议前缀 | 棋盘 View |
|------|---------------|---------|----------|
| 斗地主 | DouDiZhuOnlineActivity | DDZ:// | DouDiZhuTableView |
| 五子棋 | GomokuOnlineActivity | GMK:// | GomokuView（复用单机） |
| 中国象棋 | ChineseChessOnlineActivity | XQ:// | ChineseChessView（复用单机） |
| 围棋 | GoOnlineActivity | GO:// | GoView（复用单机） |
| 石头剪刀布 | RockOnlineActivity | ROCK:// | 无棋盘 |

**WebSocket 同步机制：**
- 主机权威性：房主验证所有操作，客户端只负责发送输入
- 3 次冗余广播：立即 + 180ms + 600ms
- STATE_ACK 确认：客户端收到 SYNC_STATE 后回复版本确认
- 自动重连：心跳 10s，超时 45s，容忍 2 次丢包
- 消息缓冲：断线期间消息自动缓冲，重连后补发

当前 `version.properties` 示例：

```properties
versionCode=262
versionName=1.3.26
```

## 2. 代码结构

```text
GameCenterApp/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── pkgInfo.txt
│       ├── java/com/gamecenter/app/
│       │   ├── App.java
│       │   ├── MainActivity.java
│       │   ├── SettingsManager.java
│       │   ├── ColorSchemeManager.java
│       │   ├── ai/                          # ← 新增：AI 功能模块
│       │   │   ├── AiTaskRouter.java        #     任务路由（本地优先 + 云端 fallback）
│       │   │   ├── AiPreferences.java       #     AI 偏好设置
│       │   │   ├── data/                    #     数据模型
│       │   │   │   ├── AiMessage.java
│       │   │   │   ├── AiTask.java
│       │   │   │   ├── AiResult.java
│       │   │   │   └── AiProviderConfig.java
│       │   │   ├── cloud/                   #     云端 API
│       │   │   │   └── AiApiClient.java
│       │   │   ├── local/                   #     本地处理
│       │   │   │   └── LocalAiProcessor.java
│       │   │   └── ui/                      #     页面
│       │   │       └── AiFragment.java
│       │   ├── fragments/
│       │   ├── games/
│       │   ├── settings/
│       │   ├── tools/
│       │   ├── update/
│       │   ├── utils/
│       │   └── views/
│       └── res/
│           ├── drawable/                    # ← 新增：AI 消息气泡背景
│           ├── layout/                      # ← 新增：fragment_ai.xml, item_ai_message.xml
│           ├── menu/
│           ├── raw/
│           ├── values/
│           ├── values-night/
│           └── xml/
├── gradle/
├── tools/jadx/
├── apk_temp/
├── com.injoy.games.crazy.poker/
├── CHANGELOG.md
├── changelog.txt
├── README.md
└── version.properties
```

目录说明：

- `app/src/main/java/com/gamecenter/app/fragments`: 三个主页面，游戏大厅、工具箱、内置浏览器。`AiFragment` 在 ai/ui 包中。
- `app/src/main/java/com/gamecenter/app/ai`: AI 助手模块。包含路由调度 (`AiTaskRouter`)、云端客户端 (`AiApiClient`)、本地处理器 (`LocalAiProcessor`)、历史与收藏 (`AiHistoryStore`)、模板 (`AiTemplateManager`)、偏好设置 (`AiPreferences`) 和数据模型。默认 DeepSeek API，可切换阿里云通义、硅基流动、智谱 AI、零一万物、OpenAI 等多家 API。
- `app/src/main/java/com/gamecenter/app/games`: 各小游戏模块。多数模块采用 `Activity + View + Game` 的简单分层。
- `app/src/main/java/com/gamecenter/app/settings`: 设置弹窗与设置项交互。`AppSettingsDialog` 已从 `GamesFragment` 拆出，负责主题、配色、版本更新和反馈入口。
- `app/src/main/java/com/gamecenter/app/tools`: 工具箱拆分后的共享结构和独立 binder。当前包含功能区模型/配置存储、剪贴板/哈希/颜色取色器 binder，以及 `AdvancedToolBinders` 中的网络体检、诊断报告、DNS 查询、局域网扫描、编码/时间戳/JSON、文件哈希、二维码增强、颜色增强、权限隐私说明。AI 不再嵌入工具箱，入口在底部导航。
- `app/src/main/java/com/gamecenter/app/update`: 自更新模块，包含 `version.json` 检查、正式/测试版策略、下载、MD5 校验、FileProvider 安装。`UpdateViewModel`（Kotlin）提供生命周期安全的更新状态管理，替代旧 `UpdatePresenter`。
- `vps/var_www_update`: VPS 更新和反馈模板；更新服务部署为 `/var/www/update/server.py`，App 上传目录为 `/var/www/update/app/`，反馈目录为 `/var/www/update/feedback/`。
- `vps/var_www_update/feedback`: VPS 反馈接收模板，部署目标为 `/var/www/update/feedback/`，通过 nginx `/api/feedback` 转发到本机 `127.0.0.1:9011`；反馈会按类型保存到 `Bug反馈/` 和 `功能建议/`，文件名包含编号、类型、时间和反馈摘要。
- `app/src/main/java/com/gamecenter/app/views`: 主题/颜色选择相关自定义控件。
- `app/src/main/res/raw`: 音效资源较多，尤其是斗地主语音和背景音。
- `tools/jadx`, `apk_temp`, `com.injoy.games.crazy.poker`: 反编译/参考 APK 相关目录，不属于主应用源码路径，修改主功能时通常不要动。

## 3. 主架构

`MainActivity` 是底部导航容器，负责挂载三个 Fragment：

- `GamesFragment`: 游戏大厅，使用 `TabLayout + RecyclerView` 展示游戏卡片，支持搜索、最近游玩、收藏。右上角设置入口委托给 `settings/AppSettingsDialog`；反馈入口优先 POST 到 VPS，公开仓库不保存个人邮箱兜底收件人。
- `ToolsFragment`: 工具箱，包含网络、设备、颜色、二维码、剪贴板等实用工具入口。支持工具搜索、收藏、最近使用、排序、显隐和单双列布局。功能区模型和配置存储已拆到 `tools/ToolSection` 与 `tools/ToolSectionStore`；剪贴板、哈希、颜色取色器和增强工具已拆成独立 binder。`r`n- `AiFragment`: AI 独立底部导航页，支持 7 种任务、模板快捷填充、历史搜索、收藏筛选和导出。
- `BrowserFragment`: 内置 WebView 浏览器。

全局状态和主题：

- `App`: Application 入口，负责应用主题初始化和刷新。
- `SettingsManager`: 保存主题模式、配色方案、自动检查更新、接收测试版、自动下载安装包和下载后提示安装等用户设置。已标注 `@Inject` 构造函数，`getInstance()` 标记 `@Deprecated`。
- `ColorSchemeManager`: 管理可选配色方案；游戏大厅卡片、按钮、标签栏和底部导航会跟随当前方案刷新。
- `UpdateViewModel`: 更新流程 ViewModel（@HiltViewModel + LiveData），替代旧 UpdatePresenter，生命周期安全。

## 4. 游戏模块

当前 `games/` 下的模块：

```text
blackjack, breakout, brotato, checkers, chinesechess, dice, doudizhu,
flappy, game2048, go, gomoku, guess, klotski, match,
memory, pipeline, plane, reaction, rock, snake, sokoban, sudoku, tetris,
tic, tiles, whack
```

游戏入口主要在 `GameRegistry` 中维护（双轨制：静态硬编码 + `@GameEntry` 注解自动发现 + `register()` 动态注册），`GamesFragment` 只负责展示、搜索、收藏和最近游玩等交互。新增或调整游戏时通常需要同步：

1. 在 `games/` 下创建或修改游戏包。
2. 在 `AndroidManifest.xml` 注册对应 Activity，并确认 `screenOrientation`。
3. 在 Activity 类上添加 `@GameEntry` 注解（推荐），或在 `GameRegistry` 中手动添加入口。
4. 在 `res/values/strings.xml` 添加游戏名称和描述。
5. 如有新布局、图标、音效，分别放入 `res/layout`、`res/drawable`、`res/raw`。

斗地主模块比较特殊：

- `DouDiZhuMenuActivity`: 菜单入口，竖屏。
- `DouDiZhuActivity`: 单机游戏，横屏。
- `DouDiZhuOnlineActivity`: 联机模式，横屏。支持三模联机（局域网 TCP + HTTP Relay + WebSocket）。
- `doudizhu/model`, `doudizhu/network`, `doudizhu/utils`: 承载牌型、联机网络和规则工具。
- `klotski/`: 华容道已重做核心棋盘和 BFS 提示求解器；提示必须从当前棋盘重新计算，目标是连续引导曹操移动到下方出口。

## 5. 构建与版本

当前版本：`versionCode=262`, `versionName=1.3.26`。当前工作区在该版本基础上完成战略优化（UpdateViewModel 协程化、网络层测试、CI 质量门、安全加固、MaterialCardView 替代 CardView）。

Windows 下推荐命令：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat clean assembleDebug
.\gradlew.bat :app:buildAndUploadDebugToVps
```

输出位置：

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`（已签名、已 R8 混淆）
- Release 版本信息：`app/build/outputs/apk/release/version.json`
- APK 内置版本信息：构建时生成到 `build/generated/assets/version/version.json` 并打包为 `assets/version.json`

### APK 签名配置

Release APK 已配置自动签名：

1. **密钥库**：`app/gamecenter.keystore`（RSA 2048 位，10000 天有效期）
2. **配置文件**：`keystore.properties`（包含密码和别名）
3. **Gradle 配置**：`signingConfigs.release` 自动读取配置
4. **安全注意**：签名文件已添加到 `.gitignore`，切勿提交

### 构建副作用：

- `assembleDebug` 在 `afterEvaluate` 中被配置为完成后执行 `generateVersionJson` 和 `bumpVersion`；带 `-PautoUploadVps=true` 时还会执行 `uploadDebugArtifactsToVps`。
- `generateBundledVersionJson` 会在构建前生成内置 `assets/version.json`；远端更新检查优先抓取 VPS 的 `/version-beta.json` 或 `/version-release.json`，并按 `channel`/`isBeta` 区分正式版和测试版。
- `bumpVersion` 会把 `version.properties` 的 `versionCode` 自动加 1。
- 发布脚本调用 `tools/upload_to_vps.py` 上传签名混淆后的 `app-release.apk`，远端按通道保存为 `app-beta.apk` 或 `app-release.apk`，并同步 `version-beta.json` / `version-release.json`。
- 上传配置位于 `local_private/vps/upload_config_hk.json`（香港 VPS）和 `upload_config_us.json`（美国 VPS），该目录被 `.gitignore` 排除。
- 如果只是验证代码能否编译，运行 Debug 构建后要留意 `version.properties` 会变更。
- 只有明确发布正式版时才修改 `versionName`；其他打包只允许递增内部 `versionCode`，不要把测试包伪装成正式版本。
- VPS 同时维护 beta/release 双通道文件：`app-beta.apk`、`version-beta.json`、`app-release.apk`、`version-release.json`；旧 `/downloads/...` 路径只做兼容转发。
- beta-only 分发时，`version.json` 带 `lastStableVersionCode`、`lastStableVersionName`、`betaNoticeVersionGap`；用户关闭测试版且本地版本明显落后上一个正式版时，App 会提示开启测试版或等待正式版。
- 设置页新增自动下载安装包能力，默认关闭；开启后自动检查到新版本会后台下载，下载完成后是否提示安装由独立子开关控制。

## 5.1 构建约定

| 配置项 | 说明 |
|--------|------|
| lint | `abortOnError=true`, `warningsAsErrors=true` |
| LeakCanary | `debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.14'` |
| autoBumpVersion | 参数控制版本号递增（默认 `true`） |
| CI/CD | `.github/workflows/ci.yml` 自动构建/测试/上传 |
| APK 签名 | `signingConfigs.release` 自动签名 Release APK |
| 敏感文件 | `keystore.properties` 和 `gamecenter.keystore` 已添加到 `.gitignore` |

## 5.2 BuildConfig 字段

`build.gradle` 中的 `local.properties` 解析会生成以下 `BuildConfig` 字段：

| 字段 | 来源 | 用途 |
|------|------|------|
| `SERVER_URL` | `server.url` | 主更新源地址（香港） |
| `SERVER_URL_FALLBACK` | `server.url.fallback` | 备用更新源地址（美国） |
| `RELAY_URL` | `relay.url` | HTTP Relay 地址 |
| `WS_URL` | `ws.url` | WebSocket Relay 地址 |
| `FEEDBACK_URL` | `feedback.url` | 反馈服务器地址 |

> **注意**：`WS_URL` 用于独立配置 WebSocket 连接地址，不依赖 `RELAY_URL`。

## 6. 关键依赖

依赖集中在 `app/build.gradle`：

```gradle
implementation 'androidx.appcompat:appcompat:1.7.1'
implementation 'com.google.android.material:material:1.12.0'
implementation 'androidx.constraintlayout:constraintlayout:2.2.1'
implementation 'androidx.navigation:navigation-fragment:2.8.9'
implementation 'androidx.navigation:navigation-ui:2.8.9'
implementation 'androidx.recyclerview:recyclerview:1.4.0'
implementation 'androidx.webkit:webkit:1.12.1'
```

项目目前没有独立测试模块，改动后最主要的验证手段是 `assembleDebug`、安装 APK、人工检查关键页面。

## 7. Manifest 和权限

主入口：

- `.MainActivity` exported，包含 LAUNCHER intent-filter。
- 游戏 Activity 基本都是 `exported=false`。
- 斗地主单机和联机 Activity 为横屏，其余大多为竖屏。
- 自更新安装使用 `androidx.core.content.FileProvider`，authority 为 `${applicationId}.update.fileprovider`。

权限包括网络、WiFi、定位、相机、存储、安装 APK 等。Android 高版本权限行为可能需要运行时处理，涉及工具箱或更新模块时要特别检查。

## 8. 开发约定

- 保持 Java 17 和现有 Android/Java 命名风格。
- 自定义 View 绘制要关注高 DPI、自适应尺寸、抗锯齿和生命周期释放。
- 新增资源按现有命名：`activity_*`, `fragment_*`, `dialog_*`, `item_*`, `ic_*`, `bg_*`。
- 避免把构建产物、反编译输出、参考 APK 目录混入主源码修改。
- 修改 `GamesFragment`、`AndroidManifest.xml`、`strings.xml` 时要一起检查入口、注册、文案是否一致。
- 更新模块涉及 `version.json`、测试版接收策略、下载、安装和证书策略，改动后至少验证远端文件解析、Android 7.0+ FileProvider 路径和 MD5 校验流程。
- VPS 更新服务改动后至少验证公网 `/version-beta.json`、`/app-beta.apk`、`version-release.json`、`app-release.apk`、旧 `/api/update/check` 和 `/downloads/...` 兼容路径。
- 反馈模块涉及 VPS 接口、诊断信息和邮箱兜底，改动后至少验证 `/api/feedback/health`、POST JSON、`feedback.log` 写入。
- 推送到 GitHub 前必须确认 `local_private/` 被忽略，并扫描密码、token、SSH 私钥、主机指纹和个人服务地址，避免把本机隐私配置提交到公开仓库。
- **签名文件安全**：`gamecenter.keystore` 和 `keystore.properties` 已添加到 `.gitignore`，切勿提交
- **发布流程**：使用 `auto-publish.bat` 或 `publish-all.ps1` 一键发布到三个更新源

### 8.1 服务器修改限制

**以下文件/服务禁止修改：**
- nginx 配置
- VPS 部署脚本
- Relay 架构（`vps/ddz_ws_relay/server.js`）
- Cloudflare DNS 配置

**如需修改服务器配置：**
1. 先说明需要修改的原因
2. 等待用户确认后再操作

### 8.2 斗地主 Beta 同步开发注意事项

- `DouDiZhuOnlineActivity.java` 是联机核心文件（3000+ 行），修改前务必先理解 seatTypes/seatClientIds 座位模型
- `remoteP2PMode=true` 时，`onClientConnected()` 在 WebSocket 模式下会 early return，座位分配依赖 `handleClientJoin()` 接收 JOIN 消息
- `broadcastSyncState()` 只会向 `SEAT_TYPE_REMOTE` 的座位发送 `SYNC_STATE`，没有 REMOTE 座位时发送 0 条消息
- `sendSyncStateNow()` 遍历座位时跳过 `cid < 0` 的座位（未分配客户端 ID）
- Client 端收到 `SYNC_STATE` 后回复 `STATE_ACK`，Host 收到 ACK 后确认客户端已同步
- Host 在没有 REMOTE 座位时点击开始游戏会提示"请等待远程玩家加入后再开始游戏"

### 8.3 扩展指南

- 网络错误：使用 `NetworkErrorHandler` 统一处理，避免硬编码 Toast
- 国际化：新增文本请同时更新 `values/strings.xml` 和 `values-en/strings.xml`
- 内存泄漏：Debug 版自动检测（LeakCanary），无需额外配置
- CI/CD：推送代码自动触发构建，产物保留30天

## 9. AI 协作提示

- 本文件是 UTF-8 编码。PowerShell 读取中文时请使用：

```powershell
Get-Content -Raw -Encoding UTF8 -LiteralPath PROJECT_CONTEXT.md
```

- 仓库可能处在大量未提交变更状态，修改前先看 `git status --short`，只改任务相关文件。
- 搜索文件优先用 `rg` 或 `rg --files`。
- 运行 `assembleDebug` 会递增版本号，除非任务需要打包，否则不要把版本号变化当作业务改动。
- 如果需要从反编译 APK 参考实现，优先只读 `apk_temp/`、`com.injoy.games.crazy.poker/`、`tools/jadx/`，不要把这些目录当作当前应用源码。

## 10. 常见任务清单

新增游戏：

```text
games/<newgame>/ -> Activity/View/Game
AndroidManifest.xml -> 注册 Activity
GameRegistry.java -> 添加入口
strings.xml -> 名称和描述
res/layout, res/drawable, res/raw -> 资源
```

新增 AI 功能模块：

```text
ai/
├── AiTaskRouter.java        # 任务路由：本地优先 → 云端 fallback
├── AiPreferences.java       # AI 偏好设置（API Key、模式选择、配额管理）
├── data/
│   ├── AiMessage.java       # 对话消息模型
│   ├── AiTask.java          # 任务模型
│   ├── AiResult.java        # 统一返回结果
│   └── AiProviderConfig.java # AI 提供商配置
├── cloud/
│   └── AiApiClient.java     # OpenAI 兼容 API 客户端
├── local/
│   └── LocalAiProcessor.java # 本地 AI 处理（规则摘要、关键词提取等）
└── ui/
    └── AiFragment.java      # AI 助手聊天页面
```

新增资源：

```text
res/layout/fragment_ai.xml            # AI 助手全页面布局
res/layout/item_tool_ai.xml           # 工具箱中 AI 入口布局
res/layout/item_ai_message.xml        # AI 消息列表项布局
res/drawable/bg_ai_message_user.xml   # 用户消息气泡背景
res/drawable/bg_ai_message_assistant.xml # AI 消息气泡背景
res/drawable/bg_ai_message_system.xml   # 系统消息气泡背景
```

---

最后更新：2026-05-19（战略优化：UpdateViewModel 协程化 + 网络层测试 + CI 质量门 + 安全加固 + 构建优化）
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
- `.gitignore` 的 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/gamecenter/app/ai/data/` 源码。
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 VPS/GitHub Release 发布仍以本机发布流程为准。
## 2026-05-18 文档同步：架构优化

- UpdateViewModel（@HiltViewModel + LiveData）替代 UpdatePresenter，密封类 UpdateCheckState/DownloadState 建模状态。
- OnlineRoomManager 组合式复用，替代 BaseOnlineActivity 继承，支持 Listener 接口。
- GameRegistry 双轨注册：静态硬编码 + @GameEntry 注解自动发现 + register() 动态注册，分类键名与本地化名称解耦。
- SaveManager 从 Java 迁移到 Kotlin（@Singleton + @Inject constructor），旧 Java 文件已删除。
- SettingsManager/OkHttpClientProvider/UpdateManager/SaveManager 添加 @Inject 构造函数，getInstance() 标记 @Deprecated。
- 新增 AppError（密封类，10 种错误类型）+ NetworkResult（类型安全结果封装）。
- AppModule 简化：仅保留 ExecutorService/OkHttpClient/AiPreferences/AppDatabase/DAO/ErrorReporter 的 @Provides。
- 版本号更新：versionCode=260, versionName=1.3.26。
## 2026-05-18 文档同步：低优先级代码质量

- Result.kt 重命名为 AppResult，消除与 kotlin.Result 标准库命名冲突。CrashHandler.kt 中的扩展函数同步更新。
- AiTask.status 从 String 改为 TaskStatus 枚举（PENDING/RUNNING/COMPLETED/FAILED），AiTaskRouter 和测试文件同步更新。
- AiResult.errorCode 新增 AiErrorCode 常量类（7 个常量），AiTaskRouter/AiApiClient/测试文件中裸字符串全部替换为常量引用。
- 16 处空 catch 块已补日志记录（Log.w/Log.d），保留原有注释说明忽略原因。
- OnlineRoomManager（35 个）+ AppSettingsDialog（13 个）共 48 个硬编码中文字符串提取到 strings.xml。
- CODE_WIKI.md 新增第 10 章"Java/Kotlin 混合边界规范"，文档化文件放置、跨语言调用注意事项、迁移优先级和同名类冲突规则。
## 2026-05-19 文档同步：战略优化

- UpdateViewModel 协程化：`viewModelScope.launch` + `suspendCancellableCoroutine` 包装 Java 回调为 suspend 函数，`CheckResult`/`DownloadResult` 密封类替代布尔标志，`checkJob`/`downloadJob` 替代 `isCheckingUpdate`/`isAutoDownloading`。
- 网络层测试：新增 `AiApiClientTest`（MockWebServer，8 个方法）和 `UpdateInfoTest`（JSON 解析，17 个方法）。
- CI 质量门：APK 大小报告、测试结果报告、Android Lint 执行和 Lint 问题报告。
- 安全加固：`allowBackup=false`，新增 `backup_rules.xml` 和 `data_extraction_rules.xml`，存储权限迁移（`READ_MEDIA_IMAGES`、`maxSdkVersion` 限制）。
- 构建优化：`MaterialCardView` 替代 `androidx.cardview.widget.CardView`，移除 `cardview:1.0.0` 依赖。
- 版本号更新：versionCode=262, versionName=1.3.26。

## 2026-05-19 Modularization Update

The project is no longer a pure single-module app. The first modularization pass introduced:

- `:core:common`: shared settings and utility/result types (`SettingsManager`, `AppResult`, `AppError`, `NetworkResult`, common Android/Kotlin helpers).
- `:core:network`: base networking (`OkHttpClientProvider`, request deduplication, relay URL/client helpers, room-code utilities, network error handling).
- `:core:update`: update subsystem (`UpdateManager`, checker/downloader/installer, notification helper, `UpdateInfo`, `SSLHelper`, `UpdateViewModel`).
- `:app`: shell application, main navigation, feature screens, games, tools, AI, resources, manifest, FileProvider declaration, and release/upload tasks.

Dependency direction should stay one-way: `app -> core:update -> core:network -> core:common`, plus direct `app -> core:network` and `app -> core:common` where needed. `CrashHandler` remains app-owned due to `ErrorReporter` coupling.
