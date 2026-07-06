# GameMatrixApp AI 编程规范

本文档用于约束后续 AI 在本项目中的编码、构建、验证和汇报行为。目标是减少“代码看似改了，但用户拿到的 APK 仍然坏”的问题，尤其是动态模块、预置模块和发布路径相关问题。

## 1. 工作原则

1. 先读上下文，再动代码。
   - 必读根目录 `AGENTS.md`。
   - 复杂任务同时阅读 `docs/AI_CONTEXT.md`、`docs/DONT_DO_THIS.md` 和相关模块文档。
   - 涉及模块市场、动态模块、发布、更新时，必须查 `docs/modules/`、`docs/module-docs/` 和相关 Gradle 脚本。

2. 不要破坏用户已有改动。
   - 默认工作区可能是脏的。
   - 不允许使用 `git reset --hard`、`git checkout -- <file>` 等命令回退用户改动，除非用户明确要求。
   - 只编辑和任务直接相关的文件。

3. 成功必须以真实运行结果为准。
   - Gradle 成功只是第一步。
   - 用户可见功能必须安装到模拟器并走真实入口。
   - 运行后必须查 logcat 是否有 `FATAL EXCEPTION`。

4. 汇报必须区分“已验证”和“未完成限制”。
   - 不要把未测试的推测说成已完成。
   - 如果某个入口只做了防崩但功能还未完整接入，必须明确说明。

## 2. 项目边界和架构规则

### 2.1 Host App

宿主 app 位于 `app/`，负责：

- 主入口、底部导航、宿主 manifest。
- 预置模块复制和启动。
- 发布 APK 和更新 JSON。
- `MainActivity`、模块壳、恢复模式、更新流程。

修改宿主时必须注意：

- 底部导航同时检查 `bottom_nav_menu.xml`、`mobile_navigation.xml`、`MainActivity` 绑定逻辑。
- 新 Activity 必须声明在宿主 `AndroidManifest.xml`，动态 APK 内 manifest 不会自动并入已安装宿主。
- 崩溃恢复模式可能因为之前崩溃计数进入 `RecoveryActivity`，测试时可用 `adb shell pm clear com.gamecenter.app` 清干净状态。

### 2.1.1 宿主 Kotlin 迁移后的文件放置规则

循环23 已完成宿主 Kotlin 迁移，`App.kt` / `MainActivity.kt` / `GameRegistry.kt` 已是 Kotlin 文件（详见 `docs/DONT_DO_THIS.md`，不要改回 Java）。

新增 Kotlin 文件放置规则：

- 宿主 Kotlin 源码统一放在 `app/src/main/kotlin/com/gamecenter/app/` 目录下。
- 不要把新的 `.kt` 文件放到 `app/src/main/java/` 下混用（虽然 Gradle 允许，但本项目约定走 kotlin 目录）。
- 如果新文件和已迁移的 `App.kt` / `MainActivity.kt` / `GameRegistry.kt` 同包，直接放在同目录即可。
- 不要为了"统一"把现有 Java 文件批量迁 Kotlin，迁移是逐文件、按循环推进的。

### 2.2 Dynamic Modules

动态模块主要在 `module-store/feature/` 下。它们不是普通 app 内代码，必须遵守以下规则：

1. 不要假设动态 APK 的资源 ID 和宿主一致。
   - 动态模块资源通过 `AssetManager.addAssetPath` 加载。
   - 模块 `R.string`、`R.layout`、`R.id` 与宿主资源表可能冲突。
   - 使用 ViewBinding 时必须确保 inflater 使用模块 Resources。

2. 谨慎使用 Material/AppCompat XML 控件。
   - `MaterialToolbar`、`TabLayout`、`FloatingActionButton`、`MaterialCardView`、`MaterialButton`、`TextInputLayout` 等会读取 `R.styleable`。
   - 宿主运行时类和模块资源表不一致时，可能出现 `Resources$NotFoundException`、`InflateException` 或把 style 当 drawable 读取。
   - 动态 APK 中优先使用普通 Android View，或者把 Material UI 放到宿主侧。

3. 不要从动态模块直接启动模块 Activity。
   - Android 只能启动宿主已安装 manifest 中声明的 Activity。
   - 动态 APK 的 Activity 即使在模块 manifest 中声明，也不会自动成为宿主可启动 Activity。
   - 需要完整页面时，使用宿主代理 Activity、Fragment 容器，或把 Activity 合入宿主 manifest。
   - 如果暂时不能完整接入，必须做 `ActivityNotFoundException` 防崩，并在汇报中说明限制。

4. 动态模块内对话框和主题要用宿主 context 或普通系统 UI。
   - 不要用模块 context 打开系统/Material 对话框，容易触发资源和主题冲突。
   - 对话框文本可以从模块 Resources 取字符串，但 Builder 优先使用 `requireContext()`。

## 3. 预置模块 APK 规则

预置模块在 `app/src/main/assets/modules/` 下进入宿主 APK。常见错误是“模块源码已编译，但宿主 APK 里还是旧模块”。

### 3.1 必须使用正确构建顺序

如果改了预置模块，例如错题本：

```powershell
.\gradlew.bat :module-store:feature:tools:wrongbook:assembleDebug :app:bundlePreinstalledModules -PautoBumpVersion=false --stacktrace
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace
```

原因：当前构建中 `bundlePreinstalledModules` 可能在 app 打包之后复制模块。只跑一次 `:app:assembleDebug` 可能导致新模块没有真正打进 `app-debug.apk`。

### 3.2 必须验证 APK 内 assets

构建后验证 `app-debug.apk` 内的模块大小或 SHA-256：

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$apk='app\build\outputs\apk\debug\app-debug.apk'
$zip=[System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $apk))
$entry=$zip.Entries | Where-Object { $_.FullName -eq 'assets/modules/feature_wrongbook_v100.apk' }
"$($entry.FullName) $($entry.Length)"
$zip.Dispose()
```

如果模块文件大小或 hash 不对，不允许安装测试并宣称修好了。

### 3.3 App 内模块缓存

宿主会把 assets 下的模块复制到：

```text
/data/user/0/com.gamecenter.app/files/modules/
```

历史问题：

- 旧逻辑遇到同名模块会跳过提取，导致模拟器一直加载旧模块。
- DexClassLoader 加载后模块文件可能被切成只读。

要求：

- 替换预置模块时必须比较大小/hash，不同则覆盖。
- 覆盖只读模块时使用临时文件写入，再 rename。
- 测试时必要情况下 `adb shell pm clear com.gamecenter.app` 清数据，确保旧模块缓存不影响验证。

## 4. 构建规则

### 4.1 Debug 构建

常规 debug：

```powershell
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace
```

### 4.2 Release 构建

发布相关问题必须跑 release：

```powershell
.\gradlew.bat :app:assembleRelease --stacktrace
```

更新 JSON 相关任务不能只看本地 APK。必须检查用户真实访问的：

- `version-release.json`
- `version.json`
- `downloadUrl`
- 下载后的 APK manifest、versionCode、SHA-256

普通用户默认走 stable/release 路径，不要先看 beta，除非用户明确提到 beta 或 `acceptBeta=true`。

### 4.3 Lint 和资源字符串

常见错误：

- app 和 core 模块中的同名 string 参数数量不一致。
- lint baseline 中存在已经不存在的文件路径。
- 英文/中文资源缺失导致构建或运行时异常。

要求：

- 修改格式化字符串时同时检查所有 locale 和调用点。
- 改 lint baseline 前先确认对应文件是否还存在。
- 不要为了过构建随意删除有效 lint 项。

### 4.4 Netty 安全版本约束

循环24（commit `f978f06`）已把 Netty 从 `4.1.134.Final` 升级到 `4.1.135.Final`，修复 7 个 CVE。

约束：

- **不要降级 Netty** 到 4.1.134.Final 及以下。降级会重新触发 7 个 GitHub Dependabot 告警，并让构建链路重新引入已知漏洞。
- 如果需要调整 Netty 版本，只能用 `4.1.135.Final` 或更高。
- Netty 是 Gradle / MediaPipe / Robolectric 的传递依赖，**不进入 APK runtime**，但仍需保持版本合规以维护构建链路安全。
- 不要为了"消除告警"而 dismiss 新出现的 Netty 告警，必须先确认是否已升级到修复版本。
- 详见 `docs/SECURITY.md`、`docs/NETWORK_LAYER.md` 和 `docs/DONT_DO_THIS.md`。

## 5. 模拟器验证规则

### 5.1 设备选择

先列设备：

```powershell
adb devices
```

用户指定设备时必须使用指定 serial，例如：

```powershell
adb -s 127.0.0.1:7555 install -r -d app\build\outputs\apk\debug\app-debug.apk
```

### 5.2 标准验证流程

```powershell
adb -s <serial> logcat -c
adb -s <serial> shell monkey -p com.gamecenter.app -c android.intent.category.LAUNCHER 1
```

执行用户路径，例如点击底部“错题本”、切换 tab、点主要按钮。

然后查日志：

```powershell
adb -s <serial> logcat -d -t 2000 |
  Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime|Resources\$NotFoundException|InflateException|ClassNotFoundException|ActivityNotFoundException|Caused by:"
```

如果出现崩溃，必须继续修，不要只汇报“已安装”。

### 5.3 UI 状态确认

可用：

```powershell
adb -s <serial> shell dumpsys window | Select-String -Pattern "mCurrentFocus|mFocusedApp"
adb -s <serial> exec-out uiautomator dump /dev/tty
```

注意：`uiautomator dump /dev/tty` 在部分模拟器上可能最后输出 `Segmentation fault`，只要 XML 已输出，通常仍可用于判断当前界面。

## 6. 错题本模块专项规则

错题本模块路径：

```text
module-store/feature/tools/wrongbook/
```

当前已知约束：

- 入口 Fragment 可动态加载。
- 页面布局应避免 Material/AppCompat XML 控件。
- `CaptureActivity` 不能作为动态模块 Activity 直接启动，除非宿主 manifest/代理 Activity 支持。
- 加号入口必须防崩；如果要完整可用，需要进一步做宿主代理或 Fragment 化。
- 修改错题本后必须确认 `feature_wrongbook_v100.apk` 已进入宿主 APK 的 `assets/modules/`。

### 6.1 预装与推进状态

- **循环20**：错题本预装已完成。`feature_wrongbook_v100.apk` 已进入宿主 APK 的 `assets/modules/`，新版本发布后用户首次安装即可用，无需额外下载。
- **循环21-23**：错题本全面推进已完成。包括宿主代理接入、Fragment 化路径、tab 切换稳定性、加号入口防崩等。
- **当前状态**：错题本处于"预装 + 全面推进"完成后的稳定维护阶段。后续改动按正常动态模块流程处理，不需要再走预装初始化或全面推进的特殊流程。
- **验证要求**：改错题本后仍需按 3.1 / 3.2 重新构建预置模块并验证 `assets/modules/feature_wrongbook_v100.apk` 的大小/SHA-256。

错题本最小验收：

1. 点击底部“错题本”不闪退。
2. 显示“错题本”标题和“错题 / 看板 / 复习 / 设置”tab。
3. tab 切换不闪退。
4. 加号点击不闪退。
5. logcat 无 `FATAL EXCEPTION`。

完整功能验收另需：

1. 添加错题页可打开。
2. OCR/拍照/选图权限路径正常。
3. 保存后列表可刷新。
4. 删除确认框不触发动态资源冲突。

## 7. 发布和版本规则

发布或用户更新问题必须验证真实路径：

- 本地构建结果
- `version-release.json` / `version.json`
- `downloadUrl`
- CDN 缓存
- 下载到的 APK 的 manifest 和 SHA-256

如果用户说“用户更新到的版本还是旧版本”，不要只看本地 `version.properties` 或 `assembleRelease` 输出。

## 8. 临时文件和日志规则

AI 调试时可能生成：

- `screen_*.png`
- `window*.xml`
- `logcat_*.txt`
- `*_extracted/`
- `*.zip` 解包中间件

这些是调试产物，不能默认纳入最终变更。需要提交前必须清理或明确说明保留原因。

## 9. 最终汇报格式

最终回复必须包含：

- 改了哪些核心文件。
- 跑了哪些构建命令。
- 安装到了哪个模拟器。
- 实际点击验证了哪些路径。
- 是否仍有功能限制。

示例：

```text
已安装到 127.0.0.1:7555。验证了启动、底部错题本入口、tab 切换、加号点击，logcat 未出现 FATAL EXCEPTION。
限制：加号目前只做防崩，完整 Capture 页还需要宿主代理 Activity。
```
