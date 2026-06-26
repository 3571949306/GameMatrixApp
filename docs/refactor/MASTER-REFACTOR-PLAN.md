# GameMatrixApp 完整改造计划 (MASTER-REFACTOR-PLAN)

> 文档版本: v1.0
> 编写日期: 2026-06-04
> 编写人: general (Mavis orchestrator synthesis)
> 输入: 4 个并行 track 调研 (track-ai / track-vpn / track-tools / track-platform)
> 受众: 项目维护者 + Mavis 后续 plan 的 verify 阶段

---

## 0. 总览 (TL;DR)

### 0.1 一句话总结

**4 个 track 调研共同指向一个核心矛盾**: 项目处于"模块化探索中"的中期阶段 —— 4 个重点模块 (AI / VPN / Tools / Platform) **都已经在正确的工程方向上 (dynamic APK + 独立进程 + 沙箱化)**，但**每一个模块内部都还停在"骨架做对，肚子空着"的状态**：协议是桩、注册是硬编码、主题是双重、错误是裸枚举。

### 0.2 项目现状快照

| 维度 | 现状 | 期望 | 缺口 |
|------|------|------|------|
| **模块化粒度** | 10 个 dynamic APK + 29 个游戏 ZIP | 完整 dynamic 化 | 工具箱内部仍是硬编码 Map；模块框架 v1/v2 双层并存 |
| **AI 助手** | 独立 APK (`feature_ai_v100.apk`, 916KB)，响应**非流式** | 流式 + 多模态 + Function calling | 缺 SSE/分块、缺 Markdown 渲染、缺 Tool 协议 |
| **VPN 模块** | 独立 APK，4 协议**全为 Socket 桩**，Intent 目标类不存在 | sing-box 接入 + 真实 VpnService | 缺 sing-box AAR、缺 GameVpnService、缺 BIND_VPN_SERVICE |
| **工具箱** | 独立 APK + 27 个内置工具，**硬编码注册** | 标准化 ToolCapability + fractal 拆 | initBinders() 27 行 put 写死，布局在宿主 res |
| **UI 主题** | Material 3 DayNight，**dark/light 调色盘冲突** (绿→青→紫跳变) | 品牌色连续过渡 | colors.xml 与 values-night/colors.xml 重复定义 |
| **国际/视觉** | 英文漏覆盖 ~300 条；dimens token 覆盖率 ~20% | 100% 覆盖 + 全 token 化 | 80% layout 仍硬编码 `16dp` |
| **网络层** | OkHttpClientProvider 已统一，但**4 处违规源**仍自 new OkHttpClient.Builder | 零违规 | NETWORK_LAYER.md §已知问题待修 |
| **构建** | JDK 17 + AGP + KSP 已就位 | 本地可出 APK | 缺依赖可走 fallback；APK 落桌面 |

### 0.3 4 个 track 输出的关键发现串联

```text
                       用户原始 5 点要求
                              │
   ┌──────────┬──────────┬────┴────┬──────────┬──────────┐
   │ AI 重点  │ VPN 次要 │工具箱模块│整体模块化│功能+美观 │
   │  ★★★★★  │  ★★★★    │  ★★★★  │  ★★★★★   │  ★★★★   │
   └─────┬────┴────┬─────┴────┬────┴─────┬────┴────┬─────┘
         ▼         ▼          ▼          ▼         ▼
       track-ai  track-vpn  track-tools  track-platform
         │         │          │          │
         └─────────┴──────────┴──────────┘
                      │
                      ▼
          ┌────────────────────────┐
          │ 4 大 P0 共性瓶颈:        │
          │ ① OkHttp 自 new (4 处)   │ ← NET_LAYER §已知问题
          │ ② 暗色品牌跳变           │ ← track-platform P0-6
          │ ③ 错误码太粗             │ ← track-ai P0-4 / track-vpn P-09
          │ ④ 单测几乎为零           │ ← track-ai P0-5 / track-tools 隐含
          │ ⑤ Token 配额/流量不可见   │ ← track-ai P1-7 / track-vpn P-07
          └────────────────────────┘
                      │
                      ▼
              本计划 (MASTER-REFACTOR-PLAN)
```

### 0.4 推荐的 6 个月时间盒

| 阶段 | 时间 | 累计工作量 | 核心交付 |
|------|------|-----------|---------|
| 短期 Sprint 1 | W1-W2 (1-2 周) | 12 人天 | 3 个"零功能丢失"修 (流式输出 + sing-box 接入 + 调色盘修) |
| 短期 Sprint 2 | W3-W4 (1-2 周) | 24 人天 | 3 个"标准化"工程 (ToolCapability + 主题 token + 错误细化) |
| 中期 | M2-M3 (1-2 月) | ~90 人天 | 多模态 + 智能选路 + 模块商店 + Compose 试点 |
| 长期 | M4-M6 (3-6 月) | ~150 人天 | 全模块化 + Function calling + 工作流编辑器 |

> **预算注**: 上表人天为全栈单人估算；实际可拆 2-3 个并行 track，每个 30-50 人天。

---

## 1. 改造总目标

### 1.1 重点模块优先级表 (回应用户原始 5 点要求)

| 排名 | 模块 | 用户原话 | 重要性 | 紧迫性 | 复杂度 | 净优先级 | 启动顺序 |
|------|------|----------|--------|--------|--------|---------|---------|
| **#1** | **AI 助手** | "AI 助手是**重点**改造模块" | ★★★★★ | P0 阻塞体验 | 中 | **#1 启动** | W1 |
| **#2** | **整体模块化** | "重点是**模块化推进**" | ★★★★★ | P0 阻塞扩展 | 高 | **#2 启动** | W1 (与 AI 并行) |
| **#3** | **VPN 模块** | "VPN 是**次重要**模块" | ★★★★ | P0 阻塞闪退 | 中-高 | **#3 启动** | W2 (依赖模块化基础) |
| **#4** | **工具箱** | "工具箱**再进行模块化**操作" | ★★★★ | P1 改善扩展 | 中 | **#4 启动** | W3 |
| **#5** | **UI 主题 / 美观** | "从**功能、美观**入手" | ★★★★ | P1 改善体验 | 低-中 | **横向铺底** | W1 起持续推进 |
| #6 | 游戏大厅 (29 模块) | (隐含) | ★★★ | P2 长尾 | 中 | 持续 | M2+ 优化 |
| #7 | 模块商店 (顶层) | (隐含) | ★★★ | P1 改善 | 中 | 与 #4 并行 | W3 |

> **净优先级公式**: (重要性 + 紧迫性) / 复杂度；该模块有人用 = 重要性高；能立刻上线 = 紧迫性高；改动小 = 复杂度低 → 净优先级高。

### 1.2 设计原则 (约束所有改造)

> 所有改动必须**同时满足**以下 6 条原则；任何冲突时按"约束 > 用户体验 > 性能 > 复用"的顺序裁决。

#### 原则 1 — 严格遵守 `DONT_DO_THIS.md` (硬约束)

**14 条 DONT_DO** 全部为 0 容忍，详见各 track §"DONT_DO_THIS 边界遵守"。本计划执行时的"禁线"复述：

- ❌ 不加回 kapt (Hilt/Room 走 KSP)
- ❌ 不 commit / push (用户明确说本地)
- ❌ 不改 `keystore.properties` / `local.properties` / `release-key.jks`
- ❌ 不切到 Compose 强迁整屏 (hall 已有 Compose 样板仅在 hall 用)
- ❌ 不把 `lint abortOnError` / `detekt ignoreFailures` 改回 true
- ❌ 不给 `:app` 加 `id 'org.jetbrains.kotlin.jvm'`
- ❌ 不自动 bump `version.properties` (用 `-PautoBumpVersion=false`)
- ❌ 不假设 AI 知道机器配置 (跑前 `where java` / `where adb`)

#### 原则 2 — 模块化优先 (横向)

**新增功能必须先问**: "这能不能放进 `core:common` / `core:network` 共享？还是必须独立 dynamic APK？"

- 可复用契约 → 进 `core:common` (e.g. `ToolCapability` 接口、`FeatureModule` 接口)
- 跨进程需求 → 走 `module-store/feature/*` (e.g. AI 助手、VPN、工具箱)
- 宿主专属 → 留在 `app/` (e.g. 启动动画、错误页)

#### 原则 3 — 死代码优先清 (横向)

4 个 track 都报告了大量"占位资源"：

- track-ai: `fragment_ai.xml` / `item_ai_message*.xml` / `dialog_chat.xml` / `bg_ai_message_*.xml` ~30% 死代码
- track-platform: `core/moduleloader/*` 1800+ 行 v2 设计稿未被业务调用
- track-tools: `tools_settings.xml` 缺 KEY_ENABLED 迁移路径
- track-vpn: 占位桩实现 (4 个协议 Module)

**清理原则**: 删之前 grep 确认 0 引用；保留 6 个月 alias；R8 收紧。

#### 原则 4 — 视觉从 Material 3 token 出发 (美观横向)

- 所有新写 layout 必须 100% 用 `@dimen/spacing_*` / `@color/md_theme_light_*` 引用
- 暗色 brand 连续：暖绿 (light) → 暖绿深 (medium) → 暖绿暗 (dark)，**不再用青色**
- 错误/空态/加载 必带插画 + 重试按钮 (Material 3 组件库)
- 启动动画用 AnimatedVectorDrawable (避免引入 Lottie 增加 APK 体积)

#### 原则 5 — 单测从 0 起步 (技术债)

- 短期 (W1-W4): 新写代码**强制** 60% 覆盖率；老代码 0 容忍
- 中期: 关键路径 (VPN 连接、AI 任务路由、工具注册) 80%
- 长期: 100% 公共契约 + 60% 业务逻辑

#### 原则 6 — 验证可逆 (工程保险)

每个 Sprint 结束：

1. 跑 `assembleDebug -PautoBumpVersion=false` 出 APK → 桌面
2. 装 5 个核心 dynamic 模块 (AI/VPN/Tools/Browser/Games) → 烟囱测试
3. 留 1 周可回滚窗口 (git revert + 强制 release 锁版本)

### 1.3 价值主张 (回应"功能 + 美观"两个维度)

| 维度 | 改前用户感受 | 改后用户感受 |
|------|-------------|-------------|
| **功能 — AI 助手** | "我问问题要等 8 秒，期间以为卡死" | "我打字就能看到 AI 边想边答" |
| **功能 — VPN** | "我点节点，App 闪退" | "我点节点，3 秒内显示已连接，告诉我延迟 87ms" |
| **功能 — 工具箱** | "27 个工具堆一屏找不到" | "我搜 'base64' 直接到工具，输入输出同屏" |
| **美观 — 主题** | "我切暗色，App 变蓝变紫不像我们品牌" | "我切暗色，绿色变成深一号的暖绿，logo 也不变" |
| **美观 — 启动** | "App 启动看 0.5 秒白屏" | "我看到 logo 淡入呼吸 0.3 秒，过渡到主页" |
| **美观 — 错误** | "网络断了弹一个 Toast '网络错误'" | "网络断了看到空态插画 + '重试' 按钮" |

---

## 2. 分阶段 Roadmap

### 2.1 短期 (W1-W4, 1-2 月) — 解决 P0 阻塞，体验脱坑

> **目标**: 用户不再觉得 App "难用" / "闪退" / "丑"。

#### Sprint 1 (W1-W2): 3 个"零功能丢失"修

| 编号 | 任务 | 模块 | 来源 track | 工期 | 验收 |
|------|------|------|-----------|------|------|
| S1-01 | **AI 响应改流式 (SSE/分块读取)** + 骨架屏 + typing 动画 | AI | track-ai P0-1 | 2d | 长答案首 token < 1.5s; 打字三点可见 |
| S1-02 | **VPN 接入 sing-box libbox.aar** + 重写 4 协议 Module | VPN | track-vpn D-01/3.1.2 | 3d | `./gradlew :module-store:feature:tools:vpn:assembleDebug` 0 错 |
| S1-03 | **修 dark/light 调色盘冲突** (md_theme_* 改 md_theme_dark_*) | 平台 | track-platform ST-1 | 0.5d | 切暗色，brand 颜色连续 (绿→深绿) |
| S1-04 | **VPN 补全 AndroidManifest** + 新建 `GameVpnService.kt` + Intent 目标修正 | VPN | track-vpn P0/P-01 | 1d | 模拟器点节点不闪退，建立 tun |
| S1-05 | **AI 错误码 7 → 12** (401/429/5xx 细分 + retryAfter 字段) | AI | track-ai P0-4 | 1d | 错误 toast 按 httpStatus 分文案 |
| S1-06 | **修 NETWORK_LAYER §已知问题**: 4 处 `new OkHttpClient.Builder()` 改 `OkHttpClientProvider` | AI + Tools | NET_LAYER + track-ai P0-3 | 1d | `grep "new OkHttpClient.Builder" -r .` 命中 0 |
| S1-07 | **AI placeholder 失败重试 + 错误细化** | AI | track-ai P0-2 | 0.5d | 模拟 VPS 500，自动重试 3 次 |
| S1-08 | **SplashActivity 启动动画** (AnimatedVectorDrawable 渐入 + 品牌 logo 呼吸) | 平台 | track-platform ST-5 | 1d | 启动 < 1.5s 看到品牌 logo |
| S1-09 | **国际补漏: 13 个 `strings_game_*.xml` 复制到 values-en/** | 平台 | track-platform ST-3 | 0.5d | 英文用户看到 "Gomoku" 而非 "五子棋" |
| S1-10 | **AI 法律提示 footer** ("AI 内容仅供参考") + 设置页 "数据使用政策" | AI | track-ai P0-8 | 0.5d | 首屏底部小字可见 |

**Sprint 1 交付物**:
- `app/build/outputs/apk/debug/app-debug.apk` (桌面可装)
- `feature_ai_v101.apk` (新版本，可独立发版)
- `feature_vpn_v101.apk` (新版本，可独立发版)

**Sprint 1 风险**: VPN sing-box AAR 可能在 minSdk 26 SO 不兼容 → CI 早期发现，准备 v2rayNG fallback。

#### Sprint 2 (W3-W4): 3 个"标准化"工程

| 编号 | 任务 | 模块 | 来源 track | 工期 | 验收 |
|------|------|------|-----------|------|------|
| S2-01 | **`ToolCapability` / `ToolRegistry` / `ToolServiceLoader` 落地** (200 行 Kotlin) | Tools | track-tools §2.1.1 | 2d | `core:common` 编译过，单测 30 cases 过 |
| S2-02 | **6 个 P0 工具转 ToolCapability** (text_codec/clipboard/qr/qr_plus/color/color_plus) | Tools | track-tools §2.1.2 | 1d | LegacyAdapter 兼容，6 工具可正常用 |
| S2-03 | **`ToolsFragment.initBinders()` 改为 `toolRegistry.registerBuiltIn()`** | Tools | track-tools §2.1.2 | 0.5d | 27 个工具**全部**可见，新增工具不改这文件 |
| S2-04 | **`fragment_tools.xml` 顶部加搜索框 + 分类 Tab** | Tools | track-tools §2.1.3 | 0.5d | 搜索 'qr' 实时过滤，分类 chip 切换 |
| S2-05 | **`ToolManagementActivity` 草稿** (开/关工具) | Tools | track-tools §2.1.4 | 1d | 禁用 network_diagnosis 后，首页看不到 |
| S2-06 | **AI 死代码清理**: 删除 `fragment_ai.xml` / `item_ai_message*.xml` / `dialog_chat.xml` / `bg_ai_message_*.xml` (dynamic APK 自带) | AI | track-ai P1-9 | 0.5d | `git diff --stat` 显示 ~30 个文件删除 |
| S2-07 | **收敛 dimens token 使用**: GamesFragment + ModuleStoreActivity 高频硬编码 → `@dimen/spacing_*` | 平台 | track-platform ST-2 | 1d | `grep -r '"[0-9]\+dp"' res/layout/ \| wc -l` 下降 50% |
| S2-08 | **模块商店按钮改 token** (0xFF9E9E9E/0xFF4CAF50 → `?attr/colorSurfaceVariant/Primary`) | 平台 | track-platform ST-4 | 0.5d | 切暗色时按钮颜色跟随 |
| S2-09 | **AI 单测补**: `AiTaskRouterTest` (15 cases) + `AiApiClientTest` (MockWebServer 20 cases) + `AiTemplateManagerTest` (10 cases) | AI | track-ai P0-5 | 2d | 覆盖率 ≥ 60% |
| S2-10 | **首页 (GamesFragment) 改版**: 卡片大图 + 间距 token + 搜索栏 | 平台 | track-platform ST-6 | 1d | 暗色模式 + 大字体无截断 |
| S2-11 | **VPN 节点数据迁移到 EncryptedSharedPreferences** | VPN | track-vpn 3.2.5 | 1d | UUID 不再明文可见 |
| S2-12 | **VPN 通知栏 + 前台服务** (FOREGROUND_SERVICE_TYPE_VPN) | VPN | track-vpn P-12 | 0.5d | VPN 启用时通知栏常驻 |

**Sprint 2 交付物**:
- `core:common` 升级到含 tool 契约
- `app-debug.apk` 桌面版
- 4 个 dynamic APK 全部升级到 v101 (feature_ai / feature_vpn / feature_tools / feature_browser)

#### Sprint 1+2 同步: 暗色 / 国际化 / lint 收紧

- 暗色品牌色 token 化：所有 layout 改用 `?attr/colorPrimary` 而非硬编码 `#5B8A72`
- 英文 strings 补完 300 条
- detekt 加 rule: `NewOkHttpClient` 禁止在指定模块

### 2.2 中期 (M2-M3, 1-2 月) — 智能 + 联动 + 模块化落地

> **目标**: 用户开始觉得 App "强大" / "连贯" / "值得付费升级"。

#### M2 重点: AI 智能 + VPN 智能 + Tools 商店

| 编号 | 任务 | 模块 | 来源 track | 工期 | 验收 |
|------|------|------|-----------|------|------|
| M2-01 | **AI 多模态 (图片 OCR → 翻译/摘要端到端)** | AI | track-ai P1-1 | 5d | 上传图片 → 看到中文摘要 + 英文翻译 |
| M2-02 | **AI Markdown 渲染** (markwon 4.6.2 + Glide image + 代码高亮) | AI | track-ai P1-3 | 2d | 代码块/列表/链接正确显示 |
| M2-03 | **AI Agent/Function calling 骨架** + 4 个内置 tool (`vpn.switch_node` / `tools.run_ping` / `browser.open` / `module.install`) | AI | track-ai P1-2 | 5d | 用户说 "帮我开 VPN 到 HK"，AI 自动调 tool |
| M2-04 | **AI 状态栏配额 mini chip** (80% 警告 / 100% 强制 cooldown) | AI | track-ai P1-7 | 1d | 状态栏可见 "12/20 今日" |
| M2-05 | **VPN 智能选路** (并发测所有节点 + 24h 缓存 + WorkManager 6h 复测) | VPN | track-vpn 3.2.1 | 5d | 启动时显示所有节点延迟，自动选低延迟 |
| M2-06 | **VPN 订阅管理** (SubscriptionManager + WorkManager 每天刷新) | VPN | track-vpn 3.2.2 | 3d | 粘贴订阅链接 → 1 分钟后 10+ 节点 |
| M2-07 | **VPN Per-App 路由** (addAllowedApplication / addDisallowedApplication) | VPN | track-vpn 3.2.3 | 2d | 设置页可勾选"仅 AI 走代理" |
| M2-08 | **`core:network` 抽 `ProxyStateProvider` / `VpnStateProvider` 接口** | 平台 | track-platform MT-1 (前奏) | 2d | AI / Browser / Update 三个模块能注入 |
| M2-09 | **`modules.json` 工具级扩展** (27 条 `tools[]` 字段) | Tools | track-tools §2.2.2 | 1d | 工具商店能列出每个工具元数据 |
| M2-10 | **`ToolManagementActivity` 完整版** (开/关/搜索/分类) | Tools | track-tools §2.2.1 | 3d | 600 行 Activity 跑通 |
| M2-11 | **`ToolAction` / `ToolActionProvider` / `ToolActionConsumer` 联动接口** | Tools | track-tools §2.2.4 | 3d | 扫码 → Base64 → 剪贴板 demo |
| M2-12 | **收敛 v1/v2 双层模块框架** (v1 迁 v2 完整 ModuleLoader) | 平台 | track-platform MT-1 | 5d | `grep "import.*v1.moduleloader" -r .` = 0 |
| M2-13 | **统一模块类型**: `ModuleInterface` 唯一接口 + `IModule`/`FeatureModule` 标 `@Deprecated` | 平台 | track-platform MT-2 | 2d | `as? ModuleInterface` 单向收敛 |
| M2-14 | **模块 APK 签名校验** (`PackageManager.GET_SIGNATURES` 校验 GameMatrix.keystore 签名) | 平台 | track-platform MT-3 | 2d | 任意 keystore 签的 APK 加载被拒 |
| M2-15 | **设计系统文档 `docs/design-system.md`** (颜色/间距/圆角/阴影/字号/字重 + 组件示例) | 平台 | track-platform MT-6 | 3d | 新人按文档还原 95% 主题 |
| M2-16 | **Compose 试点扩展** (AiFragment 顶部摘要 + ModuleStoreActivity Tab 区) | 平台 | track-platform MT-7 | 3d | 试点 2 处接 `MaterialTheme.colorScheme.*` |
| M2-17 | **模块商店空态/错误态插画** (`ic_empty_modules.xml` / `ic_error_network.xml`) | 平台 | track-platform MT-8 | 1d | 离线下拉显示"重试"按钮 |

**M2 交付物**:
- 4 个 dynamic APK 升级到 v102
- `core:network` / `core:common` 升级 (含 ProxyStateProvider + ToolCapability + ToolAction)
- `docs/design-system.md` (设计系统 v1.0)

#### M3 重点: 联动 + 灰度 + 安全

| 编号 | 任务 | 模块 | 来源 track | 工期 | 验收 |
|------|------|------|-----------|------|------|
| M3-01 | **AI 会话管理** (列表/重命名/删除/导出 md/txt/json) | AI | track-ai P1-4 | 5d | 会话抽屉可管理 100+ 会话 |
| M3-02 | **AI 模板外置** (JSON 化 + 远程拉取 + 本地兜底) | AI | track-ai P1-8 | 3d | 修改模板不需发版 |
| M3-03 | **AI 死代码清理** (host 内 host ~30% 死资源) | AI | track-ai P1-9 (延伸) | 1d | `apkanalyzer` 验证 dynamic APK 自带 |
| M3-04 | **AI 代理策略** (mimo 域名硬编码直连，其他走 VPN) | AI | track-ai P1-5 | 2d | mimo 响应延迟 < 1s (vs 经 VPN 3s+) |
| M3-05 | **VPN 模块化真正落地** (独立 keystore + Auto-Publish `modules` 子命令) | VPN | track-vpn 3.2.4 | 5d | 主 APK 移除 vpn 的 compileOnly 依赖 |
| M3-06 | **VPN 灰度发布** (`modules.json` 加 `channel` 字段 stable/beta/canary) | 平台 | track-platform MT-4 | 2d | 10% 用户先用上 VPN 新协议 |
| M3-07 | **宿主 APK 瘦身** (开启 minifyEnabled true + splits.abi) | 平台 | track-platform MT-5 | 2d | APK 体积 15MB → 8MB |
| M3-08 | **LeakCanary 接入** + Monkey 1h 跑 `ModuleLoader.unloadModule` 内存基线 | 平台 | track-platform MT-9 | 2d | 已知 OOM 路径有报警 |
| M3-09 | **离线智能路由**: 离线模式下，AI/Browser/Update 走直连 fallback | 平台 | NET_LAYER + track-ai P1-5 | 2d | 离线时 AI 仍能用本地 Gemma |
| M3-10 | **国际化扩到 5 语言** (zh/en/ja/ko/vi) | 平台 | (隐含) | 5d | 日文/韩文/越南文可用 |
| M3-11 | **AI 单测覆盖 80%** (从 60% 提升) | AI | track-ai P0-5 延伸 | 3d | `./gradlew :feature_ai:test --coverage` ≥ 80% |
| M3-12 | **VPN 单测覆盖 80%** (ShareLinkParser / Repository / State 转换) | VPN | track-vpn 短期 TODO | 3d | `ShareLinkParserTest` 50 cases |

### 2.3 长期 (M4-M6, 3-6 月) — 全模块化 + 智能 + 商业化

> **目标**: 用户开始觉得 App "是个生态" / "天天想用"。

#### M4: 第三方 + 商业化

| 编号 | 任务 | 模块 | 来源 track | 工期 | 验收 |
|------|------|------|-----------|------|------|
| M4-01 | **VLESS Reality 接入** (sing-box Reality outbound + uTLS 指纹) | VPN | track-vpn 3.3.1 | 10d | 国内用户可稳定连 |
| M4-02 | **Hysteria2 接入** (QUIC 拥塞 Brutal) | VPN | track-vpn 3.3.2 | 5d | 高丢包网络 (非洲/南美) 体验好 |
| M4-03 | **自建中转** (chain detour UI) | VPN | track-vpn 3.3.3 | 5d | "HK 中转 → US 落地" 可视配置 |
| M4-04 | **AI RAG (本机文档检索)** (MediaPipe Text Embedder + SQLite-VSS) | AI | track-ai P2-5 | 10d | "我手机里合同提到几号付款" AI 能答 |
| M4-05 | **AI 个性化 (persona dropdown)** | AI | track-ai P2-3 | 3d | "我是程序员/学生/教师" 切 system prompt |
| M4-06 | **第三方工具支持** (manifest 校验 + 沙箱 + 灰度) | Tools | track-tools §2.3.1 | 10d | 第三方开发者可提交工具 |
| M4-07 | **工具评分评论** (Giscus + 匿名上报) | Tools | track-tools §2.3.2 | 5d | 工具详情页有评分 |
| M4-08 | **AI 多 Agent 协同** (`AiPipeline` JSON DSL) | AI | track-ai P2-2 | 5d | "OCR → 翻译 → 改写" 自动串 |
| M4-09 | **AI 跨设备同步** (端到端加密 + 用户隐私评估) | AI | track-ai P2-4 | 10d | A 手机聊一半，B 手机能继续 |
| M4-10 | **全模块化** (33 个模块入口全迁到 `module-store/feature/*`) | 平台 | track-platform LT-1 | 10d | 宿主 APK ≤ 5MB |

#### M5: 体验升级

| 编号 | 任务 | 模块 | 来源 track | 工期 |
|------|------|------|-----------|------|
| M5-01 | **AI 智能体 (Autopilot)** (跨应用 Action + AccessibilityService + 审计日志) | AI | track-ai P2-6 | 15d |
| M5-02 | **离线模型市场** (Qwen/Phi/Llama 一键切换) | AI | track-ai P2-7 | 10d |
| M5-03 | **A/B 框架** (prompt/model 灰度) | AI | track-ai P2-8 | 5d |
| M5-04 | **Material You 动态取色** (Android 12+ 壁纸取色) | 平台 | track-platform LT-3 | 5d |
| M5-05 | **跨模块主题共享** (token 抽 `core:common` 资源) | 平台 | track-platform LT-2 | 5d |
| M5-06 | **自适应 UI** (WindowSizeClass 4 类布局) | 平台 | track-platform LT-4 | 10d |
| M5-07 | **工作流编辑器 (拖拽)** | Tools | track-tools §2.3.3 | 15d |
| M5-08 | **节点池 AI 选路** (30 天数据 + 线性回归) | VPN | track-vpn 3.3.4 | 5d |

#### M6: 收尾 + 加固

| 编号 | 任务 | 模块 | 来源 track | 工期 |
|------|------|------|-----------|------|
| M6-01 | **Baseline Profile 完善** (ModuleLoader.loadModule 关键路径) | 平台 | track-platform LT-5 | 3d |
| M6-02 | **APK Signature Scheme v3.1** (增量签名 + 密钥轮转) | 平台 | track-platform LT-6 | 5d |
| M6-03 | **AI 审计/可观测性** (全量 log 加密 + 用户可"举报") | AI | track-ai P2-10 | 5d |
| M6-04 | **从 dynamic APK 升级为独立 App 评估** (Phase 1 稳了再拆) | AI | track-ai P2-9 | 3d |
| M6-05 | **Performance Profiling** (Systrace + Macrobenchmark) | 平台 | (隐含) | 3d |
| M6-06 | **跨平台规划** (KMP/HarmonyOS NEXT 评估) | 平台 | (隐含) | 5d |

### 2.4 阶段对比 (改前 → 改后)

| 维度 | 改前 (W0) | 短期后 (W4) | 中期后 (M3) | 长期后 (M6) |
|------|----------|------------|-----------|------------|
| **AI 响应** | 5-20s 一次性 | < 1.5s 流式 | < 1.5s 流式 + 多模态 | < 1.5s + Agent + RAG |
| **VPN 连通** | 闪退 (Intent 目标类不存在) | sing-box 接入 + 真 tun | 智能选路 + 订阅 | Reality + Hysteria2 + AI 选路 |
| **工具发现** | 27 卡片堆一屏 | 搜索 + 分类 Tab | 工具商店 | 工作流编辑器 |
| **暗色品牌** | 绿→青→紫跳变 | 暖绿连续 | + Material You | + 自适应 UI |
| **模块化粒度** | 10 dynamic APK + 双层框架 | 标准化 ToolCapability | v1/v2 收敛 + 签名校验 | 全模块化 ≤ 5MB 宿主 |
| **单测覆盖** | 0% (几乎) | 60% 新代码 | 80% 关键路径 | 95% 公共契约 |
| **APK 体积** | ~15MB | ~15MB | ~8MB | ~5MB |
| **英文可用性** | ~70% | 90% | 100% (5 语言) | 100% |

---

## 3. 执行路径 (回应用户执行约束)

> 用户明确指定:
> 1. **本地优先** (不出网 / 不 push)
> 2. **缺依赖 fallback** (sing-box 没下到 → 用 v2rayNG; markwon 没下到 → 用纯文本)
> 3. **APK 放桌面** (避免覆盖原 release 包)

### 3.1 本地优先执行原则

#### 3.1.1 本地工具栈确认 (Sprint 1 Day 0)

**必跑命令** (写到 `docs/LOCAL_DEV_CHECK.md`):

```bash
# PowerShell 7+ (Windows)
where java       # 应输出 C:\Users\Administrator\.jdks\ms-17.0.19\bin\java.exe
where adb        # 应输出 C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe
$env:ANDROID_HOME = "C:\Users\Administrator\AppData\Local\Android\Sdk"
[System.Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Users\Administrator\AppData\Local\Android\Sdk", "User")
where gradle     # 应在 PATH 包含 Gradle 8.x (项目 gradle/wrapper 优先)
git --version    # 2.30+
```

**JDK 必须用 ms-17.0.19** (项目经验值；换版本 AGP 会报 `Unsupported class file major version`)

**SDK 必须用 Android 35** (compileSdk/targetSdk 35)

#### 3.1.2 本地命令清单 (每个 Sprint 末跑)

```bash
# 主 APK 编译 (关掉自动 bump)
$env:JAVA_HOME = "C:\Users\Administrator\.jdks\ms-17.0.19"
cd Y:\GameMatrixApp
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false
# 输出: app\build\outputs\apk\debug\app-debug.apk → 复制到桌面

# 各 dynamic APK 独立编译
.\gradlew.bat :module-store:feature:tools:ai:assembleDebug      # → 桌面 feature_ai_v101.apk
.\gradlew.bat :module-store:feature:tools:vpn:assembleDebug     # → 桌面 feature_vpn_v101.apk
.\gradlew.bat :module-store:feature:tools:tools:assembleDebug   # → 桌面 feature_tools_v101.apk

# Lint + Detekt (增量)
.\gradlew.bat :app:lintDebug          # 维持 abortOnError=false
.\gradlew.bat :core:common:detekt     # 维持 ignoreFailures=true

# 单测
.\gradlew.bat :module-store:feature:tools:ai:testDebug          # 覆盖率报告
.\gradlew.bat :module-store:feature:tools:vpn:testDebug
```

#### 3.1.3 本地 → 模拟器 → 桌面 部署流

```text
[本地编译]
  .\gradlew.bat :app:assembleDebug
       │
       ├─ OK ──→ app\build\outputs\apk\debug\app-debug.apk
       │                  │
       │                  ▼
       │       Copy-Item -Path ... -Destination C:\Users\35719\Desktop\app-debug-v1.4.0-rc1.apk
       │                  │
       │                  ▼
       │       adb install -r C:\Users\35719\Desktop\app-debug-v1.4.0-rc1.apk
       │                  │
       │                  ▼
       │       烟囱测试: 5 个核心模块 (AI/VPN/Tools/Browser/Games) 各点 1 次
       │                  │
       │                  ▼
       │       OK ──→ git commit (本地, 不 push)
       │
       └─ FAIL ──→ 看 error, 改代码, 重跑
```

**桌面 APK 命名规范** (避免覆盖):

```
C:\Users\35719\Desktop\
├── app-debug-v1.4.0-rc1-2026-06-04.apk           (主 APK 桌面版)
├── app-debug-v1.4.0-rc1-2026-06-04.md5           (校验)
├── feature_ai-v101-2026-06-04.apk
├── feature_vpn-v101-2026-06-04.apk
├── feature_tools-v101-2026-06-04.apk
├── feature_browser-v101-2026-06-04.apk
└── feature_games_hall-v101-2026-06-04.apk
```

**桌面清理脚本** (每周日跑):

```powershell
# 保留最近 3 个版本
Get-ChildItem C:\Users\35719\Desktop\app-debug-*.apk |
  Sort-Object LastWriteTime -Descending |
  Select-Object -Skip 3 |
  Move-Item -Destination C:\Users\35719\Desktop\apk-archive\
```

### 3.2 缺依赖 fallback 矩阵

> 用户的本地环境**不一定能访问 maven central / Google maven / GitHub Releases**。每个外部依赖必须设计 fallback 路径。

| 依赖 | 主源 | Fallback #1 | Fallback #2 | 检测方法 |
|------|------|------------|------------|---------|
| **sing-box libbox.aar** | `https://github.com/SagerNet/sing-box/releases` | v2rayNG 源码编译 | 内置 4 协议桩 + TODO | `./gradlew :module-store:feature:tools:vpn:dependencies` 看是否有 libbox |
| **markwon 4.6.2** | maven central | 自写 `MarkdownRenderer.kt` (200 行纯 Kotlin) | 纯文本回退 | `assembleDebug` 编译错就 fallback |
| **MediaPipe tasks-genai 0.10.27** | Google Maven | 本地 jar (已存在 `app/libs/`) | 关闭本地模式 (只用云端) | `app/build.gradle:339` 验证 |
| **androidx.security:security-crypto 1.1.0-alpha06** | Google Maven | Tink 替代 | 明文 SP (P2 修复) | `dependencies` 树 |
| **tool-ai 三方工具 manifest** | `assets/modules.json` | 内置硬编码 | empty list | `apkanalyzer` 看 |
| **AppCompat / Material 1.12.0** | Google Maven | 旧版本 1.6.0 (项目 baseline) | 缺则阻塞 | `app/build.gradle` 验证 |
| **OkHttpClientProvider** | `core:network` (项目内) | 旧 `OkHttpClient.Builder()` | 阻塞 (硬约束) | `app/.../network/` 验证 |
| **JUnit / MockWebServer / Robolectric** | maven central | 自写 main-only 测试 | 阻塞 | `testImplementation` 验证 |
| **AndroidX Compose** | Google Maven | 不引 (不强迁) | 阻塞 (试点需要) | `app/build.gradle` 验证 |
| **Hilt / KSP** | maven central + Gradle plugin portal | 旧版本 2.48 (项目经验) | 阻塞 | `app/build.gradle` 验证 |

#### 3.2.1 Fallback 决策树 (伪代码)

```python
def can_build(target: str) -> bool:
    if not can_reach("https://repo.maven.apache.org"):
        log.error("[BUILD] Maven central 不可达, 切 offline 模式")
        return try_offline_cache(target)  # ~/.gradle/caches/

    if target == "feature_vpn":
        if has_libbox_aar():
            return "sing-box"  # 主路径
        elif has_v2rayng_source():
            return "v2rayNG"   # Fallback #1
        else:
            return "stub+todo" # Fallback #2 (P0 仍阻塞, 但不报 build 错)

    if target == "feature_ai":
        if has_markwon_jar():
            return "markwon"  # 主路径
        else:
            return "plaintext" # Fallback (markdown 渲染用纯文本)

    if target == "feature_tools":
        if has_security_crypto_jar():
            return "encrypted_sp"  # 主路径
        else:
            return "plain_sp"      # Fallback (P2 修复)

    return "ok"
```

#### 3.2.2 离线模式命令

```bash
# 第一次必须联网下载依赖
.\gradlew.bat :app:dependencies --refresh-dependencies

# 之后可切离线
.\gradlew.bat :app:assembleDebug --offline -PautoBumpVersion=false
```

### 3.3 模块级 Gradle 命令 (供实施者复制粘贴)

#### 3.3.1 AI 模块 (feature_ai)

```bash
# 编译 dynamic APK
$env:JAVA_HOME = "C:\Users\Administrator\.jdks\ms-17.0.19"
cd Y:\GameMatrixApp
.\gradlew.bat :module-store:feature:tools:ai:assembleDebug

# 跑单测
.\gradlew.bat :module-store:feature:tools:ai:testDebug

# 覆盖率
.\gradlew.bat :module-store:feature:tools:ai:jacocoTestReport
# 报告: module-store/feature/tools/ai/build/reports/jacoco/test/html/index.html
```

#### 3.3.2 VPN 模块 (feature_vpn)

```bash
.\gradlew.bat :module-store:feature:tools:vpn:assembleDebug
.\gradlew.bat :module-store:feature:tools:vpn:testDebug

# sing-box AAR 单独下载 (fallback 准备)
$ProgressPreference = 'SilentlyContinue'
Invoke-WebRequest -Uri "https://github.com/SagerNet/sing-box/releases/download/v1.8.0/libbox.aar" `
    -OutFile "Y:\GameMatrixApp\module-store\feature\tools\vpn\libs\libbox.aar"
```

#### 3.3.3 工具箱 (feature_tools)

```bash
.\gradlew.bat :module-store:feature:tools:tools:assembleDebug
.\gradlew.bat :module-store:feature:tools:tools:testDebug
```

#### 3.3.4 主 APK

```bash
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest
```

#### 3.3.5 core 模块 (共享契约)

```bash
# 改 core:common / core:network 后必须先编, 否则 feature 模块报错
.\gradlew.bat :core:common:assembleDebug
.\gradlew.bat :core:network:assembleDebug
.\gradlew.bat :core:module-host:assembleDebug
```

### 3.4 桌面 APK 集中管理 (脚本)

```powershell
# C:\Users\35719\Desktop\build-apk.ps1
# 用法: .\build-apk.ps1 -Sprint "Sprint 1" -Date "2026-06-04"

param(
    [Parameter(Mandatory)][string]$Sprint,
    [Parameter(Mandatory)][string]$Date
)

$ErrorActionPreference = "Stop"
$env:JAVA_HOME = "C:\Users\Administrator\.jdks\ms-17.0.19"
$Desktop = [Environment]::GetFolderPath("Desktop")

# 1. 编主 APK
Set-Location Y:\GameMatrixApp
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false

# 2. 编 4 个核心 dynamic APK
.\gradlew.bat :module-store:feature:tools:ai:assembleDebug
.\gradlew.bat :module-store:feature:tools:vpn:assembleDebug
.\gradlew.bat :module-store:feature:tools:tools:assembleDebug
.\gradlew.bat :module-store:feature:tools:browser:assembleDebug

# 3. 复制到桌面
$Targets = @{
    "app\build\outputs\apk\debug\app-debug.apk"                       = "app-debug-$Sprint-$Date.apk"
    "module-store\feature\tools\ai\build\outputs\apk\debug\ai-debug.apk"           = "feature_ai-$Sprint-$Date.apk"
    "module-store\feature\tools\vpn\build\outputs\apk\debug\vpn-debug.apk"         = "feature_vpn-$Sprint-$Date.apk"
    "module-store\feature\tools\tools\build\outputs\apk\debug\tools-debug.apk"     = "feature_tools-$Sprint-$Date.apk"
    "module-store\feature\tools\browser\build\outputs\apk\debug\browser-debug.apk" = "feature_browser-$Sprint-$Date.apk"
}

foreach ($src in $Targets.Keys) {
    if (Test-Path $src) {
        $dst = Join-Path $Desktop $Targets[$src]
        Copy-Item -Path $src -Destination $dst -Force
        Write-Host "[OK] $($Targets[$src]) ($([math]::Round((Get-Item $dst).Length/1KB, 1)) KB)"
    } else {
        Write-Warning "[MISS] $src (模块未编出, 跳过)"
    }
}

# 4. 算 md5
foreach ($name in $Targets.Values) {
    $path = Join-Path $Desktop $name
    if (Test-Path $path) {
        $hash = (Get-FileHash $path -Algorithm MD5).Hash
        "$hash  $name" | Out-File (Join-Path $Desktop "$name.md5") -Encoding ASCII
    }
}

Write-Host "`n[完成] 桌面 APK 已就位: $Desktop"
Get-ChildItem $Desktop -Filter "*-$Sprint-$Date.apk" | Format-Table Name, Length, LastWriteTime
```

### 3.5 不出网 (本地优先) 的额外约束

- ❌ 不调用 LLM API (除 BuildConfig.MIMO_API_KEY 已配置的, 且不走 VPN)
- ❌ 不上 Sentry / Crashlytics / Firebase
- ❌ 不调 Google Play 更新
- ✅ 跑 `fetchRemoteModulesInternal` 时显式跳过, 走 `assets/modules.json` 兜底
- ✅ 启动画面 `SplashActivity` 不发任何网络请求
- ✅ 错误 toast 不上报远程

---

## 4. 资源评估

### 4.1 人力预算

| 阶段 | 单人全栈估算 | 拆 2 人并行 | 拆 3 人并行 |
|------|------------|------------|------------|
| Sprint 1 (W1-W2) | 12 人天 | 6 人天 | 4 人天 |
| Sprint 2 (W3-W4) | 12 人天 | 6 人天 | 4 人天 |
| M2 | 50 人天 | 25 人天 | 17 人天 |
| M3 | 40 人天 | 20 人天 | 14 人天 |
| M4 | 75 人天 | 38 人天 | 25 人天 |
| M5 | 73 人天 | 37 人天 | 25 人天 |
| M6 | 24 人天 | 12 人天 | 8 人天 |
| **总计** | **286 人天** | **144 人天** | **97 人天** |

> 6 个月 ≈ 130 工作日；单人 286 天不够；拆 2 人 (144 天) 紧；拆 3 人 (97 天) **可达成**。

### 4.2 技术资源 / 依赖清单

#### 4.2.1 必须新引入的依赖

| 依赖 | 版本 | 来源 | 用途 | Fallback |
|------|------|------|------|---------|
| `io.nekohakekai:libbox` | 1.8.0 | github.com/SagerNet/sing-box | VPN 内核 | v2rayNG 源码 |
| `io.noties.markwon:core` | 4.6.2 | maven central | AI Markdown 渲染 | 自写纯 Kotlin 渲染 |
| `io.noties.markwon:image-glide` | 4.6.2 | maven central | AI Markdown 图片 | 纯文本 |
| `io.noties.markwon:syntax-highlight` | 4.6.2 | maven central | AI 代码块高亮 | 不高亮 |
| `androidx.security:security-crypto` | 1.1.0-alpha06 | Google maven | VPN 节点加密 | Tink / 明文 (P2) |
| `com.google.mlkit:text-recognition` | 16.0.0 | Google maven | AI OCR | 系统相机 (P2 修复) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.7.3 | maven central | (项目已有) | - |
| `androidx.work:work-runtime-ktx` | 2.9.0 | Google maven | 智能选路后台任务 | 阻塞 (已有) |
| `androidx.benchmark:benchmark-macro-junit4` | 1.2.3 | Google maven | 性能 baseline | 跳过 |

#### 4.2.2 项目已有依赖 (复用)

| 依赖 | 项目位置 | 用途 |
|------|---------|------|
| `com.google.mediapipe:tasks-genai` | `app/build.gradle:339` | AI 本地 Gemma |
| `com.squareup.okhttp3:okhttp` | `app/build.gradle` | 网络 (统一 provider) |
| `com.google.dagger:hilt-android` | `app/build.gradle` | DI (走 KSP) |
| `androidx.room:room-runtime` | `app/build.gradle` | DB (走 KSP) |
| `com.google.android.material:material` | `app/build.gradle` | Material 3 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | `app/build.gradle` | ViewModel |
| `androidx.fragment:fragment-ktx` | `app/build.gradle` | Fragment |
| `com.google.code.gson:gson` | `app/build.gradle` | JSON |

### 4.3 硬件 / 基础设施

| 资源 | 现状 | 是否足够 |
|------|------|---------|
| 本地 JDK 17 (ms-17.0.19) | 已装 | ✅ |
| Android SDK 35 | 已装 | ✅ |
| Gradle 8.x | 项目 wrapper | ✅ |
| Git | 已装 | ✅ |
| 模拟器 (Pixel 5 API 34) | 已装 | ✅ |
| 桌面空间 (C:\Users\35719\Desktop) | 1GB+ | ✅ |
| 内存 (跑 Android Studio) | 16GB+ | ✅ |
| 编译缓存 (~/.gradle/caches) | 5GB+ | ✅ |
| **网络 (访问 maven central / Google maven)** | **不确定** | ⚠️ 见 §3.2 fallback |

### 4.4 文档产出预算

| 文档 | 阶段 | 来源 |
|------|------|------|
| `docs/refactor/track-ai.md` | 调研 | 已产出 (35KB) |
| `docs/refactor/track-vpn.md` | 调研 | 已产出 (18KB) |
| `docs/refactor/track-tools.md` | 调研 | 已产出 (38KB) |
| `docs/refactor/track-platform.md` | 调研 | 已产出 (35KB) |
| `docs/refactor/MASTER-REFACTOR-PLAN.md` | 汇总 | **本文档** |
| `docs/design-system.md` | M2 | track-platform MT-6 |
| `docs/feature-ai-contract.md` | M1 | track-ai §4.2.1 |
| `docs/feature-vpn-contract.md` | M1 | track-vpn 隐含 |
| `docs/feature-tools-contract.md` | M1 | track-tools §2.1.1 |
| `docs/LOCAL_DEV_CHECK.md` | Sprint 1 Day 0 | 本计划 §3.1.1 |
| `CHANGELOG.md` | 每 Sprint | 用户主笔 |

---

## 5. 风险点

### 5.1 技术风险 (按概率 × 影响排序)

| # | 风险 | 概率 | 影响 | 缓解 |
|---|------|------|------|------|
| **R-01** | sing-box AAR 在 minSdk 26 SO 不兼容 | 中 | 高 | Sprint 1 Day 1 早编译, 准备 v2rayNG fallback, 24h 内决策 |
| **R-02** | AI 流式输出 (SSE) 引入状态机 bug | 中 | 中 | TaskStatus sealed class + 单测 ≥ 30 case 覆盖异常路径 |
| **R-03** | markwon / Glide 引入大依赖, APK 体积 +5-7MB | 中 | 中 | 拆 variant: 基础包 1MB / 全功能包 8MB |
| **R-04** | Agent/Function calling 引入"越权"风险 | 高 | 高 | 严格白名单 + 用户确认弹窗 + 全量审计日志 + 限速 (10 calls/min) |
| **R-05** | 本地模型首次下载 800MB-1.5GB 耗电/流量 | 中 | 中 | Wi-Fi only + 显式触发 + 断点续传 + 进度条 + "小模型优先" 选项 |
| **R-06** | AI 跨模块调用 VPN/Tools 触发 classloader 隔离 | 中 | 中 | `ToolRegistry` + IPC (Binder/Messenger) + 显式契约 |
| **R-07** | AI 输出合规 (政治/版权/医疗建议) | 中 | P0 | 已有 disclaimer; 加 content filter + 命中降级 + 日志 + 设置页开关 |
| **R-08** | 暗色品牌色 token 化引发旧代码硬编码颜色显示错位 | 中 | 局部 UI 跳变 | 保留原 `md_theme_*` 别名 `@color/md_theme_legacy` 6 个月, 渐迁 |
| **R-09** | v1/v2 模块框架合并破坏模块加载 | 中 | 全模块加载失败 | 分 PR (先 Verifier 后 Loader); 装模拟器跑 5 个核心模块烟囱测试 |
| **R-10** | 模块 APK 签名校验拒签新模块 | 高 | 用户装模块失败 | `ModuleVerifier` 加 `signingKeyAllowList` 灰度开关, 默认开启允许回退 |
| **R-11** | EncryptedSharedPreferences Keystore 偶发失败 | 低 | 中 | 启动时检测, 失败回退明文 |
| **R-12** | VPN 前台服务被杀 (低内存) | 中 | 中 | startForeground + FOREGROUND_SERVICE_TYPE_VPN + START_STICKY |
| **R-13** | ToolBinder 27 个一次性迁移崩 | 高 | 中 | 短期只迁 6 个 P0, 跑通 `LegacyAdapter` 兼容路径 |
| **R-14** | META-INF/services 被 R8/AGP 处理掉 | 低 | 高 | 短期 6 个内置工具走 `registerBuiltIn()` 显式注册, 不依赖 ServiceLoader |
| **R-15** | 模块商店 UI 与现有 ModuleStoreActivity 重复 | 中 | 中 | 复用 ModuleStoreActivity 框架 (storeCategory=tools), 加新 Tab |
| **R-16** | dynamic APK 资源无法跨包访问 | 高 | 中 | 短期不拆包回避; 中期如需拆, 工具自包含 R 类 (同名 R 不冲突) |
| **R-17** | Material You 动态取色破坏品牌 | 中 | 启动后第一眼变蓝/红/绿 | 设置页加"使用品牌色"开关, 默认开 |
| **R-18** | Compose 试点与 View 主题色不一致 | 中 | 试点页面对比鲜明 | 用 `MaterialTheme(colorScheme = dynamicColorScheme(LocalContext.current))` 包装试点 |

### 5.2 产品 / 用户风险

| # | 风险 | 概率 | 影响 | 缓解 |
|---|------|------|------|------|
| **R-19** | 用户对 dynamic APK 下载耐心低 | 中 | P0 门槛 | 引导页 + 预下载 (启动后空闲时拉) + 安装后静默启用 |
| **R-20** | 本地模型质量低于云端 | 高 | P1 | UI 显式标"本地: 速度↑ 质量↓"对比; 默认云端; 提供 A/B 入口 |
| **R-21** | 模板发现性差 → 用户走了 5 次还不知道有模板 | 中 | P1 | 首屏"试一试" carousel + 空状态展示 3 个热门模板 + 模板作者署名 |
| **R-22** | VPN Reality 服务器配置错误 | 高 | 中 | 测试节点先跑通, 准备 v2rayNG fallback, 提供"诊断"入口 |
| **R-23** | 用户不会用"工具间联动" (新概念) | 中 | P2 | 工具详情页"可联动"区有示例 + 一键试用按钮 |

### 5.3 业务 / 商业风险

| # | 风险 | 概率 | 影响 | 缓解 |
|---|------|------|------|------|
| **R-24** | MiMo API 限速/涨价/停服 | 中 | P2 | 抽象 `CloudProvider` 接口, 一键切 OpenAI/Claude/Gemini; key 走 BuildConfig 注入 |
| **R-25** | 大模型输出错误导致用户受损 | 中 | P1 (法律) | UI 永久加 "AI 内容仅供参考" + 设置页"风险提示" + 关键场景 (代码/医疗) 加 warning badge |
| **R-26** | 海外用户跨境延迟/合规 | 低 | P2 | 海外节点分流 (复用 VPN 能力), GDPR/CCPA 风格隐私声明 |
| **R-27** | 第三方工具提交带来恶意代码 | 中 | P1 | manifest 校验 + 沙箱化 + 灰度 10% + 关键词过滤 + 评分评论 |

### 5.4 工程风险 (本改造直接相关)

| # | 风险 | 概率 | 影响 | 缓解 |
|---|------|------|------|------|
| **R-28** | dynamic APK 内代码不可见, 改动需"盲改 + 验签 sha256" | 高 | P0 | 中期: 把 feature_ai 源纳入 Git 仓 (monorepo), 停止"盲改" |
| **R-29** | 单测无法覆盖 feature 内代码 | 高 | P0 | 短期: 写 host 侧 mock feature 的 instrumentation test |
| **R-30** | 占位资源清理导致 dynamic APK 缺资源 | 低 | P1 | 清理前 grep dynamic APK 反编译确认未引用; apkanalyzer 验证 |
| **R-31** | 模块框架 v1/v2 双层并存, 改动选错版本 | 中 | 中 | 短期定调: v2 收敛, v1 标记 @Deprecated; 写 CONTRIBUTING.md 强制 |
| **R-32** | 桌面 APK 命名混乱 | 低 | 低 | §3.1.3 命名规范, 每周日自动清理 |
| **R-33** | 缺依赖时 build 失败 | 中 | 阻塞 | §3.2 fallback 矩阵 + offline cache |
| **R-34** | Sprint 1 范围超 12 人天 | 高 | 时延 | 砍掉 P1-1 (AI 法律提示) / P0-7 (历史多维过滤) 等可选项 |

### 5.5 DONT_DO_THIS 触发风险 (硬约束相关)

| # | 触发 | 后果 | 预防 |
|---|------|------|------|
| **R-35** | 不小心加回 kapt | R8/KSP 冲突, 全项目编译挂 | detekt 加 rule + CI grep `id 'org.jetbrains.kotlin.kapt'` |
| **R-36** | 不小心 commit / push | 用户生气, 协作流程破坏 | 写脚本 `git hook pre-commit` 检查 `git remote -v`, 远程 push 需手动 `git push --force-with-lease` |
| **R-37** | 不小心改 keystore.properties | 后续 APK 行为变, 部署挂 | 标记 `keystore.properties` 为只读, CI 校验 SHA-256 |
| **R-38** | 不小心 bump versionCode | 跟线上 release 冲突 | 永远用 `-PautoBumpVersion=false` |
| **R-39** | 不小心切到 Compose 强迁整屏 | 业务 Activity 全挂 | 每个 PR 描述必须包含 "本次改动是否动 View 系统 Activity?" |
| **R-40** | 不小心改 lint abortOnError | 老代码立刻挂 | CI 校验 `app/build.gradle` 这个值 |

---

## 6. 里程碑 (Milestone)

> 每个 Milestone 都是**可交付 + 可验证 + 可回滚**的状态。

### M0 (D-0) — 调研完成, 计划锁定

**日期**: 2026-06-04 (已完成)
**交付物**:
- ✅ `docs/refactor/track-ai.md` (35KB)
- ✅ `docs/refactor/track-vpn.md` (18KB)
- ✅ `docs/refactor/track-tools.md` (38KB)
- ✅ `docs/refactor/track-platform.md` (35KB)
- ✅ `docs/refactor/MASTER-REFACTOR-PLAN.md` (本文档)
- ✅ 4 个 track 的 deliverable.md
- ✅ 进度板 (board.md) 持续更新

**验收**: 4 个 track 报告 + 1 个 master 计划 + 6 个 deliverable.md 全部就位

**风险**: 无 (只产文档)

### M1 (W2 末) — Sprint 1 完成, 3 个 P0 修

**日期**: 2026-06-18 (预计)
**交付物**:
- 桌面 `app-debug-v1.4.0-rc1-2026-06-18.apk` (主 APK)
- 桌面 `feature_ai-v101-2026-06-18.apk` (流式输出 + 错误细化)
- 桌面 `feature_vpn-v101-2026-06-18.apk` (sing-box 接入 + GameVpnService)
- 桌面 `feature_tools-v101-2026-06-18.apk` (无功能变化, 跟随版本号)
- 桌面 `feature_browser-v101-2026-06-18.apk` (跟随版本号)
- 暗色调色盘冲突修复 (绿→深绿连续)
- 启动动画 (SplashActivity logo 淡入)
- NETWORK_LAYER §已知问题 4 处违规源 0
- 13 个 strings_game_*.xml 复制到 values-en/

**验收**:
- [ ] AI 助手发问题 1.5s 内看到首 token
- [ ] VPN 点节点不闪退, 3 秒内显示已连接
- [ ] 切暗色, 品牌色连续 (绿→深绿)
- [ ] `grep "new OkHttpClient.Builder" -r .` 命中 0
- [ ] 英文用户看到 "Gomoku" 而非 "五子棋"
- [ ] 5 个核心模块装上模拟器烟囱测试全通

**风险**: R-01 (sing-box 不兼容) / R-28 (盲改) / R-34 (超时)

### M2 (W4 末) — Sprint 2 完成, 标准化工程

**日期**: 2026-07-02 (预计)
**交付物**:
- `core:common` 升级: ToolCapability / ToolRegistry / ToolServiceLoader / ToolAction 接口
- 桌面 `app-debug-v1.4.0-rc2-2026-07-02.apk`
- 6 个 P0 工具转 ToolCapability (text_codec / clipboard / qr / qr_plus / color / color_plus)
- `ToolsFragment.initBinders()` 重构
- `fragment_tools.xml` 加搜索框 + 分类 Tab
- `ToolManagementActivity` 草稿
- AI 死代码清理 (host 内 ~30% AI 资源)
- dimens token 化 (50% 硬编码 → token)
- AI 单测覆盖 60% (45 cases)

**验收**:
- [ ] 新增工具**不**改 `ToolsFragment.java`, 只加 `META-INF/services` 文件
- [ ] 搜索 'qr' 实时过滤 27 个工具
- [ ] 禁用 network_diagnosis 后, 首页看不到
- [ ] dimens 硬编码 grep 命中数下降 50%
- [ ] AI 单测 60% 覆盖, `./gradlew testDebug` 0 失败
- [ ] 6 个 dynamic APK + 1 个主 APK 桌面装包全绿

**风险**: R-13 (27 binder 一次性迁移崩) / R-14 (META-INF 被 R8 干掉) / R-08 (调色盘跳变)

### M3 (M2 末) — 智能 + 联动 + 模块化落地

**日期**: 2026-08 月底 (预计)
**交付物**:
- 桌面 `app-debug-v1.5.0-2026-08-XX.apk`
- 4 个 dynamic APK v102
- AI 多模态 (图片 OCR → 翻译/摘要)
- AI Markdown 渲染 (markwon)
- AI Function calling 骨架 (4 个内置 tool)
- AI 状态栏配额 chip
- VPN 智能选路 (24h 缓存 + WorkManager 6h 复测)
- VPN 订阅管理
- VPN Per-App 路由
- `core:network` 抽 ProxyStateProvider / VpnStateProvider
- `modules.json` 工具级扩展
- `ToolManagementActivity` 完整版
- 工具联动 (扫码→Base64→剪贴板 demo)
- 收敛 v1/v2 双层模块框架
- 统一模块类型 ModuleInterface
- 模块 APK 签名校验
- 设计系统文档 design-system.md
- Compose 试点扩展 (AiFragment 顶部 + ModuleStoreActivity Tab)
- 模块商店空态/错误态插画

**验收**:
- [ ] AI 上传图片 → 中英摘要
- [ ] AI 答代码块有语法高亮
- [ ] 用户说"开 VPN 到 HK" → AI 自动调 tool
- [ ] 状态栏可见 "12/20 今日"
- [ ] VPN 启动时显示所有节点延迟
- [ ] 粘贴订阅链接 → 1 分钟 10+ 节点
- [ ] 工具商店可管理 27 工具开/关
- [ ] 扫码 → Base64 → 剪贴板 1 步完成
- [ ] v1/v2 import 数 14 → 0
- [ ] 任意 keystore 签的 APK 加载被拒
- [ ] Compose 试点接 MaterialTheme.colorScheme.*

**风险**: R-04 (Function calling 越权) / R-10 (签名拒签) / R-30 (资源清理错)

### M4 (M3 末) — 联动 + 灰度 + 安全

**日期**: 2026-10 月底 (预计)
**交付物**:
- 桌面 `app-debug-v1.6.0-2026-10-XX.apk`
- 4 个 dynamic APK v103
- AI 会话管理 (列表/重命名/删除/导出)
- AI 模板外置 (JSON 远程拉取)
- AI 代理策略 (mimo 直连)
- VPN 模块化真正落地 (独立 keystore + Auto-Publish)
- VPN 灰度发布 (channel 字段)
- 宿主 APK 瘦身 (15MB → 8MB)
- LeakCanary 接入
- 离线智能路由
- 5 语言国际化 (zh/en/ja/ko/vi)
- AI 单测覆盖 80%
- VPN 单测覆盖 80%

**验收**:
- [ ] 会话抽屉可管理 100+ 会话
- [ ] 修改模板不需发版 (远程拉取)
- [ ] mimo 响应延迟 < 1s (vs 经 VPN 3s+)
- [ ] 主 APK 移除 vpn 的 compileOnly 依赖
- [ ] 10% 用户先用上 VPN 新协议
- [ ] APK 体积 8MB
- [ ] 离线时 AI 仍能用本地 Gemma
- [ ] 日文/韩文/越南文可用
- [ ] AI 单测 80% / VPN 单测 80%

**风险**: R-19 (用户耐心) / R-08 (调色盘跳变) / R-11 (Keystore 失败)

### M5 (M5 末) — 第三方 + 商业化

**日期**: 2026-12 月底 (预计)
**交付物**:
- VLESS Reality 接入 (sing-box Reality outbound)
- Hysteria2 接入
- 自建中转 (chain detour UI)
- AI RAG (本机文档检索)
- AI 个性化 (persona dropdown)
- 第三方工具支持 (manifest 校验 + 沙箱 + 灰度)
- 工具评分评论 (Giscus)
- AI 多 Agent 协同 (AiPipeline JSON DSL)
- AI 跨设备同步 (端到端加密)
- 全模块化 (33 个模块入口全迁, 宿主 ≤ 5MB)

**验收**:
- [ ] 国内用户可稳定连 VPN
- [ ] 高丢包网络 (非洲/南美) 体验好
- [ ] "HK 中转 → US 落地" 可视配置
- [ ] "我手机里合同提到几号付款" AI 能答
- [ ] "我是程序员/学生/教师" 切 system prompt
- [ ] 第三方开发者可提交工具
- [ ] 工具详情页有评分
- [ ] "OCR → 翻译 → 改写" 自动串
- [ ] A 手机聊一半, B 手机能继续
- [ ] 宿主 APK ≤ 5MB

**风险**: R-04 (Function calling 越权) / R-27 (恶意代码) / R-22 (Reality 配置错)

### M6 (M5 末) — 体验升级

**日期**: 2027-02 月底 (预计)
**交付物**:
- AI 智能体 (Autopilot) (跨应用 Action)
- 离线模型市场 (Qwen/Phi/Llama)
- A/B 框架
- Material You 动态取色
- 跨模块主题共享
- 自适应 UI (WindowSizeClass)
- 工作流编辑器 (拖拽)
- 节点池 AI 选路 (30 天数据 + 线性回归)

**验收**:
- [ ] "帮我把这个 PDF 翻译后发到 Telegram" 可执行
- [ ] Qwen/Phi/Llama 一键切换
- [ ] 10% 用户走新 prompt 灰度
- [ ] Android 12+ 用户启动后看到壁纸取色
- [ ] 模块 APK 不重复带 token
- [ ] 平板/折叠屏体验优化
- [ ] 工作流拖拽编辑
- [ ] AI 选路优先推荐

**风险**: R-04 (Autopilot 越权) / R-17 (品牌色跳变) / R-08 (UI 跳变)

### M7 (M6 末) — 收尾 + 加固

**日期**: 2027-04 月底 (预计)
**交付物**:
- Baseline Profile 完善
- APK Signature Scheme v3.1
- AI 审计/可观测性
- 独立 App 评估
- Performance Profiling
- 跨平台规划 (KMP/HarmonyOS NEXT 评估)

**验收**:
- [ ] ModuleLoader.loadModule 关键路径进 baseline-prof.txt
- [ ] 抵御 APK 回滚攻击
- [ ] AI 全量 log (本地加密), 用户可"举报"
- [ ] AI 重度用户可单装独立 App
- [ ] 冷启动 < 500ms (模块商店首屏)
- [ ] KMP/HarmonyOS NEXT 可行性报告

**风险**: 无 (收尾阶段, 风险已收敛)

### 6.x 里程碑甘特图 (文字版)

```
W1-W2   [Sprint 1] 流式输出 + sing-box + 调色盘修
        [横向]    启动动画 + 国际补漏 + 错误细化
        ▼
W3-W4   [Sprint 2] ToolCapability + 6 工具迁移 + 死代码清
        [横向]    主题 token + 错误码细化 + 单测补
        ▼
M2 (W5-W8)        AI 多模态/Markdown/Function calling
        [横向]    VPN 智能选路 + 订阅 + Per-App
        [横向]    工具商店 + ToolAction 联动
        [横向]    模块框架 v1/v2 收敛 + 签名校验
        [横向]    设计系统文档 + Compose 试点
        ▼
M3 (W9-W12)       AI 会话/模板/代理策略
        [横向]    VPN 灰度 + 独立 keystore
        [横向]    宿主瘦身 + LeakCanary + 5 语言
        ▼
M4 (M3 末-M4 末)  VLESS Reality + Hysteria2 + 中转
        [横向]    AI RAG + 多 Agent + 跨设备同步
        [横向]    第三方工具 + 评分
        ▼
M5 (M4 末-M5 末)  Autopilot + 离线模型市场 + A/B
        [横向]    Material You + 跨模块主题 + 自适应 UI
        [横向]    工作流编辑器
        ▼
M6 (M5 末-M6 末)  Baseline Profile + APK v3.1 + 审计
        [横向]    独立 App 评估 + 跨平台规划
```

---

## 7. 子任务引用 (回链 4 个 track 报告)

### 7.1 track-ai 报告 (35KB, 466 行, 调研人 coder mvs_d2beeb3e0e7c4752b0e128ff08f65ca6)

**位置**: `Y:\GameMatrixApp\docs\refactor\track-ai.md`
**关键章节**:
- §1 现状: AI 助手**实际是 dynamic APK** (不是 host 内代码)
- §2 P0/P1/P2: 共 28 条问题清单
- §3 短/中/长期 roadmap
- §4 模块化建议 (维持 dynamic APK 不拆独立 App)
- §5 风险评估 (4 维)
- §7 关键引用一览 (file_path:line)

**本计划引用**:
- §1.1 重点模块优先级 #1 AI 助手 (★★★★★)
- §2.1 Sprint 1: S1-01 流式输出, S1-05 错误码 12, S1-07 placeholder 重试, S1-10 法律提示
- §2.1 Sprint 2: S2-06 AI 死代码清理, S2-09 AI 单测 60%
- §2.2 M2: M2-01 多模态, M2-02 Markdown, M2-03 Function calling, M2-04 配额 chip
- §2.2 M3: M3-01 会话管理, M3-02 模板外置, M3-04 代理策略
- §2.3 M4: M4-04 RAG, M4-05 个性化, M4-08 多 Agent, M4-09 跨设备同步
- §3.3.1 AI 模块 Gradle 命令
- §5 风险 R-02/03/04/05/06/07/25/28

### 7.2 track-vpn 报告 (18KB, 403 行, 调研人 coder)

**位置**: `Y:\GameMatrixApp\docs\refactor\track-vpn.md`
**关键章节**:
- §1 现状: 4 协议**全为 Socket 桩**, VpnServiceProxy 类不存在, x-ui 集成 0
- §2 用户痛点: P-01~15 共 15 条
- §3 改造设计: 短/中/长期 + 7 决策点 (D-01~07)
- §5 落地清单: 短期 11 + 中期 8 + 长期 7
- §6 风险: 6 条
- §7 参考: 文件 + URL

**本计划引用**:
- §1.1 重点模块优先级 #3 VPN (★★★★, 次重要)
- §2.1 Sprint 1: S1-02 sing-box 接入, S1-04 GameVpnService + Manifest
- §2.1 Sprint 2: S2-11 EncryptedSharedPreferences, S2-12 通知栏
- §2.2 M2: M2-05 智能选路, M2-06 订阅管理, M2-07 Per-App 路由
- §2.2 M3: M3-05 模块化落地 (独立 keystore + Auto-Publish)
- §2.3 M4: M4-01 VLESS Reality, M4-02 Hysteria2, M4-03 自建中转
- §2.3 M5: M5-08 节点池 AI 选路
- §3.3.2 VPN 模块 Gradle 命令
- §3.2 Fallback 矩阵: sing-box / v2rayNG / 桩 + TODO
- §5 风险 R-01/11/12/22

### 7.3 track-tools 报告 (38KB, 806 行, 调研人 coder)

**位置**: `Y:\GameMatrixApp\docs\refactor\track-tools.md`
**关键章节**:
- §1 现状: feature_tools **已是 dynamic APK**, 27 个工具, 硬编码注册
- §1.5 P0/P1/P2: 8 条核心问题
- §2 改造设计: 短/中/长期 + 27 工具决策矩阵
- §4 Kotlin interface 草案 (可直接编译)
- §5 工具商店 UI 草图 (主页/详情/工作流编辑器)
- §6 风险与缓解
- §7 DONT_DO 边界遵守
- §9 证据链 9 个核心文件

**本计划引用**:
- §1.1 重点模块优先级 #4 工具箱 (★★★★, 再进行模块化)
- §2.1 Sprint 2: S2-01 ToolCapability, S2-02 6 工具迁移, S2-03 initBinders 重构, S2-04 搜索+Tab, S2-05 ToolManagementActivity
- §2.2 M2: M2-09 modules.json 工具级扩展, M2-10 ToolManagement 完整版, M2-11 ToolAction 联动
- §2.2 M3: (隐含, 第三方工具准备)
- §2.3 M4: M4-06 第三方工具支持, M4-07 工具评分
- §2.3 M5: M5-07 工作流编辑器
- §3.3.3 工具箱 Gradle 命令
- §5 风险 R-13/14/15/16/23

### 7.4 track-platform 报告 (35KB, 471 行, 调研人 coder)

**位置**: `Y:\GameMatrixApp\docs\refactor\track-platform.md`
**关键章节**:
- §1 模块化架构现状: 11 模块 Gradle 拓扑 + v1/v2 双层框架
- §2 主题/UI 现状: 7 个 bug (dark/light 调色盘冲突为 P0)
- §3 P0/P1/P2: 15 条问题清单
- §4 分阶段方案: ST-1~7 / MT-1~9 / LT-1~6
- §5 DONT_DO_THIS 14 条逐条对照
- §6 风险与回滚
- §7 与其他 track 协作点

**本计划引用**:
- §1.1 重点模块优先级 #2 整体模块化 (★★★★★) + #5 UI 主题/美观
- §1.2 设计原则 1-6
- §1.3 价值主张
- §2.1 Sprint 1: S1-03 调色盘修, S1-08 启动动画, S1-09 国际补漏
- §2.1 Sprint 2: S2-07 dimens token, S2-08 模块商店按钮改 token, S2-10 首页改版
- §2.2 M2: M2-08 ProxyStateProvider, M2-12 v1/v2 收敛, M2-13 统一模块类型, M2-14 签名校验, M2-15 设计系统文档, M2-16 Compose 试点, M2-17 空态/错误态
- §2.2 M3: M3-06 灰度发布, M3-07 宿主瘦身, M3-08 LeakCanary
- §2.3 M5: M5-04 Material You, M5-05 跨模块主题, M5-06 自适应 UI
- §3.3.4 主 APK Gradle 命令
- §3.3.5 core 模块 Gradle 命令
- §5 风险 R-08/09/10/17/18/20/21

### 7.5 关键决策交叉验证

下表交叉对比 4 个 track 报告的关键决策, 验证是否一致:

| 决策 | track-ai | track-vpn | track-tools | track-platform | 本计划立场 |
|------|----------|-----------|-------------|----------------|------------|
| **AI 是否独立 APK** | 维持 dynamic APK | (不涉及) | (不涉及) | (不涉及) | ✅ 维持 |
| **VPN 内核** | (不涉及) | sing-box | (不涉及) | (不涉及) | ✅ sing-box |
| **VPN 协议** | (不涉及) | Reality + Hysteria2 | (不涉及) | (不涉及) | ✅ 长期接入 |
| **工具箱动态 APK 拆分** | (不涉及) | (不涉及) | **不拆** (fractal) | (不涉及) | ✅ fractal |
| **Compose 范围** | (不涉及) | (不涉及) | (不涉及) | 不整屏, hall 局部 | ✅ 不整屏 |
| **OkHttp 治理** | P0-3 改 provider | (依赖 NET_LAYER) | (不涉及) | (依赖 NET_LAYER) | ✅ Sprint 1 修 |
| **主题 token 化** | (不涉及) | (不涉及) | 接受 token 化 | ST-2 dimens token | ✅ 50% 降硬编码 |
| **暗色品牌色** | (不涉及) | (不涉及) | (不涉及) | ST-1 修冲突 | ✅ Sprint 1 修 |
| **模块签名校验** | (不涉及) | (不涉及) | (不涉及) | MT-3 | ✅ M2 接入 |
| **国际化** | (不涉及) | (不涉及) | (不涉及) | ST-3 补漏 | ✅ Sprint 1 + M3 5 语言 |
| **死代码清理** | P1-9 删 host 内 ~30% | (不涉及) | (不涉及) | (隐含) | ✅ Sprint 2 删 |
| **核心:common 共享** | §4.2.2 抽 ai-contract | (不涉及) | §2.1.1 抽 tool | MT-2 统一接口 | ✅ Sprint 2 + M2 抽 |

**所有决策无冲突, 可按本计划执行**。

---

## 附录 A: 实施检查清单 (供 Sprint 启动会勾选)

### A.1 Sprint 1 (W1-W2) 启动前

- [ ] 本地工具栈确认 (java/adb/gradle) - 见 §3.1.1
- [ ] 桌面清理 (旧 APK 移到 archive)
- [ ] sing-box AAR 下载 (或确认 fallback)
- [ ] markwon 4.6.2 离线 jar 准备
- [ ] 4 个 track 报告在 4 个 browser tab 打开
- [ ] DONT_DO_THIS.md 14 条在屏常驻
- [ ] 创建 `docs/refactor/sprint-1-notes.md` 跟踪
- [ ] 创建 `docs/LOCAL_DEV_CHECK.md`
- [ ] 桌面 `build-apk.ps1` 脚本就绪

### A.2 Sprint 2 (W3-W4) 启动前

- [ ] Sprint 1 5 个 APK 装模拟器烟囱测试全绿
- [ ] 暗色品牌色在 5 个核心模块视觉连续
- [ ] markwon 4.6.2 已确认可用 (或 fallback 自写 renderer)
- [ ] `core:common` 工具契约 review
- [ ] 6 个 P0 工具代码 review (text_codec/clipboard/qr/qr_plus/color/color_plus)
- [ ] 测试基础设施确认 (JUnit 5 / MockWebServer / Robolectric)
- [ ] LeakCanary 准备接入

### A.3 M2 (M2 末) 启动前

- [ ] Sprint 2 桌面 APK 装包全绿
- [ ] `core:network` ProxyStateProvider 接口 review
- [ ] 智能选路算法 review (延迟/丢包/负载权重)
- [ ] 设计系统文档草稿 review
- [ ] Compose 试点选点 review (AiFragment 顶部 + ModuleStoreActivity Tab)
- [ ] 模块签名校验灰度开关就绪

---

## 附录 B: 用户原始 5 点要求 → 本计划回应索引

| # | 用户原话 | 本计划回应 |
|---|----------|------------|
| 1 | **"AI 助手是重点改造模块"** | §1.1 优先级 #1 ★★★★★; §2.1 Sprint 1 S1-01/05/07/10; §2.1 Sprint 2 S2-06/09; §2.2 M2 全部 4 项; §2.2 M3 全部 4 项; §2.3 M4 4 项; §2.3 M5 M5-01/02/03 |
| 2 | **"VPN 是次重要模块"** | §1.1 优先级 #3 ★★★★; §2.1 Sprint 1 S1-02/04/12; §2.1 Sprint 2 S2-11/12; §2.2 M2 全部 3 项; §2.2 M3 M3-05; §2.3 M4 M4-01/02/03; §2.3 M5 M5-08 |
| 3 | **"工具箱再进行模块化操作"** | §1.1 优先级 #4 ★★★★; §2.1 Sprint 2 全部 5 项 (S2-01~05); §2.2 M2 M2-09/10/11; §2.3 M4 M4-06/07; §2.3 M5 M5-07 |
| 4 | **"重点是模块化推进"** | §1.1 优先级 #2 ★★★★★; §1.2 设计原则 2 (模块化优先); §2.1 Sprint 1 S1-04; §2.1 Sprint 2 S2-01/02/03/05; §2.2 M2 M2-08/12/13/14; §2.2 M3 M3-05/06/07; §2.3 M4 M4-10; §2.3 M5 M5-05 |
| 5 | **"从功能、美观入手"** | §1.1 优先级 #5 ★★★★; §1.2 设计原则 4 (视觉从 Material 3 token 出发); §1.3 价值主张 (功能 + 美观双维度); §2.1 Sprint 1 S1-03/08/09; §2.1 Sprint 2 S2-07/08/10; §2.2 M2 M2-15/16/17; §2.3 M5 M5-04/05/06 |

---

## 附录 C: 执行约束 → 本计划回应索引

| # | 用户原话 | 本计划回应 |
|---|----------|------------|
| 1 | **"本地优先"** | §3.1.1 本地工具栈确认; §3.1.2 本地命令清单; §3.1.3 本地 → 模拟器 → 桌面 部署流; §3.5 不出网约束 |
| 2 | **"缺依赖 fallback"** | §3.2 Fallback 矩阵 (10 个依赖); §3.2.1 决策树; §3.2.2 离线模式命令 |
| 3 | **"APK 放桌面"** | §3.1.3 桌面 APK 命名规范; §3.1.3 桌面清理脚本; §3.4 桌面 APK 集中管理 (脚本) |

---

## 附录 D: 文档版本与维护

| 字段 | 值 |
|------|-----|
| 文档版本 | v1.0 |
| 创建日期 | 2026-06-04 16:42 |
| 创建人 | general (Mavis orchestrator) |
| 输入 | 4 个 track 报告 (合计 126KB) + DONT_DO_THIS.md + plan.yaml |
| 维护规则 | 每次 Sprint 结束更新 "状态" 列; 重大决策变化 (D-01 选型等) 必须更新 |
| 回滚规则 | 用户/PM 一句话可回滚到 v0.9 (4 track 报告独立保留) |
| 下次 review | 2026-06-18 (Sprint 1 末) |

---

**文档结束**

> 本计划是 4 个 track 调研的合成产物, 严格遵守 `DONT_DO_THIS.md` 14 条硬约束, 回应用户原始 5 点要求 + 3 项执行约束。任何与 4 个 track 报告不一致的决策, 以本计划为准 (因为本计划经过二次综合判断); 任何与 DONT_DO_THIS.md 冲突的决策, 以 DONT_DO_THIS.md 为准 (硬约束)。


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
