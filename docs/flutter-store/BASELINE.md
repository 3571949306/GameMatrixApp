<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 本文档记录 vc595 / 已签名 Catalog V8 的基线。它不是当前全局事实来源；当前版本、活动实现与发布门槛见 [`../CURRENT_STATE.md`](../CURRENT_STATE.md)。

# Flutter-first 模块商店迁移基线

记录日期：2026-07-21  
项目：`D:\Developmment\GameMatrixApp`

## 工作树保护

任务开始前工作树已有 15 个已修改文件和未跟踪的 `test_artifacts/`。本次迁移不得覆盖或回退这些内容。

明确属于在途游戏人机 AI 的文件：

- `app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessAI.java`
- `app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessActivity.java`
- `app/src/main/java/com/gamecenter/app/games/chinesechess/ChineseChessView.java`
- `app/src/main/java/com/gamecenter/app/games/go/GoActivity.java`

其他任务开始前已有修改包括 Catalog/模块清单、预装错题本 APK、TTS/AI/工具/VPN 模块构建配置、版本与测试文档。这些文件同样按用户工作保护；确需连接时只允许增量编辑并单独说明。

## 工具链

| 项目 | 基线 |
|---|---|
| Flutter | 3.44.1 stable（`D:\Developmment\flutter`，未加入 PATH） |
| Dart | 3.12.1 |
| Android SDK | 37.0.0，项目 compile/target SDK 仍为 35 |
| JDK | Android Studio JBR 21.0.10；项目 Gradle 仍以现有配置为准 |
| 真机 | 小米 M2012K10C，Android 13 / API 33，USB serial `w4dm4dssby7xcunv` |

Flutter SDK 自身已有本地修改且 stable 落后远端；迁移过程不执行 `flutter upgrade`、`git pull` 或其他 SDK Git 写操作。

## 现有架构

- 原生商店入口：`ModuleStoreActivity`
- 权威旧状态源：`ModuleManager` + `module_manager_prefs`
- 下载/校验：`ModuleDownloader`、`ModuleVerifier`、`ModuleSignatureVerifier`
- 事务目录：`staging` / `current` / `last_good` / `quarantine`
- 事务安装与回滚：`TransactionInstaller`
- 动态 Android 模块：`ModuleLoader` + `DexClassLoader`
- 目录与 Ed25519 校验：`StoreCatalogRepository` + `CatalogSignatureVerifier`
- Unity 入口：`UnityModuleManager`
- 旧 `modules.json` 顶层使用 `version`，没有 `schemaVersion`/`runtimeType`/`deliveryType`

## 构建与测试基线

所有 Gradle 命令均显式使用：

```text
-PautoUploadVps=false -PautoBumpVersion=false
```

| 命令 | 结果 |
|---|---|
| `:app:assembleDebug` | 通过，19 秒（增量构建） |
| `:app:testDebugUnitTest` | 失败；任务开始前已有 8 个 Java 编译错误 |
| `:app:lintDebug` | 通过，2 分 9 秒 |

单测基线失败原因：`ModuleDependencyTest.java` 的 6 处 `new ModuleManifest(...)` 仍使用旧构造器签名，而规范模型已经扩展为 41 个参数。该问题不来自本次 Flutter 迁移，也不来自在途游戏 AI。

## 迁移护栏

- Flutter 只负责 UI 与 UI 偏好，不创建安装状态数据库或目录缓存。
- APK/DEX、签名、SHA-256、事务安装、回滚、服务和 Unity 生命周期继续由 Kotlin/Android 管理。
- 不动态下载/执行 Dart 源码。
- 新商店以默认关闭的构建开关接入；初始化失败或开关关闭时保留原生商店。
- 不删除旧商店、现有模块 APK、现有测试或用户数据。
- 每阶段执行实际编译、测试；最终安装并检查目标路径 logcat。