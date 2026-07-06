# GameMatrixApp 测试问题记录 - 2026-06-27

## 已修复问题

### 1. GameUsageStore.recordScore() 方法缺失
- **文件**: `app/src/main/java/com/gamecenter/app/games/GameUsageStore.java`
- **错误**: `Game2048Fragment.java:177` 调用不存在的方法
- **修复**: 添加 `recordScore()` 和 `getHighScore()` 方法

### 2. HallScreen.kt Compose 依赖缺失
- **文件**: `module-store/feature/games/games/hall/HallScreen.kt`
- **错误**: Compose 模板文件未排除编译
- **修复**: 重命名为 `.bak`

### 3. 游戏 Activity NullPointerException (4处)
- **文件**: 
  - `Game2048Activity.java:249`
  - `MinesweeperActivity.java:165`
  - `SnakeActivity.java:231`
  - `TetrisActivity.java:227`
- **原因**: `onDifficultyChanged()` 在 `initGame()` 之前调用
- **修复**: 添加 null 检查

## 待解决问题

### 4. 浏览器模块空白
- **现象**: 点击"网页浏览"后页面空白
- **待查**: 需要检查浏览器模块加载逻辑


### 4. 浏览器模块空白 (已修复)
- **现象**: 点击"网页浏览"后页面空白
- **根因**:
  1. Dart 入口点 `netLeafModuleEntry` 未被编译进 kernel_blob.bin
  2. FlutterFragment 在容器未完成布局时添加，导致 "Width is zero"
  3. `main()` 入口启动 `SplashApp`，在嵌入模式下无法正确渲染
- **修复**:
  1. 改用 `main` 入口点（已包含平台判断，Android 上跳过 windowManager）
  2. 使用 `FlutterView` 替代 `FlutterFragment`，延迟 attach 确保容器有正确尺寸
  3. 修改 `netleaf/lib/main.dart`，Android 嵌入模式下跳过 SplashApp 直接启动 NetleafApp
- **涉及文件**:
  - `module-store/feature/tools/browser/src/main/java/com/gamecenter/app/flutter/NetLeafFlutterFragment.kt`
  - `app/src/main/java/com/gamecenter/app/App.java`
  - `netleaf/lib/main.dart`
  - `app/src/main/java/com/gamecenter/app/features/ModuleShellFragment.kt`

