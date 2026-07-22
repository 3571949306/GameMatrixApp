<!-- flutter-store-doc-sync: 2026-07-22; authoritative -->
> **Flutter-first authoritative document:** Production stable vc595 and signed Catalog V8 are live and verified; this document and current build evidence are the release truth.

# Runtime Handler 指南

`ModuleRuntimeRegistry` 按 `RuntimeType` 注册六个独立 handler。公共生命周期为 `prepare/install/open/enable/disable/uninstall/rollback`；业务入口只能经 `ModuleCoreFacade` 调用。

| Runtime | 状态 | 行为与限制 |
|---|---|---|
| Flutter | 本地闭环完成 | 只允许宿主已编译并登记的 route；不下载或执行 Dart 源码。已测试已编译路由可打开、未知路由拒绝。 |
| Web | 安全运行/安装框架 | ZIP 原子解压到私有只读 `current`；`WebModuleActivity` 使用 `https://module.local` 虚拟 origin，默认无 JS/Bridge，阻断外域和 SSL 错误。尚无正式签名包完成端到端下载验证。 |
| Asset | 本地闭环完成 | 与 Web 共用安全 ZIP/事务目录；必须含有效 `asset-manifest.json`，打开时发送包内限定广播，由宿主白名单消费者解释资源。安装/更新/回滚/卸载和坏 manifest 已覆盖。 |
| Android | 旧链路兼容 | 复用 `ModuleManager`、现有 APK SHA/签名验证、动态加载与宿主代理页面；不把动态 APK Activity 当普通宿主 Activity 直接启动。正式 V2 记录必须先映射到权威 `ModuleManifest`。 |
| Native Service | 本地闭环完成 | 复用 Android 安装链，按 `serviceType` 进入 `NativeServiceControllerRegistry`；默认仅登记 `vpn`，未知类型以 `service_type_unsupported` fail-closed。正式服务仍需逐项注册权限和生命周期。 |
| Unity | 本地闭环完成 | 复用现有 `UnityModuleManager`/launcher ID；`content` 包使用安全事务安装并必须含有效 `unity-manifest.json`，缺失已验证内容时拒绝打开。 |

## 新增 Runtime Handler

1. 在 `RuntimeType` 与 Catalog schema 中增加稳定 wire 值。
2. 实现小型 `ModuleRuntimeHandler`，明确每个生命周期和失败码。
3. 注册到 `ModuleRuntimeRegistry`，增加覆盖测试。
4. 不得直接创建第二个下载器、安装数据库或权限系统。
5. 外部内容先经现有 HTTPS/域名/SHA/签名链，再进入 handler。
6. 若需要 UI，优先宿主 Activity/Fragment 或已编译 Flutter route；动态 APK 资源必须真机验证。

正式 V2 的非内置包若尚未登记到 `ModuleManager` 权威清单，Facade 会返回 `package_not_registered`，不会发布排队事件或伪造下载进度。接入新包时必须先完成签名清单映射，不能绕过权威下载器直接调用 handler。

## ZIP 安全限制

当前限制：最多 2048 项、单项 64 MiB、总解压 250 MiB、压缩比不超过 200；拒绝绝对路径、盘符路径、NUL 和规范化后逃逸目标。失败内容进入隔离/清理路径，成功才把 `staging` 原子切换到 `current` 并保留 `last_good`。

Asset/Unity manifest 均使用 `schemaVersion=1`，必须匹配 Catalog 的 `moduleId` 与 `versionCode`，声明非空 `files`；每个路径必须安全且在解压目录真实存在。Unity 还必须匹配 `launcherId`。这些 manifest 是包内容合同，不替代 Catalog Ed25519、HTTPS、SHA-256 或 Android APK 签名。

2026-07-21 已用 Robolectric API 35 覆盖普通解压、路径穿越、Web/Asset/Unity Content 安装→更新→回滚→卸载、失败更新保持 `last_good` 并隔离拒绝包、高压缩比归档拒绝、Flutter 路由与 Native Service controller fail-closed。正式 Web/Asset/Native Service/Unity 远端包仍需由发布环境提供；本地闭环通过不等于生产下载闭环完成。