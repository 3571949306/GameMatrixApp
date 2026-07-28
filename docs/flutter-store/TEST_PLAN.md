<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 本文档记录 vc595 / 已签名 Catalog V8 的测试证据。它不声明当前工作树或当前发布具有相同状态；当前版本、活动实现与发布门槛见 [`../CURRENT_STATE.md`](../CURRENT_STATE.md)。

# Flutter Store 测试计划与结果

所有 Gradle 命令必须带 `-PautoUploadVps=false -PautoBumpVersion=false`。本地验证不得修改线上 Catalog、VPS、版本号或 Git 历史。

## 已通过的自动化

| 范围 | 结果 | 覆盖 |
|---|---|---|
| Flutter analyze/test | 通过 | 6 tests；Controller 过滤/排序/降级、Catalog 渲染与详情路由、偏好并行读取、可见列表缓存、进度事件不重复查询完整模块。 |
| Catalog V2 parser/schema | 通过 | 正式 V2、旧目录适配、HTTPS、必填 runtime/delivery、六类 Runtime/四类 Delivery 合法组合、非法组合和重复 ID。 |
| Catalog Ed25519 | 通过 | RFC 8032 有效签名、精确字节篡改拒绝、密钥轮换、全零/错误长度拒绝。 |
| Runtime registry | 通过 | flutter/web/asset/android/native_service/unity 六类 handler。 |
| Secure archive / Runtime security | 通过 | 路径穿越、压缩炸弹、Asset manifest、Unity Content manifest、Web/Asset/Unity 安装→更新→回滚→卸载、Flutter 路由白名单和 Native Service controller 拒绝策略。 |
| Android Debug | 通过 | 404 tests / 0 failures / 0 errors / 1 skipped；`lintDebug` 和 `assembleDebug` 成功。lint 保留 1 个 baseline-filtered 旧 error 与 198 个 warning，不冒充零告警。 |
| Android Release | 通过 | `lintVitalRelease`、R8、资源收缩、APK V2 签名和 assemble。 |

Python 发布与签名工具测试：`python -m unittest discover -s scripts/tests -p 'test_*.py'`，8 tests passed；相关发布脚本均通过 `py_compile`。

## 设备矩阵

| 平台 | 构建 | 已验证结果 |
|---|---|---|
| Android 13 / ARM64 / 小米 M2012K10C | Flutter-enabled Debug | 首页、详情、已安装、更新、下载、旧商店回退、中英文、深色模式；80 次进出失败 0，后 40 次 PSS 仅约 +2.9 MiB，目标日志错误 0。 |
| Android 11 / API 30 / x86_64 模拟器 | 双 ABI 签名 staging Release | 安装并进入 Flutter 商店；20 次进出失败 0，目标日志错误 0。 |
| Android 12 / API 31 / x86_64 模拟器 | 双 ABI 签名 staging Release | 安装并进入 Flutter 商店；20 次进出失败 0，目标日志错误 0。 |
| Android 14 / API 34 / x86_64 模拟器 | 双 ABI 签名 staging Release | 使用 Activity 轮询的稳健脚本完成 20 次进出，失败 0，目标日志错误 0。 |
| Android 15 / API 35 / Pixel 7 x86_64 模拟器 | vc594 生产信任双 ABI Release | `ModuleStoreActivity` 直接承载 Flutter Fragment；语义树确认 34 模块；搜索、滚动、旧商店双向返回通过，应用 PID 致命错误 0。 |

第一次 API 35 测试发现 ARM64-only Flutter Release 在 x86_64 上出现 `UnsatisfiedLinkError` 并回退旧商店。现已增加 staging Release 双 ABI 构建门禁，重新构建后 APK 同时包含 ARM64/x86_64 `libflutter.so` 与 `libapp.so`，实际 Flutter Fragment 验证通过。

性能回归使用同一台 API 35 模拟器、相同清洁进程后进入个人页并连续 5 次打开/返回商店的流程。优化前 `Displayed` 为 1182/316/1842/509/478 ms；vc594 为 1178/377/337/368/345 ms，中位数由 509 ms 降至 368 ms。该小样本用于本机回归，不替代生产端真实用户分位数监控。

## Release 证据

- 文件：`app/build/outputs/apk/release/app-release.apk`
- applicationId：`com.gamecenter.app`
- version：594 / `1.4.1`
- 大小：415,646,768 字节
- SHA-256：`2c3955f9c37c5bbf2ab68966fb78e73d5c46bd1c79ef32de43363e7e04b60ce3`
- 签名：APK Signature Scheme v2，1 个 signer；证书 SHA-256 `d058a18f9e89a29b5339eda27ece3ff9f78e0dbefe605d551e7745f724d2eddc`
- 内容：Flutter assets、Catalog V2 schema、预装 wrongbook APK、ARM64/x86_64 Flutter AOT 库
- 预装 wrongbook 的长度、SHA-256 和 signer 已与源资产/宿主证书一致验证。

## 远端信任负向测试

生产 `catalog.json` 与 `modules.json` 当前返回 Catalog V8 的相同字节、ETag 与 `X-Catalog-Signature`；34 个正式模块均有显式 `runtimeType`/`deliveryType`。发布验收必须同时比较响应体、header 和 Ed25519 验签结果；vc594 已完成该门禁。此前“无签名时 fail-closed 并保留缓存”的负向测试继续保留。

## 标准命令

```powershell
cd flutter_module
D:\Developmment\flutter\bin\flutter.bat analyze
D:\Developmment\flutter\bin\flutter.bat test

cd ..
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug `
  -PenableFlutterModuleStore=true `
  -PenableCatalogSignature=true `
  -PcatalogSigningProfile=staging `
  -PcatalogEd25519PublicKeys=<STAGING_PUBLIC_KEY_BASE64> `
  -PautoUploadVps=false -PautoBumpVersion=false --stacktrace

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

当前 Hilt/ASM 在并行干净 Release 中出现过 `App.class` 转换输出竞态，因此 Release 门禁暂时必须串行。不得添加 `-dontwarn` 或关闭 R8 来掩盖缺类；升级 Gradle/Hilt/Kotlin 后需先证明并行 clean Release 稳定，再移除限制。

## 生产回归与持续运维场景

- 签名正式 Catalog V2：200、304、离线有缓存、离线无缓存、签名缺失/错误/轮换。
- 每种非内置 Runtime：下载、进度、取消、重试、SHA 错、包签名错、宿主不兼容、缺依赖、安装、更新、禁用、卸载、自动/手动回滚、重启恢复。
- 持续指标：监控 Flutter 初始化失败率、下载成功率、回滚率、崩溃率和 PSS 趋势；任何回归均触发 Catalog 或 APK 回滚。
