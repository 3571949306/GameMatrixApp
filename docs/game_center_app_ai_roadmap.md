# GameMatrixApp 改造详细计划（Detailed Plan）

> **当前进度 (2026-07-06)**：阶段1 ✅ 阶段2 ✅ 阶段3 ✅ 阶段4 ✅ 模块市场 ✅ 阶段5 本地模型 ✅ 阶段5.5 游戏内嵌 ✅ 阶段5.6 线程优化 ✅ 阶段5.7 UI优化 ✅ 阶段5.8 更新逻辑优化 ✅ 阶段5.9 浏览器原生重构 ✅ 阶段5.10 wrongbook 模块 ✅ 阶段5.11 宿主 Kotlin 迁移 ✅ 阶段5.12 Netty 安全修复 ✅ | 下一阶段：阶段6 扩展自动化
>
> 当前版本: **v1.4.1** (vc=567)
> 上次稳定版: 1.4.0 (vc=465)
> APK 已发布到 HK VPS（beta channel）和 GitHub Releases；2026-06-19 起 US VPS 已下线
> 包名: `com.gamecenter.app`
> Gradle 工具链: AGP 8.13.2, Kotlin 2.0.21, Hilt 2.57.2
> 最新 commit: `f978f06 fix(security): 循环24 修复 GitHub Dependabot 7 个 Netty 安全漏洞`
> 工作区状态：干净，main 与 origin/main 同步
>
> | 阶段 | 状态 | 版本 | 说明 |
> |------|------|------|------|
> | 阶段1：工程稳定 | ✅ 已完成 | v1.3.20 | 编译验证通过，无回归 |
> | 阶段2：接入 AI 入口 | ✅ 已完成 | v1.3.21-beta | AI 助手作为独立底部导航页接入 |
> | 阶段3：完成 MVP | ✅ 已完成 | v1.3.20/vc236 | OCR/总结/翻译/润色/问答/历史记录 |
> | 阶段4：模板与记录 | ✅ 已完成 | v1.3.20/vc236 | 模板/历史搜索/收藏/导出 |
> | 阶段4+：棋类 AI 响应优化 | ✅ 已完成 | v1.3.29 | 五子棋/象棋/围棋去除假延迟，围棋新增并行模拟 |
> | 阶段4+：更新功能优化 | ✅ 已完成 | v1.3.30-beta.1 | 下载到公共目录，通知改善，完整性校验 |
| 阶段4+：模块市场架构 | ✅ 已完成（builtIn修复 + VPN模块上线 + 右上角按钮完善） | v1.4.0 | 市场入口、默认游戏分类、刷新按钮、已下载模块列表、浏览器/工具箱/AI改为市场模块、VPN 科学上网模块（非内置可下载） |
| 阶段5：本地模型 | ✅ 已完成 | v1.4.0/vc400 | MediaPipe LLM Inference、Gemma本地推理、LocalAiProcessor、任务状态枚举、AI错误码常量 |
| 阶段5.5：游戏内嵌 | ✅ 已完成 | v1.4.0/vc465 | 28个游戏内置到主app，保留更新能力，修复BaseGameActivity/GameStartDialog缺失 |
| 阶段5.6：线程优化 | ✅ 已完成 | v1.4.0/vc465 | 统一线程管理器AppExecutors，OkHttp线程池64→8，总线程数75→17 |
| 阶段5.7：UI优化 | ✅ 已完成 | v1.4.0/vc465 | 华容道UI升级，难度选择面板优化（休闲游戏直接启动） |
| 阶段5.8：更新逻辑优化 | ✅ 已完成 | v1.4.1/vc466 | OptimizedUpdateManager（缓存+重试+MD5预检查），优化超时，占位符URL检测 |
| 阶段5.9：浏览器原生重构 | ✅ 已完成 | v1.4.1/vc567 | 循环17-19：browser/{bridge,core,data,security,ui} 包结构，Room 4张表，AdBlocker/DomainTrustManager/JsBridgePolicy |
| 阶段5.10：wrongbook 模块 | ✅ 已完成 | v1.4.1/vc567 | 循环20预装集成（assets/modules/feature_wrongbook_v100.apk），循环21-22全面推进（Room v2 schema、自定义图表 View、科目管理、复习计划、数据导入导出） |
| 阶段5.11：宿主 Kotlin 迁移 | ✅ 已完成 | v1.4.1/vc567 | 循环23：App.java/MainActivity.java/GameRegistry.java → .kt，新增 ModuleContextHelper.kt，新增 .github/workflows/android_ci.yml 和 dependabot.yml，语言比例 Java 55% + Kotlin 45% |
| 阶段5.12：Netty 安全修复 | ✅ 已完成 | v1.4.1/vc567 | 循环24：Netty 4.1.134.Final → 4.1.135.Final，修复 7 个 CVE（3 high + 4 medium），GitHub Dependabot 0 open / 7 dismissed |
> | 阶段6：扩展自动化 | 📅 规划中 | — | 界面识别/任务规划 |

## 0. 文档目标

本文档用于指导 `GameMatrixApp` 的后续改造。目标不是重做一个新项目，而是在**尽量复用现有工程**的前提下，把它升级为一个具备以下能力的安卓应用：

- 保留现有游戏中心(Game Center)
- 保留现有工具箱(Tools)
- 保留现有浏览器(Browser)
- 新增 AI 助手(AI Assistant)
- 新增 科学上网 VPN（模块商店可下载）
- 新增本地优先(Local First)的处理策略
- 为后续自动化(Automation)预留接口
- 最终形成可变现(Monetization)的产品结构

本项目的核心不是“聊天”，而是：

> **AI 辅助用户完成真实任务。**

---

## 1. 项目现状分析

### 1.1 当前工程的可复用价值
现有项目已经具备一个相对完整的安卓应用框架，适合继续扩展，而不是推倒重来。可复用部分包括：

- 主页面结构(Main Screen)
- 底部/顶部导航(Navigation)
- 游戏中心模块(Game Center)
- 工具箱模块(Tools)
- 浏览器模块(Browser)
- 更新模块(Update)
- 网络工具(Network Tools)
- 设备工具(Device Tools)
- 二维码(QR)、颜色(Color)、Ping、DNS、端口扫描(Port Scan)等功能

### 1.2 当前项目的主要问题
虽然结构完整，但从产品角度看仍存在几个明显问题：

1. **功能分散**：工具很多，但用户难以感受到统一价值。
2. **差异化不足**：很多功能在其他工具箱 App 中也能找到。
3. **变现点不明显**：缺少“非用不可”的核心能力。
4. **AI 能力缺失**：当前没有能显著提高使用价值的智能层。
5. **长期扩展空间有限**：如果继续只堆工具，后续同质化会更严重。

### 1.3 改造的意义
改造的目标不是把它变成一个“全能 AI”，而是把它升级成：

- 有明确场景
- 有智能辅助
- 有自动化能力
- 有低成本运行模式
- 有商业化空间

---

## 2. 产品定位

### 2.1 新定位
建议将项目定位为：

> **GameMatrixApp = 游戏娱乐 + 实用工具 + AI 辅助 + 自动化执行**

### 2.2 用户画像(User Persona)
本项目最适合的目标用户包括：

- 大学生
- 喜欢折腾手机工具的人
- 需要截图处理、文档总结、翻译的人
- 经常使用安卓手机的中重度用户
- 未来需要自动化执行任务的用户

### 2.3 用户核心诉求
用户最在意的不是“AI 有多先进”，而是：

- 是否省时间
- 是否省操作
- 是否容易上手
- 是否真的有用
- 是否值得付费

---

## 3. 核心产品方向

### 3.1 第一优先方向：AI 工具(AI Tools)
从最容易落地、最容易看到效果的功能开始：

- OCR(图片转文字)
- 文本总结(Summarization)
- 翻译(Translation)
- 文本润色(Rewrite)
- 简单问答(Q&A)

这些功能的优点是：

- 用户理解成本低
- 开发成本可控
- 可以和现有工具箱融合
- 便于后续扩展

### 3.2 第二优先方向：任务流(Workflow)
在 AI 基础功能之上，增加“任务流”能力：

- 一次输入，多步处理
- 文本处理链路
- 模板化任务
- 常用场景自动化

例如：

- 上传截图 → OCR → 总结 → 导出笔记
- 粘贴一段资料 → 提炼重点 → 生成复习提纲
- 输入一段代码报错 → 解释 → 给出修复建议

### 3.3 第三优先方向：自动化(Automation)
自动化是这个项目的差异化核心之一。后续可以逐步实现：

- 界面文本识别(UI Parsing)
- 按钮识别(Button Detection)
- 自动点击(Click)
- 自动滑动(Scroll)
- 自动执行固定流程(Task Flow)

---

## 4. 改造原则

### 4.1 复用优先(Reusability First)
不要一次性重写工程。优先：

- 复用现有 Fragment
- 复用现有适配器(Adapter)
- 复用现有主题系统(Theme)
- 复用现有设置系统(Settings)
- 复用现有更新机制(Update Manager)

### 4.2 层次清晰(Separation of Concerns)
建议将逻辑拆成：

- UI 层(UI Layer)：页面、按钮、列表、输入框
- 业务层(Business Layer)：AI 调度、任务分发、自动化逻辑
- 数据层(Data Layer)：历史、缓存、配置、模型参数
- 模型层(Model Layer)：本地模型、云端模型

### 4.3 本地优先(Local First)
核心原则：

- 能本地做的，绝不走云端
- 简单任务优先用规则或本地小模型
- 复杂任务再调用云端模型

这样可以显著降低成本。

### 4.4 可扩展(Extensible)
所有设计都要为未来预留：

- 新模型接入
- 新任务类型
- 新自动化流程
- 新模板
- 新收费策略

---

## 5. 建议的功能结构

### 5.1 现有模块保留
#### 游戏中心(Game Center)
保留现有小游戏和娱乐入口。

#### 工具箱(Tools)
保留现有工具，并作为 AI 工具入口的主要承载位置。

#### 浏览器(Browser)
保留内置浏览器，后续可以用于网页总结、信息抓取、自动化辅助。

#### 更新(Update)
保留现有更新机制，后续可继续用于版本管理和功能开关。

---

### 5.2 新增模块：AI 助手(AI Assistant)
建议把 AI 模块设计成一个独立功能域，包含：

- OCR 图片识别
- 文本总结
- 翻译
- 润色
- 简单问答
- 模板任务
- 历史记录
- 结果导出

---

### 5.3 新增模块：自动化助手(Automation Assistant)
后续增加：

- 屏幕内容识别
- UI 元素分析
- 操作步骤生成
- 自动执行任务
- 脚本记录与回放

---

## 6. 页面结构设计(Page Structure)

### 6.1 推荐入口方案
#### 方案 A：放入工具箱(Tools)
在现有 Tools 页面里新增一个 “AI 工具” 区域。

优点：
- 改动小
- 兼容性高
- 适合第一阶段

#### 方案 B：增加独立 AI Tab
在主导航中增加一个 AI 页签。

优点：
- 更清晰
- 更像独立产品

缺点：
- 改动更大

### 6.2 推荐选择
第一阶段建议采用：

> **方案 A：工具箱内新增 AI 工具入口**

### 6.3 AI 页面建议布局
AI 页面可设计为：

- 顶部：模型状态、模式切换、本地/云端标识
- 中间：消息/任务结果列表
- 底部：输入框、发送按钮、模板按钮
- 侧边或弹窗：历史记录、快捷模板、设置

---

## 7. 功能分层设计

### 7.1 第一层：基础 AI 功能
这是 MVP(Minimum Viable Product) 阶段必须完成的内容。

#### 功能 1：OCR(图片转文字)
- 上传图片
- 识别图片中的文本
- 输出可复制结果
- 可与总结/翻译联动

#### 功能 2：文本总结(Summary)
- 输入长文本
- 输出摘要
- 输出重点列表
- 输出考试笔记风格结果

#### 功能 3：翻译(Translation)
- 中英互译
- 技术文档翻译
- 通俗解释版翻译

#### 功能 4：润色(Rewrite)
- 更正式
- 更简洁
- 更适合发布
- 更适合展示

#### 功能 5：简单问答(Q&A)
- 解释概念
- 回答小问题
- 处理单轮轻量问答

---

### 7.2 第二层：模板化能力
模板是 AI 工具产品里非常重要的部分。

#### 推荐模板
- 课堂笔记模板
- 作业总结模板
- 代码解释模板
- 错误分析模板
- 会议纪要模板
- 文案润色模板
- 网页摘要模板

#### 模板作用
- 降低用户学习成本
- 提升使用频率
- 增强“好用”感

---

### 7.3 第三层：任务流能力(Workflow)
任务流是将多个功能串联起来。

#### 示例 1：截图处理流程
1. 用户上传截图
2. OCR 提取文字
3. AI 进行总结
4. 输出结构化结果
5. 支持一键复制/导出

#### 示例 2：学习资料处理流程
1. 粘贴长文本或上传文档
2. 自动摘要
3. 提炼重点
4. 生成复习提纲
5. 可导出为 Markdown 或 TXT

---

## 8. 技术架构建议

### 8.1 安卓端(Android)
推荐技术：

| 部分 | 推荐 |
|---|---|
| UI | XML + Material 风格 |
| 语言 | Java / Kotlin |
| 网络请求 | OkHttp / Retrofit |
| JSON 解析 | Gson / Moshi |
| 本地存储 | Room / SQLite |
| 任务调度 | WorkManager |
| 后台任务 | Foreground Service |
| 权限处理 | Activity Result API / Runtime Permission |

### 8.2 AI 层(AI Layer)
建议拆分为两种：

#### 本地模型(Local Model)
适合：
- 文本分类
- 意图识别
- 简单摘要
- 简单 OCR 后处理

#### 云端模型(Cloud Model)
适合：
- 复杂推理
- 长上下文
- 高质量写作
- 多轮任务规划

### 8.3 自动化层(Automation Layer)
建议使用：

- Accessibility Service(无障碍服务)
- MediaProjection(截图)
- OCR 识别
- 元素分析
- 任务执行器(Task Executor)

---

## 9. 数据结构设计

### 9.1 核心数据模型
#### AiMessage
用于存储消息或任务对话。

字段建议：
- id
- role(user/assistant/system)
- content
- timestamp
- taskType
- source(local/cloud)

#### AiTask
用于描述一次任务。

字段建议：
- taskId
- taskType
- input
- output
- status
- createdAt
- costLevel

#### AiResult
用于统一返回结果。

字段建议：
- success
- message
- content
- source
- errorCode

#### AiProviderConfig
用于描述模型配置。

字段建议：
- providerName
- modelName
- apiKey
- baseUrl
- enabled
- localOnly

---

## 10. 推荐代码包结构

### 10.1 新增包（已有 ✅ / 待创建 🔜）
- ✅ `com.GameMatrix.app.ai`
- ✅ `com.GameMatrix.app.ai.ui`
- ✅ `com.GameMatrix.app.ai.data`
- ✅ `com.GameMatrix.app.ai.local`
- ✅ `com.GameMatrix.app.ai.cloud`
- ✅ `com.GameMatrix.app.ai.history`
- ✅ `com.GameMatrix.app.ai.template`
- 🔜 `com.GameMatrix.app.ai.automation` — 阶段6

### 10.2 核心类建议（已实现 ✅ / 待实现 🔜）

- ✅ `AiFragment` — AI 助手页面
- 🔜 `AiViewModel` — 后续可通过 AAC MVVM 模式重构
- 🔜 `AiRepository` — 当前逻辑集成在 AiTaskRouter 中
- ✅ `AiTaskRouter` — 任务路由（本地优先 + 云端分发）
- 🔜 `LocalModelManager` — 当前由 LocalAiProcessor 提供基础能力
- ✅ `CloudModelClient` — AiApiClient 已实现同步 API 调用
- ✅ `OcrProcessor` — 当前由 LocalAiProcessor 提供基础 OCR 文本清洗
- ✅ `TextSummarizer` — 当前由 LocalAiProcessor 本地规则 + 云端 API 混合实现
- ✅ `PromptTemplateManager` — AiTemplateManager 已提供常用模板入口
- ✅ `AiHistoryStore` — 历史记录持久化
- 🔜 `AutomationExecutor` — 阶段6实现
- 🔜 `UiParser` — 阶段6实现

---

## 11. 开发顺序(Dev Order)

### 阶段 1：工程稳定 ✅（已完成 — v1.3.20）
目标：不破坏原有功能。

状态：**已完成**。编译验证通过（`assembleDebug` BUILD SUCCESSFUL），无回归错误。

检查项均通过：
- [x] 现有页面跳转正常
- [x] 工具箱正常
- [x] 浏览器正常
- [x] 更新系统正常
- [x] 主题系统正常
- [x] 资源引用无冲突

### 阶段 2：接入 AI 入口 ✅（已完成 — v1.3.21-beta）
目标：先有入口，再做能力。

状态：**已完成**。AI 智能助手采用独立底部导航入口，不再嵌入工具箱；页面包含聊天式交互能力。

动作完成情况：
- [x] 在底部导航中新增 AI 入口（`bottom_nav_menu.xml` + `mobile_navigation.xml`）
- [x] 新建 AI 页面（`AiFragment` + `fragment_ai.xml`）
- [x] 新增基础 UI（`fragment_ai.xml` + `item_ai_message.xml` + 消息气泡背景）
- [x] AI 数据模型（`AiMessage`, `AiTask`, `AiResult`, `AiProviderConfig`）
- [x] 云端 API 客户端（`AiApiClient`，支持 OpenAI 兼容接口）
- [x] 本地 AI 处理器（`LocalAiProcessor`：规则摘要、关键词提取、OCR 清洗、分类）
- [x] 任务路由（`AiTaskRouter`：本地优先 → 云端 fallback）
- [x] AI 偏好设置（`AiPreferences`：API Key、模型选择、配额管理）
- [x] 本地优先策略（默认启用，低复杂度走本地，复杂任务走云端）
- [x] 每日免费额度（默认 20 次）
- [x] 测试版 APK 已上传 HK VPS（beta channel）；US VPS 已于 2026-06-19 下线

已实现 API：`summary`, `translate`, `rewrite`, `ocr`, `qa`/`qa_pairs`, `keywords`, `classify`

### 阶段 3：完成 MVP ✅（已完成 — v1.3.20/vc236）
目标：快速可用。

MVP 功能：
- [x] OCR 文本清洗
- [x] 总结
- [x] 翻译
- [x] 润色
- [x] 简单问答
- [x] 历史记录

实现说明：
- 无 API Key 时优先使用本地规则处理，保证 MVP 可离线验证。
- 配置 API Key 后，复杂任务继续走云端 OpenAI 兼容接口增强。
- 历史记录在 `AiHistoryStore` 中持久化，AI 页面重开后自动恢复最近对话。

### 阶段 4：加入模板与记录 ✅（已完成 — v1.3.20/vc236）
目标：提高复用率。

功能：
- [x] 常用模板按钮
- [x] 历史搜索
- [x] 收藏结果
- [x] 导出结果

实现说明：
- `AiTemplateManager` 提供会议纪要、代码报错、文案润色、中英翻译、复习问答等快捷模板。
- AI 页面支持历史关键词搜索和收藏筛选，消息卡片可直接收藏/取消收藏。
- 导出使用 Android 分享文本，导出范围跟随当前筛选结果。

### 阶段 5：接入本地模型
目标：降低云端成本。

功能：
- 本地模型管理
- 模型开关
- 本地/云端切换
- 失败后自动兜底

### 阶段 6：扩展自动化
目标：形成差异化。

功能：
- 界面识别
- 任务规划
- 步骤执行
- 自动化脚本

---

## 12. 本地优先(Local First)策略

### 12.1 原则
- 所有低复杂度任务优先本地处理
- 云端 API 只用于高价值、高复杂度任务
- 对用户明确提示本地/云端状态

### 12.2 适合本地完成的任务
- OCR 后处理
- 简单分类
- 指令识别
- 关键词提取
- 简短总结
- 固定模板生成

### 12.3 适合云端完成的任务
- 长文总结
- 复杂改写
- 多步任务规划
- 高质量问答
- 多轮上下文

---

## 13. 成本控制策略(Cost Control)

### 13.1 为什么要控制成本
AI 项目最大的风险之一就是：

- 用户越多，成本越高
- 需求越复杂，推理成本越高
- 如果不控制，很容易亏损

### 13.2 控制手段
- 限制单次输入长度
- 限制上下文轮数
- 限制免费用户频次
- 使用本地小模型处理低价值请求
- 云端只处理高价值请求

### 13.3 建议的分层成本策略
#### 免费层
- 基础 OCR
- 基础总结
- 基础翻译
- 少量问答

#### 高级层
- 长文本总结
- 高级润色
- 自动化任务
- 云端高质量模型

---

## 14. 变现设计(Monetization Plan)

### 14.1 变现能力判断
当前原始项目的变现能力：**中等偏低**。原因是功能虽多，但缺少“必须使用”的核心价值。

经过改造后，若新增 AI 与自动化，变现能力可提升到：**中到高**。

### 14.2 推荐收费方式
#### 方式 1：免费 + 高级功能(Freemium)
- 基础工具免费
- AI 功能收费
- 自动化功能收费

#### 方式 2：会员制(Membership)
- 月付
- 季付
- 年付

#### 方式 3：次数包(Credit Pack)
- 按次计费
- 适合轻量用户

#### 方式 4：功能解锁(Feature Unlock)
- 解锁模板
- 解锁本地模型增强包
- 解锁自动化模块

### 14.3 更容易付费的点
- OCR 批量处理
- 学习资料总结
- 代码报错解释
- 自动签到/自动执行
- 高级模板

---

## 15. 风险分析(Risk Analysis)

### 15.1 技术风险
- 不同手机机型适配差异大
- 本地模型性能不足
- 自动化兼容性复杂
- 权限申请可能导致用户流失

### 15.2 产品风险
- 功能过多导致界面混乱
- 用户找不到核心入口
- AI 功能过于泛化，缺少重点

### 15.3 商业风险
- 用户不愿付费
- 成本控制不足
- 产品缺乏持续使用理由

### 15.4 解决思路
- 先做单一强场景
- 控制功能数量
- 将 AI 作为增强，而不是唯一卖点
- 先免费验证，再逐步收费

---

## 16. 质量标准(Quality Criteria)

### 16.1 可运行性
- 不影响原有功能
- 新功能可独立关闭
- 出错时能回退

### 16.2 可维护性
- 逻辑分层清晰
- 文件命名一致
- 新增模块独立封装

### 16.3 可扩展性
- 模型可替换
- 任务可新增
- 模板可新增
- 自动化流程可新增

### 16.4 用户体验
- 操作路径短
- 入口明确
- 结果输出清晰
- 支持复制、导出、历史查看

---

## 17. 推荐交付物(Deliverables)

后续建议继续输出以下文档或文件：

1. 页面结构图(Page Map)
2. 文件改造清单(File List)
3. 模块依赖图(Dependency Graph)
4. AI 编程提示词(Prompt)
5. MVP 开发顺序表
6. 数据表设计(Data Schema)
7. 接口设计(API Spec)
8. 变现方案说明(Monetization Spec)

---

## 18. 最终建议

如果你要让这个项目真正有价值，最合理的路线不是继续加小游戏，而是：

> **把现有 GameMatrixApp 改造成一个“工具箱 + AI + 自动化”的效率型应用。**

这条路线的优点是：
- 成本低于重做一个新产品
- 比纯工具箱更有差异化
- 比纯聊天 AI 更容易变现
- 比纯小游戏更有长期价值

---

## 19. 下一步建议

下一步最值得继续做的三件事：

1. 输出**页面结构图(Page Map)**
2. 输出**文件改造清单(File List)**
3. 输出**AI 编程提示词(Prompt)**

这三项做完后，就可以开始实际拆分开发任务。
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平台 Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题。
- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言。
- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项。
- 发布前检查需覆盖中文/英文两种语言、深色/浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮。
## 2026-05-15 文档同步：Dependabot 与 CI 修复

- Dependabot 安全告警已处理：升级 Android Gradle Plugin 到 8.13.2、Gradle Wrapper 到 8.13、Kotlin 到 2.2.21、Hilt 到 2.57.2。
- 构建 classpath 已强制解析到安全版本：Netty 4.1.133.Final、BouncyCastle 1.84、commons-compress 1.26.0、jose4j 0.9.6、jdom2 2.0.6.1。
- GitHub Actions 已改为验证型 CI：使用 JDK 21，执行 debug 构建与单元测试，不在云端构建 release 包，避免暴露或依赖 release 签名文件。
- CI 命令统一添加 `-PautoBumpVersion=false`，避免自动修改 `version.properties`。
- `.gitignore` 的 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/GameMatrix/app/ai/data/` 源码。
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 服务器部署/GitHub Release 发布仍以本机发布流程为准。

## 模块市场开发约束（AI代理必读）

> ⚠️ 以下规则是因历史bug总结的约束，AI代理在修改模块市场相关代码时必须遵守。

1. **禁止创建指向不存在文件的downloadUrl**：modules.json中的downloadUrl必须指向VPS上实际存在的文件。如果模块代码在主APK中，必须设置`builtIn: true`并将downloadUrl留空。
2. **builtIn模块不需要dex文件**：当`builtIn=true`时，ModuleAdapter显示"启用"按钮而非"下载"，ModuleManager直接标记已安装。不要为内置模块编译dex文件。
3. **新增模块时先确认代码位置**：如果模块的Activity/Fragment代码在主APK的源码目录中，该模块必须标记为`builtIn=true`。只有代码完全独立于主APK（通过DexClassLoader动态加载）的模块才能设置`builtIn=false`并提供downloadUrl。
4. **上传modules.json前验证**：每次修改deploy/modules.json后，必须同时：(a) 上传到VPS的/var/www/update/modules.json；(b) 如果有非builtIn模块，确保对应dex文件已上传到VPS的/var/www/update/modules/目录。
5. **fileSize和sha256必须真实**：非builtIn模块的fileSize和sha256必须与VPS上实际dex文件一致，不能留空或填0。builtIn模块的fileSize填0、sha256留空。

- 2026-05-24 游戏美化+中国象棋提示改进+华容道&中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌面/五子棋木纹3D棋子/华容道深色渐变金色边框/中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光环+箭头指引）+中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
