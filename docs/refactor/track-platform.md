# Track: 整体模块化架构 + UI 主题改造

> 调研日期: 2026-06-04
> 最后更新: 2026-07-06 (循环 19-24 复核)
> 负责人: Coder (track-platform)
> 协作轨道: 横向支撑，覆盖 AI / VPN / Tools 之外的架构层
> 受众: Mavis orchestrator + 后续 track-ai/track-vpn/track-tools 实施者
> 状态: 调研完成，待用户评审
> 当前版本: versionCode=567 / versionName=1.4.1 (lastStable=465/1.4.0)
> 项目根: d:\Developmment\GameMatrixApp

---

## 0. 摘要 (TL;DR)

项目处于 **"双层模块化探索中"** 的状态：

- **宿主 APK**（`com.gamecenter.app` / `com.GameMatrix.app`）已抽出 `:core:*` 公共模块 7 个
- **动态 APK 模块**（`module-store/feature/*`）共 10 个 Gradle 子工程，全部为 `com.android.application` 类型、靠 `compileOnly` 引用宿主 jar（不是 Android 官方 `Dynamic Feature` 也不是 `Bundle` 拆分）
- **运行时模块框架** 有 **两套并存**：
  1. `app/src/main/java/com/gamecenter/app/modules/*` — 现行生产路径（Kotlin，简化版）
  2. `core/moduleloader` + `core/module-host` + `core/modulestore` — v2.0 设计稿（Java + Kotlin 混合，更完整但**未被主代码调用**）
- **UI 主题** 已迁移到 Material 3 DayNight，配色已抽出 token，但 dark/light 调色盘与模块商店的霓虹蓝调色不一致，存在回归风险
- **核心改造重点**（用户口径："重点是模块化推进 + 从功能、美观入手"）：
  1. 收敛 v1/v2 双套模块框架
  2. 把 29 个游戏模块化分工稳定下来
  3. 主题 token 化、首页与模块商店视觉升级
  4. 严格避开 DONT_DO_THIS.md 列出所有禁用项

---

## 1. 模块化架构现状

### 1.1 Gradle 工程结构

根 `settings.gradle`（2026-05-19 Modularization Update 后）：

```text
GameMatrixApp/                                       (root, pluginManagement 集中)
├── app/                                             (com.android.application, 主壳)
├── core/                                            (com.android.library)
│   ├── common/                                      (SettingsManager / ModuleInterface / 工具集)
│   ├── network/                                     (OkHttpProvider / RelayUrl / 错误处理)
│   ├── update/                                      (UpdateManager + ViewModel)
│   ├── security/                                    (SecureOkHttpFactory 证书固定)
│   ├── module-host/                                 (Phase 2 引入, ClassLoader 池)
│   ├── moduleloader/                                (Phase 2 引入, DexClassLoader + V2 引擎)
│   ├── modulestore/                                 (Phase 2 引入, 内置/下载/安装/卸载)
│   └── online/                                      (Phase 3 引入, WebSocket + 房间)
├── module-store/feature/                            (com.android.application, 动态 APK)
│   ├── games/games/
│   │   ├── hall/                (游戏大厅, 顶层容器)
│   │   ├── chinesechess/       (中国象棋)
│   │   ├── game2048/            (2048)
│   │   ├── klotski/             (华容道)
│   │   └── tts/                 (TTS 语音合成)
│   └── tools/
│       ├── ai/                  (AI 助手)
│       ├── browser/             (内置浏览器)
│       ├── tools/               (工具箱)
│       └── vpn/                 (VPN)
├── 模块商店/                                        (文档 + ZIP 包目录, 实际 ZIPs)
└── build-logic/                                     (buildSrc/convention 插件)
```

> **命名不一致警告**: 目录用 `module-store`，Gradle include 用 `':module-store:feature:*'`；文档用 `模块商店/`，项目主代码用 `com.gamecenter.app.modules.*`。建议下个里程碑统一为 `modules/`。

### 1.2 宿主 / 动态 APK 数量分布

> **2026-07-06 复核**: 动态 APK 数量已从 10 个调整为 9 个（循环 20 新增 wrongbook，部分原 games 子模块合并），详见循环 19-24 章节复核表。

| 类别 | 数量 | 形态 | 加载方式 |
|------|------|------|----------|
| 宿主 APK | 1 (`app`) | `:app` 编译产物 | 系统安装 |
| 核心动态 APK 模块 | 9 (games/{hall,chinesechess,game2048,klotski,tts} + tools/{ai,tools,vpn,wrongbook}) | `com.android.application` 独立 APK | DexClassLoader + AssetManager.addAssetPath |
| 游戏 ZIP 模块 | 25–29（按 modules.json v11） | ZIP 资源包 | 复用宿主游戏代码（见 §1.3） |
| 内置游戏 Activity | 6+ | 宿主 `app/src/main/java/.../games/` 硬编码 | `GameRegistry` 注解 + 动态注册 |

**当前 `modules.json` v11 状态**: 游戏模块 29 个 / 全部模块 33 个（host 内置 + dynamic），全部已上传 VPS `/var/www/modules/` 和 `/var/www/update/modules/`。

### 1.3 模块发现 / 更新 / 卸载流程

```text
[启动] App.onCreate
  └─ AppModule 初始化
     └─ ModuleManager.loadModuleList(context, callback)
        ├─ registerBundledModuleList() → 读 assets/modules.json
        ├─ if cache HIT → parse cached JSON → 回调一次（cache 渲染）
        └─ 后台 Thread: fetchRemoteModulesInternal()
            └─ SecureOkHttpFactory.buildModuleClient().newCall(MODULES_URL)
                └─ JSON { version, modules[] }
                   └─ 版本对比 (remote < local? remote == local? remote > local?)
                      └─ 写 SharedPreferences (modules_list_version + json)
[下载] 用户点击 → ModuleStoreActivity.downloadModule()
  └─ ModuleManager.downloadModule() → ModuleDownloader
     ├─ 取 module.getAllDownloadUrls() = [downloadUrl, fallbackUrl, githubUrl]（2026-06-19 起 fallbackUrl 已废弃，US VPS 下线）
     ├─ 两级源切换 (50KB/s 阈值)（2026-06-19 起 US VPS 下线，三级简化为两级）
     ├─ SHA-256 校验 (manifest.sha256, 为空时拒绝安装)
     └─ 完成后 unloadModule + markInstalled + registerInstalledGameModules
[加载] 用户点击 open → ModuleStoreActivity.openModule()
  └─ 依据 type (nav/game/tool) 派发:
     ├─ nav + 核心模块 (games_hall/browser/tools/ai/vpn) → MainActivity 切 tab
     ├─ game + entryClass → DynamicGameActivity
     ├─ game + builtIn + activityClass → Class.forName(...).newInstance()
     └─ FeatureModule → DynamicGameActivity 承载 Fragment
[卸载] ModuleManager.uninstallModule()
  ├─ ModuleLoader.unloadModule() (stop + remove from maps)
  ├─ delete file (modules/<filename>)
  ├─ removeInstalledModule (clear SharedPreferences)
  └─ if type == "game" → GameRegistry.unregister()
```

### 1.4 ClassLoader / ResourceLoader 实现

**当前生产路径（`app/.../modules/ModuleLoader.kt`）**：
- `DexClassLoader(apkFile.absolutePath, optimizedDir, libraryDir, context.classLoader)`
- 资源：`com.gamecenter.app.modular.ModuleResourceLoader` —— `AssetManager.addAssetPath()` 反射 + 包装 ContextWrapper
- DEX 优化缓存：`cacheDir/modules_opt/<baseName>*.dex`，加载前 clear
- 版本感知重加载：`installedVersion >= manifest.versionCode` 时返回缓存，否则 `unloadModule` + `clearOptimizedDex` + reload

**v2 候选路径（`core/moduleloader/*`）**：
- `ModuleLoaderV2`（单例）：整合 Verifier + DexCacheManager + ResourceLoader + HotReloader
- `ModuleResourceLoader`（更完整：`getDrawable`/`getString`/`getLayout` + `ContextWrapper` + AssetManager 缓存）
- `ModuleVerifier`：完整校验（文件存在 → 大小 → APK 签名 → SHA-256 → minFrameworkVersion）
- `DexCacheManager`：50 MB 单模块上限 / 200 MB 全局上限 + LRU 清理
- `ModuleHotReloader`：FileObserver + 5 秒轮询双模式

**问题**：v2 已写完（372 + 414 + 333 + 434 + 292 ≈ 1800 行 Java），但 `app/build.gradle` 引入的 `project(':core:moduleloader')` 实际未被任何 `app/` 业务代码引用（仅 `compileOnly` 形式 via `:core:module-host` 间接？需验证）。`grep` 全工程无 `import com.gamecenter.app.moduleloader.ModuleLoaderV2`。

### 1.5 资源冲突 & 模块间通信

**资源冲突**：
- 模块 APK 通过 `addAssetPath` 追加到 `AssetManager`，但宿主 R 类是合并后的全量 R，模块不能用宿主 `R.layout.xx` 引用宿主资源
- `ModuleLoader.kt:14` 中 `resourceLoaders[manifest.id]` 只缓存 `ModuleResourceLoader.ModuleResources`，模块间不共享 Resources
- 资源覆盖靠 `app/build/intermediates/.../R.jar` 的 `compileOnly files(...)` 把宿主 R 暴露给模块（`hall/build.gradle:38`），但 module-store 自身有 R 类（AAR 合并顺序敏感）

**模块间通信**：
- 没有 EventBus；只有 type-based 派发（`ModuleStoreActivity.openModule` 长达 100 行的 if/else）
- 跨模块调用主要靠宿主 `DynamicGameActivity` 转发 + `FeatureModule.createFragment()` 桥接
- `module-store/feature/games/games/hall` 的 `HallScreen.kt`（Phase 2.4 Compose 样板）通过 `HallViewModel` 拉数据，**还没接 `ModuleManager`**

### 1.6 模块间通信现状（模块依赖图）

```text
                 ┌──────────────┐
                 │  :app (host) │
                 │  ──────────  │
                 │  MainActivity│
                 │  ModuleStore │
                 │  ModuleMgr*  │
                 │  ModuleLoader│
                 └──┬─────┬─────┘
                    │     │ implementation
       ┌────────────┘     └────────────┐
       ▼                                ▼
  ┌─────────┐    ┌──────────┐    ┌──────────┐
  │core:    │    │core:     │    │core:     │
  │common   │◄───┤network   │◄───┤update    │
  │         │    │          │    │          │
  │IModule  │    │OkHttp    │    │UpdateVM  │
  │(v2)     │    │          │    │          │
  └────▲────┘    └──────────┘    └──────────┘
       │ compileOnly
       ├────────────────────────────────────┐
       │                                    │
  ┌────┴────────┐                  ┌───────┴────────┐
  │:core:module-│                  │:core:modulestore│
  │  host       │                  │                │
  │             │                  │Installer/...   │
  │ClassLoader  │                  └────────────────┘
  │Pool         │
  └─────────────┘
       ▲ compileOnly (在 feature 子模块的 build.gradle)
       │
  ┌────┴────────┐  ┌────────────┐  ┌─────────────┐
  │feature/games│  │feature/    │  │feature/tools│
  │  games/hall │  │  games/... │  │  /ai/...    │
  │  /chess/... │  │            │  │  /vpn/...   │
  └─────────────┘  └────────────┘  └─────────────┘
```

> ⚠️ **没有依赖限制**：`feature/*` 都声明 `compileOnly project(':core:common')`，但能直接 `implementation` 任何东西。一旦有人 `implementation` 宿主 `com.gamecenter.app.games.*`，会绕过模块边界。需要 Enforce 插件或 `dependencyConstraints`。

---

## 2. 主题 / UI 现状

### 2.1 资源目录速览

```text
app/src/main/res/
├── values/             (default = light palette)
│   ├── colors.xml      (141 行, 包含 brand_*, eye_care_*, md_theme_*, ai_message_*)
│   ├── themes.xml      (Theme.GameMatrixApp, Splash, Dark - 95 行)
│   ├── styles.xml      (Widget.GameMatrix.* - 186 行)
│   ├── dimens.xml      (spacing_*, button_height_*, font_size_*, corner_radius_*)
│   └── strings.xml + 33 个 strings_game_*.xml
├── values-night/       (夜间覆盖)
│   ├── colors.xml      (58 行, 重写 md_theme_* + 游戏专用色)
│   └── themes.xml      (24 行, 覆盖 statusBar 等少量项)
├── values-en/          (英文, strings.xml 20.8K 字节)
└── (无 values-zh)      (中文为默认)
```

### 2.2 主题继承链

```text
Theme.Material3.DayNight.NoActionBar      (com.google.android.material 1.12.0)
  └── Theme.GameMatrixApp                  (parent, light)
       ├── colorPrimary  → md_theme_light_primary      #5B8A72 (暖绿)
       ├── ... 28 个 token 全部映射到 md_theme_light_*
       ├── materialButtonStyle → Widget.GameMatrix.Button
       └── materialCardViewStyle → Widget.GameMatrix.Card
       └── Theme.GameMatrixApp.Dark        (parent=Theme.GameMatrixApp, override 28 个 color)
              ├── colorPrimary → md_theme_primary       #00D9FF (电青)
              ├── android:windowLightStatusBar=false
              └── Theme.GameMatrixApp.Splash
```

### 2.3 ⚠️ 现状 bug 速记（不改，先记录）

| 项 | 现状 | 风险 |
|----|------|------|
| `values/colors.xml` vs `values-night/colors.xml` | 默认文件的 `md_theme_*` 是**电青暗色调**（`#00D9FF` 紫红黄），night 文件却**重写为 M3 标准暗紫**（`#D0BCFF`）—— 重复定义但内容冲突 | 切换系统暗色时实际生效的是 `values-night/colors.xml` 的 M3 标准色，与 brand 偏离 |
| `values/colors.xml` 内部 | 同时定义 `md_theme_*`（暗色）和 `md_theme_light_*`（亮色） | 名字容易混，新人改 token 时会踩坑 |
| `values/themes.xml` line 5-7 | `colorPrimary = @color/md_theme_light_primary`（#5B8A72 暖绿） | 正常亮色是绿，但 night 模式下变 `#00D9FF` 青色，**品牌识别不连续** |
| `values-night/themes.xml` | 只覆盖了 14 个 token，**没有覆盖 statusBarColor/elevation** | 状态栏/导航栏行为依赖 `Theme.GameMatrixApp` 父类，可能不正确切换 |
| `dimens.xml` token | 已抽 spacing_*、button_height_*、font_size_*、corner_radius_*、elevation_* | **但 grep 显示 80% 的 layout 仍用 `android:layout_margin="16dp"` 等硬编码**，token 化覆盖率低 |
| 国际化 | `values-en/strings.xml` 20.8K vs `values/strings.xml` 22.6K | 缺 ~300 条目；游戏专用 strings_game_*.xml 仅中文版，**英文用户看到的 13 个游戏是中文名** |
| 字体 | `font_size_*` token 化了，但**没有自定义 fontFamily** | 跟 Material 3 默认 Roboto 一致，没做到品牌定制 |
| 屏幕密度 | `resConfigs "zh-rCN", "en"`（语言）但没做 `drawable-xxhdpi/xxxhdpi` 拆分 | 已有 大量 mipmap-anydpi-v26 启动器图标，drawable 一律默认密度 |
| 启动动画 | `Theme.GameMatrixApp.Splash` 配 `windowFullscreen=true` | SplashActivity 应该有 lottie/淡入，**未确认有 lottie 依赖**（grep material 无 lottie） |
| 模块加载动画 | `ModuleStoreActivity.startSkeletonAnimation` 用 `ObjectAnimator alpha 1↔0.5` | 朴素，无 Lottie/ShapeableImageView 进度条 |
| 错误动画 | `ErrorReporter` + `AppError`（10 个错误类型）已统一 | Toast 直接弹，**无 Error 状态设计**（没插画/重试按钮） |

### 2.4 视觉一致性

| 维度 | 现状 | 一致性 |
|------|------|--------|
| 卡片样式 | `Widget.GameMatrix.Card`（圆角 16dp, 4dp elevation）+ Game（20dp/6dp, 1dp stroke）/ Module（16dp/3dp, 0.5dp stroke） | 良，但 Game/Module 卡片有 stroke 视觉跳跃 |
| 按钮样式 | 4 类（Button / Tonal / Outlined / Text / Icon）+ FAB + PlatformButton 兼容样式 | 良 |
| Chip | 2 类（Filter 32dp / Assist 24dp） | 良 |
| Tab | `Widget.GameMatrix.Tab` 用 `tabIndicatorFullWidth=false` + 3dp indicator | 中，TabLayout 仍默认 M3，无二级页签自定义 |
| 图标 | 53 个 `ic_*.xml`（游戏 + 功能 + 系统），全是 Material Symbols 风 | 一致，**但 launcher icon 仍用 mipmap-anydpi-v26（自适应）**，内部图标用 vector |
| 间距 | dimens token 已存在，**实际 layout 大量硬编码** | 差 |
| 圆角 | `corner_radius_small/medium/large/xlarge` token 化 | 中 |
| 阴影 | `elevation_none/small/medium/large/xlarge` token 化 | 中 |
| 字体 | 字号 token 化，无字重 token（都是 `bold` 或默认） | 差 |

### 2.5 View vs Compose 边界

- **现行主流**：View 系统（`AppCompatActivity` + `Fragment` + `RecyclerView` + `ConstraintLayout`）
- **Compose 已就位**：`hall/HallScreen.kt` 191 行样板（Phase 2.4 完成），`@Composable HallGrid` + `HallViewModel` + `@Preview`
- **依赖**：`com.google.android.material:material:1.12.0`（含 M3 组件），但 `androidx.compose.*` **未在 `:app` build.gradle 显式声明** —— Compose 子模块怎么编过？需在 `hall/build.gradle` 验证

### 2.6 Lint / Detekt / Baseline Profile

```text
app/build.gradle (2026-05-19 状态):
- lint:
  - abortOnError = false  ← Phase 1.5 故意（不要改回 true，见 DONT_DO）
  - warningsAsErrors = false
  - baseline = lint-baseline.xml
  - disable 'UnusedResources', 'AndroidGradlePluginVersion', 'GradleDependency',
           'TrustAllX509TrustManager', 'MissingTranslation', 'HardcodedText',
           'ContentDescription'  ← 关闭噪音
- baseline profile: androidx.profileinstaller:1.4.1 已配置
- detekt:
  - core:common, core:module-host 有 detekt-baseline.xml
  - ignoreFailures = true  ← Phase 1.1 故意（不要改回 false，见 DONT_DO）
- 测试: testImplementation MockWebServer / Robolectric / Mockito
- CI: .github/workflows/android_ci.yml (JDK 17, -PautoBumpVersion=false) ← 2026-07-06 循环 23 上线
```

> **2026-07-06 循环 23 CI 配置复核**：新增 `.github/workflows/android_ci.yml` 作为主 CI workflow（替代原 `.github/workflows/ci.yml` 的职责），以及 `.github/dependabot.yml` 做依赖周扫描。Dependabot 当前 0 open alerts（7 个已 dismissed，循环 24 Netty 升级后清零）。详见 `CLOUD-BUILD.md` §3/§5。

---

## 3. 关键问题清单

按影响范围 × 修复成本排序：

### P0 — 必须修（阻塞模块化推进 + 隐藏风险）

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| P0-1 | **双层模块框架并存** — v1 (`app/.../modules/`) 是生产路径，v2 (`core/moduleloader/`) 是设计稿但未被业务调用 | 跨 6 个包 | 模块化推进的方向性混乱，新人不知跟哪边 |
| P0-2 | **模块类型/接口分裂** — `IModule`（`com.gamecenter.app.interfaces`，已用）/ `ModuleInterface`（`com.gamecenter.app.core.common`，已用）/ `FeatureModule`（独立）/ `IModuleLoader`（v2）四个概念相似但签名不同 | core:common vs app:modules | 跨模块类型转换全是 `as?` + `instanceof`，编译期不安全 |
| P0-3 | **`ModuleManifest` 三套模型** — app `ModuleManifest`（Kotlin data class, 25 字段）/ core:common `ModuleInfo`（Java model, 11 字段）/ core:module-host `ModuleManifest`（Kotlin data class, 4 字段） | 三处 | 模块描述字段分散，写代码要在三个模型间 `fromJson`/`toJson` 翻译 |
| P0-4 | **资源加载器两套** — `com.gamecenter.app.modular.ModuleResourceLoader`（app 引用）/ `com.gamecenter.app.moduleloader.ModuleResourceLoader`（core） | 两处 | 一个是给 inflate 用，一个是给 getDrawable/getString 用，混着调 |
| P0-5 | **APK 签名未强校验** — `ModuleLoader.kt:49` 只校验 SHA-256，**不校验签名者**。`SecureOkHttpFactory` 做了证书固定（只对传输），**模块本身可以是任意 keystore 签的** | `app/.../modules/ModuleLoader.kt:49` | 模块可被替换 + 重签 → 注入恶意代码 |
| P0-6 | **`Theme.GameMatrixApp` dark/light 调色盘冲突** — 详见 §2.3 表 | res/values/colors.xml, themes.xml | 切暗色时品牌色跳变（绿→青→紫） |

### P1 — 该修（影响效率）

| # | 问题 | 位置 | 影响 |
|---|------|------|------|
| P1-1 | **dimens token 化覆盖率低** — `dimens.xml` 有 spacing/button_height/font_size/corner_radius/elevation，但 ~80% layout 仍用 `16dp` 硬编码 | res/layout/* | 改主题 token 无感知 |
| P1-2 | **国际化漏覆盖** — `values-en/strings.xml` 缺 ~300 条，13 个 `strings_game_*.xml` 仅有中文版 | values-en/ | 英文用户看到中文 |
| P1-3 | **`ModuleStoreActivity.openModule` 长 if/else** — 100+ 行 `if (module.type == "nav" && ...)` 串接，类型新增时要改 4 处 | app/.../modules/ModuleStoreActivity.kt:381-480 | 新模块类型接入成本高 |
| P1-4 | **模块商店搜索逻辑硬编码 0xFF9E9E9E/0xFF4CAF50** — 应该用 `?attr/colorSurfaceVariant` / `?attr/colorPrimary` | ModuleStoreActivity.kt:186-187, 212-213, 229-230 | 切暗色时按钮颜色不变 |
| P1-5 | **断网/失败/无更新缺统一视觉** — 错误只 Toast，缺空态插画/重试按钮 | ModuleStoreActivity / GamesFragment | UX 不够"美观" |
| P1-6 | **无启动动画** — SplashActivity 配 `windowFullscreen` 但未引入 Lottie / AnimatedVectorDrawable | SplashActivity | 启动体验平淡 |
| P1-7 | **`core/moduleloader/.../ModuleResourceLoader` 未发布到 AAR** | core:moduleloader/build.gradle | v2 设计稿无法被 app 引用（即使想切） |

### P2 — 长期债

| # | 问题 |
|---|------|
| P2-1 | 游戏模块 29 个全靠 `ModuleManager.registerInstalledGameModules` 动态注册 + 6 个硬编码内置（gomoku/doudizhu 在 `registerGameFromManifest` line 492 强制跳过）|
| P2-2 | 模块商店类目硬编码 5 个（`CATEGORY_GAMES/BROWSER/TOOLS/AI/VPN`），加类目要改 4 处 |
| P2-3 | 模块卸载后 `app` 进程未重启可能持有 `ClassLoader` 引用，导致 OOM（`ModuleLoader.unloadModule` 只清 map，DexClassLoader 自身无 close） |
| P2-4 | 无 Material You 动态取色（Android 12+ 系统取色） |
| P2-5 | 缺少设计系统文档（`docs/` 无 design-system.md） |

---

## 4. 分阶段改造方案

> **强约束** — 所有阶段严格遵守 `docs/DONT_DO_THIS.md`（详见第 5 节）。本方案**不引入 kapt**、**不动 release-key.jks**、**不改 `keystore.properties`/`local.properties`**、**不 push**、**不 git filter-repo**、**不擅自 bump versionCode**、**不把 lint abortOnError 改回 true**、**不把 detekt ignoreFailures 改回 false**、**不切到 Compose 整屏强迁**、**不给 :app 加 jvm plugin**、**不开启 detekt allRules**。

### 4.1 短期 (1–2 周) — 主题 token 化 + 关键页面改版 + 暗色一致性

| 任务 | 范围 | 验收 |
|------|------|------|
| **ST-1 修 dark/light 调色盘冲突** | `values/colors.xml` 拆分：把 `md_theme_*` 改名为 `md_theme_dark_*`（明示用途），`md_theme_light_*` 保留 | 切换暗色时 brand 连续（暖绿 → 暖绿深 → 暖绿暗）|
| **ST-2 收敛 dimens token 使用** | 把 `res/layout/` 下高频硬编码 `16dp`/`8dp`/`24sp` 替换为 `@dimen/spacing_*`/`@dimen/font_size_*`，先从 `GamesFragment` + `ModuleStoreActivity` 入手 | `grep -r '"[0-9]\+dp"' res/layout/ \| wc -l` 下降 50% |
| **ST-3 国际化补漏** | 把 13 个 `strings_game_*.xml` 复制到 `values-en/` 并翻译；补 `values/strings.xml` 缺漏的 300 条 → `values-en/strings.xml` | 英文用户在游戏大厅看到 `Gomoku`/`Snake` 而非 `五子棋`/`贪吃蛇` |
| **ST-4 模块商店按钮改 token** | `ModuleStoreActivity.kt:186-187/212-213/229-230` 硬编码 0xFF9E9E9E / 0xFF4CAF50 改为 `?attr/colorSurfaceVariant` / `?attr/colorPrimary` | 切暗色时按钮颜色跟随 |
| **ST-5 启动动画** | `SplashActivity` 增加 AnimatedVectorDrawable 渐入 + 品牌 logo 呼吸效果 | 启动 < 1.5s 不感知"等"，> 1.5s 看到品牌 logo |
| **ST-6 首页（GamesFragment）改版** | 用 `Widget.GameMatrix.Card.Game`（圆角 20/elevation 6/glow stroke）做卡片大图、间距用 token，搜索栏改用 `Widget.GameMatrix.Search` | 暗色模式 + 大字体下无截断 |
| **ST-7 模块商店改版** | 顶部改用 `Widget.GameMatrix.Tab` 替代 TabLayout 默认 M3；卡片间距统一 16dp；加 skeleton/empty/error 三态 | 商店类目切换流畅，无白屏 |

**禁线**：
- ❌ 不引入 kapt（保持 KSP）
- ❌ 不切整屏 Compose（hall 已用 Compose 样板，**仅在 hall 模块继续**，其他 Activity 维持 View）
- ❌ 不改 `Theme.Material3.DayNight.NoActionBar` 父类（已经 M3）
- ❌ 不动 `GameMatrix.keystore` / `keystore.properties` / `local.properties`
- ❌ 不 bump versionCode（不跑 `assembleDebug`，仅 lint / 局部编译）
- ❌ 不 push

### 4.2 中期 (1–2 月) — 模块化推进 + 签名加固 + Compose 试点

| 任务 | 范围 | 验收 |
|------|------|------|
| **MT-1 收敛双层模块框架** | 选型：保留 v2 设计（`core/moduleloader/*` Java + `core/module-host` Kotlin + `core/modulestore` Java），把 v1（`app/.../modules/ModuleLoader.kt`）逐方法迁过去并删除。**先迁 `ModuleVerifier`**（纯校验，无业务依赖）| 1 个月内 app 代码 import v1 的次数从 14 处降到 0 |
| **MT-2 统一模块类型** | 选 `ModuleInterface`（`com.gamecenter.app.core.common`）作唯一接口；`IModule`/`FeatureModule` 标 `@Deprecated` + typealias；`ModuleManifest` 三套合并为 1 套（25 字段那份保留，11 字段那份是旧投影） | `grep "as? ModuleInterface" -r app/ core/` 单向收敛 |
| **MT-3 模块 APK 签名校验** | `ModuleLoader.loadModule` 加 `PackageManager.GET_SIGNATURES` 校验，**宿主只接受 Phase 1 生成的 GameMatrix.keystore 签名的模块** | 任意 keystore 签的 APK 加载时 `Log.e` + 拒绝 |
| **MT-4 灰度发布** | `modules.json` 加 `channel` 字段（stable/beta/canary），`ModuleManager.fetchRemoteModulesInternal` 优先 `stable`，按 `SettingsManager.betaChannelEnabled` 切 beta | 灰度模块可独立上下架 |
| **MT-5 宿主 APK 瘦身** | 把 `core:moduleloader`/`core:module-host` 拆出独立 Gradle 子项目（已是），开启 `minifyEnabled true` 在 release；启用 `splits.abi { enable true; include 'arm64-v8a' }` | APK 体积从 ~15MB → ~8MB |
| **MT-6 设计系统文档** | 新建 `docs/design-system.md`：列出所有 token（颜色/间距/圆角/阴影/字号/字重）、组件示例（Button/Card/Chip/Tab/Search）、深浅规则 | 新人按文档即可还原 95% 主题 |
| **MT-7 Compose 试点扩展** | 在 `hall/HallScreen.kt` 基础上，把 `AiFragment` 顶部摘要区改成 Composable（**不整屏迁**），并用 `ModuleStoreActivity` 顶部 5 个 Tab 改成 `AnimatedContent` | 试点 2 处都接 `MaterialTheme.colorScheme.*` |
| **MT-8 模块商店空态/错误态** | 加 `ic_empty_modules.xml` / `ic_error_network.xml` / `ic_error_module_load.xml`，对应 empty/error 状态 | 用户离线下拉显示"网络错误，重试"按钮 |
| **MT-9 内存泄漏基线** | 加 LeakCanary 报警（已有），跑 Monkey 1h 收集 `ModuleLoader.unloadModule` 后的 ClassLoader 引用报告 | 已知 OOM 路径有缓解 |

**禁线**：
- ❌ 不切整屏 Compose（试点限于 `hall` + `AiFragment` 局部 + `ModuleStoreActivity` Tab 区）
- ❌ 不换 keystore（MT-3 复用现 keystore 签模块，模块签名校验要用户在 build 脚本里把 GameMatrix.keystore alias 暴露给模块工程）
- ❌ 不开 detekt allRules
- ❌ 不重置 git 历史

### 4.3 长期 (3–6 月) — 全模块化 + Material You + 自适应 UI

| 任务 | 范围 | 验收 |
|------|------|------|
| **LT-1 全模块化** | 把 33 个模块的入口类（5 大类）全迁到 `module-store/feature/*`；宿主 `app/` 只剩 `MainActivity` + `ModuleStoreActivity` + `SplashActivity` + `App` + `DI Modules` | 宿主 APK 体积 ≤ 5MB |
| **LT-2 跨模块主题共享** | 把 `colors.xml` + `dimens.xml` + `Widget.GameMatrix.*` 全抽到 `core:common` 资源；模块用 `com.gamecenter.app.common.R.color.xxx` | 模块 APK 不重复带 token |
| **LT-3 Material You 动态取色** | 接入 `DynamicColors.applyToActivitiesIfAvailable(this)`（Material 1.12 已支持），用户可在设置中关闭 | Android 12+ 用户启动后看到壁纸取色 |
| **LT-4 自适应 UI** | 用 `WindowSizeClass` 做 4 类布局（compact/medium/expanded/tablet），大型棋盘（围棋/象棋）折叠为 2/3 屏 UI | 平板/折叠屏体验优化 |
| **LT-5 Baseline Profile 完善** | 给 `ModuleLoader.loadModule` 关键路径写 `baseline-prof.txt` | 冷启动 < 500ms（模块商店首屏） |
| **LT-6 模块签名强校验** | 引入 APK Signature Scheme v3.1（Android 13+），增量签名 + 密钥轮转 | 抵御回滚攻击 |

**禁线**：
- ❌ 不动 release-key.jks（v3.1 用同一个 keystore，不轮转）
- ❌ 不开 detekt allRules
- ❌ 不切 jvm plugin

---

## 5. DONT_DO_THIS.md 边界遵守声明

逐条对照，标注"绝对不做"和"做的时候要小心"：

| # | 规则 | 我们的立场 | 改造方案对应行动 |
|---|------|-----------|-----------------|
| 1 | ❌ 不加回 kapt | 0 容忍 | ST-1..7/MT-1..9/LT-1..6 全部用 KSP；Compose 试点也走 KSP（`com.google.devtools.ksp`）|
| 2 | ❌ 不 commit / push | 0 容忍 | 本轮只产生**文档**（`docs/refactor/track-platform.md`），不产生代码；即使后续写代码，也由用户明确说"提交"再 commit |
| 3 | ❌ 不撤销 MiMo API key | 0 容忍 | N/A（不动 local.properties）|
| 4 | ❌ 不改 keystore.properties / local.properties | 0 容忍 | MT-3 校验模块签名只**读** `GameMatrix.keystore` 的 SHA-1，不**改**任何 props |
| 5 | ❌ 不替换 release-key.jks | 0 容忍 | LT-6 用同一 keystore 升级到 v3.1，**不换** |
| 6 | ❌ 不 git filter-repo 清历史 | 0 容忍 | 不动 |
| 7 | ❌ 不改 version.properties 自动 bump | 0 容忍 | 跑 `assembleDebug` 加 `-PautoBumpVersion=false` |
| 8 | ❌ 不 push release 包到 GitHub Releases | 0 容忍 | 不发版 |
| 9 | ❌ 不切到 Compose 强迁整屏 | 强约束 | ST-1..7 全在 View 系统；MT-7 / LT-1..6 的 Compose 试点严格限于 `hall` + `AiFragment` 局部 + `ModuleStoreActivity` Tab；**Phase 2.4 `hall/HallScreen.kt` 样板已存在，沿用不扩** |
| 10 | ❌ 不把 lint abortOnError 改回 true | 0 容忍 | ST-1..7/MT-1..9/LT-1..6 全部维持 `abortOnError=false` + `lint-baseline.xml` |
| 11 | ❌ 不把 detekt ignoreFailures 改回 false | 0 容忍 | MT-1 合并代码时跑 `detekt` 增量检查但不强制通过 |
| 12 | ❌ 不给 :app 加 `org.jetbrains.kotlin.jvm` plugin | 0 容忍 | LT-1 全模块化时 `:app` 维持 `com.android.application` + `org.jetbrains.kotlin.android` |
| 13 | ❌ 不给 detekt 加 `allRules = true` | 0 容忍 | LT-1 不变 |
| 14 | ⚠️ 不假设 AI 知道机器配置 | 主动查 | 编译/运行相关命令前先验证 JDK/SDK 路径（`C:\Users\Administrator\.jdks\ms-17.0.19` + `C:\Users\Administrator\AppData\Local\Android\Sdk`）|

**额外内部约定**（基于项目常识，不是 DONT_DO 但要遵守）：

- 新增 `res/` 文件按现有命名：`activity_*`, `fragment_*`, `dialog_*`, `item_*`, `ic_*`, `bg_*`
- 修改 `GamesFragment` / `AndroidManifest.xml` / `strings.xml` 时同步检查入口、注册、文案
- 模块商店改动同步 `deploy/modules.json` 和 `app/src/main/assets/modules.json`
- 联机游戏改动参考 PROJECT_CONTEXT §8.2 斗地主 Beta 同步规则
- **Java/Kotlin 混合边界** 遵守 `CODE_WIKI.md` 第 10 章：核心类放 Kotlin 包，UI 控制器可放 Java

---

## 6. 风险与回滚

| 风险 | 概率 | 影响 | 回滚方案 |
|------|------|------|----------|
| ST-1 调色盘调整引发旧代码硬编码颜色显示错位 | 中 | 局部 UI 跳变 | 保留原 `md_theme_*` 别名 `@color/md_theme_legacy` 6 个月，渐迁 |
| MT-1 v1/v2 合并破坏模块加载 | 中 | 全模块加载失败 | 分 PR（先 Verifier 后 Loader）；用 `assembleDebug` + 装模拟器跑 5 个核心模块烟囱测试 |
| MT-3 签名校验拒签新模块 | 高 | 用户装模块失败 | 在 `ModuleVerifier` 加 `signingKeyAllowList` 灰度开关，默认开启但允许回退 |
| MT-7 Compose 试点与 View 主题色不一致 | 中 | 试点页面对比鲜明 | 用 `MaterialTheme(colorScheme = dynamicColorScheme(LocalContext.current))` 包装试点 |
| LT-3 Material You 取色破坏品牌 | 中 | 启动后第一眼变蓝/红/绿 | 设置页加"使用品牌色"开关，默认开 |

---

## 7. 与其他 track 的协作点

| 协作对象 | 我提供 | 我需要 |
|----------|--------|--------|
| track-ai | AI Fragment 主题 token（`ai_message_*` 颜色 + `Widget.GameMatrix.Chip.Assist`）| AI 任务路由数据结构、ViewModel 切 Compose 建议 |
| track-vpn | VPN 模块的模块化形态（已独立 APK）+ 主题应用 | VPN 协议参数 schema、是否走 DataStore 持久化 |
| track-tools | 工具箱 binder 的模块化状态 | 工具分类字典（用于 LT-1 全模块化时统一 storeCategory）|
| orchestrator | 3 阶段交付清单 + 风险 | 评审 + 决定是否做 MT-3 签名校验（涉及用户控制）|

---

## 8. 附录 — 关键文件清单（按优先级）

| 优先级 | 路径 | 行数 | 备注 |
|--------|------|------|------|
| ★★★ | `app/src/main/java/com/gamecenter/app/modules/ModuleLoader.kt` | 218 | v1 现行生产路径，MT-1 替换 |
| ★★★ | `app/src/main/java/com/gamecenter/app/modules/ModuleManager.kt` | 517 | v1 业务编排，MT-1 替换 |
| ★★★ | `app/src/main/java/com/gamecenter/app/modules/ModuleStoreActivity.kt` | 481 | ST-7 改版，MT-8 状态化 |
| ★★★ | `app/src/main/res/values/colors.xml` | 141 | ST-1 调色盘冲突 |
| ★★★ | `app/src/main/res/values/themes.xml` | 95 | ST-1 + 主题继承 |
| ★★★ | `app/src/main/res/values-night/colors.xml` | 58 | ST-1 dark palette |
| ★★★ | `app/src/main/res/values-night/themes.xml` | 24 | ST-1 dark theme |
| ★★ | `app/src/main/res/values/styles.xml` | 186 | ST-2/ST-7 复用 |
| ★★ | `app/src/main/res/values/dimens.xml` | 94 | ST-2 token 化起点 |
| ★★ | `core/moduleloader/src/main/java/com/gamecenter/app/moduleloader/ModuleLoaderV2.java` | 434 | v2 候选路径，MT-1 主迁入 |
| ★★ | `core/moduleloader/src/main/java/com/gamecenter/app/moduleloader/ModuleVerifier.java` | 333 | v2 完整校验，MT-1 + MT-3 入口 |
| ★★ | `core/moduleloader/src/main/java/com/gamecenter/app/moduleloader/ModuleResourceLoader.java` | 292 | v2 资源加载器 |
| ★★ | `core/moduleloader/src/main/java/com/gamecenter/app/moduleloader/DexCacheManager.java` | 414 | v2 DEX 缓存 |
| ★★ | `core/module-host/src/main/kotlin/com/gamecenter/app/core/modulehost/*.kt` | 3 文件 | ClassLoader 池（MT-1）|
| ★★ | `core/modulestore/src/main/java/com/gamecenter/app/modulestore/*.java` | 5 文件 | 内置/下载/安装/卸载（MT-1）|
| ★ | `module-store/feature/games/games/hall/src/main/java/com/gamecenter/app/games/hall/HallScreen.kt` | 191 | Phase 2.4 Compose 样板 |
| ★ | `app/src/main/java/com/gamecenter/app/modules/ModuleDownloader.kt` | 318 | 下载主逻辑 |
| ★ | `app/src/main/java/com/gamecenter/app/modules/ModuleManifest.kt` | 119 | 25 字段 manifest |
| ★ | `app/src/main/AndroidManifest.xml` | 175 | 模块商店入口注册 |
| ★ | `app/build.gradle` | 584 | minify/splits/Compose 待定 |
| ★ | `settings.gradle` | 51 | 11 个模块声明 |
| ★ | `docs/DONT_DO_THIS.md` | 74 | **强约束** |
| ★ | `docs/modules/MODULE_DEVELOPMENT_GUIDE.md` | 531 | 模块开发对外文档 |

---

## 9. 验证清单

- [x] 阅读 DONT_DO_THIS.md 14 条规则并落到每条行动
- [x] 阅读 PROJECT_CONTEXT.md 了解项目背景
- [x] 阅读 docs/modules/MODULE_DEVELOPMENT_GUIDE.md 了解模块开发约定
- [x] 阅读 app 侧 ModuleLoader/Manager/Store/Downloader 全部 4 个核心文件
- [x] 阅读 core:moduleloader 5 个 v2 文件
- [x] 阅读 core:module-host + core:modulestore 全文件
- [x] 阅读 colors/themes/styles/dimens + values-night + values-en
- [x] 阅读 app/build.gradle 确认依赖配置
- [x] 阅读 settings.gradle 确认 11 个模块声明
- [x] 阅读 hall 模块的 build.gradle + HallScreen.kt 确认 Compose 样板
- [x] 标注 Compose 试点边界（不整屏迁）

---

## 10. 循环 19-24 平台层进展复核 (2026-07-06)

### 10.1 宿主 Kotlin 迁移进展

循环 21-22 期间完成宿主层 Kotlin 迁移首批工作：

| 文件 | 语言变化 | 状态 | 备注 |
|------|---------|------|------|
| `app/src/main/java/com/gamecenter/app/App.kt` | Java → Kotlin | ✅ 已迁移 | Application 入口，Hilt @HiltAndroidApp 注解保留 |
| `app/src/main/java/com/gamecenter/app/MainActivity.kt` | Java → Kotlin | ✅ 已迁移 | 底部导航 + 模块注册逻辑，受 `ENABLE_WRONGBOOK` flag 控制 wrongbook tab 显示 |
| `app/src/main/java/com/gamecenter/app/games/GameRegistry.kt` | Java → Kotlin | ✅ 已迁移 | 游戏注册表，动态注册/反注册 API 保持兼容 |
| `app/src/main/java/com/gamecenter/app/modules/ModuleLoader.kt` | Kotlin (保持) | ✅ | v1 现行生产路径（参见 §1.4），未启动 v2 迁移 |
| `ToolsFragment.java` | Java (保持) | 🔄 待迁移 | 等 track-tools §2.1.1 的 `ToolCapability` Kotlin 接口落地后顺势迁移 |
| `ModuleStoreActivity.kt` | Kotlin (保持) | ✅ | ST-7 改版未启动 |

### 10.2 CI/CD 配置进展

循环 23 上线：

| 配置文件 | 用途 | 状态 |
|---------|------|------|
| `.github/workflows/android_ci.yml` | 主 CI workflow（lint + test + debug build + gitleaks） | ✅ 已上线，JDK 17 |
| `.github/dependabot.yml` | 依赖周扫描（Gradle + GitHub Actions） | ✅ 已上线 |
| `.github/workflows/ci.yml` | 旧 CI workflow | 🔄 已被 android_ci.yml 替代职责，文件状态以仓库实际为准 |
| `.github/workflows/cloud-build.yml` | 云编译专用 workflow（debug + release artifact） | ✅ 已存在 |

### 10.3 分发架构变更

- **HK VPS**：主分发节点，承担 beta + stable 通道
- **美国 VPS**：2026-06-19 已下线，`server.url.fallback` 字段保留空值向后兼容
- **GitHub Releases**：仅 stable 通道使用
- **Dependabot 状态**：0 open alerts（7 个历史 alerts 已在循环 24 Netty 升级后 dismissed）

### 10.4 循环 19-24 进展汇总

| 循环 | 平台层相关完成项 | 对应本 track 任务 | 状态 |
|------|----------------|------------------|------|
| 循环 19 | 浏览器原生重构 | LT-1 全模块化（browser 模块化已先期完成） | ✅ |
| 循环 20 | wrongbook 模块预装 | 新增 `tools/wrongbook` 动态 APK，扩展模块清单 | ✅ |
| 循环 21-22 | 宿主 Kotlin 迁移（App/MainActivity/GameRegistry） | 部分对应 MT-1（v1/v2 框架收敛未启动） | 🔄 |
| 循环 23 | CI 配置（android_ci.yml + dependabot.yml） | §2.6 已更新 | ✅ |
| 循环 24 | Netty 4.1.134 → 4.1.135.Final（7 CVE 修复） | 平台层传递依赖，不影响 APK 运行时 | ✅ |

### 10.5 平台层 P0/P1/P2 任务实际进度

| 任务 | 原计划阶段 | 实际状态 (2026-07-06) | 备注 |
|------|----------|---------------------|------|
| P0-1 双层模块框架并存收敛 | MT-1 | ⚠️ 未启动 | v1 仍是生产路径，v2 未被业务调用 |
| P0-2 模块类型/接口分裂统一 | MT-2 | ⚠️ 未启动 | IModule / ModuleInterface / FeatureModule / IModuleLoader 四套仍并存 |
| P0-3 ModuleManifest 三套合并 | MT-2 | ⚠️ 未启动 | 三套模型仍分散 |
| P0-4 资源加载器两套收敛 | MT-1 | ⚠️ 未启动 | app vs core 两套仍并存 |
| P0-5 APK 签名未强校验 | MT-3 | ⚠️ 未启动 | 仅 SHA-256 校验，未校验签名者 |
| P0-6 dark/light 调色盘冲突 | ST-1 | ⚠️ 未启动 | colors.xml 仍存在重复定义冲突 |
| P1-1 dimens token 化覆盖率 | ST-2 | ⚠️ 未启动 | ~80% layout 仍硬编码 |
| P1-2 国际化漏覆盖 | ST-3 | 🔄 部分推进 | wrongbook 模块新增字符串已按规范进 strings.xml + values-en/strings.xml |
| 循环 19-24 新增项 | — | ✅ | CI 上线 + Netty 安全升级 + 宿主 Kotlin 迁移首批 + wrongbook 模块预装 |

> **结论**：P0/P1 任务整体延后，循环 19-24 主要精力投入在功能扩展（wrongbook）和工程基础设施（CI / 安全 / Kotlin 迁移）上，与原 MT-1/MT-2/MT-3 的"收敛 v1/v2 框架"目标相比进度有限。建议在 wrongbook 模块稳定后（循环 25+）重新评估 MT-1 启动时机。

> 文档结束 — 待用户/Mavis 评审后定稿。


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
