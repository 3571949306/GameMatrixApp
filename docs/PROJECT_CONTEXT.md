# GameMatrixApp 项目上下文

## 2026-05-29 维护快照

> Canonical scope: this file is the maintainer handoff and repo-constraints document. Historical release reports and duplicated publishing walkthroughs were moved under `docs/archive/`. See `docs/DOCUMENTATION_INDEX.md`.

- 当前正式版目标：`1.4.0` / `versionCode 400`。
- Gradle 工具链：AGP 8.13.2, Kotlin 2.0.21, Hilt 2.57.2。
- 包名：`com.gamecenter.app`（非 `com.GameMatrix.app`）。
- 主界面已适配系统状态栏/导航栏安全区，避免顶部标题被手机状态栏遮挡。
- AI 本地模型切换已保存完整模型元数据，下载的新本地模型可直接被本地 LLM 路由识别，不再误提示云端模型未配置。
- 五子棋和中国象棋难度选择改为按钮式四档（低 / 中 / 高 / 大师），中档 AI 搜索预算下调；对局底部按钮改为两行等宽布局，窄屏不再溢出。

- GitHub 远端只保留 `main` 分支；后续提交、发布和 CI 触发均以 `main` 为唯一主线。
- Dependabot open alerts 已清零。根 `build.gradle` 通过 resolutionStrategy 统一约束高风险传递依赖版本，避免 Gradle 插件 classpath 与子模块依赖图重新引入已知漏洞版本。
- 本地 GitHub 上传不再依赖 xray TUN/虚拟网卡模式：Git 已配置 `http.https://github.com.proxy=http://127.0.0.1:10808`，只对 GitHub 走 v2rayN/xray 本地 HTTP 代理。
- 网络诊断与恢复步骤记录在 `docs/LOCAL_GITHUB_NETWORK.md`，可执行脚本为 `tools/network/Configure-GitHubProxy.ps1`。

> 给 AI 开发助手和维护者的快速入口。优先以仓库当前代码为准，本文档用于减少查找成本和避免常见误判。

## 0. 最近更新

| 版本 | 变更内容 |
|------|----------|
| **当前工作区** | **2026-05-26：modules.json 升级至 v11，新增23款游戏模块（blackjack/21点、breakout/打砖块、brotato、checkers/跳棋、dice/骰子、flappy/Flappy Bird、go/围棋、guess/猜数字、knife/飞刀大师、match/消消乐、memory/记忆翻牌、minesweeper/扫雷、pipeline/管道、plane/飞机大战、reaction/反应测试、rock/石头剪刀布、snake/贪吃蛇、sokoban/推箱子、sudoku/数独、tetris/俄罗斯方块、tic/井字棋、tiles/拼图、whack/打地鼠），游戏模块总数29，全部模块33；所有游戏ZIP上传至VPS /var/www/modules/和/var/www/update/modules/；证书绑定临时关闭（模拟器SIGSEGV兼容）；R8混淆Debug关闭；games_hall builtIn修复；模块下载SHA-256校验修复** |
| **当前工作区** | **模块框架全面修复：下载后校验错误/打不开/不更新显示三大问题修复；ModuleLoader 版本感知重加载+DEX缓存清理；ModuleDownloader 下载前清理旧文件+多源切换清理临时文件；ModuleVerifier 资源泄漏修复；ModuleAdapter 新增"更新"按钮逻辑；ModuleStoreActivity 已安装版本追踪+乱码修复** |
| **当前工作区** | **中国象棋/华容道重构为独立APK插件 + 动态资源加载集成（通过ModuleResourceLoader解决布局/资源闪退问题）** |
| **当前工作区** | **模块商店 BuiltIn 逻辑彻底修复：核心模块（browser/tools/ai）统一改为动态 APK 模块并成功部署** |
| **当前工作区** | **2026-05-25：游戏大厅恢复为主 APK 内置模块入口（`games_hall builtIn=true`，仍保留商店 APK 更新路径）；商店清单统一展示五子棋、斗地主、2048、中国象棋、华容道；AI/VPN/工具等导航模块安装后会刷新主页面底栏并可从商店直接打开；模块框架下载/校验/加载/显示全链路修复** |
| **当前工作区** | **模块商店目录结构重组：创建项目根目录“模块商店/”，分类组织“功能模块/”和“压缩模块/”，新增飞刀大师（knife）独立游戏模块，新增模块商店实时搜索框** |
| **当前工作区** | **v1.3.29：小游戏AI响应优化（去假延迟+动态预算+根并行）、CODE_WIKI版本同步、文档一致性修复** |
| **当前工作区** | **战略优化：UpdateViewModel 协程化（viewModelScope + suspendCancellableCoroutine + CheckResult/DownloadResult 密封类）、网络层测试（AiApiClientTest + UpdateInfoTest）、CI 质量门（APK 大小/测试结果/Lint 报告）、安全加固（allowBackup=false + backup_rules + data_extraction_rules + 存储权限迁移）、构建优化（MaterialCardView 替代 CardView）** |
| **当前工作区** | **低优先级代码质量：AppResult 重命名、TaskStatus 枚举、AiErrorCode 常量类、空 catch 块补日志、硬编码文案提取到 strings.xml（48 个）、Java/Kotlin 混合边界规范文档化** |
| **当前工作区** | **架构优化：UpdateViewModel（@HiltViewModel + LiveData）替代 UpdatePresenter、OnlineRoomManager 组合式复用、GameRegistry 双轨注册（@GameEntry + 动态注册）、SaveManager Kotlin 迁移、@Inject 构造函数迁移、AppError/NetworkResult 统一错误模型** |
| **当前工作区** | **斗地主联机农民 AI 增强：AIHelper 向 AIBot 传递地主/上次出牌者/队友剩余牌上下文，农民不压队友并在地主残牌时加强拦截；斗地主音效按座位复用男女声素材** |
| **当前工作区** | **棋类单机体验增强：五子棋、围棋、中国象棋人机模式新增提示；围棋连续虚手后按吃子、地盘与 6.5 贴目判定胜负并显示比分** |
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
| `app/src/main/java/com/GameMatrix/app/games/doudizhu/DouDiZhuNetworkHandler.java` | 斗地主网络处理（消息路由、客户端意图管理、重连补发） |
| `app/src/main/kotlin/com/GameMatrix/app/database/AppDatabase.kt` | Room 数据库入口（单例、提供 DAO） |
| `app/src/main/java/com/GameMatrix/app/utils/ErrorReporter.java` | 统一错误上报（VPS + 本地回退、限流） |
| `app/src/main/kotlin/com/GameMatrix/app/update/UpdateViewModel.kt` | 更新流程 ViewModel（@HiltViewModel + LiveData，替代 UpdatePresenter） |
| `app/src/main/kotlin/com/GameMatrix/app/util/AppError.kt` | 统一错误模型（密封类，10 种错误类型） |
| `app/src/main/kotlin/com/GameMatrix/app/util/NetworkResult.kt` | 网络请求结果封装（基于 AppError） |
| `app/src/main/java/com/GameMatrix/app/network/OnlineRoomManager.java` | 联机房间管理器（组合式复用，替代 BaseOnlineActivity 继承） |
| `app/src/main/java/com/GameMatrix/app/games/GameEntry.java` | @GameEntry 注解（游戏自声明元数据） |
| `app/src/main/kotlin/com/GameMatrix/app/SaveManager.kt` | 存档管理器（Kotlin 迁移，@Singleton + @Inject） |
| `app/src/main/java/com/GameMatrix/app/ai/data/TaskStatus.java` | AI 任务状态枚举（PENDING/RUNNING/COMPLETED/FAILED） |
| `app/src/main/java/com/GameMatrix/app/ai/data/AiErrorCode.java` | AI 错误码常量类（7 个常量，消除魔法字符串） |
| `app/src/main/kotlin/com/GameMatrix/app/util/AppResult.kt` | 通用结果封装（重命名自 Result.kt，避免与 kotlin.Result 冲突） |
| `app/src/test/java/com/GameMatrix/app/ai/cloud/AiApiClientTest.java` | AI API 客户端 MockWebServer 测试（8 个方法） |
| `app/src/test/java/com/GameMatrix/app/update/UpdateInfoTest.java` | 更新信息 JSON 解析测试（17 个方法） |
| `app/src/main/res/xml/backup_rules.xml` | Auto Backup 排除规则（sharedpref/database/update/） |
| `app/src/main/res/xml/data_extraction_rules.xml` | D2D 迁移和云备份排除规则 |
| `.github/workflows/ci.yml` | GitHub Actions CI/CD 工作流 |
| `keystore.properties` | APK 签名凭证配置（不提交 Git） |
| `app/GameMatrix.keystore` | APK 签名密钥库（不提交 Git） |
| `auto-publish.bat` | 一键发布脚本（Windows） |

## 1. 项目概览

| 项目 | 说明 |
| --- | --- |
| 名称 | GameMatrixApp |
| 类型 | Android 单模块应用 |
| 包名 | `com.GameMatrix.app` |
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

> **2026-06-19 变更**：美国 VPS 已下线，分发渠道精简为**两级下载源**（香港 VPS → GitHub Releases）。

本项目采用**两级下载源**架构，App 下载更新时按以下优先级依次尝试：

| 优先级 | 下载源 | 地址 | 超时配置 |
|--------|--------|------|----------|
| 1 | 主更新源（香港 VPS） | `https://hk-update.<YOUR_DOMAIN>` | 连接 3s / 读取 5s |
| 2 | GitHub Releases | `https://github.com/3571949306/GameMatrixApp/releases/latest` | 连接 5s / 读取 10s |

**自动换源机制**：下载开始后 3 秒检测速度，如果低于 50 KB/s，自动删除临时文件并切换到下一个下载源。

**Beta 测试版仅上传到香港 VPS（不上传 GitHub Releases），正式版同时上传到香港 VPS 和 GitHub Releases。**

## 1.3 联机架构

本项目采用**房主权威**联机模型，所有联机游戏共享 `com.GameMatrix.app.network` 公共模块：

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
versionCode=267
versionName=1.3.29
```

## 2. 代码结构

```text
GameMatrixApp/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── pkgInfo.txt
│       ├── java/com/GameMatrix/app/
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

- `app/src/main/java/com/GameMatrix/app/fragments`: 三个主页面，游戏大厅、工具箱、内置浏览器。`AiFragment` 在 ai/ui 包中。
- `app/src/main/java/com/GameMatrix/app/ai`: AI 助手模块。包含路由调度 (`AiTaskRouter`)、云端客户端 (`AiApiClient`)、本地处理器 (`LocalAiProcessor`)、历史与收藏 (`AiHistoryStore`)、模板 (`AiTemplateManager`)、偏好设置 (`AiPreferences`) 和数据模型。默认 DeepSeek API，可在 AI 页切换 OpenAI、阿里云通义、硅基流动、智谱 AI、零一万物、月之暗面 Kimi 等多家 OpenAI 兼容模型；本地模型入口按低端/中端/高端手机分档展示。
- `app/src/main/java/com/GameMatrix/app/games`: 各小游戏模块。多数模块采用 `Activity + View + Game` 的简单分层。
- `app/src/main/java/com/GameMatrix/app/settings`: 设置弹窗与设置项交互。`AppSettingsDialog` 已从 `GamesFragment` 拆出，负责主题、配色、版本更新和反馈入口。
- `app/src/main/java/com/GameMatrix/app/tools`: 工具箱拆分后的共享结构和独立 binder。当前包含功能区模型/配置存储、剪贴板/哈希/颜色取色器 binder，以及 `AdvancedToolBinders` 中的网络体检、诊断报告、DNS 查询、局域网扫描、编码/时间戳/JSON、文件哈希、二维码增强、颜色增强、权限隐私说明。AI 不再嵌入工具箱，入口在底部导航。
- `app/src/main/java/com/GameMatrix/app/update`: 自更新模块，包含 `version.json` 检查、正式/测试版策略、下载、MD5 校验、FileProvider 安装。`UpdateViewModel`（Kotlin）提供生命周期安全的更新状态管理，替代旧 `UpdatePresenter`。
- `vps/var_www_update`: VPS 更新和反馈模板；更新服务部署为 `/var/www/update/server.py`，App 上传目录为 `/var/www/update/app/`，反馈目录为 `/var/www/update/feedback/`。
- `vps/var_www_update/feedback`: VPS 反馈接收模板，部署目标为 `/var/www/update/feedback/`，通过 nginx `/api/feedback` 转发到本机 `127.0.0.1:9011`；反馈会按类型保存到 `Bug反馈/` 和 `功能建议/`，文件名包含编号、类型、时间和反馈摘要。
- `app/src/main/java/com/GameMatrix/app/views`: 主题/颜色选择相关自定义控件。
- `app/src/main/res/raw`: 音效资源较多，尤其是斗地主语音和背景音。
- `tools/jadx`, `apk_temp`, `com.injoy.games.crazy.poker`: 反编译/参考 APK 相关目录，不属于主应用源码路径，修改主功能时通常不要动。

## 3. 主架构

`MainActivity` 是底部导航容器，负责挂载三个 Fragment：

- `GamesFragment`: 游戏大厅，使用 `TabLayout + RecyclerView` 展示游戏卡片，支持搜索、最近游玩、收藏。右上角设置入口委托给 `settings/AppSettingsDialog`；反馈入口优先 POST 到 VPS，公开仓库不保存个人邮箱兜底收件人。
- `ToolsFragment`: 工具箱，包含网络、设备、颜色、二维码、剪贴板等实用工具入口。支持工具搜索、收藏、最近使用、排序、显隐和单双列布局。功能区模型和配置存储已拆到 `tools/ToolSection` 与 `tools/ToolSectionStore`；剪贴板、哈希、颜色取色器和增强工具已拆成独立 binder。
- `AiFragment`: AI 独立底部导航页，支持任务类型选择、云端模型选择、本地模型分档、模板快捷填充、历史搜索、收藏筛选和导出。
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
- `DouDiZhuAIHelper` 会为联机 AI 构建 `AIBot.GameContext`，包含地主座位、最后出牌者、队友座位与剩余牌数；改斗地主 AI 时要优先保持农民协作逻辑。
- `DouDiZhuSoundManager` 支持 `bid/pass/cards(..., seatIndex)`，按座位选择男女声与更多已有 raw 音效；新增调用时优先传真实座位。
- `doudizhu/model`, `doudizhu/network`, `doudizhu/utils`: 承载牌型、联机网络和规则工具。
- `gomoku/`, `go/`, `chinesechess/`: 单机人机模式都有提示入口。五子棋用 `GomokuAI#getBestMove` 标记棋盘提示；围棋用 `GoGame#getBestMove` 与 `GoView#showHint`；象棋用 `ChineseChessAI#getBestMove` 选中建议棋子并显示目标坐标。
- `GoGame` 现在提供 `calculateScore()`、`getWinner()`、`getResultText()`，连续虚手终局后按吃子、地盘与 6.5 贴目输出胜负。
- `klotski/`: 华容道已重做核心棋盘和 BFS 提示求解器；提示必须从当前棋盘重新计算，目标是连续引导曹操移动到下方出口。

## 5. 构建与版本

当前版本：`versionCode=341`, `versionName=1.4.0`。当前工作区在该版本基础上完成了小游戏AI响应优化（v1.3.29）和小游戏AI响应优化（去假延迟+动态预算+根并行）

Windows 下推荐命令：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat :app:assembleRelease -PupdateChannel=stable -PskipReleaseLint=true
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

1. **密钥库**：`app/GameMatrix.keystore`（RSA 2048 位，10000 天有效期）
2. **配置文件**：`keystore.properties`（包含密码和别名）
3. **Gradle 配置**：`signingConfigs.release` 自动读取配置
4. **安全注意**：签名文件已添加到 `.gitignore`，切勿提交

### 构建副作用：

- `assembleDebug` 在 `afterEvaluate` 中被配置为完成后执行 `generateVersionJson` 和 `bumpVersion`；带 `-PautoUploadVps=true` 时还会执行 `uploadDebugArtifactsToVps`。
- `generateBundledVersionJson` 会在构建前生成内置 `assets/version.json`；远端更新检查优先抓取 VPS 的 `/version-beta.json` 或 `/version-release.json`，并按 `channel`/`isBeta` 区分正式版和测试版。
- `bumpVersion` 会把 `version.properties` 的 `versionCode` 自动加 1。
- 发布脚本调用 `tools/upload_to_vps.py` 上传签名混淆后的 `app-release.apk`，远端按通道保存为 `app-beta.apk` 或 `app-release.apk`，并同步 `version-beta.json` / `version-release.json`。
- `-PskipReleaseLint=true` 只用于规避 AGP 8.13 `lintVitalReportRelease` 路径变量序列化缺陷；默认不传该参数时 release lint 仍开启。
- 上传配置位于 `local_private/vps/upload_config_hk.json`（香港 VPS），该目录被 `.gitignore` 排除。美国 VPS 配置（`upload_config_us.json`）已于 2026-06-19 废弃。
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
| 敏感文件 | `keystore.properties` 和 `GameMatrix.keystore` 已添加到 `.gitignore` |

## 5.2 BuildConfig 字段

`build.gradle` 中的 `local.properties` 解析会生成以下 `BuildConfig` 字段：

| 字段 | 来源 | 用途 |
|------|------|------|
| `SERVER_URL` | `server.url` | 主更新源地址（香港） |
| `SERVER_URL_FALLBACK` | `server.url.fallback` | ~~备用更新源地址（美国）~~ 已废弃（2026-06-19），保留空值向后兼容 |
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
- **签名文件安全**：`GameMatrix.keystore` 和 `keystore.properties` 已添加到 `.gitignore`，切勿提交
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

最后更新：2026-05-26（modules.json v11：新增23款游戏模块，游戏模块总数29，全部模块33；证书绑定临时关闭；R8混淆Debug关闭；games_hall builtIn修复；SHA-256校验修复）
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

## 2026-05-25 文档同步：中国象棋/华容道独立APK插件化、内置模块BuiltIn逻辑修复与模块商店重组

- **中国象棋/华容道重构为独立APK插件**：将中国象棋 (`chinesechess`) 和华容道 (`klotski`) 完全重构为独立的 APK 功能模块（v2.0.0），支持通过模块商店动态下载、校验和加载。
- **解决 dynamic 模块布局/资源加载闪退问题**：在 `com.gamecenter.app.modules.ModuleLoader` 中集成了 `ModuleResourceLoader`，动态装载外部 APK 时的 AssetManager 和 Resources，解决了外部 APK 引用局部资源（如本地 drawable 等）导致 inflate 闪退的问题。
- **内置模块 BuiltIn 缺陷修复**：修复了 modules.json 中内置模块（`browser`、`tools`、`ai`）被错误标记为 `builtIn: true` 导致模块商店里无法展示“下载”和“启用”点击无反应的逻辑，统一修改为 `builtIn: false` 的动态 APK 模块。
- **新增模块商店实时搜索框**：在 `ModuleStoreActivity.kt` 顶部增加了实时搜索框 `etModuleSearch`，支持过滤游戏/工具/AI等功能模块。
- **目录结构重组**：创建了项目根目录下的 `模块商店/` 文件夹。将所有压缩游戏包（25个ZIP文件）移入 `模块商店/压缩模块/`。将所有独立功能模块（`vpn`、`chinesechess`、`game2048`、`klotski` 等）移入 `模块商店/功能模块/` 下，并更新 `settings.gradle` 的模块引用路径。

## 2026-05-25 文档同步：模块框架全链路修复

- **ModuleLoader 版本感知重加载**：`loadModule` 不再无条件返回缓存实例，而是对比已安装版本与 manifest 版本。版本变更时自动卸载旧实例、清除 DEX 优化缓存（`modules_opt/`）后重新加载，解决模块更新后仍运行旧代码的问题。
- **ModuleDownloader 下载前清理**：`doDownload` 在开始下载前删除旧模块文件和残留临时文件，避免文件名冲突导致新文件覆盖失败。多源切换时也清理临时文件，防止断点续传拼接出损坏文件。
- **ModuleVerifier 资源泄漏修复**：`computeSha256` 和 `verifyDexFile` 中的 `FileInputStream` 改为 try-finally 确保异常时也能关闭流。
- **ModuleManager 下载后卸载旧实例**：`downloadModule` 的 `onComplete` 回调中先调用 `ModuleLoader.unloadModule` 卸载旧实例，确保下次加载使用新文件。
- **ModuleManager 版本校验增强**：`downloadModule` 不再仅比较版本号，还会检查文件是否存在且 SHA-256 校验通过，文件损坏时自动重新下载。
- **ModuleAdapter 更新按钮**：新增 `installedVersions` 映射和 `hasUpdate` 判断，已安装版本低于远程版本时显示橙色"更新"按钮和版本变更提示（如"有更新 v100→v200"）。
- **ModuleStoreActivity 版本追踪**：新增 `buildInstalledVersionsMap()` 方法，`applyCategoryFilter` 和下载完成回调中同步刷新已安装版本信息，确保更新状态实时反映。
- **ModuleStoreActivity 乱码修复**：修复 `openModule` 中 Toast 文本"妯″潃鍔犺浇澶辫触"为正确的"模块加载失败"。
- **ModuleStoreActivity ACTION_UPDATE**：新增 `ACTION_UPDATE` 处理分支，点击"更新"按钮时触发下载流程。

