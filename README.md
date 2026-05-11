# GameCenter App

[![Android](https://img.shields.io/badge/Android-API%2024%2B-green?logo=android)](https://developer.android.com/)
[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

一个集成 **25+** 款经典小游戏的 Android 游戏中心，支持单机 AI、局域网联机和云联机对战，内置浏览器和 20+ 网络/设备工具。

An Android game center integrating **25+** classic mini-games, supporting single-player AI, LAN multiplayer, cloud multiplayer, built-in browser, and 20+ network/device tools.

---

## 快速导航 / Quick Navigation

- 🎮 [功能列表](#功能列表--feature-list) — 全部游戏列表与联机支持
- 🏗 [技术架构](#技术架构--tech-stack) — 开发环境与依赖
- 🌐 [更新分发架构](#更新分发架构--update-distribution-architecture) — 三级下载源 + 自动换源
- 🕹 [联机架构](#联机架构--multiplayer-architecture) — 多游戏云联机支持
- 📁 [目录结构](#目录结构--directory-structure) — 项目文件组织
- 🛠 [构建与部署](#构建与部署--build--deployment) — 编译、打包、发布
- 📋 [更新日志](#更新日志--changelog) — 版本历史记录

---

## 功能列表 / Feature List

### 🎲 经典游戏 / Classics

| 游戏 | 单机 AI | 局域网 | 云联机 |
|------|:-------:|:------:|:------:|
| 五子棋 Gomoku | ✅ | ❌ | ✅ WebSocket |
| 围棋 Go | ✅ | ❌ | ✅ WebSocket |
| 中国象棋 Chinese Chess | ✅ | ❌ | ✅ WebSocket |
| 贪吃蛇 Snake | ✅ | ❌ | ❌ |
| 俄罗斯方块 Tetris | ✅ | ❌ | ❌ |
| 斗地主 DouDiZhu | ✅ | ✅ | ✅ WebSocket |
| Brotato | ✅ | ❌ | ❌ |

### 🧩 益智游戏 / Puzzle

| 游戏 | 单机 AI | 局域网 | 云联机 |
|------|:-------:|:------:|:------:|
| 2048 | ✅ | ❌ | ❌ |
| 数独 Sudoku | ✅ | ❌ | ❌ |
| 推箱子 Sokoban | ✅ | ❌ | ❌ |
| 接水管 Pipeline | ✅ | ❌ | ❌ |
| 华容道 Klotski | ✅ | ❌ | ❌ |

### 🎯 休闲游戏 / Casual

| 游戏 | 单机 AI | 局域网 | 云联机 |
|------|:-------:|:------:|:------:|
| 打砖块 Breakout | ✅ | ❌ | ❌ |
| 打地鼠 Whack | ✅ | ❌ | ❌ |
| 连连看 Match | ✅ | ❌ | ❌ |
| 21点 Blackjack | ✅ | ❌ | ❌ |
| 跳棋 Checkers | ✅ | ❌ | ❌ |

### ⚡ 反应游戏 / Reaction

| 游戏 | 单机 AI | 局域网 | 云联机 |
|------|:-------:|:------:|:------:|
| Flappy Bird | ✅ | ❌ | ❌ |
| 拼图 Tiles | ✅ | ❌ | ❌ |
| 飞机大战 Plane | ✅ | ❌ | ❌ |
| **石头剪刀布 Rock-Paper-Scissors** | ✅ | ❌ | **✅ WebSocket** |
| 反应测试 Reaction | ✅ | ❌ | ❌ |

### 🃏 其他游戏 / Others

| 游戏 | 单机 AI | 局域网 | 云联机 |
|------|:-------:|:------:|:------:|
| 井字棋 Tic-Tac-Toe | ✅ | ❌ | ❌ |
| 记忆翻牌 Memory | ✅ | ❌ | ❌ |
| 猜数字 Guess | ✅ | ❌ | ❌ |
| 掷骰子 Dice | ✅ | ❌ | ❌ |

> **联机说明 / Multiplayer Note**：**斗地主、五子棋、围棋、中国象棋、石头剪刀布** 均支持 WebSocket 云联机对战，联机游戏支持内联聊天功能。其余游戏均为单机模式。

### 🛠 工具箱 / Tools

20+ 实用工具，包括：
- 网络体检、DNS 查询、局域网设备扫描、端口扫描
- 二维码生成与识别（支持 WiFi/名片/图片）
- 编码/解码（URL/Base64）、JSON 格式化、时间戳转换
- 文件哈希计算（MD5/SHA-1/SHA-256）
- 颜色取色器（支持 WCAG 对比度检测）
- 诊断报告导出、电池信息、设备信息

---

## 技术架构 / Tech Stack

### 开发环境 / Development Environment

| 项目 | 版本 |
|------|------|
| 开发语言 | Java 17 |
| 最低 Android 版本 | API 24 (Android 7.0) |
| 目标 SDK | API 35 (Android 15) |
| 编译 SDK | API 35 |
| Gradle 插件 | 8.x |

### 主要依赖 / Dependencies

| 库 | 版本 | 用途 |
|----|------|------|
| androidx.appcompat | 1.7.0 | AppCompat 基础支持 |
| com.google.android.material | 1.12.0 | Material Design 组件 |
| androidx.constraintlayout | 2.2.0 | ConstraintLayout 布局 |
| androidx.recyclerview | 1.3.2 | 游戏列表 RecyclerView |
| androidx.cardview | 1.0.0 | 游戏卡片 CardView |
| androidx.webkit | 1.12.1 | WebView 增强 |
| com.google.zxing:core | 3.5.3 | 二维码生成与识别 |
| com.squareup.okhttp3:okhttp | 4.12.0 | WebSocket 客户端 |
| com.github.bumptech.glide:glide | 4.16.0 | 图片懒加载与缓存 |
| junit:junit | 4.13.2 | 单元测试 |

---

## 更新分发架构 / Update Distribution Architecture

### 三级下载源 / Three-Level Download Sources

App 下载更新时自动尝试以下下载源，优先级从高到低：

```
┌─────────┐  优先级 1  ┌──────────────────┐
│   App   │ ─────────► │  香港 VPS         │
│         │            │  hk-update       │
│         │  优先级 2  ┌┴─────────────────┐│
│         │ ─────────► │  GitHub Releases ││
│         │            │  (全球 CDN)       ││
│         │  优先级 3  └┬─────────────────┘│
│         │ ─────────► ┌┴─────────────────┐│
│         │            │  美国 VPS         ││
│         │            │  (仅备用更新源)    ││
└─────────┘            └──────────────────┘┘
```

### VPS 职责划分 / VPS Responsibility

| VPS | 主要职责 | 说明 |
|-----|----------|------|
| **香港 VPS** | 更新服务 + 游戏联机 | 主更新源、WebSocket Relay、HTTP Relay、反馈服务 |
| **美国 VPS** | 仅备用更新源 | 仅作为更新下载备用源，不承担游戏联机任务 |

### 自动换源机制 / Auto-Switch Mechanism

- **速度检测**：下载开始后 3 秒检测下载速度
- **换源阈值**：低于 50 KB/s 自动切换到下一个下载源
- **无缝切换**：切换时自动删除不完整的临时文件

### 版本分发策略 / Version Distribution Strategy

| 版本类型 | 上传目标 | 说明 |
|----------|----------|------|
| **Beta 测试版** | 香港 VPS 更新服务器 | 仅供开启"接受测试版"的用户下载 |
| **Stable 正式版** | 香港 VPS + GitHub Releases | 所有用户均可下载 |

---

## 性能优化 / Performance Optimization

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

---

## 测试覆盖 / Test Coverage

### 单元测试统计 / Unit Test Statistics

| 游戏 | 测试文件 | 测试用例数 | 覆盖内容 |
|------|----------|-----------|----------|
| 五子棋 | GomokuGameTest | 12 | 初始状态、落子、横竖斜胜利、重置 |
| 围棋 | GoGameTest | 12 | 初始状态、落子、提子、跳过、重置 |
| 华容道 | KlotskiGameTest | 3 | 初始棋盘、提示系统、解题路径 |
| 井字棋 | TicGameTest | 9 | 初始状态、落子、AI对战、胜负判定 |
| 2048 | Game2048GameTest | 10 | 初始状态、四方向移动、合并计分 |
| 贪吃蛇 | SnakeGameTest | 10 | 初始状态、方向控制、移动、撞墙判定 |
| 记忆翻牌 | MemoryGameTest | 11 | 初始状态、翻牌、配对、全部配对判定 |
| 中国象棋 | ChineseChessGameTest | 10 | 初始棋盘、棋子走法、悔棋、深拷贝 |
| 猜数字 | GuessGameTest | 9 | 初始状态、猜测判定、难度切换 |
| 掷骰子 | DiceGameTest | 10 | 初始状态、骰子类型判定、豹子顺子对子 |
| **总计** | **10 个测试文件** | **96 个测试用例** | |

### 运行测试 / Run Tests

```bash
# 运行所有单元测试
.\gradlew.bat :app:test

# 运行特定游戏测试
.\gradlew.bat :app:testDebugUnitTest --tests "com.gamecenter.app.games.gomoku.GomokuGameTest"
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

所有联机游戏共享 `com.gamecenter.app.network` 包中的网络基础设施：

| 模块 | 用途 |
|------|------|
| `RelayHttpClient` | HTTP Relay 通信 + WebSocket URL 生成 |
| `GameSocketServer` | 房主权威服务器（WebSocket 模式） |
| `GameSocketClient` | 客户端连接管理（WebSocket 模式） |
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
GameCenterApp/
├── app/
│   ├── build.gradle                          # 构建配置（版本管理、上传任务）
│   └── src/main/
│       ├── AndroidManifest.xml               # 应用清单
│       ├── assets/
│       │   └── version.json                  # 内置版本信息（自动生成）
│       ├── java/com/gamecenter/app/
│       │   ├── App.java                      # 应用入口，全局初始化
│       │   ├── MainActivity.java             # 主界面（底部导航 + 更新检查）
│       │   ├── SettingsManager.java          # 设置管理（SharedPreferences）
│       │   ├── ColorSchemeManager.java       # 主题配色管理
│       │   ├── fragments/                    # 三个主页面 Fragment
│       │   │   ├── GamesFragment.java        # 游戏大厅（搜索/收藏/最近）
│       │   │   ├── ToolsFragment.java        # 工具箱（20+ 工具）
│       │   │   └── BrowserFragment.java      # 内置浏览器
│       │   ├── network/                      # 🆕 公共网络模块
│       │   │   ├── RelayHttpClient.java      # HTTP Relay + WebSocket URL
│       │   │   ├── GameSocketServer.java     # 房主权威服务器
│       │   │   ├── GameSocketClient.java     # 客户端连接管理
│       │   │   ├── LANManager.java           # 局域网服务发现
│       │   │   └── RemoteP2PUtil.java        # 房间码工具类
│       │   ├── games/                        # 游戏模块
│       │   │   ├── GameRegistry.java         # 游戏注册中心
│       │   │   ├── GameUsageStore.java       # 使用记录存储
│       │   │   ├── GameTutorialHelper.java   # 教程弹窗管理
│       │   │   ├── doudizhu/                 # 斗地主旧版（局域网 P2P）
│       │   │   ├── doudizhu/                 # 斗地主（三模联机）
│       │   │   ├── rock/                     # 石头剪刀布 + RockOnlineActivity
│       │   │   ├── gomoku/                   # 五子棋 + GomokuOnlineActivity
│       │   │   ├── chinesechess/             # 中国象棋 + ChineseChessOnlineActivity
│       │   │   └── go/                       # 围棋 + GoOnlineActivity
│       │   ├── tools/                        # 工具箱实现
│       │   │   ├── ToolSectionStore.java     # 工具分类与排序
│       │   │   ├── AdvancedToolBinders.java  # 高级工具绑定
│       │   │   ├── HashToolBinder.java       # 哈希计算
│       │   │   ├── ColorPickerToolBinder.java# 颜色取色器
│       │   │   └── ClipboardToolBinder.java  # 剪贴板工具
│       │   ├── update/                       # 应用更新模块
│       │   │   ├── UpdateManager.java        # 更新检查与下载（三级下载源）
│       │   │   ├── UpdateInfo.java           # 版本信息数据模型
│       │   │   └── SSLHelper.java            # SSL 证书信任
│       │   ├── utils/                        # 通用工具
│       │   ├── views/                        # 自定义 View
│       │   └── settings/                     # 设置弹窗
│       ├── res/                              # 资源文件
│       │   ├── layout/                       # 布局文件
│       │   ├── drawable/                     # 图标与形状
│       │   ├── values/                       # 字符串、颜色、主题
│       │   ├── raw/                          # 音效资源（斗地主 70+ 音频）
│       │   └── xml/                          # 配置文件
│       └── ...
├── tools/
│   ├── upload_to_vps.py                      # 上传 APK 到 VPS
│   └── upload_to_github_release.py           # 上传 APK 到 GitHub Releases
├── vps/                                      # VPS 部署模板
│   ├── var_www_update/                       # 更新服务模板
│   │   ├── update_server.py                  # 更新服务
│   │   ├── feedback/                         # 反馈服务模板
│   │   └── ddz_relay/                        # Relay 服务模板
│   └── ddz_ws_relay/                         # WebSocket Relay 服务
├── gradle/wrapper/                           # Gradle Wrapper
├── .gitignore                                # Git 忽略规则
├── local.properties.template                 # 本地配置模板
├── README.md                                 # 本文件
├── CHANGELOG.md                              # 版本更新日志
└── PROJECT_CONTEXT.md                        # 项目上下文文档
```

---

## 构建与部署 / Build & Deployment

### 首次构建 / First Build

```bash
# 1. 克隆项目 / Clone the repository
git clone https://github.com/3571949306/GameCenterApp.git
cd GameCenterApp

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

# ============ 备用 VPS（可选）============
# 备用更新源地址 / Fallback update server URL
server.url.fallback=https://<YOUR_FALLBACK_DOMAIN>

# 反馈服务器地址（可选）/ Feedback server URL (optional)
feedback.url=https://<YOUR_DOMAIN>/api/feedback
```

| 配置项 | 用途 | 缺少后果 |
|--------|------|----------|
| `server.url` | 应用更新检查 | 无法获取新版本 |
| `ws.url` | WebSocket 云联机 | 无法使用 WebSocket 联机 |
| `relay.url` | HTTP Relay 云联机 | 只能局域网对战 |
| `server.url.fallback` | 备用更新源 | 无法回退到备用源 |
| `feedback.url` | 用户反馈提交 | 反馈功能不可用 |

> **注意 / Note**：修改 `local.properties` 后必须执行 **Build → Clean Project → Rebuild Project**，否则 `BuildConfig` 不会更新。

### 发布流程 / Release Workflow

| 场景 | 命令 | 说明 |
|------|------|------|
| 日常编译 | `.\gradlew.bat :app:assembleDebug` | 仅编译，versionCode 自动递增 |
| Beta 发布 | `.\gradlew.bat :app:buildAndUploadDebugToVps` | 上传到 VPS，仅测试版用户可用 |
| 正式发布 | `.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable` | 同时上传到 VPS 和 GitHub Releases |

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
| v24 (1.3.10 beta) | 2026-05-10 | Beta | 4个游戏新增云联机、公共网络模块抽取 |
| v23 (1.3.9 beta) | 2026-05-10 | Beta | 修复 beta 用户检查更新问题 |
| v21 (1.3.8 beta) | 2026-05-10 | Beta | 斗地主 Beta 云联机状态同步修复、主 VPS 架构部署、三级下载源 |
| v18 (1.3.8) | 2026-05-09 | Stable | 华容道重做、斗地主 Beta 远程 P2P |
| v15 (1.3.7) | 2026-05-09 | Stable | 设置拆分、配色方案优化、更新下载策略 |

---

## License

MIT License

Copyright (c) 2024-2026 GameCenter App Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
