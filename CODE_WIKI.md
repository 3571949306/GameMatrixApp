# GameCenterApp Code Wiki

> Android 游戏中心应用完整技术文档

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术栈与依赖](#2-技术栈与依赖)
3. [项目架构](#3-项目架构)
4. [模块职责](#4-模块职责)
5. [关键类与函数说明](#5-关键类与函数说明)
6. [游戏模块架构](#6-游戏模块架构)
7. [网络与联机架构](#7-网络与联机架构)
8. [工具模块架构](#8-工具模块架构)
9. [AI 模块架构](#9-ai-模块架构)
10. [更新系统](#10-更新系统)
11. [构建与发布流程](#11-构建与发布流程)
12. [项目运行方式](#12-项目运行方式)

---

## 1. 项目概述

GameCenterApp 是一款 Android 平台的游戏中心应用，包名 `com.gamecenter.app`，包含 **26 个独立小游戏**、**30+ 个网络/系统工具**、**AI 辅助功能**，支持**局域网联机**和**在线多人对战**。

| 属性 | 值 |
|------|-----|
| 包名 | com.gamecenter.app |
| 最低 SDK | 24 (Android 7.0) |
| 目标 SDK | 35 (Android 15) |
| 编译 SDK | 35 |
| 当前版本 | v1.3.20 (versionCode: 236) |
| 语言 | Java + Kotlin |
| 架构 | MVC + DI (Hilt) |

---

## 2. 技术栈与依赖

### 核心框架

| 依赖 | 版本 | 用途 |
|------|------|------|
| androidx.appcompat:appcompat | 1.7.1 | 兼容性支持库 |
| com.google.android.material:material | 1.12.0 | Material Design 3 组件 |
| androidx.constraintlayout:constraintlayout | 2.2.1 | 约束布局 |
| androidx.recyclerview:recyclerview | 1.4.0 | 列表控件 |
| androidx.cardview:cardview | 1.0.0 | 卡片视图 |
| androidx.startup:startup-runtime | 1.2.0 | 应用启动初始化 |

### Kotlin 生态

| 依赖 | 版本 | 用途 |
|------|------|------|
| org.jetbrains.kotlin:kotlin-stdlib | 2.1.10 | Kotlin 标准库 |
| androidx.core:core-ktx | 1.15.0 | Kotlin 扩展函数 |
| kotlinx-coroutines-android | 1.10.1 | 协程支持 |

### 依赖注入

| 依赖 | 版本 | 用途 |
|------|------|------|
| com.google.dagger:hilt-android | 2.55 | 依赖注入框架 |

### 网络与工具

| 依赖 | 版本 | 用途 |
|------|------|------|
| com.squareup.okhttp3:okhttp | 4.12.0 | HTTP 客户端 |
| com.google.code.gson:gson | 2.11.0 | JSON 序列化 |
| com.google.zxing:core | 3.5.3 | 二维码生成与识别 |
| com.github.bumptech.glide:glide | 4.16.0 | 图片加载缓存 |

### 构建工具

| 插件 | 版本 |
|------|------|
| com.android.application | 8.7.3 |
| org.jetbrains.kotlin.android | 2.1.10 |
| org.jetbrains.kotlin.kapt | 2.1.10 |
| com.google.dagger.hilt.android | 2.55 |

### Debug 工具

| 依赖 | 版本 | 用途 |
|------|------|------|
| com.squareup.leakcanary:leakcanary-android | 2.14 | 内存泄漏检测 |

### 测试框架

| 依赖 | 版本 | 用途 |
|------|------|------|
| junit:junit | 4.13.2 | 单元测试 |
| org.mockito:mockito-core | 5.15.2 | Mock 框架 |
| mockito-kotlin | 5.4.0 | Kotlin Mock 扩展 |
| okhttp3:mockwebserver | 4.12.0 | 网络模拟服务器 |

---

## 3. 项目架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    GameCenterApp (Android)                  │
├─────────────────────────────────────────────────────────────┤
│  App.java ── 入口 + 生命周期管理 + Crash 防护                │
│  MainActivity.java ── 主容器 + Fragment 切换                 │
├─────────────────────────────────────────────────────────────┤
│  Fragments (UI 层)                                          │
│  ├── GamesFragment    ── 游戏大厅 (TabLayout + RecyclerView)│
│  ├── ToolsFragment    ── 工具中心                           │
│  ├── AiFragment       ── AI 对话界面 (独立底部导航Tab)       │
│  └── BrowserFragment  ── 内置浏览器                         │
├─────────────────────────────────────────────────────────────┤
│  核心业务层                                                  │
│  ├── games/        ── 26 个游戏 (各自 Activity + Game/View) │
│  ├── tools/        ── 30+ 工具 Binder (功能入口)             │
│  ├── ai/           ── AI 模块 (本地 + 云端)                  │
│  ├── network/      ── 联机网络 (WebSocket + HTTP Relay)      │
│  ├── update/       ── 版本更新系统                           │
│  └── settings/     ── 设置管理                               │
├─────────────────────────────────────────────────────────────┤
│  基础设施层                                                  │
│  ├── utils/        ── 工具类 (国际化、错误处理、音效、系统信息)│
│  ├── views/        ── 自定义 View (颜色选择器)               │
│  ├── di/AppModule   ── Hilt 依赖注入模块                     │
│  └── util/ (Kotlin)─ 崩溃处理、内存工具、懒加载管理等         │
├─────────────────────────────────────────────────────────────┤
│  外部服务                                                     │
│  ├── 香港 VPS  ── 更新服务器、斗地主 WebSocket 中继            │
│  ├── 美国 VPS  ── 备用更新服务器                              │
│  ├── GitHub ── Release 分发 + 源代码                          │
│  └── AI API ── OpenAI 兼容云端接口                            │
└─────────────────────────────────────────────────────────────┘
```

### 数据流架构

```
用户操作 → MainActivity → Fragment → GameActivity/ToolBinder
                                     ↓
                              GameLogic/Network
                                     ↓
                           SharedPreferences / File / Assets
                                     ↓
                              UI 更新 (LiveData / Handler)
```

---

## 4. 模块职责

### 4.1 应用入口模块

| 类 | 职责 |
|-----|------|
| `App.java` | Application 入口，初始化全局 CrashHandler、启动日志系统、管理 Activity 生命周期、主题/配色应用 |
| `MainActivity.java` | 主容器 Activity，管理底部导航切换(Games/Tools/AI)、自动检查更新、权限请求 |

### 4.2 UI 层 (Fragments)

| 类 | 职责 |
|-----|------|
| `GamesFragment` | 游戏大厅：分类 Tab、游戏卡片网格展示、搜索过滤、最近游戏/收藏、启动游戏 |
| `ToolsFragment` | 工具中心：分区块展示网络诊断、系统信息、二维码等工具，点击启动工具 |
| `BrowserFragment` | 内置 WebView 浏览器，支持常用网站快捷入口 |
| `AiFragment` | AI 交互界面：聊天式对话，支持多种 AI 任务(翻译、OCR、摘要等) |

### 4.3 游戏模块

| 类/目录 | 职责 |
|---------|------|
| `GameRegistry` | 游戏注册表：注册所有游戏元数据(id、名称、图标、分类、Activity) |
| `BaseGameActivity` | 游戏基类：封装音效播放、统计记录、返回键处理、教程辅助 |
| `GameUsageStore` | 游戏使用统计：收藏、最近、游玩次数、胜负记录，使用 SharedPreferences 持久化 |
| `GameStats` | 游戏统计数据模型(高分、胜率、总游玩时间等) |
| `GameTutorialHelper` | 教程提示辅助类 |
| `InteractiveTutorialDialog` | 交互式教程对话框 |
| `StatsActivity` | 全局游戏统计查看页面 |

#### 各游戏子模块结构

每个游戏遵循统一的 `Activity + Game + View` 三层架构：
- **Activity**: 处理 UI 交互、菜单、生命周期
- **Game**: 纯游戏逻辑(状态机、规则判断、AI 对手)
- **View**: 自定义 SurfaceView/View 负责渲染

部分游戏有联机版本（`OnlineActivity`），继承 `BaseOnlineActivity`。

### 4.4 网络与联机模块

| 类 | 职责 |
|-----|------|
| `RelayHttpClient` | HTTP 中继客户端：通过香港 VPS 中继 API 请求，支持多源回退和请求去重 |
| `GameSocketServer` | WebSocket 服务器端：作为房间主机接收客户端连接、转发消息 |
| `GameSocketClient` | WebSocket 客户端：作为加入者连接主机，支持自动重连 |
| `BaseOnlineActivity` | 联机游戏基类：封装房间管理、聊天、连接状态等通用逻辑 |
| `OnlineChatHelper` | 联机聊天助手：消息格式化和显示 |
| `OkHttpClientProvider` | OkHttpClient 单例提供者：配置超时、拦截器 |
| `RequestDeduplicationInterceptor` | 请求去重拦截器：防止重复请求 |
| `RemoteP2PUtil` | P2P 连接工具类 |

### 4.5 工具模块

| 类 | 职责 |
|-----|------|
| `ToolBinder` | 工具抽象基类：定义 `getName()`、`run()`、`getIcon()` 接口 |
| `ToolSection` | 工具分组模型(网络诊断、系统信息、开发工具等) |
| `ToolSectionStore` | 工具分组仓库：管理所有工具及其分类 |
| `AdvancedToolBinders` | 高级工具注册器：动态注册所有具体工具 |
| `AiToolBinder` | AI 工具：跳转 AI Fragment |
| `*ToolBinder` | 30+ 具体工具实现：Ping、Traceroute、DNS、端口扫描、WiFi、电池、二维码等 |

### 4.6 AI 模块

| 类 | 职责 |
|-----|------|
| `AiTaskRouter` | AI 任务调度中心：决定本地/云端路由、管理任务生命周期、本地优先策略 |
| `AiPreferences` | AI 偏好设置：本地化存储 API Key、提供商、模型选择 |
| `AiApiClient` | 云端 API 客户端：OpenAI 兼容接口，同步聊天请求 |
| `LocalAiProcessor` | 本地 AI 处理：OCR 后处理、摘要、关键词提取、分类、指令识别 |
| `AiHistoryStore` | AI 历史记录与收藏持久化 |
| `AiTemplateManager` | AI 常用模板管理 |
| `AiFragment` | AI 对话界面 UI，支持模板、搜索、收藏和导出 |

### 4.7 更新系统

| 类 | 职责 |
|-----|------|
| `UpdateManager` | 版本更新管理器：检查更新、下载 APK、校验 MD5、触发安装、通知管理 |
| `UpdateInfo` | 更新信息模型：版本号、下载链接、MD5、更新日志等 |
| `SSLHelper` | SSL 证书处理：信任自定义更新服务器证书 |
| `NetworkInitializer` | 网络组件初始化器：App Startup 自动预加载 OkHttpClient |

### 4.8 设置模块

| 类 | 职责 |
|-----|------|
| `SettingsManager` | 单例设置管理器：主题模式、配色方案、更新策略等，SharedPreferences 持久化 |
| `ColorSchemeManager` | 配色方案管理器：定义多套预设配色 |
| `AppSettingsDialog` | 设置弹窗 UI：主设置弹窗 + 版本更新子弹窗 |

### 4.9 工具类

| 类 | 职责 |
|-----|------|
| `NetworkErrorHandler` | 网络错误码与用户友好消息映射 |
| `SoundManager` | 音效管理器：播放/暂停/静音控制 |
| `I18nHelper` | 国际化辅助：中英文切换 |
| `SystemInfoCollector` | 系统信息收集器：设备信息、内存、CPU 等 |

### 4.10 Kotlin 工具模块

| 类 | 职责 |
|-----|------|
| `AppModule.kt` | Hilt 依赖注入模块：提供 ExecutorService、SettingsManager、OkHttpClient、UpdateManager |
| `CrashHandler.kt` | 全局崩溃处理器：捕获未捕获异常，记录日志 |
| `LazyInitManager.kt` | 延迟初始化管理器：空闲时初始化组件 |
| `MemoryUtils.kt` | 内存工具：内存监控和优化辅助 |
| `Extensions.kt` | Kotlin 扩展函数 |
| `Result.kt` | Result 类型封装 |

---

## 5. 关键类与函数说明

### 5.1 App.java

```java
// 核心入口，配置全局异常捕获
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化全局崩溃处理器
        // 配置主题和配色方案
        // 注册 Activity 生命周期回调
    }

    // 应用当前主题设置
    public void applyTheme() { ... }

    // 应用当前配色方案
    public void applyColorScheme() { ... }
}
```

### 5.2 MainActivity.java

```java
// 主容器，Fragment 切换
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 1. 设置底部导航栏
        // 2. 加载默认 Fragment (GamesFragment)
        // 3. 自动检查更新
        // 4. 权限检查
    }

    // 导航切换
    private void navigateTo(int itemId) { ... }

    // 检查更新
    private void checkUpdate() { ... }
}
```

### 5.3 GameRegistry.java

```java
// 游戏注册中心，集中管理所有游戏元数据
public class GameRegistry {
    // 注册所有游戏
    public static List<Entry> getGames() {
        return Arrays.asList(
            entry("gomoku", "五子棋", ...),
            entry("chinesechess", "中国象棋", ...),
            entry("doudizhu", "斗地主", ...),
            // ... 共 26 个游戏
        );
    }

    // 按分类获取游戏
    public static List<Entry> getGamesByCategory(Category category) { ... }
}
```

### 5.4 UpdateManager.java

```java
// 版本更新管理核心类
public class UpdateManager {
    // 检查更新(多源回退)
    public void checkUpdate(Context context, UpdateCheckCallback callback);

    // 下载 APK(多下载源、速度检测、MD5 校验)
    public void downloadApk(Context context, UpdateInfo info, DownloadCallback callback);

    // 安装 APK(通过 Intent 触发系统安装器)
    public boolean installApk(Context context, File apkFile);

    // 清理旧版本 APK(仅保留最新)
    public int cleanOldApks(Context context);

    // 打开下载目录
    public boolean openDownloadDirectory(Context context);
}
```

**更新检查流程**:
```
checkUpdate()
  → buildUpdateUrls() (香港VPS → 美国VPS → GitHub)
  → 依次尝试每个源
    → fetchJson(version-beta.json / version-release.json)
    → parse UpdateInfo
    → compare versionCode
    → return 第一个成功的结果
```

**下载流程**:
```
downloadApk()
  → cleanOldApksBeforeDownload()  // 清理旧版本
  → downloadFromUrl()
    → 创建临时文件 GameCenter_v{code}_{name}.apk
    → 64KB buffer 流式下载
    → 3秒后检测速度，低于50KB/s切换源
    → MD5 校验
  → showDownloadCompleteNotification()
  → callback.onComplete(apkFile)
```

### 5.5 AiTaskRouter.java

```java
// AI 任务调度中心，本地优先策略
public class AiTaskRouter {
    // 提交任务，自动路由到本地或云端
    public AiTask submitTask(String taskType, String input, AiCallback callback);

    // 本地优先：先尝试本地处理，失败再走云端
    private void executeTask(AiTask task, AiCallback callback) {
        // 1. 尝试本地处理 (OCR、摘要、关键词、分类)
        // 2. 本地无法处理，检查云端配额
        // 3. 走云端 API 调用
    }
}
```

---

## 6. 游戏模块架构

### 6.1 游戏分类

| 分类 | 游戏 |
|------|------|
| 经典 | 五子棋、围棋、中国象棋、21点、跳棋 |
| 益智 | 2048、数独、华容道、接水管、推箱子、消消乐、连连看、记忆翻牌、猜数字 |
| 休闲 | 斗地主、蛇、俄罗斯方块、打砖块、Flappy Bird、Brotato、飞机大战 |
| 反应 | 反应速度、打地鼠、石头剪刀布 |
| 其他 | 骰子 |

### 6.2 游戏三层架构示例（以五子棋为例）

```
GomokuActivity          ← UI 层：按钮、菜单、屏幕方向控制
    ↓
GomokuGame              ← 逻辑层：棋盘状态、落子判断、胜负判定、AI 对手
    ↓
GomokuView              ← 渲染层：Canvas 绘制棋盘、棋子、动画
```

### 6.3 联机游戏架构

支持联机的游戏：斗地主、围棋、五子棋、中国象棋、石头剪刀布

```
BaseOnlineActivity
    ├── GameSocketServer   (房主端 WebSocket 服务)
    ├── GameSocketClient   (加入端 WebSocket 客户端)
    ├── OnlineChatHelper   (联机聊天)
    └── 游戏特有逻辑       (子类实现)
```

联机流程：
```
1. 房主创建房间 → 生成房间码 → 启动 GameSocketServer
2. 加入者输入房间码 → GameSocketClient 连接房主
3. 连接建立 → 开始游戏 → 消息通过 WebSocket 同步
4. 中继服务器（香港 VPS）用于 NAT 穿透和信令交换
```

---

## 7. 网络与联机架构

### 7.1 服务器架构

```
客户端 (App)
    │
    ├── HTTP 请求 ──→ 香港 VPS (hk-update.tcp0053.shop)
    │                      ├── /version-release.json
    │                      ├── /version-beta.json
    │                      ├── /app-release.apk
    │                      └── /api/ddz-relay (斗地主中继)
    │
    ├── HTTP 请求 ──→ 美国 VPS (tcp0053.shop:1443) [备用]
    │
    ├── WebSocket ──→ 香港 VPS (hk-ws.tcp0053.shop/ddz-ws)
    │
    └── GitHub Releases [备用3]
```

### 7.2 VPS 服务端组件

| 服务 | 路径 | 语言 | 说明 |
|------|------|------|------|
| 更新服务器 | `vps/var_www_update/update_server.py` | Python | APK 下载 + version.json 分发 |
| 斗地主中继 | `vps/var_www_update/ddz_relay/ddz_relay_server.py` | Python | WebSocket 消息转发 |
| 反馈服务器 | `vps/var_www_update/feedback/feedback_server.py` | Python | 用户反馈收集 |
| Node.js 中继 | `vps/ddz_ws_relay/server.js` | Node.js | WebSocket 中继(备用) |

---

## 8. 工具模块架构

### 8.1 工具分类

| 分类 | 工具 |
|------|------|
| 网络诊断 | Ping、Traceroute、DNS 查询、端口扫描、子网扫描、WiFi 信息、局域网扫描、网速测试 |
| 系统信息 | 系统信息、电池信息、传感器信息、设备信息 |
| 开发工具 | IP 查询、DNS 工具、文件哈希 |
| 实用工具 | 二维码生成/识别、剪贴板、取色器、颜色+、文本编码、屏幕信息 |
| AI | AI 助手 |
| 其他 | 权限与隐私、诊断报告 |

### 8.2 ToolBinder 接口

```java
public abstract class ToolBinder {
    public abstract String getName();          // 工具名称
    public abstract String getIconResName();   // 图标资源名
    public abstract void run(Context context); // 执行工具
}
```

每个具体工具实现 `run()` 方法，启动相应的 Activity 或执行逻辑。

---

## 9. AI 模块架构

### 9.1 本地优先策略

```
用户输入 → AiTaskRouter
              │
              ├── 本地处理 (免费、快速)
              │     ├── OCR 清洗
              │     ├── 摘要提取
              │     ├── 关键词提取
              │     ├── 文本分类
              │     └── 指令识别
              │
              └── 云端处理 (需 API Key)
                    └── AiApiClient → OpenAI 兼容接口
```

### 9.2 云端提供商

支持所有 OpenAI 兼容接口的提供商：
- OpenAI
- 智谱 AI
- 阿里通义
- 自定义 API Key + Base URL

---

## 10. 更新系统

### 10.1 更新流程

```
App 启动 → NetworkInitializer 预加载
    ↓
自动检查更新 → UpdateManager.checkUpdate()
    ↓
多源检查 (HK VPS → US VPS → GitHub)
    ↓
发现新版本 → 弹窗提示
    ↓
用户确认 → downloadApk()
    ↓
多下载源 → 速度检测 → MD5 校验
    ↓
cleanOldApksBeforeDownload() → 下载最新 APK
    ↓
通知栏提示 → installApk() → 系统安装器
```

### 10.2 APK 存储位置

```
外部存储: /storage/emulated/0/Android/data/com.gamecenter.app/files/Download/update/
内部存储: /data/data/com.gamecenter.app/files/update/
```

文件名格式：`GameCenter_v{versionCode}_{versionName}.apk`

### 10.3 防错机制

- **下载前清理**: 每次下载前清理旧 APK，避免多版本共存
- **打开目录清理**: 打开下载目录时清理旧 APK
- **MD5 校验**: 下载完成后校验文件完整性
- **速度检测**: 下载 3 秒后检测速度，低于 50KB/s 自动切换下载源
- **唯一 PendingIntent**: 使用时间戳作为请求码，避免 PendingIntent 指向旧文件

---

## 11. 构建与发布流程

### 11.1 Gradle 任务

| 任务 | 说明 |
|------|------|
| `:app:assembleDebug` | 编译 Debug APK |
| `:app:assembleRelease` | 编译 Release APK (需签名) |
| `:app:buildAndUploadReleaseToVps` | 编译并上传到 VPS |
| `:app:uploadReleaseToVps` | 上传 Release APK 到 VPS |
| `:app:uploadApkToGitHubRelease` | 上传 APK 到 GitHub Releases |
| `:app:generateVersionJson` | 生成 version.json |
| `:app:bumpVersion` | 自动递增 versionCode |

### 11.2 签名配置

Release 版本需要 `keystore.properties` 文件：

```properties
STORE_FILE=path/to/keystore.jks
STORE_PASSWORD=...
KEY_ALIAS=...
KEY_PASSWORD=...
```

签名方案：v1 + v2

### 11.3 版本管理

版本信息由 `version.properties` 控制：
```properties
versionCode=236
versionName=1.3.20
lastStableVersionCode=224
lastStableVersionName=1.3.18
betaNoticeVersionGap=3
```

### 11.4 双版本分发

| 通道 | 说明 |
|------|------|
| Beta | 上传到 VPS，供开启"接受测试版"的用户下载 |
| Stable | 同时上传到 VPS 和 GitHub Releases |

### 11.5 服务器配置

`local.properties` 配置服务器地址：
```properties
server.url=https://hk-update.tcp0053.shop
server.url.fallback=https://tcp0053.shop:1443
relay.url=https://hk-relay.tcp0053.shop/api/ddz-relay
ws.url=wss://hk-ws.tcp0053.shop/ddz-ws
```

---

## 12. 项目运行方式

### 12.1 环境要求

| 项目 | 要求 |
|------|------|
| JDK | 17+ |
| Android SDK | 35 |
| Android Gradle Plugin | 8.7.3 |
| Gradle | 8.x |
| Python | 3.x (用于发布脚本) |
| Node.js | (VPS 服务端) |

### 12.2 首次设置

```bash
# 1. 克隆仓库
git clone https://github.com/3571949306/GameCenterApp.git
cd GameCenterApp

# 2. 创建 local.properties
# 复制 local.properties.template 并填写服务器地址

# 3. (可选) 配置签名用于 Release 构建
# 创建 keystore.jks 和 keystore.properties

# 4. 用 Android Studio 打开项目
# 或命令行构建
```

### 12.3 构建命令

```powershell
# Debug 构建 (无需签名)
.\gradlew.bat :app:assembleDebug

# Release 构建 (需要签名配置)
.\gradlew.bat :app:assembleRelease

# 构建并发布到 VPS (Beta 通道)
.\gradlew.bat :app:buildAndUploadReleaseToVps

# 构建并发布 (Stable 通道 + GitHub Releases)
.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable

# 运行测试
.\gradlew.bat :app:test
```

### 12.4 输出位置

| 类型 | 路径 |
|------|------|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `app/build/outputs/apk/release/app-release.apk` |
| version.json | `app/build/outputs/apk/{debug,release}/version.json` |

### 12.5 测试

项目包含 10+ 个游戏逻辑单元测试：
- ChineseChessGameTest
- DiceGameTest
- Game2048GameTest
- GuessGameTest
- KlotskiGameTest
- MemoryGameTest
- SnakeGameTest
- TicGameTest
- GoGameTest
- GomokuGameTest
- ResultTest (Kotlin)

```powershell
.\gradlew.bat :app:test
```

---

## 附录

### A. 项目文件结构

```
GameCenterApp/
├── app/
│   ├── build.gradle              # 模块构建配置
│   ├── proguard-rules.pro        # 代码混淆规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   └── pkgInfo.txt
│       │   ├── java/com/gamecenter/app/
│       │   │   ├── App.java              # 入口
│       │   │   ├── MainActivity.java     # 主容器
│       │   │   ├── SettingsManager.java  # 设置管理
│       │   │   ├── ai/                   # AI 模块
│       │   │   ├── fragments/            # UI Fragment
│       │   │   ├── games/                # 游戏模块 (26个)
│       │   │   ├── initializers/         # 启动初始化
│       │   │   ├── network/              # 网络联机
│       │   │   ├── settings/             # 设置弹窗
│       │   │   ├── tools/                # 工具模块 (30+)
│       │   │   ├── update/               # 更新系统
│       │   │   ├── utils/                # 工具类
│       │   │   └── views/                # 自定义 View
│       │   ├── kotlin/com/gamecenter/app/
│       │   │   ├── di/AppModule.kt       # Hilt DI
│       │   │   └── util/                 # Kotlin 工具
│       │   └── res/                      # 资源文件
│       └── test/                         # 测试代码
├── vps/                          # VPS 服务端代码
├── tools/                        # 发布工具脚本
├── docs/                         # 文档
├── build.gradle                  # 根构建配置
├── settings.gradle
├── version.properties
├── local.properties.template
└── README.md
```

### B. 权限说明

| 权限 | 用途 |
|------|------|
| INTERNET | 网络请求、联机游戏 |
| ACCESS_NETWORK_STATE | 网络状态检测 |
| ACCESS_WIFI_STATE | WiFi 信息获取 |
| CHANGE_WIFI_MULTICAST_STATE | 局域网扫描 |
| ACCESS_FINE_LOCATION / COARSE_LOCATION | 局域网扫描需要位置权限 |
| CAMERA | 二维码扫描 |
| READ/WRITE_EXTERNAL_STORAGE | APK 下载存储 |
| REQUEST_INSTALL_PACKAGES | 安装更新 APK |

---

*文档生成时间: 2026-05-13*
*基于版本: v1.3.20 (versionCode: 236)*
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
