# GameCenterApp Code Wiki

> 最后更新：2026-05-10  
> 项目版本：由 `version.properties` 动态管理  
> 最低 SDK：24 (Android 7.0) | 目标 SDK：35

---

## 目录

1. [项目概述](#1-项目概述)
2. [项目架构总览](#2-项目架构总览)
3. [目录结构](#3-目录结构)
4. [核心模块详解](#4-核心模块详解)
   - 4.1 [应用入口层（App / MainActivity）](#41-应用入口层app--mainactivity)
   - 4.2 [游戏大厅模块（GamesFragment / GameRegistry）](#42-游戏大厅模块gamesfragment--gameregistry)
   - 4.3 [浏览器模块（BrowserFragment）](#43-浏览器模块browserfragment)
   - 4.4 [工具箱模块（ToolsFragment + ToolBinder 架构）](#44-工具箱模块toolsfragment--toolBinder-架构)
   - 4.5 [游戏引擎模块（games 包）](#45-游戏引擎模块games-包)
   - 4.6 [网络联机模块（network 包）](#46-网络联机模块network-包)
   - 4.7 [更新与分发模块（update 包）](#47-更新与分发模块update-包)
   - 4.8 [设置与主题模块](#48-设置与主题模块)
   - 4.9 [存档管理模块（SaveManager）](#49-存档管理模块savemanager)
5. [VPS 服务端](#5-vps-服务端)
6. [依赖关系图](#6-依赖关系图)
7. [构建与运行](#7-构建与运行)
8. [关键数据流](#8-关键数据流)
9. [扩展指南](#9-扩展指南)

---

## 1. 项目概述

GameCenterApp 是一款 Android 游戏中心应用，集成了 **22+ 款内置小游戏**、**内置浏览器**、**网络工具箱**，并支持 **LAN 局域网联机**、**云中转联机**（HTTP Relay）和 **WebSocket 联机** 三种多人游戏模式。应用采用 Material Design 风格，支持 8 种配色方案和系统/亮色/暗色三种主题模式。

### 核心特性

| 特性 | 说明 |
|------|------|
| 游戏大厅 | 分类展示（经典/益智/休闲/反应/其他），支持搜索、收藏、最近游玩 |
| 内置浏览器 | 多标签页 WebView，搜索引擎切换，书签/历史，搜索建议 |
| 网络工具箱 | IP/DNS/WiFi/测速/端口扫描/Ping/Traceroute/子网计算/二维码等 20+ 工具 |
| 联机对战 | 五子棋、中国象棋、围棋、斗地主、石头剪刀布支持在线对战 |
| 自动更新 | 多源回退（香港VPS → GitHub → 美国VPS），Beta/Release 双通道分发 |
| 反馈系统 | VPS 反馈收集 + 邮件兜底 |

---

## 2. 项目架构总览

```
┌──────────────────────────────────────────────────────────────────┐
│                        Android Application                       │
│  ┌──────────┐  ┌──────────────┐  ┌──────────┐  ┌─────────────┐ │
│  │   App    │  │ MainActivity │  │ Settings │  │ ColorScheme │ │
│  │(Application)│(Navigation)  │  │ Manager  │  │  Manager    │ │
│  └────┬─────┘  └──────┬───────┘  └────┬─────┘  └──────┬──────┘ │
│       │               │               │                │        │
│  ┌────┴───────────────┴───────────────┴────────────────┴──────┐ │
│  │                    Navigation Graph                         │ │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────────┐   │ │
│  │  │GamesFragment │ │BrowserFrag   │ │ToolsFragment     │   │ │
│  │  │(游戏大厅)     │ │(浏览器)      │ │(工具箱)          │   │ │
│  │  └──────┬───────┘ └──────────────┘ └──────────────────┘   │ │
│  └─────────┼──────────────────────────────────────────────────┘ │
│            │                                                    │
│  ┌─────────┴──────────────────────────────────────────────────┐ │
│  │                    Game Engine Layer                        │ │
│  │  GameRegistry → Entry → Activity → GameLogic → View        │ │
│  │  GameUsageStore / SaveManager / GameStats                  │ │
│  └────────────────────────────────────────────────────────────┘ │
│            │                                                    │
│  ┌─────────┴──────────────────────────────────────────────────┐ │
│  │                    Network Layer                            │ │
│  │  GameSocketServer ─── GameSocketClient ─── LANManager      │ │
│  │  RelayHttpClient ─── RemoteP2PUtil ─── WebSocket(OkHttp)  │ │
│  └────────────────────────────────────────────────────────────┘ │
│            │                                                    │
│  ┌─────────┴──────────────────────────────────────────────────┐ │
│  │                    Update & Feedback Layer                  │ │
│  │  UpdateManager ─── SSLHelper ─── UpdateInfo                │ │
│  │  Feedback (VPS POST / Email fallback)                      │ │
│  └────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
         │                              │
    ┌────┴─────┐                  ┌─────┴──────┐
    │ VPS 集群  │                  │ WebSocket  │
    │ 更新服务  │                  │ Relay 服务 │
    │ 反馈服务  │                  │ (Node.js)  │
    └──────────┘                  └────────────┘
```

---

## 3. 目录结构

```
GameCenterApp/
├── app/
│   ├── build.gradle                    # 应用构建配置
│   └── src/main/
│       ├── AndroidManifest.xml         # 清单文件
│       ├── assets/                     # 静态资源（version.json 等）
│       ├── java/com/gamecenter/app/
│       │   ├── App.java                # Application 入口
│       │   ├── MainActivity.java       # 主 Activity（Navigation 宿主）
│       │   ├── ColorSchemeManager.java # 配色方案管理
│       │   ├── SaveManager.java        # 通用存档管理器
│       │   ├── SettingsManager.java    # 设置管理器
│       │   ├── fragments/
│       │   │   ├── GamesFragment.java  # 游戏大厅
│       │   │   ├── BrowserFragment.java# 内置浏览器
│       │   │   └── ToolsFragment.java  # 工具箱
│       │   ├── games/
│       │   │   ├── GameRegistry.java   # 游戏注册中心
│       │   │   ├── GameUsageStore.java # 游戏使用统计
│       │   │   ├── GameStats.java      # 统计数据模型
│       │   │   ├── GameTutorialHelper.java
│       │   │   ├── StatsActivity.java  # 统计展示
│       │   │   ├── blackjack/          # 21点
│       │   │   ├── breakout/           # 打砖块
│       │   │   ├── brotato/            # 土豆兄弟
│       │   │   ├── checkers/           # 跳棋
│       │   │   ├── chinesechess/       # 中国象棋（含联机）
│       │   │   ├── dice/               # 骰子
│       │   │   ├── doudizhu/           # 斗地主（含联机）
│       │   │   ├── flappy/             # Flappy Bird
│       │   │   ├── game2048/           # 2048
│       │   │   ├── go/                 # 围棋（含联机）
│       │   │   ├── gomoku/             # 五子棋（含联机）
│       │   │   ├── guess/              # 猜数字
│       │   │   ├── klotski/            # 华容道
│       │   │   ├── match/              # 配对游戏
│       │   │   ├── memory/             # 记忆翻牌
│       │   │   ├── pipeline/           # 管道连接
│       │   │   ├── plane/              # 飞机大战
│       │   │   ├── reaction/           # 反应测试
│       │   │   ├── rock/               # 石头剪刀布（含联机）
│       │   │   ├── snake/              # 贪吃蛇
│       │   │   ├── sokoban/            # 推箱子
│       │   │   ├── sudoku/             # 数独
│       │   │   ├── tetris/             # 俄罗斯方块
│       │   │   ├── tic/                # 井字棋
│       │   │   ├── tiles/              # 别踩白块
│       │   │   └── whack/              # 打地鼠
│       │   ├── network/
│       │   │   ├── GameSocketClient.java  # 联机客户端
│       │   │   ├── GameSocketServer.java  # 联机服务端
│       │   │   ├── LANManager.java        # 局域网发现
│       │   │   ├── RelayHttpClient.java   # 云中转 HTTP 通信
│       │   │   └── RemoteP2PUtil.java     # 远程 P2P 工具
│       │   ├── update/
│       │   │   ├── UpdateManager.java     # 更新管理器
│       │   │   ├── UpdateInfo.java        # 更新信息模型
│       │   │   └── SSLHelper.java         # SSL 信任配置
│       │   ├── settings/
│       │   │   └── AppSettingsDialog.java # 设置对话框
│       │   ├── tools/
│       │   │   ├── ToolBinder.java            # 工具绑定器策略接口
│       │   │   ├── ToolHelper.java             # 共享辅助方法（网络/系统/计算）
│       │   │   ├── ToolSection.java            # 工具区数据模型
│       │   │   ├── ToolSectionStore.java       # 工具区持久化
│       │   │   ├── IpToolBinder.java           # IP 地址查询
│       │   │   ├── DnsToolBinder.java          # DNS 查询
│       │   │   ├── WifiToolBinder.java         # WiFi/移动信号
│       │   │   ├── SpeedTestToolBinder.java    # 网络测速
│       │   │   ├── PortScanToolBinder.java     # 端口扫描
│       │   │   ├── QrToolBinder.java           # 二维码
│       │   │   ├── BatteryToolBinder.java      # 电池信息（含 BroadcastReceiver 管理）
│       │   │   ├── DeviceToolBinder.java       # 设备信息
│       │   │   ├── PingToolBinder.java         # Ping 测试
│       │   │   ├── TracerouteToolBinder.java   # 路由追踪
│       │   │   ├── SubnetToolBinder.java       # 子网计算
│       │   │   ├── ScreenToolBinder.java       # 屏幕信息
│       │   │   ├── SensorToolBinder.java       # 传感器列表
│       │   │   ├── SystemInfoToolBinder.java   # 系统信息
│       │   │   ├── HashToolBinder.java         # 哈希计算
│       │   │   ├── ClipboardToolBinder.java    # 剪贴板
│       │   │   ├── ColorPickerToolBinder.java  # 取色器
│       │   │   └── AdvancedToolBinders.java    # 高级工具绑定（DNS查询/LAN扫描/编解码等）
│       │   └── utils/
│       │       └── SystemInfoCollector.java # 系统信息采集
│       └── res/                           # 布局、图标、字符串等资源
├── vps/
│   ├── ddz_ws_relay/
│   │   └── server.js                   # WebSocket 中继服务（Node.js）
│   └── var_www_update/
│       ├── update_server.py             # 更新分发服务（Python）
│       └── feedback/
│           └── feedback_server.py       # 反馈收集服务（Python）
├── tools/
│   ├── upload_to_vps.py                # VPS 上传脚本
│   └── upload_to_github_release.py     # GitHub Release 上传脚本
├── version.properties                   # 版本号配置
├── local.properties                     # 本地配置（服务器URL等）
└── build.gradle                         # 根构建文件
```

---

## 4. 核心模块详解

### 4.1 应用入口层（App / MainActivity）

#### [App.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/App.java)

`Application` 子类，全局初始化入口。

| 方法 | 职责 |
|------|------|
| `onCreate()` | 调用 `applyTheme()` 应用主题；注册 `ActivityLifecycleCallbacks`，在每个 Activity 创建时自动应用配色方案 |
| `applyTheme()` | 根据 `SettingsManager.getThemeMode()` 设置 `AppCompatDelegate` 夜间模式 |
| `applyColorScheme(Activity)` | 读取 `SettingsManager.getColorSchemeIndex()`，通过 `ColorSchemeManager.applyScheme()` 为 Activity 着色 |
| `shouldAutoCheckUpdate()` | 确保每次进程启动只自动检查一次更新 |
| `refreshColorScheme(Activity)` | 静态方法，供外部调用刷新配色 |

#### [MainActivity.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/MainActivity.java)

主 Activity，使用 Navigation Component 管理三个 Fragment。

| 方法 | 职责 |
|------|------|
| `onCreate()` | 设置布局，初始化 `NavController`，绑定 `BottomNavigationView`，延迟 2 秒调度自动更新检查 |
| `checkUpdate(boolean showToast)` | 通过 `UpdateManager` 检查更新，支持手动/自动两种模式，处理 Beta/Release 分发策略 |
| `startDownload(UpdateInfo)` | 展示进度弹窗，调用 `UpdateManager.downloadApk()` |
| `startAutoDownload(UpdateInfo, boolean)` | 后台静默下载，完成后提示安装 |
| `installApk(File)` | 通过 `FileProvider` + `Intent` 安装 APK，处理 Android 8.0+ 安装权限 |

---

### 4.2 游戏大厅模块（GamesFragment / GameRegistry）

#### [GameRegistry.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/games/GameRegistry.java)

游戏注册中心，**所有游戏的元数据集中声明**。新增游戏只需在此类中添加 `Entry` 即可。

**核心数据结构：**

```java
public static final class Category {
    public final String name;        // 分类名：经典/益智/休闲/反应/其他
    public final List<Entry> games;  // 该分类下的游戏列表
}

public static final class Entry {
    public final String id;              // 唯一标识，如 "gomoku"
    public final int iconRes;            // 图标资源 ID
    public final String name;            // 显示名称
    public final String desc;            // 描述
    public final Class<?> activityClass; // 对应 Activity 类
    public final String category;        // 所属分类名
}
```

**关键方法：**

| 方法 | 说明 |
|------|------|
| `getCategories(Context)` | 返回 5 大分类的不可变列表 |
| `flatten(List<Category>)` | 将所有分类展平为单一游戏列表 |

**游戏分类一览：**

| 分类 | 游戏 |
|------|------|
| 经典 | 五子棋、围棋、中国象棋、贪吃蛇、俄罗斯方块、斗地主、土豆兄弟 |
| 益智 | 2048、数独、推箱子、管道连接、华容道 |
| 休闲 | 打砖块、打地鼠、配对、21点、跳棋 |
| 反应 | Flappy Bird、别踩白块、飞机大战、石头剪刀布、反应测试 |
| 其他 | 井字棋、记忆翻牌、猜数字、骰子 |

#### [GamesFragment.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/fragments/GamesFragment.java)

游戏大厅 Fragment，TabLayout + RecyclerView 卡片网格。

| 组件 | 说明 |
|------|------|
| TabLayout | 全部 / 最近 / 收藏 / 经典 / 益智 / 休闲 / 反应 / 其他 |
| RecyclerView | 以 `GameAdapter` 展示游戏卡片 |
| 搜索框 | 实时过滤（名称/描述/分类） |
| 设置按钮 | 打开 `AppSettingsDialog` |
| 反馈按钮 | VPS 反馈提交 + 邮件兜底 |

**关键内部逻辑：**
- `getSourceGames()` — 根据当前 Tab 返回数据源（全部/最近/收藏/分类）
- `filterGames(source, query)` — 按搜索关键词过滤
- `applyCardColorScheme()` — 根据当前配色方案和明暗模式着色卡片
- `submitFeedbackToVps()` — 异步 POST 反馈到 VPS

#### [GameUsageStore.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/games/GameUsageStore.java)

游戏使用统计持久化（SharedPreferences）。

| 方法 | 说明 |
|------|------|
| `recordLaunch(gameId)` | 记录启动，更新 playCount、lastPlayedAt、recentIds |
| `getPlayCount(gameId)` | 获取游玩次数 |
| `getLastPlayedAt(gameId)` | 获取最后游玩时间戳 |
| `getRecentIds(int limit)` | 获取最近游玩的游戏 ID 列表 |
| `toggleFavorite(gameId)` | 切换收藏状态 |
| `isFavorite(gameId)` | 判断是否已收藏 |
| `getFavoriteIds()` | 获取所有收藏 ID |

---

### 4.3 浏览器模块（BrowserFragment）

#### [BrowserFragment.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/fragments/BrowserFragment.java)

功能完整的内置浏览器，基于 WebView。

**核心特性：**

| 特性 | 实现 |
|------|------|
| 多标签页 | `TabInfo` 列表 + `HorizontalScrollView` 标签栏，支持新建/切换/关闭 |
| 搜索引擎 | 百度（默认）/ Google / Bing，可切换 |
| 搜索建议 | 百度 API 实时获取，其他引擎回退到本地书签/历史匹配 |
| 书签 | `SharedPreferences` 存储 URL 集合 |
| 历史记录 | JSON 数组存储（最多 100 条），含时间戳 |
| 桌面模式 | 切换 User-Agent（Mobile ↔ Desktop） |
| 下载 | `DownloadManager` 处理文件下载 |
| 状态保存 | `onSaveInstanceState` 保存标签页 URL/Title/WebView State |

**关键内部类：**

- `TabInfo` — 标签页数据（title, url, Bundle state）
- `SuggestionAdapter` — 搜索建议 RecyclerView 适配器

**关键方法：**

| 方法 | 说明 |
|------|------|
| `configureWebView()` | 启用 JS、DOM Storage、缩放、Cookie 等 |
| `processInput(String)` | URL 判断逻辑：http(s) 直接用，含点无空格补 https，否则搜索 |
| `switchToTab(int)` | 保存当前标签状态 → 加载目标标签 URL/State |
| `fetchSuggestions(String)` | 百度搜索建议 API 调用，300ms 防抖 |

---

### 4.4 工具箱模块（ToolsFragment + ToolBinder 架构）

#### 架构概述

工具箱采用**策略模式**设计，将每个工具的 UI 绑定和业务逻辑封装到独立的 `ToolBinder` 实现类中，避免了单一 Fragment 的"上帝类"问题。

```
ToolsFragment (RecyclerView 调度)
  ├── 布局管理（单列/双列切换）
  ├── 拖拽排序（ItemTouchHelper）
  ├── 收藏/筛选
  │
  └── SectionViewHolder.bindContent()
        ├── ToolBinder 注册表 (Map<String, ToolBinder>)
        │     ├── IpToolBinder → 工具卡内容视图
        │     ├── DnsToolBinder → 工具卡内容视图
        │     ├── BatteryToolBinder → 工具卡内容视图 (含 BroadcastReceiver)
        │     └── ... 其他 17 个工具
        │
        └── AdvancedToolBinders (高级工具)
              ├── bindNetworkDiagnosis()
              ├── bindDiagnosticReport()
              └── ... 其他高级工具
```

#### [ToolBinder.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/tools/ToolBinder.java)

工具绑定器策略接口，所有工具必须实现此接口。

```java
public interface ToolBinder {
    void bind(Context context, View contentView, ExecutorService executor);
}
```

| 参数 | 说明 |
|------|------|
| `context` | Android Context，用于系统服务访问 |
| `contentView` | 工具卡片的内容视图（由 layout XML inflate 而来） |
| `executor` | 后台线程执行器，用于网络/耗时操作 |

**设计原则：**
- 实现类应为无状态或使用参数传入状态，避免持有 Fragment/Activity 引用
- 需要生命周期管理的工具（如 BatteryToolBinder 的 BroadcastReceiver）应提供 `unbind()` 方法

#### [ToolHelper.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/tools/ToolHelper.java)

共享辅助类，提供网络/系统信息读取的静态方法，避免代码重复。

| 方法 | 说明 |
|------|------|
| `getWifiIpAddress(Context)` | 获取 WiFi IP 地址 |
| `getMobileIpAddress()` | 获取移动数据 IP |
| `checkVpnStatus(Context)` | 检查 VPN 状态 |
| `getDnsServers(Context)` | 获取 DNS 服务器列表 |
| `getWifiSignalStrength(Context)` | 获取 WiFi 信号强度 |
| `testPing()` | 快速 Ping 测试 |
| `testDownloadSpeed(String)` | 下载速度测试 |
| `testUploadSpeed(String)` | 上传速度测试 |
| `pingHost(String)` | Ping 指定主机 |
| `traceRouteHop(String, int)` | 单跳 Traceroute |
| `calculateSubnet(String)` | 子网计算器 (IP/CIDR) |
| `classifyIpCarrier(String)` | IP 运营商分类 |
| `sensorTypeName(int)` | 传感器类型中文名 |
| `getMobileNetworkType(TelephonyManager)` | 移动网络类型 |

#### [ToolsFragment.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/fragments/ToolsFragment.java)

工具箱 Fragment，使用策略模式架构后的核心职责：

| 职责 | 说明 |
|------|------|
| RecyclerView 调度 | 管理工具卡片列表的展示 |
| 布局模式切换 | 单列/双列网格切换，持久化到 SharedPreferences |
| 拖拽排序 | ItemTouchHelper 支持四向拖拽 |
| ToolBinder 注册 | `initBinders()` 注册 20 个标准工具 |
| 高级工具调度 | switch-case 调用 `AdvancedToolBinders` |
| 配色方案 | `applyColorScheme()` 自动应用主题 |
| 生命周期 | `onDestroyView()` 注销 BroadcastReceiver |

**26 个工具区列表：**

| ID | 工具名 | Binder 类 |
|----|--------|-----------|
| `ip` | IP 地址 | `IpToolBinder` |
| `dns` | DNS 信息 | `DnsToolBinder` |
| `wifi` | WiFi 信号 | `WifiToolBinder` |
| `speedtest` | 网络测速 | `SpeedTestToolBinder` |
| `portscan` | 端口扫描 | `PortScanToolBinder` |
| `qr` | 二维码 | `QrToolBinder` |
| `battery` | 电池信息 | `BatteryToolBinder` |
| `device` | 设备信息 | `DeviceToolBinder` |
| `ping` | Ping 工具 | `PingToolBinder` |
| `traceroute` | 路由追踪 | `TracerouteToolBinder` |
| `subnet` | 子网计算 | `SubnetToolBinder` |
| `screen` | 屏幕信息 | `ScreenToolBinder` |
| `sensor` | 传感器 | `SensorToolBinder` |
| `hash` | 哈希计算 | `HashToolBinder` |
| `clipboard` | 剪贴板 | `ClipboardToolBinder` |
| `color` | 取色器 | `ColorPickerToolBinder` |
| `sysinfo` | 系统信息 | `SystemInfoToolBinder` |
| `network_diagnosis` | 网络诊断 | `AdvancedToolBinders` |
| `diagnostic_report` | 诊断报告 | `AdvancedToolBinders` |
| `dns_lookup` | DNS 查询 | `AdvancedToolBinders` |
| `lan_scan` | LAN 扫描 | `AdvancedToolBinders` |
| `text_codec` | 文本编解码 | `AdvancedToolBinders` |
| `file_hash` | 文件哈希 | `AdvancedToolBinders` |
| `qr_plus` | 高级二维码 | `AdvancedToolBinders` |
| `color_plus` | 高级取色 | `AdvancedToolBinders` |
| `permission_privacy` | 权限隐私 | `AdvancedToolBinders` |

**添加新工具的步骤（重构后）：**

1. 创建工具布局 XML（如 `content_xxx.xml`）
2. 创建 `XxxToolBinder` 类实现 `ToolBinder` 接口
3. 在 `ToolSectionStore.loadSections()` 中添加 `ToolSection`
4. 在 `ToolsFragment.initBinders()` 中注册 `binders.put("xxx", new XxxToolBinder())`

**对比重构前：**
- 之前：每个工具需要在 `bindContent()` 的 switch 语句中添加分支，单文件超过 2000 行
- 现在：每个工具独立类，职责清晰，测试友好

---

### 4.5 游戏引擎模块（games 包）

每个游戏子包通常包含以下结构：

```
games/gomoku/
├── GomokuActivity.java       # Activity（竖屏）
├── GomokuGame.java           # 游戏逻辑
├── GomokuView.java           # 自定义 View（绘制棋盘）
└── GomokuOnlineActivity.java # 联机模式 Activity（如有）
```

**支持联机的游戏：**

| 游戏 | 联机 Activity | 联机方式 |
|------|--------------|---------|
| 五子棋 | `GomokuOnlineActivity` | LAN / Relay / WebSocket |
| 中国象棋 | `ChineseChessOnlineActivity` | LAN / Relay / WebSocket |
| 围棋 | `GoOnlineActivity` | LAN / Relay / WebSocket |
| 斗地主 | `DouDiZhuOnlineActivity` | LAN / Relay / WebSocket |
| 石头剪刀布 | `RockOnlineActivity` | LAN / Relay / WebSocket |

**游戏 Activity 通用模式：**
1. `onCreate()` 中初始化 `GameView` 和 `GameLogic`
2. `onPause()` 中通过 `SaveManager.save()` 自动存档
3. `onResume()` 中检测存档并提示恢复
4. 联机 Activity 额外初始化 `GameSocketServer`/`GameSocketClient`

---

### 4.6 网络联机模块（network 包）

这是项目最复杂的模块之一，支持三种联机传输方式：

```
┌─────────────────────────────────────────────────────────┐
│                  联机传输层架构                           │
│                                                         │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  LAN 直连   │  │ HTTP Relay   │  │  WebSocket    │  │
│  │ (TCP Socket)│  │ (长轮询)     │  │  (OkHttp WS)  │  │
│  └──────┬──────┘  └──────┬───────┘  └───────┬───────┘  │
│         │                │                   │          │
│  ┌──────┴────────────────┴───────────────────┴───────┐  │
│  │         GameSocketServer / GameSocketClient        │  │
│  │         (统一 API，屏蔽传输差异)                    │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

#### [GameSocketServer.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/network/GameSocketServer.java)

联机服务端（房主），单例模式，最多支持 4 个客户端。

| 方法 | 说明 |
|------|------|
| `start(int port)` | 启动 TCP ServerSocket 监听 |
| `startRelay(String baseUrl)` | 创建云中转房间（HTTP Relay） |
| `startWebSocket(String wsUrl)` | 连接 WebSocket 中继服务器 |
| `broadcast(JSONObject)` | 向所有客户端广播消息 |
| `sendTo(int clientId, JSONObject)` | 向指定客户端发送消息 |
| `disconnectClient(int, String)` | 断开指定客户端 |
| `stop()` | 停止服务，清理资源 |

**内部类 `ClientConnection`：** 封装 TCP Socket 连接，包含读写线程和心跳时间戳。

**三种模式的消息路由：**
- **LAN 模式**：直接通过 `ClientConnection.send()` 发送
- **Relay 模式**：通过 `RelayHttpClient.post("/send")` 发送
- **WebSocket 模式**：通过 `webSocket.send()` 发送，附加 `targetClientId` 或 `broadcast` 字段

**心跳机制：**
- TCP 模式：2 秒检查间隔，30 秒超时
- WebSocket 模式：10 秒 Ping 间隔，45 秒超时，2 次未响应 Pong 则断开

#### [GameSocketClient.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/network/GameSocketClient.java)

联机客户端，单例模式。

| 方法 | 说明 |
|------|------|
| `connectWebSocket(String wsUrl)` | 连接 WebSocket 中继 |
| `connectRelay(String roomCode, String baseUrl)` | 加入云中转房间 |
| `send(JSONObject)` | 发送消息（自动选择传输方式） |
| `sendJoin(String playerName)` | 发送加入房间消息 |
| `disconnect()` | 断开连接 |
| `reconnectNow()` | 立即重连 |

**连接状态机：**

```
DISCONNECTED → CONNECTING → CONNECTED → AUTHENTICATED
                  ↑              ↓
                  └── RECONNECTING ──┘
```

**重连策略：** 指数退避（2s → 4s → 8s，最大 15s），最多 3 次。

**消息队列：** WebSocket 模式下，未连接时消息缓存在 `ConcurrentLinkedQueue`，连接后自动发送（最多 32 条）。

**回调接口：**

| 接口 | 说明 |
|------|------|
| `OnConnectedListener` | 连接成功，返回 clientId |
| `OnDisconnectedListener` | 断开连接，返回原因 |
| `OnMessageReceivedListener` | 收到消息 |
| `OnErrorListener` | 发生错误 |
| `OnStateChangedListener` | 连接状态变化 |

#### [LANManager.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/network/LANManager.java)

局域网设备发现，基于 UDP 广播。

| 方法 | 说明 |
|------|------|
| `startDiscovery()` | 启动广播线程 + 接收线程 |
| `stopDiscovery()` | 停止发现 |
| `getDiscoveredHosts()` | 获取已发现的主机列表 |

**工作原理：**
- 广播线程每 3 秒发送 UDP 广播（端口 9877），包含游戏名、玩家名、服务器端口
- 接收线程监听同一端口的广播，发现同游戏的主机后加入列表
- 超时 8 秒未更新的主机视为过期

**内部类 `DiscoveredHost`：** 包含 IP、端口、玩家名、最后发现时间。

#### [RelayHttpClient.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/network/RelayHttpClient.java)

HTTP Relay 通信工具类，提供云中转的 HTTP API 调用和 WebSocket URL 生成。

| 方法 | 说明 |
|------|------|
| `post(baseUrl, path, body, timeout)` | 发送 HTTP POST 请求 |
| `getWebSocketUrl(baseUrl, roomCode, hostToken)` | 生成房主 WebSocket URL |
| `getWebSocketClientUrl(baseUrl, roomCode)` | 生成客户端 WebSocket URL |

**Relay API 端点：**

| 端点 | 用途 |
|------|------|
| `/create` | 创建房间，返回 roomCode + hostToken |
| `/join` | 加入房间，返回 clientId + clientToken |
| `/poll` | 长轮询获取消息（超时 35 秒） |
| `/send` | 发送消息（指定 to: "host"/clientId/"all"） |
| `/disconnect` | 断开连接 |
| `/close` | 关闭房间 |

#### [RemoteP2PUtil.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/network/RemoteP2PUtil.java)

远程 P2P 工具类，提供房间码验证、PeerToken 管理、WebSocket URL 构建。

| 方法 | 说明 |
|------|------|
| `normalizeRoomCode(String)` | 标准化房间码 |
| `isValidRoomCode(String)` | 验证 6 位数字房间码 |
| `savePeerToken()` / `getLastPeerToken()` | PeerToken 持久化 |
| `buildWebSocketUrl()` | 构建 WebSocket 连接 URL |

---

### 4.7 更新与分发模块（update 包）

#### [UpdateManager.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/update/UpdateManager.java)

应用更新管理器，单例模式，支持多源回退和 Beta/Release 双通道。

**下载源优先级：**

| 优先级 | 源 | 说明 |
|--------|-----|------|
| 1 | 自定义 URL | 用户在设置中配置的 URL |
| 2 | 香港 VPS | 主服务器（`BuildConfig.SERVER_URL`） |
| 3 | GitHub Releases | 备用源 1 |
| 4 | 美国 VPS | 备用源 2（`BuildConfig.SERVER_URL_FALLBACK`） |

**关键方法：**

| 方法 | 说明 |
|------|------|
| `checkUpdate(Context, UpdateCheckCallback)` | 检查更新（多源回退） |
| `downloadApk(Context, UpdateInfo, DownloadCallback)` | 下载 APK（多源回退 + 速度检测自动换源） |
| `installApk(Context, File)` | 安装 APK |
| `openDownloadDirectory(Context)` | 打开下载目录 |

**更新检查流程：**
1. 构建 URL 列表（按优先级）
2. 依次请求 `version-beta.json` 或 `version-release.json`
3. 解析 JSON 为 `UpdateInfo`
4. 应用更新策略（Beta 用户特殊处理）
5. 回退到 Legacy API（`/api/update/check`）

**下载流程：**
1. 构建下载 URL 列表
2. 逐源尝试下载
3. 3 秒后检测下载速度，低于 50 KB/s 自动换源
4. 下载完成后校验 MD5
5. 回调通知安装

**Beta/Release 双通道策略：**
- `acceptBeta=true`：检查 `version-beta.json`，无更新时检查 `version-release.json`
- `acceptBeta=false`：检查 `version-release.json`，Beta 用户额外检查 beta 版本并提示

#### [UpdateInfo.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/update/UpdateInfo.java)

更新信息数据模型，包含版本号、下载 URL、MD5、文件大小、更新日志、Beta 标记等。

#### [SSLHelper.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/update/SSLHelper.java)

SSL 信任配置工具，为自签名证书的更新服务器建立信任。

---

### 4.8 设置与主题模块

#### [SettingsManager.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/SettingsManager.java)

单例设置管理器，SharedPreferences 持久化。

| 设置项 | Key | 默认值 | 说明 |
|--------|-----|--------|------|
| 主题模式 | `theme_mode` | 0 (跟随系统) | 0=系统 / 1=亮色 / 2=暗色 |
| 配色方案 | `color_scheme` | 0 (清朗紫) | 0-7 对应 8 种配色 |
| 自动检查更新 | `auto_check_update` | true | 是否启动时自动检查 |
| 接受 Beta 更新 | `accept_beta_update` | false | 是否接受测试版 |
| 自动下载更新 | `auto_download_update` | false | 发现更新后自动下载 |
| 下载后提示安装 | `prompt_install_after_auto_download` | false | 自动下载完成后是否弹窗提示 |
| 更新源 | `update_source` | 0 (自动) | 0=自动 / 1=香港VPS / 2=美国VPS / 3=GitHub |

#### [ColorSchemeManager.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/ColorSchemeManager.java)

配色方案管理器，定义 8 种 Material Design 风格配色。

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

每种配色包含完整的亮色/暗色方案（primary, onPrimary, surface, onSurface 等 26 个色值）。

**关键方法：**

| 方法 | 说明 |
|------|------|
| `applyScheme(Activity, Scheme, boolean)` | 为 Activity 应用配色（状态栏、导航栏、TabLayout、BottomNav） |
| `applySchemeToView(View, Scheme, boolean)` | 为单个 View 应用配色 |

---

### 4.9 存档管理模块（SaveManager）

#### [SaveManager.java](file:///d:/kaifa/GameCenterApp/app/src/main/java/com/gamecenter/app/SaveManager.java)

通用存档管理器，单例模式，为所有游戏提供统一的存档/读档/删除接口。

**Key 格式：**
- 存档：`save_{gameId}_{slotKey}`（如 `save_sudoku_auto`）
- 进度：`progress_{gameId}`（如 `progress_sokoban`）

| 方法 | 说明 |
|------|------|
| `save(gameId, slotKey, jsonState)` | 保存游戏状态 |
| `load(gameId, slotKey)` | 读取游戏状态 |
| `hasSave(gameId, slotKey)` | 检查是否存在存档 |
| `deleteSave(gameId, slotKey)` | 删除存档 |
| `saveProgress(gameId, jsonProgress)` | 保存关卡进度 |
| `loadProgress(gameId)` | 读取关卡进度 |
| `hasProgress(gameId)` | 检查是否存在进度 |
| `deleteProgress(gameId)` | 删除进度 |

---

## 5. VPS 服务端

### 5.1 WebSocket 中继服务

**文件：** [vps/ddz_ws_relay/server.js](file:///d:/kaifa/GameCenterApp/vps/ddz_ws_relay/server.js)

**技术栈：** Node.js + `ws` 库

**端口：** 18080（默认绑定 127.0.0.1，需 Nginx 反代）

**房间管理：**
- `rooms` Map：roomCode → { host: ws, clients: Map<clientId, ws>, createdAt }
- 每 10 分钟清理空房间（1 小时以上未使用）

**消息路由：**
- 房主发送的消息 → 广播给所有客户端
- 客户端发送的消息 → 转发给房主
- PING → 回复 PONG

**HTTP 端点：**

| 端点 | 说明 |
|------|------|
| `/health` | 健康检查（返回房间数、运行时间） |
| `/stats` | 统计信息（房间数、内存使用） |

### 5.2 更新分发服务

**文件：** [vps/var_www_update/update_server.py](file:///d:/kaifa/GameCenterApp/vps/var_www_update/update_server.py)

**技术栈：** Python 3 + `http.server.ThreadingHTTPServer`

**端口：** 9000（默认绑定 127.0.0.1，需 Nginx 反代）

**双版本分发：**
- `version-beta.json` — Beta 版元数据
- `version-release.json` — 正式版元数据
- `app-beta.apk` / `app-release.apk` — 对应安装包

**HTTP 端点：**

| 端点 | 说明 |
|------|------|
| `/version-beta.json` | Beta 版版本信息 |
| `/version-release.json` | 正式版版本信息 |
| `/version.json` | 兼容旧版，根据 `acceptBeta` 参数返回 |
| `/api/update/check` | Legacy 更新检查 API |
| `/*.apk` | APK 文件下载 |
| `/health` | 健康检查 |

### 5.3 反馈收集服务

**文件：** [vps/var_www_update/feedback/feedback_server.py](file:///d:/kaifa/GameCenterApp/vps/var_www_update/feedback/feedback_server.py)

**技术栈：** Python 3 + SQLite + `http.server.ThreadingHTTPServer`

**端口：** 9011（默认绑定 127.0.0.1，需 Nginx 反代）

**数据存储：**
- SQLite 数据库（`feedback.sqlite`）
- JSON 文件（按类型分目录）
- 文本文件镜像（同步到 app/反馈/ 目录）

**HTTP 端点：**

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/feedback` | POST | 提交反馈 |
| `/admin/feedback?token=xxx` | GET | 管理页面（HTML） |
| `/health` | GET | 健康检查 |

---

## 6. 依赖关系图

### Gradle 依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| `androidx.appcompat:appcompat` | 1.7.0 | 兼容性支持库 |
| `com.google.android.material:material` | 1.12.0 | Material Design 组件 |
| `androidx.constraintlayout:constraintlayout` | 2.2.0 | 约束布局 |
| `androidx.navigation:navigation-fragment` | 2.8.4 | Navigation Component |
| `androidx.navigation:navigation-ui` | 2.8.4 | Navigation UI |
| `androidx.recyclerview:recyclerview` | 1.3.2 | RecyclerView |
| `androidx.cardview:cardview` | 1.0.0 | CardView |
| `androidx.webkit:webkit` | 1.12.1 | WebView 支持 |
| `com.google.zxing:core` | 3.5.3 | 二维码编解码 |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP/WebSocket 客户端 |

### 模块间依赖关系

```
App ──→ SettingsManager ──→ SharedPreferences
  │
  ├──→ ColorSchemeManager ──→ Activity UI
  │
  └──→ UpdateManager ──→ SSLHelper
                    ──→ UpdateInfo
                    ──→ BuildConfig (SERVER_URL, RELAY_URL, etc.)

MainActivity ──→ Navigation Component
           ──→ UpdateManager
           ──→ SettingsManager

GamesFragment ──→ GameRegistry ──→ 各游戏 Activity
             ──→ GameUsageStore
             ──→ SettingsManager / ColorSchemeManager
             ──→ AppSettingsDialog

BrowserFragment ──→ WebView
               ──→ SharedPreferences (history, bookmarks, settings)

ToolsFragment ──→ ToolSectionStore
              ├──→ ToolBinder 注册表 (Map<String, ToolBinder>)
              │       ├──→ IpToolBinder / DnsToolBinder / WifiToolBinder / ...
              │       └──→ ToolHelper (共享辅助方法)
              └──→ AdvancedToolBinders (高级工具)

联机游戏 Activity ──→ GameSocketServer / GameSocketClient
                  ──→ LANManager / RelayHttpClient / RemoteP2PUtil
                  ──→ SaveManager

各游戏 Activity ──→ SaveManager
                ──→ GameUsageStore
```

---

## 7. 构建与运行

### 环境要求

- Android Studio (Iguana 或更高)
- JDK 17
- Android SDK：compileSdk 35, minSdk 24
- Gradle 8.x

### 配置文件

**`local.properties`**（不纳入版本控制）：

```properties
server.url=https://your-hk-server.example.com
server.url.fallback=https://your-us-server.example.com
relay.url=https://your-server.example.com/api/ddz-relay
ws.url=wss://your-server.example.com/ddz-ws
feedback.url=https://your-server.example.com/api/feedback
```

**`version.properties`**：

```properties
versionCode=1
versionName=1.0
lastStableVersionCode=0
lastStableVersionName=
betaNoticeVersionGap=3
```

### 构建命令

```bash
# 本地调试编译
.\gradlew.bat :app:assembleDebug

# 编译并上传 Beta 到 VPS
.\gradlew.bat :app:buildAndUploadDebugToVps

# 编译并发布正式版（VPS + GitHub Releases）
.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable

# 手动递增版本号
.\gradlew.bat :app:bumpVersion
```

### VPS 部署

```bash
# 更新服务
cd /var/www/update
python3 update_server.py

# 反馈服务
cd /var/www/update/feedback
python3 feedback_server.py

# WebSocket 中继
cd /path/to/ddz_ws_relay
node server.js
```

建议使用 `systemd` 或 `pm2` 管理进程，Nginx 反代到对应端口。

---

## 8. 关键数据流

### 8.1 游戏启动流程

```
用户点击游戏卡片
  → GamesFragment.GameAdapter.onBindViewHolder()
    → usageStore.recordLaunch(gameId)
    → startActivity(new Intent(activity, entry.activityClass))
      → 游戏Activity.onCreate()
        → SaveManager.load() 检查存档
        → 有存档 → 弹窗询问是否恢复
        → 无存档 → 初始化新游戏
```

### 8.2 联机对战流程（WebSocket 模式）

```
房主:
  GameSocketServer.startWebSocket(wsUrl)
    → doWebSocketConnect()
      → OkHttpClient WebSocket 连接到中继服务器
      → 收到 WELCOME → 开始心跳

客户端:
  GameSocketClient.connectWebSocket(wsUrl)
    → doWebSocketConnect()
      → OkHttpClient WebSocket 连接到中继服务器
      → 收到 WELCOME → sendJoin(playerName)
      → 房主收到 JOIN 消息

游戏消息:
  客户端 → client.send(json) → WebSocket → 中继服务器 → 房主
  房主   → server.broadcast(json) → WebSocket → 中继服务器 → 所有客户端
```

### 8.3 更新检查流程

```
MainActivity.scheduleAutoUpdateCheck()
  → App.shouldAutoCheckUpdate() (仅首次)
    → UpdateManager.checkUpdate()
      → buildUpdateUrls() (按优先级构建 URL 列表)
      → 逐源请求 version-beta.json / version-release.json
      → 解析为 UpdateInfo
      → applyUpdatePolicy() (Beta/Release 策略)
      → 回调 onResult(UpdateInfo)
        → hasUpdate=true → showUpdateDialog()
        → hasUpdate=true + autoDownload=true → startAutoDownload()
        → betaUpdateOutdated=true → showBetaOnlyNoticeDialog()
```

### 8.4 反馈提交流程

```
用户点击反馈按钮
  → GamesFragment.showFeedbackDialog()
    → 选择邮件客户端 / 在线提交
    → 在线提交:
      → buildFeedbackPayload() (包含版本、设备、诊断信息)
      → submitFeedbackToVps()
        → POST JSON 到 BuildConfig.FEEDBACK_URL
        → 成功 → Toast "反馈已提交"
        → 失败 → Toast "提交到 VPS 失败，可使用下方邮箱兜底"
```

---

## 9. 扩展指南

### 添加新游戏

1. 在 `app/src/main/java/com/gamecenter/app/games/` 下创建游戏子包
2. 实现 `XxxActivity.java`（继承 `AppCompatActivity`）
3. 可选：实现 `XxxGame.java`（游戏逻辑）和 `XxxView.java`（自定义 View）
4. 在 `GameRegistry.getCategories()` 中添加 `Entry`
5. 在 `AndroidManifest.xml` 中注册 Activity
6. 添加对应的图标资源和字符串资源

### 添加联机模式

1. 创建 `XxxOnlineActivity.java`
2. 房主端：初始化 `GameSocketServer`（选择 LAN/Relay/WebSocket 模式）
3. 客户端：初始化 `GameSocketClient`（连接到房主）
4. 定义游戏协议（JSON 消息格式）
5. 在 `GameRegistry` 中将联机 Activity 注册到对应 Entry

### 添加新工具

1. 创建工具布局 XML（如 `content_xxx.xml`）
2. 在 `ToolSectionStore.loadSections()` 中添加 `ToolSection`
3. 在 `ToolsFragment.SectionViewHolder.bindContent()` 的 switch 中添加绑定逻辑
4. 复杂工具可拆分到 `AdvancedToolBinders` 或独立 Binder 类

### 添加新配色方案

1. 在 `ColorSchemeManager` 的 `SCHEMES` 静态列表中添加 `Scheme` 对象
2. 定义完整的 26 个色值（亮色 + 暗色方案）
3. 更新 `SCHEME_INDEX_*` 常量

---

> 本文档基于项目源码自动分析生成，如需更新请重新运行分析。
