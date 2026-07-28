<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 下文任何关于 vc595 / 已签名 Catalog V8 的陈述均记录 2026-07-22 的发布证据。它不是当前工作树或生产状态的声明；实时事实请查阅项目根目录的 `docs/CURRENT_STATE.md` 与当前实现。

# 小游戏拆分剥离项目总结

## 项目概述

将 `app` 模块中除五子棋（Gomoku）、斗地主（DouDiZhu）之外的 **27 个小游戏** 剥离到独立的 `:feature:games` library 模块中，降低主包体积和编译耦合度。每个游戏的 `Activity` 重构为 `Fragment`，通过 `DynamicGameActivity` 动态加载。

**当前状态**：编译验证通过（BUILD SUCCESSFUL），ADB 部署与回归测试待执行。

---

## 架构设计

### 模块依赖关系

```
:app (com.gamecenter.app)
  ├── :feature:games (com.gamecenter.app.games)
  ├── :core:common
  ├── :core:network
  └── :core:update

:feature:games
  ├── :core:common
  └── :core:network
```

### 关键原则

| 原则 | 说明 |
|------|------|
| library 模块 | `:feature:games` 为 `com.android.library`，无 `applicationId` |
| 命名空间 | `:feature:games` 使用 `com.gamecenter.app.games` |
| 禁止反向依赖 | `:feature:games` 不能依赖 `:app`，避免循环 |
| 资源随代码迁移 | 游戏专用布局、字符串、图标移至 `:feature:games/src/main/res/` |
| 非传递性资源隔离 | 各模块只访问本地声明的资源，或显式导入对应库的 R |

---

## 完成阶段详情

### 阶段 0：前置修复

- 修复中国象棋网格线不可见问题（`ChineseChessView.java` 中 `setStyle(Paint.Style.STROKE)`）
- 定位 JDK 环境：`C:\Program Files\Android\Android Studio\jbr`

### 阶段 1：基础架构搭建

- 创建 `:feature:games` 模块目录结构
- 在 `settings.gradle` 中添加 `include ':feature:games'`
- 创建 `feature/games/build.gradle`
- 创建 `feature/games/src/main/AndroidManifest.xml`
- 在 `app` 模块创建 `DynamicGameActivity.java` + `activity_dynamic_game.xml`
- 在 `GameRegistry.java` 中添加 `getFragmentClassById()` / `getActivityClassById()`

### 阶段 2：配置修复

- 插件改为 `com.android.library`，namespace 改为 `com.gamecenter.app.games`
- 移除 `applicationId`，移除 `implementation project(':app')`（解决循环依赖）
- 依赖改为 `compileOnly project(':core:common')`
- 修复 `AndroidManifest.xml`（移除 `package` 属性）
- 删除错误的占位文件 `SaveManager.java` 和不可编译的 `Game2048Fragment.java`

### 阶段 3：公共基础设施与网络迁移

- 迁移游戏共享基础类：`SaveManager.kt`、`GameUsageStore.java`、`GameTutorialHelper.java`、`InteractiveTutorialDialog.java`、`GameStats.java`、`BaseGameActivity.java`、`SoundManager.java`
- 迁移网络对战组件：`GameSocketServer`、`GameSocketClient`、`LANManager`、`OnlineChatHelper`、`OnlineRoomManager`、`BaseOnlineActivity`
- 更新 `app` 模块和各游戏中的 `import` 路径

### 阶段 4：小游戏全量迁移与资源去重

- 27 个游戏代码全部移至 `:feature:games`
- 游戏资源（布局、字符串、颜色、drawable、raw）迁移至 `:feature:games`
- 清理 `:app` 中冗余游戏类与资源
- 修复 R 资源导入（`:app` 使用 `com.gamecenter.app.R`，`:feature:games` 使用 `com.gamecenter.app.games.R`）
- 清理 UTF-8 BOM 字符
- 增加 Hilt（KSP）及 OkHttp 编译时依赖
- **全量编译验证通过**

---

## 辅助脚本说明

迁移过程中使用了以下 PowerShell 脚本（位于 `scratch/` 目录）：

| 脚本 | 用途 |
|------|------|
| `clean_app_games.ps1` | 从 `app/games` 中删除除五子棋、斗地主、GameRegistry 之外的所有游戏目录 |
| `clean_app_res.ps1` | 清理 `app` 中与 `feature/games` 重复的布局和 drawable 资源，同时移除 `feature/games` 中误迁的五子棋/斗地主资源 |
| `copy_missing_res.ps1` | 补拷缺失的共享资源（dot_active.xml、ic_launcher_foreground.xml、item_tutorial_page.xml 等）到 `feature/games` |
| `copy_raw_res.ps1` | 将 `app/raw` 下的音效资源拷贝到 `feature/games/raw` |
| `delete_app_copied_res.ps1` | 删除 `app` 中已拷贝到 `feature/games` 的重复共享资源 |
| `delete_app_raw.ps1` | 删除 `app/raw` 目录（资源已迁移至 `feature/games`） |
| `remove_bom.ps1` | 批量移除 `feature/games/src/main/java` 下所有 Java/Kotlin 文件的 UTF-8 BOM 头 |
| `restore_app_r.ps1` | 将 `app` 模块中误用的 `com.gamecenter.app.games.R` 导入还原为 `com.gamecenter.app.R` |
| `restore_app_raw.ps1` | 将 `feature/games/raw` 资源回拷到 `app/raw`（用于五子棋/斗地主等保留游戏） |

---

## 待完成工作

### 阶段 5：ADB 部署与回归测试

- [ ] 安装 APK 到模拟器/真机
  ```bash
  adb install -r app\build\outputs\apk\debug\app-debug.apk
  ```
- [ ] 启动 `DynamicGameActivity` 传入 `gameId` 验证 Fragment 加载
  ```bash
  adb shell am start -n com.gamecenter.app/.DynamicGameActivity -e gameId 2048
  ```
- [ ] 验证保留在 `:app` 的五子棋、斗地主和其他功能不受影响
- [ ] 对各 Fragment 进行生命周期和参数边界测试，确保跳转平滑

---

## 编译验证结果

```text
BUILD SUCCESSFUL in 46s
219 actionable tasks: 21 executed, 198 up-to-date
```


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)