<!-- flutter-store-doc-sync: 2026-07-22; authoritative -->
> **Flutter-first authoritative document:** Production stable vc595 and signed Catalog V8 are live and verified; this document and current build evidence are the release truth.

# Flutter Store 回退手册

本迁移默认可逆，且不删除旧原生商店或现有模块数据。

## 运行时立即回退

1. 构建时不传 `-PenableFlutterModuleStore=true`（默认值为 `false`）。
2. `ModuleStoreActivity` 会直接显示原生商店。
3. Flutter 商店内的菜单可调用 `openLegacyStore`，以 `force_legacy_module_store=true` 打开旧页面，避免再次重定向。
4. Flutter Engine 初始化失败时，兼容入口记录错误并继续原生页面。

该操作不改动 `module_manager_prefs`、模块 `current/last_good`、目录缓存或已安装数据。

## 模块级回滚

- Android APK：经 `ModuleCoreFacade.rollbackModule` 调用现有 `TransactionInstaller.rollback`，并交换记录的 current/last-good 版本。
- Web/Asset：`SecureArchiveInstaller.rollback` 把 current 隔离到 quarantine，再恢复 last_good。
- Flutter 内置 route：随宿主版本回退，不下载 Dart；可禁用非 required route。
- Unity/Native Service：通过各自 handler 和既有管理器回滚；若没有 last_good，返回结构化 `rollback_unavailable`，不伪造成功。

## 源码配置回退（由维护者执行）

若需要从产品中完全停止实验，保持旧代码不删，只需维持功能开关默认 false，并从入口配置中不显示 Flutter 实验入口。不要删除 `ModuleStoreActivity`、旧布局、`ModuleManager`、TransactionInstaller 或用户模块目录。

若未来要移除本次源文件，应先按 `docs/PENDING_DELETIONS_FLUTTER_STORE.md` 的引用检查和测试步骤执行；本任务未获授权，不做文件删除，也不执行 Git 回滚命令。

## 数据恢复

发生失败时优先顺序：保留 current；若 current 不可用则 last_good；可疑包进入 quarantine；下载/刷新失败继续使用上次有效 Catalog。只有用户明确卸载模块时才按对应 handler 删除该模块隔离目录，不能清空整个 `files/modules`。