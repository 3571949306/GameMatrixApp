<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# GameMatrix Flutter Store Module

GameMatrixApp 的 Flutter Add-to-App 模块商店。它不是独立 App，也不是全宿主 Flutter 重写：Flutter 负责商店 UI、路由和 UI 偏好，Android 通过 Pigeon 提供可信目录、模块状态、下载/安装/回滚和 Runtime 生命周期。

## 目录

- `lib/core/bridge/`：Pigeon 生成代码和 Gateway。
- `lib/features/module_store/`：首页、详情、已安装、下载和更新页面及状态管理。
- `pigeons/module_store_api.dart`：桥接合同源文件。
- `.android/`：Flutter 生成的宿主工程，不作为手工业务代码入口。

## 开发与验证

```powershell
D:\Developmment\flutter\bin\flutter.bat pub get
D:\Developmment\flutter\bin\flutter.bat analyze
D:\Developmment\flutter\bin\flutter.bat test
```

宿主 Debug 验证：

```powershell
cd ..
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug -PenableFlutterModuleStore=true -PautoUploadVps=false -PautoBumpVersion=false --stacktrace
```

签名 staging Release 验证必须同时覆盖 ARM64 真机与 x86_64 官方模拟器：

```powershell
cd ..
.\gradlew.bat :app:lintVitalRelease :app:assembleRelease `
  -PenableFlutterModuleStore=true `
  -PenableCatalogSignature=true `
  -PcatalogSigningProfile=staging `
  -PcatalogEd25519PublicKeys=<STAGING_PUBLIC_KEY_BASE64> `
  -PstagingApplicationIdSuffix=true `
  "-Ptarget-platform=android-arm64,android-x64" `
  -PautoUploadVps=false -PautoBumpVersion=false `
  --no-parallel --max-workers=1 --no-daemon --stacktrace
```

当前 Hilt ASM 与并行 clean Release 存在输出竞态，所以 Release 门禁暂时固定为串行；不得用关闭 R8 或忽略缺类替代修复。

## 生成代码

修改 `pigeons/module_store_api.dart` 后必须同时重新生成 Dart 与 Kotlin 输出，禁止手工让两端合同分叉。字段 `runtime` 对应 Catalog JSON 的 `runtimeType`，用于避免与 Dart `Object.runtimeType` 冲突。

## 安全和发布边界

- Flutter 不接收私有文件路径，也不直接操作 APK、DEX、Web ZIP、服务或 Unity。
- 禁止模拟下载进度；所有状态来自 Android 事件。
- 高频下载进度只更新对应下载监听器；阶段切换时才回查完整权威模块，避免桥接和整页重建风暴。
- 功能开关默认关闭，旧商店始终保留回退。
- 生产门禁和实际完成度见 `/docs/flutter-store/MIGRATION_STATUS.md`，桥接细节见 `/docs/flutter-store/BRIDGE_API.md`。
