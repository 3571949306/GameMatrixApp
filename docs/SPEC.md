<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# Spec — GameMatrixApp 全面改造 v1.0

> 生成日期：2026-07-07
> 基于：PRD v1.0（许清楚）+ 架构与技术可行性文档（高见远）+ UIUX/设计系统文档（颜好看）+ Master Plan v1.0（郝交付）
> 状态：**已确认**（用户 2026-07-07 批准）
> 编制：郝交付（交付总监）

> **2026-07-21 增量决策：** 原“一次性全量 Compose 重写”继续不做；模块商店 UI 改为受默认关闭开关保护的 Flutter Add-to-App。宿主首页、游戏、人机 AI 与动态模块业务页面保持 Android/Compose/既有实现。目录信任、下载、签名、安装和回滚不得迁入 Flutter。最新范围与发布门禁见 `/docs/flutter-store/MIGRATION_STATUS.md`。

---

## 1. 产品定义

- **一句话描述**：Android 模块化游戏中心——28 款内置游戏 + 动态下载 + AI 错题本 + 本地 LLM，主打无广告、隐私不出端、离线可用。
- **目标用户**：
  - P-A 隐私敏感型单机玩家（核心，~60%）：通勤碎片时间玩棋牌/益智，厌恶广告与追踪
  - P-B 学生/家长 AI 错题本用户（~25%）：拍照 OCR 整理错题，重隐私不外传
  - P-C 旧设备/兼容敏感用户（潜在增量，当前被拒）：32 位设备无法安装
- **核心问题**：明暗调色盘冲突导致暗色模式错乱；模块 APK 仅校验 SHA-256 不校验签名者可被伪造；~80% layout 硬编码 dimens 致 UI 不一致且维护成本高。

---

## 2. MVP 范围（锁定——不在此列表的功能一律不做）

| 优先级 | 功能 | 对应痛点 | 验收标准摘要 | RICE | Effort |
|--------|------|----------|-------------|------|--------|
| P0 | **A1 暗色调色盘冲突修复** | P0-6 | 切换暗色无同名冲突取错值；主色迁移至 `#3D5AFE`；自定义 View 走 `?attr/` | 50.0 | 2 人周 |
| P0 | **S1 模块 APK 签名者强校验** | P0-5 | 模块安装前除 SHA-256 外校验签名者证书，不匹配则拒装并提示 | 33.8 | 4 人周 |
| P0 | **A2 设计 Token 系统** | P1-1 | 语义色/间距/圆角/阴影 Token 单源；新增布局强制引用 token，Lint 拦截硬编码 | 15.0 | 6 人周 |

### 明确不做的功能（Won't Have）
- 社区/社交广场（论坛、动态、好友）——偏离收束复杂度哲学
- 广告/变现系统——与隐私优先定位冲突
- 云游戏串流（自建）——需独立算力基建
- iOS / 桌面端版本——Android 专域
- 一次性全量 Compose 重写——遵循渐进迁移，现有 XML 不重写
- 新增语种大扩张——本轮只补中英漏覆盖

### MVP 依赖链
```
A1(暗色修复) ──→ A2(设计Token) ──→ 全部美术UI 后续工作
                                    │
S1(签名校验) ──────────────────→ F1(框架收敛, 一期) 的模块安全前置
```

---

## 3. 技术架构（锁定）

| 维度 | 选型 | 备注 |
|------|------|------|
| 前端框架 | Material 3 (1.13.0) + 纯 XML View；**Compose 渐进引入** | 试点页：设置 + AI 对话（Phase 1）；大厅/商店/引导 Phase 2 |
| Compose 互操作 | `ComposeView`（XML 嵌 Compose）+ `AndroidView`（Compose 嵌 XML） | 不重写现有 XML 页面 |
| 语言 | Kotlin 2.0.21 (~45%) + Java 17 (~55%) | kapt→ksp 迁移（一期 P5） |
| DI | Hilt 2.57.2 | 保留 |
| 数据库 | Room 2.7.1 | MVP 不新增表 |
| 网络 | OkHttp 4.12.0 | 保留 |
| 构建 | Gradle/AGP 8.13.2 | 保留 |
| SDK | minSdk 24 / target·compileSdk 35 / ABI arm64-v8a | 锁定，C1 多 ABI 为二期 |
| 分发 | HK VPS + GitHub Releases（非 Google Play） | 影响 Baseline Profile 须打包内置 |
| 认证方案 | 无账号体系（免登录）；错题本密钥经 Android Keystore | 保留 |

---

## 4. API 端点清单（锁定）

### MVP 阶段
**MVP 不涉及新增 API 端点。** A1/A2 为客户端资源重构，S1 为客户端校验逻辑增强。

### 二期新功能 API 草案（锁定方向，开发时以此为依据，受 server/ 扩容排期约束 W2）

| Method | Path | 功能 | 认证 | 请求体 | 响应体 |
|--------|------|------|------|--------|--------|
| POST | /api/v1/saves/sync | 云存档上传 | Keystore token | `{slot_key, payload_json, version}` | `{version, synced_at}` |
| GET | /api/v1/saves/:slot_key | 云存档拉取 | Keystore token | — | `{slot_key, payload_json, version}` |
| PATCH | /api/v1/saves/:slot_key | 冲突解决 | Keystore token | `{payload_json, version}` | 200 ok / 409 conflict |
| POST | /api/v1/share/cards | 生成战绩卡 | — | `{game_id, score, duration_ms}` | `{card_id, share_token, image_url}` |
| GET | /api/v1/share/cards/:id | 取战绩卡 | — | — | `{card_id, game_id, score, ...}` |
| GET | /api/v1/achievements | 成就目录 | — | — | `[{id, key, title_zh, title_en, ...}]` |
| GET | /api/v1/users/:id/achievements | 用户已解锁 | Keystore token | — | `[{achievement_id, progress, unlocked_at}]` |
| POST | /api/v1/achievements/progress | 进度上报 | Keystore token | `{achievement_id, progress}` | `{unlocked: bool}` |
| POST | /api/v1/wrongbook/items | 新增错题 | Keystore token | `{subject, question_text, image_ref}` | `{id, next_review_at}` |
| GET | /api/v1/wrongbook/items?subject= | 错题列表 | Keystore token | — | `[{id, subject, ...}]` |
| PATCH | /api/v1/wrongbook/items/:id | 复习结果上报 | Keystore token | `{grade(0-5)}` | `{next_review_at, ease_factor}` |
| GET | /api/v1/wrongbook/review/next | 取待复习项 | Keystore token | — | `{id, question_text, ...}` |

---

## 5. 数据库表清单（锁定）

### MVP 阶段
**MVP 不新增数据库表。** A1/A2 为资源层重构，S1 为校验逻辑（无持久化新增）。

### 二期新功能 DB Schema 草案（锁定方向）

| 表名 | 核心字段 | 索引 | 关联 |
|------|----------|------|------|
| cloud_save_snapshots | id(PK), user_id, slot_key, payload_json, version, synced_flag, created_at, updated_at, deleted_at | (user_id, slot_key), updated_at | users |
| score_cards | id(PK), user_id, game_id, score, duration_ms, share_token, views, created_at, deleted_at | (user_id, game_id), share_token(UNIQUE) | users, games |
| achievements | id(PK), key(UNIQUE), title_zh, title_en, desc_zh, desc_en, threshold, game_id | game_id | games |
| user_achievements | (user_id, achievement_id)(PK), progress, unlocked_at, deleted_at | user_id | users, achievements |
| wrongbook_items | id(PK), user_id, subject, question_text, answer_text, image_ref, ocr_engine, next_review_at, ease_factor, interval_days, repetitions, created_at, updated_at, deleted_at | (user_id, subject), (user_id, next_review_at) | users |
| wrongbook_reviews | id(PK), item_id, reviewed_at, grade(0-5), correct | item_id | wrongbook_items |

> 所有表含 `id(UUID TEXT)/created_at/updated_at(INTEGER epoch ms)/deleted_at(软删)`；密钥不落库，经 Android Keystore 封装。

---

## 6. 页面清单（锁定）

### MVP 阶段涉及的页面

| 页面 | 路由 | MVP 改动 | 对应功能 | 设计 Token 主题 |
|------|------|----------|----------|-----------------|
| 全局主题 | — | A1: colors.xml + values-night 统一；主色迁移 #3D5AFE | 暗色冲突修复 | Light/Dark 双主题 |
| 全局资源 | — | A2: 新建 tokens.xml（spacing/radius/elevation/shape） | 设计 Token 系统 | — |
| 模块安装/校验流程 | — | S1: 新增签名者证书校验逻辑 | 签名强校验 | — |

### Compose 试点页（Phase 1，MVP 后一期起）

| 页面 | 路由 | 核心组件 | 对应 API | 设计 Token 主题 |
|------|------|----------|----------|-----------------|
| 设置页 | SettingsActivity → Compose | M3 列表 + Switch/Slider + 分组 | — | GameMatrixTheme |
| AI 对话页 | AIChatActivity → Compose | 消息流 + 输入栏 + Loading Indicator | 本地 MediaPipe + 后端代理 | GameMatrixTheme |

### Phase 2 迁移页（锁定方向，不在 MVP 范围）

| 页面 | Compose 优先级 | 备注 |
|------|----------------|------|
| 游戏大厅（首页） | 高 | Large TopAppBar + 内容 Rail |
| 模块商店 | 高 | SearchBar + Filter Chip + 响应式网格 |
| 新手引导（斗地主/围棋） | 高 | 交互式 Coachmark |
| 错题本 | 中 | 折叠段 + 错题卡 + 复习进度环 |

---

## 7. 设计 Token（锁定）

### 7.1 色彩 Token（明/暗双主题）

**主色：Vivid Indigo-Blue `#3D5AFE`**（替原 Google Blue `#1A73E8`）
**强调色：Amber Gold `#FFB300`**（呼应华容道深金）
**辅助色：Teal `#00897B`**

**LIGHT 主题（关键 Token）**
| Token | 值 |
|-------|----|
| colorPrimary | `#3D5AFE` |
| colorOnPrimary | `#FFFFFF` |
| colorPrimaryContainer | `#DEE3FF` |
| colorOnPrimaryContainer | `#001558` |
| colorSecondary | `#FFB300` |
| colorOnSecondary | `#1A1200` |
| colorTertiary | `#00897B` |
| colorOnTertiary | `#FFFFFF` |
| colorBackground | `#F7F8FC` |
| colorOnBackground | `#16181F` |
| colorSurface | `#FFFFFF` |
| colorOnSurface | `#16181F` |
| colorSurfaceVariant | `#E7E9F2` |
| colorOnSurfaceVariant | `#43474F` |
| colorSurfaceContainerLow | `#EEF0F7` |
| colorSurfaceContainer | `#E7E9F2` |
| colorSurfaceContainerHigh | `#E0E3EC` |
| colorOutline | `#C8CCDA` |
| colorError | `#D8392F` |
| colorOnError | `#FFFFFF` |

**DARK 主题（关键 Token）**
| Token | 值 |
|-------|----|
| colorPrimary | `#8C9CFF` |
| colorOnPrimary | `#001558` |
| colorPrimaryContainer | `#2E3BB8` |
| colorOnPrimaryContainer | `#DEE3FF` |
| colorSecondary | `#FFC24D` |
| colorOnSecondary | `#2E1E00` |
| colorTertiary | `#4FD1C5` |
| colorOnTertiary | `#00302B` |
| colorBackground | `#0E1016` |
| colorOnBackground | `#E4E6F0` |
| colorSurface | `#161922` |
| colorOnSurface | `#E4E6F0` |
| colorSurfaceVariant | `#2A2E3A` |
| colorOnSurfaceVariant | `#C5C9D6` |
| colorSurfaceContainerLow | `#1C202B` |
| colorSurfaceContainer | `#222633` |
| colorSurfaceContainerHigh | `#2A2F3D` |
| colorOutline | `#3D4250` |
| colorError | `#FF8A84` |
| colorOnError | `#4B0D0A` |

**语义色扩展**
| 语义 | Light | Dark | 用途 |
|------|-------|------|------|
| colorSuccess | `#1FA463` | `#4CC38A` | 安装成功/正确/完成 |
| colorWarning | `#E08A00` | `#FFB84D` | 更新可用/弱提示 |
| colorInfo | = colorPrimary | = colorPrimary | 信息提示 |

> **落地原则**：单一真源放 `common` 模块；明/暗仅通过 `themes.xml`/`themes-night.xml` overlay 表达同名 `?attr/`；layout 永不直接写 hex，只引用 `?attr/colorXxx`。

### 7.2 字体
- **字体栈**：`"Noto Sans SC", "Inter", system-ui, "PingFang SC", "Microsoft YaHei", sans-serif`
- **等宽**：`"JetBrains Mono", "Roboto Mono", monospace`
- **Type Scale**（sp / weight）
  | 角色 | 大 | 中 | 小 | 字重 |
  |------|----|----|----|------|
  | Display | 57 | 45 | 36 | 400 |
  | Headline | 32 | 28 | 24 | 400/500 |
  | Title | 22 | 16 | 14 | 500 |
  | Body | 16 | 14 | 12 | 400 |
  | Label | 14 | 12 | 11 | 500 |

### 7.3 间距 Token（4dp 基准，根解 P1-1）
`gm_spacing_0=0, _1=4, _2=8, _3=12, _4=16, _5=20, _6=24, _7=32, _8=40, _9=48, _10=64`
触控目标最小 `48×48dp`。

### 7.4 圆角 Token
`gm_radius_xs=4, sm=8, md=12, lg=16, xl=24, xxl=32, full=9999dp`
卡片用 md/lg，Chip/游戏块用 full，对话框用 xl。

### 7.5 阴影 / 层级 Token
| 级 | Light 阴影 | Dark | 用途 |
|----|-----------|------|------|
| 0 | none | none | 平面/分割 |
| 1 | `0 1dp 2dp rgba(0,0,0,.30)` | surfaceContainerLow 色调 | 卡片静止 |
| 2 | `0 3dp 6dp rgba(0,0,0,.30)` | surfaceContainer | 悬浮卡/FAB |
| 3 | `0 6dp 10dp rgba(0,0,0,.30)` | surfaceContainerHigh | 底部弹层/菜单 |
| 4 | `0 8dp 14dp rgba(0,0,0,.30)` | — | 对话框 |
| 5 | `0 12dp 20dp rgba(0,0,0,.30)` | — | 顶层模态 |

### 7.6 图标库
- **主**：Material Symbols（Rounded, Variable）——M3 原生，主题色自动着色
- **辅**：Lucide——仅新手引导/空状态插画
- **严禁**：emoji 作功能图标

### 7.7 动效
| 用途 | 曲线/参数 |
|------|-----------|
| Emphasized 进入 | `cubic-bezier(0.2, 0, 0, 1)` |
| Emphasized 退出 | `cubic-bezier(0.4, 0, 1, 1)` |
| Standard 遗留 | `cubic-bezier(0.4, 0, 0.2, 1)` |
| Compose 弹性 | `spring(stiffness≈MediumHigh, damping≈Medium)` |
| Duration | micro 100 / small 150-200 / medium 250-300 / large 350-400 ms |
| Reduced-motion | 监听 `animatorDurationScale`，关闭或压缩至 ≤100ms |

---

## 8. 验收标准（锁定——QA 测试时以此为唯一依据）

### AC-A1: 暗色调色盘冲突修复
| 编号 | Given | When | Then |
|------|-------|------|------|
| A1-1 | 用户在系统设置切换为暗色模式 | 打开任意内置页面（大厅/商店/设置/各游戏） | 颜色取自唯一主题源，无 values/colors.xml 与 values-night 同名冲突取错值 |
| A1-2 | 开发者查看 colors.xml | 检查主色定义 | colorPrimary = `#3D5AFE`（非原 `#1A73E8`） |
| A1-3 | 自定义 View（斗地主桌面/五子棋/华容道/象棋）在暗色模式 | 渲染 | 通过 `?attr/` 取色，暗色正确跟随，无亮色残留 |
| A1-4 | CI 构建 | 运行 Lint | 无 `values` 与 `values-night` 同名直接 color 定义冲突告警 |

### AC-S1: 模块 APK 签名者强校验
| 编号 | Given | When | Then |
|------|-------|------|------|
| S1-1 | 下载一个动态模块 APK | 安装前校验 | 除 SHA-256 完整性外，还校验签名者证书（X509 DER）与内置发布证书匹配 |
| S1-2 | 模块 APK 签名者证书不匹配 | 校验 | 拒绝安装并提示"模块签名验证失败" |
| S1-3 | 模块 APK SHA-256 不匹配 | 校验 | 拒绝安装并提示"完整性校验失败" |
| S1-4 | 合法签名的模块 | 校验 | 正常安装加载，不影响现有下载链路 |
| S1-5 | 过渡期（灰度） | 签名校验失败 | 仅告警不阻断（双轨），灰度后切为硬失败 |

### AC-A2: 设计 Token 系统
| 编号 | Given | When | Then |
|------|-------|------|------|
| A2-1 | 开发者新增/修改布局尺寸 | 提交 | 尺寸引用 `gm_spacing_*` token（非硬编码 dp），Lint `hardcoded-dimens` 规则拦截 |
| A2-2 | 开发者新增/修改布局颜色 | 提交 | 颜色引用 `?attr/colorXxx`（非硬编码 hex），Lint 拦截 |
| A2-3 | 查看 common 模块资源 | 检查 token 定义 | 存在 `color_tokens.xml` + `dimens_tokens.xml` + `shape_tokens.xml`，明/暗通过 overlay 表达 |
| A2-4 | 既有 layout（分批迁移后） | 检查 | ~80% 硬编码 dimens 替换为 token 引用 |
| A2-5 | 圆角/阴影 | 检查 | 统一引用 `gm_radius_*` / `gm_elevation_*`，无跨页不一致 |

### 通用验收红线
- 14 单测 + 145 UI 自动化用例**全过**（不回退）
- Lint 基线**只减不增**（1007 条不增加）
- CI / Dependabot **0 告警**
- 无紫色/品红渐变主视觉、无 emoji 功能图标、无硬编码 hex
- 对比度达标（正文 ≥4.5:1，大字号/图标 ≥3:1）
- 间距 4dp 整数倍
- 支持减少动态效果

---

## 9. 边界与约束

- **不支持** IE 浏览器（Android 专域，不适用）
- **最低支持** Android 7.0 (API 24)
- **目标/编译** Android 15 (API 35)
- **ABI**：仅 arm64-v8a（C1 多 ABI 为二期，需权衡 ROI）
- **分发**：HK VPS + GitHub Releases（非 Google Play）→ Baseline Profile 须打包内置
- **性能目标**：冷启动 <2s（一期 U1，中端 arm64 设备）
- **响应式断点**：手机（Compact）/ 平板（Expanded）/ 折叠屏（Medium）——一期起持续
- **MediaPipe 16KB（W1）**：立即立项跟踪，不阻塞 MVP；三路并行（上游/源码构建/换库）+ 运行时降级守卫
- **server/ 扩容（W2）**：二期新功能联调受后端排期约束，前端草案可先行

---

## 10. 变更记录

| 日期 | 变更内容 | 原因 | 影响范围 |
|------|----------|------|----------|
| 2026-07-07 | Spec v1.0 初始生成 | 用户批准 Master Plan | 全部 |

---

## 11. 立即立项跟踪项（不阻塞 MVP）

| 编号 | 项目 | 级别 | 负责人 | 排期 |
|------|------|------|--------|------|
| W1 | MediaPipe 16KB 页对齐 | 🔴 最高 | 架构师 | 立即跟踪，三路并行 |
| W2 | server/ 后端扩容 | 🟡 | 后端 | 二期功能前置 |

---

— 郝交付 / 交付总监，Spec v1.0 已锁定，团队内部以此为唯一开发依据。