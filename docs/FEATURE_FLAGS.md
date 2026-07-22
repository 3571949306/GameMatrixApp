<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# Feature Flags 索引 - GameMatrixApp

> 本文档索引项目所有 Feature Flag，包括用途、引入版本、退役计划。
> 规则：Flag 超过 3 个月未关闭 = 应该删除代码（保留功能）。

**最后更新**: 2026-07-22
**Flag 总数**: 75 个（基础设施 11 + 加法升级 58 + 混合架构 5 + 首页沉浸式改版 1）
**默认值分布**: 70 个 true / 5 个 false（`TEST_MODE`、`BROWSER_JS_BRIDGE_ENABLED`、`BROWSER_WEBVIEW_DEBUG`、`ENABLE_CATALOG_SIGNATURE`、`ENABLE_FLUTTER_MODULE_STORE`）
**声明位置**: `app/build.gradle` → `defaultConfig`（`buildTypes` 中无覆盖）

---

## 索引表

### 一、基础设施 Flag（11 个）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `ENABLE_MODULE_SYSTEM` | true | T08（APK 体积优化阶段） | 框架 APK 体积优化：启用模块系统按需下载，配合 `MODULE_CACHE_DIR` 与 `MAX_MODULE_CACHE_SIZE` | 长期保留 | 活跃 |
| `ENABLE_FLUTTER_MODULE_STORE` | false（源码回退值） | Flutter-first 商店（2026-07-21） | 在 `ModuleStoreActivity` 中挂载缓存 Flutter Engine 的 Fragment；失败、关闭或显式强制旧商店时渲染原生 UI。Release 启用时强制 Catalog 验签 | stable vc595 已通过生产参数启用；Catalog V8、模块包、Android 11–15 与生产灰度均完成 | 生产 100%/stable 已启用 |
| `ENABLE_MIMO_TTS` | true | Phase 1（早期） | MiMo TTS 能力注入开关 | 长期保留 | 活跃 |
| `TEST_MODE` | false | 早期基础设施 | 自动化测试模式：开启后跳过首次启动权限说明弹窗、简化更新提示（Debug 构建运行 UI 测试时使用） | 长期保留 | 活跃 |
| `GOMOKU_ENHANCED` | true | 五子棋增强阶段 | 五子棋增强功能：界面优化、AI 增强、先手选择、计时、音效等 | 待评估（已稳定，可考虑退役） | 待评估 |
| `ENABLE_WRONGBOOK` | true | 循环 20（错题本预装集成） | AI 错题本模块开关（预装） | 长期保留 | 活跃 |
| `WRONGBOOK_BACKEND_PROXY` | true | 循环 21-22（错题本推进） | 错题本后端代理模式：开启后启用百度 OCR + 智谱 GLM 后端代理通道 | 长期保留 | 活跃 |
| `WRONGBOOK_SECURE_API_CONFIG` | true | 循环 21-22（错题本推进） | 错题本正式版直连密钥安全存储：开启后允许在设置页输入百度/智谱 API Key（EncryptedSharedPreferences 加密保存） | 长期保留 | 活跃 |
| `BROWSER_SECURITY_POLICY` | true | 循环 19（浏览器原生重构） | 浏览器安全策略总开关（AdBlocker / DomainTrustManager / JsBridgePolicy） | 长期保留 | 活跃 |
| `BROWSER_JS_BRIDGE_ENABLED` | false | 循环 19（浏览器原生重构） | 浏览器 JS Bridge 启用开关（默认关闭，按需开启） | 实验性，3 个月内未启用 → 删除 | 待评估 |
| `BROWSER_WEBVIEW_DEBUG` | false | 循环 19（浏览器原生重构） | 浏览器 WebView 调试开关（默认关闭，仅 Debug 用途） | 改为 `BuildConfig.DEBUG` 自动判断后退役 | 待评估 |

### 二、加法升级 Batch 1（4 个，第一轮）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `ACHIEVEMENT_V2` | true | 1.4.1（加法升级第一轮） | 成就中心新增"每日挑战"+"连胜概览"卡片 | 待评估 | 活跃 |
| `SETTINGS_ENHANCE` | true | 1.4.1（加法升级第一轮） | 设置弹窗新增字号选择 + 缓存清理 | 待评估 | 活跃 |
| `HOME_REVAMP` | true | 1.4.1（加法升级第一轮） | 游戏大厅首页新增"最近游玩"横向滚动条 | 待评估 | 活跃 |
| `VISUAL_REFRESH` | true | 1.4.1（加法升级第一轮） | 全局视觉打磨：游戏卡片图标渐变背景 + 底部导航选中胶囊指示器 | 待评估 | 活跃 |

### 三、加法升级 Batch 2（4 个，第二轮）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `HOME_CARD_ENHANCE` | true | 1.4.1（加法升级第二轮） | 顶栏渐变美化 + 收藏/评分/热门徽章激活 + 图标点击动效 | 待评估 | 活跃 |
| `GAME_STATS_DASHBOARD` | true | 1.4.1（加法升级第二轮） | 游戏统计仪表盘（总时长/活跃天数/Top5/胜负/成就概览） | 待评估 | 活跃 |
| `THEME_SWITCHER` | true | 1.4.1（加法升级第二轮） | 多主题色切换（补全已存在 UI 的"最后一公里"：持久化+应用+重建） | 待评估 | 活跃 |
| `SPLASH_ANIMATION_ENHANCE` | true | 1.4.1（加法升级第二轮） | 启动动画增强（波纹扩散动画 + 深色模式启动屏背景修复） | 待评估 | 活跃 |

### 四、加法升级 Batch 5/6（5 个，第三轮）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `DAILY_CHECKIN` | true | 1.4.1（加法升级第三轮 Batch 5） | 每日登录天数自动记录（2026-07-22 起由手动签到改为自动记录，用户进入游戏大厅即后台记录，无弹窗无按钮。记录连续登录天数/累计登录天数/最佳连续登录天数） | 待评估 | 活跃 |
| `NOTIFICATIONS_CENTER` | true | 1.4.1（加法升级第三轮 Batch 5） | 通知中心（顶栏通知按钮打开 NotificationsDialog） | 待评估 | 活跃 |
| `PROFILE_FRAGMENT` | true | 1.4.1（加法升级第三轮 Batch 5） | 个人中心 Fragment（底部导航"Me"项） | 待评估 | 活跃 |
| `HOME_DAILY_CARDS` | true | 1.4.1（加法升级第三轮 Batch 5） | 首页每日卡片（每日挑战 + 连胜概览双卡片） | 待评估 | 活跃 |
| `NAV_ACTIVE_ANIM` | true | 1.4.1（加法升级第三轮 Batch 6） | 底部导航激活动画（选中 item 图标缩放 1→1.25→1, 220ms） | 待评估 | 活跃 |

### 五、加法升级 Batch 7（4 个，第四轮）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `GAME_DETAIL_SHEET` | true | 1.4.1（加法升级第四轮 Batch 7-1） | 游戏详情 BottomSheet（点击卡片弹出详情面板：图标+名称+分类+描述+战绩+收藏+立即开始） | 待评估 | 活跃 |
| `ANIM_SHIMMER_LOADING` | true | 1.4.1（加法升级第四轮 Batch 7-2） | 首页骨架屏加载（自实现 ObjectAnimator alpha 脉冲，6 个占位卡片，600ms 后隐藏，避免引入 shimmer 第三方依赖） | 待评估 | 活跃 |
| `EMPTY_STATE_ILLUSTRATION` | true | 1.4.1（加法升级第四轮 Batch 7-3） | 空状态精美插图（搜索/收藏/默认三种状态显示对应 VectorDrawable 插图） | 待评估 | 活跃 |
| `SETTINGS_ABOUT_PAGE` | true | 1.4.1（加法升级第四轮 Batch 7-4） | 设置-关于页面（AboutDialog：Logo+应用名+版本卡片+GitHub 卡片+开源许可卡片+复制版本信息+检查更新按钮） | 待评估 | 活跃 |

### 六、加法升级 Batch 8（4 个，第五轮）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `SEARCH_HISTORY_CHIPS` | true | 1.4.1（加法升级第五轮 Batch 8-1） | 搜索历史 Chip 流（搜索框下方最近 5 条历史，SharedPreferences 持久化，一键清空，`SearchHistoryManager.kt`） | 待评估 | 活跃 |
| `CARD_TILT_ANIM` | true | 1.4.1（加法升级第五轮 Batch 8-2） | 卡片按压倾斜动效（MaterialCardView 按下抬升阴影+放大 1.03 倍，松开 OvershootInterpolator 回弹，`CardTiltHelper.kt`） | 待评估 | 活跃 |
| `ACHIEVEMENT_TOAST` | true | 1.4.1（加法升级第五轮 Batch 8-3） | 成就解锁顶部 Toast（自定义 AchievementToastView，280ms 滑入+3 秒+240ms 滑出，橙红渐变背景） | 待评估 | 活跃 |
| `HOME_HERO_BANNER` | true | 1.4.1（加法升级第五轮 Batch 8-4） | 首页英雄横幅轮播（3 张横幅：每日精选/活动进行中/连胜挑战，PagerSnapHelper+4 秒自动轮播+指示器，`HeroBannerAdapter.kt`） | 待评估 | 活跃 |

### 七、加法升级 Batch 9（4 个，第六轮）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `GAME_LONG_PRESS_MENU` | true | 1.4.1（加法升级第六轮 Batch 9-1） | 游戏卡片长按菜单（PopupMenu 4 项：立即开始/收藏切换/分享/添加桌面快捷方式，`GameLongPressMenu.kt` + `ShortcutResultReceiver`） | 待评估 | 活跃 |
| `HOME_PULL_REFRESH` | true | 1.4.1（加法升级第六轮 Batch 9-2） | 首页下拉刷新（SwipeRefreshLayout 包裹 rv_games，4 段主题色进度环，刷新重载游戏列表+顶栏数据） | 待评估 | 活跃 |
| `ACHIEVEMENT_DETAIL_PAGE` | true | 1.4.1（加法升级第六轮 Batch 9-3） | 单游戏成就详情页（成就中心长按游戏卡片跳转，显示稀有度色块+筛选 Chip+总进度条，`AchievementDetailActivity.kt`，`@JvmStatic` 保证 Java 互操作） | 待评估 | 活跃 |
| `NAV_BADGE_UNREAD` | true | 1.4.1（加法升级第六轮 Batch 9-4） | 底部导航未读徽章（"游戏大厅" tab 显示 BadgeDrawable，未读数=每日挑战未完成+今日未签到+连胜为 0，`NavBadgeHelper.kt`） | 评估用户价值后决定 | 待评估 |

### 八、加法升级 Batch 10（4 个，第七轮，2026-07-18）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `HOME_QUICK_STATS_BAR` | true | 1.4.1（加法升级第七轮 Batch 10-1，2026-07-18） | 首页快速统计栏（"今日时长+连胜+成就数"3 列渐变卡片，`GameUsageStore.getTodayPlayTimeMs()` 读取 `daily_play_time_yyyy-MM-dd` 键） | 待评估 | 活跃 |
| `HOME_GAME_OF_DAY` | true | 1.4.1（加法升级第七轮 Batch 10-2，2026-07-18） | 首页"今日推荐"卡片（基于日期 Calendar 日历 dayKey hash % allEntries.size() 轮换游戏，含"立即开始"按钮） | 待评估 | 活跃 |
| `RANDOM_GAME_FAB` | true | 1.4.1（加法升级第七轮 Batch 10-3，2026-07-18） | 随机游戏悬浮按钮（FloatingActionButton 骰子图标 `ic_fab_dice.xml`，`Math.random()` 从全部 entries 随机启动一款游戏） | 待评估 | 活跃 |
| `ACHIEVEMENT_PROGRESS_RING` | true | 1.4.1（加法升级第七轮 Batch 10-4，2026-07-18） | 成就中心圆环进度头部（`AchievementProgressRingView.kt` 自定义 View，SweepGradient 渐变环+中心百分比+等级文案，5 档：Beginner/Explorer/Expert/Master/Legend） | 待评估 | 活跃 |

### 九、加法升级 Batch 11（4 个，第八轮，2026-07-19）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `GAME_RATING_SYSTEM` | true | 1.4.1（加法升级第八轮 Batch 11-1，2026-07-19） | 游戏 5 星用户评分（`GameRatingStore.java` 用 `game_ratings` SharedPreferences 存 `rating_<gameId>` 1-5 整数，卡片右上角金色星徽章 + 详情页 BottomSheet RatingBar） | 待评估 | 活跃 |
| `DATA_BACKUP_RESTORE` | true | 1.4.1（加法升级第八轮 Batch 11-2，2026-07-19） | 数据备份与恢复（`DataBackupHelper.kt` 通过 SAF `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`，`ActivityResultLauncher` 在 Fragment 构造期注册） | 待评估 | 活跃 |
| `HOME_PLAYTIME_REMINDER` | true | 1.4.1（加法升级第八轮 Batch 11-3，2026-07-19） | 首页今日时长超阈值显示"休息提醒"警示卡片（`PlaytimeReminderHelper.kt` 读取 `GameUsageStore.getTodayPlayTimeMs()`，超 30 分钟显示渐变警示卡片） | 待评估 | 活跃 |
| `GAME_FAVORITE_REORDER` | true | 1.4.1（加法升级第八轮 Batch 11-4，2026-07-19） | 收藏置顶（`GameFavoriteReorderHelper.kt` 排序 Comparator，设置面板提供开关，开启后已收藏游戏在列表中自动排在前面） | 评估使用率后决定 | 待评估 |

### 十、加法升级 Batch 12（4 个，第九轮，2026-07-19）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `HOME_RESUME_GAME_CARD` | true | 1.4.1（加法升级第九轮 Batch 12-1，2026-07-19） | 首页"继续上次游玩"卡片（`ResumeGameHelper.kt` 单例读取 `RecentGamesManager.getRecentIds()[0]`，时间显示用 `GameUsageStore.getLastPlayedAt()` 计算"刚刚/N 分钟前/N 小时前/N 天前"，点击"继续"按钮直接启动游戏） | 待评估 | 活跃 |
| `ACHIEVEMENT_RECENT_UNLOCKED_BANNER` | true | 1.4.1（加法升级第九轮 Batch 12-2，2026-07-19） | 首页"最近解锁成就"横幅（`RecentAchievementHelper.kt` 遍历 `game_achievements` SharedPreferences 查找 `unlock_<id>=true` 且 `unlocked_at_<id>` 最大的项；dismiss 写入 `home_recent_achievement_session` 当日 key） | 待评估 | 活跃 |
| `GAME_PLAY_TIME_BADGE` | true | 1.4.1（加法升级第九轮 Batch 12-3，2026-07-19） | 游戏卡片右上角总游玩时长徽章（`GameCardAdapter.onBindViewHolder` 末尾读取 `GameUsageStore.getTotalPlayTimeMs(gameId)`，按 `>=3600000 → "X.Xh"` / `>=60000 → "Xm"` / `>=1000 → "Xs"` 格式化，半透明绿色 `bg_play_time_badge.xml`） | 待 `fragment_games.xml` 布局修复后验证 | 待评估 |
| `APP_LAUNCH_TIME_DISPLAY` | true | 1.4.1（加法升级第九轮 Batch 12-4，2026-07-19） | 启动耗时显示（`LaunchTimeTracker.kt` 单例使用 `SystemClock.elapsedRealtime()`，`SplashActivity.onCreate` 第一行调用 `markStart()`，`GamesFragment.initTopBar` 中读取 `elapsedMs()` 拼接副标题） | 改为 `BuildConfig.DEBUG` 自动判断后退役 | 待评估 |

### 十一、加法升级 Batch 13（5 个，第十轮，2026-07-19，浏览器模块全面优化）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `BROWSER_GESTURE_NAV` | true | 1.4.1（加法升级第十轮 Batch 13-1，2026-07-19） | 浏览器左右滑动手势导航（`BrowserGestureHelper.java` 双路检测：GestureDetector.onFling + 手动 ACTION_MOVE 跟踪；边缘宽度 32dp，触发阈值 48dp；左边缘右滑 → goBack，右边缘左滑 → goForward；视觉反馈 `bg_gesture_indicator.xml` + 振动反馈 15ms；`setSystemGestureExclusionRects` 声明系统手势排除区域；AndroidManifest 新增 VIBRATE 权限） | 待评估（MIUI 系统手势优先级较高，左滑后退通过 OnBackPressedCallback 链路工作，右滑前进需用户使用 btn_forward 按钮） | 活跃 |
| `BROWSER_FIND_IN_PAGE` | true | 1.4.1（加法升级第十轮 Batch 13-2，2026-07-19） | 页面内查找（`BrowserFindInPageHelper.java` 封装 WebView.findAllAsync + FindListener；顶部 Find Bar 含输入框/上一个/下一个/关闭/匹配计数 `0/0`；debounce 300ms 输入） | 待评估 | 活跃 |
| `BROWSER_READER_MODE` | true | 1.4.1（加法升级第十轮 Batch 13-3，2026-07-19） | 阅读模式（`BrowserReaderModeHelper.java` 通过 evaluateJavascript 注入 JS 提取 `<article>` 或最大文本密度 `<div>/<section>` → loadDataWithBaseURL 渲染美化 HTML；prefers-color-scheme 浅色/深色自适应；行高 1.75 字号 17px；退出时恢复原 URL） | 待评估 | 活跃 |
| `BROWSER_SCREENSHOT` | true | 1.4.1（加法升级第十轮 Batch 13-4，2026-07-19） | 网页截图（`BrowserScreenshotHelper.java` 使用 webView.capturePicture → Bitmap → MediaStore（Android 10+）或 Pictures/BrowserScreenshots 目录；文件名 `Browser_yyyyMMdd_HHmmss.png`） | 待评估 | 活跃 |
| `BROWSER_PROGRESS_SMOOTH` | true | 1.4.1（加法升级第十轮 Batch 13-5，2026-07-19） | 进度条主题色改进（`progressTint="?attr/colorPrimary"` + `progressBackgroundTint="?attr/colorSurfaceVariant"`） | 待评估 | 活跃 |

### 十二、加法升级 Batch 14（16 个，第十一轮，2026-07-19，浏览器模块全面优化 Phase 1-2）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `BROWSER_HOME_PAGE` | true | 1.4.1（加法升级第十一轮 Batch 14-1，2026-07-19） | 浏览器起始页（`BrowserHomeHelper.java` 渲染 `layout_browser_home.xml`，3 种风格：宫格 / 卡片流 / 极简，通过 `BrowserSettingsManager.KEY_HOME_PAGE_STYLE` 切换；默认宫格，含百度/Bing/Google 等快捷入口） | 待评估 | 活跃 |
| `BROWSER_SMART_URL_BAR` | true | 1.4.1（加法升级第十一轮 Batch 14-2，2026-07-19） | 智能 URL Bar（`UrlInputHelper.java` URL 检测 + 搜索引擎选择；长按 et_url 弹出菜单含"搜索引擎"项，4 引擎列表：百度/Bing/Google/DuckDuckGo；`SplashActivity` 转发 `EXTRA_NAV_TAB` 支持 adb deep link 启动） | 待评估 | 活跃 |
| `BROWSER_FORCE_DARK` | true | 1.4.1（加法升级第十一轮 Batch 14-3，2026-07-19） | 夜间模式三档策略（`BrowserSettingsManager.applyDarkMode()` 调用 `WebSettings.setForceDark(int)` API 29+；三档：AUTO 跟随系统 / FORCE_ON / FORCE_OFF；设置页 `row_dark_mode` 点击弹出单选对话框） | 待评估 | 活跃 |
| `BROWSER_TRACKER_PROTECTION` | true | 1.4.1（加法升级第十四轮 Batch 17，2026-07-19） | 追踪保护（已实现：`BrowserTrackerBlocker` 57 条域名黑名单 + 9 条路径关键词 + 白名单 + 第一方域名保护；`BrowserTrackerStats` SharedPreferences 持久化累计/会话/按域名分布；`BrowserWebViewClient.shouldInterceptRequest` 接入拦截；`BrowserSettingsManager.KEY_TRACKER_PROTECTION` 双向同步开关；`PrivacyDashboardActivity` 隐私仪表盘 UI 含统计卡 + Top 域名列表 + 总开关 + 重置统计） | ✅ 已实现 | 稳定 |
| `BROWSER_READING_LIST` | true | 1.4.1（加法升级第十三轮 Batch 16，2026-07-19） | 阅读列表稍后阅读（已实现：Room v3 migration + BrowserReadingListEntity + ReadingListActivity + WebView.evaluateJavascript 摘要提取 + 未读/已读状态切换） | ✅ 已实现 | 稳定 |
| `BROWSER_OFFLINE_CACHE` | true | 1.4.1（加法升级第十五轮 Batch 18，2026-07-19） | 离线缓存 LRU（已实现：`BrowserOfflineCache` LinkedHashMap accessOrder LRU 10 项 + SharedPreferences JSON 持久化；`onPageFinished` 调用 `captureAsync` 通过 evaluateJavascript 提取 outerHTML；`OfflineCacheActivity` 展示列表 + 长按删除 + 清空全部） | ✅ 已实现 | 稳定 |
| `BROWSER_TRANSLATE` | true | 1.4.1（加法升级第十五轮 Batch 18，2026-07-19） | 页面翻译（已实现：`BrowserTranslateHelper` 接入 Google/百度/必应翻译 URL 模式，URLEncoder 编码页面 URL 后通过 ACTION_VIEW 打开翻译页） | ✅ 已实现 | 稳定 |
| `BROWSER_SMART_ZOOM` | true | 1.4.1（加法升级第十五轮 Batch 18，2026-07-19） | 双指缩放改进（已实现：`BrowserZoomHelper` 按 host 持久化字号 50%~200%，SharedPreferences `browser_text_zoom` 文件存储；`applySavedTextZoom` 在 WebView 加载 URL 时应用；`increaseTextZoom`/`decreaseTextZoom`/`resetTextZoom` API） | ✅ 已实现 | 稳定 |
| `BROWSER_VOLUME_SCROLL` | true | 1.4.1（加法升级第十五轮 Batch 18，2026-07-19） | 音量键滚动页面（已实现：`BrowserActivity.onKeyDown/onKeyUp` 拦截 VOLUME_UP/DOWN 调用 `WebView.pageUp/pageDown`；Feature Flag + SettingsManager 双控；设置默认关闭） | ✅ 已实现 | 稳定 |
| `BROWSER_MEDIA_SNIFFER` | true | 1.4.1（加法升级第十五轮 Batch 18，2026-07-19） | 媒体嗅探（已实现：`BrowserMediaSniffer.sniff` 通过 evaluateJavascript 注入脚本提取 `<video>` src、`<source>` src、`<a href=*.pdf>`、`<embed>` PDF 链接，返回 JSON 数组） | ✅ 已实现 | 稳定 |
| `BROWSER_DATA_SAVER` | true | 1.4.1（加法升级第十五轮 Batch 18，2026-07-19） | 数据节省模式（已实现：`BrowserWebViewClient.shouldInterceptRequest` 在 SettingsManager.isDataSaverEnabled 时拦截图片/字体资源，按 URL 后缀判断 jpg/png/gif/webp/svg/woff/woff2/ttf/otf/eot；仅非主框架；设置默认关闭） | ✅ 已实现 | 稳定 |
| `BROWSER_MULTI_FINGER_GESTURE` | true | 1.4.1（加法升级第十五轮 Batch 18，2026-07-19） | 多指手势（已实现：`BrowserMultiFingerGestureHelper` 检测三指点击/双指下拉刷新/边缘长按切 Tab；Callback 接口由调用方实现具体行为） | ✅ 已实现 | 稳定 |
| `BROWSER_TAB_SWITCHER_ANIM` | true | 1.4.1（加法升级第十五轮 Batch 18，2026-07-19） | Tab 切换动画（已实现：`TabManagerActivity` 返回按钮调用 `overridePendingTransition(fade_in, fade_out)`） | ✅ 已实现 | 稳定 |
| `BROWSER_CUSTOM_BOTTOM_BAR` | true | 1.4.1（加法升级第十五轮 Batch 18，2026-07-19） | 底部工具栏可定制（已实现：`BottomBarCustomizeActivity` RecyclerView 列出 8 个底栏按钮，MaterialSwitch 控制显隐，SharedPreferences `browser_bottom_bar_prefs` 持久化；`isVisible` 静态方法供 BrowserFragment 读取） | ✅ 已实现 | 稳定 |
| `BROWSER_SKELETON_LOADING` | true | 1.4.1（加法升级第十五轮 Batch 18，2026-07-19） | 页面加载骨架屏（已实现：`layout_browser_skeleton.xml` 8 个 View 占位块 + `bg_skeleton_block.xml` 圆角灰色背景；`BrowserFragment.onPageStarted` 显示 + `bringToFront`，`onPageFinished` 隐藏） | ✅ 已实现 | 稳定 |
| `BROWSER_REAL_MULTI_TAB` | true | 1.4.1（加法升级第十二轮 Batch 15，2026-07-19） | 真·多 Tab 架构（已实现：BrowserWebViewPool 活跃池上限 5 + LRU 释放 + saveState/restoreState 状态保留；TabManagerActivity 通过 ActivityResultLauncher + setResult 回传操作结果；BrowserController 双模式：单 WebView / 多 Tab 池） | ✅ 已实现 | 稳定 |

### 十三、混合架构改造（5 个，2026-07-20，P0-P6）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `ENABLE_CATALOG_SIGNATURE` | false | 1.4.1（混合架构 P3） | Ed25519 远程目录签名验证（当前兼容模式，默认关闭，验证失败仅记录日志不阻止使用） | 正式上线并稳定后改为 true，再评估是否退役 | 活跃 |
| `ENABLE_TRANSACTIONAL_INSTALL` | true | 1.4.1（混合架构 P3） | 事务性模块安装（`staging/current/last_good/quarantine` 目录结构，原子提升 + 自动回滚） | 长期保留 | 活跃 |
| `ENABLE_P4_DYNAMIC_NAVIGATION` | true | 1.4.1（混合架构 P4） | 模块声明式动态底部导航（`ModuleNavigationContribution` + `BottomNavigationManager`）。2026-07-21 修复兜底逻辑：对已安装但未贡献 `NavigationContribution` 的 browser/ai/vpn 模块，`BottomNavigationManager.refreshNavigation()` 会通过 `ModuleManager.getInstalledModuleIds()` 动态添加 tab，点击时跳转到 `ModuleShellFragment`，保持 6 tab 完整体验 | 长期保留 | 活跃 |
| `ENABLE_P5_STORE_OWNED_UPDATE` | true | 1.4.1（混合架构 P5） | Store-Owned 批量模块更新（远程目录为权威源 + 依赖拓扑排序 + 失败回滚） | 长期保留 | 活跃 |
| `ENABLE_P6_UNITY_MODULE` | true | 1.4.1（混合架构 P6） | Unity 模块架构支持（`UnityModuleLauncher` 接口 + 注册中心 + 占位启动器） | Unity SDK 接入稳定后评估 | 活跃 |

### 十四、首页沉浸式改版（1 个，2026-07-22）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `HOME_IMMERSIVE_REVAMP` | true | 1.4.1（首页沉浸式改版，2026-07-22） | 首页沉浸式 Hero 区 + 卡片流设计：240dp 三色渐变 Hero 背景（融合问候/头像/通知/药丸搜索/统计/今日推荐/继续游玩）、圆角 20dp 卡片流、空状态引导卡片（`resumeGameSection` 空数据时显示引导文案而非隐藏）。重写 `fragment_games.xml` 保留全部 36 个原 id，新增 `home_immersive_*` 色板（浅色/深色双适配）+ 6 个 drawable。`GamesFragment.refreshResumeGameCard()` 空状态走引导分支 | Flutter 化首页接入后评估退役 | 活跃 |

### 十五、首页 V2 游戏活力风重设计（1 个，2026-07-22）

| Flag 名称 | 默认值 | 引入版本 | 用途 | 退役计划 | 状态 |
|-----------|--------|----------|------|----------|------|
| `HOME_REVAMP_V2` | true | 1.4.1（首页 V2 游戏活力风重设计，2026-07-22） | 首页 V2 游戏霓虹风重设计：3 层信息架构（L1 沉浸式 Hero 区 / L2 快速入口带 / L3 游戏列表区），霓虹配色系统（浅色 深紫 #6B2FB3 → 品红 #E91E63 → 暖橙 #FF6B35；深色 深紫黑 #1A0B2E → 品红紫 #4A1A5E → 霓虹紫 #7C4DFF）。今日推荐大图卡（160dp，游戏图标 alpha 0.35 背景 + 底部渐变蒙版 + 徽章/名称/描述/立即开玩按钮）。重写 `fragment_games.xml`（保留全部 37 个原 id）+ `layout_home_game_of_day.xml`，新增 `home_v2_*` 色板（浅色/深色双适配）+ 8 个 drawable + 1 个 anim（Hero 进入动画）。`GamesFragment.refreshResumeGameCard()` 空状态走 V2 引导分支。Hero Banner 轮播 / 最近成就 section 在 V2 中弱化为 GONE 占位以保留 id 兼容 | Flutter 化首页接入后评估退役 | 活跃 |

---

## 治理规则

### 1. Flag 生命周期
- **引入**：必须有明确用途和引入版本
- **灰度**：默认 false，按需开启
- **稳定**：默认 true，全量生效
- **退役**：删除 Flag 引用 + 删除条件分支代码（保留功能）

### 2. 退役标准
- 超过 3 个月未关闭的 Flag = 应该删除代码
- 同一 Flag 在 2 次以上版本中未引起问题 = 应该删除
- 实验性 Flag 默认 false，3 个月内未启用 = 删除

### 3. 命名规范
- 全大写 + 下划线
- 前缀按模块：`HOME_` / `GAME_` / `ACHIEVEMENT_` / `BROWSER_` / `NAV_` / `ANIM_` / `SETTINGS_` / `DATA_` / `SPLASH_` / `PROFILE_` / `VISUAL_` / `CARD_` / `EMPTY_` / `SEARCH_` / `DAILY_` / `APP_` / `THEME_` / `RANDOM_` / `WRONGBOOK_` / `GOMOKU_` / `ENABLE_` / `TEST_`

---

## 待评估 Flag 清单（基于已知问题）

基于 `docs/AI_CONTEXT.md` 与项目历史已知问题：

| Flag | 已知问题 | 建议处理 |
|------|----------|----------|
| `GAME_PLAY_TIME_BADGE` | 实现了但 UI 不可见（`fragment_games.xml` 顶部内容堆叠把 `tab_layout`/`rv_games` 推到屏幕外），徽章虽正确实现但暂未在 UI dump 中验证到 | **已修复**（2026-07-19 把 `fragment_games.xml` 改为 `FrameLayout` + `NestedScrollView` 包裹，详见 `docs/AI_CONTEXT.md`）；待模拟器 UI 验证徽章可见后再决定是否退役 |
| `APP_LAUNCH_TIME_DISPLAY` | 仅 Debug 用途，但默认 true（Release 也会显示启动毫秒数） | 建议改为 `BuildConfig.DEBUG` 自动判断后退役此 Flag |
| `NAV_BADGE_UNREAD` | 计算逻辑复杂（每日挑战未完成 + 今日未签到 + 连胜为 0 三项求和），需评估用户价值 | 评估用户价值与点击率后决定是否保留 |
| `GAME_FAVORITE_REORDER` | 设置面板开关，需评估使用率 | 通过 SettingsManager 日志或埋点评估使用率后决定 |
| `BROWSER_JS_BRIDGE_ENABLED` | 默认 false，3 个月内未启用 | 按退役标准可直接删除（实验性未启用） |
| `BROWSER_WEBVIEW_DEBUG` | 默认 false，仅 Debug 用途 | 改为 `BuildConfig.DEBUG` 自动判断后退役 |
| `GOMOKU_ENHANCED` | 已稳定运行多个版本 | 已满足退役标准（2 次以上版本未引起问题），可考虑删除 Flag 引用并保留功能 |

---

## 操作指引

### 新增 Flag
1. 在 `app/build.gradle` `defaultConfig` 中添加 `buildConfigField "boolean", "FLAG_NAME", "true|false"`
2. 在本索引表中添加条目（注明引入版本、用途、退役计划）
3. 在代码中通过 `BuildConfig.FLAG_NAME` 引用
4. 默认值选择：
   - 主功能 / 已确定要上的功能：`true`
   - 实验性 / 灰度 / 调试用途：`false`

### 关闭 Flag
1. 在 `app/build.gradle` 中将值改为 `"false"`
2. 重新编译验证：
   ```powershell
   .\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace
   ```
3. 在本索引表中更新状态为"已关闭"

### 退役 Flag
1. 删除 `app/build.gradle` 中的 `buildConfigField` 行
2. 删除代码中所有 `BuildConfig.FLAG_NAME` 引用（**保留** Flag 控制的功能代码，只删除条件分支）
3. 在本索引表中更新状态为"已退役" + 退役日期
4. 退役后建议在 `修改记录.md` 中记录退役批次

### 验证清单（退役前必做）
- [ ] 全代码库 grep `BuildConfig.FLAG_NAME` 确认所有引用已删除
- [ ] `.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace` 构建通过
- [ ] 受影响 UI 流程在模拟器上跑通
- [ ] `adb logcat` 检查无 `FATAL EXCEPTION` / `Resources$NotFoundException` / `InflateException`

---

## 数据来源

- `app/build.gradle` 第 194–307 行（`defaultConfig` 中所有 `buildConfigField "boolean"` 声明）
- `docs/AI_CONTEXT.md` 第 304–374 行（Feature Flag 章节，含九轮加法升级详细说明）
- 实际总数：**76 个 boolean Flag**（基础设施 11 + 加法升级 58 + 混合架构 5 + 首页沉浸式改版 1 + 首页 V2 游戏活力风重设计 1），全部声明于 `defaultConfig`，`buildTypes` 中无覆盖

---

[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
