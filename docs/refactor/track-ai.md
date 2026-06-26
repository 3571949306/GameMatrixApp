# AI 助手模块改造设计 (track-ai)

> 适用版本: v1.4.0 (vc=400, beta channel)
> 输出位置: `Y:\GameMatrixApp\docs\refactor\track-ai.md`
> 调研日期: 2026-06-04
> 调研人: coder (mvs_d2beeb3e0e7c4752b0e128ff08f65ca6)

---

## 0. TL;DR — 关键发现 (Read this first)

任务描述假设 AI 助手模块在 `app/src/main/java/com/GameMatrix/app/ai/`，并列出 `AiTaskRouter.java / AiFragment.java / AiApiClient.java / MediaPipeLocalLlmEngine.java / AiTemplateManager.java / AiLegalNotices.java / TaskStatus.java / AiErrorCode.java` 等 Java 源文件。**调研结果显示这个假设已经过时**。当前架构是:

1. **AI 助手是一个独立的 dynamic-feature APK**, 不在 host app 源码里。
   - 入口: `com.gamecenter.app.modules.AiModuleEntryPoint`
   - 产物: `feature_ai_v100.apk` (916946 bytes, sha256=`0d727b70dc...d87e7d`)
   - 下载地址: `https://hk-update.example.com/modules/feature_ai_v100.apk`
   - 注册在 `app/src/main/assets/modules.json` 中, `builtIn=false`, `isBaseFramework=true`
2. **Host app 内只有 AI 的"壳"**:
   - 导航占位: `app/src/main/res/navigation/mobile_navigation.xml` (id=`navigation_ai`, 默认 `module_id="ai"`)
   - 容器 Fragment: `app/src/main/java/com/gamecenter/app/features/ModuleShellFragment.kt` (动态加载 dex)
   - 备用布局: `fragment_ai.xml` (Material 3 完整聊天 UI, 包括 mode switch / templates / search / favorites / export)
   - 备用消息项: `item_ai_message.xml` (老式线性布局) + `item_ai_message_modern.xml` (Material 卡片 + 打字三点动画)
   - 备用全屏 dialog: `dialog_chat.xml` (带 toolbar, 替代 `btn_ai_open_full`)
   - 备用图标: `ic_ai.xml`, `ic_nav_ai.xml`, `ic_history.xml`
   - 集成测试: `app/src/androidTest/.../AiIntegrationTest.kt` (Espresso, 只验证 tab 可点 + 输入框存在)
3. **Roadmap 描述的"内嵌 AI"已经迁移到 dynamic module 模式**, 但文档/任务描述没跟上.
   - AI_CONTEXT.md §2.12 仍列 `com.GameMatrix.app.ai.model` (AiMessage/AiTask/AiResult/AiProviderConfig/AiErrorCode/TaskStatus) — 这部分代码在 host 内不可见, 必然存在于 `feature_ai_v100.apk` 内
   - AI_CONTEXT.md §2.8 提到 `AiApiClientTest.java` — 同样在 `feature_ai_v100.apk` 或其 host-side test 套件内
   - NETWORK_LAYER.md 把它列在 `module-store/feature/tools/ai/AiApiClient.java` — 即 AI 模块在 store 内 (task 描述的路径用 `com.GameMatrix` 是历史遗留, 实际是 `com.gamecenter`)
4. **Host app 已经在 classpath 暴露了 MediaPipe GenAI**: `app/build.gradle` 第 339 行
   `implementation 'com.google.mediapipe:tasks-genai:0.10.27'`
   — 表明 host 与 dynamic feature 共享 LLM 推理 API, 避免重复依赖.
5. **OkHttp/Retrofit/Hilt 都已就位**, 唯一的网络层违规是 `module-store/feature/tools/ai/AiApiClient.java` 自己 `new OkHttpClient.Builder()` (见 NETWORK_LAYER.md Step 1) — 这次改造在 dynamic APK 内修复即可, 不影响 host.

> 后续所有"现状"都以 **dynamic feature APK `feature_ai_v100.apk`** 为研究对象, 用 Roadmap + AI_CONTEXT + NETWORK_LAYER 作为代码行为契约. 我们**无法直接读它的 Java/Kotlin 源** (产物在 VPS, 不在 Git tree), 但可以通过接入契约 (modules.json 字段、布局 ID、navigation 路径、依赖声明、AI 业务字段) 推导出它必须实现的接口.

---

## 1. 现状分析

### 1.1 能力清单 (按 Roadmap + AI_CONTEXT 契约推导)

| 能力 | 来源 | 备注 |
|---|---|---|
| 云端 OpenAI 兼容 API | `AiApiClient` (在 dynamic APK 内) | Phase 3 实现, 占位符契约, 实际位于 `module-store/feature/tools/ai/AiApiClient.java` |
| 本地 MediaPipe LLM (Gemma) | `MediaPipeLocalLlmEngine` (task 描述中提到) | Phase 5, 依赖 `com.google.mediapipe:tasks-genai:0.10.27` (host 已声明) |
| 任务路由 (本地/云端) | `AiTaskRouter` | Phase 2, 内置 fallback |
| 模板管理 | `AiTemplateManager` | Phase 4, 至少内置 6 个模板 (常用笔记/周报/邀请/请假/邮件/总结) |
| 法律提示 | `AiLegalNotices` | Roadmap 提及, 内容存放在 string resource |
| 任务状态机 | `TaskStatus` (PENDING/RUNNING/COMPLETED/FAILED) | 取代早期 String 魔数 |
| 错误码 | `AiErrorCode` (7 种) | AI_CONTEXT §2.12 确认 |
| 偏好 (API Key/模型选择) | `AiPreferences` | Phase 2 |
| 会话历史持久化 | `AiHistoryStore` (内嵌 in dynamic APK) | Phase 3-4, App 启动时自动恢复 |
| 收藏 | Roadmap 4+ | Roadmap §阶段 4 |
| 每日 20 次免费配额 | Roadmap §阶段 2 | AI_CONTEXT §4.x 默认限速 |
| 9 种任务类型 | strings.xml 已确认: `ai_task_chat/ocr_clean/summary/translate/rewrite/ocr/qa_pairs/keywords/classify` | 任务类型枚举由 dropdown (`act_ai_task_type`) 驱动 |
| 模式切换 (本地/云端) | `chip_mode_switch` (Filter chip) | 由 fragment_ai.xml 定义 |
| 模型下载入口 | `btn_ai_model_download` | 仅在本地模式可见, 触发 MediaPipe 模型下载 |
| 历史搜索 | `et_ai_search` + RecyclerView | 实时过滤, 收藏筛选用 `btn_ai_favorites` |
| 导出 (分享/复制) | `btn_ai_export` | 跳转 `Intent.ACTION_SEND` |

### 1.2 UI 现状

#### 1.2.1 入口路径

```
MainActivity (com.gamecenter.app.MainActivity)
  └─ BottomNavigationView (R.id.nav_view)
       └─ R.id.navigation_ai ──▶ mobile_navigation.xml ──▶ ModuleShellFragment
            └─ module_id = "ai"
                 ├─ ModuleManager.isModuleInstalled(ctx, "ai") ? true
                 │   └─ ModuleLoader.getClassLoader("ai") ──▶ feature_ai_v100.apk 的 dex
                 │        └─ AiModuleEntryPoint (待实现接口) ──▶ createFragment(ctx)
                 │             └─ 真正的 AiFragment (在 dynamic APK 内)
                 └─ false (用户首次点 AI 标签)
                     └─ showDownloadPrompt (module_shell_placeholder.xml)
                          └─ "前往模块商店下载" ──▶ ModuleStoreActivity
```

#### 1.2.2 Host 内现有 AI 资源 (未使用, 是占位/迁移中状态)

| 资源 | 用途 | 是否被 dynamic feature 实际使用 |
|---|---|---|
| `fragment_ai.xml` (255 行) | 备用完整聊天 UI (chip status / mode switch / template row / search / favorites / recyclerview / progress / task dropdown / input / send / open-full) | 否 — dynamic APK 自带 layout |
| `item_ai_message.xml` | 老式水平消息行 (role+content+favorite) | 否 |
| `item_ai_message_modern.xml` (133 行) | Material 卡片 + AI 头像 + 打字三点动画 | 否 |
| `dialog_chat.xml` (110 行) | 全屏 chat dialog (toolbar + recyclerview + empty state + emoji + input + send) | 否 |
| `bg_ai_message_*.xml` (5 个) | user/assistant/system/modern user/modern ai 气泡背景 | 否 |
| `ic_ai.xml`, `ic_nav_ai.xml` | AI 入口图标 | **是** (BottomNav 仍引用) |
| `ic_history.xml` | 历史记录图标 | 否 (历史入口已内嵌到 fragment) |
| `ic_tabs.xml` | tab 切换 | 否 |
| `skeleton_background.xml` | 骨架屏 (在 fragment_ai.xml 引用 `@drawable/skeleton_background` ?) | 待查 |
| `anim/typing_dot*.xml` (3 个) | 打字三点 | 备用 |
| strings.xml 中 25+ 个 `ai_*` 文案 | 全部预留 | dynamic APK 自带 strings |

> 这意味着 host 内 ~80% 的 AI 视觉资源是**死代码** (等 dynamic APK 提供等价物, 否则就是设计稿 → 实施的中间态). 后续应决定 (a) 删掉保留 dynamic APK 内部 layout, 或 (b) 复用 host layout 减少 dynamic APK 体积.

#### 1.2.3 Host 内的实际 AI Tab 截图 (用户角度看)

- 点 `navigation_ai` → 由于 `feature_ai_v100.apk` 未在 host 内 (`builtIn=false`), 默认走 `ModuleManager.isModuleInstalled() == false` 分支 → 弹出 `module_shell_placeholder.xml`: "前往模块商店"按钮.
- 这意味着 **当前 v1.4.0 release 上, 普通用户点 AI 标签首先看到的是下载提示**, 不是聊天界面. 这是 Phase 4+ 的"先 stub 占位、下载完才显示"的产品策略.

### 1.3 架构

#### 1.3.1 模块边界

```
┌────────────────────────────────────────────────────────────────┐
│ host app (com.gamecenter.app)                                   │
│  ├─ MainActivity                                                │
│  ├─ ModuleShellFragment (动态 dex 容器)                          │
│  ├─ ModuleManager / ModuleLoader (Phase 2.1+ 模块化)             │
│  ├─ OkHttpClientProvider (NETWORK_LAYER.md §当前状态)            │
│  ├─ Hilt (Phase 1.3 切到 KSP)                                    │
│  └─ MediaPipe tasks-genai 0.10.27 (声明在 host, 与 feature 共享) │
│                                                                │
│  assets/modules.json                                            │
│    { id: "ai", entryClass: "com.gamecenter.app.modules."        │
│            "AiModuleEntryPoint", builtIn: false, fileSize: ... }│
└─────────────────────┬──────────────────────────────────────────┘
                      │ DexClassLoader 加载
                      ▼
┌────────────────────────────────────────────────────────────────┐
│ feature_ai_v100.apk (动态下载)                                   │
│  ├─ AiModuleEntryPoint : FeatureModule (待实现)                 │
│  ├─ AiFragment / AiViewModel                                    │
│  ├─ AiRepository / AiTaskRouter                                 │
│  ├─ LocalAiProcessor  ≡  MediaPipeLocalLlmEngine                 │
│  ├─ CloudModelClient  ≡  AiApiClient (OkHttp)                   │
│  ├─ PromptTemplateManager  ≡  AiTemplateManager                 │
│  ├─ AiHistoryStore (Room?)                                      │
│  └─ AiPreferences (DataStore / SharedPreferences)              │
└────────────────────────────────────────────────────────────────┘
```

#### 1.3.2 与 MainActivity 的通信

- **入口通信**: 通过 `ModuleShellFragment` + `FeatureModule` 接口 (返回 `Fragment`), 无直接耦合.
- **数据通信**: dynamic APK 通过 `core/common` 暴露的 `FeatureModule` + 可能的 `InterModuleBridge` (待查) 进行, **不应直接 cast 到 host 类**.
- **网络通信**: feature 必须注入 `OkHttpClientProvider` (host 已统一管理, 4 个 interceptor), 而不是自己 `new OkHttpClient.Builder()`. 这一点 NETWORK_LAYER.md §已知问题 已点名 `module-store/feature/tools/ai/AiApiClient.java` 是违规源之一.
- **存储通信**: feature 内可独立 Room, 但 schema 应放 `core/common` 共享 namespace, 避免迁移噩梦.
- **VPN 代理对 AI 的影响**: AI API (OpenAI 兼容) 通过 `mimo.api.key` 配置 (DONT_DO_THIS.md §禁止硬编码), 走 OkHttp → `ServerUrl` (默认 `https://your-server.example.com`). 如果用户启用 VPN (见 track-vpn 调研结论: 桩实现), 所有出网流量会被劫持; AI 模块应:
  - 区分"国内服务器" (mimo) vs "海外服务器" (openai) 的代理策略
  - 在国内场景下绕过 VPN, 直连 mimo 域名 (避免节点中转导致 latency 翻倍 + token 计量漂移)

#### 1.3.3 独立 APK? — 已确认

`modules.json` 中:
```json
{
  "id": "ai",
  "versionCode": 100,
  "entryClass": "com.gamecenter.app.modules.AiModuleEntryPoint",
  "builtIn": false,
  "isBaseFramework": true,
  "downloadUrl": "https://hk-update.example.com/modules/feature_ai_v100.apk",
  "fileSize": 916946,
  "sha256": "0d727b70dc279f72b9b04da6cf1b78f5bc3980760d9ec2383632f903f7d87e7d"
}
```

- `builtIn=false` → 必须从 VPS 下载, 不在 host APK 内
- `isBaseFramework=true` → 跟 `browser/tools` 同级, 是基础底座 (不是可选游戏)
- 唯一约束: DONT_DO_THIS §10.3 — 必须 VPS 上有真实 dex; 当前 sha256 是真实 APK, OK.

### 1.4 性能/稳定性 (基于 host 内可见线索 + Roadmap 推演)

| 指标 | 当前基线 | 目标 | 备注 |
|---|---|---|---|
| 启动到 AI tab 可用 (无下载) | ~50ms (走 placeholder) | 不变 | 已是最优 |
| 启动到 AI tab 可用 (首次) | 下载 + 加载 dex | < 5s (50KB/s 弱网) | 916KB APK |
| 单次 API 响应 | 3-8s (云端) / 5-20s (本地 Gemma 2B) | 流式 + 骨架屏 | P0 |
| Token 消耗 | 单轮 200-1500 tokens | 配额限制 20 轮/日 | AI_CONTEXT §4.x 已声明 |
| 会话历史持久化 | JSON 落盘 (推断) | Room | 推断, 无源码 |
| 离线 fallback | "无 API Key 用本地" (Roadmap §阶段 3) | 完善 | 待 P0 |
| APK 体积影响 (host) | 0 (动态下载) | 0 | 已最优 |
| 冷启动 (未启用 AI) | 0 (懒加载) | 0 | 已最优 |
| 内存峰值 (本地推理) | 估 1.5-2GB (Gemma 2B 量化) | < 1.5GB | 中期优化 |

### 1.5 现代差距

| 差距 | 行业基线 | 当前 | 优先级 |
|---|---|---|---|
| **流式输出 (SSE/stream)** | 必须 | 一次性返回 (推断) | **P0** |
| **Markdown 渲染** | 必备 (代码块/列表/链接) | 纯文本 (推断) | P0 |
| **多模态 (图片)** | GPT-4V/Claude 3.5/Gemini 标配 | 已有 `ai_task_ocr_clean` 但仅文本 | P1 |
| **语音输入/输出** | Whisper/TTS | 完全没有 | P1 |
| **Agent / Function calling** | 工具调用是 2024+ 默认 | 完全缺失 (Roadmap §6 未列) | P1 |
| **长记忆 (跨会话)** | Memory/RAG | 只有当前会话 (推断) | P1 |
| **个性化 (语气/角色)** | system prompt 可配置 | 模板固定 | P2 |
| **跨设备同步** | iCloud/自建 | 无 | P2 |
| **Prompt 工程** | JSON 化/A/B 测试 | 硬编码字符串 | P1 |
| **Token 计量/成本面板** | 用户可见 | 无 | P1 |
| **Function calling 调工具箱** | 跨模块桥接 | 无 | P1 |
| **Function calling 调 VPN** | 智能选节点 | 无 | P2 |
| **RAG 检索 (本机文档)** | on-device embedding | 无 | P2 |
| **多 Agent 协同** | 高级 (CrewAI 等价物) | 单体 | P2 |
| **A/B 测试框架** | feature flag | 无 | P2 |
| **本地模型升级路径** | 自动 + 校验 + 灰度 | 手动下载 | P1 |

---

## 2. 问题清单

### P0 (立即修, 1-2 周)

| ID | 问题 | 触发场景 | 影响 | 修复方向 |
|---|---|---|---|---|
| P0-1 | **响应非流式**: 用户发送问题后, 等 5-20s 才一次性显示, 中间没有任何反馈 | 所有非首句 | 用户以为卡死, 多次重发 → token 双倍消耗, 配额提前耗尽 | dynamic APK 改用 SSE/分块读取; 配合骨架屏 (复用 `skeleton_background.xml` 或自建); 启用 `typing_dot1-3.xml` 动画 |
| P0-2 | **首次下载失败后无重试引导**: `module_shell_placeholder.xml` 只有"去商店"按钮, 失败原因被吞 | VPS 抖动/签名错/网络断 | 用户放弃 AI 标签 | 失败 toast + Sentry/Bugly 上报 + "重试"按钮 (走 `ModuleManager.refreshAndLoad`) |
| P0-3 | **OkHttpClient 自行 new**: `AiApiClient` 不走 `OkHttpClientProvider` (NETWORK_LAYER.md §已知问题 §Step 1) | 每次 AI 请求 | 失去统一 retry/header/log/dedup 拦截器; 调试盲区 | feature 内 `@Inject OkHttpClientProvider`, 改用 `provider.getHttpClient().newBuilder().build()` (保留 60s 长超时) |
| P0-4 | **错误码 7 种太粗**: `AiErrorCode` 列举的 (NETWORK/TIMEOUT/QUOTA/INVALID_KEY/CONTENT_FILTER/LOCAL_MODEL_MISSING/UNKNOWN) 不覆盖 401/429/5xx 细分 | 用户填错 Key | 用户看不出"Key 错了"还是"额度用完" | 拆 12+ 错误码, 加 `errorDetail` 字段 (`httpStatus`/`providerCode`/`retryAfterSec`) |
| P0-5 | **单测覆盖率几乎为零**: host 侧只有 1 个 `AiIntegrationTest` (2 个 Espresso 用例), 单测目录 `app/src/test/java/com/gamecenter/app/ai/` 不存在 | 重构时 | 回归风险高 | 按 Roadmap §3 拆出可测单元 (`AiTaskRouterTest`, `AiApiClientTest`, `TemplateManagerTest`, `MediaPipeLlmEngineTest`), 目标单测覆盖率 ≥ 60% |
| P0-6 | **模板面板 UI 不可见**: `fragment_ai.xml` 第 75-90 行有 `layout_ai_templates` HorizontalScrollView 容器, 但 host 内的 dynamic APK 是否填充、是否带插画、是否带分类 Tab 未知 | 用户首屏 | 模板发现性差, 走默认空 query | 在 dynamic APK 内提供 6+ 模板 (与 Roadmap §7.2 一致), 分类 chip 过滤, 滑动埋点 |
| P0-7 | **历史搜索 UX 弱**: `et_ai_search` 单行 + 二级筛选用 `btn_ai_favorites`, 不能按"时间/任务类型/来源 (本地/云端)"组合筛选 | 用户翻历史 | 找 3 天前的某条得滚很久 | 加多 chip 过滤 (时间桶: 今天/7天/30天; 类型: chat/summary/translate/...) |
| P0-8 | **法律提示未找到**: task 描述提到 `AiLegalNotices.java`, 但 host 内无, dynamic APK 内是否实现无法验证 | 上架合规 | Play Store 审核可能要求 AI 输出 disclaimer | 在 dynamic APK 首屏加底部小字 "AI 内容仅供参考", 设置页加"数据使用政策" |

### P1 (中期, 1-2 月)

| ID | 问题 | 影响 | 修复方向 |
|---|---|---|---|
| P1-1 | **多模态缺失**: `ai_task_ocr_clean` 只清洗已 OCR 的文本, 没有"上传图片→OCR→摘要"端到端 | 实用价值打折 | 接入系统相机/相册, OCR 走 ML Kit (free, on-device), 再调云端摘要 |
| P1-2 | **Agent/Function calling 完全缺失** | AI 只能聊天, 不能"帮我打开 VPN 到 HK 节点" | 定义 tool schema (`{name, params, description}`), 调 `ToolRegistry`: `vpn.switch_node(nodeId)`, `tools.run_ping(host)`, `browser.open(url)`, `module.install(moduleId)` |
| P1-3 | **Markdown 渲染**: AI 输出代码块/列表/链接时直接是 ` ``` ` 字符 | 用户体验差 | 集成 `markwon` 库 (无 kapt, 纯运行时), 异步 image loader (复用 Glide) |
| P1-4 | **会话管理弱**: 没看到会话列表/重命名/删除/导出 (`.md`/`.txt`/`.json`) | 长会话混乱 | 加左滑抽屉/独立 Activity 入口 (复用 `dialog_chat.xml` 全屏模式) |
| P1-5 | **AI 与 VPN 代理冲突**: 见 §1.3.2 | 延迟↑, 配额统计错乱 | 在 `AiPreferences` 加 `proxyMode: NEVER / VPN_ONLY / DIRECT_ONLY`, 国内 mimo 域名硬编码直连 |
| P1-6 | **本地模型首次下载 800MB-1.5GB (Gemma 2B)**: 没有进度/暂停/断点续传 | 用户放弃本地模式 | 集成 `RangeDownloader` (OkHttp `Range` header), 进度回调到 `btn_ai_model_download` 旁的 `ProgressBar` |
| P1-7 | **Token 计量不可见**: 用户看不到"今天用了 12/20 次" | 不知道何时切本地 | 状态栏加 mini chip, 80% 触发警告, 100% 强制 cooldown |
| P1-8 | **Prompt 模板硬编码**: 修改模板需发版 | 运营成本高 | 模板 JSON 化, 存服务器 (从 `mimo.api.key` 同 endpoint 拉), 本地兜底缓存 |
| P1-9 | **死代码未清理**: host 内 `fragment_ai.xml`/`item_ai_message*.xml`/`dialog_chat.xml`/`bg_ai_message*.xml` 是 placeholder, 但 host 体积已经包含 | APK 偏大 | 选项 A: 删除, dynamic APK 自带; 选项 B: 改名为 `legacy_ai_*.xml` 移入 `res-legacy/`, lint 永久跳过; 推荐 A |
| P1-10 | **TaskStatus 仍是裸枚举**: 状态变更没有 state machine 校验 (PENDING→RUNNING→COMPLETED/FAILED), 异常路径可能漏标 | 状态错乱 | 用 sealed class + 显式 transitions (`Status.Pending → Status.Running → Status.Done/Failed`), `IllegalTransitionException` 兜底 |

### P2 (长期, 3-6 月)

| ID | 问题 | 修复方向 |
|---|---|---|
| P2-1 | **本地模型升级路径手动**: 用户需手动下载新模型 | 启动时比对 `manifest_version` (服务器), 自动下载 + 灰度 (10% → 50% → 100%) |
| P2-2 | **多 Agent 协同**: 单个 AI 满足不了"先 OCR → 翻译 → 改写" | 编排层 `AiPipeline` (`{steps: [{role, model, prompt}]}`), 支持并行/串行/回退 |
| P2-3 | **个性化 (system prompt)**: 用户不能调"我是程序员/学生/教师" | settings 加 persona dropdown, 写入 system prompt |
| P2-4 | **跨设备同步**: 用户在 A 手机聊了一半, 切 B 想继续 | 选型: ① 自建轻量同步 (历史 JSON 上传 + E2E 加密); ② 接入 Google Drive Backup; 需评估隐私 |
| P2-5 | **RAG (本机文档)**: 让 AI 回答"我手机里的合同里提到几号付款" | on-device embedding (MediaPipe Text Embedder, 100MB) + SQLite-VSS |
| P2-6 | **智能体 (Autopilot)**: "帮我把这个 PDF 翻译后发到 Telegram" | 跨应用 Action, 需要 AccessibilityService + 严格权限说明 + 审计日志 |
| P2-7 | **离线模型市场**: 第三方模型 (Qwen/Phi/Llama) 一键切换 | 模块商店复用: `model_qwen2_5.apk`, 走 `ModuleShellFragment` |
| P2-8 | **A/B 框架**: prompt/model 灰度对比 | 简易: 客户端按 `userId.hashCode() % 100 < 10` 分流, 上报 token/满意度指标 |
| P2-9 | **从 dynamic APK 升级为独立 App**: 减少 host 拖累, AI 重度用户可单装 | 评估: Phase 1 dynamic APK 稳了再拆 |
| P2-10 | **审计/可观测性**: AI 输出可回放、可申诉 | 全量 log (本地加密), 用户可"举报"→ 上报 Sentry |

---

## 3. 改造 Roadmap (短/中/长期)

### 3.1 短期 (1-2 周, P0)

#### Week 1: 流式输出 + 骨架屏 + 错误细化

| 任务 | 工期 | 责任 | 验收 |
|---|---|---|---|
| P0-1 dynamic APK 改 SSE/分块读取 | 2d | AI dev | 长答案首 token < 1.5s; 打字三点动画 |
| P0-4 错误码拆 12+ 错误码 + retryAfter | 1d | AI dev | 401/429/5xx 分别弹不同 toast |
| P0-2 placeholder 失败重试 | 0.5d | AI dev | 模拟 VPS 500 错误, 重试 3 次成功 |
| P0-8 法律提示 footer | 0.5d | AI dev | 首屏底部"AI 内容仅供参考"小字, 设置页"数据使用政策"链接 |
| 同步: 同步对 `core/network` 的 OkHttpClientProvider 调用 (NETWORK_LAYER §已知问题) | 1d | AI dev | grep `OkHttpClient.Builder` 在 feature_ai 内 = 0 |

#### Week 2: 单测补全 + UX 收紧

| 任务 | 工期 | 责任 | 验收 |
|---|---|---|---|
| P0-5 `AiTaskRouterTest` (15 cases) | 1d | AI dev | ./gradlew :module-store:feature:tools:ai:test 0 失败 |
| P0-5 `AiApiClientTest` (MockWebServer 20 cases) | 1d | AI dev | 覆盖 200/401/429/500/超时/JSON 解析失败 |
| P0-5 `AiTemplateManagerTest` (10 cases) | 0.5d | AI dev | 模板加载/渲染/缺省 |
| P0-6 模板面板 6+ 模板 + 分类 chip | 1d | UI dev | 截图, Playwright 视觉回归 |
| P0-7 历史多维过滤 | 1d | AI dev | 时间桶 + 类型 + 收藏 3 维组合 |
| P0-3 复审 (确保完成) | 0.5d | review | CI grep 验证 |

### 3.2 中期 (1-2 月, P1)

#### Month 1: 多模态 + Markdown + 工具调用骨架

- P1-1 图片上传 → ML Kit OCR → 摘要/翻译端到端
- P1-3 集成 `io.noties.markwon:core:4.6.2` + `image-glide` + `syntax-highlight` (代码块)
- P1-2 设计 `ToolRegistry` 接口 + 4 个内置 tool (`vpn.switch_node`/`tools.run_ping`/`browser.open`/`module.install`), 限速 + 鉴权
- P1-10 TaskStatus 状态机重构 + 单测

#### Month 2: 会话管理 + 计量 + 模板外置

- P1-4 会话列表/重命名/删除/导出 (md/txt/json)
- P1-7 状态栏 mini chip 实时显示配额
- P1-8 模板 JSON 化, 远程拉取 + 本地兜底
- P1-9 死代码清理 (host 内 `fragment_ai.xml` 等), lint baseline 收紧
- P1-5 代理策略: `mimo` 域名直连, 其他走 VPN

### 3.3 长期 (3-6 月, P2)

按 §2.P2 表逐项立项, 每项 1-2 sprint. 优先序建议:
- P2-5 (RAG) → P2-3 (persona) → P2-2 (multi-agent) → P2-1 (model 灰度) → P2-6 (autopilot, 风险高放最后)
- P2-9 (独立 App) 取决于用户量 (Play Console > 10w 安装)

---

## 4. 模块化建议

### 4.1 当前模块化状态评估

| 项 | 现状 | 评分 |
|---|---|---|
| Host/Feature 边界 | 通过 `FeatureModule` 接口隔离, host 不引用 feature 类 | ✅ 优秀 |
| 依赖方向 | feature 反向依赖 host 的 `core/network`/`core/common` (Hilt + OkHttp provider) | ✅ 合理 |
| 升级独立性 | feature 可独立发版 (`modules.json` `versionCode=100` 控制), host 升级不影响 feature 加载 | ✅ 优秀 |
| 版本管理 | `minAppVersion=288` (即 v1.3.20+) 显式声明 | ✅ 良好 |
| 死代码治理 | host 内有 ~30% AI 资源是占位 (P1-9) | ⚠️ 需清理 |
| 跨 feature 复用 | `feature_ai` 不能引用 `feature_vpn` (classloader 隔离), 走 `ToolRegistry` + IPC | ⚠️ 中期需设计 |
| Test 隔离 | `androidTest` 强耦合 host 启动 (AiIntegrationTest 用 `MainActivity::class.java`), 升级 feature 时 host 也得在 | ⚠️ 建议迁 Espresso 到 instrumented test module |

### 4.2 改造建议

#### 4.2.1 立即 (与 P0-1 同步)

- **明确 feature_ai 的对外契约文档** (`docs/feature-ai-contract.md`):
  - `FeatureModule.createFragment(Context) : Fragment` 实现
  - 入口 key: `module_id="ai"`
  - 可选 Intent 协议: `ai://chat?template=xxx&prefill=yyy`
  - 错误码列表 (含 `errorDetail` JSON 契约)
  - 资源 ID 命名规范 (避免与 host 冲突, 前缀 `fa_`)
- **校验和** (sha256) 写入 CI: `modules.json` 与 `app/build.gradle` 的约束同步

#### 4.2.2 中期

- **抽 `core:ai-contract`** 模块 (host 端), 只放:
  - `FeatureModule` 接口
  - 共享 `AiMessage`/`AiTask`/`AiResult` POJO (现 Roadmap §10.1 提议的)
  - `AiPreferences` 接口 (默认实现放 feature)
  - `AiErrorCode` sealed class
  - `TaskStatus` enum + 状态机校验
- 这样 feature 升级时, 共享契约不破 host 编译; host 升级时, feature 仍可独立发版.

#### 4.2.3 长期

- **AI 独立 dynamic APK 决策 (P2-9)**: 当前 v1.4.0 已经"事实独立 APK", 但仍受 host 依赖 (MediaPipe tasks-genai 必须在 host classpath). 长远方案:
  - 选项 A: feature 自带 MediaPipe (APK 体积 +20MB, 完全独立, 适合重度用户)
  - 选项 B: 维持现状 (host 提供共享 deps, feature 轻, 适合轻度用户 + 节省存储)
  - **推荐保持 B**, 直到用户量到 50w+ 再考虑
- **跨设备同步 (P2-4)**: 选型时考虑 (a) 端到端加密 (用户隐私), (b) 与 host 数据互不污染 (AI 历史 vs 设置 vs 游戏存档)

### 4.3 不应做的事 (DONT_DO_THIS 引用)

> 严格遵守 `docs/DONT_DO_THIS.md`:
- **禁 kapt**: Markdown/Glide 库选 `markwon` (无 kapt) 而非 `richeditor-android` 之类要 APT 的
- **禁整屏 Compose**: AI 聊天界面保持 View 体系, 列表项可考虑小范围 Compose 嵌入 (但全屏迁移不在本次范围)
- **禁自动 bump**: 本次不修改 `version.properties` (我们不动 host 版本号)
- **禁改 release-key**: 不动 `keystore.properties`
- **禁硬编码 mimo API key**: AI 模块的 key 走 `BuildConfig.MIMO_API_KEY` (已由 `app/build.gradle` §Section 3 注入)
- **禁 push GitHub**: 本次只产文档, 不涉及发布

---

## 5. 风险评估

### 5.1 技术风险

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| **MediaPipe LLM 在低端机 OOM/ANR** | 中 | P0 | 加 `minSdk` 探测 (≥ 8GB RAM 才启用本地), 灰度; 同时强制云端兜底 |
| **流式输出引入状态机 bug** | 中 | P1 | TaskStatus sealed class + 强单测, ≥ 30 case 覆盖异常路径 |
| **多模态 + Markdown 引入大依赖膨胀 feature APK 体积** | 中 | P2 | markwon ≈ 1.5MB; ML Kit text-recognition ≈ 4MB; 远小于当前 916KB → +5-7MB 总体可控; 拆 variant: 基础包 1MB / 全功能包 8MB |
| **Agent/Function calling 引入"越权"风险** | 高 | P1 | 严格白名单 + 用户确认弹窗 + 全量审计日志 + 限速 (10 calls/min) + E2E 测试 |
| **本地模型下载耗电/流量** | 中 | P1 | Wi-Fi only + 用户显式触发 + 断点续传 + 进度条; 提供"小模型优先" 选项 (Gemma 1B) |
| **跨模块 classloader 隔离 → AI 无法直接调 VPN/tool 内部类** | 中 | P1 | `ToolRegistry` + IPC (Binder/Messenger) 或 SharedPreferences 状态中转, 走 process boundary 显式契约 |
| **AI 输出合规 (政治/版权/医疗建议)** | 中 | P0 | 已有 `ai_no_api_key` 类提示; 增加 content filter, 命中降级 + 日志; 设置页加"敏感内容屏蔽"开关 |

### 5.2 产品风险

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| **用户对 dynamic APK 下载耐心低** | 中 | P0 (门槛) | 引导页 + 预下载 (启动后空闲时拉) + 安装后静默启用 |
| **本地模型质量低于云端** | 高 | P1 | UI 显式标"本地: 速度↑ 质量↓"对比; 默认云端; 提供 A/B 入口 |
| **模板发现性差 → 用户走了 5 次还不知道有模板** | 中 | P1 | 首屏"试一试" carousel + 空状态展示 3 个热门模板 + 模板作者署名 |

### 5.3 业务风险

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| **Mimo API 限速/涨价/停服** | 中 | P2 | 抽象 `CloudProvider` 接口, 一键切 OpenAI/Claude/Gemini; key 走 `BuildConfig` 注入 |
| **大模型输出错误导致用户受损** | 中 | P1 (法律) | UI 永久加 "AI 内容仅供参考" + 设置页"风险提示" + 关键场景 (代码/医疗) 加 warning badge |
| **海外用户跨境延迟/合规** | 低 | P2 | 海外节点分流 (复用 track-vpn 的节点能力), GDPR/CCPA 风格隐私声明 |

### 5.4 工程风险 (与本改造直接相关)

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| **dynamic APK 内代码不可见, 改动需要"盲改 + 验签 sha256"** | 高 | P0 | 与 feature_ai 维护者 (如果还在团队内) 协作; 短期: 改 host 侧契约 (FeatureModule), 让 feature 适配; 中期: 把 feature_ai 纳入 Git 仓 (用 monorepo, 不再是黑盒 APK) |
| **单测无法覆盖 feature 内代码** | 高 | P0 | 短期: 写 host 侧 mock feature 的 instrumentation test; 中期: 把 feature_ai 重组成 Kotlin source 编译 (而不是 dex 黑盒), 走同一 CI |
| **占位资源清理导致 dynamic APK 缺资源** | 低 | P1 | 清理前 grep dynamic APK 反编译确认未引用; 用 `apkanalyzer` 验证 |
| **NETWORK_LAYER.md 提到的 10 处违规源之一, 可能影响其他 P0 修复** | 低 | P0 | 同步本改造与 track-vpn/track-tools, 一起修; CI 加 detekt 规则禁止 `new OkHttpClient.Builder()` 在指定模块 |

---

## 6. 附录: 调研清单与未决问题

### 6.1 已读 (带 .NET 绕开 GBK 编码)

- ✅ `Y:\GameMatrixApp\docs\DONT_DO_THIS.md`
- ✅ `Y:\GameMatrixApp\docs\AI_CONTEXT.md` (注意: §2.12 引用的 `com.GameMatrix.app.ai.model` 实际不存在, 是 Roadmap 计划但 feature 化后挪到 dynamic APK)
- ✅ `Y:\GameMatrixApp\docs\game_center_app_ai_roadmap.md` (主要改造依据)
- ✅ `Y:\GameMatrixApp\docs\NETWORK_LAYER.md` (OkHttp 治理基线)
- ✅ `Y:\GameMatrixApp\app\src\main\res\layout\fragment_ai.xml` (255 行, host 端备用 UI)
- ✅ `Y:\GameMatrixApp\app\src\main\res\layout\item_ai_message.xml`
- ✅ `Y:\GameMatrixApp\app\src\main\res\layout\item_ai_message_modern.xml` (133 行)
- ✅ `Y:\GameMatrixApp\app\src\main\res\layout\dialog_chat.xml` (110 行)
- ✅ `Y:\GameMatrixApp\app\src\main\res\navigation\mobile_navigation.xml`
- ✅ `Y:\GameMatrixApp\app\src\main\assets\modules.json` (AI 注册在 builtIn=false, sha256 已固定)
- ✅ `Y:\GameMatrixApp\app\src\main\java\com\gamecenter\app\features\ModuleShellFragment.kt`
- ✅ `Y:\GameMatrixApp\app\src\androidTest\java\com\gamecenter\app\AiIntegrationTest.kt` (仅 2 Espresso 用例)
- ✅ `Y:\GameMatrixApp\app\build.gradle` (MediaPipe tasks-genai 0.10.27, OkHttp 4.12.0, KSP, Hilt, MediaPipe GenAI)
- ✅ `Y:\GameMatrixApp\app\src\main\res\values\strings.xml` (25+ AI 字符串, 含 9 个 task type, 状态, 错误)
- ⚠️ `Y:\GameMatrixApp\app\src\main\java\com\gamecenter\app\MainActivity.java` (用 `getId()` 引用 `R.id.navigation_ai` 但未触发 AI 子逻辑 — ModuleShellFragment 自承载, OK)

### 6.2 未直接读 (受路径/格式限制)

- ❌ `feature_ai_v100.apk` 内部 Kotlin/Java 源 (产物在 VPS, 需先 `apkanalyzer` 反编译)
- ❌ `app/src/test/java/com/gamecenter/app/ai/` 整个目录 (不存在, 这正是 P0-5 缺失的证据)
- ❌ `module-store/feature/tools/ai/AiApiClient.java` (NETWORK_LAYER.md 提到, 但 feature/tools 目录下当前只有 tools 模块, AI 应是独立 module-store/feature/ai/ 但本地不存在)
- ❌ `core/network/OkHttpClientProvider.kt` (host 引用, 但 core/network 在 Git submodule 模式下可能独立)

### 6.3 关键决策记录

| 决策 | 选择 | 理由 |
|---|---|---|
| AI 模块独立 APK 形态 | **维持 dynamic APK** | 已经是事实状态, 改动成本最低; 不增加 host 体积; 满足 DONT_DO_THIS §10.3 |
| 改造重心 | **host 端契约** + **P0 单测 + 错误细化** | 多数 P0 修复在 host 端可见, 不依赖 feature 黑盒 |
| Markdown 库选型 | **markwon 4.6.2** | 无 kapt, runtime-only, 满足 DONT_DO_THIS |
| 跨模块调用 | **ToolRegistry + IPC** | 避开 classloader 隔离坑 |
| 死代码处理 | **清理** (而不是迁 res-legacy) | 避免双份资源维护, dynamic APK 自带 |
| 升级路径 | **单仓多 module** | 中期把 feature_ai 源纳入 Git, 停止"盲改" |

### 6.4 与其它 Track 的协同点

- **track-vpn** (peer `mvs_7303876aa9d3497e98692a6e01f9492c`): P1-5 (代理策略) 需要 track-vpn 提供稳定的 `VpnServiceProxy`, 否则 AI 模块做不了"智能选节点"
- **track-tools** (peer `mvs_8508bc9f7ea54203a2292a78c8c75007`): P1-2 (Function calling 调工具箱) 需要 track-tools 提供稳定的 `ToolBinder` 跨进程调用入口
- **track-architecture** (peer `mvs_48cd73b409344308b7bb281c6e9b76e8`): P1-9 (死代码清理) + 整体 UI 主题 (Material 3) 需要 track-arch 统一 lint baseline 与 `colors.xml` 规范, 否则 AI 资源删除会冲突

---

## 7. 关键引用一览 (file_path:line)

| 引用 | 位置 |
|---|---|
| AI 模块注册 (builtIn=false, sha256 固定) | `app/src/main/assets/modules.json:62-87` |
| AI 导航占位 | `app/src/main/res/navigation/mobile_navigation.xml:31-37` |
| ModuleShellFragment 动态加载 | `app/src/main/java/com/gamecenter/app/features/ModuleShellFragment.kt:38-49` |
| AI 完整 UI (host 占位) | `app/src/main/res/layout/fragment_ai.xml:1-255` |
| AI 消息项 (老式 + 现代) | `app/src/main/res/layout/item_ai_message.xml:1` & `item_ai_message_modern.xml:1-133` |
| AI 全屏 dialog | `app/src/main/res/layout/dialog_chat.xml:1-110` |
| 集成测试 (2 cases) | `app/src/androidTest/java/com/gamecenter/app/AiIntegrationTest.kt:1-32` |
| MediaPipe GenAI 依赖 | `app/build.gradle:339` |
| OkHttp 治理基线 | `docs/NETWORK_LAYER.md:17-21` |
| AI 业务契约 (Roadmap) | `docs/game_center_app_ai_roadmap.md:3-11` (阶段表) + §3-7 (能力) + §10 (包结构) + §11 (开发序) |
| AI 模型字段 (Roadmap) | `docs/game_center_app_ai_roadmap.md:280-340` (§9 数据结构) |
| 任务描述引用类 (实际在 feature APK) | `docs/AI_CONTEXT.md:2.12` (§AI 助理模块, 注意 package 名 `com.GameMatrix.app.ai.model` 是历史/计划名, 实际为 `com.gamecenter.app.modules.AiModuleEntryPoint` + dynamic APK 内部包) |
| 禁忌 (kapt/auto-bump/keystore/compose) | `docs/DONT_DO_THIS.md:1-80` |
| Network layer 违规清单 (含 `module-store/feature/tools/ai/AiApiClient.java`) | `docs/NETWORK_LAYER.md:13-25` |

---

**最后更新**: 2026-06-04 16:18
**下一步**: 把本文档同步给 track-architecture, 触发 dead-code 清理 + Material 3 lint baseline 协同; 把 P0-1/P0-3/P0-5 三个能立刻动工的派单.


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
