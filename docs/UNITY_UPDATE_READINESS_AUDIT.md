<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 下文任何关于 vc595 / 已签名 Catalog V8 的陈述均记录 2026-07-22 的发布证据。它不是当前工作树或生产状态的声明；实时事实请查阅项目根目录的 `docs/CURRENT_STATE.md` 与当前实现。

# Unity 更新就绪审计

日期：2026-06-21

范围：

- 主应用：`D:\Developmment\GameMatrixApp`
- 早期 Unity 项目：`D:\unity\ZombieGateShooter`
- 现有 Unity 集成规划文档：`D:\unity\integration-poc\docs`

## 结论

主应用在首个 Unity 概念验证之前不需要大规模源码重写。它已具备远程模块清单、模块下载、SHA-256 验证、本地缓存和主 APK 更新路径。

但 Unity 不应被当作又一个普通 `ModuleManifest` 包处理。当前模块系统按"每个模块一个可安装产物"设计，而早期开发中的 Unity 游戏需要频繁更新内容、配置、平衡数据，有时还包括运行时或原生代码。这些更新层需要不同的安全规则。

建议方向：

1. 保持主应用稳定，仅在宿主能力、bridge API、权限、ABI 打包或 Unity 运行时嵌入变化时才更新主应用。
2. 增加独立的 Unity 更新 manifest 和 content updater，而不是塞进现有 `modules.json`。
3. 让 Unity 内容和配置通过分阶段下载、SHA-256 验证、原子切换和回滚独立更新。
4. 仅在首个可玩的 Unity 构建可导出且 bridge 合同明确后，才在主应用加入 `ZombieGateShooter` 入口。

## 主应用当前能力

### 远程模块系统

证据：

- `app/src/main/java/com/gamecenter/app/modules/ModuleManifest.kt`
- `app/src/main/java/com/gamecenter/app/modules/ModuleDownloader.kt`
- `app/src/main/java/com/gamecenter/app/modules/ModuleManager.kt`
- `app/src/main/assets/modules.json`

已可工作：

- 主应用能从远程 URL 读取模块 manifest。
- 能在本地缓存模块元数据。
- 把模块文件下载到应用私有存储。
- 用 SHA-256 校验模块包完整性。
- 能从 `assets/modules.json` 注册内置模块元数据。

限制：

- manifest 描述的是单个模块产物，字段包括 `fileName`、`sha256`、`downloadUrl`、`entryClass`、`versionCode`。
- 不建模 Unity 特有版本，如宿主 bridge 版本、Unity 运行时版本、游戏逻辑版本、内容版本、配置版本、灰度、kill switch、分阶段安装或 last-good 回滚。

### 动态代码加载

证据：

- `app/src/main/java/com/gamecenter/app/modules/ModuleLoader.kt`

已可工作：

- 当前加载器使用 `DexClassLoader`。
- 期望一个实现了应用模块接口的 Java/Kotlin 模块入口类。

限制：

- 适合 feature APK 或 Dex 风格模块。
- 不是 Unity 内容更新的正确主机制。
- 不应作为加载 Unity 原生运行时、IL2CPP 产物或 asset bundle 的主策略。

### 壳入口

证据：

- `app/src/main/java/com/gamecenter/app/features/ModuleShellFragment.kt`
- `app/src/main/java/com/gamecenter/app/features/SmartModuleLoader.kt`

已可工作：

- 主应用能为模块显示壳页面。
- 现有壳路由知道当前顶层 feature：游戏大厅、浏览器、工具、AI 和 VPN。

限制：

- 还没有 `ZombieGateShooter` 入口。
- smart loader 对当前 Unity 回退价值有限，因为内置 Fragment 创建目前不会返回 Unity 屏幕。

### APK 更新

证据：

- `core/update/src/main/java/com/gamecenter/app/update/UpdateInfo.java`
- `core/update/src/main/java/com/gamecenter/app/update/UpdateDownloader.java`

已可工作：

- 主应用具备 APK 更新路径，支持多下载源和文件大小校验。

限制：

- APK 更新元数据当前使用 MD5。
- 模块包已使用 SHA-256。Unity 运行时或主 APK 更新也应迁移到 SHA-256。

### 存档

证据：

- `app/src/main/java/com/gamecenter/app/SaveManager.java`

已可工作：

- 当前存档管理器通过 `SharedPreferences` 存储 JSON 字符串。

限制：

- 对早期小体量进度 JSON 可接受。
- 不适合大体量或频繁写入的 Unity 存档数据。Unity 应使用应用私有文件并节流写入，主应用只保存摘要或索引。

### ABI 打包

证据：

- `app/build.gradle`

已可工作：

- Release 构建已面向 `arm64-v8a`，与 Unity Android 主要目标一致，并能减小包体。

限制：

- 若后续嵌入 Unity as a Library，必须显式审查原生库打包、重复 `.so` 处理、keep 规则和 asset 打包。

## 必要变更框架

### P0：深度集成前先做

这些是设计与边界设定变更，可以在 Unity 玩法稳定之前完成。

1. 创建 Unity 更新合同。

   建议文档：

   - `docs/unity/UPDATE_MANIFEST.md`
   - `docs/unity/BRIDGE_CONTRACT.md`
   - `docs/unity/RELEASE_RUNBOOK.md`

2. 定义独立的 Unity 更新 manifest。

   不要把 Unity 内容包作为普通 ZIP 游戏模块直接加进现有 `modules.json`。使用独立 manifest，例如：

   ```json
   {
     "gameId": "zombiegate",
     "manifestVersion": 1,
     "minHostVersionCode": 123,
     "bridgeVersion": "1.0",
     "unityRuntimeVersion": "unity-2022.3-android-arm64-r1",
     "gameLogicVersion": 1,
     "contentVersion": 1,
     "configVersion": 1,
     "rollout": {
       "percent": 10
     },
     "killSwitch": false,
     "files": [
       {
         "path": "content/catalog.json",
         "url": "https://example.com/unity/zombiegate/content/catalog.json",
         "size": 12345,
         "sha256": "..."
       }
     ]
   }
   ```

3. 保持首次集成方式简单。

   首个可玩阶段，优先选择之一：

   - 主应用通过包名启动独立安装的 Unity APK；
   - 主应用打开一个壳占位页，显示下载/更新/启动状态；
   - 主应用链接到一个导出的 Unity 构建产物进行手动安装。

   这避免在 Unity 项目稳定之前把主应用绑死到 Unity as a Library。

4. 确定最小 bridge API。

   保持小：

   - 宿主应用版本与能力查询；
   - 启动参数；
   - 存档/读取路径交接；
   - 远程配置交接；
   - 分析/错误事件交接；
   - 退出/结果回调。

### P1：首个可玩构建后增加 Unity 内容更新管理器

增加专用管理器，而不是直接扩展 `ModuleDownloader`。

建议的 Android 侧组件：

- `core/unityupdate`
- 或 `app/src/main/java/com/gamecenter/app/unityupdate`

职责：

- 拉取 Unity 更新 manifest；
- 独立比较 `runtimeVersion`、`gameLogicVersion`、`contentVersion`、`configVersion`；
- 下载到 staging 目录；
- 对每个文件做 SHA-256 校验；
- 全部通过后才切换上线；
- 保留 `current` 和 `last_good` 指针；
- 启动失败或收到 kill switch 时自动回滚。

建议的存储布局：

```text
filesDir/unity/zombiegate/
  manifests/
  staging/<contentVersion>/
  current -> 版本指针（preferences 或小元数据文件）
  last_good -> 版本指针（preferences 或小元数据文件）
  saves/
  logs/
```

重要行为：

- 在新内容完全下载并校验通过之前，绝不删除当前工作中的 Unity 内容。
- 在首次启动成功或定义的冒烟检查通过之前，不要把更新标记为 active。
- 保持可回滚，无需重装主应用。

### P2：稳定后再嵌入 Unity

仅在以下稳定后再考虑 Unity as a Library：

- Android 包名和签名流程；
- Unity 版本；
- 目标 ABI；
- bridge API；
- 存档格式；
- 内容打包格式；
- 更新服务器布局；
- 崩溃与回滚策略。

引入 Unity as a Library 时，主应用必须审查：

- Gradle 工程布局；
- Unity 生成的 Android library 模块；
- 原生 `.so` 打包；
- ProGuard/R8 规则；
- asset 压缩与 streaming assets；
- 生命周期转发；
- 权限归属；
- 内存压力处理；
- 冷启动成本；
- 崩溃隔离。

## 主应用更新策略

使用此规则：

| 更新类型 | 是否需要主应用更新 | 优先路径 |
| --- | --- | --- |
| 宿主 UI 入口、bridge API、权限、ABI、Unity 运行时嵌入 | 是 | 主 APK 更新 |
| Unity 原生代码或 IL2CPP player 变更 | 通常需要 | 主 APK 或独立 Unity APK |
| Unity 内容、关卡、纹理、catalog | 否 | Unity 内容 manifest |
| 平衡数值、功能开关、难度、刷新率 | 否 | 远程配置 |
| 紧急禁用 | 否 | Unity manifest/config 中的 kill switch |
| 存档格式破坏性变更 | 视情况 | bridge 受控迁移 |

## 当前不要做的事

- 不要把每次 Unity 变更都强制走主应用发布流程。
- 不要把当前 `DexClassLoader` 模块加载器当作 Unity 运行时更新机制。
- 不要把大体量 Unity 存档存进 `SharedPreferences`。
- 不要在替换内容完全校验之前删除当前 Unity 内容。
- 不要在尚无可启动构建和回退屏幕之前加入硬性底部导航 Unity 入口。
- 不要把 Unity 更新 manifest 混进现有模块 manifest，除非字段和语义被清晰分离。

## 建议的下一步

1. 在 Unity 项目产出首个 Android 构建产物之前，保持当前主应用代码不变。
2. 在实现之前先增加 `docs/unity/UPDATE_MANIFEST.md` 和 `docs/unity/BRIDGE_CONTRACT.md` 锁定接口。
3. 先把首个 Unity 产物构建为可独立启动的 APK。
4. 仅在启动路径已知时，才在主应用加入 `ZombieGate` 壳入口。
5. 等 Unity 有真实内容包需要更新时，再实现 `UnityContentUpdateManager`。
6. 在依赖大体量 Unity 运行时更新之前，把 APK 更新元数据从 MD5 迁移到 SHA-256。

## 工作树说明

主应用工作树当前有大量修改和未跟踪文件。在这些变更提交、stash 或有意分离到 Unity 集成专用分支之前，避免源码编辑。


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
