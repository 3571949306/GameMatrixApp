# GameCenterApp Code Wiki

> Android 游戏中心应用完整技术文档 · 版本 1.3.26 · versionCode 249

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术栈与依赖](#2-技术栈与依赖)
3. [项目架构总览](#3-项目架构总览)
4. [目录结构](#4-目录结构)
5. [核心模块详解](#5-核心模块详解)
   - 5.1 [应用入口层](#51-应用入口层)
   - 5.2 [游戏模块](#52-游戏模块)
   - 5.3 [网络通信模块](#53-网络通信模块)
   - 5.4 [AI 模块](#54-ai-模块)
   - 5.5 [工具箱模块](#55-工具箱模块)
   - 5.6 [更新模块](#56-更新模块)
   - 5.7 [UI Fragment 层](#57-ui-fragment-层)
   - 5.8 [基础设施层](#58-基础设施层)
   - 5.9 [Kotlin 工具层](#59-kotlin-工具层)
6. [游戏模块详解](#6-游戏模块详解)
7. [联机架构详解](#7-联机架构详解)
8. [服务端组件](#8-服务端组件)
9. [模块间依赖关系](#9-模块间依赖关系)
10. [构建与运行](#10-构建与运行)
11. [CI/CD](#11-cicd)
12. [测试体系](#12-测试体系)
13. [设计模式与架构决策](#13-设计模式与架构决策)
14. [线程模型与并发安全](#14-线程模型与并发安全)
15. [安全模型](#15-安全模型)
16. [国际化与主题系统](#16-国际化与主题系统)

---

## 1. 项目概述

GameCenterApp 是一个集成 **26 款经典小游戏**的 Android 游戏中心应用，支持单机 AI 对战、局域网联机和云联机对战，同时内置浏览器和 20+ 网络/设备工具箱。

| 属性 | 值 |
|------|------|
| 包名 | `com.gamecenter.app` |
| 最低 SDK | API 24 (Android 7.0) |
| 目标 SDK | API 35 (Android 15) |
| 编译 SDK | API 35 |
| 开发语言 | Java 17 + Kotlin 2.2 |
| 版本 | 1.3.26 (versionCode 249) |
| 许可证 | MIT |

### 核心功能

- **26 款游戏**：五子棋、围棋、中国象棋、斗地主、贪吃蛇、俄罗斯方块等，分 5 大类别
- **5 款联机游戏**：五子棋、围棋、中国象棋、斗地主、石头剪刀布，支持 WebSocket 云联机
- **AI 对战**：中国象棋（PVS+迭代加深+置换表）、五子棋（Minimax+Alpha-Beta）、斗地主（规则驱动）
- **26+ 工具**：网络体检、DNS 查询、端口扫描、二维码、编码解码、哈希、取色器等
- **AI 助手**：本地优先策略，支持 DeepSeek/阿里云/硅基流动/智谱等多家云端 API + 本地 Gemma 模型
- **内置浏览器**：多标签页、书签、历史、搜索引擎切换
- **双版本分发**：Beta/Stable 分通道更新，三级下载源自动降级

---

## 2. 技术栈与依赖

### 构建工具

| 工具 | 版本 |
|------|------|
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.21 |
| KSP | 2.2.21-2.0.5 |
| Gradle Wrapper | 8.13 |
| JDK | 17 (编译) / 21 (CI) |

### 核心依赖

| 库 | 版本 | 用途 |
|----|------|------|
| `androidx.appcompat` | 1.7.1 | AppCompat 兼容支持 |
| `com.google.android.material` | 1.12.0 | Material Design 组件 |
| `androidx.constraintlayout` | 2.2.1 | 约束布局 |
| `androidx.navigation` | 2.8.9 | 导航组件 (Fragment 切换) |
| `androidx.recyclerview` | 1.4.0 | 列表视图 |
| `androidx.cardview` | 1.0.0 | 卡片视图 |
| `com.google.code.gson` | 2.11.0 | JSON 序列化/反序列化 |
| `com.google.zxing:core` | 3.5.3 | 二维码生成与识别 |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP/WebSocket 客户端 |
| `com.github.bumptech.glide:glide` | 4.16.0 | 图片加载与缓存 |
| `com.google.mediapipe:tasks-genai` | 0.10.27 | 本地 LLM 推理 (Gemma) |
| `com.google.dagger:hilt-android` | 2.57.2 | 依赖注入 |
| `androidx.room:room-runtime` | 2.6.1 | 本地数据库 (ORM) |
| `androidx.startup` | 1.2.0 | 启动初始化 (延迟加载) |
| `org.jetbrains.kotlinx:kotlinx-coroutines` | 1.10.1 | 协程支持 |
| `androidx.lifecycle` | 2.8.7 | ViewModel + Lifecycle 感知 |

### 测试依赖

| 库 | 版本 | 用途 |
|----|------|------|
| `junit:junit` | 4.13.2 | 单元测试 |
| `org.mockito:mockito-core` | 5.15.2 | Mock 框架 |
| `org.mockito.kotlin:mockito-kotlin` | 5.4.0 | Kotlin Mock 扩展 |
| `com.squareup.okhttp3:mockwebserver` | 4.12.0 | HTTP 测试服务器 |
| `androidx.test.espresso` | 3.6.1 | UI 测试 |
| `com.squareup.leakcanary` | 2.14 | 内存泄漏检测 (Debug) |

### 安全依赖强制版本

| 库 | 强制版本 | 原因 |
|----|---------|------|
| `com.google.guava:guava` | 33.4.0-jre | CVE-2023-2976 |
| `com.squareup.okio:okio` | 3.10.2 | CVE-2023-3635 |
| `io.netty:*` | 4.1.133.Final | 构建脚本安全修复 |
| `org.bouncycastle:*` | 1.84 | 构建脚本安全修复 |
| `org.apache.commons:commons-compress` | 1.26.0 | 构建脚本安全修复 |

---

## 3. 项目架构总览

```
┌──────────────────────────────────────────────────────────────────────┐
│                         GameCenterApp                                │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │
│  │  App.java    │  │ MainActivity│  │  Settings   │  │ Permission │ │
│  │  (Application)│  │  (入口)     │  │  Manager    │  │  Helper    │ │
│  └──────┬───────┘  └──────┬──────┘  └──────┬──────┘  └────────────┘ │
│         │                 │                 │                        │
│  ┌──────┴─────────────────┴─────────────────┴──────────────────┐    │
│  │                    UI Fragment 层                             │    │
│  │  GamesFragment  │  ToolsFragment  │  BrowserFragment         │    │
│  └──────┬──────────┴───────┬────────┴───────┬──────────────────┘    │
│         │                  │                │                        │
│  ┌──────┴──────┐  ┌───────┴───────┐  ┌─────┴──────┐               │
│  │  游戏模块    │  │  工具箱模块    │  │  浏览器     │               │
│  │  26 款游戏   │  │  26+ 工具     │  │  WebView    │               │
│  │  GameRegistry│  │  ToolBinder   │  │  多标签页    │               │
│  └──────┬──────┘  └───────┬───────┘  └────────────┘               │
│         │                  │                                        │
│  ┌──────┴──────────────────┴──────────────────────────────────┐    │
│  │                    核心服务层                                │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │    │
│  │  │ 网络模块  │  │ AI 模块  │  │ 更新模块  │  │ 存档管理  │  │    │
│  │  │ Socket   │  │ TaskRouter│  │ UpdateMgr│  │ SaveMgr  │  │    │
│  │  │ Relay    │  │ Local LLM│  │ 3源降级   │  │ SharedPref│  │    │
│  │  │ LAN      │  │ Cloud API│  │ MD5校验   │  │          │  │    │
│  │  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │    │
│  └────────────────────────────────────────────────────────────┘    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                    基础设施层                                  │  │
│  │  Hilt DI  │  Room DB  │  CrashHandler  │  ColorScheme       │  │
│  │  OkHttp   │  SoundMgr │  NetworkError  │  I18nHelper        │  │
│  │  Result<T>│  Memory   │  LazyInit      │  Accessibility     │  │
│  └──────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
```

### 导航架构

应用使用 Jetpack Navigation Component 管理页面切换：

```
MainActivity (NavHost)
  ├── bottom_nav_menu (BottomNavigationView)
  │     ├── nav_games    → GamesFragment
  │     ├── nav_tools    → ToolsFragment
  │     └── nav_browser  → BrowserFragment
  └── mobile_navigation.xml (NavGraph)
```

---

## 4. 目录结构

```
GameCenterApp/
├── app/
│   ├── build.gradle                              # 应用构建配置 (7 个 Section)
│   ├── proguard-rules.pro                        # R8/ProGuard 混淆规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml               # 应用清单 (30+ Activity)
│       │   ├── assets/
│       │   │   └── pkgInfo.txt                   # 包信息
│       │   ├── java/com/gamecenter/app/
│       │   │   ├── App.java                      # Application 入口 (@HiltAndroidApp)
│       │   │   ├── MainActivity.java             # 主界面 Activity (@AndroidEntryPoint)
│       │   │   ├── SettingsManager.java          # 设置管理 (SharedPreferences + Hilt)
│       │   │   ├── SaveManager.java              # 存档管理 (SharedPreferences + Hilt)
│       │   │   ├── ColorSchemeManager.java       # 主题配色管理 (8 套方案)
│       │   │   ├── PermissionHelper.java         # 权限管理 (位置/相机/存储/安装)
│       │   │   ├── ai/                           # AI 模块
│       │   │   │   ├── AiTaskRouter.java         # AI 任务路由 (本地优先)
│       │   │   │   ├── AiPreferences.java        # AI 偏好设置 (7 家 API)
│       │   │   │   ├── cloud/                    # 云端 AI
│       │   │   │   │   └── AiApiClient.java      # OpenAI 兼容 API 客户端
│       │   │   │   ├── data/                     # AI 数据模型
│       │   │   │   │   ├── AiProviderConfig.java # 提供商配置
│       │   │   │   │   ├── AiResult.java         # 结果模型 (Builder)
│       │   │   │   │   └── AiTask.java           # 任务模型
│       │   │   │   ├── local/                    # 本地 AI
│       │   │   │   │   ├── LocalAiProcessor.java # 规则引擎 (OCR/摘要/翻译/润色)
│       │   │   │   │   ├── LocalLlmOutputGuard.java # 输出守卫 (防退化)
│       │   │   │   │   └── MediaPipeLocalLlmEngine.java # Gemma 推理引擎
│       │   │   │   └── model/                    # 模型管理
│       │   │   │       ├── AiModelDownloadManager.java # 模型下载
│       │   │   │       └── AiModelInfo.java      # 模型元信息
│       │   │   ├── fragments/                    # Fragment 层
│       │   │   │   ├── GamesFragment.java        # 游戏大厅 (TabLayout+RecyclerView)
│       │   │   │   ├── ToolsFragment.java        # 工具箱 (拖拽排序+双列布局)
│       │   │   │   └── BrowserFragment.java      # 内置浏览器 (多标签 WebView)
│       │   │   ├── games/                        # 游戏模块
│       │   │   │   ├── BaseGameActivity.java     # 游戏基类 (音效/震动/动画)
│       │   │   │   ├── GameRegistry.java         # 游戏注册中心 (5 大类 26 款)
│       │   │   │   ├── GameStats.java            # 游戏统计
│       │   │   │   ├── GameUsageStore.java       # 使用记录存储
│       │   │   │   ├── GameTutorialHelper.java   # 教程弹窗管理
│       │   │   │   ├── InteractiveTutorialDialog.java # 交互式教程
│       │   │   │   ├── StatsActivity.java        # 统计页面
│       │   │   │   ├── common/                   # 游戏公共接口
│       │   │   │   │   ├── GameLogic.java        # 游戏逻辑接口
│       │   │   │   │   └── OnlineGameLogic.java  # 联机游戏逻辑接口
│       │   │   │   └── [26 个游戏子包]/           # 各游戏实现
│       │   │   ├── initializers/
│       │   │   │   └── NetworkInitializer.java   # App Startup 延迟初始化
│       │   │   ├── network/                      # 网络通信模块
│       │   │   │   ├── BaseOnlineActivity.java   # 联机基类
│       │   │   │   ├── GameSocketClient.java     # WebSocket 客户端 (单例+状态机)
│       │   │   │   ├── GameSocketServer.java     # WebSocket 服务端 (3 种模式, 委托门面)
│       │   │   │   ├── RelayHostHelper.java      # 云中转模式逻辑
│       │   │   │   ├── WebSocketHostHelper.java  # WebSocket 模式逻辑
│       │   │   │   ├── LANManager.java           # 局域网 UDP 广播发现
│       │   │   │   ├── OkHttpClientProvider.java # OkHttp 单例 (50MB 缓存+重试)
│       │   │   │   ├── OnlineChatHelper.java     # 联机聊天 (内联/弹窗)
│       │   │   │   ├── RelayHttpClient.java      # HTTP Relay 通信 + WS URL 生成
│       │   │   │   ├── RemoteP2PUtil.java        # 房间码 + P2P 令牌工具
│       │   │   │   └── RequestDeduplicationInterceptor.java # 请求去重
│       │   │   ├── settings/
│       │   │   │   └── AppSettingsDialog.java    # 设置对话框
│       │   │   ├── tools/                        # 工具箱模块
│       │   │   │   ├── ToolBinder.java           # 工具绑定接口
│       │   │   │   ├── ToolHelper.java           # 工具辅助 (委托门面)
│       │   │   │   ├── NetworkDiagHelper.java    # 网络诊断
│       │   │   │   ├── IpClassifier.java         # IP 运营商分类
│       │   │   │   ├── SubnetCalculator.java     # 子网计算
│       │   │   │   ├── ToolSection.java          # 工具分区
│       │   │   │   ├── ToolSectionStore.java     # 工具分区存储 (排序/收藏/最近)
│       │   │   │   ├── AdvancedToolBinders.java  # 高级工具绑定
│       │   │   │   └── [20+ *ToolBinder.java]    # 各工具实现
│       │   │   ├── update/                       # 更新模块
│       │   │   │   ├── UpdateManager.java        # 更新管理器 (委托门面)
│       │   │   │   ├── UpdateChecker.java        # 更新检查逻辑
│       │   │   │   ├── UpdateDownloader.java     # APK 下载逻辑
│       │   │   │   ├── UpdateInstaller.java      # APK 安装逻辑
│       │   │   │   ├── UpdateNotificationHelper.java # 更新通知管理
│       │   │   │   ├── UpdateInfo.java           # 版本信息模型 (POJO+Builder)
│       │   │   │   └── SSLHelper.java            # SSL 辅助 (Debug/Release 区分)
│       │   │   ├── utils/                        # 通用工具
│       │   │   │   ├── ErrorReporter.java        # 错误上报 (10次/小时+降级)
│       │   │   │   ├── I18nHelper.java           # 国际化辅助
│       │   │   │   ├── NetworkErrorHandler.java  # 网络错误 (10 种错误码)
│       │   │   │   ├── SoundManager.java         # 音效管理 (SoundPool+MediaPlayer)
│       │   │   │   └── SystemInfoCollector.java  # 系统信息采集
│       │   │   └── views/                        # 自定义 View
│       │   │       ├── ColorAlphaBar.java        # 颜色透明度条
│       │   │       ├── ColorHueBar.java          # 色相条
│       │   │       └── ColorSVPanel.java         # 饱和度/明度面板
│       │   ├── kotlin/com/gamecenter/app/
│       │   │   ├── database/
│       │   │   │   ├── AppDatabase.kt            # Room 数据库 (v1)
│       │   │   │   ├── dao/
│       │   │   │   │   ├── AiMessageDao.kt       # AI 消息 DAO
│       │   │   │   │   └── GameStatsDao.kt       # 游戏统计 DAO
│       │   │   │   └── entity/
│       │   │   │       ├── AiMessageEntity.kt    # AI 消息实体
│       │   │   │       └── GameStatsEntity.kt    # 游戏统计实体
│       │   │   ├── di/
│       │   │   │   └── AppModule.kt              # Hilt DI 模块 (12 个 @Provides)
│       │   │   └── util/
│       │   │       ├── AccessibilityHelper.kt    # 无障碍辅助
│       │   │       ├── CrashHandler.kt           # 崩溃处理 + ThreadPools
│       │   │       ├── Extensions.kt             # Kotlin 扩展 + AppLog
│       │   │       ├── LazyInitManager.kt        # 延迟初始化 + PerformanceMonitor
│       │   │       ├── MemoryUtils.kt            # BitmapCache + MemoryMonitor
│       │   │       └── Result.kt                 # 函数式结果类型 (sealed class)
│       │   └── res/                              # 资源文件
│       │       ├── layout/                       # 布局 (50+ XML)
│       │       ├── drawable/                     # 图标与形状
│       │       ├── values/                       # 字符串/颜色/主题 (中文)
│       │       ├── values-en/                    # 英文资源
│       │       ├── values-night/                 # 暗色主题资源
│       │       ├── raw/                          # 音效 (96 个斗地主音频)
│       │       ├── anim/                         # 动画 (7 种)
│       │       ├── menu/                         # 菜单 (底部导航+工具布局)
│       │       ├── navigation/                   # 导航图
│       │       ├── mipmap-anydpi-v26/            # 自适应图标
│       │       └── xml/                          # 配置 (网络安全/文件路径)
│       ├── test/                                 # 单元测试
│       └── androidTest/                          # 集成测试
├── vps/                                          # 服务端部署模板
│   ├── ddz_ws_relay/
│   │   ├── server.js                             # WebSocket 中继 (Node.js)
│   │   └── package.json
│   └── var_www_update/
│       ├── update_server.py                      # 更新服务 (Python)
│       ├── error_report.py                       # 错误报告服务
│       ├── ddz_relay/
│       │   └── ddz_relay_server.py               # HTTP Relay 服务
│       └── feedback/
│           └── feedback_server.py                # 反馈服务
├── tools/                                        # 构建与部署工具
│   ├── upload_to_vps.py                          # VPS 上传
│   ├── upload_to_github_release.py               # GitHub Release 上传
│   ├── publish-all.py                            # 一键发布
│   ├── verify_vps.py                             # VPS 验证
│   ├── check_vps_nginx.py                        # Nginx 检查
│   ├── test_vps_http.py                          # VPS HTTP 测试
│   └── optimize-images.ps1                       # 图片优化
├── docs/                                         # 技术文档
├── gradle/
│   ├── wrapper/                                  # Gradle Wrapper
│   └── libs.versions.toml                        # 版本目录 (95+ 条目)
├── .github/workflows/ci.yml                      # CI/CD 配置
├── build.gradle                                  # 根构建文件 (安全版本强制)
├── settings.gradle                               # 项目设置 (单模块 :app)
├── version.properties                            # 版本号管理
├── gradle.properties                             # Gradle 属性 (JVM/AndroidX/R8)
├── local.properties.template                     # 本地配置模板
└── CHANGELOG.md                                  # 更新日志
```

---

## 5. 核心模块详解

### 5.1 应用入口层

#### App.java

应用入口类，继承 `Application`，标注 `@HiltAndroidApp`。

| 项目 | 说明 |
|------|------|
| 类名 | `App extends Application` |
| 注解 | `@HiltAndroidApp` |
| 关键字段 | `isDarkMode` — 当前暗色模式状态；`updateAutoCheckDone` — 自动更新检查标记 |

**关键方法**：

| 方法 | 说明 |
|------|------|
| `onCreate()` | 应用启动入口：调用 `applyLanguage()`、`applyTheme()`，注册 `ActivityLifecycleCallbacks` |
| `applyTheme()` | 根据 `SettingsManager` 主题模式设置 `AppCompatDelegate` 夜间模式 (SYSTEM/LIGHT/DARK) |
| `applyLanguage()` | 从 `SettingsManager` 读取语言偏好并设置 `AppCompatDelegate.setApplicationLocales()` |
| `applyColorScheme(Activity)` | 对每个 Activity 应用配色方案（在 `onActivityCreated` 回调中触发） |
| `shouldAutoCheckUpdate()` | 确保自动更新检查仅执行一次（原子布尔标记） |
| `refreshColorScheme(Activity)` | 静态方法，供外部刷新 Activity 配色 |

**启动流程**：

```
App.onCreate()
  ├── applyLanguage()          → SettingsManager.getAppLanguage() → AppCompatDelegate
  ├── applyTheme()             → SettingsManager.getThemeMode() → AppCompatDelegate
  └── registerLifecycleCallbacks()
        └── onActivityCreated() → applyColorScheme() → ColorSchemeManager

NetworkInitializer.create()    → OkHttpClientProvider.preload() (延迟 500ms)
```

**依赖**：`SettingsManager`、`ColorSchemeManager`、`OkHttpClientProvider`、`UpdateManager`

#### MainActivity.java

主界面 Activity，标注 `@AndroidEntryPoint`，承载底部导航。更新逻辑已抽取到 `UpdatePresenter`，Handler 回调使用 `WeakReference` 静态内部类防止内存泄漏。

| 项目 | 说明 |
|------|------|
| 类名 | `MainActivity extends AppCompatActivity` |
| 注解 | `@AndroidEntryPoint` |
| 导航 | `NavController` + `NavHostFragment` + `BottomNavigationView` |
| 更新 | 委托给 `UpdatePresenter` |

**关键方法**：

| 方法 | 说明 |
|------|------|
| `onCreate()` | 初始化权限、Navigation、底部导航栏、创建 UpdatePresenter、调度自动更新检查 |
| `checkUpdate()` | 委托给 UpdatePresenter |
| `onDestroy()` | 调用 UpdatePresenter.onDestroy() 清理资源 |
| `scheduleAutoUpdateCheck()` | 延迟 2s 后检查 `App.shouldAutoCheckUpdate()` + `SettingsManager.isAutoCheckUpdate()` |
| `checkUpdate(boolean)` | 调用 `UpdateManager.checkUpdate()`，处理 4 种结果：有更新/Beta过期/Beta阻止/无更新 |
| `showUpdateDialog(UpdateInfo)` | 展示更新信息对话框（支持强制更新不可取消） |
| `showBetaOnlyNoticeDialog(UpdateInfo)` | Beta 过期提示，引导用户开启 Beta 接受 |
| `startDownload(UpdateInfo)` | 手动下载 APK（带进度弹窗） |
| `startAutoDownload(UpdateInfo, boolean)` | 自动后台下载 APK（无进度弹窗，下载完提示安装） |
| `installApk(File)` | 安装 APK（含 `REQUEST_INSTALL_PACKAGES` 权限检查） |
| `showInstallDialog(File)` | 安装确认对话框（安装/打开目录/取消） |

**依赖**：`UpdateManager`、`UpdateInfo`、`PermissionHelper`、`SettingsManager`、`App`

---

### 5.2 游戏模块

#### GameRegistry.java

游戏注册中心，管理全部 26 款游戏的分类与元信息。

| 项目 | 说明 |
|------|------|
| 类名 | `GameRegistry` (final 工具类，私有构造) |
| 内部类 | `Category` — 游戏分类（name + games 列表）；`Entry` — 游戏条目（id/iconRes/name/desc/activityClass/category） |

**关键方法**：

| 方法 | 说明 |
|------|------|
| `getCategories(Context)` | 返回所有游戏分类（经典7/益智5/休闲5/反应5/其他4），不可变列表 |
| `flatten(List<Category>)` | 将分类列表展平为游戏列表 |

**设计模式**：注册表模式 (Registry) + 不可变值对象

#### BaseGameActivity.java

所有游戏的抽象基类，提供音效、震动、动画等通用功能。

| 项目 | 说明 |
|------|------|
| 类名 | `BaseGameActivity extends AppCompatActivity` (abstract) |
| 关键字段 | `soundManager`、`vibrator`、`settings`、`soundEnabled`、`vibrationEnabled` |

**关键方法**：

| 方法 | 说明 |
|------|------|
| `onCreate()` | 初始化音效管理器、震动器、读取设置 |
| `loadGameSounds()` | 可重写方法，子类重写以加载游戏特定音效 |
| `playClickSound()` | 播放点击音效 (R.raw.sound_click_button) |
| `vibrateShort()` / `vibrateLong()` | 短震动 (50ms) / 长震动 (200ms) |
| `animateView()` / `animateViewWithAction()` | 视图动画（带可选回调） |
| `playWinAnimation()` | 胜利动画 (R.anim.win_celebrate) |
| `onPause()` / `onResume()` | 暂停/恢复背景音乐 |
| `onDestroy()` | 释放音效资源 |

**设计模式**：模板方法模式 (Template Method)

**依赖**：`SoundManager`、`SettingsManager`

---

### 5.3 网络通信模块

网络模块位于 `com.gamecenter.app.network` 包，为所有联机游戏提供统一的通信基础设施。

#### GameSocketServer.java

房主端权威服务器，支持三种通信模式。已提取 `RelayHostHelper`（云中转逻辑）和 `WebSocketHostHelper`（WebSocket 逻辑），原类保留为委托门面。

| 项目 | 说明 |
|------|------|
| 类名 | `GameSocketServer` |
| 内部类 | `ClientConnection` — 客户端连接封装（Socket + 读写线程 + 心跳） |
| 委托类 | `RelayHostHelper` — 云中转模式逻辑；`WebSocketHostHelper` — WebSocket 模式逻辑 |
| 常量 | `MAX_CLIENTS=4`、`HEARTBEAT_TIMEOUT=30s` |

**三种通信模式**：

| 模式 | 启动方法 | 委托类 | 说明 |
|------|---------|--------|------|
| 局域网直连 | `start(port)` | — | ServerSocket 监听，最大 4 客户端 |
| 云中转 (HTTP) | `startRelay(baseUrl)` | `RelayHostHelper` | HTTP 轮询中转服务器 |
| WebSocket | `startWebSocket(wsUrl)` | `WebSocketHostHelper` | WebSocket 连接 (OkHttpClient) |

**关键方法**：

| 方法 | 说明 |
|------|------|
| `start(port)` | 启动局域网服务器（线程池 + 心跳检测 + 连接接收） |
| `startRelay(baseUrl)` | 创建云房间（POST /create），启动轮询线程 |
| `startWebSocket(wsUrl)` | 连接 WebSocket 服务器，启动心跳 |
| `stop()` | 停止服务器（关闭所有连接、线程、通知中转服务器关闭房间） |
| `broadcast(JSONObject)` | 广播消息给所有客户端（根据模式分发） |
| `sendTo(int, JSONObject)` | 单播消息给指定客户端 |
| `broadcastGameOver(int)` | 广播游戏结束 |
| `disconnectClient(int, String)` | 断开指定客户端 |

**回调接口**：`OnClientConnectedListener`、`OnClientDisconnectedListener`、`OnMessageReceivedListener`、`OnErrorListener`

**设计模式**：观察者模式 + 策略模式（三种通信策略）

#### GameSocketClient.java

客户端连接管理，单例模式，支持三种通信模式。

| 项目 | 说明 |
|------|------|
| 类名 | `GameSocketClient` (单例，`getInstance()` / `getInstance(Context)`) |
| 枚举 | `ConnectionState` — DISCONNECTED/CONNECTING/CONNECTED/AUTHENTICATED/RECONNECTING |

**关键方法**：

| 方法 | 说明 |
|------|------|
| `connectWebSocket(wsUrl)` | WebSocket 方式连接（自动解析 roomCode） |
| `connectRelay(roomCode, baseUrl)` | 云中转方式连接（POST /join） |
| `disconnect()` | 主动断开连接（通知服务器） |
| `send(JSONObject)` | 发送消息（根据模式分发：WebSocket/Relay/Socket） |
| `reconnectNow()` | 立即重连（重置心跳计数器） |
| `release()` | 释放所有资源（断开连接 + 关闭线程池） |

**重连策略**：指数退避（`reconnectInterval * 2^(attempt-1)`，上限 `maxReconnectInterval`，默认 3 次尝试）

**待发消息队列**：`pendingMessages`（ConcurrentLinkedQueue，上限 32 条，WebSocket 连接后自动 flush）

**设计模式**：单例 + 状态机 + 策略模式

#### RelayHttpClient.java

HTTP Relay 通信工具类，统一供所有联机游戏使用。已从 `HttpURLConnection` 迁移到 OkHttp，与项目统一 HTTP 客户端策略一致。

| 项目 | 说明 |
|------|------|
| 类名 | `RelayHttpClient` (final 工具类，私有构造) |
| 常量 | `DEFAULT_BASE_URL = BuildConfig.RELAY_URL` |

**关键方法**：

| 方法 | 说明 |
|------|------|
| `getWebSocketUrl(baseUrl, roomCode, hostToken)` | 生成主机端 WebSocket URL（含 room/role/token 参数） |
| `getWebSocketClientUrl(baseUrl, roomCode)` | 生成客户端 WebSocket URL |
| `post(baseUrl, path, body, timeoutMs)` | 发送 HTTP POST 请求到中转服务器（使用 OkHttp，public，支持跨包调用） |
| `convertHttpToWs()` | 将 http(s):// 转换为 ws(s)://（去除路径部分） |

**WebSocket URL 生成逻辑**：优先使用 `BuildConfig.WS_URL`，否则从 Relay URL 推导

#### LANManager.java

局域网 UDP 广播服务发现管理器。

| 项目 | 说明 |
|------|------|
| 类名 | `LANManager` |
| 内部类 | `DiscoveredHost` — 发现的主机信息 (ip/port/playerName/lastSeen) |
| 常量 | `DISCOVERY_PORT=9877`、`BROADCAST_INTERVAL=3000ms`、`DISCOVERY_TIMEOUT=8000ms` |

**关键方法**：

| 方法 | 说明 |
|------|------|
| `LANManager(gameName, playerName, serverPort)` | 构造时指定游戏名、玩家名、服务器端口 |
| `startDiscovery()` | 启动 UDP 广播发现（广播线程 + 接收线程） |
| `stopDiscovery()` | 停止发现（关闭 Socket + 中断线程） |
| `getDiscoveredHosts()` | 获取已发现的主机列表（不可变） |

**回调接口**：`OnHostDiscoveredListener`

**发现协议**：UDP 广播 JSON `{"type":"DISCOVERY","game":"...","player":"...","port":...}`

#### RemoteP2PUtil.java

房间码与 P2P 令牌工具类。

| 方法 | 说明 |
|------|------|
| `normalizeRoomCode(code)` | 规范化房间码（去除协议前缀如 `DDZ://`，转大写，过滤非法字符） |
| `isValidRoomCode(code)` | 验证 6 位字母数字房间码（`ROOM_CODE_ALPHABET` = A-Z2-9，32 字符） |
| `verifyRoomCode(context, code, callback)` | 异步验证房间码 |
| `savePeerToken()` / `getLastPeerToken()` / `clearPeerToken()` | P2P 令牌管理（SharedPreferences） |
| `buildWebSocketUrl(wsServer, gameProtocol, roomCode, peerToken)` | 构建 WebSocket URL（含 game/room/token 参数） |

#### BaseOnlineActivity.java

联机游戏 Activity 基类，封装联机通用逻辑（创建/加入房间、聊天、连接管理）。

#### OnlineChatHelper.java

联机聊天辅助类，处理聊天消息的发送和显示，支持弹窗模式和内联模式。

| 项目 | 说明 |
|------|------|
| 常量 | `MAX_MESSAGES=50` |
| 内部类 | `ChatMessage` — 聊天消息 (sender/text/isMine) |
| 模式 | 弹窗模式 (AlertDialog) / 内联模式 (TextView + ScrollView) |

#### OkHttpClientProvider.java

OkHttpClient 单例提供者，标注 `@Singleton` + `@Inject`，统一管理 HTTP/WebSocket 连接。

| 项目 | 说明 |
|------|------|
| HTTP 缓存 | 50MB 磁盘缓存 |
| HTTP 超时 | 连接 15s / 读取 30s / 写入 30s |
| WebSocket 超时 | 连接 10s / 读取无超时 |
| 重试 | 最大 3 次，指数退避延迟 |
| 拦截器 | `RetryInterceptor`（重试）+ `RequestDeduplicationInterceptor`（去重） |
| 预加载 | `preload(Context)` 延迟 500ms 初始化，不阻塞启动 |

#### RequestDeduplicationInterceptor.java

OkHttp 拦截器，防止短时间内重复请求同一 URL。

#### NetworkInitializer.java

App Startup 初始化器，实现 `Initializer<Void>` 接口，在应用启动时自动调用 `OkHttpClientProvider.preload()`。

---

### 5.4 AI 模块

AI 模块位于 `com.gamecenter.app.ai` 包，采用分层架构：

```
ai/
├── AiTaskRouter.java          # 调度中心 (本地优先)
├── AiPreferences.java         # 偏好设置 (7 家 API)
├── cloud/
│   └── AiApiClient.java       # OpenAI 兼容 API 客户端
├── data/
│   ├── AiProviderConfig.java  # 提供商配置 (Builder)
│   ├── AiResult.java          # 结果模型 (Builder)
│   └── AiTask.java            # 任务模型
├── local/
│   ├── LocalAiProcessor.java  # 规则引擎 (OCR/摘要/翻译/润色/问答/关键词/分类)
│   ├── LocalLlmOutputGuard.java # 输出守卫 (防退化/乱码/重复)
│   └── MediaPipeLocalLlmEngine.java # Gemma 推理引擎 (MediaPipe)
└── model/
    ├── AiModelDownloadManager.java # 模型下载管理
    └── AiModelInfo.java       # 模型元信息
```

#### AiTaskRouter.java

AI 任务路由器，实现 **本地优先 (Local First)** 策略。

| 项目 | 说明 |
|------|------|
| 类名 | `AiTaskRouter` |
| 关键字段 | `aiPrefs`、`aiExecutor`(单线程)、`modelDownloadManager`、`localLlmEngine`、统计计数器 |

**路由策略**：

```
用户提交任务 → tryLocalProcessing() → tryLocalLlm() → 云端 API
                  (本地规则)           (Gemma 模型)    (DeepSeek/阿里云/...)
```

**关键方法**：

| 方法 | 说明 |
|------|------|
| `submitTask(taskType, input, callback)` | 提交 AI 任务（异步），返回 AiTask |
| `executeTask()` | 执行路由：本地 → 云端，含网络检查和额度检查 |
| `tryLocalProcessing()` | 尝试本地规则处理（OCR/摘要/翻译/润色/问答/关键词/分类/指令识别） |
| `tryLocalLlm()` | 尝试本地 Gemma 模型推理（含内存检查 ≥3GB、输出守卫） |
| `buildPrompt()` | 根据任务类型构建提示词（8 种任务类型） |
| `estimateCost()` | 估算任务成本等级 (1=轻量, 2=重量) |
| `hasEnoughMemory(minRamMb)` | 检查设备内存是否满足模型要求 |
| `shutdown()` | 关闭执行器和模型引擎 |

**支持的任务类型**：`ocr`、`summary`、`translate`、`rewrite`、`qa`/`qa_pairs`、`keywords`、`classify`、`template`、`chat`

**回调接口**：`AiCallback { void onResult(AiTask, AiResult); }`

**设计模式**：策略路由 + 责任链（本地 → 云端）

#### AiPreferences.java

AI 偏好设置管理，基于 SharedPreferences (`ai_settings`)。

| 方法 | 说明 |
|------|------|
| `getSelectedProvider()` / `setSelectedProvider()` | 当前 AI 提供商（默认 DeepSeek） |
| `getSelectedModel()` / `setSelectedModel()` | 当前模型（默认 deepseek-chat） |
| `isLocalFirst()` / `setLocalFirst()` | 是否本地优先（默认 true） |
| `getApiKey()` / `setApiKey()` | API Key |
| `getLocalModel()` / `setLocalModel()` | 本地模型选择（默认 on-device） |
| `hasFreeQuota()` / `incrementUsage()` | 每日免费额度管理（默认 20 次/天，按日重置） |
| `hasAcceptedGemmaNotice(version)` / `acceptGemmaNotice(version)` | Gemma 用户协议确认 |
| `getAvailableProviders(Context)` | 返回所有可用 AI 提供商列表（静态方法） |

**支持的 AI 提供商**（全部使用 OpenAI 兼容接口格式）：

| 提供商 | 模型 | 类型 |
|--------|------|------|
| 本地 | on-device | 免费/本地 |
| OpenAI | gpt-4o-mini | 国外/付费 |
| DeepSeek | deepseek-chat | 国产/高性价比 |
| DeepSeek Reasoner | deepseek-reasoner | 国产/推理 |
| 阿里云通义 Turbo | qwen-turbo | 国产/快速 |
| 阿里云通义 Plus | qwen-plus | 国产/均衡 |
| 阿里云通义 Max | qwen-max | 国产/高级 |
| 硅基流动 DeepSeek | deepseek-ai/DeepSeek-V3 | 国产/极低价 |
| 硅基流动 Qwen | Qwen/Qwen2.5-7B-Instruct | 国产/极低价 |
| 智谱 Flash | glm-4-flash | 国产/快速 |
| 智谱 Plus | glm-4-plus | 国产/均衡 |
| 零一万物 Lightning | yi-lightning | 国产/快速 |
| 零一万物 Large | yi-large | 国产/高级 |

---

### 5.5 工具箱模块

#### ToolBinder.java (接口)

所有工具的绑定接口，每个工具实现此接口。

```java
public interface ToolBinder {
    void bind(Context context, View contentView, ExecutorService executor);
}
```

**设计原则**：实现类应为无状态或使用参数传入状态，避免持有 Fragment/Activity 引用。

#### ToolSectionStore.java

工具分区存储，管理 26 个工具的分类、排序、可见性和收藏。

| 方法 | 说明 |
|------|------|
| `loadSections()` | 加载工具分区列表（含排序和可见性恢复） |
| `saveOrder()` / `saveVisibility()` | 持久化排序和可见性 |
| `getLayoutMode()` / `saveLayoutMode()` | 布局模式（单列/双列） |
| `isFavorite()` / `toggleFavorite()` | 收藏管理 |
| `recordRecent()` / `getRecentIds()` | 最近使用记录（最多 8 个） |

**默认工具分区** (26 个)：

| ID | 名称 |
|----|------|
| `network_diagnosis` | 一键网络体检 |
| `diagnostic_report` | 诊断报告导出 |
| `dns_lookup` | DNS查询 |
| `lan_scan` | 局域网设备扫描 |
| `text_codec` | 编码/时间戳/JSON |
| `file_hash` | 文件哈希 |
| `qr_plus` | 二维码增强 |
| `color_plus` | 颜色增强 |
| `permission_privacy` | 权限与隐私说明 |
| `ip` | IP地址信息 |
| `dns` | DNS服务器 |
| `wifi` | WiFi信号 |
| `speedtest` | 网络测速 |
| `portscan` | 端口扫描 |
| `qr` | 二维码工具 |
| `battery` | 电池信息 |
| `device` | 设备信息 |
| `ping` | Ping工具 |
| `traceroute` | 路由追踪 |
| `subnet` | 子网计算器 |
| `screen` | 屏幕信息 |
| `sensor` | 传感器信息 |
| `hash` | 哈希计算器 |
| `clipboard` | 剪贴板工具 |
| `color` | 颜色取色器 |
| `sysinfo` | 手机系统详细信息 |

#### ToolHelper.java

工具辅助类（委托门面），提供网络和系统信息查询功能。已拆分为 `NetworkDiagHelper`（网络诊断）、`IpClassifier`（IP 运营商分类）、`SubnetCalculator`（子网计算），原类保留为委托门面。

| 方法 | 委托类 | 说明 |
|------|--------|------|
| `getWifiIpAddress()` / `getMobileIpAddress()` | — | 获取 IP 地址 |
| `checkVpnStatus()` | — | 检测 VPN 连接状态 |
| `getDnsServers()` | — | 获取 DNS 服务器列表 |
| `testPing()` / `pingHost()` | `NetworkDiagHelper` | Ping 测试 |
| `testDownloadSpeed()` / `testUploadSpeed()` | `NetworkDiagHelper` | 网络测速 |
| `traceRouteHop()` | `NetworkDiagHelper` | 路由追踪 |
| `classifyIpCarrier()` | `IpClassifier` | IP 归属地运营商分类 |
| `calculateSubnet()` | `SubnetCalculator` | 子网计算器 |
| `fetchPublicIpFast()` | `NetworkDiagHelper` | 获取公网 IP（多 API 降级） |

#### 工具实现类列表

| 工具绑定器 | 功能 |
|-----------|------|
| `PingToolBinder` | Ping 测试 |
| `DnsToolBinder` | DNS 配置查询 |
| `DnsLookupToolBinder` | DNS 记录查询 |
| `IpToolBinder` | IP 地址信息 |
| `LanScanToolBinder` | 局域网设备扫描 |
| `PortScanToolBinder` | 端口扫描 |
| `SubnetToolBinder` | 子网计算器 |
| `TracerouteToolBinder` | 路由追踪 |
| `NetworkDiagnosisToolBinder` | 网络体检 |
| `SpeedTestToolBinder` | 网络测速 |
| `WifiToolBinder` | WiFi 信息 |
| `QrToolBinder` | 二维码生成 |
| `QrPlusToolBinder` | 高级二维码（WiFi/名片/图片） |
| `HashToolBinder` | 文本哈希 (MD5/SHA) |
| `FileHashToolBinder` | 文件哈希 |
| `TextCodecToolBinder` | 编码/解码 (URL/Base64/JSON/时间戳) |
| `ColorPickerToolBinder` | 颜色取色器 |
| `ColorPlusToolBinder` | 高级取色器 (WCAG 对比度) |
| `ClipboardToolBinder` | 剪贴板管理 |
| `BatteryToolBinder` | 电池信息 |
| `DeviceToolBinder` | 设备信息 |
| `ScreenToolBinder` | 屏幕信息 |
| `SensorToolBinder` | 传感器信息 |
| `SystemInfoToolBinder` | 系统信息 |
| `DiagnosticReportToolBinder` | 诊断报告导出 |
| `PermissionPrivacyToolBinder` | 权限隐私说明 |

---

### 5.6 更新模块

#### UpdateManager.java

应用更新管理器（委托门面），单例模式，支持三级下载源自动降级。已拆分为 `UpdateChecker`（检查逻辑）、`UpdateDownloader`（下载逻辑）、`UpdateInstaller`（安装逻辑）、`UpdateNotificationHelper`（通知逻辑），原类保留为委托门面。

| 项目 | 说明 |
|------|------|
| 类名 | `UpdateManager` (单例，`getInstance()`) |
| 委托类 | `UpdateChecker` — 更新检查；`UpdateDownloader` — APK 下载；`UpdateInstaller` — APK 安装；`UpdateNotificationHelper` — 通知管理 |
| 回调接口 | `UpdateCheckCallback` (onResult/onError/onCancelled)、`DownloadCallback` (onProgress/onVerifying/onComplete/onError/onCancelled) |

**三级下载源优先级**：

```
1. 自定义 URL (用户设置的自定义更新源)
2. 香港 VPS (BuildConfig.SERVER_URL)
3. 美国 VPS (BuildConfig.SERVER_URL_FALLBACK)
4. GitHub Releases
```

**关键方法**（均委托到对应子类）：

| 方法 | 委托类 | 说明 |
|------|--------|------|
| `checkUpdate(context, callback)` | `UpdateChecker` | 多源检查更新（按优先级遍历，首个成功即返回） |
| `downloadApk(context, info, callback)` | `UpdateDownloader` | 多源下载 APK（含速度检测自动换源、MD5 校验、通知栏进度） |
| `installApk(context, apkFile)` | `UpdateInstaller` | 安装 APK（FileProvider URI） |
| `canRequestInstall()` / `requestInstallPermission()` | `UpdateInstaller` | 安装权限检查与请求 (Android O+) |
| `cleanOldApks()` | `UpdateDownloader` | 清理旧版 APK（仅保留最新版本） |
| `openDownloadDirectory()` | `UpdateInstaller` | 打开下载目录 |

**设计模式**：门面模式 + 单例 + 策略模式（多源降级）+ 回调模式

#### UpdateInfo.java

版本信息数据模型 (POJO)。

| 字段 | 说明 |
|------|------|
| `hasUpdate` | 是否有更新 |
| `versionCode` / `versionName` | 远程版本号 |
| `downloadUrl` | 下载地址 |
| `changelog` | 更新日志 |
| `forceUpdate` | 是否强制更新 |
| `fileSize` / `md5` | 文件大小和 MD5 校验 |
| `channel` | 发布通道 (beta/stable) |
| `betaRelease` | 是否为测试版 |
| `localVersionCode` / `localVersionName` | 本地版本号 |
| `sourceVersionUrl` | 版本信息来源 URL |
| `lastStableVersionCode` / `lastStableVersionName` | 上一个正式版版本号 |
| `betaNoticeVersionGap` | Beta 提示版本差（默认 3） |
| `betaUpdateBlocked` | Beta 更新是否被用户设置阻止 |
| `betaUpdateOutdated` | 本地版本是否相对正式版过旧 |

**关键方法**：

| 方法 | 说明 |
|------|------|
| `fromJson(JSONObject)` | 从 JSON 解析创建 UpdateInfo（支持多字段兼容：downloadUrl/apkUrl/url） |
| `resolveChannel()` | 智能推断发布通道（从 channel 字段或 versionName 推断） |
| `getChannelLabel()` | 返回"测试版"/"正式版" |
| `getFileSizeFormatted()` | 格式化文件大小 |

---

### 5.7 UI Fragment 层

#### GamesFragment.java

游戏大厅 Fragment，展示游戏分类列表。

| 方法 | 说明 |
|------|------|
| `onViewCreated()` | 初始化 TabLayout（全部/最近/收藏/经典/益智/休闲/反应/其他）、搜索框、RecyclerView |
| `initCategories()` | 从 `GameRegistry` 加载游戏目录 |
| `setupSearch()` | 搜索过滤 |
| `applyFilter()` | 根据当前 Tab 和搜索词过滤游戏 |
| `showSettingsDialog()` | 打开应用设置对话框 |
| `showFeedbackDialog()` | 反馈对话框 |
| `showChangelog()` | 更新日志对话框 |

**依赖**：`GameRegistry`、`GameUsageStore`、`ColorSchemeManager`、Glide

#### ToolsFragment.java

工具箱 Fragment，展示 26+ 工具列表。

| 方法 | 说明 |
|------|------|
| `onViewCreated()` | 初始化 RecyclerView、加载工具分区 |
| `initBinders()` | 初始化 26 个工具的 `ToolBinder` 映射 |
| `showLayoutModeMenu()` | 单列/双列布局切换 |
| `attachTouchHelper()` | 拖拽排序支持 (ItemTouchHelper) |

**设计模式**：适配器模式 + 策略模式（ToolBinder）

#### BrowserFragment.java

内置浏览器 Fragment，多标签页 WebView。

| 方法 | 说明 |
|------|------|
| `configureWebView()` | 配置 WebView（JS/DOM/缩放/Cookie/SSL/下载） |
| `fetchSuggestions()` | 百度搜索建议 API |
| `createNewTab()` / `switchToTab()` / `closeTab()` | 标签页管理 |
| `toggleBookmark()` / `showBookmarksDialog()` | 书签管理 |
| `addToHistory()` / `showHistoryDialog()` | 历史记录（上限 100 条） |
| `showSearchEngineDialog()` | 搜索引擎切换（百度/Google/Bing） |

---

### 5.8 基础设施层

#### SettingsManager.java

应用设置管理，单例模式，基于 SharedPreferences (`app_settings`)。通过 `@Provides` 在 AppModule 中提供，构造函数中设置 `instance = this`，确保 Hilt 注入和 `getInstance()` 返回同一实例。

| 常量 | 值 | 说明 |
|------|------|------|
| `THEME_SYSTEM` | 0 | 跟随系统 |
| `THEME_LIGHT` | 1 | 浅色主题 |
| `THEME_DARK` | 2 | 深色主题 |
| `UPDATE_SOURCE_AUTO` | 0 | 自动选择 |
| `UPDATE_SOURCE_VPS_HK` | 1 | 香港 VPS |
| `UPDATE_SOURCE_VPS_US` | 2 | 美国 VPS |
| `UPDATE_SOURCE_GITHUB` | 3 | GitHub |
| `LANGUAGE_SYSTEM` | "" | 跟随系统 |
| `LANGUAGE_ZH` | "zh" | 中文 |
| `LANGUAGE_EN` | "en" | English |

**管理范围**：主题模式、配色方案、自动更新、Beta 接受、自动下载、安装提示、更新源、音效、震动、语言

#### SaveManager.java

游戏存档管理，双重检查锁定单例，基于 SharedPreferences。通过 `@Provides` 在 AppModule 中提供，构造函数中设置 `instance = this`，确保 Hilt 注入和 `getInstance()` 返回同一实例。

| 方法 | 说明 |
|------|------|
| `save(gameId, slotKey, jsonState)` | 保存游戏存档 |
| `load(gameId, slotKey)` | 读取存档 |
| `hasSave(gameId, slotKey)` | 检查存档是否存在 |
| `deleteSave(gameId, slotKey)` | 删除存档 |
| `saveProgress(gameId, jsonProgress)` / `loadProgress(gameId)` | 关卡进度存取 |

#### ColorSchemeManager.java

主题配色管理，纯静态工具类，提供 8 套预定义配色方案。

| 配色方案 | 说明 |
|---------|------|
| 清朗紫 | 默认紫色主题 |
| 深海蓝 | 蓝色主题 |
| 竹影绿 | 绿色主题 |
| 晨曦橙 | 橙色主题 |
| 蔷薇莓 | 粉色主题 |
| 极光青 | 青色主题 |
| 墨金 | 深色金色主题 |
| 朱砂红 | 红色主题 |

**关键方法**：`getScheme(int)` — 按索引获取配色方案；`applyScheme(Activity, Scheme, boolean)` — 将配色应用到 Activity

#### PermissionHelper.java

权限请求辅助类，管理位置、相机、存储、安装权限。首次启动时展示权限使用说明对话框。

#### SoundManager.java

音效管理器，封装 SoundPool + MediaPlayer。

| 方法 | 说明 |
|------|------|
| `setEnabled(boolean)` | 启用/禁用音效 |
| `loadSound(int)` / `playSound(int, boolean)` | 音效加载与播放 |
| `playBackgroundMusic(int, boolean)` / `stopBackgroundMusic()` | 背景音乐管理 |
| `pauseBackgroundMusic()` / `resumeBackgroundMusic()` | 暂停/恢复 |
| `release()` | 释放所有音效资源 |

#### ErrorReporter.java

错误上报器，双重检查锁定单例，频率限制 10 次/小时，网络失败时降级到本地文件存储。通过 `@Provides` 在 AppModule 中提供，构造函数中设置 `instance = this`，确保 Hilt 注入和 `getInstance()` 返回同一实例。使用共享 OkHttpClient 发送错误报告，不再每次创建新实例。

#### NetworkErrorHandler.java

网络错误统一处理工具类，定义 10 种错误码，支持指数退避重试。提供统一的 `isNetworkAvailable(Context)` 方法（兼容 API M+ 的 `NetworkCapabilities`），供 AiTaskRouter、GamesFragment 等模块共享。

| 错误码 | 值 | 说明 |
|--------|------|------|
| `ERROR_NETWORK_DISCONNECTED` | -1 | 网络断开 |
| `ERROR_TIMEOUT` | -2 | 超时 |
| `ERROR_DNS_RESOLUTION` | -3 | DNS 解析失败 |
| `ERROR_SERVER_5XX` | -4 | 服务器 5xx |
| `ERROR_SERVER_4XX` | -5 | 服务器 4xx |
| `ERROR_SSL` | -8 | SSL 错误 |

#### I18nHelper.java

国际化辅助，根据语言环境显示中文或英文 Toast。

#### SystemInfoCollector.java

系统信息采集器，收集设备标识、系统版本、硬件、CPU、内存、存储、显示、电池、GPU、Root 检测、传感器等全面信息。

#### SSLHelper.java

SSL 辅助类，Debug 构建信任所有证书便于开发，Release 构建仅设置 HostnameVerifier 保障安全。

---

### 5.9 Kotlin 工具层

#### AppDatabase.kt

Room 数据库定义，版本 1，数据库名 `gamecenter_database`。

| 实体 | 说明 |
|------|------|
| `AiMessageEntity` | AI 聊天消息 |
| `GameStatsEntity` | 游戏统计数据 |

| DAO | 说明 |
|-----|------|
| `aiMessageDao()` | AI 消息数据访问 |
| `gameStatsDao()` | 游戏统计数据访问 |

**获取方式**：`AppDatabase.getDatabase(context)` — 双重检查锁定单例

#### AppModule.kt

Hilt 依赖注入模块，标注 `@Module` + `@InstallIn(SingletonComponent::class)`。

**提供的依赖**（全部 `@Singleton`）：

| 依赖 | 类型 | 提供方式 | 说明 |
|------|------|---------|------|
| `ExecutorService` | CachedThreadPool | `@Provides` | 线程池 |
| `SettingsManager` | 单例 | `@Provides` (getInstance) | 应用设置 |
| `OkHttpClientProvider` | 单例 | `@Provides` (getInstance) | OkHttp 提供者 |
| `OkHttpClient` | 单例 | `@Provides` (从 OkHttpClientProvider) | HTTP 客户端 |
| `UpdateManager` | 单例 | `@Provides` (getInstance) | 更新管理 |
| `AiPreferences` | 单例 | `@Provides` | AI 偏好（EncryptedSharedPreferences） |
| `AppDatabase` | 单例 | `@Provides` | Room 数据库 |
| `AiMessageDao` / `GameStatsDao` | DAO | `@Provides` | 数据访问对象 |
| `SaveManager` | 单例 | `@Provides` (getInstance) | 存档管理 |
| `ErrorReporter` | 单例 | `@Provides` (getInstance) | 错误上报 |

**统一实例化策略**：所有单例类通过 `@Provides` 方法委托到 `getInstance()`，构造函数中设置 `instance = this`，确保 Hilt 注入和 `getInstance()` 返回同一实例。

#### Result.kt

函数式结果类型，Rust/Scala 风格的 Result/Either。

```kotlin
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}
```

**关键方法**：`map(transform)`、`onSuccess()`、`onError()`、`onLoading()`、`getOrNull()`、`getOrThrow()`、`Result.of(action)`、`Result.ofNullable(action)`

#### CrashHandler.kt

崩溃处理器，Kotlin object 单例，实现 `Thread.UncaughtExceptionHandler`。

| 组件 | 说明 |
|------|------|
| `CrashHandler` | 替换默认异常处理器，记录日志 → 上报 ErrorReporter → 通知监听器 → 传递给默认处理器 |
| `NamedThreadFactory` | 命名线程工厂 |
| `ThreadPools` | 线程池工具（IO/Network/Game 三类线程池） |
| `runCatchingResult(block)` | 安全执行返回 `Result` |

#### Extensions.kt

Kotlin 扩展函数集合。

| 组件 | 说明 |
|------|------|
| `AppLog` | 统一日志工具 (d/i/w/e) |
| `Context.showToast()` | Toast 扩展 |
| `withIO(block)` / `withMain(block)` | 协程调度器切换 |
| `tryOrNull(block)` / `tryOrDefault(default, block)` | 安全执行 |

#### LazyInitManager.kt

延迟初始化与性能监控。

| 组件 | 说明 |
|------|------|
| `LazyInitManager` | 空闲时初始化（主线程延迟 1s）、定时延迟初始化 |
| `PerformanceMonitor` | 计时追踪（超过 100ms 打印警告） |

#### MemoryUtils.kt

内存管理工具。

| 组件 | 说明 |
|------|------|
| `BitmapCache` | Bitmap 缓存（WeakReference + ConcurrentHashMap，上限 10MB，LRU 淘汰） |
| `MemoryMonitor` | 内存状态查询与 GC |

#### AccessibilityHelper.kt

无障碍辅助工具，提供内容描述、按钮角色、标题设置等扩展函数。

---

## 6. 游戏模块详解

### 6.1 游戏分类总览

| 类别 | 数量 | 游戏 |
|------|------|------|
| 经典 | 7 | 五子棋、围棋、中国象棋、贪吃蛇、俄罗斯方块、斗地主、Brotato |
| 益智 | 5 | 2048、数独、推箱子、接水管、华容道 |
| 休闲 | 5 | 打砖块、打地鼠、连连看、21点、跳棋 |
| 反应 | 5 | Flappy Bird、别踩白块、飞机大战、石头剪刀布、反应测试 |
| 其他 | 4 | 井字棋、记忆翻牌、猜数字、掷骰子 |

### 6.2 联机游戏详情

| 游戏 | 包名 | 单机 Activity | 在线 Activity | 协议前缀 | P2P_PREFS | 玩家数 |
|------|------|-------------|-------------|---------|-----------|--------|
| 五子棋 | `gomoku` | GomokuActivity | GomokuOnlineActivity | `GMK://` | `gomoku_p2p` | 2 |
| 围棋 | `go` | GoActivity | GoOnlineActivity | `GO://` | `go_p2p` | 2 |
| 中国象棋 | `chinesechess` | ChineseChessActivity | ChineseChessOnlineActivity | `XQ://` | `xiangqi_p2p` | 2 |
| 斗地主 | `doudizhu` | DouDiZhuActivity | DouDiZhuOnlineActivity | `DDZ://` | `doudizhu_p2p` | 2-3 |
| 石头剪刀布 | `rock` | RockActivity | RockOnlineActivity | `ROCK://` | `rock_p2p` | 2 |

### 6.3 AI 实现详解

#### 中国象棋 AI — ChineseChessAI

项目中最复杂的 AI 实现（约 1129 行），采用深度搜索型策略：

| 技术 | 说明 |
|------|------|
| 搜索 | 迭代加深 (Iterative Deepening) + PVS (Principal Variation Search) + 渴望窗口 (Aspiration Window) |
| 裁剪 | 空走裁剪 (Null Move Pruning) + 无效裁剪 (Futility Pruning) + LMR |
| 置换表 | Zobrist 哈希，1M 条目，EXACT/ALPHA/BETA 三种标志 |
| 走法排序 | TT 走法优先 + 杀手走法 + 历史启发 + 吃子排序 |
| 静止期搜索 | 只搜索吃子走法，避免水平线效应 |
| 评估函数 | 分段 PST + 机动性 + 子力协调 + 王安全 + 兵卒结构 + 阶段感知 |
| 难度 | 6 档（搜索时间 1s~10s），最大深度 24 |

#### 五子棋 AI — GomokuAI

搜索 + 威胁分析型 AI（约 391 行）：

| 技术 | 说明 |
|------|------|
| 搜索 | 迭代加深 + Minimax + Alpha-Beta 剪枝 |
| 威胁分析 | 活四/冲四/活三检测与评分，防守权重 1.18 倍 |
| 走法排序 | 候选点生成（已有棋子周围 2 格），按攻防综合评分排序 |
| 难度 | 6 档（搜索时间 500ms~10s），最大深度 10 |

#### 斗地主 AI — AIBot + DouDiZhuAIHelper

规则驱动型 AI（AIBot 约 789 行）：

| 技术 | 说明 |
|------|------|
| 手牌预处理 | 分类为王炸/炸弹/三条/对子/单牌/顺子/连对/三带一/三带一对 |
| 绝对保护 | 炸弹/王炸绝不拆开当单牌或对子 |
| 角色战术 | 顶牌（农民对地主）、放水（农民对农民）、极限防守 |
| 首发策略 | 优先出累赘牌（最小单牌→最小对子→三带一→顺子→炸弹） |
| 叫地主 | 评分阈值 >= 7 时叫地主 |

### 6.4 斗地主核心架构

斗地主是项目中最复杂的游戏，采用分层架构：

```
DouDiZhuMenuActivity (入口菜单)
  ├── DouDiZhuActivity (单机游戏)
  └── DouDiZhuOnlineActivity (联机游戏)

核心逻辑层:
  ├── DouDiZhuGameStateManager  — 游戏状态机 (LOBBY/BIDDING/PLAYING/GAME_OVER)
  ├── DouDiZhuRuleEngine        — 规则引擎 (验证出牌、叫地主评估)
  ├── GameRuleUtil              — 牌型识别与比较工具
  ├── DouDiZhuSeatManager       — 座位管理
  ├── DouDiZhuSyncManager       — 联机状态同步
  ├── DouDiZhuProtocol          — 消息协议定义
  ├── DouDiZhuNetworkHandler    — 网络事件处理
  ├── AIBot / DouDiZhuAIHelper  — AI 决策
  └── DouDiZhuSoundManager      — 音效管理

UI 层:
  ├── DouDiZhuTableView         — 牌桌自定义 View
  └── DouDiZhuUIController      — UI 控制器

数据模型层:
  ├── Card / CardType / Rank / Suit
  └── model/ 包
```

**斗地主协议消息类型**：JOIN、SEAT_ASSIGNED、HAND_CARDS、BID_REQUEST、BID_RESPONSE、GAME_START、REQUEST_PLAY、PASS、SYNC_STATE、GAME_OVER、CHAT

---

## 7. 联机架构详解

### 7.1 联机模式

所有联机游戏采用 **房主-客户端 (Host-Client)** 模式，支持三种连接方式：

| 模式 | 技术 | 说明 |
|------|------|------|
| 局域网 (LAN) | UDP 广播 + ServerSocket | 通过 `LANManager` 发现局域网内主机 |
| WebSocket 中继 | WSS (OkHttp WebSocket) | 通过 `GameSocketServer`/`GameSocketClient` + Node.js Relay |
| HTTP Relay 轮询 | HTTP POST 长轮询 (25s 超时) | 通过 `RelayHttpClient`，兼容性最好 |

### 7.2 WebSocket 同步机制

| 机制 | 说明 |
|------|------|
| 主机权威性 | 房主验证所有操作，客户端只负责发送输入 |
| 3 次冗余广播 | 立即 + 180ms + 600ms |
| STATE_ACK 确认 | 客户端收到 SYNC_STATE 后回复版本确认 |
| 自动重连 | 心跳 10s，超时 45s，容忍 2 次丢包 |
| 消息缓冲 | 断线期间消息自动缓冲（上限 32 条），重连后补发 |
| 状态版本 | 防止重复处理和消息乱序 |

### 7.3 联机数据流

```
┌──────────────┐    WSS (wss://hk-ws.<DOMAIN>/ddz-ws)    ┌──────────────┐
│   房主端      │ ─────────────────────────────────────────► │   Relay      │
│  (Android)   │ ◄───────────────────────────────────────── │  (Node.js)   │
└──────────────┘         消息转发 (基于 room 参数路由)      └──────────────┘
        ▲                                                             │
        │  操作请求 (MOVE/PLACE_STONE/THROW)                          │
        │                                                             ▼
┌──────────────┐         WSS (wss://hk-ws.<DOMAIN>/ddz-ws)     ┌──────────────┐
│   客户端      │ ──────────────────────────────────────────────► │   nginx      │
│  (Android)   │ ◄────────────────────────────────────────────── │  (WSS代理)   │
└──────────────┘         SYNC_STATE / GAME_OVER                  └──────────────┘
```

### 7.4 联机游戏共享模块

所有联机游戏共享 `com.gamecenter.app.network` 包中的基础设施：

| 类 | 角色 |
|----|------|
| `BaseOnlineActivity` | 联机 Activity 基类（创建/加入房间、聊天、连接管理） |
| `GameSocketServer` | 房主端服务器（3 种模式） |
| `GameSocketClient` | 客户端连接（3 种模式 + 自动重连） |
| `RelayHttpClient` | HTTP Relay 通信 + WebSocket URL 生成（已迁移 OkHttp） |
| `LANManager` | 局域网 UDP 广播发现 |
| `NetworkLogger` | 网络模块统一日志工具（GameSocketServer/Client 共享） |
| `RemoteP2PUtil` | 房间码生成/解析/验证 + P2P 令牌管理 |
| `OnlineChatHelper` | 联机聊天（弹窗/内联模式） |
| `OkHttpClientProvider` | OkHttpClient 单例（HTTP + WebSocket 客户端） |

---

## 8. 服务端组件

### 8.1 DDZ WebSocket 中继服务器

| 属性 | 值 |
|------|------|
| 文件 | `vps/ddz_ws_relay/server.js` |
| 技术栈 | Node.js + `ws` 库 |
| 监听 | `127.0.0.1:18080` |

**功能**：WebSocket 房间中继，房主-客户端消息转发

**HTTP 端点**：

| 端点 | 说明 |
|------|------|
| `GET /health` | 健康检查（房间数、运行时间） |
| `GET /stats` | 统计信息（房间数、运行时间、内存使用） |

**连接参数**：`?room=<房间码>&role=host|client&clientId=<ID>`

**房间管理**：自动清理 1 小时以上的空房间（每 10 分钟检查）

### 8.2 应用更新服务器

| 属性 | 值 |
|------|------|
| 文件 | `vps/var_www_update/update_server.py` |
| 技术栈 | Python 3 + `http.server.ThreadingHTTPServer` |
| 监听 | `127.0.0.1:9000` |

**API 端点**：

| 端点 | 说明 |
|------|------|
| `GET /api/update/check?versionCode=N&acceptBeta=true` | 版本检查（兼容旧版 App） |
| `GET /version-beta.json` | Beta 版元数据 |
| `GET /version-release.json` | 正式版元数据 |
| `GET /app-beta.apk` / `GET /app-release.apk` | APK 下载 |
| `GET /health` | 健康检查 |

**特性**：MD5 校验、APK 流式传输（1MB 分块）、CORS 支持、双版本文件名约定

### 8.3 斗地主 HTTP Relay 服务器

| 属性 | 值 |
|------|------|
| 文件 | `vps/var_www_update/ddz_relay/ddz_relay_server.py` |
| 技术栈 | Python 3 + `http.server.ThreadingHTTPServer` + `threading.Condition` |
| 监听 | `127.0.0.1:9012` |

**API 端点**：

| 端点 | 说明 |
|------|------|
| `POST /api/ddz-relay/create` | 创建房间（返回房间码 + hostToken） |
| `POST /api/ddz-relay/join` | 加入房间（支持 peerToken 重连） |
| `POST /api/ddz-relay/poll` | 长轮询消息（25 秒超时） |
| `POST /api/ddz-relay/send` | 发送消息 |
| `POST /api/ddz-relay/disconnect` | 客户端断开 |
| `POST /api/ddz-relay/close` | 房主关闭房间 |

**安全机制**：hostToken/clientToken 认证，消息队列上限 200 条，房间 TTL 6 小时

### 8.4 反馈服务器

| 属性 | 值 |
|------|------|
| 文件 | `vps/var_www_update/feedback/feedback_server.py` |
| 技术栈 | Python 3 + `http.server.ThreadingHTTPServer` + SQLite3 |
| 监听 | `127.0.0.1:9011` |

**API 端点**：

| 端点 | 说明 |
|------|------|
| `POST /api/feedback` | 提交反馈 |
| `GET /admin/feedback?token=xxx` | 管理页面 |
| `GET /health` | 健康检查 |

**数据存储**：SQLite + JSON + TXT 三重存储

### 8.5 错误报告服务器

| 属性 | 值 |
|------|------|
| 文件 | `vps/var_www_update/error_report.py` |
| 技术栈 | Python 3 + `http.server.ThreadingHTTPServer` |
| 监听 | `127.0.0.1:9012` |

**API 端点**：`POST /api/error` — 提交错误报告；`GET /health` — 健康检查

### 8.6 服务端口汇总

| 服务 | 端口 | 协议 | 用途 |
|------|------|------|------|
| DDZ WebSocket Relay | 18080 | WebSocket/HTTP | 实时中继 |
| Update Server | 9000 | HTTP | 版本检查与 APK 分发 |
| DDZ HTTP Relay | 9012 | HTTP | 长轮询中继 |
| Feedback Server | 9011 | HTTP | 反馈收集 |
| Error Report Server | 9012 | HTTP | 错误报告 |

---

## 9. 模块间依赖关系

### 核心依赖图

```
App ──► SettingsManager
    ──► ColorSchemeManager
    ──► OkHttpClientProvider
    ──► UpdateManager

MainActivity ──► UpdateManager
            ──► PermissionHelper
            ──► SettingsManager
            ──► App

GamesFragment ──► GameRegistry
              ──► GameUsageStore
              ──► ColorSchemeManager
              ──► Glide

ToolsFragment ──► ToolSectionStore
              ──► ToolBinder (26 个实现)
              ──► ToolHelper
              ──► ColorSchemeManager

BaseGameActivity ──► SoundManager
                 ──► SettingsManager

BaseOnlineActivity ──► GameSocketServer
                   ──► GameSocketClient
                   ──► LANManager
                   ──► RelayHttpClient
                   ──► RemoteP2PUtil
                   ──► OnlineChatHelper

GameSocketServer ──► RelayHttpClient
                ──► OkHttpClientProvider

GameSocketClient ──► RelayHttpClient
                ──► OkHttpClientProvider
                ──► RemoteP2PUtil

UpdateManager ──► UpdateInfo
             ──► SettingsManager
             ──► NetworkErrorHandler
             ──► SSLHelper
             ──► BuildConfig

AiTaskRouter ──► AiPreferences
             ──► AiApiClient (云端)
             ──► LocalAiProcessor (本地规则)
             ──► MediaPipeLocalLlmEngine (本地 LLM)
             ──► LocalLlmOutputGuard
             ──► AiModelDownloadManager

AppModule (Hilt) ──► SettingsManager
                ──► OkHttpClientProvider
                ──► UpdateManager
                ──► ErrorReporter
                ──► AiPreferences
                ──► AppDatabase
                ──► SaveManager

NetworkInitializer ──► OkHttpClientProvider

CrashHandler ──► ErrorReporter
             ──► AppLog
```

### 模块依赖矩阵

|  | SettingsManager | OkHttpClient | SoundManager | GameRegistry | ToolBinder | UpdateManager | NetworkModule | AiModule | Room DB |
|--|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| App | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| MainActivity | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ |
| GamesFragment | ✅ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| ToolsFragment | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| BaseGameActivity | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| OnlineActivity | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| AiTaskRouter | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| UpdateManager | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |

---

## 10. 构建与运行

### 环境要求

| 要求 | 版本 |
|------|------|
| JDK | 17+ |
| Android SDK | API 35 |
| Gradle | 8.13 (Wrapper) |
| Android Studio | 推荐最新版 |

### 首次构建

```bash
# 1. 克隆项目
git clone https://github.com/3571949306/GameCenterApp.git
cd GameCenterApp

# 2. 配置本地服务器地址
cp local.properties.template local.properties
# 编辑 local.properties，替换占位符

# 3. 编译调试版
.\gradlew.bat :app:assembleDebug

# 4. 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

### local.properties 配置

| 配置项 | 用途 | 必填 |
|--------|------|------|
| `server.url` | 更新检查服务器 (HK VPS) | 是 |
| `ws.url` | WebSocket 联机服务器 | 是 |
| `relay.url` | HTTP Relay 联机服务器 | 是 |
| `server.url.fallback` | 备用更新源 (US VPS) | 否 |
| `feedback.url` | 反馈服务器 | 否 |

> **注意**：修改 `local.properties` 后必须执行 **Build → Clean Project → Rebuild Project**，否则 `BuildConfig` 不会更新。

### 构建命令

| 场景 | 命令 |
|------|------|
| 日常编译 | `.\gradlew.bat :app:assembleDebug` |
| Release 编译 | `.\gradlew.bat :app:assembleRelease` |
| Beta 发布到 VPS | `.\gradlew.bat uploadReleaseArtifactsToVps` |
| 正式发布 (VPS + GitHub) | `.\gradlew.bat buildAndUploadToVpsAndGitHub -PupdateChannel=stable` |
| 运行单元测试 | `.\gradlew.bat :app:test` |
| 版本号递增 | `.\gradlew.bat bumpVersion` |
| 清理构建 | `.\gradlew.bat clean` |

### build.gradle 分区结构

`app/build.gradle` 按 7 个 Section 组织：

| Section | 说明 |
|---------|------|
| Section 1: Version Configuration | 版本号读取、local.properties 解析、通道判断 |
| Section 2: Helper Functions | changelog 读取、JSON 转义、version.json 生成 |
| Section 3: Android Configuration | SDK/签名/构建类型/Lint/编译选项/BuildConfig |
| Section 4: Dependencies | 全部依赖声明 + 安全版本约束 |
| Section 5: Version JSON Generation Tasks | `generateBundledVersionJson` + `generateVersionJson` |
| Section 6: Publish & Upload Tasks | VPS 上传 + GitHub Release 上传 |
| Section 7: Version Bump & Build Lifecycle | `bumpVersion` + `afterEvaluate` 自动化 |

### APK 签名

1. 创建密钥库：`keytool -genkey -v -keystore gamecenter.keystore ...`
2. 创建 `keystore.properties`（不提交 Git）：
   ```properties
   STORE_FILE=gamecenter.keystore
   STORE_PASSWORD=<your-store-password>
   KEY_ALIAS=gamecenter
   KEY_PASSWORD=<your-key-password>
   ```
3. Gradle 自动读取配置并签名 Release APK（启用 V1 + V2 签名方案）

### 版本管理

版本号由 `version.properties` 管理：

| 字段 | 说明 |
|------|------|
| `versionCode` | 内部版本号，每次构建自动递增 |
| `versionName` | 面向用户的版本号 |
| `lastStableVersionCode` | 上一个正式版内部版本号 |
| `lastStableVersionName` | 上一个正式版展示版本号 |
| `betaNoticeVersionGap` | Beta 提示版本差阈值（默认 3） |

### 双版本分发架构

VPS 上同时维护两个通道的文件：

```
/var/www/update/app/
├── app-beta.apk          # 测试版安装包
├── version-beta.json     # 测试版元数据
├── app-release.apk       # 正式版安装包
└── version-release.json  # 正式版元数据
```

**更新逻辑**：
- 用户开启"接收测试版"：先查 `/version-beta.json`，无更新再查 `/version-release.json`
- 用户关闭"接收测试版"：只查 `/version-release.json`，检测到 Beta 更新时提示开启

---

## 11. CI/CD

### GitHub Actions 配置

| 属性 | 值 |
|------|------|
| 配置文件 | `.github/workflows/ci.yml` |
| 触发条件 | push 到 main/master，或 PR 到 main/master |
| 运行环境 | `ubuntu-latest` |
| JDK 版本 | 21 (Temurin) |

**构建步骤**：

1. Checkout 代码
2. 设置 JDK 21
3. 赋予 gradlew 执行权限
4. 构建 Debug APK：`./gradlew clean assembleDebug -PautoBumpVersion=false`
5. 上传 Debug APK（保留 30 天）
6. 运行单元测试：`./gradlew test -PautoBumpVersion=false`
7. 上传测试报告（保留 7 天）

**注意事项**：
- CI 不构建 Release 包，避免暴露签名文件
- 统一添加 `-PautoBumpVersion=false`，避免自动修改 `version.properties`
- 正式签名、R8 混淆和 VPS/GitHub Release 发布仍以本机发布流程为准

---

## 12. 测试体系

### 单元测试

| 模块 | 测试文件 | 测试用例数 | 覆盖内容 |
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
| 斗地主规则引擎 | DouDiZhuRuleEngineTest | 40+ | 出牌验证、叫地主决策、清台判定、手牌评分 |
| 斗地主牌型工具 | GameRuleUtilTest | 60+ | 牌型识别、出牌比较、主权重、洗牌发牌、CardType 属性 |
| 更新管理逻辑 | UpdateManagerLogicTest | 40+ | URL 处理、版本比较、更新策略、Beta 通知、MD5、文件格式化 |
| AI 任务路由 | AiTaskRouterTest | 60+ | 任务模型、结果模型、提供商配置、命令识别、本地处理路由 |
| **总计** | **13+ 个文件** | **411+ 个用例** | |

### 其他测试

| 测试文件 | 类型 | 覆盖内容 |
|---------|------|---------|
| AiTaskRouterTest | 单元测试 | AI 任务路由逻辑 |
| RelayHttpClientTest | 单元测试 | HTTP Relay 通信 |
| UpdateInfoTest / UpdateInfoBasicTest | 单元测试 | 版本信息解析 |
| ResultTest | 单元测试 | Result 类型功能 |
| AiIntegrationTest | 集成测试 | AI 功能集成 |
| DouDiZhuIntegrationTest | 集成测试 | 斗地主联机集成 |
| UpdateIntegrationTest | 集成测试 | 更新流程集成 |
| RoomDatabaseIntegrationTest | 集成测试 | 数据库集成 |

### 运行测试

```bash
# 运行所有单元测试
.\gradlew.bat :app:test

# 运行特定测试
.\gradlew.bat :app:testDebugUnitTest --tests "com.gamecenter.app.games.gomoku.GomokuGameTest"

# 运行集成测试
.\gradlew.bat :app:connectedAndroidTest
```

---

## 13. 设计模式与架构决策

### 使用的设计模式

| 模式 | 应用位置 | 说明 |
|------|---------|------|
| 单例 | SettingsManager, SaveManager, UpdateManager, GameSocketClient, OkHttpClientProvider | 全局唯一实例（部分已迁移到 Hilt `@Singleton`） |
| 模板方法 | BaseGameActivity | 定义游戏生命周期骨架，子类实现具体逻辑 |
| 策略 | ToolBinder 接口, GameSocketServer 三种通信模式, GameLogic 接口 | 可替换的算法/行为 |
| 观察者 | 网络模块回调接口, Activity 生命周期 | 事件通知机制 |
| 注册表 | GameRegistry | 集中管理游戏条目 |
| 工厂方法 | UpdateInfo.fromJson(), ColorSchemeManager.getScheme() | 对象创建 |
| 责任链 | AiTaskRouter (本地→云端), CrashHandler (异常处理链) | 逐级处理 |
| 适配器 | GamesFragment.GameAdapter, ToolsFragment.ToolsAdapter | 数据-视图桥接 |
| 门面 | SoundManager (封装 SoundPool + MediaPlayer) | 简化子系统接口 |
| 值对象 | GameRegistry.Entry, UpdateInfo, ColorSchemeManager.Scheme | 不可变数据 |
| 依赖注入 | Hilt (AppModule) + @Inject 构造函数 | 解耦依赖关系，渐进式迁移中 |
| 回调 | UpdateCheckCallback, DownloadCallback, AiCallback | 异步结果通知 |
| 状态机 | GameSocketClient.ConnectionState, DouDiZhuGameStateManager | 状态转换管理 |
| 接口契约 | GameLogic, OnlineGameLogic | 游戏逻辑与 UI 分离的统一契约 |
| Builder | AiResult, AiProviderConfig, AiModelInfo | 复杂对象构建 |

### 架构决策

| 决策 | 理由 |
|------|------|
| Java 主体 + Kotlin 工具层 | 历史项目 Java 为主，新功能用 Kotlin 编写 |
| Hilt DI 统一实例化 | 所有单例类通过 `@Provides` 委托到 `getInstance()`，构造函数设置 `instance = this`，确保 Hilt 注入和静态方法返回同一实例 |
| 本地优先 AI 策略 | 减少云端依赖，保护用户隐私，降低 API 成本 |
| 三模式联网 | 兼顾局域网无服务器场景和云联机需求 |
| 三级下载源降级 | 保证更新可用性，自动切换最优源 |
| 双版本分发 (Beta/Stable) | 测试版与正式版隔离，灵活控制发布节奏 |
| SharedPreferences 存储设置 | 轻量级，适合键值对配置 |
| Room 数据库存储 AI 消息和统计 | 结构化数据，支持复杂查询 |
| OkHttp 统一管理 | 共享连接池、缓存、重试策略，减少内存占用 |
| R8 代码混淆 | APK 体积减小约 30%，保护代码 |
| SSLHelper Debug/Release 区分 | Debug 信任所有证书便于开发，Release 仅设置 HostnameVerifier 保障安全 |
| 房间码字母数字混合 | 与服务端 `ROOM_CODE_ALPHABET` 一致，密钥空间从 10^6 扩展到 32^6 |
| GameLogic 接口契约 | 新游戏遵循统一接口，逐步分离游戏逻辑与 UI |
| 离线体验 | GamesFragment 离线提示 + AiTaskRouter 离线检测，避免无意义网络请求 |
| build.gradle 分区注释 | 7 个 Section 注释提升构建脚本可读性 |
| App Startup 延迟初始化 | OkHttpClient 不再在 App.onCreate 同步初始化，避免阻塞启动 |
| 请求去重拦截器 | 防止短时间内重复请求同一 URL，减少无效网络流量 |

---

## 14. 线程模型与并发安全

### 线程池使用

| 组件 | 线程池类型 | 用途 |
|------|-----------|------|
| `AiTaskRouter` | `Executors.newSingleThreadExecutor()` | AI 任务串行执行 |
| `UpdateManager` | `Executors.newSingleThreadExecutor()` | 更新检查/下载串行执行 |
| `GameSocketServer` | `Executors.newFixedThreadPool(4)` | 客户端连接处理 |
| `GameSocketServer` | `Executors.newSingleThreadExecutor()` (daemon) | 消息发送 |
| `GameSocketServer` | `Executors.newScheduledThreadPool(1)` | 心跳检测 |
| `GameSocketClient` | `Executors.newSingleThreadExecutor()` (daemon) | 消息发送 |
| `GameSocketClient` | `Executors.newScheduledThreadPool(1)` | 心跳/重连 |
| `AppModule` | `Executors.newCachedThreadPool()` | Hilt 提供的通用线程池 |
| `ThreadPools.IO` | 固定线程池 | IO 密集操作 |
| `ThreadPools.Network` | 固定线程池 | 网络操作 |
| `ThreadPools.Game` | 固定线程池 | 游戏计算 |

### 并发安全机制

| 机制 | 应用位置 |
|------|---------|
| `ConcurrentHashMap` | GameSocketServer.clients, relayKnownClients |
| `ConcurrentLinkedQueue` | GameSocketClient.pendingMessages |
| `Collections.synchronizedList` | LANManager.discoveredHosts, OnlineChatHelper.messages |
| `volatile` | GameSocketServer.isRunning, GameSocketClient.state/manualDisconnect |
| `synchronized` | 单例 getInstance(), GameSocketClient.handleDisconnection() |
| `Handler(Looper.getMainLooper())` | 所有网络回调切回主线程 |

### 资源释放

所有游戏 Activity 在 `onDestroy` 中正确释放：
- `SoundManager.release()` — 释放音效资源
- `GameSocketServer.stop()` — 关闭所有连接和线程池
- `GameSocketClient.release()` — 断开连接 + 关闭线程池
- `ExecutorService.shutdownNow()` — 立即停止待执行任务

---

## 15. 安全模型

### 权限声明

| 权限 | 用途 | 必需 |
|------|------|------|
| `INTERNET` | 网络通信 (更新/联机/AI) | 是 |
| `ACCESS_NETWORK_STATE` | 网络状态检测 | 是 |
| `ACCESS_WIFI_STATE` | WiFi 信息 (工具箱) | 是 |
| `CHANGE_WIFI_MULTICAST_STATE` | 局域网发现 | 是 |
| `ACCESS_FINE_LOCATION` | WiFi/局域网功能 | 否 |
| `ACCESS_COARSE_LOCATION` | IP 归属地 | 否 |
| `CAMERA` | 二维码扫描 | 否 |
| `READ_EXTERNAL_STORAGE` | 文件哈希计算 | 否 |
| `WRITE_EXTERNAL_STORAGE` | APK 下载 | 否 |
| `REQUEST_INSTALL_PACKAGES` | 应用内更新安装 | 否 |

### 网络安全

- `network_security_config.xml`：默认禁止明文 HTTP，仅局域网 IP 段允许明文
- `usesCleartextTraffic`：已移除，由 `network_security_config.xml` 统一管控
- `SSLHelper`：不再修改全局默认 SSL 设置，改为 per-connection 应用（`applySsl()`）；Debug 模式信任白名单主机，Release 模式叠加系统证书校验
- WebSocket Token 传输：同时通过 URL 参数和 `Authorization: Bearer` Header 传递，服务端优先读取 Header
- `RequestDeduplicationInterceptor`：防止请求重放
- HTTP 客户端统一：`RelayHttpClient`、`GamesFragment.postFeedbackJson()`、`DouDiZhuOnlineActivity.fetchPublicIp()` 已从 `HttpURLConnection` 迁移到 OkHttp，共享连接池、重试策略和超时管理

### 数据安全

- `keystore.properties` 和 `gamecenter.keystore` 已加入 `.gitignore`
- `local.properties` 已加入 `.gitignore`（含服务器 URL 和密钥）
- API Key 使用 `EncryptedSharedPreferences` 加密存储（`ai_settings_encrypted`），自动从旧明文存储迁移
- Peer Token 使用 `EncryptedSharedPreferences` 加密存储（`enc_` 前缀文件名），自动从旧明文存储迁移
- APK 下载使用 FileProvider URI，安全共享文件

---

## 16. 国际化与主题系统

### 国际化

| 语言 | 资源目录 | 覆盖范围 |
|------|---------|---------|
| 中文 (默认) | `values/strings.xml` | 完整 |
| English | `values-en/strings.xml` | 完整 |

**语言切换**：`SettingsManager.setAppLanguage()` → `AppCompatDelegate.setApplicationLocales()` → 应用重启生效

**AI 任务类型国际化**：AI 任务下拉使用资源字符串，切换 English 后显示 Chat/Summary/Translate 等英文选项

### 主题系统

| 主题模式 | 常量 | 说明 |
|---------|------|------|
| 跟随系统 | `THEME_SYSTEM (0)` | `AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM` |
| 浅色 | `THEME_LIGHT (1)` | `AppCompatDelegate.MODE_NIGHT_NO` |
| 深色 | `THEME_DARK (2)` | `AppCompatDelegate.MODE_NIGHT_YES` |

**配色方案**：8 套预定义配色（`ColorSchemeManager`），通过 `SettingsManager.setColorSchemeIndex()` 切换

**暗色模式资源**：`values-night/colors.xml` + `values-night/themes.xml`

### 动画资源

| 动画 | 文件 | 用途 |
|------|------|------|
| `fade_in` / `fade_out` | 淡入淡出 | 页面过渡 |
| `slide_in_right` / `slide_out_left` | 滑动 | 页面切换 |
| `button_press` | 缩放 | 按钮点击反馈 |
| `win_celebrate` | 缩放旋转 | 胜利庆祝 |
| `scale_up` | 弹出 | 卡片/对话框显示 |

---

> 文档生成时间：2026-05-16 · 基于 versionCode 257 (v1.3.26) · 最后更新：2026-05-17
