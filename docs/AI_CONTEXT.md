<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# AI 上下文文档 - GameMatrix App

> 本文档合并了原 `AI_CONTEXT.md`、`PROJECT_CONTEXT.md`、`AI_ONBOARDING.md`、`项目AI接手说明.md` 四个文档，供 AI 编程助手和新开发者阅读。

**最后更新**: 2026-07-23
**当前工作树/生产版本**: versionCode=599, versionName=1.4.1
**上次稳定版**: versionCode=599, versionName=1.4.1

---

## 1. 项目概览

| 项目 | 说明 |
|------|------|
| 名称 | GameMatrixApp（夹层） |
| 类型 | Android 游戏中心应用 |
| 包名 | `com.gamecenter.app` |
| 语言 | Java 17 (约 55%) + Kotlin 2.0.21 (约 45%，循环23宿主 Kotlin 迁移后) |
| 构建 | Gradle 8.13 + AGP 8.13.2 |
| SDK | minSdk 24, targetSdk 35, compileSdk 36 |
| DI | Hilt 2.57.2 |
| 数据库 | Room 2.7.1（浏览器模块 v2 schema 4 张表） |
| 网络 | OkHttp 4.12.0 |
| GitHub | `https://github.com/3571949306/GameMatrixApp.git`（当前工作树包含用户并行修改，不能假定 clean） |
| 当前文档真值 | 实时代码、Gradle、APK、真机和 logcat；旧 commit/发布说明仅作历史参考 |

### 核心功能
- 28 款内置经典游戏（经典/益智/休闲三大类）
- 模块商店：按需下载扩展游戏、工具箱、浏览器、AI 助手、VPN
- 单机 AI 对战 + 局域网联机 + 云联机
- 自动更新（香港 VPS + GitHub Releases 双源）

---

## 2. 当前状态（2026-07-22）

### Flutter-first 模块商店

- `flutter_module/` 已通过 Add-to-App 接入；Flutter 负责模块商店 UI、路由与 UI 状态。
- Pigeon 调用 `ModuleCoreFacade`，Android 继续独占目录信任、下载、SHA-256、签名、安装、启停、回滚和 Runtime 生命周期。
- 支持 `flutter/web/asset/android/native_service/unity` 六类 Runtime 框架；`ModuleStoreActivity` 在开关启用时直接承载 Flutter Fragment，关闭、失败或显式强制旧商店时渲染原生 UI。
- 启用参数：`-PenableFlutterModuleStore=true`；源码默认值保留 false 作为安全回退，stable vc595 已用生产参数启用。Flutter Release 必须同时启用 Catalog 验签。
- Flutter UI、客户端、六类 Runtime、Android 11–15 矩阵和生产发布闭环均为 100%；stable vc595、可定制宿主底部导航与签名 Catalog V8 已上线，以 `docs/flutter-store/MIGRATION_STATUS.md` 为准。

### 当前验证状态

```text
✅ Flutter analyze / 6 个 Flutter tests
✅ Android 全量单测、lint、Debug assemble
✅ lintVitalRelease、R8、资源收缩、APK v2 签名和 ARM64/x86_64 双 ABI staging Release
✅ Android 11/API 30、12/API 31、14/API 34 各 20 次签名 Release 进出；Android 15/API 35 最新 Release 40 次；Android 13 真机 Debug 80 次
✅ 目标流无 FATAL / Flutter channel 错误；强制验签正确拒绝无签名远端并保留缓存
✅ 线上签名 Catalog V8、多 Runtime 正式包、生产灰度与 stable vc595 已完成；底部导航排序/隐藏及 Flutter 商店升级路径已在 API 35 验证。
⚠️ 工作树包含本任务及用户并行的游戏 AI/模块修改
```

## 2A. 2026-07-06 历史状态

### 构建状态
```
✅ 当时的 Debug/Release 构建记录
✅ R8 混淆已启用
✅ ABI splits: arm64-v8a
✅ Lint 严格模式（abortOnError true）
✅ 证书绑定: Release 启用
⚠️ “工作区干净”仅是当时记录；当前必须以实时 `git status` 为准
```

### 循环 17-24 维护记录（2026-07-06）
- **循环 17-19**：浏览器循环19重构为原生实现，新增 `browser/{bridge,core,data,security,ui}/` 包结构，Room 数据库（4 张表），AdBlocker/DomainTrustManager/JsBridgePolicy 安全模块
- **循环 20**：wrongbook 模块预装集成（`assets/modules/feature_wrongbook_v100.apk`）
- **循环 21-22**：错题本全面推进（Room v2 schema、自定义图表 View、科目管理、复习计划、数据导入导出）
- **循环 23**：宿主 Kotlin 迁移完成（`App.java`/`MainActivity.java`/`GameRegistry.java` → `.kt`，位于 `app/src/main/kotlin/com/gamecenter/app/`）；新增 `core/moduleloader/.../ModuleContextHelper.kt`；新增 `.github/workflows/android_ci.yml` 和 `.github/dependabot.yml`
- **循环 24**：Netty 4.1.134.Final → 4.1.135.Final，修复 7 个 CVE（3 high + 4 medium），GitHub Dependabot 0 open / 7 dismissed

### 适配状态
```
✅ Android 11-17 全版本适配
✅ SplashScreen API (Android 12+)
✅ POST_NOTIFICATIONS (Android 13+)
✅ registerReceiver export 标志 (Android 14+)
✅ Edge-to-Edge (Android 15+)
✅ 屏幕方向锁定 (Android 16+)
```

### 测试状态
```
✅ 145 个 UI 自动化测试用例全部通过
✅ 14 个单元测试文件
✅ 47 个 Android 测试文件
```

---

## 3. 服务器架构

### 3.1 VPS 列表

| VPS | 用途 | 域名前缀 |
|-----|------|----------|
| 香港 VPS | 主更新源 + WebSocket Relay + HTTP Relay | `hk-*` |
| ~~美国 VPS~~ | ~~备用~~（2026-06-19 已下线） | ~~无~~ |

### 3.2 主 VPS 服务

| 服务 | 域名 | 用途 |
|------|------|------|
| APK 更新 | `hk-update.<DOMAIN>` | 应用更新分发 |
| WebSocket | `hk-ws.<DOMAIN>` | 云联机 Relay |
| HTTP Relay | `hk-relay.<DOMAIN>` | 云联机 HTTP |

### 3.3 更新分发架构

```
优先级 1: 香港 VPS (连接 2s/读取 3s)
    ↓ (失败或速度 <30KB/s)
优先级 2: GitHub Releases (连接 5s/读取 15s)
```

---

## 4. 代码结构

```
GameMatrixApp/
├── app/                          # 主应用
│   ├── src/main/java/            # Java 源码（112 个文件）
│   ├── src/main/kotlin/          # Kotlin 源码（18 个文件）
│   ├── src/main/res/             # 资源文件
│   ├── src/main/assets/          # modules.json, game_configs.json
│   ├── src/test/                 # 单元测试（14 个文件）
│   ├── src/androidTest/          # UI 自动化测试（47 个文件）
│   ├── build.gradle              # 应用构建配置
│   └── proguard-rules.pro        # R8 混淆规则
├── core/                         # 核心模块
│   ├── common/                   # 通用基础模块
│   ├── network/                  # 网络模块
│   ├── update/                   # 更新模块
│   ├── security/                 # 安全模块（证书绑定）
│   ├── module-host/              # 模块宿主框架
│   ├── moduleloader/             # 模块加载引擎
│   ├── modulestore/              # 模块商店
│   └── online/                   # 联机公共模块
├── module-store/feature/         # 功能模块
│   ├── games/                    # 游戏模块
│   └── tools/                    # 工具模块（browser, tools, ai, vpn）
├── docs/                         # 文档
├── scripts/                      # 构建脚本
├── tools/                        # 工具脚本
├── version.properties            # 版本配置
├── local.properties              # 本地配置（不提交 Git）
└── keystore.properties           # 签名配置（不提交 Git）
```

---

## 5. 关键文件索引

### 5.1 应用入口

| 文件 | 职责 |
|------|------|
| `App.kt` | Application 入口，全局初始化（循环23由 `App.java` 迁移至 Kotlin，路径 `app/src/main/kotlin/com/gamecenter/app/App.kt`） |
| `MainActivity.kt` | 主界面，底部导航 + 更新检查（循环23由 `MainActivity.java` 迁移至 Kotlin） |
| `SplashActivity.java` | 启动页（已适配 SplashScreen API） |
| `SettingsManager.java` | SharedPreferences 封装 |
| `PermissionHelper.java` | 权限管理（含 POST_NOTIFICATIONS） |

### 5.2 游戏系统

| 文件 | 职责 |
|------|------|
| `GameRegistry.kt` | 游戏注册中心（静态 + @GameEntry + 动态，循环23由 `GameRegistry.java` 迁移至 Kotlin，路径 `app/src/main/kotlin/com/gamecenter/app/games/GameRegistry.kt`） |
| `BaseGameActivity.java` | 游戏基类（屏幕方向锁定） |
| `games/gomoku/` | 五子棋（UI/AI/音效/状态保存） |
| `games/doudizhu/` | 斗地主（单机 + 三模联机） |
| `games/chinesechess/` | 中国象棋 |
| `games/go/` | 围棋 |

### 5.3 模块系统

| 文件 | 职责 |
|------|------|
| `ModuleManager.kt` | 模块管理器（下载/安装/卸载） |
| `ModuleLoaderV2.java` | 模块加载引擎（DexClassLoader） |
| `ModuleDownloader.kt` | 模块下载器（多源/SHA-256 校验） |
| `ModuleResourceLoader.kt` | 模块资源加载（反射私有 API） |
| `ModuleStoreActivity.kt` | 模块商店界面 |
| `ModuleContextHelper.kt` | 模块上下文辅助（循环23新增，位于 `core/moduleloader/`） |

### 5.4 动态模块（共 9 个，循环 20 起）

| 模块 | 路径 | 说明 |
|------|------|------|
| hall | `module-store/feature/games/games/hall` | 游戏大厅容器 |
| chinesechess | `module-store/feature/games/games/chinesechess` | 中国象棋 |
| game2048 | `module-store/feature/games/games/game2048` | 2048 |
| klotski | `module-store/feature/games/games/klotski` | 华容道 |
| tts | `module-store/feature/games/games/tts` | TTS 语音合成 |
| ai | `module-store/feature/tools/ai` | AI 智能助手 |
| tools | `module-store/feature/tools/tools` | 工具箱 |
| vpn | `module-store/feature/tools/vpn` | 科学上网 VPN |
| wrongbook | `module-store/feature/tools/wrongbook` | 错题本（循环20预装集成，`assets/modules/feature_wrongbook_v100.apk`） |

### 5.5 更新系统

| 文件 | 职责 |
|------|------|
| `UpdateChecker.java` | 更新检查器（多源自动切换） |
| `OptimizedUpdateManager` | 缓存、重试、MD5 预检查 |
| `UpdateInstaller.java` | APK 安装器（FileProvider URI） |
| `UpdateViewModel.kt` | 更新 ViewModel（@HiltViewModel） |

### 5.6 安全模块

| 文件 | 职责 |
|------|------|
| `SecureOkHttpFactory.kt` | 证书绑定开关（Release 启用） |
| `SSLHelper.java` | SSL 信任管理 |
| `browser/security/AdBlocker` | 浏览器广告拦截（循环19新增） |
| `browser/security/DomainTrustManager` | 域名信任管理（循环19新增） |
| `browser/security/JsBridgePolicy` | JS Bridge 安全策略（循环19新增） |

### 5.7 联机模块

| 文件 | 职责 |
|------|------|
| `GameSocketServer.java` | 房主权威服务器 |
| `GameSocketClient.java` | 客户端连接管理 |
| `RelayHttpClient.java` | HTTP Relay + WebSocket |
| `OnlineRoomManager.java` | 联机房间管理器 |

---

## 6. 游戏列表（28 款内置）

### 经典类（8 款）
五子棋、中国象棋、围棋、斗地主、21点、跳棋、骰子、石头剪刀布

### 益智类（10 款）
2048、数独、华容道、推箱子、管道、扫雷、消消乐、记忆翻牌、打砖块、拼图

### 休闲类（9 款）
俄罗斯方块、贪吃蛇、Flappy Bird、Brotato、飞机大战、反应测试、猜数字、井字棋、打地鼠

### 特殊
斗地主支持三模联机（TCP + HTTP Relay + WebSocket）

---

## 7. 构建与开发

### 7.1 构建前提
- JDK 17（Android Studio 自带 JBR）
- Android SDK 35
- `keystore.properties`（Release 构建需要）

### 7.2 常用命令
```bash
# Debug 构建
.\gradlew.bat :app:assembleDebug

# Release 构建
.\gradlew.bat :app:assembleRelease

# 单元测试
.\gradlew.bat :app:test

# Lint 检查
.\gradlew.bat :app:lintDebug

# UI 自动化测试
adb shell am instrument -w -r -e class com.gamecenter.app.tests.* com.gamecenter.app.test/androidx.test.runner.AndroidJUnitRunner

# 清理构建
.\gradlew.bat clean
```

### 7.3 Git 操作
```bash
# 代理配置（GitHub 需要）
git config --global http.https://github.com.proxy http://127.0.0.1:10808

# 基本操作
git add .
git commit -m "说明"
git push origin main
```

### 7.4 发布流程
```bash
# 上传到 VPS
python tools/upload_to_vps.py --channel release

# 上传到 GitHub Releases
python tools/upload_to_github_release.py --apk app/build/outputs/apk/release/app-release.apk --metadata app/build/outputs/apk/release/version.json --version-name 1.4.1 --version-code 599 --changelog-file RELEASE_NOTES.md
```

---

## 8. 配置文件说明

### 8.1 version.properties
```properties
versionCode=599          # 内部版本号（每次构建自动递增）
versionName=1.4.1        # 展示版本号
lastStableVersionCode=599
lastStableVersionName=1.4.1
betaNoticeVersionGap=3
```

### 8.2 local.properties（不提交 Git）
```properties
server.url=https://hk-update.<DOMAIN>
ws.url=wss://hk-ws.<DOMAIN>/ddz-ws
relay.url=https://hk-relay.<DOMAIN>/api/ddz-relay
feedback.url=https://<DOMAIN>/api/feedback
```

### 8.3 BuildConfig 字段
- `SERVER_URL`: 主更新源 URL
- `MODULES_URL`: 模块清单 URL
- `DOWNLOAD_BASE_URL`: 模块下载基础 URL
- `TEST_MODE`: 测试模式开关（Debug 默认 true）

#### Feature Flag（加法升级，2026-07-19）
九轮加法升级共启用 37 个 Feature Flag，全部默认开启，可在 `app/build.gradle` 中按需关闭：

**第一轮（4 个）**：
- `ACHIEVEMENT_V2`: 成就中心新增"每日挑战"+"连胜概览"卡片
- `SETTINGS_ENHANCE`: 设置弹窗新增字号选择 + 缓存清理
- `HOME_REVAMP`: 游戏大厅首页新增"最近游玩"横向滚动条
- `VISUAL_REFRESH`: 全局视觉打磨：游戏卡片图标渐变背景 + 底部导航选中胶囊指示器

**第二轮（4 个）**：
- `HOME_CARD_ENHANCE`: 顶栏渐变美化 + 收藏/评分/热门徽章激活 + 图标点击动效
- `GAME_STATS_DASHBOARD`: 游戏统计仪表盘（总时长/活跃天数/Top5/胜负/成就概览）
- `THEME_SWITCHER`: 多主题色切换（补全已存在 UI 的"最后一公里"：持久化+应用+重建）
- `SPLASH_ANIMATION_ENHANCE`: 启动动画增强（波纹扩散动画 + 深色模式启动屏背景修复）

**第三轮 Batch 5/6（5 个）**：
- `DAILY_CHECKIN`: 每日签到（头像菜单入口 + DialogFragment 签到弹窗 + 累计签到天数）
- `NOTIFICATIONS_CENTER`: 通知中心（顶栏通知按钮打开 NotificationsDialog）
- `PROFILE_FRAGMENT`: 个人中心 Fragment（底部导航"Me"项）
- `HOME_DAILY_CARDS`: 首页每日卡片（每日挑战 + 连胜概览双卡片）
- `NAV_ACTIVE_ANIM`: 底部导航激活动画（选中 item 图标缩放 1→1.25→1, 220ms）

**第四轮 Batch 7（4 个）**：
- `GAME_DETAIL_SHEET`: 游戏详情 BottomSheet（点击卡片弹出详情面板：图标+名称+分类+描述+战绩+收藏+立即开始）
- `ANIM_SHIMMER_LOADING`: 首页骨架屏加载（自实现 ObjectAnimator alpha 脉冲，6 个占位卡片，600ms 后隐藏，避免引入 shimmer 第三方依赖）
- `EMPTY_STATE_ILLUSTRATION`: 空状态精美插图（搜索/收藏/默认三种状态显示对应 VectorDrawable 插图：放大镜/心形/游戏手柄 + 标题 + 副标题 + 清除按钮）
- `SETTINGS_ABOUT_PAGE`: 设置-关于页面（AboutDialog：Logo+应用名+版本卡片+GitHub 卡片+开源许可卡片+复制版本信息+检查更新按钮）

**第五轮 Batch 8（4 个）**：
- `SEARCH_HISTORY_CHIPS`: 搜索历史 Chip 流（搜索框下方最近 5 条历史，SharedPreferences 持久化，一键清空，`SearchHistoryManager.kt`）
- `CARD_TILT_ANIM`: 卡片按压倾斜动效（MaterialCardView 按下抬升阴影+放大 1.03 倍，松开 OvershootInterpolator 回弹，`CardTiltHelper.kt`）
- `ACHIEVEMENT_TOAST`: 成就解锁顶部 Toast（自定义 AchievementToastView，280ms 滑入+3 秒+240ms 滑出，橙红渐变背景）
- `HOME_HERO_BANNER`: 首页英雄横幅轮播（3 张横幅：每日精选/活动进行中/连胜挑战，PagerSnapHelper+4 秒自动轮播+指示器，`HeroBannerAdapter.kt`）

**第六轮 Batch 9（4 个）**：
- `GAME_LONG_PRESS_MENU`: 游戏卡片长按菜单（PopupMenu 4 项：立即开始/收藏切换/分享/添加桌面快捷方式，`GameLongPressMenu.kt` + `ShortcutResultReceiver`）
- `HOME_PULL_REFRESH`: 首页下拉刷新（SwipeRefreshLayout 包裹 rv_games，4 段主题色进度环，刷新重载游戏列表+顶栏数据）
- `ACHIEVEMENT_DETAIL_PAGE`: 单游戏成就详情页（成就中心长按游戏卡片跳转，显示稀有度色块+筛选 Chip+总进度条，`AchievementDetailActivity.kt`，`@JvmStatic` 保证 Java 互操作）
- `NAV_BADGE_UNREAD`: 底部导航未读徽章（"游戏大厅" tab 显示 BadgeDrawable，未读数=每日挑战未完成+今日未签到+连胜为 0，`NavBadgeHelper.kt`）

**第七轮 Batch 10（4 个，2026-07-18）**：
- `HOME_QUICK_STATS_BAR`: 首页快速统计栏（"今日时长+连胜+成就数"3 列渐变卡片，`GameUsageStore.getTodayPlayTimeMs()` 读取 `daily_play_time_yyyy-MM-dd` 键，`layout_home_quick_stats.xml` + `bg_quick_stats_gradient.xml`）
- `HOME_GAME_OF_DAY`: 首页"今日推荐"卡片（基于日期 Calendar 日历 dayKey hash % allEntries.size() 轮换游戏，`layout_home_game_of_day.xml` + `bg_game_of_day_gradient.xml` + `bg_game_of_day_btn.xml`，含"立即开始"按钮）
- `RANDOM_GAME_FAB`: 随机游戏悬浮按钮（FloatingActionButton 骰子图标 `ic_fab_dice.xml`，`Math.random()` 从全部 entries 随机启动一款游戏）
- `ACHIEVEMENT_PROGRESS_RING`: 成就中心圆环进度头部（`AchievementProgressRingView.kt` 自定义 View，SweepGradient 渐变环+中心百分比+等级文案，5 档：Beginner/Explorer/Expert/Master/Legend，180dp×180dp）

**第八轮 Batch 11（4 个，2026-07-19）**：
- `GAME_RATING_SYSTEM`: 游戏 5 星用户评分（`GameRatingStore.java` 用 `game_ratings` SharedPreferences 存 `rating_<gameId>` 1-5 整数，卡片右上角金色星徽章 + 详情页 BottomSheet RatingBar）
- `DATA_BACKUP_RESTORE`: 数据备份与恢复（`DataBackupHelper.kt` 通过 SAF `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`，`ActivityResultLauncher` 在 Fragment 构造期注册）
- `HOME_PLAYTIME_REMINDER`: 首页今日时长超阈值显示"休息提醒"警示卡片（`PlaytimeReminderHelper.kt` 读取 `GameUsageStore.getTodayPlayTimeMs()`，超 30 分钟显示渐变警示卡片）
- `GAME_FAVORITE_REORDER`: 收藏置顶（`GameFavoriteReorderHelper.kt` 排序 Comparator，设置面板提供开关，开启后已收藏游戏在列表中自动排在前面）

**第九轮 Batch 12（4 个，2026-07-19）**：
- `HOME_RESUME_GAME_CARD`: 首页"继续上次游玩"卡片（`ResumeGameHelper.kt` 单例读取 `RecentGamesManager.getRecentIds()[0]` 匹配 `GameRegistry.Entry`；时间显示用 `GameUsageStore.getLastPlayedAt()` + `System.currentTimeMillis()` 计算 "刚刚 / N 分钟前 / N 小时前 / N 天前"；点击"继续"按钮直接启动游戏）
- `ACHIEVEMENT_RECENT_UNLOCKED_BANNER`: 首页"最近解锁成就"横幅（`RecentAchievementHelper.kt` 遍历 `game_achievements` SharedPreferences 查找 `unlock_<id>=true` 且 `unlocked_at_<id>` 最大的项；dismiss 写入 `home_recent_achievement_session` SharedPreferences 当日 key，当日不再显示）
- `GAME_PLAY_TIME_BADGE`: 游戏卡片右上角总游玩时长徽章（`GameCardAdapter.onBindViewHolder` 末尾读取 `GameUsageStore.getTotalPlayTimeMs(gameId)`，按 `>=3600000 → "X.Xh"` / `>=60000 → "Xm"` / `>=1000 → "Xs"` 格式化，半透明绿色 `bg_play_time_badge.xml`）
- `APP_LAUNCH_TIME_DISPLAY`: 启动耗时显示（`LaunchTimeTracker.kt` 单例使用 `SystemClock.elapsedRealtime()` 避免系统时间被修改导致负值；`SplashActivity.onCreate` 第一行调用 `markStart()`；`GamesFragment.initTopBar` 中读取 `elapsedMs()` 拼接副标题 "启动耗时：N ms · ..."）

**第十轮 Batch 13（5 个，2026-07-19，浏览器模块全面优化）**：
- `BROWSER_GESTURE_NAV`: 浏览器左右滑动手势导航（`BrowserGestureHelper.java` 双路检测：GestureDetector.onFling + 手动 ACTION_MOVE 跟踪；边缘宽度 32dp，触发阈值 48dp；左边缘右滑 → goBack，右边缘左滑 → goForward；视觉反馈 `bg_gesture_indicator.xml` + 振动反馈 15ms；`setSystemGestureExclusionRects` 声明系统手势排除区域；AndroidManifest 新增 VIBRATE 权限）
- `BROWSER_FIND_IN_PAGE`: 页面内查找（`BrowserFindInPageHelper.java` 封装 WebView.findAllAsync + FindListener；顶部 Find Bar 含输入框/上一个/下一个/关闭/匹配计数 `0/0`；debounce 300ms 输入）
- `BROWSER_READER_MODE`: 阅读模式（`BrowserReaderModeHelper.java` 通过 evaluateJavascript 注入 JS 提取 `<article>` 或最大文本密度 `<div>/<section>` → loadDataWithBaseURL 渲染美化 HTML；prefers-color-scheme 浅色/深色自适应；行高 1.75 字号 17px；退出时恢复原 URL）
- `BROWSER_SCREENSHOT`: 网页截图（`BrowserScreenshotHelper.java` 使用 webView.capturePicture → Bitmap → MediaStore（Android 10+）或 Pictures/BrowserScreenshots 目录；文件名 `Browser_yyyyMMdd_HHmmss.png`）
- `BROWSER_PROGRESS_SMOOTH`: 进度条主题色改进（`progressTint="?attr/colorPrimary"` + `progressBackgroundTint="?attr/colorSurfaceVariant"`）

**第十一轮 Batch 14（P0-3 + P0-4 + P1-1，2026-07-19，浏览器模块全面优化 Phase 1-2）**：
- `BROWSER_HOME_PAGE`: 浏览器起始页（`BrowserHomeHelper.java` 渲染 `layout_browser_home.xml`，3 种风格：宫格 / 卡片流 / 极简，通过 `BrowserSettingsManager.KEY_HOME_PAGE_STYLE` 切换；默认宫格，含百度/Bing/Google 等快捷入口；起始页 URL 通过 `KEY_HOME_URL` 自定义，默认 `https://www.baidu.com`；`isHomePageUrl()` 判断逻辑避免重复加载）
- `BROWSER_SMART_URL_BAR`: 智能 URL Bar（`UrlInputHelper.java` 处理输入：URL 检测 + 搜索引擎选择；长按 et_url 弹出菜单含"搜索引擎"项，点击弹出 4 引擎列表（百度/Bing/Google/DuckDuckGo）；选中后写入 `KEY_SEARCH_ENGINE` SharedPreferences；`SplashActivity` 转发 `EXTRA_NAV_TAB` extra 支持 adb deep link 启动到指定 tab；修复 `BrowserHomeHelper.bindToContainer` 使用 applicationContext 导致 InflateException 崩溃，改为 `container.getContext()`）
- `BROWSER_FORCE_DARK`: 夜间模式三档策略（`BrowserSettingsManager.applyDarkMode()` 在 `applyToWebView()` 末尾调用，三档：`DARK_MODE_AUTO=0`（跟随系统 uiMode NIGHT_YES）/ `DARK_MODE_FORCE_ON=1` / `DARK_MODE_FORCE_OFF=2`；通过 `WebSettings.setForceDark(int)` API 29+ 实现；设置页 `row_dark_mode` 点击弹出单选对话框；`BrowserFragment.onResume()` 重新应用设置响应系统夜间模式变化；`resetToDefaults()` 包含 dark mode 重置）
- `BROWSER_TRACKER_PROTECTION`: 追踪保护（已实现，见下方 Batch 17 章节）
- `BROWSER_READING_LIST`: 阅读列表稍后阅读（已实现，见下方 Batch 16 章节）
- `BROWSER_OFFLINE_CACHE`: 离线缓存 LRU（已实现，见下方 Batch 18 章节）
- `BROWSER_TRANSLATE`: 页面翻译（已实现，见下方 Batch 18 章节）
- `BROWSER_SMART_ZOOM`: 双指缩放改进（已实现，见下方 Batch 18 章节）
- `BROWSER_VOLUME_SCROLL`: 音量键滚动（已实现，见下方 Batch 18 章节）
- `BROWSER_MEDIA_SNIFFER`: 媒体嗅探（已实现，见下方 Batch 18 章节）
- `BROWSER_DATA_SAVER`: 数据节省模式（已实现，见下方 Batch 18 章节）
- `BROWSER_MULTI_FINGER_GESTURE`: 多指手势（已实现，见下方 Batch 18 章节）
- `BROWSER_TAB_SWITCHER_ANIM`: Tab 切换动画（已实现，见下方 Batch 18 章节）
- `BROWSER_CUSTOM_BOTTOM_BAR`: 底部工具栏可定制（已实现，见下方 Batch 18 章节）
- `BROWSER_SKELETON_LOADING`: 页面加载骨架屏（已实现，见下方 Batch 18 章节）
- `BROWSER_REAL_MULTI_TAB`: 真·多 Tab 架构（已实现，见下方 Batch 15 章节）

P0-3 手势导航增强（已完成 2026-07-19）：
- `BrowserGestureHelper` 新增 `onDoubleTap` 重写：双击 WebView 触发 `onGoForward`（仅当 `doubleTapForwardEnabled=true` 且 `canGoForward` 时）
- `BrowserGestureHelper` 新增 `onLongPress` 重写：长按 WebView 触发 `onShowHistory` 回调，调用方 `BrowserFragment` 启动 `HistoryActivity`
- `GestureActionCallback` 接口新增 `onShowHistory()` 方法
- `BrowserSettingsManager` 新增 `KEY_GESTURE_DOUBLE_TAP_FORWARD` / `KEY_GESTURE_LONG_PRESS_HISTORY` 设置项，默认均 true，通过设置页可关闭
- 真机验证：长按 baidu.com 页面触发 onLongPress → onShowHistory → HistoryActivity 显示历史列表，logcat 无 FATAL

P1-1 夜间模式三档策略（已完成 2026-07-19）：
- 三档：`DARK_MODE_AUTO`（跟随系统 `Configuration.UI_MODE_NIGHT_YES`）/ `DARK_MODE_FORCE_ON` / `DARK_MODE_FORCE_OFF`
- 实现：`BrowserSettingsManager.applyDarkMode()` 调用 `WebSettings.setForceDark(int)` API 29+
- UI：设置页新增 `row_dark_mode` 条目，点击弹出 `setSingleChoiceItems` 单选对话框
- 副标题 `tv_dark_mode_summary` 实时显示当前模式
- `BrowserFragment.onResume()` 重新 applySettings 响应系统夜间模式变化
- 真机验证：强制开启模式 logcat 显示 `applyDarkMode: mode=1 forceDark=2`，无 FATAL

P0-1 真·多 Tab 架构（已完成 2026-07-19，Batch 15）：
- 新增 `BrowserWebViewPool.java`：WebView 池管理器
  - `MAX_ACTIVE_WEBVIEWS = 5`（活跃池上限；用户选择 10 个，实际取 5 以控制内存压力，超出部分通过 saveState/restoreState 恢复）
  - `activePool` Map<tabId, WebView> + `releasedStates` Map<tabId, Bundle> + `lastAccessMap` LinkedHashMap<tabId, Long>（LRU 排序）
  - 关键 API：`acquireWebView(tabId, fallbackUrl)` / `releaseTab(tabId)` / `trimToActiveOnly()` / `saveTabState(tabId)`
- `BrowserController` 改造为双模式：
  - `BuildConfig.BROWSER_REAL_MULTI_TAB=false` → 保留原单 WebView 路径（向后兼容）
  - `BuildConfig.BROWSER_REAL_MULTI_TAB=true` → 使用 poolContainer + pool，所有通用 API（loadUrl / goBack / canGoBack / reload 等）通过 `getActiveWebView()` 统一路由
  - 新增多 Tab API：`switchToTab(tabId, fallbackUrl)` / `closeTabWebView(tabId)` / `trimToActiveOnly()` / `getPoolActiveCount()` / `getPoolReleasedCount()` / `getActiveTabId()`
  - `setDownloadListener()` 同时为池中所有 WebView 设置监听；`destroy()` 双模式销毁
- `TabManagerActivity` 改造为结果回传模式：
  - 新增常量 `EXTRA_SELECTED_TAB_ID` / `EXTRA_CLOSED_TAB_ID` / `RESULT_NEW_TAB = 0x1001`
  - 所有操作（切换/关闭/新建/关闭全部）通过 `setResult` 回传给 BrowserFragment
  - 关闭单个 Tab 后不自动 finish，让用户继续管理（除非 Tab 数为 0）
  - 关闭全部使用 `"__all__"` 哨兵值
- `BrowserFragment` 集成：
  - 新增 `tabManagerLauncher` ActivityResultLauncher + `REQUEST_TAB_MANAGER = 0x1001` 常量
  - `onViewCreated` 初始化 tabManager 并为当前 Tab 创建 WebView
  - `btnTabs.setOnClickListener` 根据 Flag 分支：多 Tab 模式 → `launchTabManager()`；单 WebView 模式 → 原 `showTabList()`
  - `tabManagerLauncher` 回调处理：关闭全部（`__all__`）→ `controller.destroy()` + 重新 `initWebView()`；关闭单个 → `controller.closeTabWebView(closedId)`；切换/新建 → `switchToTabById(targetId)` + Toast 反馈
- 真机验证（小米 ares M2012K10C）：TabManagerActivity 启动 / 新建 Tab / 切换 Tab / 关闭单个 Tab 全部通过；切换时 WebView 状态不丢失（baidu.com 仍加载）；无 FATAL EXCEPTION

P1-3 阅读列表（已完成 2026-07-19，Batch 16）：
- Room 数据库 v2 → v3 升级：
  - 新增 `BrowserReadingListEntity`：id / url（unique index）/ title / summary / host / savedAt / read
  - 新增 `BrowserReadingListDao`：insert / deleteById / deleteByUrl / deleteAll / updateRead / getAll（未读优先）/ search / countByUrl / getByUrl / countUnread
  - `MIGRATION_2_3`：CREATE TABLE + CREATE UNIQUE INDEX `index_browser_reading_list_url`
  - `addMigrations(MIGRATION_1_2, MIGRATION_2_3)` 链式注册
- 新增 `BrowserReadingListRepository`：Callback 风格 API 包装 Dao
- 新增 `ReadingListActivity` + `ReadingListAdapter`：
  - 全屏阅读列表管理页（顶栏 + 搜索 + RecyclerView + 空状态）
  - 列表项：图标 + 未读小红点（read=0 显示）+ 标题 + 摘要（最多 2 行）+ URL + 时间 + 删除按钮
  - 点击列表项 → 标记已读 + 跳转 BrowserActivity
  - 长按弹出"标记为未读/删除"操作对话框
  - 顶栏"清空"按钮 → AlertDialog 确认后 deleteAll
- `BrowserFragment` 新增"加入阅读列表"逻辑：
  - `addToReadingList()`：获取 url/title/host，先查重（已存在则 Toast 提示），否则调用 `extractSummaryAndSave`
  - `extractSummaryAndSave(url, title, host)`：通过 `WebView.evaluateJavascript` 注入 JS 提取页面正文前 200 字（兼容 article / main / p / body）
  - `parseJsString(jsResult)`：解析 evaluateJavascript 返回值（去引号、反转义）
  - `saveReadingListItem(url, title, summary, host)`：异步落库 + Toast 反馈
- 菜单入口：`browser_more_menu.xml` 新增 `menu_reading_list_add` 和 `menu_reading_list` 两项
  - Feature Flag 控制：`BuildConfig.BROWSER_READING_LIST` 为 false 时菜单项隐藏
- 真机验证（小米 ares M2012K10C）：Room 迁移成功；菜单显示正确；加入阅读列表 + 摘要提取成功；Activity 启动显示 1 项数据；点击跳转 + 标记已读生效；长按弹出操作对话框；删除功能正常；无 FATAL EXCEPTION

P1-2 追踪保护 + 隐私仪表盘（已完成 2026-07-19，Batch 17）：
- 拦截器 `BrowserTrackerBlocker`（单例）：
  - `TRACKER_DOMAINS` 57 条静态黑名单（Google Analytics / Facebook Pixel / 百度统计 / 友盟 / 神策 / growingio / Criteo / Taboola / Yandex / Adobe Analytics 等）
  - `TRACKER_PATH_KEYWORDS` 9 条（/track /pixel /beacon /analytics /collect 等）
  - 白名单优先 + `isFirstPartyDomain` 二次校验（避免误伤 baidu.com/track 等顶级站点路径）
  - `shouldBlock(url)` 返回 true 时由 WebViewClient 中止请求
- 统计 `BrowserTrackerStats`（单例，SharedPreferences 文件 `browser_tracker_stats`）：
  - `recordBlock(url)` 更新 total + session + 按域名分布（key=`domain_<host>`）
  - `getTotalBlocked()` / `getSessionBlocked()` / `getTopDomains(topN)` / `reset()`
- 集成 `BrowserWebViewClient.shouldInterceptRequest`：
  - AdBlocker 分支之后新增追踪拦截分支
  - 命中后调用 `BrowserTrackerStats.recordBlock(url)` + 返回空 `WebResourceResponse`
- 设置项 `BrowserSettingsManager.KEY_TRACKER_PROTECTION`：
  - `isTrackerProtectionEnabled()` / `setTrackerProtectionEnabled(boolean)` setter 同步到 `BrowserTrackerBlocker.setEnabled`
  - `applyToWebView()` 末尾同步开关状态
  - `resetToDefaults()` 包含此 key
- 隐私仪表盘 `PrivacyDashboardActivity`：
  - 顶栏（返回 + 隐私盾牌图标 + 标题 + 重置按钮）+ MaterialSwitch 总开关 + NestedScrollView
  - 两张统计卡（累计拦截 / 本次会话）使用 `bg_privacy_stat_total` / `bg_privacy_stat_session` 渐变背景
  - 规则数说明卡（"57 条内置规则" + 拦截说明文案）
  - Top 域名列表（内置 `TrackerDomainAdapter`，最多 20 项）
  - 空状态视图（"尚未拦截任何追踪器" + 提示文案）
  - 总开关切换：`BrowserSettingsManager.setTrackerProtectionEnabled` 同步到 `BrowserTrackerBlocker` + Toast 反馈
  - 重置统计：AlertDialog 确认 → `BrowserTrackerStats.reset()` + 刷新 UI + Toast 反馈
- 菜单入口：`browser_more_menu.xml` 在 `menu_reading_list` 后新增 `menu_privacy_dashboard`
  - Feature Flag 控制：`BuildConfig.BROWSER_TRACKER_PROTECTION` 为 false 时菜单项隐藏
- 真机验证（小米 ares M2012K10C）：访问 baidu.com 拦截 hm.baidu.com 1 次；累计/会话/规则数显示正确；Top 域名列表显示 hm.baidu.com 1 次；开关切换 checked=true↔false 正常；重置统计对话框 + 清零空状态显示正常；无 FATAL EXCEPTION

Batch 18（2026-07-19，浏览器模块 Phase 1-3 剩余 12 项一次性完成）：

P1-4 离线缓存（已完成）：
- `BrowserOfflineCache`（单例）：
  - `LinkedHashMap(16, 0.75f, true)` accessOrder LRU，`MAX_ENTRIES=10`
  - `captureAsync(WebView, url, titleHint)`：通过 `evaluateJavascript` 提取 `document.documentElement.outerHTML`，反序列化为 htmlSnapshot
  - `put/get/contains/remove/clear/getAll/size` + `trimToSize` + `persist/loadFromDisk`（JSON 序列化到 SharedPreferences `browser_offline_cache`）
  - `CacheEntry` 内部类：url / title / htmlSnapshot / savedAt
  - `unquoteJs` 反转义 evaluateJavascript 返回的 JSON 字符串字面量
- `OfflineCacheActivity`（全屏列表管理页）：
  - 顶栏（返回 + 标题 + 计数 + 清空按钮）+ RecyclerView + 空状态
  - 长按列表项 → AlertDialog 确认删除
  - 清空全部 → AlertDialog 确认 → `BrowserOfflineCache.clear()` + 刷新列表
  - 内置 `OfflineCacheAdapter`：标题 + URL + 时间
- `BrowserFragment.onPageFinished` 自动捕获：`BuildConfig.BROWSER_OFFLINE_CACHE && controller != null && getContext() != null` 时调用 `BrowserOfflineCache.getInstance(getContext()).captureAsync(wv, url, currentTitle)`
- 菜单入口：`browser_more_menu.xml` 新增 `menu_offline_cache`
- 真机验证：访问 baidu.com 后菜单 → "离线缓存" 进入列表显示 2 个条目，无 FATAL EXCEPTION

P1-5 页面翻译（已完成）：
- `BrowserTranslateHelper`：
  - `showEngineDialog(context, pageUrl)`：弹出 AlertDialog 列出 Google / 百度 / 必应 三引擎
  - `openTranslate(context, pageUrl, engine)`：URLEncoder 编码页面 URL → 构造翻译 URL → `ACTION_VIEW` 打开
  - `buildTranslateUrl`：
    - Google: `https://translate.google.com/translate?sl=auto&tl=zh-CN&u=<encoded>`
    - Baidu: `https://fanyi.baidu.com/transpage?query=<encoded>&from=auto&to=zh&source=url`
    - Bing: `https://www.translatetheweb.com/?from=&to=zh-Hans&a=<encoded>`
- `BrowserFragment` 菜单点击 `menu_translate` → `BrowserTranslateHelper.showEngineDialog(getContext(), controller.getCurrentUrl())`
- 真机验证：菜单 → "翻译" 弹出三引擎选择对话框，无 FATAL EXCEPTION

P2-1 智能双指缩放（已完成）：
- `BrowserZoomHelper`：
  - 按 host 持久化字号到 SharedPreferences `browser_text_zoom`
  - 常量：`MIN_TEXT_ZOOM=50` / `MAX_TEXT_ZOOM=200` / `DEFAULT_TEXT_ZOOM=100` / `STEP=10`
  - `applySavedTextZoom(WebView, url)` / `increaseTextZoom` / `decreaseTextZoom` / `resetTextZoom`
  - 内部 `ZoomListener` extends `SimpleOnScaleGestureListener`，缩放时调用 `setTextZoom`

P2-2 音量键滚动（已完成）：
- `BrowserActivity.onKeyDown/onKeyUp` 拦截 `KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN`：
  - 双控条件：`BuildConfig.BROWSER_VOLUME_SCROLL && BrowserSettingsManager.isVolumeScrollEnabled()`
  - `onKeyUp` 消费 up 事件避免系统音量调节
  - `getActiveWebView()` 通过 `findFragmentByTag(BrowserFragment.TAG)` 获取 WebView
- `BrowserFragment` 新增 `public WebView getControllerWebView()` 方法暴露给 BrowserActivity

P2-3 媒体嗅探（已完成）：
- `BrowserMediaSniffer`：
  - `sniff(WebView, Callback)` 通过 `evaluateJavascript` 注入脚本提取：
    - `<video>` src + `<source>` src
    - `<a href="*.pdf">` 链接
    - `<embed src="*.pdf">` 链接
  - 返回 JSON 数组字符串 `[{type, url, label}]`
  - `unescape` 方法反转义 JSON 字符串字面量
- 已知限制：UI 入口未接入菜单（避免菜单溢出），保留 Helper 供后续使用

P2-4 数据节省模式（已完成）：
- `BrowserSettingsManager` 新增常量/默认值/getter/setter：
  - `KEY_DATA_SAVER = "data_saver_enabled"` / `DEFAULT_DATA_SAVER = false`
  - `KEY_VOLUME_SCROLL` / `KEY_SMART_ZOOM`
  - `resetToDefaults()` 包含三个新 key
- `BrowserWebViewClient.shouldInterceptRequest` 在 AdBlocker + 追踪保护之后新增数据节省拦截：
  - 条件：`!request.isForMainFrame() && isDataSaverEnabled()`
  - 按 URL 后缀判断：`.jpg/.jpeg/.png/.gif/.webp/.bmp/.svg` 图片 + `.woff/.woff2/.ttf/.otf/.eot` 字体
  - 命中返回 `new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]))`（空响应节省流量）

P2-5 多指手势（已完成）：
- `BrowserMultiFingerGestureHelper`：
  - 三指点击 → `onThreeFingerTap()`
  - 双指下拉刷新 → `onPullToRefresh()`（简化实现：顶部区域双指 MOVE 触发）
  - 边缘长按切 Tab → `onEdgeLongPressLeft()` / `onEdgeLongPressRight()`
  - Callback 接口由调用方实现
- 已知限制：双指下拉刷新为简化实现，未使用 VelocityTracker

P3-1 Material 3 Expressive 重设计（已完成）：
- `fragment_browser.xml` 修改：
  - 顶栏 `browser_top_bar`：`colorSurface` → `colorSurfaceContainer`，`elevation` 2dp → 3dp
  - 底栏 `browser_bottom_bar`：同上
- 已知限制：仅修改顶栏/底栏颜色 token，未全面重设计

P3-2 Tab 切换动画（已完成）：
- `TabManagerActivity` 返回按钮加动画：
  - 条件：`BuildConfig.BROWSER_TAB_SWITCHER_ANIM`
  - `finish()` 后调用 `overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)`

P3-3 底部工具栏可定制（已完成）：
- `BottomBarCustomizeActivity`（全屏列表页）：
  - RecyclerView 列出 8 个底栏按钮（back/forward/refresh/home/bookmark/tabs/download/more）
  - MaterialSwitch 控制每个按钮显隐
  - SharedPreferences `browser_bottom_bar_prefs` 持久化
  - 静态方法 `isVisible(Context, buttonId)` 供 BrowserFragment 读取
- 菜单入口：`browser_more_menu.xml` 新增 `menu_customize_bottom_bar`
- 已知限制：仅持久化可见性，BrowserFragment 启动时未读取应用；菜单项在 PopupWindow 中可能因不可滚动导致 bounds 为 [0,0][0,0]

P3-4 页面加载骨架屏（已完成）：
- 新增 layout `layout_browser_skeleton.xml`：8 个 View 占位块（标题条 + 文本行 + 图片块 + 文本行）
- 新增 drawable `bg_skeleton_block.xml`：圆角灰色背景 `#22808080` + corners 6dp
- `fragment_browser.xml` 中 `webview_container` 内新增 `<include layout="@layout/layout_browser_skeleton" android:id="@+id/skeleton_overlay" android:visibility="gone" />`
- `BrowserFragment`：
  - `onPageStarted`：`if (BuildConfig.BROWSER_SKELETON_LOADING && skeletonOverlay != null && !isReaderModeUrl(url)) { skeletonOverlay.setVisibility(View.VISIBLE); skeletonOverlay.bringToFront(); }`
  - `onPageFinished`：`if (skeletonOverlay != null) skeletonOverlay.setVisibility(View.GONE);`

P3-5 Toast→Snackbar 替换（已完成）：
- `BrowserFragment` 新增 `showFeedback` helper：
  - `showFeedback(int stringRes)` / `showFeedback(@NonNull CharSequence text)`
  - 条件分支：`BuildConfig.BROWSER_SNACKBAR_FEEDBACK=true` → Snackbar（setAnchorView(bottomBar)）
  - 否则回退到 Toast.makeText
- `app/build.gradle` 新增 `buildConfigField "boolean", "BROWSER_SNACKBAR_FEEDBACK", "true"`

10 个新增 Feature Flag（全部默认 true）：
- `BROWSER_OFFLINE_CACHE` / `BROWSER_TRANSLATE` / `BROWSER_SMART_ZOOM` / `BROWSER_VOLUME_SCROLL` / `BROWSER_MEDIA_SNIFFER` / `BROWSER_DATA_SAVER` / `BROWSER_MULTI_FINGER_GESTURE` / `BROWSER_TAB_SWITCHER_ANIM` / `BROWSER_CUSTOM_BOTTOM_BAR` / `BROWSER_SKELETON_LOADING` / `BROWSER_SNACKBAR_FEEDBACK`

回滚方法：将 `app/build.gradle` 中 11 个 Batch 18 新增 Feature Flag 的值从 `"true"` 改为 `"false"`，重新 `:app:assembleDebug` 即可禁用全部 Batch 18 功能而不影响代码。

真机验证（小米 ares M2012K10C，2026-07-19）：
- 编译：`BUILD SUCCESSFUL in 34s`
- 安装：`adb install -r -d app-debug.apk` 成功
- 菜单：BrowserFragment → 菜单 → 弹出菜单含"离线缓存 / 翻译 / 自定义底栏 / 隐私仪表盘"等全部新项
- 离线缓存：访问 baidu.com 后菜单 → "离线缓存" → 列表显示 2 个条目
- 翻译：菜单 → "翻译" → 弹出三引擎选择对话框
- logcat：无 FATAL EXCEPTION / Resources$NotFoundException / InflateException / ClassNotFoundException

已知限制汇总：
- P2-3 媒体嗅探 UI 入口未接入菜单
- P2-5 双指下拉刷新为简化实现（无 VelocityTracker）
- P3-1 M3 重设计仅修改顶栏/底栏颜色 token
- P3-3 底栏定制仅持久化可见性，BrowserFragment 启动时未读取应用；菜单项在 PopupWindow 中可能因不可滚动导致 bounds 为 [0,0][0,0]

> 注：错题本入口因 BottomNavigationView 6 item 限制，已从底部导航移到 GamesFragment 头像 PopupMenu（id=7），通过 `NavHostFragment.findNavController(this).navigate(R.id.navigation_wrongbook)` 跳转。
>
> 注：第四轮新增 BottomSheet 主题 `Theme.GameMatrix.BottomSheet`（顶部 20dp 圆角）在 `themes.xml` 中定义，供 `GameDetailBottomSheet` 使用。
>
> 注：第五轮 Batch 8 英雄横幅初始高度 160dp 会挤压游戏列表，已调整为 120dp（图标 72dp），保证 `rv_games` 可见。
>
> 注：第六轮 Batch 9 的 `AchievementDetailActivity.launch()` 必须标注 `@JvmStatic`，否则 Java 侧 `AchievementCenterActivity` 调用会报"找不到符号"。`NavBadgeHelper` 中颜色直接用常量 `ERROR_COLOR`/`ON_ERROR_COLOR`，避免 `com.google.android.material.R.attr.colorError` 在编译期解析失败。
>
> 注：第七轮 Batch 10 的 `AchievementProgressRingView.kt` 中读取主题属性 `colorPrimary` 时，不能直接用 `context.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.colorPrimary))`（Kotlin 编译期 `Unresolved reference 'colorPrimary'`）。正确姿势：`context.theme.resolveAttribute(android.R.attr.colorPrimary, tv, true)` + `tv.data` 取 color int。同一模式也用于 NavBadgeHelper 的颜色常量替代方案。`GameUsageStore.recordPlayTime()` 已在写 `play_time_<gameId>` 之外，同步写入 `daily_play_time_yyyy-MM-dd` 按天累计键，向后兼容旧历史数据。
>
> 注：第九轮 Batch 12 的 `ResumeGameHelper` 数据来源必须是 `RecentGamesManager.getRecentIds()`（覆盖所有游戏启动，包含 GomokuActivity 等旧游戏），而不是 `GameUsageStore.getRecentIds()`（仅覆盖继承 `BaseGameActivity` 的新游戏），否则继续游玩卡片在仅玩过旧游戏时永远空白。`RecentAchievementHelper.dismissForSession()` 用单独的 `home_recent_achievement_session` SharedPreferences 文件，避免污染 `game_achievements` 主文件。`LaunchTimeTracker` 使用 `SystemClock.elapsedRealtime()` 而非 `System.currentTimeMillis()`，避免系统时间被修改导致负值。
>
> 注：第九轮 Batch 12 的 `GAME_PLAY_TIME_BADGE` 历史受 ConstraintLayout 布局影响（顶部内容堆叠把 tab_layout/rv_games 推到屏幕外），徽章虽正确实现但暂未在 UI dump 中验证到。该布局问题已于 2026-07-19 修复：`fragment_games.xml` 根布局由 `ConstraintLayout` 改为 `FrameLayout` + `NestedScrollView`（fillViewport=true） + 垂直 `LinearLayout` 包裹所有 section，游戏区用 `FrameLayout` overlay 叠加 SwipeRefreshLayout + shimmer + empty_state，整个页面可垂直滚动。新增 `home_game_grid_min_height` dimen（400dp）保证游戏区最小可见高度。所有 View id、`?attr/`、`@dimen/`、`@string/`、`@drawable/`、`@style/` 引用全部保留不变，GamesFragment.java 无需改动。

#### 本地化架构（2026-07-17 修复）

应用支持应用内语言切换（跟随系统 / 中文 / English），通过 `AppCompatDelegate.setApplicationLocales(LocaleListCompat)` 实现：

- **资源目录**：`values/`（默认中文）/ `values-en/`（英文）/ `resConfigs "zh-rCN", "en"`
- **LocaleConfig**：`app/src/main/res/xml/locales_config.xml` 声明支持 `zh-CN` 和 `en`，在 `AndroidManifest.xml` 的 `<application>` 中通过 `android:localeConfig` 引用
- **语言常量**：`SettingsManager.LANGUAGE_SYSTEM=""` / `LANGUAGE_ZH="zh-CN"` / `LANGUAGE_EN="en"`（注意：`zh-CN` 必须与 `resConfigs "zh-rCN"` 和 `locales_config` 匹配，否则 Android 13+ per-app language API 会静默回退到系统默认语言）
- **持久化**：`SettingsManager` 的 `app_settings` SharedPreferences，key=`app_language`，String 类型
- **应用时机**：① `App.onCreate()` 调用 `applyLanguage()` 应用保存的语言；② `AppSettingsDialog.showLanguagePicker()` 用户选择后即时调用 `setApplicationLocales` + `recreate()`
- **旧值迁移**：`getAppLanguage()` 读取到旧值 `"zh"` 时自动迁移为 `"zh-CN"` 并持久化（2026-07-17 修复）
- **硬编码文本禁令**：所有 UI 文本必须通过 `@string/` 或 `getString(R.string.*)` 引用，不允许在 Java/Kotlin/layout 中硬编码中英文文本
- **休闲游戏组 D 本地化与主题合规（2026-07-22）**：dice / guess / reaction / whack / rock / blackjack 六款游戏的 UI 文本与颜色已完成资源化重构。UI 文本追加到各自的 `res/values/strings_game_<name>.xml`（命名约定 `game_<name>_<purpose>`，含变量的用 `%1$d`/`%2$s` 占位符 + `getString(R.string.xxx, args...)`）。UI 颜色（背景/文字/按钮，不含 Canvas `onDraw` 中的 `paint.setColor`）集中到新建的 `res/values/colors_game_group_d.xml`（浅色）与 `res/values-night/colors_game_group_d.xml`（深色），命名约定 `game_<name>_color_<purpose>`，Java 侧用 `ContextCompat.getColor(this, R.color.xxx)` 读取。`getGameName()` 复用已有的 `game_<name>_name` 资源。Reaction 的 `static final int COLOR_*` 常量已删除，改为使用处直接调用 `ContextCompat.getColor()`；Rock 的 `static final String[] CHOICE_NAMES` 改为实例字段 `choiceNames`，在 `initGame()` 中用 `getString()` 初始化。本次共新增 86 条字符串资源、41 条颜色资源（浅色 41 + 深色 41）。

---

## 9. 注意事项

### 9.1 不要做的事
- 不要删除斗地主包（主入口，GameRegistry 引用依赖）
- 不要将 LeakCanary 改为默认启用（蓝叠模拟器崩溃）
- 不要禁用 R8 混淆（已修复 BreakoutActivity$1 问题）
- 不要恢复美国 VPS 配置（已下线）
- 不要关闭 ABI splits（除非需要模拟器测试）
- 不要将 `abortOnError` 改回 false

### 9.2 向后兼容
- `UPDATE_SOURCE_VPS_US` 常量保留用于旧用户自动回退
- 旧用户选择美国 VPS 时自动显示为"自动"
- 模块加载失败时返回 null，调用方需处理

### 9.3 模拟器注意事项
- ABI splits 启用后 x86_64 模拟器无法安装（需临时改回 `enable false`）
- LeakCanary 默认关闭，需通过 `-PleakCanary=true` 显式启用
- Debug 包默认开启 TEST_MODE，跳过权限弹窗

---

## 10. 文档索引

| 文档 | 用途 |
|------|------|
| `README.md` | 项目总览、功能介绍 |
| `CHANGELOG.md` | 版本更新历史 |
| `CODE_WIKI.md` | 详细代码架构说明 |
| `修改记录.md` | 24 轮修复循环的完整变更历史 |
| `docs/PROJECT_STATUS.md` | 项目当前状态总览（合并自 7 个审计文档） |
| `docs/DOCUMENTATION_INDEX.md` | 文档统一索引 |
| `docs/PUBLISH_GUIDE.md` | 发布指南 |
| `docs/SECURITY.md` | 安全文档 |
| `docs/NETWORK_LAYER.md` | 网络层文档 |
| `server/wrongbook-service/` | 错题本后端服务（FastAPI + 百度OCR + 智谱GLM） |

---

## 错题本后端服务（v1.0，2026-07-06 新增）

错题本模块的云端 OCR 与 AI 解题后端，独立于 Android 工程，不影响 APK 构建。

### 位置
`server/wrongbook-service/`（项目根目录下新增，纯 Python，不参与 Gradle 构建）

### 技术栈
- Python 3.10+（已验证 3.14 兼容）
- FastAPI + Uvicorn + httpx + Pydantic + Pillow + loguru

### 接口
| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/wrongbook/config/status` | 配置检查 |
| POST | `/api/wrongbook/ocr` | 百度 OCR 识别 |
| POST | `/api/wrongbook/solve-text` | 智谱 GLM-4.7-flash 解题 |
| POST | `/api/wrongbook/solve-image` | 图片 → OCR → AI 一步完成 |
| GET | `/health` | 健康检查 |
| GET | `/docs` | Swagger UI |

### API 密钥管理
- 密钥存于 `server/wrongbook-service/.env`（已被 `.gitignore` 排除）
- `.env.example` 为占位符模板（可入库）
- 安卓端**不保存**百度/智谱密钥，仅保存后端地址
- 日志中密钥脱敏（仅前 4 后 4 位）
- 占位符（含中文/ASCII 校验）被视为未配置，返回 503

### 启动方式
```bash
cd server/wrongbook-service
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env   # 填入真实密钥
uvicorn main:app --host 0.0.0.0 --port 8080 --reload
```

### 与安卓端的集成（后续阶段）
安卓端错题本模块 `module-store/feature/tools/wrongbook/` 已有扩展点：
- `OcrEngine` 接口 → 第二阶段新增 `BaiduOcrEngine`（调用后端 `/api/wrongbook/ocr`）
- `AiAnalysisService` cloud 模式 → 第二阶段改造为调用后端 `/api/wrongbook/solve-text`
- `SettingsFragment` → 第二阶段新增"后端地址"配置项

详见 `修改记录.md` 与 `GameMatrixApp_错题本_百度OCR_智谱AI实施计划.md`。

---

[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
