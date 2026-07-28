# GameMatrixApp 1-28 条建议详细执行计划

> **状态**：执行计划任务拆解
> **最后更新**：2026-07-27
> **配套文档**：[`EXECUTION_PLAN_2026H2.md`](EXECUTION_PLAN_2026H2.md)（阶段与节奏）、[`PRODUCT_DIRECTION_AND_UX.md`](PRODUCT_DIRECTION_AND_UX.md)（产品方向）、[`CURRENT_STATE.md`](CURRENT_STATE.md)（当前事实）
> **事实来源**：当前实现与 `CURRENT_STATE.md`；文档治理见 [`DOCUMENTATION_GOVERNANCE.md`](DOCUMENTATION_GOVERNANCE.md)。

本文把第 1–28 条建议拆成可执行任务。每条含：子任务、依赖、验收标准、涉及文件、工作量估算（人天）。工作量仅供排期参考，不含缓冲。

---

## 0. 建议清单与阶段映射

| # | 建议 | 阶段 | 优先级 | 依赖 | 状态 |
|---|---|---|---|---|---|
| 1 | 平台定位与北极星 | guardrail | — | — | 已落地，持续维护 |
| 2 | 初始安装包策略（200 MB 预算原则） | guardrail | — | — | 已落地，持续维护 |
| 3 | 不做行为追踪/广告/强制连胜 | guardrail | — | — | 已落地，持续维护 |
| 4 | 200 MB 预算分配 | P0 | P0 | release 体积测量 | 进行中 |
| 5 | 以用户任务完成衡量质量 | guardrail | — | — | 已落地，持续维护 |
| 6 | 预装/按需边界 | P0 | P0 | 4 | 进行中 |
| 7 | 意图驱动 onboarding | P2 | P1 | 10, 11 | 待启动 |
| 8 | 首页聚焦 | P2 | P1 | 7 | 待启动 |
| 9 | 底部导航服务意图 | P2 | P1 | 7 | 待启动 |
| 10 | 按结果组织模块 | P1 | P0 | 12 | 代码完成，待 #10.5 真机验证 |
| 11 | 模块详情必填 + 隐私卡 | P1 | P0 | 10 | 代码完成，待 #11.6 真机验证 |
| 12 | 修复发布阻塞 | P0 | P0 | — | 进行中 |
| 13 | 模块认证清单 | P4 | P2 | 11, 12 | 待启动 |
| 14 | 跨模块平台契约 | P2 | P1 | 11 | 待启动 |
| 15 | 统一打开方式接入 | P2 | P1 | 14 | 待启动 |
| 16 | AI 上下文动作入口 | P3 | P1 | 15, 24 | 待启动 |
| 17 | AI 调用统一披露 | P3 | P0 | 24 | 待启动 |
| 18 | 结果作为可编辑草稿 | P3 | P1 | 16 | 待启动 |
| 19 | 挑战改为可选旅程 | P4 | P2 | 14 | 待启动 |
| 20 | 回放复盘强化解释 | P4 | P2 | 15 | 待启动 |
| 21 | 排行榜优先个人/可控范围 | P4 | P2 | — | 待启动 |
| 22 | 难度建议可解释可调整 | P4 | P2 | — | 待启动 |
| 23 | 数据与连接中心 v1 | P1 | P1 | 11 | 代码完成，待 #23.6 真机验证 |
| 24 | 云端调用前明示 | P1 | P0 | 11, 23 | 待启动 |
| 25 | WebDAV 同步成熟 | P1 | P1 | 独立，可并行 P0 | 待启动 |
| 26 | 文档事实层 | guardrail | — | — | 已落地 |
| 27 | 语言边界 | guardrail | — | — | 已落地 |
| 28 | 工程事实 | guardrail | — | — | 已落地 |

**关键路径**：#12 → #10 → #11 → #24 → #17 → #16。P0 签名/Catalog 修复是后续一切模块商店体验的前置。

**总工作量估算**：约 120–150 人天（不含 guardrail 持续维护），分布：
- P0：12–15 人天
- P1：30–40 人天
- P2：35–45 人天
- P3：18–25 人天
- P4：25–35 人天

---

## P0 — 地基与解锁（12–15 人天）

> **目标**：验证模块商店端到端可信；用 200 MB 预算余量决定预装范围。
> **前置**：无
> **当前状态**：签名同源已核验（`D0:58:A1:8F:...:24:D2:ED:DC`）；chinesechess 签名块与接线已修复；VPN Release 产物与双 Catalog 字段一致性尚未闭环。

### #12 修复发布阻塞（8–10 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 12.1 | 构建 `vpn-release.apk`，用 `gamecenter.jks` 签名 | `module-store/feature/tools/vpn/build.gradle`、`keystore.properties` | 1d | `apksigner verify --print-certs vpn-release.apk` 证书 SHA256 与宿主一致 |
| 12.2 | 将 `vpn-release.apk` 放入 `app/src/main/assets/modules/` 并更新 `modules.json` / `catalog.json` 的 `fileSize` / `sha256` | `app/src/main/assets/modules.json`、`app/src/main/assets/catalog.json`、`app/src/main/assets/modules/vpn-release.apk` | 0.5d | 两个 Catalog 的 VPN 条目字段一致且与实际 APK 匹配 |
| 12.3 | 收敛双 Catalog 中 `tools` 的顶层 `fileSize` / `sha256` 与嵌套 `package` 元数据 | `app/src/main/assets/modules.json`、`app/src/main/assets/catalog.json` | 1d | 字段级 diff 显示两个 Catalog 的 `tools` 条目完全一致 |
| 12.4 | 真机端到端：5 个可下载模块（tools/ai/wrongbook/tts_voice/vpn）完成 下载 → SHA-256 → APK signer → 安装 → 打开 → 更新 → 失败回滚 | 真机 + logcat | 2d | 5 模块全链路通过，logcat 无 `FATAL EXCEPTION` |
| 12.5 | 文档化发布闭环结果到 `CURRENT_STATE.md` 的"当前发布门槛" | `docs/CURRENT_STATE.md` | 0.5d | 发布门槛 1-3 项标记为已核验，含日期与证据 |

**依赖**：无
**风险**：VPN Release 构建可能因 sing-box AAR / minSdk 兼容性失败 → 准备 v2rayNG fallback 或暂留桩实现但用 Release 签名。

### #4 + #6 200 MB 预算分配（4–5 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 4.1 | 测量当前 release APK 精确体积与各模块 APK 体积 | `app/build/outputs/apk/release/`、`module-store/*/build/outputs/` | 0.5d | 体积表记录到 `CURRENT_STATE.md` |
| 4.2 | 评估候选预装模块（基础工具箱、代表性游戏）的体积、离线价值、首启成功率 | `module-store/feature/tools/tools/`、游戏模块 | 1d | 候选清单含体积/价值/风险评估 |
| 6.1 | 决定首包预装清单（目标 120–150 MB，保留 ≥50 MB 余量） | `app/build.gradle`（`sourceSets.assets`）、`modules.json` 的 `builtIn` 字段 | 1d | 预装清单文档化，每项有预装理由 |
| 6.2 | 调整 `modules.json` 的 `builtIn` 字段与 `app/build.gradle` 的 assets 打包 | `app/src/main/assets/modules.json`、`app/build.gradle` | 1d | 预装模块随 APK 打包，按需模块保持下载 |
| 6.3 | 真机验证预装模块首次打开成功率 | 真机 | 0.5d | 5 类意图（玩/学/浏览/创作/连接）各有 ≥1 个预装可完成入口 |

**依赖**：#12 完成（签名/Catalog 稳定后才能定预装清单）
**风险**：预装过多导致首包超 150 MB → 严格按价值/体积比排序，保留余量。

### #26–28 持续 guardrail（0 人天，已落地）

文档事实层、语言边界、工程事实作为持续维护，不新增立项。每次阶段完成后更新 `CURRENT_STATE.md`。

---

## P1 — 商店体验与隐私卡（30–40 人天）

> **目标**：安装前可理解；任何云端调用可信任、可取消。
> **前置**：P0 完成（#12 签名/Catalog 稳定）

### #10 按结果组织模块（5–7 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 10.1 | 在 Catalog schema 中新增 `storeCategory` 字段，枚举：娱乐与对战/学习与整理/阅读与浏览/文本与创作/设备与网络/个性化 | `app/src/main/assets/catalog_v2.schema.json`、`CatalogModels.kt`、`CatalogV2Parser.kt` | 1.5d | schema 校验通过，6 类枚举可用 |
| 10.2 | 为现有 34 个模块填充 `storeCategory` | `app/src/main/assets/modules.json`、`app/src/main/assets/catalog.json` | 1d | 34 模块均有 `storeCategory`，分类合理 |
| 10.3 | Flutter 商店 UI 按 `storeCategory` 分组展示，隐藏技术交付类型（APK/ZIP/Web/Asset/Unity/Flutter） | `flutter_module/lib/`、`ModuleStoreActivity.kt` | 2d | 用户看到的分组是结果类别，不是技术类型 |
| 10.4 | 旧原生商店（`ModuleStoreActivity`）同步分组逻辑 | `app/src/main/java/com/gamecenter/app/modules/ModuleStoreActivity.kt` | 1.5d | 旧商店与 Flutter 商店分组一致 |
| 10.5 | 真机验证：用户能在 ≤3 步内找到目标分类的模块 | 真机 | 0.5d | 5 类分类各有 ≥3 模块可被找到 |

**依赖**：#12 完成

### #11 模块详情必填 + 隐私卡（8–10 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 11.1 | 定义模块详情数据模型：价值描述、受众、体积、权限、离线能力、兼容性、更新/卸载影响 | `core/common/.../ModuleDetail.kt`（新建）、`CatalogModels.kt` | 1.5d | 数据模型覆盖 7 项必填字段 |
| 11.2 | 定义隐私卡数据模型：本地数据、云端数据、网络域、同步位置、保存期限、删除方式 | `core/common/.../PrivacyCard.kt`（新建） | 1d | 隐私卡模型覆盖 6 项字段，与 #23 共用 |
| 11.3 | 在 Catalog schema 中扩展 `details` 与 `privacy` 字段 | `catalog_v2.schema.json`、`CatalogV2Parser.kt` | 1.5d | schema 校验通过，字段可选但建议填写 |
| 11.4 | 为 34 个模块填充详情与隐私卡（至少为核心模块：browser/ai/tools/wrongbook/vpn/tts） | `modules.json`、`catalog.json` | 2d | 核心模块详情完整，其余模块有基础信息 |
| 11.5 | Flutter 商店详情页 UI：展示详情 + 隐私卡 | `flutter_module/lib/` | 2d | 详情页含全部必填字段与隐私卡区块 |
| 11.6 | 真机验证：用户安装前能看到全部必填信息与隐私卡 | 真机 | 0.5d | 详情页信息完整可读 |

**依赖**：#10 完成（分类体系确定后详情才有归属）

### #23 数据与连接中心 v1（6–8 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 23.1 | 定义 `DataConnectionCenter` 数据模型：已授权权限、已启用联网模块、最近同步、下载记录、缓存/本地数据 | `core/common/.../DataConnectionCenter.kt`（新建） | 1d | 数据模型覆盖 6 项 |
| 23.2 | 实现数据聚合：从 `PackageManager` / `ModuleManager` / `CloudSyncManager` / `DownloadManager` 收集状态 | `app/src/main/kotlin/com/gamecenter/app/settings/DataConnectionCenterProvider.kt`（新建） | 2d | 聚合数据正确反映当前状态 |
| 23.3 | UI：数据与连接中心页面（设置入口） | `app/src/main/java/com/gamecenter/app/settings/DataConnectionCenterActivity.kt`（新建）或 Compose | 2d | 页面展示 6 项数据，支持一键导出/删除 |
| 23.4 | 一键导出：导出用户数据为 JSON/ZIP | `DataConnectionCenterProvider.kt` | 1d | 导出文件包含可读的用户数据 |
| 23.5 | 一键删除：删除缓存/本地数据（不删除模块本身） | `DataConnectionCenterProvider.kt` | 1d | 删除后缓存清空，模块仍可使用 |
| 23.6 | 真机验证：数据准确、导出/删除可用 | 真机 | 0.5d | 数据与实际状态一致 |

**依赖**：#11 完成（隐私卡数据模型共用）

### #24 云端调用前明示（5–7 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 24.1 | 定义统一 `ConsentComponent`：发送什么、为什么、可否本地、费用/网络、如何取消 | `core/common/.../ConsentComponent.kt`（新建） | 1.5d | 组件支持 5 项必填信息 |
| 24.2 | 实现 `ConsentDialog`：操作前弹窗，支持同意/拒绝/改用本地 | `app/src/main/java/com/gamecenter/app/ui/ConsentDialog.kt`（新建） | 1.5d | 弹窗 UI 清晰，三选项可用 |
| 24.3 | AI 模块接入 consent：AI 调用前显示数据来源/模型/费用/取消 | `module-store/feature/tools/ai/` | 1d | AI 调用前有 consent |
| 24.4 | OCR（错题本）接入 consent：图片上传前显示去向/可否本地 | `module-store/feature/tools/wrongbook/`、`server/wrongbook-service/` | 1d | OCR 前有 consent |
| 24.5 | WebDAV 同步接入 consent：同步前显示发送内容/取消 | `app/src/main/kotlin/com/gamecenter/app/cloudsync/` | 1d | 同步前有 consent |
| 24.6 | 真机验证：任意云端调用有可取消的 consent，且可改用本地路径 | 真机 | 0.5d | 3 类调用（AI/OCR/同步）均有 consent |

**依赖**：#11, #23 完成

### #25 WebDAV 同步成熟（6–8 人天，可并行 P0）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 25.1 | 初次配置引导：3 步内完成 WebDAV 配置（URL/用户名/密码/测试） | `app/src/main/kotlin/com/gamecenter/app/cloudsync/CloudSyncSetupActivity.kt`（新建或扩展） | 1.5d | 首次配置 ≤3 步 |
| 25.2 | 连接测试：配置时立即测试连接并反馈结果 | `CloudSyncManager.kt` | 1d | 测试结果可读（成功/失败原因） |
| 25.3 | 最近同步结果展示：时间、状态、冲突数 | `CloudSyncManager.kt`、设置页 UI | 1d | 用户能看到最近同步状态 |
| 25.4 | 可读冲突选择：冲突时显示两版本，用户选择保留哪个 | `CloudSyncManager.kt`、`ConflictResolutionDialog.kt`（新建） | 2d | 冲突可读、可选择 |
| 25.5 | 凭据加密：替换当前 Base64 为 `EncryptedSharedPreferences` + Android Keystore | `CloudSyncManager.kt`、`app/src/main/java/com/gamecenter/app/security/CredentialVault.kt`（新建或扩展） | 1.5d | 凭据不再明文存储 |
| 25.6 | 本地备份兜底：同步失败时自动保存本地备份 | `CloudSyncManager.kt` | 1d | 同步失败后本地有备份 |
| 25.7 | 真机验证：首次配置 ≤3 步，冲突可读可恢复 | 真机 | 0.5d | 端到端验证通过 |

**依赖**：独立，可并行 P0

---

## P2 — 首次体验与跨模块连续性（35–45 人天）

> **目标**：新用户 60 秒内进入目标能力；任务可跨模块流转。
> **前置**：P1 完成（#10 分类、#11 详情/隐私卡）

### #7 意图驱动 onboarding（6–8 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 7.1 | 定义意图枚举：玩游戏/学习与整理/浏览与阅读/AI 辅助/常用工具/网络连接 | `core/common/.../UserIntent.kt`（新建） | 0.5d | 6 类意图定义完成 |
| 7.2 | 实现 onboarding 页面：首次启动展示意图选择，可跳过 | `app/src/main/java/com/gamecenter/app/onboarding/IntentOnboardingActivity.kt`（新建） | 2d | 首次启动显示，可跳过 |
| 7.3 | 每个意图给出 1–3 个"起始能力"，明确哪些已可用、哪些需下载 | `IntentOnboardingActivity.kt`、`modules.json` 的 `storeCategory` | 1.5d | 6 类意图各有起始能力清单 |
| 7.4 | 意图映射到底部导航排序与首页推荐 | `MainActivity.kt`、`BottomNavigationManager`、`GamesFragment` | 2d | 选择意图后导航与首页跟随调整 |
| 7.5 | 设置页提供"恢复默认导航" | `app/src/main/java/com/gamecenter/app/settings/` | 1d | 用户可随时恢复默认 |
| 7.6 | 真机验证：新用户 60 秒内进入目标能力 | 真机 | 0.5d | 首次会话进入目标能力比例可测 |

**依赖**：#10, #11 完成

### #8 首页聚焦（5–7 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 8.1 | 首页改版：从"全功能控制台"转为"继续上次任务 + 符合意图的下一步 + 可管理的模块入口" | `GamesFragment.java`、`app/src/main/res/layout/fragment_games.xml` | 3d | 首页三段式布局 |
| 8.2 | "继续上次任务"：记录最近打开的模块/游戏，首页展示快捷入口 | `RecentTaskProvider.kt`（新建）、`SharedPreferences` | 1.5d | 首页显示最近任务 |
| 8.3 | 模块管理入口移到次页 | `GamesFragment.java`、导航配置 | 0.5d | 首页不再堆叠全部入口 |
| 8.4 | 真机验证：首页聚焦后首次会话进入目标能力比例提升 | 真机 | 0.5d | 比例可测 |

**依赖**：#7 完成

### #9 底部导航服务意图（4–5 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 9.1 | 扩展现有排序/隐藏（最大 6 项，游戏大厅强制保留）为"意图预设" | `BottomNavigationManager`、`MainActivity.kt` | 2d | 意图预设可一键应用 |
| 9.2 | 预设方案：每类意图对应一个导航配置（如"学习"意图突出错题本/工具） | `core/common/.../NavigationPresets.kt`（新建） | 1.5d | 6 类意图各有预设 |
| 9.3 | 真机验证：一键应用导航配置后入口符合意图 | 真机 | 0.5d | 预设应用后导航合理 |

**依赖**：#7 完成

### #14 跨模块平台契约（8–10 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 14.1 | 定义跨模块能力接口：`RecentProvider`、`FavoriteProvider`、`CollectionProvider`、`OpenWithProvider`、`ExportProvider`、`BackupProvider` | `core/common/.../crossmodule/`（新建包） | 2d | 6 个接口定义完成，小而稳 |
| 14.2 | 实现 `RecentProvider`：统一最近打开/完成/导入记录 | `app/src/main/kotlin/com/gamecenter/app/crossmodule/RecentProviderImpl.kt`（新建） | 1.5d | 最近记录按意图展示 |
| 14.3 | 实现 `FavoriteProvider` + `CollectionProvider`：游戏/网页/AI 结果/错题/工具预设可保存到命名集合 | `FavoriteProviderImpl.kt`、`CollectionProviderImpl.kt`（新建） | 2d | 收藏与集合可用 |
| 14.4 | 实现 `ExportProvider` + `BackupProvider`：统一导出与备份入口 | `ExportProviderImpl.kt`、`BackupProviderImpl.kt`（新建） | 1.5d | 导出/备份可指定格式 |
| 14.5 | 实现 `OpenWithProvider`：统一"打开方式"入口 | `OpenWithProviderImpl.kt`（新建） | 1.5d | 打开方式可路由到目标模块 |
| 14.6 | 真机验证：3 条跨模块链路可用 | 真机 | 0.5d | 网页→阅读、图片→OCR、游戏→回放 至少可用 |

**依赖**：#11 完成

### #15 统一打开方式接入（6–8 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 15.1 | 网页 → 阅读/翻译/摘要/保存到知识库 | `OpenWithProviderImpl.kt`、`browser/`、AI 模块 | 1.5d | 网页内容可进入 4 种目标 |
| 15.2 | 图片 → OCR/整理/复习卡/导出笔记 | `OpenWithProviderImpl.kt`、`wrongbook/` | 1.5d | 图片可进入 4 种目标 |
| 15.3 | 错题 → 识别/归类/解释/安排复习 | `OpenWithProviderImpl.kt`、`wrongbook/` | 1d | 错题可进入 4 种目标 |
| 15.4 | 游戏 → 复盘/提示/难度建议/可分享战绩 | `OpenWithProviderImpl.kt`、`games/` | 1.5d | 游戏结果可进入 4 种目标 |
| 15.5 | 工具 → 解释结果/下一步建议 | `OpenWithProviderImpl.kt`、`tools/`、AI 模块 | 1d | 工具结果可进入 2 种目标 |
| 15.6 | 真机验证：至少 3 条链路端到端可用 | 真机 | 0.5d | 3 条链路通过 |

**依赖**：#14 完成

---

## P3 — AI 作为上下文协作（18–25 人天）

> **目标**：AI 在用户已有内容和意图上减少操作，而非新开聊天页。
> **前置**：P1 的 #24 consent 组件 + P2 的 #15 打开方式契约

### #16 AI 上下文动作入口（8–10 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 16.1 | 在网页/图片/错题/游戏/工具的结果处提供 explain/summarize/OCR/translate/review/assist 动作入口 | `OpenWithProviderImpl.kt`、各模块 UI | 3d | 5 类结果处有 AI 动作入口 |
| 16.2 | AI 动作调用 `ConsentComponent` 后执行 | `ConsentComponent.kt`、AI 模块 | 1.5d | 调用前有 consent |
| 16.3 | AI 结果不自动执行，作为草稿展示 | `AiDraftViewModel.kt`（新建） | 2d | 结果以草稿形式展示 |
| 16.4 | 真机验证：至少 3 个上下文 AI 动作可用且可取消 | 真机 | 1d | 3 个动作端到端通过 |

**依赖**：#15, #24 完成

### #17 AI 调用统一披露（4–5 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 17.1 | 复用 P1 的 `ConsentComponent`，扩展 AI 专属字段：模型、本地/云端、费用、网络/隐私、结果去向 | `ConsentComponent.kt` | 1.5d | AI 专属字段完整 |
| 17.2 | AI 调用前显示完整披露 | AI 模块 | 1.5d | 披露信息完整可读 |
| 17.3 | 真机验证：AI 调用有完整披露且可取消 | 真机 | 0.5d | 披露与取消可用 |
| 17.4 | 文档化 AI 披露标准 | `docs/AI_DISCLOSURE.md`（新建） | 1d | 标准文档化 |

**依赖**：#24 完成

### #18 结果作为可编辑草稿（6–8 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 18.1 | AI 结果草稿编辑器：可编辑文本/结构化内容 | `AiDraftEditorActivity.kt`（新建）或 Compose | 2d | 草稿可编辑 |
| 18.2 | 草稿导出：导出为文本/图片/PDF | `AiDraftEditorActivity.kt` | 1.5d | 导出可用 |
| 18.3 | 草稿保存到对应模块：错题/收藏/笔记 | `AiDraftViewModel.kt`、`FavoriteProvider`、`wrongbook/` | 2d | 草稿可保存到 ≥3 种目标 |
| 18.4 | 真机验证：AI 结果可编辑、可导出、可保存 | 真机 | 0.5d | 端到端通过 |

**依赖**：#16 完成

---

## P4 — 健康连续性与模块生态（25–35 人天）

> **目标**：进度感不靠压力；新模块接入可预测。
> **前置**：P1 的 #11 详情/隐私卡 + P0 的 #12 签名稳定

### #19 挑战改为可选旅程（5–6 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 19.1 | 将每日挑战/连胜改为"本周目标"或"个人旅程"，可暂停/替换/关闭 | `app/src/main/java/com/gamecenter/app/games/`（挑战相关） | 2d | 挑战可暂停/替换/关闭 |
| 19.2 | 暂停后不影响核心功能使用 | 各游戏 Activity | 1d | 关闭挑战后游戏正常可用 |
| 19.3 | 通知只服务未完成任务（下载完成/同步冲突/复习到期/回放生成） | `NotificationHelper.kt` | 1.5d | 无签到/强制连胜/营销提醒 |
| 19.4 | 真机验证：关闭挑战后仍能正常使用核心功能 | 真机 | 0.5d | 核心功能不受影响 |

**依赖**：#14 完成（旅程数据走跨模块契约）

### #20 回放复盘强化解释（5–6 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 20.1 | 棋类复盘增加"好棋/失误/漏算"标注 | `GameReviewAnalyzer`、`app/src/main/java/com/gamecenter/app/games/`（回放相关） | 2.5d | 复盘有标注 |
| 20.2 | 标注解释：为什么好/为什么失误/漏算什么 | `GameReviewAnalyzer` | 1.5d | 解释可读 |
| 20.3 | 真机验证：复盘后用户理解"哪里可以更好" | 真机 | 0.5d | 标注与解释可用 |
| 20.4 | 复盘后可"再玩一局"应用建议 | 回放 UI | 1d | 可再玩 |

**依赖**：#15 完成（回放走打开方式契约）

### #21 排行榜优先个人/可控范围（4–5 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 21.1 | 排行榜默认展示个人历史 | `LeaderboardActivity` | 1.5d | 默认个人历史 |
| 21.2 | 可选好友/同设备/明确范围比较 | `LeaderboardActivity` | 2d | 范围可选 |
| 21.3 | 不制造不透明全局竞争 | `LeaderboardActivity` | 0.5d | 无全局强制排名 |
| 21.4 | 真机验证：排行榜默认个人、范围可控 | 真机 | 0.5d | 默认与范围切换可用 |

**依赖**：无

### #22 难度建议可解释可调整（4–5 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 22.1 | 基于 `DifficultyRecommender` 增加解释：为什么推荐这个难度 | `DifficultyRecommender` | 1.5d | 推荐有解释 |
| 22.2 | 永远允许手动选择难度 | 各游戏难度选择 UI | 1d | 手动选择始终可用 |
| 22.3 | 难度调整后不强制退出 | 各游戏 Activity | 1d | 调整后可继续 |
| 22.4 | 真机验证：难度建议可解释、可调整 | 真机 | 0.5d | 建议与调整可用 |

**依赖**：无

### #13 模块认证清单（7–8 人天）

| 子任务 | 描述 | 涉及文件 | 工期 | 验收 |
|---|---|---|---|---|
| 13.1 | 编写模块作者认证清单文档：价值/权限最小化/无障碍/中英文/生命周期/回滚/导航/数据导出 | `docs/modules/MODULE_CERTIFICATION_CHECKLIST.md`（新建） | 2d | 清单文档化 |
| 13.2 | 实现认证清单自动化校验脚本 | `scripts/validate_module_certification.py`（新建） | 2d | 脚本可校验模块 |
| 13.3 | 至少 1 个新模块走完认证流程 | 选一个候选模块 | 2d | 认证流程端到端通过 |
| 13.4 | 文档化认证流程 | `docs/modules/MODULE_CERTIFICATION_PROCESS.md`（新建） | 1d | 流程文档化 |
| 13.5 | 真机验证：认证模块符合清单要求 | 真机 | 0.5d | 认证模块通过 |

**依赖**：#11, #12 完成

---

## 持续 guardrail（#1, #2, #3, #5, #26–28）

以下建议已落地，作为持续维护的 guardrail，不单独立项：

| # | 建议 | 维护方式 |
|---|---|---|
| 1 | 平台定位与北极星 | `CURRENT_STATE.md` 的"产品定位"节持续维护 |
| 2 | 初始安装包策略（200 MB 预算原则） | `CURRENT_STATE.md` 的"初始安装包预算"节持续维护 |
| 3 | 不做行为追踪/广告/强制连胜 | guardrail，任何新功能不得违反 |
| 5 | 以用户任务完成衡量质量 | `EXECUTION_PLAN_2026H2.md` 的"度量"节持续维护 |
| 26 | 文档事实层 | `DOCUMENTATION_GOVERNANCE.md` 持续维护 |
| 27 | 语言边界 | `DOCUMENTATION_GOVERNANCE.md` 持续维护 |
| 28 | 工程事实 | `DOCUMENTATION_GOVERNANCE.md` 持续维护 |

---

## 执行顺序与里程碑

| 里程碑 | 完成建议 | 预计工期 | 验收标志 |
|---|---|---|---|
| M0：P0 地基解锁 | #4, #6, #12, #26-28 | 2–3 周 | 5 模块真机端到端通过，预装清单确定 |
| M1：P1 商店体验 | #10, #11, #23, #24, #25 | 4–6 周 | 详情/隐私卡可用，consent 组件可用 |
| M2：P2 跨模块连续性 | #7, #8, #9, #14, #15 | 6–8 周 | onboarding 可用，3 条跨模块链路通过 |
| M3：P3 AI 上下文 | #16, #17, #18 | 4–6 周 | 3 个 AI 上下文动作可用 |
| M4：P4 健康连续性 | #13, #19, #20, #21, #22 | 6–8 周 | 认证清单可用，1 个新模块通过认证 |

---

## 度量

每个阶段完成后测量以下指标（替代"增长至上"指标）：

- 首次模块安装后成功打开率
- 安装前必填信息完整度（隐私卡覆盖率）
- 云端调用取消率与"改用本地"比例
- WebDAV 同步冲突解决完成率
- 跨模块任务完成链路数
- 用户主动关闭提醒/挑战后留存变化
- 模块更新/回滚成功率

---

## 执行约束

- 遵循 `AGENTS.md` Prime Directive：不回退用户或无关改动；工作树常为脏。
- 任何用户可见改动必须安装到真机/模拟器并走真实入口，运行后检查 logcat `FATAL EXCEPTION`。
- 涉及签名/Catalog/模块加载的改动，必须以真实产物验证，不能仅凭 Gradle 成功。
- 每个阶段完成后更新 `CURRENT_STATE.md` 的"已核验能力"与"当前发布门槛"。
- 不做行为追踪/广告/强制连胜换留存（#3）。
- 不在 WebDAV 同步成熟前引入账号体系（#25）。
- 不在模块认证清单成熟前承诺"第三方模块生态"（#13）。
- 不为追求极小首包把首次必需能力拆成额外下载（#4/#6）。
- 不把 AI 做成独立聊天页主路径（#16）。
- 历史路线图/审计保留为日期化快照，不删不改写（#26–28）。

---

[🔙 返回文档索引](DOCUMENTATION_INDEX.md)
