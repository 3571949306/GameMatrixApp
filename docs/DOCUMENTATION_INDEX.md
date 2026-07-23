<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# GameMatrixApp 全局文档索引

项目全部文档的中枢链接。最后更新：2026-07-23。所有文档均已加入 Flutter-first 状态或历史快照提示；Flutter 化的权威事实以本页列出的 `docs/flutter-store/` 文档集为准。

## docs/flutter-store — Flutter-first 模块商店权威文档

| 文档 | 描述 |
|---|---|
| [MIGRATION_STATUS.md](/docs/flutter-store/MIGRATION_STATUS.md) | 总进度、范围边界、收尾验收与发布判断（首要真值） |
| [ARCHITECTURE.md](/docs/flutter-store/ARCHITECTURE.md) | Add-to-App、缓存 Engine、Facade、状态源和安全边界 |
| [CATALOG_V2.md](/docs/flutter-store/CATALOG_V2.md) | Catalog V2 协议、旧目录适配和验签阻塞 |
| [BRIDGE_API.md](/docs/flutter-store/BRIDGE_API.md) | Pigeon Host/Flutter API 与事件合同 |
| [RUNTIME_HANDLERS.md](/docs/flutter-store/RUNTIME_HANDLERS.md) | 六类 Runtime 生命周期、能力与限制 |
| [TEST_PLAN.md](/docs/flutter-store/TEST_PLAN.md) | 自动化、真机证据和未覆盖矩阵 |
| [ROLLBACK.md](/docs/flutter-store/ROLLBACK.md) | 功能开关、旧商店、安装事务和回滚路径 |
| [BASELINE.md](/docs/flutter-store/BASELINE.md) | 任务开始时的工具链、工作树和测试基线 |

## 根目录 (Root)

| 文档 | 描述 |
|---|---|
| [README.md](/README.md) | 项目总览、功能介绍 |
| [RELEASE_NOTES.md](/RELEASE_NOTES.md) | 当前版本面向用户的简短更新公告 |
| [CHANGELOG.md](/CHANGELOG.md) | 完整开发历史，不直接作为 Release 公告 |
| [CODE_WIKI.md](/CODE_WIKI.md) | 详细代码架构说明 |
| [CLOUD-BUILD.md](/CLOUD-BUILD.md) | 云编译 & VPS 部署指南 |
| [修改记录.md](/修改记录.md) | 24 轮修复循环的完整变更历史 |
| [AGENTS.md](/AGENTS.md) | AI Coding 规则（必读） |
| [待删除文件清单.md](/待删除文件清单.md) | 按规则 22 登记的待删除文件清单 |
| [TEST_ISSUES_2026-06-27.md](/TEST_ISSUES_2026-06-27.md) | 2026-06-27 测试问题记录 |

## docs/ — 主文档

| 文档 | 描述 |
|---|---|
| [AI_CONTEXT.md](/docs/AI_CONTEXT.md) | AI 上下文文档（合并自 4 个文档，供 AI 助手和新开发者阅读） |
| [PROJECT_STATUS.md](/docs/PROJECT_STATUS.md) | 项目状态总览（合并自 7 个审计文档，包含问题/规划/测试/报告） |
| [DOCUMENTATION_INDEX.md](/docs/DOCUMENTATION_INDEX.md) | 本文档 — 全局文档索引 |
| [项目改进建议书.md](/docs/项目改进建议书.md) | 项目改进建议书 v7 |
| [game_center_app_ai_roadmap.md](/docs/game_center_app_ai_roadmap.md) | AI 开发路线图 |
| [DONT_DO_THIS.md](/docs/DONT_DO_THIS.md) | 写给 AI / 协作者：不要做 |
| [FEATURE_FLAGS.md](/docs/FEATURE_FLAGS.md) | Feature Flag 索引（88 个，含默认关闭的 Flutter 商店/区块渲染器/动态游戏大厅开关），含用途、引入版本、退役计划与治理规则 |
| [PUBLISH_GUIDE.md](/docs/PUBLISH_GUIDE.md) | 发布指南 |
| [SECURITY.md](/docs/SECURITY.md) | 安全策略 |
| [NETWORK_LAYER.md](/docs/NETWORK_LAYER.md) | 网络层架构 |
| [ERROR_HANDLING.md](/docs/ERROR_HANDLING.md) | 错误处理 & Result 类型 |
| [PROGUARD_AUDIT.md](/docs/PROGUARD_AUDIT.md) | ProGuard / R8 审计 |
| [GRADLE_BUILD_CACHE.md](/docs/GRADLE_BUILD_CACHE.md) | Gradle Build Cache 配置 |
| [BASELINE_PROFILE.md](/docs/BASELINE_PROFILE.md) | Baseline Profile (启动加速) |
| [COMPOSE_MIGRATION.md](/docs/COMPOSE_MIGRATION.md) | Compose 迁移指南 |
| [ROOM_MIGRATION.md](/docs/ROOM_MIGRATION.md) | Room 数据库迁移 |
| [UNITY_UPDATE_READINESS_AUDIT.md](/docs/UNITY_UPDATE_READINESS_AUDIT.md) | Unity Update Readiness Audit |
| [ADB_REAL_DEVICE_TEST_PLAN.md](/docs/ADB_REAL_DEVICE_TEST_PLAN.md) | ADB 真机全量测试计划（含 2026-07-20 小米 ares 测试记录） |
| [FILES_TO_DELETE_BATCH21.md](/docs/FILES_TO_DELETE_BATCH21.md) | Batch 21 待删除文件清单（Hilt DI 引用暂不删除） |
| [FILES_TO_REVIEW_HYBRID_STORE.md](/docs/FILES_TO_REVIEW_HYBRID_STORE.md) | 混合商店改造待 Review 文件清单 |

## docs/module-docs — 模块说明

| 文档 | 描述 |
|---|---|
| [游戏模块说明.md](/docs/module-docs/游戏模块说明.md) | 游戏模块说明（含 hall/chinesechess/game2048/klotski/tts 等动态游戏模块） |
| [浏览器模块说明.md](/docs/module-docs/浏览器模块说明.md) | 浏览器模块说明（循环19原生重构：bridge/core/data/security/ui） |
| [工具箱模块说明.md](/docs/module-docs/工具箱模块说明.md) | 工具箱模块说明 |
| [VPN模块说明.md](/docs/module-docs/VPN模块说明.md) | VPN 模块说明 |
| [错题本模块说明.md](/docs/module-docs/错题本模块说明.md) | 错题本（wrongbook）模块说明（循环20预装集成，循环21-22全面推进） |
| [games_split_summary.md](/docs/module-docs/games_split_summary.md) | 小游戏拆分剥离项目总结 |

## docs/modules — 模块商店

| 文档 | 描述 |
|---|---|
| [MODULE_DEVELOPMENT_GUIDE.md](/docs/modules/MODULE_DEVELOPMENT_GUIDE.md) | 模块开发指南 |
| [MODULE_STORE_POLICY.md](/docs/modules/MODULE_STORE_POLICY.md) | 模块商店策略 |
| [MODULE_STORE_REDESIGN_PLAN.md](/docs/modules/MODULE_STORE_REDESIGN_PLAN.md) | 模块商店重新设计计划 |
| [P3_IMPLEMENTATION_PLAN.md](/docs/modules/P3_IMPLEMENTATION_PLAN.md) | P3 目录签名 + 事务安装实施计划（已完成） |
| [PUBLISH_GUIDE.md](/docs/modules/PUBLISH_GUIDE.md) | 模块发布指南 |
| [模块变更日志/](/docs/modules/模块变更日志/) | 各模块变更日志（游戏/浏览器/工具箱/AI/VPN） |

## docs/refactor — 重构计划

| 文档 | 描述 |
|---|---|
| [MASTER-REFACTOR-PLAN.md](/docs/refactor/MASTER-REFACTOR-PLAN.md) | 完整改造计划 |
| [track-ai.md](/docs/refactor/track-ai.md) | AI 助手模块改造设计 |
| [track-platform.md](/docs/refactor/track-platform.md) | 模块化架构 + UI 主题改造 |
| [track-tools.md](/docs/refactor/track-tools.md) | 工具箱模块化改造设计 |
| [track-vpn.md](/docs/refactor/track-vpn.md) | VPN 模块改造设计 |

## docs/audits — 审计记录

| 文档 | 描述 |
|---|---|
| [2026-06-25/](/docs/audits/2026-06-25/) | 安全审查、漏洞修复、模块重构总结 |

## docs/archive — 历史归档（44 个文档）

历史版本的发布说明、架构设计、测试报告等。内容较旧，仅供参考。

## 其他位置

| 文档 | 描述 |
|---|---|
| [项目审计_2026-06-19/](/项目审计_2026-06-19/) | 2026-06-19 审计快照（10 个文档） |
| [scripts/automated_test/README.md](/scripts/automated_test/README.md) | 自动化测试系统说明 |
| [docs/superpowers/specs/](/docs/superpowers/specs/) | Modular Shell APK 设计规范 |
