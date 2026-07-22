<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# Flutter Store 待删除清单

本任务未删除任何现有源码、资源、配置或文档。以下仅是未来达到稳定门槛后可重新评估的候选；当前均不得删除。

| 文件路径 | 当前职责 | 替代文件 | 引用检查 | 建议删除原因 | 删除风险 | 删除前验证步骤 |
|---|---|---|---|---|---|---|
| `app/src/main/java/com/gamecenter/app/modules/ModuleStoreActivity.kt` | 旧原生商店与失败回退 | Flutter `/store` + `ModuleCoreFacade` | 搜索 manifest、MainActivity、模块入口、测试与文档引用 | 未来减少重复 UI | 失去稳定回退及部分旧交互 | Flutter 默认启用多个版本；48 场景全过；确认无专属动作；用户明确批准 |
| `app/src/main/res/layout/activity_module_store.xml` 及旧商店专用资源 | 旧原生商店 UI | Flutter 页面 | `aapt2`/`rg` 全局资源引用 | 未来减少资源体积 | 动态模块或回退页面资源崩溃 | 删除候选逐项 `rg`；Debug/Release 构建；真机 legacy 回退；用户批准 |
| `flutter_module/.android/` | Flutter module 的本地生成 host 工程 | `flutter_module/host_include.gradle` + Flutter 工具再生成 | Gradle source integration 当前仍引用其 `Flutter` 子工程 | 仅属于工具生成目录，可能造成噪音 | 删除后离线构建或 IDE 同步失败 | 新 checkout 执行 `flutter pub get` 和宿主 build；确认忽略策略；仅删可再生缓存 |

`ModuleManager`、下载/签名/事务安装、旧目录适配、用户模块目录和游戏业务源码不是删除候选。