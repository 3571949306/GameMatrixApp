# :macrobenchmark 模块 — Baseline Profile 生产者

## 背景

GameMatrixApp 分发渠道为 **HK VPS + GitHub Releases**（非 Google Play），无法使用 Play Cloud Profile。
因此 Baseline Profile 必须本地生成并 **打包内置** 进 APK，安装即 AOT 预热。

## 模块职责

生成冷启动 Baseline Profile，覆盖以下热路径：
- `Application.onCreate`（Hilt 初始化、Room 数据库、网络栈）
- `SplashActivity`（启动屏动画）
- `MainActivity` 首帧渲染

生成后的 `baseline-prof.txt` 写入 `app/src/main/`，由 AGP 打包进 APK `assets/`，
运行时 `androidx.profileinstaller` 解析并触发 ART AOT 编译。

## 目录结构

```
macrobenchmark/
├── build.gradle                                    # com.android.library + com.android.baselineprofile
└── src/main/
    ├── AndroidManifest.xml
    └── java/com/gamecenter/app/macrobenchmark/
        └── StartupBenchmark.kt                     # BaselineProfileRule 冷启动采集
```

## 生成 Baseline Profile

### 方式一：连接真机（推荐，arm64）

```bash
# 连接 arm64 真机（USB 调试已开启），执行：
./gradlew :app:generateBaselineProfile
```

### 方式二：Gradle Managed Device（无需本地真机）

模块已配置 `pixel6Api34` GMD（Pixel 6 / API 34 / AOSP 镜像）：

```bash
./gradlew :app:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=baselineprofile \
  --rerun-tasks
```

首次运行会自动下载 AVD 系统镜像（约 1-2 GB），后续复用缓存。

### 方式三：assembleRelease 自动触发

`app/build.gradle` 已配置 `automaticGenerationDuringBuild = true`，执行 `assembleRelease` 时
自动运行 profile 生成（需有设备或 GMD 可用）。

> **注意**：`assembleDebug` 不触发 profile 生成（debug 构建不走 R8 优化，profile 无意义）。

## 生成后的产物

- `app/src/main/baseline-prof.txt` — ART 可读的 profile 文本（纳入 Git 版本控制）
- APK `assets/dexopt/baseline.prof` — 二进制 profile（AGP 自动转换并打包）

## 性能目标

| 指标 | 目标 | 测试设备 |
|------|------|----------|
| 冷启动 timeToInitialDisplay | < 1.5s | 中端 arm64（如 Pixel 6 / 骁龙 7 系） |
| 冷启动 timeToFullDisplay | < 2.0s | 同上 |

## 与 ProfileInstaller 的关系

`app/build.gradle` 已依赖 `androidx.profileinstaller:profileinstaller:1.4.1`：
- App 启动时 `ProfileInstaller` 读取 `assets/` 中的 baseline profile
- 触发 ART 后台 AOT 编译被 profile 覆盖的热路径
- **首次安装后冷启动即生效**，无需用户多次启动预热

## 注意事项

1. **非 Play 渠道**：不能用 Cloud Profile，必须打包内置（当前方案）
2. **ABI 限制**：App 仅构建 `arm64-v8a`，生成 profile 时也应在 arm64 设备上运行
3. **R8 混淆**：Release 构建启用了 `minifyEnabled`，profile 中的类名/方法名对应混淆后的映射
4. **版本对齐**：每次重大版本更新（新增 Activity / 改启动流程）后应重新生成 profile

## 已知问题（TODO）

### `com.android.baselineprofile` 插件未在 AGP 8.13.2 中内置

通过检查 `gradle-8.13.2.jar` 的 `META-INF/gradle-plugins/` 目录，确认 AGP 8.13.2 **不包含**
`com.android.baselineprofile` 插件描述符，也没有 `BaselineProfilePlugin` 类。

当前采用 **容错方案**：
- `app/build.gradle` 和 `macrobenchmark/build.gradle` 用 `apply plugin:` + `try-catch` 包裹
- `baselineProfile {}` DSL 用 `try-catch(MissingMethodException)` 包裹
- `baselineProfile project(':macrobenchmark')` 依赖用 `configurations.findByName('baselineProfile')` 条件判断
- 插件不可用时自动跳过，`./gradlew :app:assembleDebug` 正常成功

**修复方案（待实施）**：在根 `build.gradle` 的 `buildscript.dependencies` 添加：
```groovy
classpath 'androidx.benchmark:benchmark-baseline-profile-gradle-plugin:1.3.4'
```
并将插件 ID 从 `com.android.baselineprofile` 改为 `androidx.baselineprofile`。
此修改需要联网下载依赖，本轮未实施（遵守"只做本地文件操作和构建检查"约束）。

