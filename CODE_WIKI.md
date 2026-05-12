# GameCenterApp Code Wiki

> 本文档为 GameCenterApp 项目的完整代码百科，涵盖项目架构、模块职责、关键类与函数说明、依赖关系及运行方式等核心信息。

---

## 目录

1. [项目概述](#1-项目概述)
2. [项目架构](#2-项目架构)
3. [核心模块详解](#3-核心模块详解)
4. [关键类与函数说明](#4-关键类与函数说明)
5. [依赖关系](#5-依赖关系)
6. [项目运行方式](#6-项目运行方式)
7. [联机架构说明](#7-联机架构说明)
8. [测试说明](#8-测试说明)

---

## 1. 项目概述

### 1.1 项目简介

**GameCenterApp** 是一款 Android 游戏中心应用，集成了 30+ 款经典小游戏和实用工具箱。支持单机游戏、局域网联机、云端联机等多种游戏模式。

### 1.2 版本信息

| 属性 | 值 |
|------|-----|
| 应用包名 | `com.gamecenter.app` |
| 当前版本 | v1.3.18 (versionCode: 224) |
| 最低 SDK | Android 7.0 (API 24) |
| 目标 SDK | Android 15 (API 35) |
| 编译 SDK | Android 15 (API 35) |
| 开发语言 | Java 17 |

### 1.3 主要功能

- **游戏大厅**: 30+ 款经典小游戏，分类展示、搜索、收藏
- **工具箱**: 25+ 款实用工具（网络诊断、二维码、颜色取色器等）
- **联机对战**: 支持局域网和云端 WebSocket 联机
- **自动更新**: 多源更新（香港VPS → 美国VPS → GitHub Releases）
- **主题定制**: 明暗主题切换、多种配色方案

---

## 2. 项目架构

### 2.1 目录结构

```
GameCenterApp/
├── app/                          # 主应用模块
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/gamecenter/app/
│   │   │   │   ├── App.java                 # Application 入口
│   │   │   │   ├── MainActivity.java        # 主 Activity
│   │   │   │   ├── fragments/               # Fragment 页面
│   │   │   │   │   ├── GamesFragment.java   # 游戏大厅
│   │   │   │   │   ├── ToolsFragment.java   # 工具箱
│   │   │   │   │   └── BrowserFragment.java # 浏览器
│   │   │   │   ├── games/                   # 游戏模块
│   │   │   │   │   ├── BaseGameActivity.java
│   │   │   │   │   ├── GameRegistry.java
│   │   │   │   │   ├── gomoku/              # 五子棋
│   │   │   │   │   ├── go/                  # 围棋
│   │   │   │   │   ├── chinesechess/        # 中国象棋
│   │   │   │   │   ├── doudizhu/            # 斗地主
│   │   │   │   │   ├── snake/               # 贪吃蛇
│   │   │   │   │   ├── tetris/              # 俄罗斯方块
│   │   │   │   │   └── ...                  # 其他游戏
│   │   │   │   ├── network/                 # 网络通信
│   │   │   │   │   ├── BaseOnlineActivity.java
│   │   │   │   │   ├── GameSocketClient.java
│   │   │   │   │   ├── GameSocketServer.java
│   │   │   │   │   ├── LANManager.java
│   │   │   │   │   └── RelayHttpClient.java
│   │   │   │   ├── tools/                   # 工具模块
│   │   │   │   │   ├── ToolBinder.java
│   │   │   │   │   ├── ToolSectionStore.java
│   │   │   │   │   └── *ToolBinder.java     # 各工具实现
│   │   │   │   ├── update/                  # 更新模块
│   │   │   │   │   ├── UpdateManager.java
│   │   │   │   │   └── UpdateInfo.java
│   │   │   │   ├── utils/                   # 工具类
│   │   │   │   ├── settings/                # 设置模块
│   │   │   │   └── views/                   # 自定义视图
│   │   │   ├── res/                         # 资源文件
│   │   │   └── AndroidManifest.xml
│   │   └── test/                            # 单元测试
│   └── build.gradle
├── docs/                         # 文档目录
├── tools/                        # 发布脚本
├── vps/                          # VPS 服务端代码
├── build.gradle                  # 根构建配置
├── settings.gradle               # 项目设置
└── version.properties            # 版本配置
```

### 2.2 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        GameCenterApp                            │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │   App.java  │  │MainActivity │  │   Fragment Navigation   │  │
│  │  (入口)     │→ │  (主导航)   │→ │                         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                         核心模块层                              │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────────┐   │
│  │  Games    │ │  Tools    │ │  Network  │ │    Update     │   │
│  │  Module   │ │  Module   │ │  Module   │ │    Module     │   │
│  └───────────┘ └───────────┘ └───────────┘ └───────────────┘   │
├─────────────────────────────────────────────────────────────────┤
│                         基础设施层                              │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌───────────────┐   │
│  │Settings   │ │SaveManager│ │SoundManager│ │ColorScheme   │   │
│  │Manager    │ │           │ │           │ │Manager       │   │
│  └───────────┘ └───────────┘ └───────────┘ └───────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 核心模块详解

### 3.1 应用入口模块

#### App.java
应用入口类，继承自 `Application`。

**职责**:
- 初始化应用主题（明/暗模式）
- 注册 Activity 生命周期回调
- 应用配色方案
- 管理自动更新检查状态

**关键方法**:
| 方法 | 说明 |
|------|------|
| `onCreate()` | 应用启动初始化 |
| `applyTheme()` | 根据设置应用主题 |
| `applyColorScheme(Activity)` | 为 Activity 应用配色方案 |
| `shouldAutoCheckUpdate()` | 判断是否应自动检查更新 |

#### MainActivity.java
主 Activity，作为应用入口界面。

**职责**:
- 初始化底部导航栏
- 管理 Fragment 导航
- 处理应用更新检查和下载
- 管理权限请求

**关键方法**:
| 方法 | 说明 |
|------|------|
| `onCreate(Bundle)` | 初始化导航和权限 |
| `checkUpdate(boolean)` | 检查应用更新 |
| `startDownload(UpdateInfo)` | 开始下载更新包 |
| `installApk(File)` | 安装 APK |

---

### 3.2 游戏模块 (games/)

#### BaseGameActivity.java
所有游戏 Activity 的基类。

**职责**:
- 提供音效播放功能
- 提供震动反馈功能
- 提供动画播放功能
- 管理游戏设置（音效、震动开关）

**关键方法**:
```java
protected void playClickSound()           // 播放点击音效
protected void vibrateShort()             // 短震动
protected void vibrateLong()              // 长震动
protected void animateView(View, int)     // 播放动画
protected void playWinAnimation(View)     // 播放胜利动画
```

#### GameRegistry.java
游戏注册中心，管理所有游戏的元数据。

**职责**:
- 定义游戏分类（经典、益智、休闲、反应、其他）
- 注册所有游戏及其 Activity 映射
- 提供游戏列表查询接口

**数据结构**:
```java
public static final class Category {
    public final String name;           // 分类名称
    public final List<Entry> games;     // 游戏列表
}

public static final class Entry {
    public final String id;             // 游戏 ID
    public final int iconRes;           // 图标资源
    public final String name;           // 游戏名称
    public final String desc;           // 游戏描述
    public final Class<?> activityClass; // Activity 类
    public final String category;       // 所属分类
}
```

**游戏列表**:
| 分类 | 游戏 |
|------|------|
| 经典 | 五子棋、围棋、中国象棋、贪吃蛇、俄罗斯方块、斗地主、Brotato |
| 益智 | 2048、数独、推箱子、接水管、华容道 |
| 休闲 | 打砖块、打地鼠、消消乐、21点、跳棋 |
| 反应 | Flappy Bird、Tiles、飞机大战、石头剪刀布、反应测试 |
| 其他 | 井字棋、记忆翻牌、猜数字、骰子 |

#### GamesFragment.java
游戏大厅 Fragment。

**职责**:
- 展示游戏分类 Tab
- 游戏卡片列表展示
- 搜索过滤功能
- 最近游玩和收藏管理
- 设置对话框入口

**关键功能**:
- Tab 切换：全部、最近、收藏、各分类
- 搜索：按名称、描述、分类过滤
- 卡片点击：启动对应游戏 Activity

---

### 3.3 工具模块 (tools/)

#### ToolBinder.java
工具绑定器接口。

```java
public interface ToolBinder {
    void bind(Context context, View contentView, ExecutorService executor);
}
```

**设计模式**: 每个工具实现此接口，将 UI 和业务逻辑绑定到视图。

#### ToolSectionStore.java
工具配置存储。

**职责**:
- 加载工具列表配置
- 保存工具排序
- 保存工具可见性
- 管理收藏和最近使用

**工具列表**:
| 工具 ID | 名称 |
|---------|------|
| network_diagnosis | 一键网络体检 |
| diagnostic_report | 诊断报告导出 |
| dns_lookup | DNS 查询 |
| lan_scan | 局域网设备扫描 |
| text_codec | 编码/时间戳/JSON |
| file_hash | 文件哈希 |
| qr_plus | 二维码增强 |
| color_plus | 颜色增强 |
| permission_privacy | 权限与隐私说明 |
| ip | IP 地址信息 |
| dns | DNS 服务器 |
| wifi | WiFi 信号 |
| speedtest | 网络测速 |
| portscan | 端口扫描 |
| qr | 二维码工具 |
| battery | 电池信息 |
| device | 设备信息 |
| ping | Ping 工具 |
| traceroute | 路由追踪 |
| subnet | 子网计算器 |
| screen | 屏幕信息 |
| sensor | 传感器信息 |
| hash | 哈希计算器 |
| clipboard | 剪贴板工具 |
| color | 颜色取色器 |
| sysinfo | 手机系统详细信息 |

#### ToolsFragment.java
工具箱 Fragment。

**职责**:
- 展示工具卡片列表
- 支持拖拽排序
- 支持单列/双列布局切换
- 工具收藏功能

---

### 3.4 网络模块 (network/)

#### BaseOnlineActivity.java
联机游戏基类。

**职责**:
- 封装房间管理逻辑
- 封装聊天功能
- 封装连接状态管理
- 提供游戏消息收发接口

**抽象方法** (子类必须实现):
```java
protected abstract String getP2pPrefsName();
protected abstract String getGameName();
protected abstract void initGameViews(LinearLayout gameContent);
protected abstract void onGameStarted();
protected abstract void onGameMessageReceived(JSONObject message);
protected abstract void onGameReset();
```

**房间管理流程**:
1. 创建房间 → 生成房间码 → 启动 WebSocket 服务器 → 等待对手
2. 加入房间 → 输入房间码 → 连接 WebSocket → 开始游戏

#### GameSocketClient.java
游戏 Socket 客户端。

**职责**:
- 支持 TCP Socket 连接
- 支持 WebSocket 连接
- 支持 HTTP Relay 轮询
- 心跳保活
- 断线重连

**连接状态**:
```java
public enum ConnectionState {
    DISCONNECTED,   // 已断开
    CONNECTING,     // 连接中
    CONNECTED,      // 已连接
    AUTHENTICATED,  // 已认证
    RECONNECTING    // 重连中
}
```

**关键方法**:
| 方法 | 说明 |
|------|------|
| `connectWebSocket(String)` | 连接 WebSocket |
| `connectRelay(String, String)` | 连接 HTTP Relay |
| `send(JSONObject)` | 发送消息 |
| `disconnect()` | 断开连接 |
| `reconnectNow()` | 立即重连 |

#### LANManager.java
局域网发现管理器。

**职责**:
- UDP 广播发现局域网内主机
- 维护已发现主机列表
- 超时检测

**协议**:
- 端口: 9877
- 广播间隔: 3000ms
- 超时时间: 8000ms

#### RelayHttpClient.java
HTTP Relay 通信工具类。

**职责**:
- 生成 WebSocket URL
- HTTP POST 请求封装
- URL 编码

---

### 3.5 更新模块 (update/)

#### UpdateManager.java
应用更新管理器。

**职责**:
- 多源更新检查（香港VPS → 美国VPS → GitHub Releases）
- APK 下载（支持断点续传、速度检测、自动换源）
- MD5 校验
- 安装 APK

**更新源优先级**:
1. 香港 VPS (主)
2. 美国 VPS (备用1)
3. GitHub Releases (备用2)

**关键方法**:
| 方法 | 说明 |
|------|------|
| `checkUpdate(Context, Callback)` | 检查更新 |
| `downloadApk(Context, UpdateInfo, Callback)` | 下载 APK |
| `installApk(Context, File)` | 安装 APK |
| `cancel()` | 取消操作 |

**版本策略**:
- Beta 通道：仅上传到 VPS
- Stable 通道：上传到 VPS + GitHub Releases
- 用户可设置是否接受测试版

#### UpdateInfo.java
更新信息数据类。

**字段**:
| 字段 | 说明 |
|------|------|
| versionCode | 版本号 |
| versionName | 版本名 |
| channel | 发布通道 (beta/stable) |
| downloadUrl | 下载地址 |
| fileSize | 文件大小 |
| md5 | MD5 校验值 |
| changelog | 更新日志 |

---

### 3.6 设置模块 (settings/)

#### SettingsManager.java
设置管理器（单例）。

**职责**:
- 持久化用户偏好设置
- 提供设置读写接口

**设置项**:
| 设置 | 说明 | 默认值 |
|------|------|--------|
| themeMode | 主题模式 | THEME_SYSTEM |
| colorSchemeIndex | 配色方案索引 | 0 |
| autoCheckUpdate | 自动检查更新 | true |
| acceptBetaUpdate | 接受测试版 | false |
| autoDownloadUpdate | 自动下载更新 | false |
| soundEnabled | 音效开关 | true |
| vibrationEnabled | 震动开关 | true |
| updateSource | 更新源 | UPDATE_SOURCE_AUTO |

---

## 4. 关键类与函数说明

### 4.1 游戏逻辑示例 - 五子棋

#### GomokuGame.java
五子棋游戏逻辑类。

**常量**:
```java
public static final int BOARD_SIZE = 15;  // 棋盘大小
public static final int EMPTY = 0;        // 空位
public static final int BLACK = 1;        // 黑棋
public static final int WHITE = 2;        // 白棋
```

**关键方法**:
| 方法 | 说明 |
|------|------|
| `makeMove(int x, int y, int player)` | 落子 |
| `checkWinAt(int x, int y, int player)` | 检查获胜 |
| `undoLastMoves(int count)` | 悔棋 |
| `reset()` | 重置游戏 |

#### GomokuAI.java
五子棋 AI（Minimax + Alpha-Beta 剪枝）。

**AI 难度等级**:
| 等级 | 思考时间 |
|------|----------|
| 1 | 500ms |
| 2 | 1500ms |
| 3 | 3000ms |
| 4 | 5000ms |
| 5 | 7000ms |
| 6 | 10000ms |

**关键方法**:
| 方法 | 说明 |
|------|------|
| `getBestMove(GomokuGame, int)` | 获取最佳落子 |
| `minimax(...)` | Minimax 搜索 |
| `evaluateMoveThreat(...)` | 威胁评估 |
| `findImmediateWin(...)` | 寻找必胜点 |

---

### 4.2 配色方案管理

#### ColorSchemeManager.java
配色方案管理器。

**配色方案**:
- 默认蓝
- 森林绿
- 海洋青
- 玫瑰红
- 紫罗兰
- 橙色阳光
- 深空灰

**应用方式**:
```java
ColorSchemeManager.Scheme scheme = ColorSchemeManager.getScheme(index);
ColorSchemeManager.applyScheme(activity, scheme, isDarkMode);
```

---

### 4.3 音效管理

#### SoundManager.java
音效管理器。

**职责**:
- 加载和播放音效
- 播放背景音乐
- 音效开关控制

---

## 5. 依赖关系

### 5.1 Gradle 依赖

```groovy
dependencies {
    // AndroidX 核心库
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.2.0'
    
    // 导航组件
    implementation 'androidx.navigation:navigation-fragment:2.8.4'
    implementation 'androidx.navigation:navigation-ui:2.8.4'
    
    // 列表组件
    implementation 'androidx.recyclerview:recyclerview:1.3.2'
    implementation 'androidx.cardview:cardview:1.0.0'
    
    // App Startup
    implementation 'androidx.startup:startup-runtime:1.2.0'
    
    // 二维码
    implementation 'com.google.zxing:core:3.5.3'
    
    // HTTP 客户端
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    
    // 图片加载
    implementation 'com.github.bumptech.glide:glide:4.16.0'
    
    // 测试
    testImplementation 'junit:junit:4.13.2'
    debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.14'
}
```

### 5.2 模块依赖图

```
┌─────────────────────────────────────────────────────────────┐
│                        app 模块                             │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────┐   ┌─────────┐   ┌─────────┐   ┌─────────────┐  │
│  │ games/  │   │ tools/  │   │network/ │   │   update/   │  │
│  └────┬────┘   └────┬────┘   └────┬────┘   └──────┬──────┘  │
│       │             │             │               │         │
│       └─────────────┴─────────────┴───────────────┘         │
│                           │                                  │
│                    ┌──────┴──────┐                          │
│                    │  utils/     │                          │
│                    │ settings/   │                          │
│                    │  views/     │                          │
│                    └─────────────┘                          │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 外部服务依赖

| 服务 | 用途 | 配置 |
|------|------|------|
| 香港 VPS | 主更新源、联机中继 | `SERVER_URL` |
| 美国 VPS | 备用更新源 | `SERVER_URL_FALLBACK` |
| GitHub Releases | 备用更新源 | - |
| WebSocket 服务 | 云端联机 | `WS_URL` |
| 反馈服务 | 用户反馈收集 | `FEEDBACK_URL` |

---

## 6. 项目运行方式

### 6.1 环境要求

- JDK 17+
- Android SDK 35
- Android Gradle Plugin 8.7.3
- Android Studio (推荐)

### 6.2 本地开发

```bash
# 克隆项目
git clone <repository-url>
cd GameCenterApp

# 配置 local.properties
cp local.properties.template local.properties
# 编辑 local.properties，填入服务器 URL

# 编译 Debug 版本
./gradlew :app:assembleDebug

# 安装到设备
./gradlew :app:installDebug

# 运行单元测试
./gradlew :app:test
```

### 6.3 发布构建

```bash
# 编译 Release 版本
./gradlew :app:assembleRelease

# 发布 Beta 版本（仅上传 VPS）
./gradlew :app:buildAndUploadDebugToVps

# 发布 Stable 版本（上传 VPS + GitHub Releases）
./gradlew :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable
```

### 6.4 版本管理

版本信息存储在 `version.properties`:

```properties
versionCode=224           # 内部版本号
versionName=1.3.18        # 显示版本号
lastStableVersionCode=224 # 上一个正式版版本号
lastStableVersionName=1.3.18
betaNoticeVersionGap=3    # Beta 提示阈值
```

**版本号规则**:
- `versionCode`: 每次构建自动递增
- `versionName`: 手动更新，用于正式发布

### 6.5 签名配置

Release 签名配置在 `keystore.properties`:

```properties
STORE_FILE=keystore.jks
STORE_PASSWORD=***
KEY_ALIAS=***
KEY_PASSWORD=***
```

---

## 7. 联机架构说明

### 7.1 联机模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| 局域网 (LAN) | UDP 广播发现 + TCP 直连 | 同一 WiFi 下 |
| 云端 WebSocket | WebSocket 中继服务器 | 跨网络联机 |
| HTTP Relay | HTTP 轮询中继 | 网络受限环境 |

### 7.2 WebSocket 联机流程

```
┌─────────────┐                    ┌─────────────┐
│   主机端    │                    │   客户端    │
└──────┬──────┘                    └──────┬──────┘
       │                                  │
       │ 1. 创建房间                      │
       │    生成房间码                    │
       │                                  │
       │                                  │ 2. 输入房间码
       │                                  │    加入房间
       │                                  │
       │ ◄──────── WebSocket 连接 ────────┤
       │                                  │
       │ 3. 发送 JOIN                     │
       │ ────────────────────────────────►│
       │                                  │
       │ ◄────── WELCOME (clientId) ──────│
       │                                  │
       │ 4. 游戏开始                       │
       │ ◄─────── 游戏消息交换 ───────────►│
       │                                  │
```

### 7.3 服务端

WebSocket 中继服务端位于 `vps/ddz_ws_relay/`:

```
vps/ddz_ws_relay/
├── package.json
└── server.js          # Node.js WebSocket 服务器
```

---

## 8. 测试说明

### 8.1 单元测试

测试位于 `app/src/test/java/`:

| 测试类 | 测试内容 |
|--------|----------|
| ChineseChessGameTest | 中国象棋逻辑 |
| DiceGameTest | 骰子游戏逻辑 |
| Game2048GameTest | 2048 游戏逻辑 |
| GuessGameTest | 猜数字游戏逻辑 |
| KlotskiGameTest | 华容道逻辑 |
| MemoryGameTest | 记忆翻牌逻辑 |
| SnakeGameTest | 贪吃蛇逻辑 |
| TicGameTest | 井字棋逻辑 |
| GoGameTest | 围棋逻辑 |
| GomokuGameTest | 五子棋逻辑 |

### 8.2 运行测试

```bash
# 运行所有测试
./gradlew :app:test

# 运行单个测试类
./gradlew :app:test --tests "com.gamecenter.app.games.gomoku.GomokuGameTest"
```

---

## 附录

### A. 权限说明

| 权限 | 用途 |
|------|------|
| INTERNET | 网络访问 |
| ACCESS_NETWORK_STATE | 网络状态检测 |
| ACCESS_WIFI_STATE | WiFi 状态 |
| CHANGE_WIFI_MULTICAST_STATE | 局域网发现 |
| ACCESS_FINE_LOCATION | WiFi 扫描（Android 要求） |
| ACCESS_COARSE_LOCATION | 网络定位 |
| CAMERA | 二维码扫描 |
| READ_EXTERNAL_STORAGE | 读取文件 |
| WRITE_EXTERNAL_STORAGE | 保存文件 |
| REQUEST_INSTALL_PACKAGES | 安装 APK |

### B. 资源文件

| 目录 | 内容 |
|------|------|
| res/anim/ | 动画定义 |
| res/drawable/ | 图标和图形 |
| res/layout/ | 布局文件 |
| res/menu/ | 菜单定义 |
| res/navigation/ | 导航图 |
| res/raw/ | 音效文件 |
| res/values/ | 字符串、颜色、主题 |
| res/xml/ | 配置文件 |

### C. 相关文档

- [README.md](README.md) - 项目说明
- [CHANGELOG.md](CHANGELOG.md) - 更新日志
- [联机架构说明.md](联机架构说明.md) - 联机架构详细说明
- [PUBLISH_SYSTEM_OVERVIEW.md](PUBLISH_SYSTEM_OVERVIEW.md) - 发布系统说明
- [docs/](docs/) - 详细文档目录

---

*文档生成时间: 2026-05-12*
*项目版本: v1.3.18*
