# GameMatrixApp Documentation Index

Last updated: 2026-05-25

## Canonical docs

- `README.md`
  - External-facing overview.
  - Keep this focused on project introduction, local build, and the shortest usable release entrypoints.

- `PROJECT_CONTEXT.md`
  - Maintainer context and repo operating constraints.
  - Use this as the default handoff/start-here document for future repo work.

- `CODE_WIKI.md`
  - Code structure and module-level technical reference.
  - Do not treat it as the source of truth for release state or temporary rollout notes.

- `项目改进建议书.md`
  - Ongoing maintenance and governance document.
  - Record documentation cleanup decisions and remaining drift here.

- `CHANGELOG.md`
  - Version history and user-visible changes.

- `AI_CONTEXT.md`
  - AI assistant onboarding context for new developers and AI coding tools.
  - Covers module marketplace, dynamic navigation, and game registry changes.

- `文档/PUBLISH_GUIDE.md`
  - Single publishing guide.

- `文档/LOCAL_GITHUB_NETWORK.md`
  - Local GitHub connectivity and proxy recovery notes for this Windows machine.

- `文档/MODULE_STORE_POLICY.md`
  - Current module store behavior: default game category, no all-tab, store-only game launch, VPS module paths, and publish checks.

## Module Changelogs

Per-module version history and change records:

- `文档/模块变更日志/游戏模块变更日志.md`
  - 游戏模块变更记录：内置游戏、模块商店上架游戏、视觉美化、功能改进等

- `文档/模块变更日志/浏览器模块变更日志.md`
  - 浏览器模块变更记录：多标签页、书签管理、文件下载、稳定性修复等

- `文档/模块变更日志/工具箱模块变更日志.md`
  - 工具箱模块变更记录：20+实用工具、ToolBinder架构、稳定性修复等

- `文档/模块变更日志/AI助手模块变更日志.md`
  - AI助手模块变更记录：多AI提供商、Gemma本地推理、对话功能、稳定性修复等

- `文档/模块变更日志/VPN模块变更日志.md`
  - VPN模块变更记录：四协议支持、VpnServiceProxy桥接、模块架构、修复记录等

## Archived docs

The following documents were moved out of the repo root because they were historical, duplicated other guides, or had mixed old/new release content:

- `文档/archive/context/AI_CONTEXT.md`
- `文档/archive/publish/PUBLISH_SYSTEM_OVERVIEW.md`
- `文档/archive/publish/UPLOAD_INSTRUCTIONS.md`
- `文档/archive/publish/AUTO_PUBLISH_README.md`
- `文档/archive/releases/RELEASE_COMPLETE.md`
- `文档/archive/releases/RELEASE_STATUS.md`
- `文档/archive/releases/RELEASE_SUMMARY_1.3.18.md`
- `文档/archive/network/联机架构改造说明.md`
- `文档/archive/network/WEBSOCKET_MIGRATION.md`
- `文档/releases/RELEASE_NOTES_v1.3.27.md`

## Maintenance rules

1. One document should have one job.
2. Release snapshots belong in `CHANGELOG.md` or versioned release notes, not in long-lived context docs.
3. `README.md`, `PROJECT_CONTEXT.md`, and `CODE_WIKI.md` must not each carry separate version tables that drift independently.
4. Historical migration reports should live under `文档/archive/`, not in the repo root.
5. When code structure changes, update `PROJECT_CONTEXT.md` and `CODE_WIKI.md` together; when release state changes, update `CHANGELOG.md` and versioned release notes.
6. When the module marketplace architecture changes (new module types, dynamic navigation rules, game category changes), update `CODE_WIKI.md` section 4.2a, `PROJECT_CONTEXT.md` section 4, and `AI_CONTEXT.md` section 2.3a together.

## 2026-05-24 文档同步
- 底部导航切换闪退修复：创建 KeepStateNavigator 自定义导航器，使用 add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日志
- 内存泄漏全面修复：移除 WeakReference callback、Fragment 回调安全检查、视图引用彻底清理
- 压力测试通过：40轮快速Tab切换无崩溃

- 2026-05-24 游戏美化+中国象棋提示改进+华容道&中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌面/五子棋木纹3D棋子/华容道深色渐变金色边框/中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光环+箭头指引）+中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店

## 2026-05-25 文档同步：模块商店目录结构重组
- **新目录结构创建**：在项目根目录创建 `模块商店/` 文件夹
- **压缩模块迁移**：将 `deploy/modules/` 下的所有游戏压缩包移至 `模块商店/压缩模块/`
- **功能模块迁移**：将 `feature/games/` 移至 `模块商店/功能模块/游戏/games/`，将 `feature/vpn/` 移至 `模块商店/功能模块/工具/vpn/`
- **模块清单复制**：将 `deploy/modules.json` 和 `deploy/modules_v2.json` 复制到 `模块商店/` 目录（deploy目录保留备份）
- **构建配置更新**：更新 `settings.gradle` 文件中的模块引用路径
- **文档创建**：在 `模块商店/` 目录下创建 `模块商店结构说明.md`，详细说明新的目录结构
- **备份保留**：原 `deploy/` 目录仍保留所有文件作为备份，确保可回滚
- **飞刀游戏上传**：将飞刀大师游戏（v1.0.0）添加至模块商店
