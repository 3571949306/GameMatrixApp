?# AI_CONTEXT.md �?GameMatrix App 项目上下�?
> 本文档供后续 AI 编程助手（Trae / Codex / Claude 等）阅读�?> 目标：让 AI 读完后能安全上手，不会因为不了解项目背景而乱改代码�?
---

## 2. 项目概览

**项目用�?*：一个集�?2 款内置经典游戏的 Android 游戏中心 App，支持通过模块市场按需下载扩展游戏、工具箱、浏览器、AI 助手和科学上网（VPN）�?
> 2026-05-24 更新：新安装包默认游戏收敛为五子棋和斗地主；模块市场默认进入"游戏"分类，右上角提供刷新和已下载模块列表，下载/打开的游戏会注册回大厅。模块清单以 `deploy/modules.json` 和 `app/src/main/assets/modules.json` 为准。华容道和中国象棋已创建独立 APK 模块（feature/games/klotski、feature/games/chinesechess）并上架模块商店，v2.0.0。中国象棋提示功能改为棋盘可视化（蓝色脉冲光环+箭头指引）+中文棋谱描述。 2026-05-25 更新：模块框架全链路修复——ModuleLoader 版本感知重加载+DEX缓存清理、ModuleDownloader 下载前清理旧文件+多源切换清理临时文件、ModuleVerifier 资源泄漏修复、ModuleAdapter 新增"更新"按钮逻辑、ModuleStoreActivity 已安装版本追踪+乱码修复。模块更新后校验错误/打不开/不更新显示三大问题已修复。
**当前状�?*�?- 主版本（`games.doudizhu`）：稳定运行，支持局域网 TCP + HTTP Relay + WebSocket 三模联机
- 5 个游戏支持云联机：斗地主、五子棋、中国象棋、围棋、石头剪刀�?- 公共网络模块（`com.GameMatrix.app.network`）：所有联机游戏共�?- 其余 20+ 款游戏为单机模式
- **APK 签名配置已修�?*：Release 构建自动签名，支持正式发布（v1.3.17�?- **自动更新源选择已修�?*：版本比较逻辑正确，可检测新版本
- **自动化发布流�?*：一键上传到 HK VPS、US VPS、GitHub Releases
- **Kotlin 升级**�?.9.25 �?2.1.10
- **Hilt 升级**�?.52 �?2.55
- **JSON 序列化替�?*：GameUsageStore 手工 JSON 改为 Gson 2.11.0
- **日志工具统一**：删�?util/Log.java，统一使用 AppLog (Extensions.kt)
- **Play Store 描述更新**：README 首部版本徽标同步�?1.3.20
- **AI 助手入口**：独立底部导�?AI 页面，支�?7 种任务、历史搜�?��收藏、导出和常用模板
- **�?AI 提供商支�?*：默�?DeepSeek API，可选阿里云通义、硅基流动、智�?AI、零一万物、OpenAI（全�?OpenAI 兼容接口�?
**当前版本**：v1.4.0 (versionCode=343)

**重点模块**�?1. **公共联机网络模块** �?`com.GameMatrix.app.network` 包，包含 GameSocketClient/Server/LANManager/RelayHttpClient/RemoteP2PUtil/OnlineChatHelper/OnlineRoomManager
2. **应用更新模块** �?三级下载源（GitHub Releases �?香港 VPS �?美国 VPS），UpdateViewModel（@HiltViewModel + LiveData）替�?UpdatePresenter
3. **游戏大厅** �?GamesFragment + GameRegistry（三轨注册：静态 + @GameEntry 注解 + 动态注册）管理 2 款内置游戏 + 市场下载扩展游戏，模块市场入口位于版本号下方
4. **工具�?* �?ToolsFragment 包含 20+ 网络/设备工具，使�?ToolBinder 架构
5. **APK 签名模块** �?keystore.properties + GameMatrix.keystore + signingConfigs（已修复�?6. **自动化发�?* �?upload_to_vps.py + upload_to_github_release.py + auto-publish.bat
7. **统一错误模型** �?AppError（密封类�? NetworkResult（类型安全结果封装）
8. **模块市场系统** �?ModuleStoreActivity + ModuleManager + ModuleManifest，支持浏览器/工具�?AI/VPN 与游戏模块按需下载安装，已下载模块列表支持分类筛选，右上角有刷新和已下载模块入口；底部导航栏根据已安装模块动态显�?8. **DI 迁移** �?SettingsManager/OkHttpClientProvider/UpdateManager/SaveManager 已加 @Inject 构造函�?9. **类型安全枚举** �?TaskStatus 替代 AiTask.status 字符串，AiErrorCode 替代 AiResult.errorCode 裸字符串
10. **国际化推�?* �?OnlineRoomManager + AppSettingsDialog 硬编码文案已提取�?strings.xml�?8 个资源）

---

## 2. 完整文件索引

### 2.1 根包 / com.GameMatrix.app

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| App.java | Application 入口，全局初始化主题和配色 | Android 系统（Manifest 注册�?| SettingsManager, ColorSchemeManager, UpdateManager |
| MainActivity.java | 主界面，底部导航�?+ UpdateViewModel 更新检�?| Android 系统（Launcher�?| UpdateViewModel, SettingsManager, GamesFragment, ToolsFragment, BrowserFragment, AppSettingsDialog |
| SettingsManager.java | SharedPreferences 封装，管理所有用户设置（@Inject 构造函数） | MainActivity, App, AppSettingsDialog, DouDiZhuOnlineActivity, UpdateManager, UpdateViewModel | �?|
| ColorSchemeManager.java | 主题配色管理（亮�?暗色/跟随系统�?| App, MainActivity | �?|

### 2.2 Fragments / com.GameMatrix.app.fragments

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| GamesFragment.java | 游戏大厅，RecyclerView 展示游戏列表，模块市场入口（版本号下方按钮） | MainActivity | GameRegistry, GameUsageStore, GameTutorialHelper, 各游�?Activity |
| ToolsFragment.java | 工具箱，20+ 网络/设备/编码工具 | MainActivity | ToolSectionStore, ToolSection, AdvancedToolBinders, HashToolBinder, ColorPickerToolBinder, ClipboardToolBinder, SystemInfoCollector, ColorSVPanel, ColorHueBar, ColorAlphaBar |
| BrowserFragment.java | 内置浏览�?| MainActivity | 无（独立 WebView�?|

### 2.3 Games 注册中心 / com.GameMatrix.app.games

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| GameRegistry.java | 游戏元数据注册中心（双轨制：静�?+ @GameEntry + 动态注册） | GamesFragment | 各游�?Activity 类（仅引用，不实例化�?|
| GameEntry.java | @GameEntry 注解，游戏自声明元数据（id/iconRes/nameRes/descRes/category�?| GameRegistry（扫描） | �?|
| GameUsageStore.java | 游戏使用次数/收藏状态存�?| GamesFragment | �?|
| GameTutorialHelper.java | 游戏教程弹窗管理 | 各游�?Activity（showXxxTutorial�?| �?|

### 2.3a 模块市场 / com.GameMatrix.app.modules

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| ModuleManager.kt | 模块管理器（下载/安装/卸载/动态注册游戏/注销游戏） | GamesFragment, ModuleStoreActivity | ModuleManifest, ModuleLoader, ModuleDownloader, GameRegistry |
| ModuleManifest.kt | 模块清单数据模型（含type/builtIn/storeCategory/isBaseFramework�?| ModuleManager, ModuleAdapter | �?|
| ModuleLoader.kt | 模块加载器（DexClassLoader 动态加载） | ModuleManager | �?|
| ModuleDownloader.kt | 模块下载器（多源/断点续传/进度/SHA-256 校验�?| ModuleManager | ModuleVerifier |
| ModuleVerifier.kt | 文件完整性校验（SHA-256 + DEX magic bytes�?| ModuleDownloader | �?|
| ModuleAdapter.kt | 模块列表适配�?| ModuleStoreActivity | ModuleManifest |
| InstalledModulesActivity.kt | 已下载模块列表页�?| ModuleStoreActivity（Intent�?| ModuleManager, InstalledModuleAdapter |
| InstalledModuleAdapter.kt | 已下载模块列表适配�?| InstalledModulesActivity | ModuleManifest |
| BrowserModuleEntryPoint.kt | 浏览器模块入口（EXTRA_NAV_TAB="browser"�?| ModuleManager | MainActivity |
| ToolsModuleEntryPoint.kt | 工具箱模块入口（EXTRA_NAV_TAB="tools"�?| ModuleManager | MainActivity |
| AiModuleEntryPoint.kt | AI助手模块入口（EXTRA_NAV_TAB="ai"�?| ModuleManager | MainActivity |
| ModuleStoreActivity.kt | 模块市场页面（默认游戏分类、刷新、已下载模块入口、下载/打开/卸载） | GamesFragment（Intent） | ModuleManager, ModuleAdapter |

### 2.3b VPN 模块 / com.GameMatrix.app.vpn（feature 模块 + �?APK 壳）

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| VpnServiceProxy.kt (app) | VPN 服务桥接（TUN 隧道，委�?VpnDelegate�?| Android 系统 | VpnDelegate (core:common), ModuleManager |
| ModuleShellFragment.kt (app) | 动态模块宿�?Fragment（未安装时引导） | Navigation Component | FeatureModule (core:common), ModuleManager |
| VpnModuleEntryPoint.kt (feature) | VPN 模块入口（实�?ModuleInterface + FeatureModule + VpnDelegate�?| ModuleLoader | ProtocolFactory, VpnFragment |
| VpnFragment.kt (feature) | VPN 主界面（节点列表/添加/连接，纯代码 UI�?| ModuleShellFragment | NodeRepository, NodeAdapter |
| ProtocolModule.kt (feature) | 协议统一接口 | ProtocolFactory | 各协议实�?|
| ProtocolFactory.kt (feature) | 协议工厂（内�?�?+ DexClassLoader 扩展�?| VpnModuleEntryPoint | Shadowsocks/Vmess/Vless/TrojanModule |

### 2.4 斗地�?/ com.GameMatrix.app.games.doudizhu

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| DouDiZhuMenuActivity.java | 菜单界面（单�?联机/远程 P2P�?| GameRegistry | DouDiZhuActivity, DouDiZhuOnlineActivity |
| DouDiZhuActivity.java | 游戏主界�?| DouDiZhuMenuActivity | DouDiZhuTableView, AIBot, DouDiZhuSoundManager, GameRuleUtil, model.* |
| DouDiZhuOnlineActivity.java | 联机对战界面（三模：TCP/HTTP/WS�?| DouDiZhuMenuActivity | GameSocketClient, GameSocketServer, LANManager, RelayHttpClient, RemoteP2PUtil, DouDiZhuTableView, DouDiZhuSoundManager |
| DouDiZhuTableView.java | 牌桌自定�?View | DouDiZhuActivity, DouDiZhuOnlineActivity | model.Card |
| DouDiZhuSoundManager.java | 音效播放管理 | DouDiZhuActivity, DouDiZhuOnlineActivity | 无（直接操作 MediaPlayer�?|
| AIBot.java | AI 出牌决策逻辑 | DouDiZhuActivity | model.Card, GameRuleUtil |
| model/Card.java | 扑克牌数据模�?| AIBot, GameRuleUtil, DouDiZhuTableView, DouDiZhuActivity | model.Rank, model.Suit |
| model/CardType.java | 牌型枚举（单�?对子/顺子/炸弹等） | GameRuleUtil, AIBot | �?|
| model/Rank.java | 牌面大小枚举 | Card | �?|
| model/Suit.java | 花色枚举 | Card | �?|
| utils/GameRuleUtil.java | 牌型判定、洗牌发牌、合法性检�?| AIBot, DouDiZhuActivity | model.Card, model.CardType |
| network/GameSocketClient.java | 客户端网络（TCP + HTTP Relay + WebSocket 三模�?| DouDiZhuOnlineActivity | RelayHttpClient, LANManager, BuildConfig |
| network/GameSocketServer.java | 服务器端网络（TCP + HTTP Relay + WebSocket 三模�?| DouDiZhuOnlineActivity | RelayHttpClient, LANManager, BuildConfig |
| network/LANManager.java | NSD 局域网服务发现 | DouDiZhuOnlineActivity | �?|
| network/RelayHttpClient.java | HTTP Relay 通信 + WebSocket URL 生成 | DouDiZhuOnlineActivity | BuildConfig |
| network/RemoteP2PUtil.java | 房间码规范化、P2P 地址格式化与解析 | DouDiZhuOnlineActivity | �?|

### 2.5 联机游戏 / com.GameMatrix.app.games.{gomoku,chinesechess,go,rock}

以下 4 个游戏支持云联机，使用公共网络模�?`com.GameMatrix.app.network`�?
| 游戏 | OnlineActivity | 协议前缀 | P2P_PREFS | 棋盘 View |
|------|---------------|---------|-----------|----------|
| 五子�?| GomokuOnlineActivity | GMK:// | gomoku_p2p | GomokuView（复用单机） |
| 中国象棋 | ChineseChessOnlineActivity | XQ:// | xiangqi_p2p | ChineseChessView（复用单机） |
| 围棋 | GoOnlineActivity | GO:// | go_p2p | GoView（复用单机） |
| 石头剪刀�?| RockOnlineActivity | ROCK:// | rock_p2p | 无棋�?|

每个游戏包通常包含�?- `XxxActivity.java` �?单机游戏入口（被 GameRegistry 引用�?- `XxxOnlineActivity.java` �?联机对战界面
- `XxxView.java` �?`XxxGame.java` �?游戏逻辑与绘�?- 部分游戏�?`XxxAI.java`（如 GomokuAI, ChineseChessAI�?
### 2.6 其他游戏 / com.GameMatrix.app.games.*

其余 20+ 款游戏为单机模式，无联机功能。包括：blackjack、breakout、brotato、checkers、dice、flappy、game2048、guess、klotski、match、memory、pipeline、plane、reaction、snake、sokoban、sudoku、tetris、tic、tiles、whack

### 2.7 公共网络模块 / com.GameMatrix.app.network

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| GameSocketClient.java | 客户端连接管理（TCP + HTTP Relay + WebSocket 三模�?| 所有联�?OnlineActivity | RelayHttpClient, LANManager, BuildConfig |
| GameSocketServer.java | 房主权威服务器（TCP + HTTP Relay + WebSocket 三模�?| 所有联�?OnlineActivity | RelayHttpClient, LANManager, BuildConfig |
| LANManager.java | NSD 局域网服务发现 | GameSocketClient, GameSocketServer | �?|
| RelayHttpClient.java | HTTP Relay 通信 + WebSocket URL 生成 | GameSocketClient, GameSocketServer | BuildConfig |
| RemoteP2PUtil.java | 房间码规范化、P2P 地址格式化与解析 | �?OnlineActivity | �?|
| OnlineChatHelper.java | 可复用联机聊天组件（支持内联模式和弹窗模式） | 所有联�?OnlineActivity | �?|
| OnlineRoomManager.java | 联机房间管理器（组合式复用，替代 BaseOnlineActivity 继承�?| 各联�?OnlineActivity | GameSocketClient, GameSocketServer, OnlineChatHelper |

### 2.8 工具�?/ com.GameMatrix.app.tools

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| ToolBinder.java | 工具绑定器接�?| ToolsFragment, �?ToolBinder | �?|
| ToolHelper.java | 工具通用辅助方法 | �?ToolBinder | �?|
| ToolSectionStore.java | 工具分类与排序持久化 | ToolsFragment | ToolSection |
| ToolSection.java | 工具分类数据模型 | ToolSectionStore, ToolsFragment | �?|
| AdvancedToolBinders.java | 高级工具绑定（被�?Binder 类调用） | NetworkDiagnosisToolBinder �?| 大量 Android API |
| NetworkDiagnosisToolBinder.java | 网络体检工具 | ToolsFragment | AdvancedToolBinders |
| DiagnosticReportToolBinder.java | 诊断报告工具 | ToolsFragment | AdvancedToolBinders |
| DnsLookupToolBinder.java | DNS 查询工具 | ToolsFragment | AdvancedToolBinders |
| LanScanToolBinder.java | 局域网扫描工具 | ToolsFragment | AdvancedToolBinders |
| TextCodecToolBinder.java | 编码/时间�?JSON 工具 | ToolsFragment | AdvancedToolBinders |
| FileHashToolBinder.java | 文件哈希工具 | ToolsFragment | AdvancedToolBinders |
| QrPlusToolBinder.java | 二维码增强工�?| ToolsFragment | AdvancedToolBinders |
| ColorPlusToolBinder.java | 颜色增强工具 | ToolsFragment | AdvancedToolBinders |
| PermissionPrivacyToolBinder.java | 权限隐私说明工具 | ToolsFragment | AdvancedToolBinders |
| IpToolBinder.java | IP 地址查询工具 | ToolsFragment | ToolHelper |
| DnsToolBinder.java | DNS 服务器查询工�?| ToolsFragment | ToolHelper |
| WifiToolBinder.java | WiFi 信号信息工具 | ToolsFragment | ToolHelper |
| SpeedTestToolBinder.java | 网络测速工�?| ToolsFragment | ToolHelper |
| PortScanToolBinder.java | 端口扫描工具 | ToolsFragment | ToolHelper |
| QrToolBinder.java | 二维码生�?识别工具 | ToolsFragment | ZXing |
| BatteryToolBinder.java | 电池信息工具 | ToolsFragment | BatteryManager |
| DeviceToolBinder.java | 设备信息工具 | ToolsFragment | Build |
| PingToolBinder.java | Ping 工具 | ToolsFragment | ToolHelper |
| TracerouteToolBinder.java | 路由追踪工具 | ToolsFragment | ToolHelper |
| SubnetToolBinder.java | 子网计算�?| ToolsFragment | ToolHelper |
| ScreenToolBinder.java | 屏幕信息工具 | ToolsFragment | DisplayMetrics |
| SensorToolBinder.java | 传感器信息工�?| ToolsFragment | SensorManager |
| HashToolBinder.java | 哈希计算工具（MD5/SHA1/SHA256�?| ToolsFragment | �?|
| ClipboardToolBinder.java | 剪贴板历史工�?| ToolsFragment | ClipboardManager |
| ColorPickerToolBinder.java | 颜色取色器工�?| ToolsFragment | ColorSVPanel, ColorHueBar, ColorAlphaBar |
| SystemInfoToolBinder.java | 手机系统详细信息 | ToolsFragment | Build, SystemProperties |

### 2.8 更新模块 / com.GameMatrix.app.update

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| UpdateManager.java | 更新检查、下载、安装管理（@Inject 构造函数） | MainActivity, App, UpdateViewModel | UpdateInfo, SSLHelper, SettingsManager, BuildConfig |
| UpdateViewModel.kt | 更新流程 ViewModel（@HiltViewModel + LiveData + 协程，viewModelScope.launch + suspendCancellableCoroutine 包装 Java 回调�?| MainActivity | UpdateManager, SettingsManager |
| UpdateInfo.java | 版本信息数据模型 | UpdateManager, MainActivity | �?|
| SSLHelper.java | SSL 证书信任（仅针对更新服务器域名启用） | UpdateManager | �?|
| UpdatePresenter.java | 更新展示器（已废弃，�?UpdateViewModel 替代�?| �?| �?|
| AiApiClientTest.java | AI API 客户�?MockWebServer 测试�? 个方法） | CI | AiApiClient |
| UpdateInfoTest.java | 更新信息 JSON 解析测试�?7 个方法） | CI | UpdateInfo |

### 2.9 Kotlin 工具 / com.GameMatrix.app.util (Kotlin)

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| AppError.kt | 统一错误模型（密封类�?0 种错误类型） | NetworkResult, 网络�?| �?|
| NetworkResult.kt | 网络请求结果封装（基�?AppError�?| 网络请求调用�?| AppError |
| AppResult.kt | 通用结果封装（重命名�?Result.kt，避免与 kotlin.Result 冲突�?| CrashHandler | �?|
| SaveManager.kt | 存档管理器（@Singleton + @Inject�?| 各游�?Activity | �?|

### 2.9 自定�?View / com.GameMatrix.app.views

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| ColorSVPanel.java | 颜色饱和�?明度选择面板 | ColorPickerToolBinder | �?|
| ColorHueBar.java | 颜色色相�?| ColorPickerToolBinder | �?|
| ColorAlphaBar.java | 颜色透明度条 | ColorPickerToolBinder | �?|

### 2.10 通用工具 / com.GameMatrix.app.utils

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| SystemInfoCollector.java | 设备硬件/软件信息收集 | ToolsFragment | �?|
| PermissionHelper.java | 权限管理辅助，处理首次启动权限说明和运行时权限请�?| MainActivity | ActivityResultLauncher, Build.VERSION |

### 2.11 设置 / com.GameMatrix.app.settings

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| AppSettingsDialog.java | 设置弹窗（主�?更新/反馈/关于�?| MainActivity, GamesFragment | SettingsManager, ColorSchemeManager |

### 2.12 AI 数据模型 / com.GameMatrix.app.ai.model

| 文件路径 | 类职�?| 被谁调用 | 调用了谁 |
|---------|--------|----------|----------|
| AiMessage.java | AI 聊天消息数据模型 | AiFragment, AiViewModel | �?|
| AiTask.java | AI 任务数据模型 | AiViewModel, AiFragment | TaskStatus |
| TaskStatus.java | 任务状态枚举（替代 AiTask.status 字符串） | AiTask | �?|
| AiResult.java | AI 结果数据模型 | AiViewModel | AiErrorCode |
| AiErrorCode.java | 错误码常量类（替�?AiResult.errorCode 裸字符串�?| AiResult | �?|
| AiProviderConfig.java | AI 提供商配置数据模�?| AiViewModel, AiSettingsDialog | �?|

---

## 3. 依赖清单

### 3.1 主要依赖

| 依赖 | 用�?| 说明 |
|------|------|------|
| androidx.activity:activity | ActivityResultLauncher 权限请求 | 必须包含，用�?`PermissionHelper` 的运行时权限处理 |
| androidx.appcompat | AppCompatActivity 兼容 | 核心支持�?|
| androidx.recyclerview | RecyclerView | 游戏列表/工具箱列表展�?|
| androidx.navigation:navigation-fragment | 导航组件 | 可能未使用（详见 7.6�?|
| androidx.webkit:webkit | WebView 增强 | 可能未使用（详见 7.6�?|
| com.google.zxing:core | 二维码生�?识别 | QrToolBinder 使用 |

### 3.2 Debug 依赖

| 依赖 | 用�?| 说明 |
|------|------|------|
| com.squareup.leakcanary:leakcanary-android:2.14 | 内存泄漏检�?| `debugImplementation`，自动检�?Activity/Fragment 泄漏 |

---

## 4. 核心架构与约�?
### 4.1 权限管理约定

- **首次启动权限说明**：App 首次启动时弹出权限说明对话框，向用户解释所需权限的用�?- **用户选择�?*：用户可选择立即授权或暂不授权，暂不授权不影�?App 基础功能使用
- **实现方式**：通过 `PermissionHelper` + `ActivityResultLauncher` 处理运行时权限请�?- **权限状态持久化**：已授权状态通过 SharedPreferences 记录，避免重复弹�?
### 4.2 构建与混淆约�?
- **Release 构建已启�?R8/ProGuard 混淆**：所有发布版�?APK 均经过代码混�?- **Keep 规则**：`com.GameMatrix.app.**` 包下的所有类已配�?keep 规则，防止被混淆
- **原因**：游戏注册、反射调用、JNI 交互等场景需要保持类�?方法名不�?
### 4.3 模块调用约定

- **GameRegistry 双轨注册**：游�?Activity 类可通过 @GameEntry 注解自声明元数据，也可在 GameRegistry 中静态硬编码或动态注�?- **联机游戏共享网络模块**：所有联机游戏使�?`com.GameMatrix.app.network` 包中的公共网络组件；推荐使用 `OnlineRoomManager` 组合式复用，而非继承 `BaseOnlineActivity`
- **工具 Binder 架构**：每�?ToolBinder 独立实现，通过 ToolBinder 接口�?ToolsFragment 解�?
### 4.4 网络错误处理约定

- **统一使用 NetworkErrorHandler**：所有网络请求的错误处理必须通过 NetworkErrorHandler 统一处理
- **禁止混用 Toast/日志**：不要在不同位置随意使用 Toast 弹出�?Log 打印，统一交给 NetworkErrorHandler 处理
- **错误分类**：网络超时、连接失败、HTTP 错误码等需分类处理，给出明确的用户提示

### 4.5 国际化约�?
- **用户可见文本提取�?strings.xml**：所有面向用户的界面文本必须提取�?`res/values/strings.xml`
- **支持中英文双�?*：同时提�?`res/values-zh/strings.xml`（中文）�?`res/values/strings.xml`（英文默认）
- **禁止硬编码文�?*：不要在布局文件�?Java 代码中直接写死用户可见的文本内容

### 4.6 内存泄漏约定

- **Debug 版集�?LeakCanary**：Debug 构建自动集成 LeakCanary 2.14，使�?`debugImplementation` 依赖
- **自动检�?*：LeakCanary 自动检�?Activity/Fragment/View 泄漏，无需手动配置
- **修复原则**：发现泄漏后优先检查未取消的监听器、未关闭�?Handler、静态引用等问题

### 4.7 CI/CD 约定

- **GitHub Actions 工作�?*：CI/CD 配置定义�?`.github/workflows/ci.yml`
- **自动化流�?*：Push/PR 触发构建、Lint 检查、单元测试（如有�?- **CI 质量�?*：APK 大小报告、测试结果报告、Android Lint 执行�?Lint 问题报告
- **本地�?CI 一致�?*：确保本地构建与 CI 环境使用相同�?Gradle 参数和检查规�?
### 4.8 Lint 规则约定

- **Release 构建强制检�?*：Lint 配置�?`abortOnError = true`，`warningsAsErrors = true`
- **禁止忽略警告**：所�?Lint 警告必须修复，不得通过 `tools:ignore` 粗暴屏蔽
- **CI 集成**：CI 工作流包�?Lint 检查步骤，失败则阻断合�?
---

## 5. 模块依赖关系�?
```mermaid
graph TD
    subgraph Android系统
        Manifest[AndroidManifest.xml]
    end

    subgraph 应用�?        App[App.java]
        MainActivity[MainActivity.java]
        SettingsManager[SettingsManager.java]
        ColorSchemeManager[ColorSchemeManager.java]
    end

    subgraph Fragments
        GamesFragment[GamesFragment]
        ToolsFragment[ToolsFragment]
        BrowserFragment[BrowserFragment]
    end

    subgraph 游戏注册
        GameRegistry[GameRegistry.java]
        GameUsageStore[GameUsageStore.java]
        GameTutorialHelper[GameTutorialHelper.java]
    end

    subgraph 斗地�?        DDZ_Menu[DouDiZhuMenuActivity]
        DDZ_Activity[DouDiZhuActivity]
        DDZ_Online[DouDiZhuOnlineActivity]
        DDZ_Network[GameSocketClient<br/>GameSocketServer<br/>LANManager<br/>RelayHttpClient<br/>RemoteP2PUtil]
    end

    subgraph 斗地主公�?        DDZ_TableView[DouDiZhuTableView]
        DDZ_Sound[DouDiZhuSoundManager]
        DDZ_AIBot[AIBot]
        DDZ_RuleUtil[GameRuleUtil]
        DDZ_Model[Card/CardType<br/>Rank/Suit]
    end

    subgraph 联机游戏
        Gomoku[GomokuOnlineActivity]
        ChineseChess[ChineseChessOnlineActivity]
        Go[GoOnlineActivity]
        Rock[RockOnlineActivity]
        OnlineChat[OnlineChatHelper]
    end

    subgraph 其他20款游�?        OtherGames[各游�?Activity/View/Game]
    end

    subgraph 工具�?        ToolSectionStore[ToolSectionStore]
        ToolBinder[ToolBinder接口]
        NetworkDiagnosis[NetworkDiagnosisToolBinder]
        DiagnosticReport[DiagnosticReportToolBinder]
        DnsLookup[DnsLookupToolBinder]
        LanScan[LanScanToolBinder]
        TextCodec[TextCodecToolBinder]
        FileHash[FileHashToolBinder]
        QrPlus[QrPlusToolBinder]
        ColorPlus[ColorPlusToolBinder]
        PermissionPrivacy[PermissionPrivacyToolBinder]
        OtherBinders[其他17个Binder]
        ColorViews[ColorSVPanel<br/>ColorHueBar<br/>ColorAlphaBar]
    end

    subgraph 更新模块
        UpdateManager[UpdateManager<br/>@Inject 构造函数]
        UpdateViewModel[UpdateViewModel<br/>@HiltViewModel + LiveData]
        UpdateInfo[UpdateInfo]
        SSLHelper[SSLHelper]
    end

    subgraph 设置
        AppSettingsDialog[AppSettingsDialog]
    end

    Manifest --> App
    Manifest --> MainActivity
    App --> SettingsManager
    App --> ColorSchemeManager
    App --> UpdateManager

    MainActivity --> GamesFragment
    MainActivity --> ToolsFragment
    MainActivity --> BrowserFragment
    MainActivity --> UpdateViewModel
    MainActivity --> SettingsManager
    UpdateViewModel --> UpdateManager
    MainActivity --> SettingsManager
    MainActivity --> AppSettingsDialog

    GamesFragment --> GameRegistry
    GamesFragment --> GameUsageStore
    GamesFragment --> GameTutorialHelper
    GamesFragment --> AppSettingsDialog

    GameRegistry --> DDZ_Menu
    GameRegistry --> Gomoku
    GameRegistry --> ChineseChess
    GameRegistry --> Go
    GameRegistry --> Rock
    GameRegistry --> OtherGames

    DDZ_Menu --> DDZ_Activity
    DDZ_Menu --> DDZ_Online
    DDZ_Online --> DDZ_Network

    DDZ_Activity --> DDZ_TableView
    DDZ_Activity --> DDZ_AIBot
    DDZ_Activity --> DDZ_Sound
    DDZ_Activity --> DDZ_RuleUtil
    DDZ_Activity --> DDZ_Model

    DDZ_Online --> DDZ_TableView
    DDZ_Online --> DDZ_Sound

    Gomoku --> OnlineChat
    ChineseChess --> OnlineChat
    Go --> OnlineChat
    Rock --> OnlineChat

    DDZ_AIBot --> DDZ_RuleUtil
    DDZ_AIBot --> DDZ_Model

    ToolsFragment --> ToolSectionStore
    ToolsFragment --> ToolBinder
    ToolBinder --> NetworkDiagnosis
    ToolBinder --> DiagnosticReport
    ToolBinder --> DnsLookup
    ToolBinder --> LanScan
    ToolBinder --> TextCodec
    ToolBinder --> FileHash
    ToolBinder --> QrPlus
    ToolBinder --> ColorPlus
    ToolBinder --> PermissionPrivacy
    ToolBinder --> OtherBinders
    ColorPickerToolBinder --> ColorViews

    UpdateManager --> UpdateInfo
    UpdateManager --> SSLHelper
    UpdateManager --> SettingsManager

    AppSettingsDialog --> SettingsManager
    AppSettingsDialog --> ColorSchemeManager
```

---

## 6. 网络层调用链

### 6.1 房主建房流程

```mermaid
sequenceDiagram
    actor Host as 房主用户
    participant Online as DouDiZhuOnlineActivity
    participant Server as GameSocketServer
    participant RelayClient as RelayHttpClient
    participant RelaySvr as Node.js Relay
    participant Nginx as nginx

    Host->>Online: 点击"创建房间"
    Online->>Online: generateRoomCode() 生成6位房间码
    Online->>Server: startWebSocket(wsUrl)
    Server->>RelayClient: getWebSocketUrl(baseUrl, roomCode, hostToken)
    RelayClient-->>Server: 返回 wss://.../ddz-ws?room=ABC123&role=host
    Server->>Nginx: WebSocket 连接请求
    Nginx->>RelaySvr: proxy_pass �?127.0.0.1:18080
    RelaySvr-->>Nginx: 连接成功，返�?host 角色确认
    Nginx-->>Server: WebSocket 连接建立
    Server->>Server: startWebSocketHeartbeat() 启动心跳
    Server-->>Online: onHostReady(roomCode) 回调
    Online->>Online: 显示房间码，等待客户端加�?```

### 6.2 客户端加入流�?
```mermaid
sequenceDiagram
    actor Client as 加入者用�?    participant Online as DouDiZhuOnlineActivity
    participant ClientSocket as GameSocketClient
    participant RelayClient as RelayHttpClient
    participant Nginx as nginx
    participant RelaySvr as Node.js Relay
    participant Server as GameSocketServer

    Client->>Online: 输入房间码，点击"加入房间"
    Online->>ClientSocket: connectWebSocket(wsUrl)
    ClientSocket->>RelayClient: getWebSocketClientUrl(baseUrl, roomCode)
    RelayClient-->>ClientSocket: 返回 wss://.../ddz-ws?room=ABC123&role=client
    ClientSocket->>Nginx: WebSocket 连接请求
    Nginx->>RelaySvr: proxy_pass �?127.0.0.1:18080
    RelaySvr->>RelaySvr: 查找房间 ABC123，转发给房主
    RelaySvr-->>Nginx: 连接成功
    Nginx-->>ClientSocket: WebSocket 连接建立
    ClientSocket->>ClientSocket: startWebSocketHeartbeat() 启动心跳
    ClientSocket->>RelaySvr: 发�?JOIN 消息（clientId=-1�?    RelaySvr->>Server: 转发 JOIN 消息
    Server->>Server: generateTempClientId() 生成临时 ID
    Server->>Server: relayKnownClients.put(clientId)
    Server->>Server: postClientConnected(clientId, "websocket")
    Server-->>RelaySvr: 返回 ACK
    RelaySvr-->>ClientSocket: 转发 ACK
    ClientSocket-->>Online: onClientConnected() 回调
    Online->>Online: 更新 UI，显示玩家加�?```

### 6.3 消息收发流程

```mermaid
sequenceDiagram
    participant ClientA as 玩家A (GameSocketClient)
    participant Nginx as nginx
    participant RelaySvr as Node.js Relay
    participant Server as GameSocketServer (房主)
    participant ClientB as 玩家B (GameSocketClient)

    ClientA->>ClientA: sendWebSocket(json) 发送出牌消�?    ClientA->>Nginx: WebSocket send
    Nginx->>RelaySvr: 转发消息
    RelaySvr->>RelaySvr: 根据 roomCode 查找房间内所有连�?    RelaySvr->>Server: 转发给房�?    RelaySvr->>ClientB: 转发给其他客户端
    Server->>Server: handleWebSocketMessage() 处理消息
    Server->>Server: webSocketBroadcast() 广播给所有客户端
    Server->>Nginx: WebSocket send (广播)
    Nginx->>RelaySvr: 转发
    RelaySvr->>ClientA: 转发广播消息
    RelaySvr->>ClientB: 转发广播消息
    ClientA->>ClientA: onMessage() 回调更新 UI
    ClientB->>ClientB: onMessage() 回调更新 UI
```

### 6.4 断线重连流程

```mermaid
sequenceDiagram
    participant Client as GameSocketClient
    participant Scheduler as Handler (后台线程)
    participant Nginx as nginx
    participant RelaySvr as Node.js Relay

    Client->>Client: onFailure() / onClosing() 检测到断线
    Client->>Client: handleDisconnection()
    Client->>Client: scheduleReconnect()
    Client->>Client: 指数退避计算延迟：base * 2^(attempts-1)
    Client->>Scheduler: postDelayed(reconnectRunnable, delay)
    note over Client,Scheduler: 延迟后执行重�?    Scheduler->>Client: reconnectNow()
    Client->>Client: reconnectAttempts++
    Client->>Client: doWebSocketConnect()
    Client->>Nginx: 新的 WebSocket 连接请求
    Nginx->>RelaySvr: proxy_pass
    RelaySvr-->>Nginx: 连接成功
    Nginx-->>Client: onOpen() 回调
    Client->>Client: reconnectAttempts = 0（重置计数器�?    Client->>Client: flushPendingMessages() 发送缓冲队列中的消�?    alt 重连次数超过 maxReconnectAttempts
        Client->>Client: 停止重连，回�?onConnectionFailed()
    end
```

---

## 7. 配置项完整说�?
### 7.1 local.properties（用户本地配置，不提�?Git�?
| key�?| 所在文�?| 作用 | 缺失时默认�?| 缺失后果 |
|-------|---------|------|-------------|---------|
| `sdk.dir` | local.properties | Android SDK 路径 | �?| 编译失败 |
| `server.url` | local.properties �?BuildConfig.SERVER_URL | 更新检查服务器地址 | `"https://your-server.example.com"` | 更新检�?404，无法获取新版本 |
| `relay.url` | local.properties �?BuildConfig.RELAY_URL | 云联�?Relay 服务器地址 | `"https://your-server.example.com/api/ddz-relay"` | 云联机功能不可用，只能局域网对战 |
| `feedback.url` | local.properties �?BuildConfig.FEEDBACK_URL | 用户反馈提交地址 | `"https://your-server.example.com/api/feedback"` | 反馈功能不可�?|

### 7.2 version.properties（版本控制，提交 Git�?
| key�?| 所在文�?| 作用 | 说明 |
|-------|---------|------|------|
| `version.code` | version.properties | 内部版本号（整数�?| 每次打包自动 +1 |
| `version.name` | version.properties | 展示版本号（�?1.3.8�?| 正式版发布时手动提升 |
| `version.channel` | version.properties | 版本通道（beta/stable�?| beta 为测试版，stable 为正式版 |

### 6.3 app/build.gradle 中的构建配置

| 配置�?| 作用 | 来源 |
|--------|------|------|
| `versionCode` | APK 内部版本�?| version.properties |
| `versionName` | APK 展示版本�?| version.properties |
| `VERSION_CHANNEL` | BuildConfig 中的通道标识 | version.properties |
| `SERVER_URL` | BuildConfig 中的服务器地址 | local.properties |
| `RELAY_URL` | BuildConfig 中的 Relay 地址 | local.properties |
| `FEEDBACK_URL` | BuildConfig 中的反馈地址 | local.properties |
| `CHANGELOG` | BuildConfig 中的更新日志 | CHANGELOG.md |
| `release.minifyEnabled` | Release 构建代码混淆 | 已设�?`true`，启�?R8/ProGuard |
| `release.shrinkResources` | Release 构建资源压缩 | 已设�?`true`，移除未使用资源 |
| `lint.abortOnError` | Lint 检查失败时中止构建 | 已设�?`true`，Lint 错误将阻�?Release 打包 |
| `lint.checkReleaseBuilds` | Release 构建时执�?Lint 检�?| 已设�?`true`，Release 构建强制检�?|
| `lint.warningsAsErrors` | Lint 警告视为错误 | 已设�?`true`，所有警告必须修�?|

> **构建参数**�?> - `-PautoBumpVersion`：构建时自动递增 `version.properties` 中的 `version.code`，CI 打包时推荐使�?> 
> **重要**：修�?`local.properties` �?`version.properties` 后，必须执行 **Build �?Clean Project �?Rebuild Project**，否�?`BuildConfig` 不会更新�?
---

## 6. 核心约束（禁止事项）

以下内容�?AI 绝对不应该自行修改的�?
### 6.1 包结构约�?
- **不要删除斗地主包** �?斗地主是主入口，删除会导�?GameRegistry 引用失效�?- **斗地主已合并为单一�?* �?`doudizhu` 包包含完整的三模联机支持（TCP + HTTP Relay + WebSocket）�?
### 6.2 游戏逻辑约束

- **不要修改 `GameRuleUtil` 中的牌型判断逻辑** �?牌型判定是斗地主核心规则，修改会导致游戏逻辑错误�?- **不要修改 `AIBot` 的出牌决策逻辑** �?AI 逻辑经过大量调试，随意修改可能导�?AI 行为异常�?- **不要修改 `Card/CardType/Rank/Suit` 的数据结�?* �?这些模型类被多处引用，修改会影响整个游戏逻辑�?
### 6.3 网络层约�?
- **不要删除网络层的心跳/重连机制** �?GameSocketClient 中的心跳�?0s）、重连（指数退避）、消息缓冲队列是保障弱网稳定性的核心机制�?- **不要修改 WebSocket 路径 `/ddz-ws`** �?此路径与 nginx 配置�?Node.js Relay 服务绑定，修改会导致连接失败�?- **不要修改 `RelayHttpClient` 中的 URL 转换逻辑** �?`convertHttpToWs()` 负责�?HTTP baseUrl 转换�?WebSocket URL，修改可能导�?URL 格式错误�?- **不要�?BuildConfig 中的 URL 硬编�?* �?所有服务器地址必须通过 `local.properties` 配置，走 BuildConfig 生成。禁止在代码中写死任何服务器地址�?
### 6.4 更新系统约束

- **不要修改双版本分发逻辑** �?UpdateManager 中的 `acceptBeta` 开关、`version-beta.json` / `version-release.json` 双通道机制是经过设计的，修改可能导致更新系统失效�?- **不要修改 `upload_to_vps.py` 中的 channel 逻辑** �?`--channel beta` / `--channel release` 控制上传哪个版本�?APK，修改可能导致版本混乱�?
### 6.3 构建约束

- **不要修改 `version.properties` 中的版本号格�?* �?`version.code` 必须是整数，`version.name` 必须�?`x.y.z` 格式�?- **不要删除 `version.properties` �?`local.properties`** �?这两个文件是构建系统的必要输入�?- **不要提交签名文件�?Git** �?`GameMatrix.keystore` �?`keystore.properties` 包含敏感信息，已添加�?`.gitignore`
- **不要修改签名配置** �?`signingConfigs.release` 已配置完成，除非明确需要更换密�?
---

## 8. 已知问题与技术债务

### 8.1 WebSocket 临时 clientId 机制

| 位置 | 内容 | 影响 |
|------|------|------|
| `GameSocketServer.java:99-101` | `generateTempClientId()` �?为尚未分�?ID �?WebSocket 客户端生成临�?ID | 临时 ID 可能与后续正�?ID 冲突，需要房主端确认机制 |
| `GameSocketServer.java:483-486` | JOIN 消息处理中，clientId=-1 时生成临�?ID | 客户端重连后可能获得不同 ID，导致状态不一�?|

### 8.2 联机状态同�?
| 位置 | 内容 | 影响 |
|------|------|------|
| �?OnlineActivity | SYNC_STATE 同步逻辑 | 已修复：胜利状态双向同�?|
| GameSocketClient.java | 消息缓冲队列 `MAX_PENDING_MESSAGES = 32` | 极端弱网下可能丢消息 |

### 8.3 更新系统

| 位置 | 内容 | 影响 |
|------|------|------|
| `UpdateManager.java` | HTTP 80 端口访问更新服务�?| Cloudflare 2083 端口 HTTPS �?Xray 占用�?43 无法复用；已�?nginx 1443 端口配置 HTTPS，云防火墙开放后启用 |
| `local.properties` | `server.url=http://<YOUR_DOMAIN>` | 使用明文 HTTP；SSLHelper 只对更新服务器域名绕过证书验证，不影响其�?HTTPS 连接 |
| `SSLHelper.java` | 信任特定更新服务器域�?| 改为 `trustUpdateServer(baseUrl)`，只对指定域名禁用证书验证，不再全局禁用所有证�?|

### 8.4 资源重复

| 位置 | 内容 | 影响 |
|------|------|------|
| `res/raw/` vs `res/raw/doudizhu_archive/` | �?70 �?mp3 文件完全重复 | APK 体积增大，但代码只引用根目录的文�?|

### 8.5 布局文件缺失

| 位置 | 内容 | 影响 |
|------|------|------|
| `ToolsFragment.java:670` | 引用 `R.layout.item_tool_section` | 该布局文件不存在，可能导致运行时崩溃（需确认�?|

### 8.6 可能的未使用依赖

| 依赖 | 状�?| 说明 |
|------|------|------|
| `androidx.navigation:navigation-fragment` | ⚠️ 可能未使�?| 代码中使用的�?BottomNavigationView + FragmentTransaction，没有使�?Navigation 组件 |
| `androidx.webkit:webkit` | ⚠️ 可能未使�?| BrowserFragment 可能使用系统 WebView 而非 androidx.webkit.WebView |

---

## 9. 修改某功能时必须同步修改的文件清�?
### 9.1 修改联机协议/消息格式

| 必须同步修改的文�?| 原因 |
|-------------------|------|
| `GameSocketClient.java` | 客户端发�?接收消息格式 |
| `GameSocketServer.java` | 服务器端发�?接收消息格式 |
| `DouDiZhuOnlineActivity.java` | UI 层消息处理逻辑 |
| `Node.js Relay (server.js)` | 服务端消息转发逻辑 |

### 9.2 修改房间码格�?长度

| 必须同步修改的文�?| 原因 |
|-------------------|------|
| `RemoteP2PUtil.java` | `ROOM_CODE_LENGTH`、`normalizeRoomCode()`、`findRoomCode()` |
| `DouDiZhuOnlineActivity.java` | `generateRoomCode()` 生成逻辑 |
| `Node.js Relay (server.js)` | 房间码验证逻辑 |

### 9.3 修改 WebSocket 路径/端口

| 必须同步修改的文�?| 原因 |
|-------------------|------|
| `RelayHttpClient.java` | `WS_PATH` 常量 |
| `nginx conf` | `location /ddz-ws` 配置 |
| `Node.js Relay (server.js)` | WebSocket 服务器监听路�?|
| `local.properties` | `relay.url` 配置 |

### 9.4 修改更新检�?URL/协议

| 必须同步修改的文�?| 原因 |
|-------------------|------|
| `local.properties` | `server.url` 配置 |
| `UpdateManager.java` | `buildVersionJsonUrl()` 方法 |
| `upload_to_vps.py` | `publicBaseUrl` 配置 |
| `update_server.py` | 服务端接口路�?|

### 9.5 修改版本号规�?
| 必须同步修改的文�?| 原因 |
|-------------------|------|
| `version.properties` | 版本号定�?|
| `app/build.gradle` | `readVersionProperties()` 解析逻辑 |
| `UpdateManager.java` | 版本比较逻辑 |
| `upload_to_vps.py` | 版本验证逻辑 |

### 9.6 添加新游�?
| 必须同步修改的文�?| 原因 |
|-------------------|------|
| `GameRegistry.java` | 注册游戏元数据（或使�?@GameEntry 注解自动注册�?|
| `GameEntry.java` | �?Activity 类上添加 @GameEntry 注解（推荐方式） |
| `AndroidManifest.xml` | 注册 Activity |
| `GamesFragment.java` | 如需要特殊分类处�?|
| `res/layout/activity_xxx.xml` | 游戏布局 |
| `res/drawable/ic_xxx.xml` | 游戏图标 |
| `res/values/strings.xml` | 游戏名称和描�?|
| `GameTutorialHelper.java` | 添加教程方法（可选） |
| `deploy/modules.json` | 添加模块清单条目（type=game, activityClass, gameId, gameCategory, gameDesc�?|

### 8.7 修改主题/配色

| 必须同步修改的文�?| 原因 |
|-------------------|------|
| `ColorSchemeManager.java` | 主题切换逻辑 |
| `res/values/themes.xml` | 亮色主题定义 |
| `res/values-night/themes.xml` | 暗色主题定义 |
| `res/values/colors.xml` | 颜色值定�?|
| `res/values-night/colors.xml` | 暗色颜色值定�?|
| `AppSettingsDialog.java` | 主题选择 UI |

---

## 附录：快速参�?
### 构建命令

```bash
# 本地编译
.\gradlew.bat :app:assembleDebug

# 打包并上�?Beta 版到 VPS
.\gradlew.bat :app:buildAndUploadDebugToVps

# 打包并上传正式版
.\gradlew.bat :app:assembleDebug -PupdateChannel=stable -PautoUploadVps=true
```

### 关键常量

| 常量 | �?| 位置 |
|------|-----|------|
| WebSocket 心跳间隔 | 10 �?| `GameSocketClient.java` / `GameSocketServer.java` |
| WebSocket 超时时间 | 45 �?| `GameSocketClient.java` |
| 最大重连次�?| 3 �?| `GameSocketClient.java` |
| 消息缓冲队列上限 | 50 �?| `GameSocketClient.java` |
| 房间码长�?| 6 �?| `RemoteP2PUtil.java` |
| HTTP 轮询超时 | 35 �?| `RelayHttpClient.java` |
| HTTP 创建房间超时 | 10 �?| `RelayHttpClient.java` |

### 服务器端文件位置（VPS�?
```
/var/www/update/
├── server.py              # Python 更新服务
├── app/
�?  ├── app-beta.apk       # Beta �?APK
�?  ├── version-beta.json  # Beta 版版本信�?�?  ├── app-release.apk    # 正式�?APK
�?  └── version-release.json # 正式版版本信�?└── ddz_ws_relay/
    ├── server.js          # Node.js WebSocket Relay
    └── node_modules/      # ws 库依�?```

### nginx 配置位置

```
/etc/nginx/conf.d/ws-ssl.conf    # 2083 端口 HTTPS + WebSocket
/etc/nginx/conf.d/update.conf    # 80 端口 HTTP 更新服务
```

### systemd 服务

```bash
systemctl status update-server           # 更新服务
systemctl status GameMatrix-ddz-ws-relay # WebSocket Relay
```
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平�?Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题�?- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言�?- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项�?- 发布前检查需覆盖中文/英文两种语言、深�?浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮�?## 2026-05-15 文档同步：Dependabot �?CI 修复

- Dependabot 安全告警已处理：升级 Android Gradle Plugin �?8.13.2、Gradle Wrapper �?8.13、Kotlin �?2.2.21、Hilt �?2.57.2�?- 构建 classpath 已强制解析到安全版本：Netty 4.1.133.Final、BouncyCastle 1.84、commons-compress 1.26.0、jose4j 0.9.6、jdom2 2.0.6.1�?- GitHub Actions 已改为验证型 CI：使�?JDK 21，执�?debug 构建与单元测试，不在云端构建 release 包，避免暴露或依�?release 签名文件�?- CI 命令统一添加 `-PautoBumpVersion=false`，避免自动修�?`version.properties`�?- `.gitignore` �?`data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/GameMatrix/app/ai/data/` 源码�?- 最�?GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆�?服务器部署/GitHub Release 发布仍以本机发布流程为准�?## 2026-05-18 文档同步：架构优�?
- UpdateViewModel（@HiltViewModel + LiveData）替�?UpdatePresenter，密封类 UpdateCheckState/DownloadState 建模状态�?- OnlineRoomManager 组合式复用联机房间逻辑，替�?BaseOnlineActivity 继承�?- GameRegistry 双轨注册：静态硬编码 + @GameEntry 注解自动发现 + register() 动态注册�?- SaveManager �?Java 迁移�?Kotlin（@Singleton + @Inject constructor），�?Java 文件已删除�?- SettingsManager/OkHttpClientProvider/UpdateManager/SaveManager 添加 @Inject 构造函数，getInstance() 标记 @Deprecated�?- 新增 AppError（密封类�?0 种错误类型）+ NetworkResult（类型安全结果封装）�?- AppModule 简化：仅保�?ExecutorService/OkHttpClient/AiPreferences/AppDatabase/DAO/ErrorReporter �?@Provides�?- 版本号更新：versionCode=260, versionName=1.3.26�?## 2026-05-18 文档同步：低优先级代码质�?
- Result.kt 重命名为 AppResult，消除与 kotlin.Result 标准库命名冲突�?- AiTask.status �?String 改为 TaskStatus 枚举（PENDING/RUNNING/COMPLETED/FAILED）�?- AiResult.errorCode 新增 AiErrorCode 常量类（7 个常量），消除魔法字符串�?- 16 处空 catch 块已补日志记录�?- OnlineRoomManager + AppSettingsDialog 硬编码文案提取到 strings.xml�?8 个资源）�?- CODE_WIKI.md 新增�?10 �?Java/Kotlin 混合边界规范"�?## 2026-05-19 文档同步：战略优�?
- UpdateViewModel 协程化：`viewModelScope.launch` + `suspendCancellableCoroutine` 包装 Java 回调�?suspend 函数，`CheckResult`/`DownloadResult` 密封类替代布尔标志，`checkJob`/`downloadJob` 替代 `isCheckingUpdate`/`isAutoDownloading`�?- 网络层测试：新增 `AiApiClientTest`（MockWebServer�? 个方法）�?`UpdateInfoTest`（JSON 解析�?7 个方法）�?- CI 质量门：APK 大小报告、测试结果报告、Android Lint 执行�?Lint 问题报告�?- 安全加固：`allowBackup=false`，新�?`backup_rules.xml` �?`data_extraction_rules.xml`，存储权限迁移（`READ_MEDIA_IMAGES`、`maxSdkVersion` 限制）�?- 构建优化：`MaterialCardView` 替代 `androidx.cardview.widget.CardView`，移�?`cardview:1.0.0` 依赖�?- 版本号更新：versionCode=262, versionName=1.3.26�?
## 2026-05-19 AI Handoff: Modularized Core

- The app now includes `:core:common`, `:core:network`, and `:core:update`.
- Do not assume `SettingsManager`, `OkHttpClientProvider`, `RelayHttpClient`, `NetworkErrorHandler`, or `UpdateManager` live under `app/src/main` anymore.
- Package names were intentionally preserved (`com.GameMatrix.app.*`) to minimize Java/Kotlin call-site churn, while Gradle module ownership changed.
- Module config values are generated from root `local.properties` and `version.properties`; update both app and core module build config logic if new release fields are added.
- `CrashHandler` remains app-owned due to `ErrorReporter` coupling.

## 10. 模块市场开发约束（AI代理必读�?
> ⚠️ 以下规则是因历史bug总结的约束，AI代理在修改模块市场相关代码时必须遵守�?
1. **禁止创建指向不存在文件的downloadUrl**：modules.json中的downloadUrl必须指向VPS上实际存在的文件。如果模块代码在主APK中，必须设置`"builtIn": true`并将downloadUrl留空�?2. **builtIn模块不需要dex文件**：当`builtIn=true`时，ModuleAdapter显示"启用"按钮而非"下载"，ModuleManager直接标记已安装。不要为内置模块编译dex文件�?3. **新增模块时先确认代码位置**：如果模块的Activity/Fragment代码在主APK的源码目录中，该模块必须标记为`builtIn=true`。只有代码完全独立于主APK（通过DexClassLoader动态加载）的模块才能设置`builtIn=false`并提供downloadUrl�?4. **上传modules.json前验�?*：每次修改deploy/modules.json后，必须同时�?a) 上传到VPS�?var/www/update/modules.json�?b) 如果有非builtIn模块，确保对应dex文件已上传到VPS�?var/www/update/modules/目录�?5. **fileSize和sha256必须真实**：非builtIn模块的fileSize和sha256必须与VPS上实际dex文件一致，不能留空或填0。builtIn模块的fileSize�?、sha256留空�?
## 2026-05-23 文档同步：模块市场架�?
- 新增模块市场（ModuleStoreActivity），入口位于游戏大厅左上角版本号下方；初始安装包仅内置五子棋和斗地主，其余游戏改为市场下载模块并在安装后回流大厅；浏览器、工具箱、AI助手改为独立市场模块，初始安装包不再自带；底部导航栏动态化：默认仅显示"游戏"Tab，安装浏览器/工具箱/AI模块后自动出现对应Tab；游戏分类精简为默认游戏分类和已下载模块分类；ModuleManifest扩展5个字段（type/activityClass/gameId/gameCategory/gameDesc）；ModuleManager新增registerInstalledGameModules()和registerGameFromManifest()；VPS更新服务器新增modules.json/modules/路由；新增3个模块入口类：BrowserModuleEntryPoint、ToolsModuleEntryPoint、AiModuleEntryPoint；ModuleManifest新增builtIn字段，内置模块（代码在主APK中）显示"启用"按钮而非"下载"，无需下载dex文件；修复模块市场下载失败：所有模块都指向不存在的dex文件，改为builtIn=true无需下载；修复模块市场界面被状态栏遮挡：添加fitsSystemWindows=true；修复lint-baseline.xml路径变量导致release构建失败；模块市场新增分类Tab（游戏/浏览器/工具箱/AI助手/VPN），支持按storeCategory筛选；新增已下载模块列表页面，支持更新/卸载操作
- ModuleManifest新增storeCategory和isBaseFramework字段
- 浏览�?工具�?AI助手拆分为基础框架+扩展功能
- 新增VPN基础服务占位模块

## 2026-05-24 文档同步
- 底部导航切换闪退修复：创建 KeepStateNavigator 自定义导航器，使用 add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日志
- 内存泄漏全面修复：移除 WeakReference callback、Fragment 回调安全检查、视图引用彻底清理
- 压力测试通过：40轮快速Tab切换无崩溃


