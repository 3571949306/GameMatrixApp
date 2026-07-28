# GameMatrixApp 文档索引

> **状态**：当前导航
> **最后核验**：2026-07-27
> **使用规则**：先读 [`DOCUMENTATION_GOVERNANCE.md`](DOCUMENTATION_GOVERNANCE.md)，再按任务进入相应资料。当前事实以代码、配置和 [`CURRENT_STATE.md`](CURRENT_STATE.md) 为准；路线图和审计是日期化快照，不代替当前实现。

## 先读什么

| 目的 | 文档 | 角色 |
|---|---|---|
| 了解当前版本、能力、发布门槛和用户路径 | [`CURRENT_STATE.md`](CURRENT_STATE.md) | 当前参考 |
| 了解文档的权威层级与更新规则 | [`DOCUMENTATION_GOVERNANCE.md`](DOCUMENTATION_GOVERNANCE.md) | 当前规则 |
| 了解文档语言与术语规范 | [`DEVELOPER_TERMINOLOGY.md`](DEVELOPER_TERMINOLOGY.md) | 当前术语规范 |
| 了解未来产品定位与体验方向 | [`PRODUCT_DIRECTION_AND_UX.md`](PRODUCT_DIRECTION_AND_UX.md) | 当前产品方向 |
| 查看当前执行计划 | [`EXECUTION_PLAN_2026H2.md`](EXECUTION_PLAN_2026H2.md) | 第 1–28 条建议的阶段性执行计划 |
| 了解用户可见功能、安装、权限与隐私 | [`../README.md`](../README.md) | 用户文档 |
| 查看已发布版本的简短更新说明 | [`../RELEASE_NOTES.md`](../RELEASE_NOTES.md) | 用户发布公告 |
| 追溯开发历史 | [`../CHANGELOG.md`](../CHANGELOG.md) | 历史记录 |

## 当前技术参考

| 主题 | 文档 | 说明 |
|---|---|---|
| 代码架构与目录职责 | [`../CODE_WIKI.md`](../CODE_WIKI.md) | 代码结构参考；历史维护记录以日期为准 |
| 项目状态与已知发布门槛 | [`PROJECT_STATUS.md`](PROJECT_STATUS.md) | 当前状态摘要，需与 `CURRENT_STATE.md` 一起阅读 |
| 功能开关 | [`FEATURE_FLAGS.md`](FEATURE_FLAGS.md) | BuildConfig 开关、默认值和退役规则 |
| 产品/架构基线 | [`SPEC.md`](SPEC.md) | v1.0 决策基线；实现进度以当前状态为准 |
| 安全 | [`SECURITY.md`](SECURITY.md) | 安全策略与开发约束 |
| 网络层 | [`NETWORK_LAYER.md`](NETWORK_LAYER.md) | 网络、联机与服务依赖 |
| 错误处理 | [`ERROR_HANDLING.md`](ERROR_HANDLING.md) | 错误模型与用户可见错误处理 |
| Compose 迁移 | [`COMPOSE_MIGRATION.md`](COMPOSE_MIGRATION.md) | 渐进迁移策略 |
| Room 迁移 | [`ROOM_MIGRATION.md`](ROOM_MIGRATION.md) | 数据库演进约束 |
| Baseline Profile | [`BASELINE_PROFILE.md`](BASELINE_PROFILE.md) | 启动性能基线 |
| R8/ProGuard | [`PROGUARD_AUDIT.md`](PROGUARD_AUDIT.md) | 混淆与发布收缩 |
| 云构建与部署 | [`../CLOUD-BUILD.md`](../CLOUD-BUILD.md) | 构建/部署参考；产物状态须实时复核 |
| 应用与模块发布 | [`PUBLISH_GUIDE.md`](PUBLISH_GUIDE.md) | 发布流程与验证 |
| 真机验证 | [`ADB_REAL_DEVICE_TEST_PLAN.md`](ADB_REAL_DEVICE_TEST_PLAN.md) | Android 真机测试矩阵与历史证据 |

## 模块商店与 Flutter 技术资料

以下文档记录 Flutter-first 商店和多 Runtime 的架构/发布证据。文件中关于 vc595、Catalog V8 的内容是当时发布快照；当前版本和实时门槛请回到 [`CURRENT_STATE.md`](CURRENT_STATE.md)。

| 文档 | 主题 |
|---|---|
| [`flutter-store/ARCHITECTURE.md`](flutter-store/ARCHITECTURE.md) | Add-to-App、Engine、Facade 和权威边界 |
| [`flutter-store/CATALOG_V2.md`](flutter-store/CATALOG_V2.md) | Catalog V2 协议与签名目录 |
| [`flutter-store/BRIDGE_API.md`](flutter-store/BRIDGE_API.md) | Pigeon Host/Flutter 合同 |
| [`flutter-store/RUNTIME_HANDLERS.md`](flutter-store/RUNTIME_HANDLERS.md) | Android、Flutter、Web、Asset、Native Service、Unity Runtime |
| [`flutter-store/ROLLBACK.md`](flutter-store/ROLLBACK.md) | 功能/安装回滚路径 |
| [`flutter-store/TEST_PLAN.md`](flutter-store/TEST_PLAN.md) | Flutter 商店测试证据与覆盖边界 |
| [`flutter-store/BASELINE.md`](flutter-store/BASELINE.md) | Flutter 商店改造基线 |
| [`modules/MODULE_DEVELOPMENT_GUIDE.md`](modules/MODULE_DEVELOPMENT_GUIDE.md) | 模块作者开发与发布约定 |
| [`modules/MODULE_STORE_POLICY.md`](modules/MODULE_STORE_POLICY.md) | 模块商店策略 |
| [`modules/MODULE_STORE_REDESIGN_PLAN.md`](modules/MODULE_STORE_REDESIGN_PLAN.md) | 商店改造计划快照 |
| [`modules/P3_IMPLEMENTATION_PLAN.md`](modules/P3_IMPLEMENTATION_PLAN.md) | P3 实施计划与验收历史 |
| [`modules/PUBLISH_GUIDE.md`](modules/PUBLISH_GUIDE.md) | 模块发布指南 |
| [`modules/STORE_HYBRID_PHASE_BASELINE.md`](modules/STORE_HYBRID_PHASE_BASELINE.md) | 混合商店阶段基线 |

## 用户能力与模块说明

| 文档 | 说明 |
|---|---|
| [`module-docs/游戏模块说明.md`](module-docs/游戏模块说明.md) | 游戏模块与大厅 |
| [`module-docs/浏览器模块说明.md`](module-docs/浏览器模块说明.md) | 浏览器能力、数据与隐私 |
| [`module-docs/工具箱模块说明.md`](module-docs/工具箱模块说明.md) | 工具箱能力 |
| [`module-docs/VPN模块说明.md`](module-docs/VPN模块说明.md) | VPN 模块边界与说明 |
| [`module-docs/错题本模块说明.md`](module-docs/错题本模块说明.md) | 错题本功能与数据流程 |
| [`module-docs/games_split_summary.md`](module-docs/games_split_summary.md) | 小游戏拆分历史 |
| [`modules/GAME_EVALUATION_REPORT_2026-07-22.md`](modules/GAME_EVALUATION_REPORT_2026-07-22.md) | 27 款游戏评估快照 |

## 路线图与计划（历史/决策快照）

这些文件记录提出计划时的版本、问题和估算。它们不宣称实时状态；若内容与实现冲突，以 [`CURRENT_STATE.md`](CURRENT_STATE.md) 和源代码为准。

| 文档 | 主题 |
|---|---|
| [`ROADMAP_MVP_CONVERGENCE.md`](ROADMAP_MVP_CONVERGENCE.md) | 产品定位收束与 MVP 计划 |
| [`ROADMAP_BUSINESS_SUSTAINABILITY.md`](ROADMAP_BUSINESS_SUSTAINABILITY.md) | 用户价值闭环与商业化探索 |
| [`ROADMAP_ARCHITECTURE_KOTLIN.md`](ROADMAP_ARCHITECTURE_KOTLIN.md) | 架构收敛与 Kotlin 迁移 |
| [`ROADMAP_TEST_UPGRADE.md`](ROADMAP_TEST_UPGRADE.md) | 测试体系升级 |
| [`ROADMAP_SERVER_HA.md`](ROADMAP_SERVER_HA.md) | 服务端高可用 |
| [`RENOVATION_MASTER_PLAN.md`](RENOVATION_MASTER_PLAN.md) | 全面改造总计划 |
| [`game_center_app_ai_roadmap.md`](game_center_app_ai_roadmap.md) | AI 发展计划与历史阶段 |
| [`项目改进建议书.md`](项目改进建议书.md) | 历史改进与风险建议汇总 |
| [`refactor/MASTER-REFACTOR-PLAN.md`](refactor/MASTER-REFACTOR-PLAN.md) | 重构任务规划 |
| [`refactor/track-ai.md`](refactor/track-ai.md) | AI 模块重构方案 |
| [`refactor/track-platform.md`](refactor/track-platform.md) | 平台与主题改造方案 |
| [`refactor/track-tools.md`](refactor/track-tools.md) | 工具箱改造方案 |
| [`refactor/track-vpn.md`](refactor/track-vpn.md) | VPN 改造方案 |

## 历史与审计

- [`archive/`](archive/)：已归档的发布、迁移、性能与网络资料，仅供追溯。
- [`audits/`](audits/)：日期化审计任务和 walk-through。
- [`../项目审计_2026-06-19/`](../项目审计_2026-06-19/)：2026-06-19 审计快照。
- [`compose/spec/`](compose/spec/)：特定 Compose 功能规范。
- [`superpowers/`](superpowers/)：历史计划和规格。

## 文档核验

运行以下命令检查当前文档链接、版本声明和本地 Catalog 摘要：

```bash
python tools/audit_markdown_docs.py
```

脚本只检查文档一致性；涉及 APK、模块签名、真实下载、安装、更新或回滚时，仍必须按发布/真机测试文档完成实际验证。
