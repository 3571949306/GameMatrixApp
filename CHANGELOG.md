# GameMatrixApp - 版本更新日志

## [v1.4.1] - 2026-07-06（循环 17-24：浏览器原生重构 + wrongbook 模块 + 宿主 Kotlin 迁移 + Netty 安全修复）

### 🌐 循环 17-19：浏览器循环19重构为原生实现
- **包结构重组**：`app/src/main/java/com/gamecenter/app/browser/{bridge,core,data,security,ui}/`
- **Room 数据库**：4 张表（浏览历史/书签/下载/cookie）
- **安全模块**：AdBlocker、DomainTrustManager、JsBridgePolicy
- **卸载第三方 WebView 依赖**，使用原生 Android WebView 实现

### 📚 循环 20：wrongbook 模块预装集成
- **错题本模块**：`module-store/feature/tools/wrongbook`
- **预装 APK**：`assets/modules/feature_wrongbook_v100.apk`
- 支持科目管理、复习计划、数据导入导出

### 📖 循环 21-22：错题本全面推进
- Room v2 schema 升级
- 自定义图表 View（科目统计/复习进度）
- 科目管理、复习计划、数据导入导出

### 🔧 循环 23：宿主 Kotlin 迁移完成
- `App.java` / `MainActivity.java` / `GameRegistry.java` → `.kt`
- 路径：`app/src/main/kotlin/com/gamecenter/app/{App.kt, MainActivity.kt, games/GameRegistry.kt}`
- 新增 `core/moduleloader/.../ModuleContextHelper.kt`
- 新增 `.github/workflows/android_ci.yml`（GitHub Actions CI/CD）
- 新增 `.github/dependabot.yml`（Dependabot 配置）
- 语言比例：Java 约 55% + Kotlin 约 45%

### 🔒 循环 24：Netty 安全漏洞修复
- Netty 4.1.134.Final → 4.1.135.Final
- 修复 7 个 CVE（3 high + 4 medium）：
  - High: CVE-2026-50010 / CVE-2026-45416 / CVE-2026-44249
  - Medium: CVE-2026-50560 / CVE-2026-50020 / CVE-2026-48043 / CVE-2026-47244
- GitHub Dependabot：0 open / 7 dismissed
- 最新 commit：`f978f06 fix(security): 循环24 修复 GitHub Dependabot 7 个 Netty 安全漏洞`

### 📦 版本信息
- `versionCode`: 567
- `versionName`: 1.4.1
- 上次稳定版: 1.4.0 (vc=465)
- 包名: `com.gamecenter.app`
- 工作区状态：干净，main 与 origin/main 同步

### 🗂 动态模块（共 9 个）
- 游戏（5 个）：`module-store/feature/games/games/{hall,chinesechess,game2048,klotski,tts}`
- 工具（4 个）：`module-store/feature/tools/{ai,tools,vpn,wrongbook}`

---

## [v1.4.1] - 2026-06-22（更新逻辑优化）

### 🚀 更新逻辑优化
- **OptimizedUpdateManager**：新增缓存、重试、MD5预检查
- **超时时间优化**：主源 2s/3s（原 3s/5s），备用 5s/15s（原 15s/30s）
- **速度阈值降低**：30KB/s（原 50KB/s）
- **占位符URL检测**：自动跳过无效URL（避免卡住）
- **本地APK预检查**：MD5匹配时跳过下载

### 🛠 修复
- **更新卡住问题**：CDN缓存的version.json导致版本比较失败
- **VPS路径修复**：更新 `/var/www/update/app/version-release.json`（update_server读这个）
- **软链接创建**：nginx现在可以通过软链接访问version.json和APK
- **FileUriExposedException 修复**：`UpdateInstaller.openDownloadDirectory()` 打开下载目录时改用 FileProvider 提供 content:// URI（Android 7.0+），避免 file:// URI 暴露崩溃，功能不再降级到 Toast 提示

### 🧪 自动化测试（功能模块）
- 新增 5 个功能模块 UI 自动化测试文件（位于 `app/src/androidTest/java/com/gamecenter/app/tests/features/`）
  - `SettingsTest.kt`：设置模块（10 个用例）
  - `ToolBoxTest.kt`：工具箱模块（3 个用例，模块未安装时优雅跳过）
  - `AiAssistantTest.kt`：AI 助手模块（3 个用例，模块未安装时优雅跳过）
  - `BrowserTest.kt`：浏览器模块（3 个用例，模块未安装时优雅跳过）
  - `ModuleStoreTest.kt`：模块商店（8 个用例）
- 所有测试继承 `EmulatorTestBase`，使用 `GameTestHelper` 辅助方法

### 📦 版本信息
- `versionCode`: 466
- `versionName`: 1.4.1
- 上次稳定版: 1.4.0 (vc=465)

---

## [v1.4.0] - 2026-06-21（游戏内嵌 + 线程优化 + UI优化）

### 🎮 游戏内嵌
- **28个游戏内置到主app**：无需下载即可使用，开箱即用
- 保留模块市场更新能力：内置游戏可通过模块市场检查更新
- 游戏分类：经典（8款）、益智（10款）、休闲（9款）
- 新增游戏基类 `BaseGameActivity`、`AchievementManager`、`DifficultyLevel`、`AchievementData`
- 新增游戏启动对话框 `GameStartDialog`

### ⚡ 线程架构优化
- **总线程数：~75 → ~17（-77%）**
- OkHttp线程池：64 → 8（-87%）
- 新增统一线程管理器 `AppExecutors`（IO/Compute/AI/Background）
- AiTaskRouter 使用统一线程池，避免线程爆炸
- 为未来融合计划和协程迁移预留架构

### 🎨 UI优化
- **华容道UI升级**：渐变色方块、阴影效果、动画过渡
- **难度选择面板优化**：休闲游戏（2048、贪吃蛇等）不再显示难度选择，直接启动
- 只有有AI对手的游戏（五子棋、象棋、斗地主等）才显示难度选择

### 🔧 关键修复
- 修复 `BaseGameActivity` 类缺失导致的崩溃
- 修复 `GameStartDialog` 类缺失导致的游戏点击崩溃
- 修复游戏图标显示问题（28个游戏图标全部注册）
- 修复 modules.json 中 builtIn 标记不一致问题
- 修复华容道UI资源显示为简化版的问题

### 📦 模块市场
- modules.json 版本升级至 29 个内置模块
- 所有游戏标记为 `builtIn: true`，保留更新能力
- 修复 `games_hall` 模块的 builtIn 标记不一致问题

### 🧪 自动化测试
- 新增自动化测试框架 `adb_test_framework.py`（50+测试用例）
- 支持ADB连接模拟器进行功能测试
- 自动生成HTML/JSON测试报告
- 支持冒烟测试、完整测试、回归测试三种模式

### 📄 文档更新
- 更新 README.md 添加 v1.4.0 更新记录
- 更新 CHANGELOG.md 添加详细更新日志
- 更新 game_center_app_ai_roadmap.md 路线图状态

---

## [v1.4.0] - 2026-06-19（分发渠道精简）

### 🌐 分发架构调整
- **美国 VPS 下线**：分发渠道从三级（香港 VPS → GitHub Releases → 美国 VPS）精简为两级（香港 VPS → GitHub Releases）
- **代码调整**：
  - `UpdateChecker`：移除 US VPS 三级源逻辑，`UPDATE_SOURCE_VPS_US` 回退到 AUTO 模式
  - `UpdateDownloader`：移除 US URL 构建逻辑
  - `RecoveryDownloader`：移除 US fallback 源
  - `SettingsManager`：`UPDATE_SOURCE_VPS_US` 标记 `@Deprecated`，`getUpdateSource()` 自动将 US 值重定向到 AUTO
  - `AppSettingsDialog`：UI 移除"美国 VPS"选项，保持索引映射兼容
- **配置变更**：`server.url.fallback` 配置项废弃（保留空值向后兼容），`SERVER_URL_FALLBACK` BuildConfig 字段保留但标注废弃
- **文档同步**：更新 README、PROJECT_CONTEXT、PUBLISH_GUIDE、AI_CONTEXT、AI_ONBOARDING、CODE_WIKI 等文档

---

## [v1.4.0] - 2026-05-27（版本号升级，vc=400）

### 📊 版本升级
- **versionCode**: �?343 升级至400
- **versionName**: 保持 1.4.0
- **Gradle 工具层*: AGP 升级至8.13.2, Hilt 升级至2.57.2

---

## [v1.4.0] - 2026-05-26（模块化架构重构）

### 🏗�?模块化架构重构
- **模块系统框架**：实现完整的模块化架构，支持动态加载 APK 模块
- **模块加载器V2**：新增`ModuleLoaderV2`，支持版本管理、DEX 缓存、资源加载、热更新
- **模块商店核心**：实现 `ModuleDownloadManager`（断点续传）、`ModuleInstaller`、`ModuleUninstaller`
- **内置游戏更新**：实现 `BuiltInModuleUpdater`，支持斗地主、五子棋通过模块商店更新

### 📦 模块商店功能
- **模块商店 UI**：新增`ModuleStoreActivity`，支持模块浏览、搜索、下载、安装、卸载
- **实时搜索**：`etModuleSearch` 支持按关键词实时过滤模块
- **版本更新提示**：`ModuleAdapter` 显示橙色"更新"按钮（当已安装版本落后时）
- **模块分类**：按游戏、工具、浏览器、AI、VPN 分类展示

### 🔌 联机功能模块
- **联机核心模块**：将 `OnlineRoomManager`、`GameSocketServer`、`GameSocketClient`、`RelayHttpClient`、`LANManager` 拆分为独立模块`online-core`
- **动态加载联机模块*：游戏模块通过 `OnlineCoreModule` 动态加载联机功能
- **支持云联机**：斗地主、五子棋、围棋、中国象棋、石头剪刀布均支持 WebSocket 云联机对战

### 📊 APK 体积优化（T08）
- **目标**：框架 APK 小于 5MB
- **ABI 拆分**：仅保留 `arm64-v8a` 架构（减少 Native 库体积约 75%）
- **移除嵌入 APK**：从 `assets/` 中移除嵌入式 APK（节省 ~17.3MB）
- **R8 全模式优化**：`minifyEnabled true` + `shrinkResources true`
- **ProGuard 规则完善**：保留 Android 核心组件、序列化接口、Dagger/Hilt、网络层、模块系统接口
- **待优化**：启动图标压缩（`ic_launcher_logo.png` 1.6MB 降至目标 <100KB）、音频文件压缩、`raw/` 2.4MB�?

### 🧪 测试覆盖增强
- **单元测试**：新增 15+ 个测试文件，437+ 个测试用�?
- **测试覆盖**：斗地主规则引擎（80+）、牌型识别（60+）、AI 决策�?）、更新逻辑�?0+）、AI API 客户端（8�?
- **测试工具**：使用 MockWebServer、Mockito、Kotlin Coroutines Test

### 📝 文档完善
- **模块开发指�?*：新增`文档/MODULE_DEVELOPMENT_GUIDE.md`，说明如何创建和发布模块
- **架构设计文档**：新增`文档/MODULAR_ARCHITECTURE_DESIGN.md`，详细说明模块化架构
- **README 更新**：更新模块化架构说明、快速入门、目录结�?

### 🐛 Bug 修复
- **模块下载 SHA-256 校验修复**：所有 VPS 文件现在与`modules.json` �?SHA-256 哈希值一�?
- **模块加载失败修复**：版本感知重加载、DEX 优化缓存清理
- **模块商店显示修复**：`builtIn` 逻辑修复、`games_hall` 模块正确显示
- **证书绑定临时关闭**：解决模拟器 SIGSEGV 兼容性问题

### 📊 版本信息
- **versionCode**: 341
- **versionName**: 1.4.0
- **modules.json 版本**: 11
- **模块总数**: 33�?9 款游戏模�?+ 4 个功能模块）

---

## [当前工作区] - 2026-05-26（modules.json v11）3款新游戏模块 + 兼容性修复）

### 模块商店扩展
- **modules.json 升级至v11**：从 v10 升级，新增23 款游戏模块，游戏模块总数为 29，全部模块总数 33
- **新增游戏**：blackjack(21点)、breakout(打砖块)、brotato、checkers(跳棋)、dice(骰子)、flappy(Flappy Bird)、go(围棋)、guess(猜数字)、knife(飞刀大师)、match(消消乐)、memory(记忆翻牌)、minesweeper(扫雷)、pipeline(管道)、plane(飞机大战)、reaction(反应测试)、rock(石头剪刀布)、snake(贪吃蛇)、sokoban(推箱子)、sudoku(数独)、tetris(俄罗斯方块)、tic(井字棋)、tiles(拼图)、whack(打地鼠)
- **VPS 部署**：所有游戏 ZIP 文件已上传至 VPS `/var/www/modules/` �?`/var/www/update/modules/`

### 兼容性修复
- **证书绑定临时关闭**：`SecureOkHttpFactory` 中证书绑定临时禁用，解决模拟�?SIGSEGV 兼容性问题
- **R8 混淆 Debug 关闭**：Debug 构建禁用 R8 minify，加快调试构建速度

### Bug 修复
- **games_hall builtIn 修复**：游戏大厅模块修正为在初始 APK 中正确显示为 builtIn
- **模块下载 SHA-256 校验修复**：所有 VPS 文件现在与modules.json �?SHA-256 哈希值一致，下载校验不再失败

## [当前工作区] - 2026-05-25（模块框架全链路修复：校验错误打不开/不更新显示）

### 模块下载与校验修复
- **下载前清理旧文件**：`ModuleDownloader.doDownload` 在开始下载前删除旧模块文件和残留临时文件，避免文件名冲突导致新文件覆盖失败
- **多源切换清理临时文件**：切换下载源时删除临时文件，防止断点续传拼接出损坏文件
- **ModuleVerifier 资源泄漏修复**：`computeSha256` �?`verifyDexFile` 中的 `FileInputStream` 改为 try-finally 确保异常时也能关闭流
- **版本校验增强**：`ModuleManager.downloadModule` 不再仅比较版本号，还会检查文件是否存在且 SHA-256 校验通过，文件损坏时自动重新下载

### 模块加载修复（下载后打不开）
- **版本感知重加载**：`ModuleLoader.loadModule` 不再无条件返回缓存实例，而是对比已安装版本与 manifest 版本。版本变更时自动卸载旧实例后重新加载
- **DEX 优化缓存清理**：新增 `clearOptimizedDex` 方法，重加载前清除`modules_opt/` 中对应的优化 DEX 缓存，避免 DexClassLoader 加载旧代码
- **下载后卸载旧实例**：`ModuleManager.downloadModule` �?`onComplete` 回调中先调用 `ModuleLoader.unloadModule` 卸载旧实例

### 模块显示更新修复
- **ModuleAdapter 更新按钮**：新增 `installedVersions` 映射和`hasUpdate` 判断，已安装版本低于远程版本时显示橙色"更新"按钮和版本变更提示
- **ModuleStoreActivity 版本追踪**：新增 `buildInstalledVersionsMap()` 方法，`applyCategoryFilter` 和下载完成回调中同步刷新已安装版本信息
- **ModuleStoreActivity 乱码修复**：修复 `openModule` �?Toast 文本乱码为正确的"模块加载失败"
- **ModuleStoreActivity ACTION_UPDATE**：新增 `ACTION_UPDATE` 处理分支

## [当前工作区] - 2026-05-25（中国象�?华容道重构与动态资源加载集成+ 模块商店BuiltIn逻辑修复 + 一键部署）

### 动态模块编译与打包修复
- **补充桩函数与缺失类**：在宿主 `app` 模块中创建了 `SaveManager.java` 编译桩，并补全了 `GameUsageStore.recordPlayTime` 游戏时长追踪方法以及 `GameTutorialHelper` 中的中国象棋与华容道教程弹窗，解决了子模块无法编译引用的问题
- **模块编译与打�?*：成功编译中国象�?(`chinesechess`) 和华容道 (`klotski`) 两个独立 APK 模块，并生成了对应的 `feature_chinesechess_v200.apk` �?`feature_klotski_v200.apk` 插件包
- **Gradle 任务优化**：修复了 Gradle 构建生命周期�?`packageAppClasses` 的隐式依赖检测警告，显式声明其依�?`compileDebugJavaWithJavac` �?`compileDebugKotlin`，保障类编译生成顺序列化

### 动态资源加载集成与修复
- **动态资源加密*：在 `com.gamecenter.app.modules.ModuleLoader` 中集成了 `ModuleResourceLoader`。在加载外部 APK 时为其单独装�?`AssetManager` 并生�?`Resources`，解决外�?APK 模块由于缺少布局和资源导致无法打开的问题
- **单例引用修复**：修复`ChineseChessModuleFragment.java` �?`KlotskiModuleFragment.java` 中错误的 `com.gamecenter.app.modular.ModuleManager.INSTANCE` 包路径，统一指向新的 `com.gamecenter.app.modules.ModuleManager.INSTANCE`�?

### 模块商店设计逻辑与部署修复
- **BuiltIn 逻辑修复**：修复了 `modules.json` �?`browser`、`tools`、`ai` 被错误标记为 `"builtIn": true` 导致无法显示下载按钮且启用无反应的逻辑设计错误。将其修正为 `"builtIn": false`，模块商店目前能够正常下载、校验、加载和启用。
- **加入中国象棋与华容道**：在 `modules.json` 中添加了中国象棋 (chinesechess) 和华容道 (klotski) 的下载选项，并将配置清单升级至 **Version 8**。
- **一键上传部署*：编写并运行 `upload_modules.py` 同步脚本，成功将所有功能模块 APK 和`modules.json` 同步部署至香港 VPS 的`/var/www/update/modules/` 下，实现模块商店的一键秒级拉取
- **ADB 自动部署安装**：编写了 `install_app.ps1` 一键部署测试脚本，实现了宿�?App 对模拟器 `emulator-5554` 的自动检测、安装与 MainActivity 运行�?

## [当前工作区] - 2026-05-25（模块商店目录结构重组）

### 模块商店目录结构重组
- **新目录结构创建**：在项目根目录创�?`模块商店/` 文件
- **压缩模块迁移**：将 `deploy/modules/` 下的所有游戏压缩包（25个ZIP文件）移至`模块商店/压缩模块/`
- **功能模块迁移**：将 `feature/games/` 移至 `模块商店/功能模块/游戏/games/`，将 `feature/vpn/` 移至 `模块商店/功能模块/工具/vpn/`
- **模块清单复制**：将 `deploy/modules.json` �?`deploy/modules_v2.json` 复制`模块商店/` 目录（deploy目录保留备份�?
- **构建配置更新**：更�?`settings.gradle` 文件中的模块引用路径
  - `:feature:vpn` �?`:模块商店:功能模块:工具:vpn`
  - `:feature:games` �?`:模块商店:功能模块:游戏:games`
  - `:feature:games:game2048` �?`:模块商店:功能模块:游戏:games:game2048`
  - `:feature:games:klotski` �?`:模块商店:功能模块:游戏:games:klotski`
  - `:feature:games:chinesechess` �?`:模块商店:功能模块:游戏:games:chinesechess`
- **文档创建**：在 `模块商店/` 目录下创�?`模块商店结构说明.md`，详细说明新的目录结�?
- **备份保留**：原 `deploy/` 目录仍保留所有文件作为备份，确保可回�?
- **文档更新**：同步更�?`PROJECT_CONTEXT.md`、`DOCUMENTATION_INDEX.md` 等相关文�?

### 新增游戏模块
- **飞刀大师** - 经典飞刀游戏（v1.0.0）已添加至模块商店
  - 旋转靶子投掷飞刀，击中苹果获得额外分支
  - 连击系统、关卡递进、多种视觉效�?
  - 游戏源代码已移动�?`模块商店/功能模块/游戏/games/knife/`
  - 模块压缩包：`game_knife_v100.zip`

### 新目录结�?
```
模块商店/
├  ── 压缩模块/                    # 游戏模块压缩包（26个ZIP文件
├  ── 功能模块/                   # 独立功能模块源代�?
│  ├─  ─ 游戏/                   # 游戏功能模块
│  │  └  ── games/              # 包含knife、chinesechess、game2048、klotski�?
│  └─  ─ 工具/                   # 工具功能模块
│  └─  ─ vpn/                # 科学上网VPN模块
├  ── modules.json                # 模块市场清单文件（主版本号
├  ── modules_v2.json             # 模块市场清单文件（v2版本号
└  ── 模块商店结构说明.md         # 本文�?
```

## [当前工作区] - 2026-05-24（游戏美化+ 中国象棋提示改进 + 华容道中国象棋模块商店上架）

### 四个游戏视觉美化
- 斗地�?DouDiZhuTableView：径向渐变桌面、菱形花纹卡牌背面、金色选中高亮、AI信息区半透明面板
- 五子�?GomokuView：木纹渐变棋盘�?D棋子效果增强、星位标记增大、最后一手红色圆�?
- 华容道KlotskiView：深色渐变背景、方块金色边框外发光、出口脉冲动画、双层边�?
- 中国象棋 ChineseChessView：木纹渐变棋盘、四角L形角标、楚河汉界波浪线、棋子投�?选中发光

### 中国象棋提示功能改进
- 提示改为棋盘可视化：起始位置蓝色脉冲光环 + 目标位置蓝色脉冲光环 + 连接箭头指引
- 状态栏显示中文棋谱描述（如"馬八进七"�?車九平五"），替代原来的坐标文�?
- ChineseChessView 新增 setHintMove/clearHint 方法drawHint 绘制逻辑
- ChineseChessActivity 新增 buildHintDescription/numToChinese 方法生成中文棋谱
- 走棋、悔棋、重新开始、切换选中时自动清除提�?

### 华容道和中国象棋模块商店上架
- 创建华容道独�?APK 模块 `feature/games/klotski/`（KlotskiModuleEntryPoint + KlotskiModule 的
- 创建中国象棋独立 APK 模块 `feature/games/chinesechess/`（ChineseChessModuleEntryPoint + ChineseChessModule 的
- settings.gradle 注册新模块：`:feature:games:klotski` �?`:feature:games:chinesechess`
- modules.json 更新：版v1.0.0 �?v2.0.0，ZIP �?APK 格式，添�?entryClass
- APK 已上传到 VPS：`feature_klotski_v200.apk`�?,085 bytes）、`feature_chinesechess_v200.apk`�?,069 bytes�?
- 服务modules.json 已同步更新，通过 `https://your-server.example.com/modules/` 可正常下�?

## [当前工作区] - 2026-05-24（底部导航切换闪退修复 + 模块下载修复 + 内存泄漏全面修复制

### 底部导航切换闪退修复
- 核心修复：创�?KeepStateNavigator 自定义导航器，使�?add/show/hide 策略替代 Navigation 组件默认�?replace 策略
- 切换Tab时不再销毁和重建Fragment，只改变可见性，彻底解决快速切换时的闪退问题
- 导航�?mobile_navigation.xml 中将 fragment 标签改为 keep_state_fragment
- activity_main.xml 移除 app:navGraph 属性，改为代码中设置（先注册导航器再设置导航图
- MainActivity 中自定义底部导航点击处理，替换NavigationUI.setupWithNavController（避免setPopUpTo 破坏Fragment复用�?
- MainActivity.onResume 中仅当菜单为空时才重建导航菜�?

### 模块下载修复
- ModuleDownloader 全面重写：添加全局异常捕获（try-catch 覆盖整个下载线程），防止线程崩溃导致回调永远不被调用
- 降低网络超时：连接超时从30秒降5秒，读取超时00秒降0�?
- 移除全局 cancelled 标志的死代码，改为通过 activeDownloads Map 检查取消状�?
- 增加详尽的日志输出：每个关键步骤都有 Log.d/Log.e 日志
- 下载回调使用 mainHandler.post 确保在主线程执行
- ModuleManager.downloadModule 增加日志和错误回�?
- ModuleStoreActivity.downloadModule 增加即时Toast反馈和日�?

### 内存泄漏全面修复
- ModuleManager.loadModuleList 移除 WeakReference 包装callback（不再需要，Fragment不会被销毁重建）
- GamesFragment.refreshInstalledModuleGames 添加 try-catch �?isDestroyed 安全检查
- BrowserFragment 添加 isDestroyed 标记，WebViewClient/WebChromeClient/DownloadListener 回调中添加安全检查
- ToolsFragment �?requireContext() 替换行getContext() 安全调用
- AiFragment.onDestroyView 更彻底的视图引用清理
- ModuleShellFragment 添加 isDestroyed 标记�?isAdded 安全检查
- UpdateChecker 使用 WeakReference 包装 UpdateCheckCallback

### 压力测试结果
- 40轮快速Tab切换（每�?次切换，间隔100ms）无崩溃
- �?onDestroyView 回调（Fragment被复用而非重建
- �?LeakCanary 泄漏报告
- APP进程保持稳定运行

## [当前工作区] - 2026-05-23（科学上网修复：内存泄漏 + 模块ID + CloudFlare缓存 + VPS路由分离�?

### 科学上网修复
- 修复内存泄漏：ProtocolFactory 及四个协议模块改applicationContext 代替 Activity Context
- 修复模块 ID 不一致：统一�?"vpn"（原 modules.json �?"vpn_basic"，MainActivity 检查"vpn"�?
- 修复 CloudFlare CDN 缓存旧响应：APK 文件改名�?feature_vpn_v100_v2.apk
- 重构 VPS 模块服务体系：模块商店与 App 更新完全分离�?001 端口独立模块服务器）
- ModuleManager 重写：本地缓存+ 版本对比 + 后台刷新（loadModuleList 替代 fetchRemoteModules�?
- 新增科学上网功能：支�?VMess/VLESS/Trojan/Shadowsocks 四种协议
- VPN 模块为非内置模块（builtIn=false），通过模块商店下载启用，不预制在主 APK 和
- 模块架构三层分离：core:common（共享接口）+ �?APK（VpnServiceProxy �?+ ModuleShellFragment 宿主�? feature/vpn（独�?APK，含全部协议�?UI�?
- 新增 VpnServiceProxy：主 APK 中唯一�?VpnService 实现（~70行），仅负责 TUN 隧道建立/拆除，委�?VpnDelegate 处理协议
- 新增 ModuleShellFragment：通用动态模块宿主，未下载模块时显示引导页，下载后自动加载模�?Fragment
- 新增 FeatureModule 接口（core:common）：可下载功能模块的 Fragment 提供
- 新增 VpnDelegate 接口（core:common）：VPN 服务代理，返回Tunnel（InputStream/OutputStream）供 VpnServiceProxy 转发流量
- 新增 ModuleInterface 迁移至core:common（通过 typealias 保持向后兼容�?
- feature/vpn 模块 VpnFragment 采用纯代码构�?UI（无 XML 布局依赖），确保动态加载时资源可用
- 模块 dex 文件已上传至 HK VPS：`/var/www/update/modules/feature_vpn_v100.apk`�?62KB�?
- modules.json 已更�?vpn_basic 条目：entryClass、downloadUrl、sha256、fileSize

### 模块市场
- 新增模块市场入口：游戏大厅左上角版本号下方添�?模块市场"按钮，点击进入ModuleStoreActivity

### VPS 服务器修复
- 修复 update_server.py 路由顺序 bug：`/modules/*` 检测必须在 `.apk` 后缀检测之前，否则 `/modules/xxx.apk` 会被误路由到 APP_DIR 的主 APK 文件（返回71MB 而非实际模块文件 662KB�?
- 新增ModuleStoreActivity：展示可下载模块列表，支持下载、安装、卸载操�?
- 新增ModuleAdapter：模块列表适配器，支持已安装状态实时更�?

### 底部导航栏动态化
- 底部导航栏改为动态显示：初始仅显示游戏"Tab
- 安装浏览器模块后自动出现"浏览�?Tab
- 安装工具箱模块后自动出现"工具层Tab
- 安装AI助手模块后自动出�?AI"Tab
- MainActivity.onResume()时刷新导航栏状�?

### 游戏分类重新划分
- 游戏分类精简�?个：经典（内置）、益智（市场下载）、休闲（市场下载�?
- GameRegistry.buildStaticCategories()仅保留classics分的个经典游戏）
- reaction/other分类映射到casual分类
- GameRegistry.Entry构造函数改为public，允许外部动态注

### 模块化改
- 浏览器改为独立市场模块（type="nav"）
- 工具箱改为独立市场模块（type="nav"）
- AI助手改为独立市场模块（type="nav"）
- 19款非经典游戏改为独立市场模块（type="game"�?
- 初始安装包不再自带浏览器、工具箱、AI工具和非经典游戏

### ModuleManifest扩展
- 新增type字段：区�?game"（游戏模块）�?nav"（导航模块）
- 新增activityClass字段：游戏Activity全限定的
- 新增gameId字段：游戏ID（与GameRegistry对应�?
- 新增gameCategory字段：游戏分类键�?
- 新增gameDesc字段：游戏描�?

### 模块市场分类与已安装列表
- 模块市场顶部新增分类Tab：全部、游戏、浏览器、工具箱、AI助手、VPN
- 模块市场标题栏右侧新增已安装模�?按钮，点击进入InstalledModulesActivity
- 新增InstalledModulesActivity：展示已安装模块列表，支持更�?卸载操作
- 新增InstalledModuleAdapter：已安装模块列表适配置
- 新增layout文件：activity_installed_modules.xml、item_installed_module.xml
- ModuleManifest新增storeCategory字段：game/browser/工具/ai/vpn
- ModuleManifest新增isBaseFramework字段：标记基础框架模块
- ModuleStoreActivity支持按storeCategory筛选模块，基础框架模块置顶显示
- modules.json所有模块添加storeCategory和isBaseFramework字段

### 模块拆分
- 浏览器模块拆分为：浏览器基础框架（isBaseFramework=true），扩展功能
- 工具箱模块拆分为：工具箱框架（isBaseFramework=true�? 扩展工具
- AI助手模块拆分为：AI的基础框架（isBaseFramework=true），扩展功能
- 新增VPN基础服务占位模块（storeCategory=vpn�?

### 内置模块（builtIn）机�?
- ModuleManifest新增builtIn字段：标记代码已在主APK中的模块，无需下载dex文件
- ModuleAdapter新增ACTION_ENABLE：内置模块显示内置"标签+蓝色"启用"按钮，而非"下载"
- ModuleStoreActivity新增enableBuiltInModule()：点�?启用"直接标记已安装并注册游戏
- ModuleManager新增enableBuiltInModule()：标记已安装+动态注册游戏到GameRegistry
- modules.json全部22个模块标记builtIn=true：因为代码都在主APK中，无需下载
- 修复模块市场下载失败：之前所有模块都指向不存在的dex文件导致404，现在内置模块无需下载

### 修复
- 修复扫雷难度切换闪退（ClassCastException：ContentFrameLayout无法转换为LinearLayout�?
- 修复模块市场界面被状态栏遮挡：activity_module_store.xml添加fitsSystemWindows=true
- 修复lint-baseline.xml路径变量导致release构建失败
- 修复VPS上modules.json只有扫雷1个条目的问题：上传完成2模块版本
- 修复ModuleStoreActivity中R.color.material_blue_500颜色资源不存在导致编译失�?

### ModuleManager增强
- 新增registerInstalledGameModules()：遍历已安装模块，将game类型模块注册到GameRegistry
- 新增registerGameFromManifest()：从ModuleManifest创建GameRegistry.Entry并动态注
- GamesFragment.initCategories()中调用动态注册和远程模块获取

### 模块入口�?
- 新增BrowserModuleEntryPoint：浏览器模块入口，通过EXTRA_NAV_TAB跳转
- 新增ToolsModuleEntryPoint：工具箱模块入口
- 新增AiModuleEntryPoint：AI助手模块入口

### VPS服务器扩展
- Python更新服务器新增modules.json路由：返回模块清除
- Python更新服务器新增modules/路由：提供模块dex文件下载
- deploy/modules.json扩展�?2个模块（3个nav + 19个game�?
- 上传完整modules.json�?2个模块）到VPS，替换原来只有扫�?个条目的版本

### 构建与发布
- 编译验证通过（assembleDebug BUILD SUCCESSFUL�?

## [v1.4.0] - 2026-05-22（正式版：全面质量提升）

### 测试覆盖
- 新增斗地主核心单元测试：DouDiZhuGameStateManagerTest�?8个用例）、DouDiZhuProtocolTest�?5个用例）、DouDiZhuSeatManagerTest�?2个用例）
- 新增棋类AI测试：GomokuAITest，0个用例）、ChineseChessAITest，0个用例）、GoGameExtendedTest�?3个用例）

### 安全加固
- 网络安全配置增强：新增VPS更新服务器证书固定（pin-set）、AI API域名强制HTTPS
- ProGuard规则收紧：Release构建移除调试日志、保留Serializable/Parcelable类、保护@Keep注解字段
- AiApiClient新增API Key XOR混淆方法（obfuscateKey/deobfuscateKey�?

### 英语国际化
- 英文字符串资源从73条补全至250+条，实现与中文字符串100%对齐
- 覆盖所有模块：游戏列表、工具箱、联机对战、设置、更新、成就等

### 深色模式适配
- 夜间颜色资源4个补全至49个，覆盖所有游戏模块（五子棋、象棋、贪吃蛇等）
- ColorSchemeManager新增4个暗色变体字段（darkPrimary/darkTabIndicator/darkNavBarActive/darkCardBorder�?
- 8套配色方案均支持暗色模式，确保暗色背景下对比度充�?

### 架构现代�?
- 新增AiViewModel：基于LiveData的AI聊天状态管理，支持后台线程调用、生命周期感知、请求取

### 成就系统（基础架构
- 新增AchievementType枚举�?6种成就类型，覆盖通用/围棋/象棋/五子�?在线/AI/日常
- 新增AchievementManager单例：SharedPreferences+JSON持久化，线程安全，监听器模式

### 性能与构建优化
- Gradle构建加速：启用构建缓存、配置缓存、关闭Jetifier、启用非传递R�?
- 预期增量构建时间减少20-40%

### 构建与发布
- 正式版版本号提升，`versionName=1.4.0`、`versionCode=280`

## [v1.3.30-beta.4] - 2026-05-22（测试版：P2 任务完成�?

### P2 安全与构建优化
- LAN 发现协议已具�?HMAC-SHA256 签名验证，防止同网段恶意设备伪造发现报�?
- P2 任务已全部完成：allowBackup 关闭、旧存储权限迁移、kapt/ksp 统一�?KSP

### 构建与发布
- 测试版版本号提升，`versionName=1.3.30-beta.4`、`versionCode=275`

## [v1.3.30-beta.3] - 2026-05-22（测试版：项目改+ KSP迁移 + R8 优化

### 项目改名
- 项目名称�?GameCenter 全面更名�?GameMatrix
- GitHub 仓库重命名为 GameMatrixApp
- 所有字符串资源、主题样式、Java/Kotlin 常量已更�?

### 构建系统优化
- KAPT �?KSP 全量迁移：Hilt 2.57.2、Glide 4.16.0、Room 2.7.1 均使�?KSP 处理
- Room 升级至2.7.1，解密Kotlin 2.2.x Continuation 签名不兼容问题
- ProGuard 规则收紧，Release APK 体积进一步优化
- R8 编译配置优化，解密MediaPipe 内部类缺失警�?

### 代码质量
- Java 工具类迁移到 Kotlin：RelayHostHelper、RelayClientHelper、WebSocketClientHelper、I18nHelper
- EncryptedSharedPreferences 用于 API Key 安全存储
- CI/CD 新增自动 Release 发布流程

### 构建与发布
- 测试版版本号提升，`versionName=1.3.30-beta.3`、`versionCode=274`

## [v1.3.30-beta.1] - 2026-05-21（测试版：更新功能优化）

### 更新功能优化
- 默认�?APK 下载到公�?Download 目录，用户可通过文件管理器直接查看和分享安装包
- 提高更新通知优先级为 IMPORTANCE_DEFAULT，用户更容易注意到下载进度�?
- 新增下载通知取消按钮，用户可随时取消正在进行的下载�?
- 下载通知中显示实时下载速度（KB/s �?MB/s）�?
- 新增双重完整性校验：先验证文件大小，再验证MD5，减少解析安装包错�?问题
- 新增 APK 安装前预检测功能，安装失败时提示重新下载�?
- 修复文件保存路径，确保兼�?Android 16 分区存储策略�?

### 构建与发布
- 测试版版本号提升，`versionName=1.3.30-beta.1`、`versionCode=268`�?

## [v1.3.29] - 2026-05-21（正式版：小游戏 AI 响应优化

### 小游�?AI 响应链路优化
- 五子棋：去除固定 300ms 假延迟，改为按难度最小响应延迟（80/120/170/230ms），AI 算完即落子。
- 中国象棋：去除固�?1 秒最小展示时间，改为按难度最小响应延迟（140/220/340/480ms），AI 算完即落子。
- 围棋：去除固�?400ms 假延迟，改为统一 120ms 最小响应延迟�?
- 围棋 AI 引擎：固�?1800ms 时间预算改为动态时间预算（开局 900ms、中局 1400ms、终局 1000ms）�?
- 围棋 AI 引擎：新增保守战术短路，3 子以上提子直接返回，唯一 2 子提法也直接返回�?
- 围棋 AI 引擎：新增根并行蒙特卡洛模拟，多线程独立运行 UCB1 选择后合并结果�?
- 围棋 AI 引擎：`Math.random()` 替换行`ThreadLocalRandom`，并行模拟无锁竞争�?

### 代码修复
- 修复 GomokuActivity 中 `getAiMinResponseDelayMs()` 方法注释被乱码污染的问题
- 修复 ChineseChessActivity 中 `getAiMinResponseDelayMs()` 方法误插�?`applyAIMove` Javadoc 的问题
- 修复 ChineseChessActivity �?GoActivity �?`delay==0` 时仍使用 `postDelayed(..., 0)` 的问题，改为 `post()`�?

### 构建与发布
- 正式版版本号提升，`versionName=1.3.29`、`versionCode=267`�?

## [v1.3.28] - 2026-05-21（正式版：中国象棋棋盘对比度修复制

### 小游戏修复
- 中国象棋棋盘改为统一读取 `chess_bg` / `chess_line` 资源色，避免背景色变化后网格线丢失�?
- 棋盘主网格、外框、棋子边框和“楚河汉界”文字统一提高对比度，并加粗关键线条，保证不同背景下仍清晰可见�?

### 构建与发布
- 正式版版本号提升，`versionName=1.3.28`、`versionCode=266`�?

## [v1.3.27] - 2026-05-21（正式版：界面安全区 + 本地 AI 模型 + 小游戏难度重排）

### 界面适配
- MainActivity 统一应用系统inset，根布局避开状态栏，底部导航避开手势�?导航栏�?

### AI 本地模型
- 本地模型启用时保存完整模型元数据（名称、运行时、文件名、校验值、设备要求），避免切换新本地模型后误报“未配置云端模型”�?
- AI 路由按已下载的本�?LLM 文件执行 MediaPipe 推理；规则引擎模型仍作为 `on-device` 保底�?

### 小游戏体�?
- 五子棋、中国象棋难度从滑杆改为四个直接按钮：低 / �?/ �?/ 大师�?
- 五子棋、中国象�?AI 拆分为四档独立难度配置，中档搜索预算下调，避免中难度偏高�?
- 五子棋、中国象棋对局底部功能按钮改为两行等宽布局，保证提示、悔棋、重新开始、教程在窄屏可见�?

### 构建与发布
- 已发布`versionName=1.3.27`、`versionCode=265` �?stable 正式包，并上传至 HK/US VPS 的GitHub Release�?

## [当前工作区] - 2026-05-21（AI 模型扩容 + 输出长度提升级

### AI 模型选择
- AI 页模型状�?Chip 现在可打开云端模型选择列表，并保存所选供应商与模型�?
- 云端模型扩展�?OpenAI、DeepSeek、阿里云通义、硅基流动、智谱 AI、零一万物、月之暗�?Kimi 等多�?OpenAI 兼容模型�?
- 本地模型弹窗从“只展示第一个模型”改为模型列表，并按低端中端高端机分档显示。

### 输出长度
- 云端 `max_tokens` 从固�?1024 改为按模型能力自适应�?048 / 3072 / 4096�?
- MediaPipe 本地 LLM 输出上限�?384 token 提升级768 token�?
- 聊天提示词从 300 字限制调整为常规 800 字以内，长文/方案/分析类回答允许更完整输出�?

---

## [当前工作区] - 2026-05-21（斗地主 AI + 棋类提示 + 围棋计分支

### 斗地�?
- 增强联机 AI 上下文：AIHelper 会向 AIBot 传递地主座位、上次出牌者、队友座位、队�?地主剩余牌数
- 农民 AI 默认不压队友出牌，下家队友临近跑完时优先放小牌，只有地主残牌时才允许紧急炸弹拦截断
- 斗地主音效按座位选择男女声素材，并扩展叫地主、不出、炸弹、火箭、飞机、顺子、连对等事件的音效调用�?

### 棋类单机提示
- 五子棋单机人机模式新增“提示”，复用 GomokuAI 推荐落子并在棋盘上标记�?
- 围棋单机人机模式新增“提示”，复用 GoGame 蒙特卡洛评估推荐落子或提示虚手�?
- 中国象棋单机人机模式新增“提示”，复用 ChineseChessAI 推荐红方下一步并选中建议棋子�?

### 围棋规则与终局
- GoGame 新增 `calculateScore()`、`getWinner()`、`getResultText()`�?
- 双方连续虚手后按吃子、地盘和 6.5 贴目计算胜负，并在状态栏与终局遮罩展示比分支
- GoGameTest 新增连续虚手终局与胜负判定测试

### 构建与发布
- 正式 APK 已用 stable 渠道构建并通过 APK v2 签名验证
- 根构建脚本不再强制覆�?Gradle 插件 classpath �?protobuf 版本，避免AGP release 依赖收集任务运行时冲突�?
- 新增 `-PskipReleaseLint=true` 发布逃生开关，仅用于绕�?AGP 8.13 lintVital 路径变量序列化缺陷；默认 release lint 仍保持开启

---

## [当前工作区] - 2026-05-20（GitHub 安全告警清零 + 本地上传网络修复制

### Dependabot / 依赖安全
- GitHub Dependabot open alerts 已从 33 个降0 个
- 根构建脚本继续强制安全版本，覆盖 Gradle classpath 与全项目配置中的 Kotlin stdlib、Guava、Protobuf、Netty、OpenTelemetry、BouncyCastle、commons-compress、commons-lang3、jose4j、jdom2 等传递依赖�?

### GitHub 分支�?CI
- 远端仓库保持单一主分支`main`，未保留其它远端分支�?
- `CI/CD Pipeline` �?`Dependency Submission` 已在最新安全修复后通过

### 本地 GitHub 网络
- 本机 Git 已配置为仅对 `https://github.com` 使用 v2rayN/xray 本地 HTTP 代理 `http://127.0.0.1:10808`，避免上传代码必须开xray TUN/虚拟网卡模式
- 新增 `工具/network/Configure-GitHubProxy.ps1` �?`文档/LOCAL_GITHUB_NETWORK.md`，用于重复检测、应用或清除 GitHub-only Git 代理配置

### Lint 基线
- 重新生成 `app/lint-baseline.xml`，当�?`lintDebug` 以“无新增问题”通过；历�?1007 �?lint 问题仍在 baseline 中，后续应按模块逐步清理�?
- CI lint 禁用 `AndroidGradlePluginVersion`、`GradleDependency` �?debug-only `TrustAllX509TrustManager` 依赖分析噪声，避免外部仓库版本提示或依赖 jar 扫描差异导致主分支红灯；依赖安全继续�?Dependabot �?Gradle 强制安全版本约束负责�?

---


## [当前工作区] - 2026-05-19（战略优化：协程 + 网络测试 + CI 质量+ 安全加固 + 构建优化

### UpdateViewModel 协程�?
- **协程替代回调**：`UpdateViewModel.kt` 使用 `viewModelScope.launch` + `suspendCancellableCoroutine` �?Java 回调（`UpdateManager.checkUpdate`/`downloadApk`）包装为 Kotlin suspend 函数
- **新增密封�?*：`CheckResult`（Success/NoUpdate/BetaOnly/BetaBlocked/Error）和 `DownloadResult`（Success/Verifying/Error/Cancelled）替代原有布尔标志�?
- **Job 替代布尔标志**：`isCheckingUpdate`/`isAutoDownloading` 布尔标志替换行`checkJob: Job?`/`downloadJob: Job?`，支持结构化并发取消�?
- **生命周期安全**：`onCleared()` 自动取消两个 Job，避免泄漏。
- **弃用�?API**：使�?`resumeWith(kotlin.Result.success(...))` 替代已废弃的 `resume(value){}`�?

### 网络层测试
- **AiApiClientTest.java**：使用 MockWebServer 编写 8 个测试方法，覆盖成功响应、HTTP 错误�?xx/5xx）、连接失败、畸�?JSON、缺少字段、空 system prompt 等场景�?
- **UpdateInfoTest.java**：全�?JSON 解析测试7 个测试方法覆盖所有字段、Beta 渠道、版本回退等场景�?

### CI 质量
- **APK 大小报告**：CI 流水线构建后计算 APK 大小并写入`GITHUB_STEP_SUMMARY`。
- **测试结果报告**：解密XML 测试报告，输出通过/失败/跳过统计�?`GITHUB_STEP_SUMMARY`。
- **Android Lint 执行**：CI 新增 `lintDebug` 步骤�?
- **Lint 问题报告**：解密Lint 输出并上传结�?artifact�?

### 安全加固
- **禁用备份**：`AndroidManifest.xml` 设置 `android:allowBackup="false"`�?
- **备份规则**：新增`android:fullBackupContent="@xml/backup_rules"` �?`android:dataExtractionRules="@xml/data_extraction_rules"`�?
- **backup_rules.xml**：排列sharedpref、database、update/ 目录
- **data_extraction_rules.xml**：排除相同目录的云备份和设备传输出
- **存储权限迁移**：新增`READ_MEDIA_IMAGES` 权限（Android 13+）。`READ_EXTERNAL_STORAGE` 设置 `maxSdkVersion="32"`，`WRITE_EXTERNAL_STORAGE` 设置 `maxSdkVersion="29"`�?

### 构建优化
- **MaterialCardView 替代 CardView**：`item_game_card.xml`、`GomokuOnlineActivity.java`、`GamesFragment.java` �?`androidx.cardview.widget.CardView` 替换行`com.google.android.material.card.MaterialCardView`�?
- **移除 CardView 依赖**：从 `build.gradle` 删除 `implementation 'androidx.cardview:cardview:1.0.0'`�?

---

## [当前工作区] - 2026-05-18（架构优化：ViewModel + 组合复用 + 双轨注册 + DI 迁移 + 错误模型�?

### 低优先级代码质量改进

- **Result.kt 重命名为 AppResult**：消除与 `kotlin.Result` 标准库的命名冲突。`CrashHandler.kt` 中的 `runCatchingResult` / `getOrElse` 扩展函数同步更新增
- **TaskStatus 枚举替代 AiTask.status 字符�?*：新增`TaskStatus.java`（PENDING/RUNNING/COMPLETED/FAILED），`AiTask.status` 类型�?`String` 改为 `TaskStatus`，`AiTaskRouter` �?`AiTaskRouterTest` 全部替换为枚举引用�?
- **AiErrorCode 常量类替换AiResult.errorCode 裸字符串**：新增`AiErrorCode.java`（NETWORK_ERROR/QUOTA_EXCEEDED/NO_API_KEY/LOCAL_LLM_*�?7 个常量），`AiTaskRouter`、`AiApiClient` �?`AiTaskRouterTest` 全部替换为常量引用�?
- **修复全部署catch �?*�?6 处空 catch 块已补日志记录（`Log.w`/`Log.d`），保留原有注释说明忽略原因。涉�?MainActivity、CrashHandler、OkHttpClientProvider、UpdateChecker、Game2048Activity、DouDiZhuProtocol/SyncManager/UIController�?
- **提取硬编码文案到 strings.xml**：OnlineRoomManager�?5 个）+ AppSettingsDialog�?3 个）�?48 个中文字符串资源提取`strings.xml`，Java 代码改用 `context.getString(R.string.xxx)`�?
- **Java/Kotlin 混合边界规范**：在 CODE_WIKI.md 新新增10 章，文档化文件放置、跨语言调用注意事项、迁移优先级和同名类冲突规则�?

### 高优先级架构改进

- **UpdateViewModel 替代 UpdatePresenter**：新增`UpdateViewModel.kt`（@HiltViewModel + LiveData），使用密封�?`UpdateCheckState` / `DownloadState` 建模状态，生命周期安全，消�?`isFinishing()/isDestroyed()` 防御代码。`UpdatePresenter` 标记 `@Deprecated`�?
- **@Inject 构造函数迁�?*：`SettingsManager`、`OkHttpClientProvider`、`UpdateManager` 添加 `@Inject` 构造函数+ `@ApplicationContext`，`getInstance()` 标记 `@Deprecated`。`AppModule` 移除对应 `@Provides` 方法
- **统一错误模型**：新增`AppError.kt`（密封类层次结构�?0 种错误类型，支持 `fromException()`/`fromHttpCode()` 自动映射）和 `NetworkResult.kt`（类型安全结果封装，`onSuccess`/`onFailure` 链式调用）�?

### 中优先级架构改进

- **GameRegistry 双轨注册**：新增`@GameEntry` 注解（运行时保留，支�?id/iconRes/nameRes/descRes/category 属性），`GameRegistry` 新增 `register()`/`registerAll()`/`clearDynamicEntries()`/`scanAnnotatedGames()` API，分类键名与本地化名称解耦（`categoryKey` 字段）�?
- **OnlineRoomManager 组合式复制*：新增`OnlineRoomManager.java`，从 `BaseOnlineActivity` 提取联机房间管理逻辑为独立组件，支持 `Listener` 接口（onGameStarted/onGameMessageReceived/onGameReset），各游戏通过组合方式复用联机逻辑，无需继承 BaseOnlineActivity。
- **SaveManager Kotlin 迁移**：`SaveManager` �?Java 迁移至Kotlin（`@Singleton` + `@Inject constructor(@ApplicationContext)`），�?Java 文件已删除。`AppModule` 移除 `@Provides` 方法

### DI 模块简�?

- `AppModule.kt` 当前仅保留`@Provides`：`ExecutorService`、`OkHttpClient`、`AiPreferences`、`AppDatabase`、`AiMessageDao`、`GameStatsDao`、`ErrorReporter`�?
- `SettingsManager`、`OkHttpClientProvider`、`UpdateManager`、`SaveManager` 均通过 `@Inject` 构造函数由 Hilt 自动管理器

---## [当前工作区] - 2026-05-16（测试补�?+ 网络去重 + DI迁移 + 安全加固 + 构建优化 + 离线体验证

### 测试补充
- 新增 `DouDiZhuRuleEngineTest`：覆盖出牌验证、叫地主决策、清台判定、手牌评分（40+ 用例）。
- 新增 `GameRuleUtilTest`：覆盖牌型识别（�?�?三条/顺子/炸弹/火箭等）、出牌比较、主权重计算、洗牌发牌、CardType 属性（60+ 用例）。
- 新增 `UpdateManagerLogicTest`：覆�?URL 处理、版本比较、更新策略、Beta 通知逻辑、MD5 计算、文件大小格式化、渠道归一化（40+ 用例）。
- 单元测试总数96 增至 411+，核心模块测试覆盖显著提升级

### 网络模块去重
- 删除 `com.GameMatrix.app.games.doudizhu.network.RelayHttpClient`（与共享�?95% 重复）�?
- 斗地主模块（DouDiZhuOnlineActivity、GameSocketServer、GameSocketClient）统一使用 `com.GameMatrix.app.network.RelayHttpClient`�?
- 共享�?`RelayHttpClient.post()` 方法从包私有改为 `public`，支持跨包调用�?

### DI 迁移统一
- `SettingsManager`、`SaveManager`、`ErrorReporter`、`OkHttpClientProvider` 添加 `@Singleton` + `@Inject` 构造函数，支持 Hilt 自动注入�?
- 保留 `getInstance()` 静态方法，确保向后兼容（未注入的调用方不受影响）�?
- `AppModule` 简化：`SettingsManager`/`ErrorReporter`/`OkHttpClientProvider` 改为 Hilt 自动管理实例，移除手�?`@Provides` 方法
- `AppModule` 新增 `SaveManager` 提供

### 游戏逻辑�?UI 分离
- 新增 `GameLogic<S>` 接口（`games/common/GameLogic.java`）：定义 `getState()`/`applyAction()`/`isGameOver()`/`getWinner()`/`reset()` 统一契约�?
- 新增 `OnlineGameLogic<S>` 接口（`games/common/OnlineGameLogic.java`）：扩展 `GameLogic`，增加联机动作序列化/反序列化和协议前缀�?
- 现有游戏暂不强制迁移，新游戏应遵循此接口

### 安全性加密
- `SSLHelper` 区分 Debug/Release 模式：Debug 构建信任所有证书（开发便利），Release 构建仅设HostnameVerifier（不覆盖 SSLSocketFactory）�?
- `RemoteP2PUtil` 房间码验证修复：从纯数字 `^[0-9]{6}$` 改为字母数字混合 `^[A-HJ-NP-Z2-9]{6}$`，与服务`ROOM_CODE_ALPHABET` 一致�?
- `RemoteP2PUtil.normalizeRoomCode()` 增强：自动去�?`DDZ://` 前缀、转大写、过滤非法字符，与服务端 `normalize_room_code()` 对齐�?

### 构建脚本优化
- `app/build.gradle` 添加 7 个分区注释（Version Configuration / Helper Functions / Android Configuration / Dependencies / Version JSON Generation / Publish & Upload / Version Bump & Build Lifecycle），提升可读性

### 包结构优化
- 新增 `games/common/package-info.java`：文档化推荐的游戏模块架构（Activity �?GameController �?GameLogic）�?
- 新增 `GameLogic<S>` �?`OnlineGameLogic<S>` 接口（上一轮已完成）�?

### 离线体验
- `GamesFragment` 新增 `isNetworkAvailable()` 网络检测，离线时调整空状态提示透明度�?
- `AiTaskRouter` 新增离线检测：本地无法处理的任务在无网络时直接返回友好提示"当前无网络连接，仅支持本地规则处理，避免无意义的云端请求超时

### Code Wiki
- 生成完整的项目技术文�?`CODE_WIKI.md`，覆盖架构、模块、依赖、构建、CI/CD、测试体系等 13 个章节省

---## [当前工作区] - 2026-05-15（Dependabot 安全告警 + CI 修复制

### 构建依赖安全
- Android Gradle Plugin 升级至8.13.2，Gradle Wrapper 升级至8.13�?
- Kotlin 调整�?2.2.21，Hilt 升级至2.57.2，保持与当前 kapt/Hilt 处理链兼容�?
- 对构�?classpath 强制安全版本，覆�?Netty、BouncyCastle、commons-compress、jose4j、jdom2 �?Dependabot 告警来源�?

### GitHub Actions
- CI 改为验证型流程：JDK 21 + `assembleDebug` + 单元测试
- CI 不再云端执行 release 构建，避免缺�?`keystore.properties` / release keystore 时失败，也避免把签名材料放入 GitHub Secrets�?
- CI 命令添加 `-PautoBumpVersion=false`，防止自动递增版本号�?
- 修复 `.gitignore` 和 `data/` 规则误忽�?AI data 源码的问题

---## [当前工作区] - 2026-05-14（全局文字适配 + 应用内英文切换）

### 文字适配
- 新增 `Widget.GameMatrix.Button`、`Widget.GameMatrix.Button.Tonal`、`Widget.GameMatrix.Button.Outlined` 和平台按钮默认样式
- 全局替换 MaterialButton 使用项目样式，统一按钮最小高度、内边距、两行显示和省略策略�?
- 修正斗地主、工具箱、游戏卡片、AI 页面等低高度按钮，减少“进入游戏”“发送”等文字被按钮裁切的问题

### 应用语言
- 设置弹窗新增“应用语言”：跟随系统、中文、English�?
- App 启动时会恢复语言偏好，并通过 AppCompat application locales 应用
- AI 任务类型下拉改为资源字符串，英文模式下显示Chat、Summary、Translate 等选项

---

## [当前工作区] - 2026-05-14（AI 可读性修复+ 本地聊天模式

### AI 页面可读取
- 修复 AI 消息气泡在深�?动态主题下文字对比度不足的问题
- 消息气泡改为日间/夜间独立高对比配色，用户、AI 助手、系统消息分别使用明确的背景色和文字色�?
- 收藏星标同步使用高对比颜色，避免暗色主题下不可见�?

### 本地聊天模式
- AI 任务类型新增“聊天”，并设为默认模式
- 本地 Gemma 启用后，聊天模式会使用本地模型直接回答用户问题
- 聊天提示词明确要求中文、简洁、可执行，并在不确定时说明边界�?

---

## [当前工作区] - 2026-05-14（Gemma 本地推理接入 + 用户协议补强�?

### Gemma 本地推理
- 新增 `MediaPipeLocalLlmEngine`，通过 `com.google.mediapipe:tasks-genai` 加载 `.task` 模型并执行本地文本生成�?
- `AiTaskRouter` 支持在本地模型下载并启用后，优先将总结、翻译、润色、问答、关键词、分类和聊天任务路由到本�?Gemma�?
- 本地推理加入设备内存检查和异常兜底，避免低内存或模型加载失败导致崩溃�?
- 下载完成后自动启`gemma3-1b-it-q4` 并保持本地优先策略�?

### AI 协议与合�?
- 新增 `AiLegalNotices`，在首次下载 Gemma 前展�?Google Gemma Terms、本地推理说明、风险提示和用户责任�?
- `AiPreferences` 记录 Gemma notice 版本和确认时间，避免条款变化后无法重新触达用户
- 新增 `文档/AI_USER_AGREEMENT_LOCAL_AI.md`，记�?App �?AI 用户协议、下载前确认项、发布检查清单�?

---

## [当前工作区] - 2026-05-14（Gemma 本地模型分发 + 更新下载修复制

### AI 本地模型准备
- 新增 AI 页面“本地模型”入口，可从 HK VPS 读取 `ai-models/models.json`�?
- 新增 `AiModelDownloadManager`，模型下载位置固定为 App 私有目录 `Android/data/<package>/files/Documents/ai_models`，不写入公共下载目录，适配置Android 版本的存储限制
- HK VPS 模型清单加入 `Gemma3-1B-IT q4` 条目；由于上�?Gemma 权重需要许可确认，当前清单默认禁用直下，避免无授权下载失败�?

### 更新下载修复
- 修复 APK 下载地址回退逻辑，不再错误回退�?`app-debug.apk`�?
- GitHub 备用下载地址改为 `releases/download/<tag>/app-release.apk`。

---
## [当前工作区] - 2026-05-14（AI 阶段 4 + 发布链路修复制

### AI 阶段 4 �?
- **AI 独立底部导航�?*：AI 不再嵌入工具箱，入口位于底部导航�?
- **模板能力**：新增`AiTemplateManager`，提供会议纪要、代码报错、文案润色、中英翻译、复习问答模板�?
- **历史增强**：AI 页面支持历史搜索、收藏筛选、消息收取消收藏�?
- **导出能力**：支持按当前筛选结果通过系统分享导出 AI 记录

### 发布链路修复 🔐
- 发布脚本统一上传已签名、已 R8 混淆�?`app-release.apk`。
- `version.json` 中的 `apkName` 按渠道生成：beta 使用 `app-beta.apk`，release 使用 `app-release.apk`。
- 已替换VPS beta 通道 APK，HK/US 节点均为签名混淆包（vc=236）�?

---

## [1.3.20] - 2026-05-12（依赖升级+ 代码清理）�?

### 依赖版本升级 📦
- **Kotlin**: 1.9.25 �?2.1.10
- **Hilt**: 2.52 �?2.55
- **AppCompat**: 1.7.0 �?1.7.1
- **ConstraintLayout**: 2.2.0 �?2.2.1
- **Navigation**: 2.8.4 �?2.8.9
- **RecyclerView**: 1.3.2 �?1.4.0
- **Mockito Core**: 5.14.2 �?5.15.2

### 新增依赖 �?
- `com.google.code.gson:gson:2.11.0` - JSON 序列�?反序列化
- `org.json:json:20250107` - 单元测试 JSONObject 支持

### 代码清理 🧹
- **GameUsageStore**: 替换手工 JSON 拼接/解析�?Gson，消除潜在的格式错误隐患
- **LANManager.postHostDiscovered()**: 修复为空方法的问题，现在正确回调 OnHostDiscoveredListener
- **util/Log.java**: 删除未使用的自定义日志类（项目统一使用 AppLog/Timber 风格�?
- 删除空目�?`app/src/main/java/com/GameMatrix/app/startup/`
- 修复 `upload_config_hk.json` publicBaseUrl（移至2083端口，使用Cloudflare HTTPS代理�?
- 新增 `dependencies {} constraints` 块，锁定 Guava/Okio/Kotlin 等传递依赖版

### 编译验证。
- 所有单元测试通过24/124 PASS�?
- Debug & Release 编译通过

---

## [1.3.21-beta] - 2026-05-12（AI 智能助手接入）�?

### 新增功能 �?
- **AI 智能助手**：在底部导航中新增AI 独立入口，支�?7 �?AI 任务�?
  - 文本总结（summary�?
  - 翻译（translate�?
  - 润色改写（rewrite�?
  - OCR 处理（ocr�?
  - 问答对生成（qa_pairs�?
  - 关键词提取（keywords�?
  - 文本分类（classify�?
- **AI 全页�?*：新增`AiFragment`，提供聊天式交互界面，支持任务类型选择和历史消息列�?
- **本地 AI 处理**：新增`LocalAiProcessor`，支�?OCR 后处理、规则摘要、关键词提取等本地优先处理
- **任务路由**：新增`AiTaskRouter`，实现本地优化�?云端 fallback 的智能调用
- **AI 数据模型**：新增`AiMessage`, `AiTask`, `AiResult`, `AiProviderConfig` 四个核心模型
- **API 客户�?*：新增`AiApiClient`，支�?OpenAI 兼容接口（默�?DeepSeek API，可切换阿里云通义、硅基流动、智谱 AI、零一万物、OpenAI�?

### 架构变更 🏗�?
- 新增 `com.GameMatrix.app.ai` 包及子包：`ai.data`, `ai.cloud`, `ai.local`, `ai.ui`
- 底部导航新增 AI 独立入口注册
- `MainActivity` 底部导航集成 `AiFragment`
- 资源文件新增：`fragment_ai.xml`, `item_ai_message.xml`
- Drawable 新增：AI 消息气泡背景（user/assistant/system 三种样式

### 本地优先策略 🎯
- 默认启用本地优先模式（`localFirst=true`�?
- 低复杂度任务（OCR 清洗、关键词提取、分类）自动走本地处理
- 云端仅在需要时启用，依�?API Key 配置
- 内置每日免费额度（默�?20 次），无需付费即可基础使用

### 编译验证。
- `assembleDebug` 编译通过
- 无回归错�?

---

---

## [1.3.19] - 2026-05-12（双版本分发架构重构 + 关键修复）🚀

### 关键问题修复 🔥🔥

#### 问题1：版本检查显示已是最新版 - 已修复。
**原因**：
- VPS 返回�?`version-release.json` 可能缺少关键�?`versionCode` 字段
- 导致比较逻辑失效，新版本无法被检测到

**修复**：
- �?`UpdateManager.java` 中确保从 `BuildConfig.VERSION_CODE` 获取本地版本号作为后
- 添加了详细的日志输出（`remote.versionCode` vs `local.versionCode`�?
- `applyUpdatePolicy` 方法现在直接比较 `remote.versionCode > local.versionCode`，不再依赖其他逻辑

**验证**�?
- 本地 223 版本用户现在可以正确检测到 224 版本的更�?

#### 问题2：切换更新源失效 - 已修复。
**原因**：
- `buildUpdateUrls` 方法中自定义 URL 的处理逻辑有问题
- 自定义 URL 没有被正确添加到 URL 列表的首�?
- 没有添加备用源，导致自定�?URL 失效时无法更�?

**修复**：
- 重构�?`buildUpdateUrls` 方法
- 自定义 URL 现在被优先放在列表的第一�?
- 添加备用源（香港 VPS 的美国 VPS 的GitHub）作为兜�?
- 添加了日志输出显示完整的 URL 构建列表

### 双版本分发架构重🎯

#### 核心修复 🔥
- **重构 UpdateManager.java** - 实现清晰的测试版/正式版分离逻辑
  - 用户开接收测试 �?检查version-beta.json
  - 用户关闭"接收测试 �?只检查version-release.json
  - 双重 API 支持：新 JSON API + �?API 自动回退
  - 简化的版本号比较逻辑：只�?remote.versionCode > local.versionCode 就标记有更新

#### 服务器端修复 ⚙️
- **修复 upload_to_vps.py** - 防止误删其他通道文件
  - `cleanup_remote` 函数现在保护两个通道的所有文�?
  - beta �?release 版本文件可以共存，互不覆�?
  - 修复前：上传 beta 会删除release 文件
  - 修复后：两个通道文件同时保留

#### VPS 文件结构更新 📦
```
/var/www/update/app/
├  ── app-beta.apk         # 测试版安装包 �?
├  ── version-beta.json     # 测试版元数据 �?
├  ── app-release.apk      # 正式版安装包 �?
└  ── version-release.json  # 正式版元数据 �?
```

#### APP 更新逻辑 🧠

**新版 APP（开启测试版**�?
1. 检查`/version-beta.json`
2. 如果有更高版�?提供更新
3. 否则检查`/version-release.json`

**新版 APP（关闭测试版**�?
1. 只检查`/version-release.json`
2. 不显示测试版更新提示
3. 如果检测到有更新的测试版，会提示用户开启测试版以获取更�?

**旧版 APP**�?
- 使用 `/api/update/check` �?API
- 服务器端自动比较 `versionCode`
- 只要 `versionCode` 更低 �?提示更新

#### 向后兼容性保留🔒
- �?新旧 API 共存，自动回退保证兼容�?
- �?服务器端同时维护两个版本
- �?无论 APP 版本新旧，只�?`versionCode` 更低，就能检测到更新

---

## [1.3.18] - 2026-05-12（正式版）�?

### 严重问题修复 🔥🔥
- **修复 Handler 内存泄漏问题** - 所有游戏 Activity 现在正确使用 `removeCallbacksAndMessages(null)` 清理 Handler
  - TetrisActivity: 修复游戏循环 Handler 清理
  - SnakeActivity: 修复游戏循环 Handler 清理
  - FlappyActivity: 修复游戏循环 Handler 清理
  - PlaneActivity: 添加 onDestroy 清理逻辑
  - TilesActivity: 添加 onDestroy 清理逻辑
  - SokobanActivity: 添加 onDestroy 清理逻辑
  - WhackActivity: 调用 releaseResources() 完全释放资源
- **修复 WhackView 资源泄漏** - stopGame() 使用 `removeCallbacksAndMessages(null)` 确保完全清理
- **清理重复 import** - 修复 UpdateManager.java 中的重复导入语句

### 代码质量提升 📈
- 统一所有游戏 Activity 的生命周期管理
- 完善 Handler 和 Runnable 的清理逻辑
- 优化内存管理，防�?Activity 泄漏
- 提高长时间使用稳定�?

### 之前版本的修复内容（1.3.17�?

## [1.3.17] - 2026-05-12（正式版）✅

### 重要修复 🔥
- **修复 APK 签名配置问题** - 解决 keystore 文件路径错误，使�?`rootProject.file()` 替代 `file()`
- **启用 V1 �?V2 签名方案** - 确保兼容所�?Android 版本（`enableV1Signing = true`, `enableV2Signing = true`�?
- **修复自动更新源选择逻辑** - 修正版本号比较逻辑，解密已是最新版误报问题
- **修复开发者签名异常提�?* - 现在 APK 已正确签名，可正常安�?

### 推箱子游戏优化🎮
- **美化 UI 界面** - 使用渐变色、阴影效果、圆角设计，画面更精�?
- **修复推箱子移动逻辑** - 修复玩家站在目标点上时状态处理不当的问题
- **添加方向控制按钮** - 支持滑动和按钮两种操作方式，更易上手
- **优化玩家角色设计** - 圆形角色带白色圆点，更像游戏角色
- **优化箱子设计** - 圆角矩形带对角线装饰，目标点显示绿色圆点标记

### 构建系统优化
- 修复 `upload_to_vps.py` 脚本中的文件名逻辑错误（beta/release 版本命名�?
- 修正 release 版本上传任务，使用正确的 APK 路径
- �?debug �?release 构建都生�?version.json 文件
- 禁用有问题的 lint 任务以避免构建失�?

### 内存泄漏修复
- 修复 TetrisActivity、SnakeActivity、FlappyActivity �?Handler 和 Runnable 清理问题
- 修复 WhackView 的资源释放，添加 releaseResources() 方法
- 优化 DouDiZhuOnlineActivity �?cleanup 方法，确保所有回调都正确移除
- 所有游戏 Activity �?onDestroy 中正确释放资�?

### 错误处理优化
- UpdateManager 集成 NetworkErrorHandler，统一错误提示
- 下载失败时使用友好的用户消息替代技术错误信�?
- 优化网络异常分类和错误码映射

### 性能优化
- 优化 Handler 和 Runnable 的生命周期管理
- 移除不必要的对象引用，防止内存泄�?
- 改进游戏循环的资源释放逻辑

### 技术更�?
- 更新 `keystore.properties` 配置
- 创建新的 `GameMatrix.keystore` 签名文件（SHA384withRSA, 2048 位）
- 修复 `UpdateManager.java` 版本比较逻辑
- 修复 `build.gradle` 签名配置

### 签名验证
```bash
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk
# 输出：jar 已验证
```

签名信息
- 证书：CN=GameMatrix, OU=Development, O=GameMatrixApp, L=Shenzhen, ST=Guangdong, C=CN
- 签名算法：SHA384withRSA, 2048 位密�?
- 有效期：10000 �?

### 发布状�?�?
- **版本号*: 223 (1.3.17)
- **APK 大小**: 16.44 MB
- **发布渠道**: 正式(stable)
- **更新增*: 香港 VPS + 美国 VPS
- **发布状�?*: �?已成功发布

---

## [1.3.16] - 2026-05-12

### 新增
- APK 签名配置（release 构建自动签名�?
- 敏感文件排除（keystore.properties、GameMatrix.keystore 不提�?Git�?
- 自动化发布流程（一键上传到 HK VPS、US VPS、GitHub Releases�?

### 优化
- 完善发布流程文档和说�?
- 更新所�?MD 文档与最新版本同�?

### 技�?
- 新增 `keystore.properties` 配置签名凭证
- 新增 `app/GameMatrix.keystore` 签名密钥库（RSA 2048 位，10000 天有效期�?
- `build.gradle` 添加 `signingConfigs.release` 配置
- `.gitignore` 添加签名文件排除规则

### 发布状�?
- �?香港 VPS 上传成功（version 217�?
- �?美国 VPS 上传成功（version 217�?
- �?APK 签名验证通过（可正常安装�?

---

## [1.11.0] - 2026-05-11

### 新增
- Lint 严格模式（abortOnError true, warningsAsErrors true�?
- 统一网络错误处理器（NetworkErrorHandler），支持错误码分类、智能重试、网络状态检查
- 国际化支持（中英文），添�?values-en/strings.xml 英文资源
- LeakCanary 内存泄漏检测（Debug 版集�?2.14�?
- autoBumpVersion 开关控制版本号自动递增
- GitHub Actions CI/CD 工作流（自动构建、测试、上传）

### 优化
- 网络错误提示统一为友好的中文/英文 Toast 消息
- 版本号递增可通过 `-PautoBumpVersion=false` 关闭
- 资源文件按语言分离，支持多语言扩展

### 技�?
- 新增 `utils.NetworkErrorHandler` - 网络错误统一处理
- 新增 `utils.I18nHelper` - 国际化辅助工�?
- `debugImplementation leakcanary-android:2.14`

---

## v31 1.10.3 - 2026-05-11

### 新增
- 首次启动权限使用说明对话框，支持一键授权或暂不授权
- R8/ProGuard 代码混淆，Release APK 体积�?22MB 减小15.58MB（约30%�?
- Lint 规则配置，支�?release 构建时严格检查

### 优化
- 斗地主联机核心逻辑拆分支3 个独立管理类（DouDiZhuProtocol、DouDiZhuSeatManager、DouDiZhuSyncManager�?
- 删除 res/raw/doudizhu_archive/ 目录96 个重复音频文�?
- 移除未使用的 androidx.webkit 依赖
- ProGuard 规则完善，确保所有游戏类和第三方库不被混�?

### 修复
- 修复工具箱布局引用确认问题

---

## v30 1.3.16 - 2026-05-11（正式版）

### 测试覆盖完善

#### 新增单元测试
- **井字�?TicGameTest**�? 个测试用例，覆盖初始状态、落子、胜负判定、重置等
- **2048 Game2048GameTest**，0 个测试用例，覆盖初始状态、移动合并、分数计算、重置等
- **贪吃�?SnakeGameTest**，0 个测试用例，覆盖初始状态、方向控制、移动、撞墙判定等
- **记忆翻牌 MemoryGameTest**�?1 个测试用例，覆盖初始状态、翻牌、配对、重置等
- **中国象棋 ChineseChessGameTest**，0 个测试用例，覆盖初始棋盘、棋子移动、胜负判定等
- **猜数GuessGameTest**�? 个测试用例，覆盖初始状态、猜测判定、难度切换等
- **掷骰�?DiceGameTest**，0 个测试用例，覆盖初始状态、骰子类型判定、投掷等

#### 测试统计
| 游戏 | 测试文件 | 测试用例�?|
|------|----------|-----------|
| 五子�?| GomokuGameTest | 12 |
| 围棋 | GoGameTest | 12 |
| 华容道| KlotskiGameTest | 3 |
| 井字�?| TicGameTest | 9 |
| 2048 | Game2048GameTest | 10 |
| 贪吃�?| SnakeGameTest | 10 |
| 记忆翻牌 | MemoryGameTest | 11 |
| 中国象棋 | ChineseChessGameTest | 10 |
| 猜数| GuessGameTest | 9 |
| 掷骰�?| DiceGameTest | 10 |
| **总计** | **10 个测试文�?* | **96 个测试用�?* |

#### 新增测试文件
| 文件 | 说明 |
|------|------|
| `TicGameTest.java` | 井字棋单元测试|
| `Game2048GameTest.java` | 2048 单元测试 |
| `SnakeGameTest.java` | 贪吃蛇单元测试|
| `MemoryGameTest.java` | 记忆翻牌单元测试 |
| `ChineseChessGameTest.java` | 中国象棋单元测试 |
| `GuessGameTest.java` | 猜数字单元测试|
| `DiceGameTest.java` | 掷骰子单元测试|

---

## v29 1.3.15 - 2026-05-11（正式版）

### 用户体验优化

#### 交互式教程系�?
- **InteractiveTutorialDialog**：新增交互式教程对话框，支持 ViewPager2 多页滑动
- **分步引导**：将复杂游戏规则拆分为多个页面，降低学习门槛
- **圆点指示�?*：显示当前页面位�?
- **动画效果**：页面切换带有平滑过渡动�?

#### 音效反馈系统
- **SoundManager**：新增通用音效管理器，支持音效池和背景音乐
- **音效控制**：设置中可开关音效和震动反馈
- **BaseGameActivity**：游戏基类集成音效、震动、动画功�?

#### 动画效果
- **页面过渡动画**：fade_in、fade_out、slide_in_right、slide_out_left
- **交互反馈动画**：button_press 按钮点击动画
- **胜利庆祝动画**：win_celebrate 缩放旋转动画

#### 新增文件
| 文件 | 说明 |
|------|------|
| `SoundManager.java` | 通用音效管理器|
| `BaseGameActivity.java` | 游戏基类，集成音效和动画 |
| `InteractiveTutorialDialog.java` | 交互式教程对话框 |
| `dialog_interactive_tutorial.xml` | 交互式教程布局 |
| `item_tutorial_page.xml` | 教程页面项布局 |
| `dot_active.xml` | 活动状态圆点指示器 |
| `dot_inactive.xml` | 非活动状态圆点指示器 |
| `fade_in.xml` | 淡入动画 |
| `fade_out.xml` | 淡出动画 |
| `slide_in_right.xml` | 右侧滑入动画 |
| `slide_out_left.xml` | 左侧滑出动画 |
| `scale_up.xml` | 缩放弹出动画 |
| `button_press.xml` | 按钮点击动画 |
| `win_celebrate.xml` | 胜利庆祝动画 |

#### 修改文件清单
| 文件 | 改动 |
|------|------|
| `SettingsManager.java` | 添加音效和震动设|
| `GameTutorialHelper.java` | 五子棋、中国象棋、围棋等游戏改用交互式教�?|
| `colors.xml` | 添加 dark_gray、gray_light、purple_500 颜色定义 |

---

## v28 1.3.14 - 2026-05-11（正式版）

### 性能优化

#### 图片加载优化
- **Glide 图片缓存**：游戏列表图标使�?Glide 库进行懒加载，支持内存和磁盘缓存
- 添加 `com.github.bumptech.glide:glide:4.16.0` 依赖

#### 网络优化
- **OkHttpClientProvider**：新增统一�?OkHttp 客户端管理类，所有网络模块共享实现
- **HTTP 缓存**�?0MB 磁盘缓存，减少重复网络请�?
- **自动重试**：网络请求失败时自动重试 3 次，指数退避延�?
- **连接复用**：GameSocketServer、GameSocketClient 统一使用 OkHttpClientProvider

#### 内存优化
- **资源及时释放**：所有游戏 Activity �?onDestroy 中正确释放资�?
- **Handler 回调清理**：游戏暂�?销毁时移除所有待执行的回�?

#### VPS 架构调整
- **美国 VPS 仅作备用更新增*：明确美�?VPS 不承担游戏联机任�?
- **香港 VPS 承担主要服务**：更新服务、WebSocket Relay、HTTP Relay、反馈服�?

#### 修改文件清单
| 文件 | 改动 |
|------|------|
| `app/build.gradle` | 添加 Glide 依赖 |
| `GamesFragment.java` | 使用 Glide 加载游戏图标 |
| `OkHttpClientProvider.java` | 新增：OkHttp 统一管理器|
| `App.java` | 初始化器OkHttpClientProvider |
| `GameSocketServer.java` | 使用 OkHttpClientProvider，添加 Context 参数 |
| `GameSocketClient.java` | 使用 OkHttpClientProvider，添加 Context 参数 |
| `BaseOnlineActivity.java` | 传统 Context 到网络模块 |
| `RockOnlineActivity.java` | 传统 Context 到网络模块 |
| `GoOnlineActivity.java` | 传统 Context 到网络模块 |
| `ChineseChessOnlineActivity.java` | 传统 Context 到网络模块 |
| `GomokuOnlineActivity.java` | 传统 Context 到网络模块 |
| `doudizhu/network/GameSocketServer.java` | 使用 OkHttpClientProvider，添加 Context 参数 |
| `doudizhu/network/GameSocketClient.java` | 使用 OkHttpClientProvider，添加 Context 参数 |
| `README.md` | 更新依赖表、添加性能优化说明、更�?VPS 架构说明 |

---

## v27 1.3.13 - 2026-05-11（正式版）

### 联机功能全面修复

#### 核心 Bug 修复
- **修复房间码一闪而过**：`GameSocketServer` 添加 `ROOM_STATE` 消息忽略，避免relay 服务器消息误判为客户端加密
- **修复双方不在同一对局**：主机走棋后直接调用 `sendSyncState()`，不再走 `onHostMessageReceived` 导致状态不同步
- **修复客户�?ID 检测失�?*：移至`clientId == 1` 的错误判断，改为接受任意客户端连接
- **修复主机/客户端玩�?ID**：主�?`myPlayerId = 1`，客户端 `myPlayerId = 2`，确保回合判断正�?

#### 胜利状态同步修复
- **五子�?*：客户端收到 `SYNC_STATE`/`GAME_OVER` 时直接调用`game.setGameOver(winner)` 设置胜利状�?
- **中国象棋**：`handleGameOver` 正确调用 `game.setGameOver(winnerSide)` 同步胜利�?
- **围棋**：添�?`GoGame.setGameOver()` 方法，客户端同步时设置游戏结束状�?
- **石头剪刀�?*：主�?`resolveRound` 中添�?`showRoundResult` 调用，主机也能看到比赛结�?

#### UI 改进
- **等待对话框优化*�? 个联机游戏的等待弹窗显示大号蓝色房间�?+ "复制房间�?按钮 + "取消"按钮
- **内联聊天�?*：所有联机游戏在棋盘下方添加内联聊天区域�?行高消息显示。+ 输入�?+ 发送按钮）
- **联机棋盘复用单机 View**�?
  - 中国象棋联机复用 `ChineseChessView`（渐变棋子、选中高亮、最后落子标记、动画）
  - 围棋联机复用 `GoView`（棋子边框、星位标记、最后落子标记）
- **断线重连 UI**：联机断线时显示弹窗，客户端可选择"重新连接"�?离开房间"，主机端可选择"等待重连"

#### 架构优化
- **BaseOnlineActivity 基类**：抽取联机游戏通用逻辑（房间管理、聊天、连接状态），减少代码重
- **工具层Binder 拆分**：`AdvancedToolBinders` 中的 9 个工具拆分为独立 Binder 类，保持一致�?
- **单元测试**：添�?`GomokuGameTest`�?2 个测试）�?`GoGameTest`�?2 个测试），覆盖胜负判断逻辑

#### 更新模块优化
- **下载通知**：更新下载时显示通知栏进度，下载完成后点击可直接安装

#### 新增文件
- `OnlineChatHelper.java`：可复用的联机聊天组件，支持内联模式和弹窗模�?
- `BaseOnlineActivity.java`：联机游戏基类，封装通用逻辑
- `NetworkDiagnosisToolBinder.java` �?9 个工�?Binder �?
- `GomokuGameTest.java`：五子棋单元测试
- `GoGameTest.java`：围棋单元测试

#### 修改文件清单
| 文件 | 改动 |
|------|------|
| `GameSocketServer.java` | 添加 `ROOM_STATE` 消息忽略 |
| `GomokuOnlineActivity.java` | 修复胜利同步、添加内联聊天、修复玩家 ID |
| `ChineseChessOnlineActivity.java` | 修复胜利同步、复制ChineseChessView、添加内联聊天、修复玩家 ID |
| `GoOnlineActivity.java` | 修复胜利同步、复制GoView、添加内联聊天、修复玩家 ID |
| `RockOnlineActivity.java` | 修复结果同步、添加内联聊天、修复玩家 ID |
| `GomokuGame.java` | 添加 `setGameOver()`、`setCurrentPlayer()` 方法 |
| `GoGame.java` | 添加 `setGameOver()`、`setLastMove()`、`clearLastMove()` 方法 |
| `ToolsFragment.java` | 使用新的 Binder 类替换switch 语句 |
| `UpdateManager.java` | 添加下载通知功能 |
| `OnlineChatHelper.java` | 新增：可复用聊天组件 |
| `BaseOnlineActivity.java` | 新增：联机游戏基�?|

---

## v26 1.3.12 - 2026-05-11（正式版）

### 工具箱修复
- **修复工具箱全部功能失�?*：`ToolsAdapter.getItemViewType()` 返回错误的布局 ID，导致工具卡片无法正确显示
- 修正为返回`R.layout.item_tool_section` 包装布局

### 工具箱重
- 工具绑定逻辑拆分为独�?Binder 类，提升可维护�?
- 新增多个工具 Binder：BatteryToolBinder、DeviceToolBinder、DnsToolBinder、IpToolBinder、PingToolBinder、PortScanToolBinder、QrToolBinder、ScreenToolBinder、SensorToolBinder、SpeedTestToolBinder、SubnetToolBinder、SystemInfoToolBinder、TracerouteToolBinder、WifiToolBinder

---

## v25 1.3.11 - 2026-05-10（正式版）

### 更新源选择功能
- **设置页新增更新增选择�?*，位�?版本更新"标题�?
- 支持三种更新源：**自动（推荐）**�?*香港 VPS**�?*GitHub Releases**
- 用户可根据网络环境手动指定首选更新源
- 指定源失败后仍会自动尝试备用�?

### 修复 beta 用户检查更新问题（增强版）
- 修复本地�?beta 版本号`acceptBeta=false` 时，请求 release 版本不存在导致检查失败的问题
- �?release 版本不存在且本地�?beta 版本时，自动 fallback 检查beta 版本
- beta 用户即使未开接受测试设置，也会提示有 beta 更新可用（blocked 状态）

---

## v24 1.3.10 beta - 2026-05-10

### 联机功能全面扩展
- **新增 4 个游戏的云联机功能*：剪刀石头布、五子棋、中国象棋、围�?
- 所有联机均使用**香港 VPS WebSocket 中继服务*，支持远程双人对战
- **公共网络模块**：抽�?`com.GameMatrix.app.network` 包，所有游戏共享同一套网络基础设施
  - `GameSocketServer.java` �?房主权威服务
  - `GameSocketClient.java` �?客户端连接管�?
  - `RelayHttpClient.java` �?HTTP Relay 通信 + WebSocket URL 生成
  - `LANManager.java` �?局域网 NSD 服务发现
  - `RemoteP2PUtil.java` �?房间码工具类
- **统一架构**：所有联机游戏采用主机权威性模型，房主验证所有操作，客户端发送操作后接收 SYNC_STATE 同步
- **状态版本机�?*：防止消息重复处理和乱序
- **断线重连**：支持基�?peer_token 的座位恢复

### 新增 OnlineActivity
| 游戏 | OnlineActivity | 联机协议 | 棋盘/玩法 |
|------|---------------|---------|----------|
| 剪刀石头�?| `RockOnlineActivity` | `ROCK://` | 双人对战，同时出�?|
| 五子�?| `GomokuOnlineActivity` | `GMK://` | 15×15 棋盘，先连五子者胜 |
| 中国象棋 | `ChineseChessOnlineActivity` | `XQ://` | 10×9 棋盘，完整规则验证|
| 围棋 | `GoOnlineActivity` | `GO://` | 9×9 棋盘，含提子和劫�?|

### UI 改进
- 四个游戏原有 Activity 均新增🌐 联机对战"按钮
- 点击后进入联机大厅，可选择创建房间或输入房间码加入
- 游戏内复用原�?View 渲染棋盘/界面

### 架构优化
- 网络模块从斗地主独立包中抽取到公共位置，避免代码重复
- 后续新增联机游戏只需创建 OnlineActivity，直接复用公共网络模�?
- 每个游戏独立 P2P_PREFS 命名空间和协议前缀，互不干�?

---

## v23 1.3.9 beta - 2026-05-10

### 修复 Beta 用户检查更新问题
- 修复 versionCode 162 �?beta 用户点击"检查更�?显示"已是最新版的问题
- beta 用户在没�?beta 更新时，现在会自动检查稳定版（version-release.json）是否有可用更新
- 当稳定版 versionCode 高于本地时，正确展示稳定版更新信�?
- 向后兼容：不影响旧版用户、稳定版用户和未开beta 更新的用�?
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平�?Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题
- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言�?
- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项
- 发布前检查需覆盖中文/英文两种语言、深�?浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮�?
## 2026-05-15 文档同步：Dependabot �?CI 修复

- Dependabot 安全告警已处理：升级 Android Gradle Plugin �?8.13.2、Gradle Wrapper �?8.13、Kotlin �?2.2.21、Hilt �?2.57.2�?
- 构建 classpath 已强制解析到安全版本：Netty 4.1.133.Final、BouncyCastle 1.84、commons-compress 1.26.0、jose4j 0.9.6、jdom2 2.0.6.1�?
- GitHub Actions 已改为验证型 CI：使�?JDK 21，执行debug 构建与单元测试，不在云端构建 release 包，避免暴露或依�?release 签名文件
- CI 命令统一添加 `-PautoBumpVersion=false`，避免自动修复`version.properties`�?
- `.gitignore` 和 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/GameMatrix/app/ai/data/` 源码�?
- 最�?GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆�?服务器部署GitHub Release 发布仍以本机发布流程为准�?

## [Current Workspace] - 2026-05-19 Core Modularization Phase 1

### Modularization
- Added `:core:common`, `:core:network`, and `:core:update` Android Library modules; `:app` now acts as the shell application and feature aggregator.
- Moved `SettingsManager`, `AppResult`, `AppError`, `NetworkResult`, `Extensions`, `LazyInitManager`, `MemoryUtils`, and `AccessibilityHelper` into `:core:common`.
- Moved `OkHttpClientProvider`, `RequestDeduplicationInterceptor`, `NetworkLogger`, `RelayHttpClient`, `RemoteP2PUtil`, and `NetworkErrorHandler` into `:core:network`.
- Moved the update subsystem (`UpdateManager`, checker/downloader/installer/notification helper, `UpdateInfo`, `SSLHelper`, `UpdatePresenter`, `UpdateViewModel`) into `:core:update`.
- `:core:network` and `:core:update` now generate module-level `BuildConfig` values from root `local.properties` and `version.properties`.

### Follow-up Note
- `CrashHandler` remains in `:app` because it still directly calls app-owned `ErrorReporter`.
- Local Gradle verification is currently blocked before compilation by a Windows socket/buffer resource error in Gradle file lock startup.



---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
