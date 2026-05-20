# GameCenterApp — Code Wiki

## 2026-05-20 Maintenance Notes

- GitHub branch policy: `main` is the only maintained remote branch.
- Dependabot open alerts are currently `0`; root Gradle security constraints cover high-risk transitive dependencies from both plugin classpath and project configurations.
- Local GitHub upload should use the GitHub-only Git proxy `http://127.0.0.1:10808` instead of requiring xray TUN mode. See `docs/LOCAL_GITHUB_NETWORK.md`.
- New helper: `tools/network/Configure-GitHubProxy.ps1`.

> **项目名称**: GameCenterApp  
> **包名**: `com.gamecenter.app`  
> **当前版本**: 1.3.26 (versionCode 262)  
> **最低 SDK**: 24 (Android 7.0) | **目标 SDK**: 35  
> **语言**: Java (主体) + Kotlin (数据层/工具层)  
> **构建系统**: Gradle 8.13.2 + AGP 8.13.2  
> **依赖注入**: Hilt (Dagger)  
> **数据库**: Room  

---

## 目录

1. [项目概述](#1-项目概述)
2. [整体架构](#2-整体架构)
3. [目录结构](#3-目录结构)
4. [核心模块详解](#4-核心模块详解)
   - 4.1 [应用入口层 (App / MainActivity)](#41-应用入口层)
   - 4.2 [游戏模块 (games)](#42-游戏模块)
   - 4.3 [网络与联机模块 (network)](#43-网络与联机模块)
   - 4.4 [AI 模块 (ai)](#44-ai-模块)
   - 4.5 [工具箱模块 (tools)](#45-工具箱模块)
   - 4.6 [更新模块 (update)](#46-更新模块)
   - 4.7 [设置与主题管理](#47-设置与主题管理)
   - 4.8 [数据层 (database / di)](#48-数据层)
   - 4.9 [Fragment 层](#49-fragment-层)
   - 4.10 [工具类 (utils)](#410-工具类)
5. [服务端组件](#5-服务端组件)
6. [依赖关系图](#6-依赖关系图)
7. [构建与发布](#7-构建与发布)
8. [测试体系](#8-测试体系)
9. [CI/CD](#9-cicd)
10. [Java/Kotlin 混合边界规范](#10-javakotlin-混合边界规范)

---

## 1. 项目概述

GameCenterApp 是一款 Android 综合应用，集成了 **25+ 款小游戏**、**网络工具箱**、**AI 助手**和**应用内更新**四大核心功能。应用采用单 Activity + 多 Fragment 的 Navigation Component 架构，通过底部导航栏切换四大功能区：游戏大厅、浏览器、工具箱和 AI 助手。

### 核心特性

| 功能域 | 说明 |
|--------|------|
| **游戏中心** | 25+ 款内置游戏，涵盖经典棋类、益智、休闲、反应力等分类，支持单机和联机对战 |
| **联机对战** | 基于 WebSocket 中转服务器的房间制联机，支持斗地主、五子棋、象棋、围棋等 |
| **网络工具箱** | 20+ 款网络诊断工具（Ping、Traceroute、端口扫描、子网计算等） |
| **AI 助手** | 本地优先策略（规则引擎 + Gemma 本地 LLM），可回退至云端 API（DeepSeek/OpenAI/通义等） |
| **应用更新** | 双通道分发（Beta/Stable），支持自动检查、自动下载、MD5 校验、强制更新 |
| **主题定制** | 8 套配色方案 + 浅色/深色/跟随系统三种模式 |

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                      Android App                        │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │  Games   │  │ Browser  │  │  Tools   │  │   AI   │ │
│  │ Fragment │  │ Fragment │  │ Fragment │  │Fragment│ │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └───┬────┘ │
│       │              │              │             │      │
│  ┌────┴──────────────┴──────────────┴─────────────┴───┐ │
│  │              MainActivity (Navigation)              │ │
│  └──────────────────────┬─────────────────────────────┘ │
│                         │                               │
│  ┌──────────────────────┴─────────────────────────────┐ │
│  │                    App (Application)                │ │
│  │   @HiltAndroidApp · 主题 · 语言 · 配色 · 更新门控    │ │
│  └────────────────────────────────────────────────────┘ │
│                                                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐ │
│  │  games   │ │ network  │ │   ai     │ │   update   │ │
│  │ 游戏引擎  │ │ 联机通信  │ │ AI调度   │ │  更新管理   │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘ │
│                                                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐ │
│  │  tools   │ │ settings │ │ database │ │    di      │ │
│  │ 网络工具  │ │ 偏好管理  │ │ Room DB  │ │ Hilt DI   │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────────┘ │
└─────────────────────────────────────────────────────────┘
          │                    │
          ▼                    ▼
┌─────────────────┐  ┌─────────────────┐
│  VPS 更新服务器  │  │  WebSocket 中转  │
│  (Python)       │  │  (Node.js)      │
└─────────────────┘  └─────────────────┘
```

### 架构模式

- **UI 层**: 单 Activity + Navigation Component + Fragment
- **业务层**: ViewModel + LiveData（更新模块），Manager 模式（其他模块），逐步迁移中
- **数据层**: Room 数据库 + SharedPreferences + 加密存储 (EncryptedSharedPreferences)
- **依赖注入**: Hilt (Dagger) 统一管理单例，`@Inject` 构造函数优先，`getInstance()` 已标记 `@Deprecated`
- **通信模式**: LiveData（更新模块），回调接口 + 主线程 Handler（其他模块）

---

## 3. 目录结构

```
GameCenterApp/
├── app/                              # Android 应用模块
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/gamecenter/app/
│   │   │   │   ├── App.java                   # Application 入口
│   │   │   │   ├── MainActivity.java          # 主 Activity（使用 UpdateViewModel）
│   │   │   │   ├── ColorSchemeManager.java    # 配色方案管理
│   │   │   │   ├── SettingsManager.java       # 设置管理器（@Inject 构造函数）
│   │   │   │   ├── SaveManager.java           # 存档管理器
│   │   │   │   ├── PermissionHelper.java      # 权限辅助
│   │   │   │   ├── ai/                        # AI 模块
│   │   │   │   │   ├── AiTaskRouter.java      # AI 任务调度中心
│   │   │   │   │   ├── AiPreferences.java     # AI 偏好（加密存储）
│   │   │   │   │   ├── cloud/                 # 云端 AI 客户端
│   │   │   │   │   ├── data/                  # AI 数据模型
│   │   │   │   │   ├── local/                 # 本地 AI 处理
│   │   │   │   │   ├── model/                 # 模型下载管理
│   │   │   │   │   └── ui/                    # AI Fragment
│   │   │   │   ├── fragments/                 # 页面 Fragment
│   │   │   │   │   ├── BrowserFragment.java
│   │   │   │   │   ├── GamesFragment.java
│   │   │   │   │   └── ToolsFragment.java
│   │   │   │   ├── games/                     # 游戏模块
│   │   │   │   │   ├── BaseGameActivity.java  # 游戏基类
│   │   │   │   │   ├── GameRegistry.java      # 游戏注册中心（双轨制：静态+动态注册）
│   │   │   │   │   ├── GameEntry.java         # @GameEntry 注解（自声明游戏元数据）
│   │   │   │   │   ├── GameStats.java
│   │   │   │   │   ├── GameUsageStore.java
│   │   │   │   │   ├── InteractiveTutorialDialog.java
│   │   │   │   │   └── {各游戏子包}/           # 25+ 游戏实现
│   │   │   │   ├── initializers/              # App Startup 初始化
│   │   │   │   │   └── NetworkInitializer.java
│   │   │   │   ├── network/                   # 网络与联机模块
│   │   │   │   │   ├── BaseOnlineActivity.java # 联机基类（模板方法）
│   │   │   │   │   ├── OnlineRoomManager.java  # 房间管理器（组合式复用）
│   │   │   │   │   ├── GameSocketClient.java  # WebSocket 客户端
│   │   │   │   │   ├── GameSocketServer.java  # WebSocket 服务器
│   │   │   │   │   ├── LANManager.java        # 局域网发现
│   │   │   │   │   ├── OkHttpClientProvider.java # HTTP 客户端（@Inject 构造函数）
│   │   │   │   │   ├── RelayClientHelper.java # 中转客户端
│   │   │   │   │   ├── RelayHostHelper.java   # 中转主机
│   │   │   │   │   ├── RelayHttpClient.java   # 中转 HTTP
│   │   │   │   │   ├── WebSocketClientHelper.java
│   │   │   │   │   ├── WebSocketHostHelper.java
│   │   │   │   │   ├── OnlineChatHelper.java  # 联机聊天
│   │   │   │   │   └── RemoteP2PUtil.java     # 远程 P2P
│   │   │   │   ├── settings/                  # 设置对话框
│   │   │   │   │   └── AppSettingsDialog.java
│   │   │   │   ├── tools/                     # 网络工具箱
│   │   │   │   │   ├── ToolBinder.java        # 工具绑定接口
│   │   │   │   │   ├── ToolHelper.java        # 工具辅助类
│   │   │   │   │   ├── ToolSection.java       # 工具分区
│   │   │   │   │   ├── ToolSectionStore.java  # 分区存储
│   │   │   │   │   ├── AdvancedToolBinders.java
│   │   │   │   │   └── {各工具Binder}/        # 20+ 工具实现
│   │   │   │   ├── update/                    # 更新模块
│   │   │   │   │   ├── UpdateManager.java     # 更新管理器（门面）
│   │   │   │   │   ├── UpdateChecker.java     # 更新检查
│   │   │   │   │   ├── UpdateDownloader.java  # 下载管理
│   │   │   │   │   ├── UpdateInstaller.java   # 安装管理
│   │   │   │   │   ├── UpdateInfo.java        # 更新信息模型
│   │   │   │   │   ├── UpdatePresenter.java   # 更新展示器
│   │   │   │   │   ├── UpdateNotificationHelper.java
│   │   │   │   │   └── SSLHelper.java         # SSL 信任
│   │   │   │   ├── utils/                     # 通用工具
│   │   │   │   │   ├── ErrorReporter.java     # 错误上报
│   │   │   │   │   ├── I18nHelper.java        # 国际化
│   │   │   │   │   ├── NetworkErrorHandler.java
│   │   │   │   │   ├── SoundManager.java      # 音效管理
│   │   │   │   │   └── SystemInfoCollector.java
│   │   │   │   └── views/                     # 自定义 View
│   │   │   │       ├── ColorAlphaBar.java
│   │   │   │       ├── ColorHueBar.java
│   │   │   │       └── ColorSVPanel.java
│   │   │   ├── kotlin/com/gamecenter/app/
│   │   │   │   ├── database/                  # Room 数据库
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── dao/
│   │   │   │   │   └── entity/
│   │   │   │   ├── di/                        # Hilt 依赖注入
│   │   │   │   │   └── AppModule.kt
│   │   │   │   └── util/                      # Kotlin 工具
│   │   │   │       ├── Extensions.kt
│   │   │   │       ├── Result.kt
│   │   │   │       ├── MemoryUtils.kt
│   │   │   │       └── CrashHandler.kt
│   │   │   ├── res/                           # 资源文件
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                              # 单元测试
│   │   └── androidTest/                       # 集成测试
│   └── build.gradle                           # 应用构建配置
├── vps/                               # 服务端组件
│   ├── ddz_ws_relay/                  # WebSocket 中转服务器
│   │   ├── server.js
│   │   └── package.json
│   └── var_www_update/                # 更新服务器
│       ├── update_server.py
│       ├── ddz_relay/
│       └── feedback/
├── tools/                             # 构建与发布工具
│   ├── upload_to_vps.py
│   ├── upload_to_github_release.py
│   └── publish-all.py
├── docs/                              # 项目文档
├── gradle/                            # Gradle 配置
│   └── libs.versions.toml             # 版本目录
├── build.gradle                       # 项目构建配置
├── settings.gradle                    # 模块设置
├── version.properties                 # 版本号配置
└── .github/workflows/ci.yml          # CI/CD 配置
```

---

## 4. 核心模块详解

### 4.1 应用入口层

#### [App.java](app/src/main/java/com/gamecenter/app/App.java)

应用全局入口类，标注 `@HiltAndroidApp`，负责：

| 职责 | 实现方式 |
|------|---------|
| 语言设置 | `AppCompatDelegate.setApplicationLocales()` |
| 主题管理 | `AppCompatDelegate.setDefaultNightMode()` |
| 配色方案 | 通过 `ActivityLifecycleCallbacks` 在每个 Activity 创建时自动应用 |
| 更新检查 | `shouldAutoCheckUpdate()` 一次性门控，确保每次启动仅自动检查一次 |

**关键方法**:
- `applyTheme()` — 根据 SettingsManager 读取主题模式，同步更新 `isDarkMode` 状态
- `applyColorScheme(Activity)` — 为每个 Activity 应用用户选择的配色方案
- `refreshColorScheme(Activity)` — 静态方法，供设置页面在更改配色后立即刷新

#### [MainActivity.java](app/src/main/java/com/gamecenter/app/MainActivity.java)

主界面 Activity，标注 `@AndroidEntryPoint`，作为所有 Fragment 的宿主：

| 职责 | 实现方式 |
|------|---------|
| 导航管理 | Navigation Component + BottomNavigationView |
| 权限处理 | `PermissionHelper` + `ActivityResultLauncher` |
| 更新检查 | 延迟 2 秒 + `WeakReference` 防泄漏 |

**内部类** `SafeUpdateCheckRunnable` — 使用 WeakReference 持有 Activity，三重防御（引用回收 / finishing / destroyed）防止泄漏。

---

### 4.2 游戏模块

#### [GameRegistry.java](app/src/main/java/com/gamecenter/app/games/GameRegistry.java)

游戏注册中心（final 工具类），采用**双轨制**管理游戏注册信息：

**注册方式**:

| 方式 | 说明 | 适用场景 |
|------|------|---------|
| 静态注册 | `buildStaticCategories()` 硬编码的游戏列表 | 内置游戏，保证启动速度 |
| 动态注册 | `register(Entry)` / `registerAll()` 手动注册 | 插件/扩展游戏 |
| 注解扫描 | `@GameEntry` 注解 + `scanAnnotatedGames()` | 新增游戏自声明元数据 |

**动态注册 API**:
- `register(Entry)` — 注册单个游戏，同 ID 自动去重
- `registerAll(List<Entry>)` — 批量注册，返回成功数量
- `clearDynamicEntries()` — 清除所有动态条目（测试用）
- `scanAnnotatedGames(Context)` — 扫描 APK 中标注 `@GameEntry` 的 Activity

**数据结构**:
- `Category` — 游戏分类，包含分类名称、游戏列表和分类键名（`categoryKey`，非本地化）
- `Entry` — 游戏条目，新增 `categoryKey` 字段，支持分类标识符与本地化名称解耦

**分类键名常量**: `CATEGORY_CLASSICS` / `CATEGORY_PUZZLE` / `CATEGORY_CASUAL` / `CATEGORY_REACTION` / `CATEGORY_OTHER`

#### [GameEntry.java](app/src/main/java/com/gamecenter/app/games/GameEntry.java)

游戏条目注解（`@Target(TYPE)` + `@Retention(RUNTIME)`），用于在 Activity 类上声明游戏元数据：

```java
@GameEntry(
    id = "gomoku",
    iconRes = R.drawable.ic_gomoku,
    nameRes = R.string.gomoku,
    descRes = R.string.gomoku_desc,
    category = "classics"
)
public class GomokuActivity extends BaseGameActivity { ... }
```

| 属性 | 说明 |
|------|------|
| `id` | 游戏唯一标识符（必填） |
| `iconRes` | 图标资源 ID |
| `nameRes` | 名称字符串资源 ID（优先于 `name`） |
| `descRes` | 描述字符串资源 ID（优先于 `desc`） |
| `name` | 硬编码名称（不支持国际化） |
| `desc` | 硬编码描述（不支持国际化） |
| `category` | 分类键名：classics/puzzle/casual/reaction/other |

**五大分类与游戏列表**:

| 分类 | 游戏 |
|------|------|
| **经典** | 五子棋、围棋、中国象棋、贪吃蛇、俄罗斯方块、斗地主、Brotato |
| **益智** | 2048、数独、推箱子、管道、华容道 |
| **休闲** | 打砖块、打地鼠、消消乐、21点、跳棋 |
| **反应力** | Flappy Bird、别踩白块、飞机大战、石头剪刀布、反应测试 |
| **其他** | 井字棋、记忆翻牌、猜数字、骰子 |

**关键方法**:
- `getCategories(Context)` — 返回不可修改的分类列表，从字符串资源读取名称（支持国际化）
- `flatten(List<Category>)` — 将分类列表扁平化为游戏条目列表

#### [BaseGameActivity.java](app/src/main/java/com/gamecenter/app/games/BaseGameActivity.java)

所有游戏 Activity 的抽象基类，提供通用基础设施：

| 能力 | 实现 |
|------|------|
| 音效 | `SoundManager`，由 SettingsManager 控制开关 |
| 震动 | `Vibrator`，短震 50ms / 长震 200ms |
| 动画 | `AnimationUtils`，支持带回调的动画 |
| 生命周期 | onPause 暂停背景音乐，onResume 恢复，onDestroy 释放资源 |

**模板方法**: `loadGameSounds()` — 子类重写以加载各自音效资源

---

### 4.3 网络与联机模块

#### [BaseOnlineActivity.java](app/src/main/java/com/gamecenter/app/network/BaseOnlineActivity.java)

联机游戏 Activity 基类（模板方法模式），封装房间管理、聊天、连接状态等联机通用逻辑。

> ⚠️ 当前各游戏的 OnlineActivity（GomokuOnlineActivity、GoOnlineActivity 等）并未继承此类，
> 而是各自独立实现联机逻辑。新增游戏建议使用组合方式，通过 `OnlineRoomManager` 复用联机功能。

#### [OnlineRoomManager.java](app/src/main/java/com/gamecenter/app/network/OnlineRoomManager.java)

联机房间管理器（组合式），从 `BaseOnlineActivity` 中提取的独立组件，使各游戏 OnlineActivity 可以通过组合方式复用联机逻辑，而不必继承 BaseOnlineActivity。

**使用方式**:
```java
OnlineRoomManager roomManager = new OnlineRoomManager(activity, "gomoku_p2p", "五子棋");
roomManager.initServer();
roomManager.initClient();
roomManager.initChatHelper();
roomManager.setListener(new OnlineRoomManager.Listener() {
    void onGameStarted() { ... }
    void onGameMessageReceived(JSONObject message) { ... }
    void onGameReset() { ... }
});
```

**核心 API**:

| 方法 | 功能 |
|------|------|
| `initServer()` / `initClient()` / `initChatHelper()` | 初始化网络组件 |
| `initLobbyLayout(LinearLayout)` | 构建大厅 UI |
| `initChatViews(LinearLayout)` | 构建聊天 UI |
| `createRoom()` / `joinRoom(code)` / `leaveRoom()` | 房间管理 |
| `broadcast(JSONObject)` | 主机广播消息 |
| `showGameScreen()` / `showLobby()` | UI 切换 |
| `cleanup()` | 资源释放 |

**Listener 接口**: `onGameStarted()` / `onGameMessageReceived(JSONObject)` / `onGameReset()`

#### [LANManager.java](app/src/main/java/com/gamecenter/app/network/LANManager.java)

局域网设备发现管理器（单例），支持双模式：

| 模式 | 协议 | 适用场景 |
|------|------|---------|
| NSD (优先) | mDNS/DNS-SD (`_doudizhu._tcp.`) | Android 设备间 |
| UDP 广播 (备选) | JSON 报文 + 255.255.255.255 | NSD 不可用时 |

**关键参数**: 发现端口 9877，广播间隔 3s，主机过期超时 8s

#### [OkHttpClientProvider.java](app/src/main/java/com/gamecenter/app/network/OkHttpClientProvider.java)

HTTP/WebSocket 客户端提供者（单例），通过 Hilt 注入：

| 客户端 | 连接超时 | 读取超时 | 缓存 | 重试 | 去重 |
|--------|---------|---------|------|------|------|
| httpClient | 15s | 30s | 50MB | 3次线性退避 | ✓ |
| webSocketClient | 10s | 无超时 | - | 连接失败重试 | - |

**内部类** `RetryInterceptor` — 线性退避策略（delay × attempt+1），最多重试 3 次。

---

### 4.4 AI 模块

#### [AiTaskRouter.java](app/src/main/java/com/gamecenter/app/ai/AiTaskRouter.java)

AI 功能调度中心，核心设计遵循**本地优先 (Local First)** 策略：

```
任务提交 → 本地 LLM (Gemma3-1B) → 本地规则引擎 → 云端 API
```

**路由决策流程**:
1. 检查本地优先开关 (`AiPreferences.isLocalFirst()`)
2. 尝试本地 LLM（需模型已下载 + 内存 ≥ 3GB）
3. 匹配本地规则引擎（OCR/摘要/关键词/分类等）
4. 翻译/润色/问答类任务：仅无 API Key 时本地兜底
5. 云端调用前依次检查：网络 → 免费额度 → API Key

**支持的任务类型**:

| 类型 | 本地规则 | 本地 LLM | 云端 |
|------|---------|---------|------|
| ocr / ocr_clean | ✓ | - | - |
| summary | ✓ | ✓ | ✓ |
| translate | 仅无Key | ✓ | ✓ |
| rewrite | 仅无Key | ✓ | ✓ |
| qa / qa_pairs | 仅无Key | ✓ | ✓ |
| keywords | ✓ | ✓ | ✓ |
| classify | ✓ | ✓ | ✓ |
| chat | - | ✓ | ✓ |
| template | ✓ | - | - |

**关键设计**:
- 单线程线程池 (`aiExecutor`) 串行执行，避免并发推理冲突
- `LocalLlmOutputGuard` 校验输出质量，防止退化输出（乱码/重复）
- 结果通过 `Handler` 回调到主线程

#### [AiPreferences.java](app/src/main/java/com/gamecenter/app/ai/AiPreferences.java)

AI 偏好设置管理器，使用 `EncryptedSharedPreferences` 加密存储 API Key 等敏感数据：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| selected_provider | DeepSeek | 云端供应商 |
| selected_model | deepseek-chat | 模型名称 |
| use_local_first | true | 本地优先开关 |
| api_key | (空) | 加密存储的 API Key |
| local_model | on-device | 本地模型标识 |
| free_daily_limit | 20 | 每日免费额度 |
| history_max | 50 | 历史记录上限 |

**安全特性**: 自动从明文 SharedPreferences 迁移到加密存储，迁移完成后清除旧数据；加密初始化失败时降级为明文存储。

---

### 4.5 工具箱模块

#### [ToolBinder.java](app/src/main/java/com/gamecenter/app/tools/ToolBinder.java)

工具绑定器接口，定义工具模块的 UI 与业务逻辑绑定契约：

```java
void bind(Context context, View contentView, ExecutorService executor);
```

每个工具卡片提供一个 `ToolBinder` 实现，在 `bind()` 方法中完成 UI 初始化、事件监听和业务逻辑绑定。

#### [ToolHelper.java](app/src/main/java/com/gamecenter/app/tools/ToolHelper.java)

工具辅助类（final 静态工具类），提供通用方法：

| 方法 | 功能 |
|------|------|
| `getWifiIpAddress()` | 获取 WiFi IPv4 地址 |
| `getMobileIpAddress()` | 获取移动数据 IPv4 地址 |
| `checkVpnStatus()` | 检测 VPN 连接状态 |
| `getDnsServers()` | 获取 DNS 服务器列表 |
| `getWifiSignalStrength()` | WiFi 信号强度 |
| `testPing()` / `pingHost()` | Ping 延迟测试 |
| `testDownloadSpeed()` / `testUploadSpeed()` | 网速测试 |
| `traceRouteHop()` | 路由追踪 |
| `calculateSubnet()` | 子网计算 |
| `classifyIpCarrier()` | IP 运营商分类 |

#### 工具列表

| 工具 | Binder 类 | 功能 |
|------|-----------|------|
| Ping | PingToolBinder | ICMP Ping 延迟测试 |
| 端口扫描 | PortScanToolBinder | TCP 端口扫描 |
| 子网计算 | SubnetToolBinder | CIDR 子网计算 |
| DNS 查询 | DnsToolBinder / DnsLookupToolBinder | DNS 记录查询 |
| 路由追踪 | TracerouteToolBinder | Traceroute 路由追踪 |
| 网速测试 | SpeedTestToolBinder | 下载/上传速度测试 |
| 网络诊断 | NetworkDiagnosisToolBinder | 综合网络诊断 |
| WiFi 信息 | WifiToolBinder | WiFi 连接详情 |
| IP 信息 | IpToolBinder | 公网/内网 IP 查询 |
| LAN 扫描 | LanScanToolBinder | 局域网设备扫描 |
| QR 码 | QrToolBinder / QrPlusToolBinder | 二维码生成/扫描 |
| 颜色取色 | ColorToolBinder / ColorPlusToolBinder | 屏幕取色器 |
| 哈希计算 | HashToolBinder / FileHashToolBinder | 文本/文件哈希 |
| 文本编解码 | TextCodecToolBinder | Base64/URL 编解码 |
| 设备信息 | DeviceToolBinder / SystemInfoToolBinder | 设备/系统信息 |
| 传感器 | SensorToolBinder | 传感器数据读取 |
| 电池信息 | BatteryToolBinder | 电池状态查询 |
| 剪贴板 | ClipboardToolBinder | 剪贴板管理 |
| 屏幕信息 | ScreenToolBinder | 屏幕参数查询 |
| 权限隐私 | PermissionPrivacyToolBinder | 权限与隐私检查 |
| 诊断报告 | DiagnosticReportToolBinder | 综合诊断报告 |

---

### 4.6 更新模块

#### [UpdateViewModel.kt](app/src/main/kotlin/com/gamecenter/app/update/UpdateViewModel.kt)

更新流程 ViewModel（`@HiltViewModel`），替代原有的 `UpdatePresenter`，使用 LiveData 暴露状态。采用 Kotlin 协程（`viewModelScope.launch` + `suspendCancellableCoroutine`）将 Java 回调包装为 suspend 函数，实现结构化并发。

**协程改造要点**:
- `viewModelScope.launch` + `suspendCancellableCoroutine` 包装 `UpdateManager.checkUpdate`/`downloadApk` 的 Java 回调
- `checkJob: Job?` / `downloadJob: Job?` 替代 `isCheckingUpdate` / `isAutoDownloading` 布尔标志
- `onCleared()` 自动取消两个 Job
- 使用 `resumeWith(kotlin.Result.success(...))` 替代已废弃的 `resume(value){}`

**状态模型**:

| 状态类 | 说明 |
|--------|------|
| `UpdateCheckState.Idle` | 空闲 |
| `UpdateCheckState.Checking` | 检查中 |
| `UpdateCheckState.Available(info)` | 有可用更新 |
| `UpdateCheckState.NotAvailable` | 无更新 |
| `UpdateCheckState.BetaOnly(info)` | 仅 Beta 版可用 |
| `UpdateCheckState.BetaBlocked(info)` | Beta 被用户设置阻止 |
| `UpdateCheckState.Error(message)` | 检查出错 |
| `DownloadState.Idle` | 下载空闲 |
| `DownloadState.Downloading(downloaded, total)` | 下载中 |
| `DownloadState.Verifying` | 校验中 |
| `DownloadState.Completed(apkFile)` | 下载完成 |
| `DownloadState.Error(message)` | 下载出错 |
| `DownloadState.Cancelled` | 下载取消 |
| `CheckResult`（密封类） | 检查结果：Success/NoUpdate/BetaOnly/BetaBlocked/Error |
| `DownloadResult`（密封类） | 下载结果：Success/Verifying/Error/Cancelled |

**关键方法**:
- `checkUpdate(context, showToast)` — 检查更新，结果通过 `updateCheckState` LiveData 发射
- `startDownload(context, info)` — 开始下载，进度通过 `downloadState` LiveData 发射
- `enableBetaAndRecheck(context)` — 开启 Beta 并重新检查
- `installApk(context)` — 安装 APK
- `onInstallPermissionResult(context, resultCode)` — 处理安装权限回调

**优势**（相比旧 UpdatePresenter）:
- ViewModel 生命周期感知，自动在 Activity 销毁时清理，无需手动 `onDestroy()`
- LiveData 自动取消订阅，消除 `isFinishing()/isDestroyed()` 防御代码
- 配置变更（如旋转屏幕）时自动恢复状态
- 协程结构化并发：`viewModelScope` 自动取消，`Job?` 替代布尔标志实现精确取消控制

#### [UpdatePresenter.java](app/src/main/java/com/gamecenter/app/update/UpdatePresenter.java)（已废弃）

> ⚠️ 此类已被 `UpdateViewModel` 替代，保留仅供参考。新代码应使用 `UpdateViewModel`。

#### [UpdateManager.java](app/src/main/java/com/gamecenter/app/update/UpdateManager.java)

更新管理器（门面模式 + `@Singleton`），协调四个核心组件：

```
UpdateManager (门面)
    ├── UpdateChecker      — 检查更新（网络请求 + 版本比对）
    ├── UpdateDownloader   — 下载 APK（断点续传 + MD5 校验）
    ├── UpdateInstaller    — 安装 APK（权限检查 + FileProvider）
    └── UpdateNotificationHelper — 通知管理
```

**DI 变更**: 构造函数已标注 `@Inject`，Hilt 可直接创建实例。`getInstance()` 已标记 `@Deprecated`。

### 安全加固

**AndroidManifest.xml 变更**:

| 配置项 | 变更 | 说明 |
|--------|------|------|
| `android:allowBackup` | `true` → `false` | 禁止 ADB 备份，保护本地数据 |
| `android:fullBackupContent` | 新增 `@xml/backup_rules` | Auto Backup 排除规则 |
| `android:dataExtractionRules` | 新增 `@xml/data_extraction_rules` | D2D 迁移和云备份排除规则 |

**备份排除规则**（`backup_rules.xml` / `data_extraction_rules.xml`）:

| 排除目录 | 原因 |
|----------|------|
| sharedpref | 含 AI Key、Peer Token 等加密偏好 |
| database | Room 数据库含聊天记录和游戏统计 |
| update/ | 下载的 APK 和临时文件 |

**存储权限迁移**:

| 权限 | 变更 | 说明 |
|------|------|------|
| `READ_MEDIA_IMAGES` | 新增 | Android 13+ 细粒度媒体权限 |
| `READ_EXTERNAL_STORAGE` | 添加 `maxSdkVersion="32"` | Android 13 后不再需要 |
| `WRITE_EXTERNAL_STORAGE` | 添加 `maxSdkVersion="29"` | Android 10 后使用私有目录 |

---

### 4.7 设置与主题管理

#### [SettingsManager.java](app/src/main/java/com/gamecenter/app/SettingsManager.java)

应用设置管理器（`@Singleton`），基于 SharedPreferences：

**DI 变更**: 构造函数已标注 `@Inject` + `@ApplicationContext`，Hilt 可直接创建实例。`getInstance()` 已标记 `@Deprecated`，保留作为非 DI 场景的兼容桥接。

| 设置项 | 键名 | 类型 | 默认值 |
|--------|------|------|--------|
| 主题模式 | theme_mode | int | 0 (跟随系统) |
| 配色方案 | color_scheme | int | 0 (清朗紫) |
| 自动检查更新 | auto_check_update | boolean | true |
| 接受测试版 | accept_beta_update | boolean | false |
| 自动下载更新 | auto_download_update | boolean | false |
| 下载后提示安装 | prompt_install_after_auto_download | boolean | false |
| 更新来源 | update_source | int | 0 (自动) |
| 音效 | sound_enabled | boolean | true |
| 振动 | vibration_enabled | boolean | true |
| 应用语言 | app_language | String | "" (跟随系统) |

**更新来源选项**: 自动(0) / 香港VPS(1) / 美国VPS(2) / GitHub(3)

#### [ColorSchemeManager.java](app/src/main/java/com/gamecenter/app/ColorSchemeManager.java)

配色方案管理器（静态工具类），定义 8 套配色方案：

| 索引 | 名称 | 主色 |
|------|------|------|
| 0 | 清朗紫 | `#5B4E9A` |
| 1 | 深海蓝 | `#2563EB` |
| 2 | 竹影绿 | `#047857` |
| 3 | 晨曦橙 | `#C2410C` |
| 4 | 蔷薇莓 | `#BE123C` |
| 5 | 极光青 | `#0891B2` |
| 6 | 墨金 | `#A16207` |
| 7 | 朱砂红 | `#B91C1C` |

每套方案包含完整的 Material Design 3 色彩体系（浅色 + 深色双模式），通过 `applyScheme()` 应用到 Activity 级别，或通过 `applySchemeToView()` 应用到单个 View。

---

### 4.8 数据层

#### [AppDatabase.kt](app/src/main/kotlin/com/gamecenter/app/database/AppDatabase.kt)

Room 数据库（版本 1），包含两张实体表：

| 实体 | DAO | 用途 |
|------|-----|------|
| `AiMessageEntity` | `AiMessageDao` | AI 聊天消息持久化 |
| `GameStatsEntity` | `GameStatsDao` | 游戏统计数据 |

单例模式（双重检查锁定），数据库名 `gamecenter_database`。

#### [AppModule.kt](app/src/main/kotlin/com/gamecenter/app/di/AppModule.kt)

Hilt 依赖注入模块（`@InstallIn(SingletonComponent::class)`），提供以下绑定：

**DI 变更**: `SettingsManager`、`OkHttpClientProvider`、`UpdateManager` 已改用 `@Inject` 构造函数，不再需要 `@Provides` 方法。Hilt 自动通过 `@Singleton` + `@Inject` 管理这些实例。

| 提供对象 | 注入方式 | 变更说明 |
|----------|---------|---------|
| `ExecutorService` | `Executors.newCachedThreadPool()` | 无变化 |
| `OkHttpClientProvider` | ~~`getInstance()`~~ → `@Inject` 构造函数 | 已移除 `@Provides` |
| `OkHttpClient` | 从 Provider 获取 | 无变化 |
| `SettingsManager` | ~~`getInstance()`~~ → `@Inject` 构造函数 | 已移除 `@Provides` |
| `UpdateManager` | ~~`getInstance()`~~ → `@Inject` 构造函数 | 已移除 `@Provides` |
| `AiPreferences` | 直接构造 | 无变化 |
| `AppDatabase` | `getDatabase(context)` | 无变化 |
| `AiMessageDao` | 从 Database 获取 | 无变化 |
| `GameStatsDao` | 从 Database 获取 | 无变化 |
| `SaveManager` | ~~`getInstance()`~~ → `@Inject` 构造函数 | 已迁移到 Kotlin，移除 `@Provides` |
| `ErrorReporter` | `getInstance(context)` | 待迁移 |

---

### 4.9 Fragment 层

应用使用 Navigation Component 管理四个主 Fragment：

| Fragment | 导航 ID | 功能 |
|----------|---------|------|
| `GamesFragment` | navigation_games | 游戏大厅（TabLayout + RecyclerView 卡片网格） |
| `BrowserFragment` | navigation_browser | 内置浏览器 |
| `ToolsFragment` | navigation_tools | 网络工具箱 |
| `AiFragment` | navigation_ai | AI 助手 |

#### GamesFragment

游戏大厅，核心功能：
- **TabLayout**: 全部 / 最近 / 收藏 / 各分类标签
- **搜索**: 实时过滤游戏名称、描述和分类
- **收藏**: 通过 `GameUsageStore` 管理收藏状态
- **反馈**: 支持 VPS API 在线提交 + 邮箱兜底两种方式
- **配色**: 卡片根据当前主题和配色方案动态着色

---

### 4.10 工具类

| 类 | 功能 |
|----|------|
| `SoundManager` | 音效播放与背景音乐管理 |
| `ErrorReporter` | 错误上报到 VPS |
| `I18nHelper` | 国际化辅助 |
| `NetworkErrorHandler` | 网络状态检测与错误处理（错误码 + Toast + 重试策略） |
| `SystemInfoCollector` | 系统信息收集 |
| `CrashHandler` (Kotlin) | 全局未捕获异常处理 |
| `Result.kt` (Kotlin) | 通用结果封装类（Success/Error/Loading） |
| `AppError.kt` (Kotlin) | **新增** 统一错误模型，密封类层次结构，支持从异常/HTTP状态码自动映射 |
| `NetworkResult.kt` (Kotlin) | **新增** 网络请求结果封装，基于 `AppError`，提供类型安全的错误处理 |
| `MemoryUtils.kt` (Kotlin) | 内存工具 |
| `Extensions.kt` (Kotlin) | Kotlin 扩展函数 |
| `LazyInitManager.kt` (Kotlin) | 延迟初始化管理 |

#### AppError 错误模型

```
AppError (sealed class)
├── NetworkDisconnected   网络断开
├── Timeout               请求超时
├── DnsResolution         DNS解析失败
├── ServerError(httpCode) 服务端5xx
├── ClientError(httpCode) 客户端4xx
├── SslError              SSL/TLS握手失败
├── IoError               网络IO异常
├── Cancelled             请求已取消
├── BusinessError         业务逻辑错误
└── Unknown               未知错误
```

关键 API：
- `AppError.fromException(Throwable)` — 从异常自动映射为对应错误类型
- `AppError.fromHttpCode(Int)` — 从 HTTP 状态码映射为对应错误类型
- `isNetworkError()` / `isServerError()` / `isRecoverable()` — 错误分类判断

---

## 5. 服务端组件

### 5.1 WebSocket 中转服务器

**位置**: `vps/ddz_ws_relay/server.js`  
**技术栈**: Node.js + ws 库  
**监听**: `127.0.0.1:18080`

**房间管理**:
- `rooms` Map：roomCode → { host, clients, createdAt }
- 房主 (host) 消息广播给所有客户端
- 客户端消息转发给房主
- 每 10 分钟清理空房间（超过 1 小时）

**HTTP 端点**:
- `GET /health` — 健康检查
- `GET /stats` — 统计信息（房间数、运行时间、内存）

**WebSocket 协议**:
- 连接参数: `room` (房间码) + `role` (host/client) + `clientId` + `token`
- 消息类型: `WELCOME` / `ROOM_STATE` / `PING`/`PONG` / `HOST_DISCONNECTED` / 自定义游戏消息

### 5.2 更新服务器

**位置**: `vps/var_www_update/update_server.py`  
**技术栈**: Python 3 + `http.server`  
**监听**: `127.0.0.1:9000`

**API 端点**:

| 端点 | 方法 | 说明 |
|------|------|------|
| `/health` | GET | 健康检查 |
| `/api/update/check?versionCode=&acceptBeta=` | GET | 更新检查 |
| `/version.json` | GET | 兼容旧版（根据 acceptBeta 参数路由） |
| `/version-beta.json` | GET | Beta 版元数据 |
| `/version-release.json` | GET | 正式版元数据 |
| `/*.apk` | GET | APK 文件下载 |

**双版本分发逻辑**: 根据 `acceptBeta` 参数决定返回 Beta 或 Stable 版本信息，若请求版本不存在则回退。

### 5.3 反馈服务器

**位置**: `vps/var_www_update/feedback/feedback_server.py`  
接收客户端 POST 的 JSON 反馈数据。

### 5.4 斗地主中转服务器

**位置**: `vps/var_www_update/ddz_relay/ddz_relay_server.py`  
斗地主专用的 HTTP 中转服务器。

---

## 6. 依赖关系图

### 模块间依赖

```
MainActivity
    ├── Navigation Component → {GamesFragment, BrowserFragment, ToolsFragment, AiFragment}
    ├── UpdateViewModel (LiveData) → UpdateManager → {UpdateChecker, UpdateDownloader, UpdateInstaller}
    ├── PermissionHelper
    └── SettingsManager

GamesFragment
    ├── GameRegistry → {各游戏 Activity}
    ├── GameUsageStore
    ├── AppSettingsDialog → SettingsManager
    └── OkHttpClientProvider → OkHttpClient

BaseOnlineActivity (abstract)
    ├── GameSocketServer ← WebSocketHostHelper
    ├── GameSocketClient ← WebSocketClientHelper
    ├── OnlineChatHelper
    ├── RelayHttpClient
    └── LANManager

AiTaskRouter
    ├── AiPreferences (加密存储)
    ├── MediaPipeLocalLlmEngine (本地 Gemma)
    ├── LocalAiProcessor (规则引擎)
    ├── LocalLlmOutputGuard (输出质量校验)
    ├── AiApiClient (云端 API)
    ├── AiModelDownloadManager
    └── NetworkErrorHandler

ToolsFragment
    └── {各 ToolBinder} → ToolHelper → NetworkDiagHelper

App (Application)
    ├── SettingsManager
    ├── ColorSchemeManager
    └── OkHttpClientProvider (App Startup 延迟初始化)

AppModule (Hilt DI)
    ├── SettingsManager (@Inject 构造函数)
    ├── OkHttpClientProvider (@Inject 构造函数) → OkHttpClient
    ├── UpdateManager (@Inject 构造函数)
    ├── AiPreferences
    ├── AppDatabase → {AiMessageDao, GameStatsDao}
    ├── SaveManager
    └── ErrorReporter
```

### 外部依赖

| 库 | 版本 | 用途 |
|----|------|------|
| AndroidX AppCompat | 1.7.1 | 兼容性支持 |
| Material Components | 1.12.0 | Material Design 组件（含 MaterialCardView，已替代 androidx.cardview） |
| Navigation | 2.8.9 | Fragment 导航 |
| OkHttp | 4.12.0 | HTTP/WebSocket 客户端 |
| Gson | 2.11.0 | JSON 序列化 |
| Glide | 4.16.0 | 图片加载 |
| ZXing | 3.5.3 | 二维码核心库 |
| Hilt | 2.57.2 | 依赖注入 |
| Room | 2.6.1 | 本地数据库 |
| MediaPipe GenAI | 0.10.27 | 本地 LLM 推理 |
| Kotlin Coroutines | 1.10.1 | 异步编程（UpdateViewModel 协程化） |
| LeakCanary | 2.14 | 内存泄漏检测 (debug) |

> **已移除依赖**：`androidx.cardview:cardview:1.0.0` — 已由 `MaterialCardView`（来自 Material Components）替代。

---

## 7. 构建与发布

### 环境要求

- JDK 17+ (编译目标)
- Android SDK 35
- Gradle 8.x (由 wrapper 管理)
- Python 3 (发布脚本)

### 本地构建

```bash
# Debug 构建
.\gradlew.bat :app:assembleDebug

# Release 构建（需配置 keystore.properties）
.\gradlew.bat :app:assembleRelease
```

### 版本管理

版本信息存储在 `version.properties`：

```properties
versionCode=262          # 内部版本号，每次构建自动递增
versionName=1.3.26       # 展示版本号
lastStableVersionCode=241
lastStableVersionName=1.3.25
betaNoticeVersionGap=3
```

### 发布流程

```bash
# Beta 发布（仅上传 VPS）
.\gradlew.bat :app:buildAndUploadDebugToVps

# 正式发布（VPS + GitHub Releases）
.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable
```

### 服务器配置

在 `local.properties` 中配置（不纳入版本控制）：

```properties
server.url=https://your-server.example.com
server.url.fallback=
relay.url=https://your-server.example.com/api/ddz-relay
ws.url=
feedback.url=https://your-server.example.com/api/feedback
```

### 签名配置

在 `keystore.properties` 中配置（不纳入版本控制）：

```properties
STORE_FILE=path/to/keystore.jks
STORE_PASSWORD=***
KEY_ALIAS=***
KEY_PASSWORD=***
```

---

## 8. 测试体系

### 单元测试 (`src/test/`)

| 测试类 | 测试目标 |
|--------|---------|
| `AiApiClientTest` | AI API 客户端（MockWebServer：成功/HTTP错误/连接失败/畸形JSON/缺字段/空system prompt，8 个方法） |
| `AiTaskRouterTest` | AI 任务路由逻辑 |
| `GoGameTest` | 围棋游戏逻辑 |
| `GomokuGameTest` | 五子棋游戏逻辑 |
| `GameSocketClientTest` | WebSocket 客户端 |
| `RelayHttpClientTest` | 中转 HTTP 客户端 |
| `RemoteP2PUtilTest` | 远程 P2P 工具 |
| `ToolHelperTest` | 工具辅助方法 |
| `UpdateInfoTest` | 更新信息模型（JSON 解析：全部字段/Beta渠道/版本回退，17 个方法） |
| `UpdateManagerLogicTest` | 更新管理器逻辑 |
| `UpdatePresenterTest` | 更新展示器 |
| `ResultTest` | Result 封装类 |

### 集成测试 (`src/androidTest/`)

| 测试类 | 测试目标 |
|--------|---------|
| `AiIntegrationTest` | AI 端到端集成 |
| `DouDiZhuIntegrationTest` | 斗地主联机集成 |
| `RoomDatabaseIntegrationTest` | Room 数据库集成 |
| `UpdateIntegrationTest` | 更新流程集成 |

### 测试依赖

| 库 | 用途 |
|----|------|
| JUnit 4.13.2 | 单元测试框架 |
| Mockito 5.15.2 + mockito-kotlin 5.4.0 | Mock 框架 |
| Kotlin Test 2.2.21 | Kotlin 测试断言 |
| kotlinx-coroutines-test 1.10.1 | 协程测试 |
| OkHttp MockWebServer 4.12.0 | HTTP Mock 服务器 |
| Espresso 3.6.1 | UI 测试 |

---

## 9. CI/CD

**配置文件**: `.github/workflows/ci.yml`

**触发条件**: push/PR 到 main/master 分支

**流水线步骤**:

1. Checkout 代码
2. 设置 JDK 21 (Temurin)
3. 授予 gradlew 执行权限
4. 构建 Debug APK (`assembleDebug`)
5. APK 大小报告（计算 APK 大小并写入 `GITHUB_STEP_SUMMARY`）
6. 上传 Debug APK (保留 30 天)
7. 运行单元测试
8. 测试结果报告（解析 XML 测试报告，输出通过/失败/跳过统计到 `GITHUB_STEP_SUMMARY`）
9. 上传测试报告 (保留 7 天)
10. Android Lint 执行 (`lintDebug`)
11. Lint 问题报告（解析 Lint 输出并上传结果 artifact）

---

## 附录: 关键设计模式

| 模式 | 应用场景 |
|------|---------|
| **单例** | SettingsManager, UpdateManager, OkHttpClientProvider, LANManager, AppDatabase |
| **门面** | UpdateManager 协调 Checker/Downloader/Installer/NotificationHelper |
| **模板方法** | BaseGameActivity (loadGameSounds), BaseOnlineActivity (initGameViews/onGameStarted 等) |
| **观察者/回调** | UpdateCheckCallback, DownloadCallback, AiCallback, LANManager 监听器 |
| **策略** | AiTaskRouter 的本地/云端路由策略, 配色方案切换 |
| **工厂** | GameRegistry 构建 Category/Entry, AiProviderConfig 工厂方法 |
| **双重检查锁定** | SettingsManager, OkHttpClientProvider, AppDatabase 单例初始化 |
| **一次性门控** | App.shouldAutoCheckUpdate() 确保更新检查仅执行一次 |
| **弱引用防泄漏** | MainActivity.SafeUpdateCheckRunnable |
| **组合优于继承** | OnlineRoomManager 替代 BaseOnlineActivity 继承，各游戏通过组合复用联机逻辑 |
| **双轨注册** | GameRegistry 静态硬编码 + @GameEntry 注解自动发现 + register() 动态注册 |
| **ViewModel + LiveData** | UpdateViewModel 替代 UpdatePresenter，生命周期安全的更新状态管理 |
| **密封类状态机** | UpdateCheckState / DownloadState / AppError，类型安全的状态与错误建模 |
| **@Inject 构造函数** | SettingsManager, OkHttpClientProvider, UpdateManager, SaveManager — Hilt 原生 DI |
| **枚举替代字符串** | TaskStatus 替代 AiTask.status 字符串，AiErrorCode 替代 AiResult.errorCode 裸字符串 |
| **重命名避冲突** | AppResult 替代 Result，避免与 kotlin.Result 标准库冲突 |

---

## 10. Java/Kotlin 混合边界规范

本项目主体为 Java 17，逐步引入 Kotlin 用于数据层、工具层和 ViewModel。以下是跨语言边界的开发约定：

### 10.1 文件放置

| 类型 | 语言 | 目录 |
|------|------|------|
| Activity / Fragment / Service | Java | `app/src/main/java/.../` |
| ViewModel | Kotlin | `app/src/main/kotlin/.../` |
| 数据模型（Entity / DTO） | Java | `app/src/main/java/.../data/` |
| 枚举 / 常量 | Java | `app/src/main/java/.../data/` |
| 工具类（密封类 / Result 封装） | Kotlin | `app/src/main/kotlin/.../util/` |
| DI 模块 | Kotlin | `app/src/main/kotlin/.../di/` |

### 10.2 Java 调用 Kotlin 的注意事项

- **伴生对象方法**：Kotlin `companion object` 中的方法需加 `@JvmStatic` 才能以静态方式从 Java 调用（如 `NetworkResult.loading()`）。
- **密封类子类**：Java 中通过 `AppError.NetworkError` 形式访问密封类子类，无需特殊处理。
- **默认参数**：Kotlin 的默认参数在 Java 侧不可见，需加 `@JvmOverloads` 或显式提供所有参数。
- **属性访问**：Kotlin 属性在 Java 中生成 getter/setter。布尔属性的 getter 为 `isXxx()` 而非 `getXxx()`，但仅当属性名以 `is` 开头时遵循此规则。Java 类中名为 `hasUpdate()` 的方法在 Kotlin 中必须用 `info.hasUpdate()` 显式调用，不能用属性语法 `info.hasUpdate`。
- **Unit 返回值**：Kotlin 的 `Unit` 在 Java 中需显式返回 `Unit.INSTANCE` 或使用 `void` 返回类型。

### 10.3 Kotlin 调用 Java 的注意事项

- **getter 方法**：Java 的 `getXxx()` 方法在 Kotlin 中可用属性语法 `xxx` 访问，但 `hasXxx()` / `isXxx()` 除外——仅当 Java 方法名符合 JavaBeans 规范时才能用属性语法。例如 `hasUpdate()` 不符合规范（应为 `isHasUpdate()`），必须用 `info.hasUpdate()` 显式调用。
- **可空性**：Java 类型在 Kotlin 中为平台类型（`Type!`），需显式处理可空性。推荐在 Java 代码中添加 `@Nullable` / `@NonNull` 注解。
- **受检异常**：Kotlin 不强制处理 Java 受检异常，但建议在调用 Java 方法时仍用 try-catch 包裹可能抛出异常的代码。
- **数组**：Java 数组在 Kotlin 中映射为 `Array<T>`，基本类型数组映射为 `IntArray`、`ByteArray` 等。

### 10.4 迁移优先级

新增代码优先使用 Kotlin 的场景：
- ViewModel / Repository
- 数据转换 / 工具函数
- 密封类 / when 表达式
- 协程相关代码

保持 Java 的场景：
- 与 Android Framework 强绑定的代码（Activity / Service / BroadcastReceiver）
- 已有大量 Java 调用者的公共 API
- 性能敏感的热路径（游戏循环、Canvas 绘制）

### 10.5 同名类冲突

同一包下不允许同时存在同名的 `.java` 和 `.kt` 文件（如 `SaveManager.java` 和 `SaveManager.kt`）。迁移时必须先删除旧文件再创建新文件。

---

## 2026-05-19 文档同步：战略优化

- UpdateViewModel 协程化：`viewModelScope.launch` + `suspendCancellableCoroutine` 包装 Java 回调为 suspend 函数，`CheckResult`/`DownloadResult` 密封类替代布尔标志，`checkJob`/`downloadJob` 替代 `isCheckingUpdate`/`isAutoDownloading`。
- 网络层测试：新增 `AiApiClientTest`（MockWebServer，8 个方法）和 `UpdateInfoTest`（JSON 解析，17 个方法）。
- CI 质量门：APK 大小报告、测试结果报告、Android Lint 执行和 Lint 问题报告。
- 安全加固：`allowBackup=false`，新增 `backup_rules.xml` 和 `data_extraction_rules.xml`，存储权限迁移（`READ_MEDIA_IMAGES`、`maxSdkVersion` 限制）。
- 构建优化：`MaterialCardView` 替代 `androidx.cardview.widget.CardView`，移除 `cardview:1.0.0` 依赖。
- 版本号更新：versionCode=262, versionName=1.3.26。

## 2026-05-19 Modularization Snapshot

Current Gradle modules:

```text
:app
:core:common
:core:network
:core:update
```

Ownership:

- `:app`: application shell, feature UI, games, tools, AI, manifest, app resources, release tasks.
- `:core:common`: `SettingsManager`, shared Kotlin result/error types, common Android/Kotlin helpers.
- `:core:network`: OkHttp provider, request deduplication, relay HTTP/WebSocket helpers, room-code utilities, network error handling.
- `:core:update`: update manager/checker/downloader/installer/notification/presenter/model/ViewModel.

Dependency direction:

```text
app -> core:update -> core:network -> core:common
app -> core:network
app -> core:common
```

Keep new cross-feature dependencies out of `:core:*` unless they are genuinely reusable infrastructure.
