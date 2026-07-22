<!-- flutter-store-doc-sync: 2026-07-22; authoritative -->
> **Flutter-first authoritative document:** Production stable vc595, customizable host navigation and signed Catalog V8 are live and verified; this document and current build evidence are the release truth.

# Flutter-first 模块商店迁移状态

更新时间：2026-07-22。

## 最终结论

本次约定范围已经达到 100%：模块商店展示与交互层由 Flutter Add-to-App 承担；Android 继续作为 Catalog 信任、下载、SHA-256/APK 证书校验、事务安装、启停、回滚与 Runtime 生命周期的唯一权威端。游戏大厅、棋类与其他人机 AI 业务没有被 Flutter 商店迁移替换。

生产 stable 已发布 `versionCode 595` / `versionName 1.4.1`，包名为 `com.gamecenter.app`。源码中的功能开关仍允许本地安全回退，但正式 vc595 构建已启用 Flutter 商店、生产 Catalog 验签和正式 applicationId。

vc595 新增 Android 宿主底部导航扩展：可信 Catalog 中已安装且兼容的 Android 功能模块可声明 `navigationContribution`，用户可在“设置 → 数据与导航 → 底部导航”中拖拽排序、隐藏或恢复默认。游戏大厅始终保留，最多显示 6 项。该能力不改变 Flutter/Android 权威边界，Flutter 商店仍不直接修改宿主导航、APK 或 DEX。

## 完成度

| 维度 | 完成度 | 已验证事实 |
|---|---:|---|
| Flutter UI、路由、状态与本地化 | 100% | `flutter analyze` 无问题，6 个 Flutter 测试通过；首页、详情、搜索、筛选、安装、更新、下载和旧商店回退已覆盖。 |
| Add-to-App、Pigeon、缓存 Engine 与故障回退 | 100% | vc594 改为在 `ModuleStoreActivity` 内直接承载缓存 Engine 的 Flutter Fragment；API 35 显示 34 个模块，旧商店双向返回正常，目标 FATAL 为 0。 |
| Catalog V2 信任链 | 100% | Catalog V8 / legacy version 28 已上线；34 个条目均有显式 runtime/delivery；`catalog.json` 与 `modules.json` 返回相同字节和 `X-Catalog-Signature`，本地使用生产公钥完成 Ed25519 验签。 |
| 多 Runtime 生命周期 | 100% | Flutter、Web、Asset、Android APK、Native Service、Unity 的处理器、安全边界与负向测试均已完成；生产灰度实际完成 Web/Asset/Unity 包的下载与安装，Web v1.0.1 完成更新识别、清洁安装和隔离页面启动。 |
| 宿主底部导航扩展 | 100% | Catalog `navigationContribution`、安装态过滤、宿主图标白名单、动态模块 shell、最多 6 项、游戏大厅强制保留、排序/隐藏/恢复默认和冷启动持久化均已覆盖。 |
| Release 与发布门禁 | 100% | `assembleRelease`、`lintVitalRelease`、R8、资源收缩、APK V2 签名、ARM64/x86_64 均通过；修复并复验 R8 下 Flutter `GeneratedPluginRegistrant` 反射入口，生产签名配置拒绝占位公钥、错误密钥和未签名目录。 |
| VPS 与公网发布 | 100% | Catalog、签名 header、模块包、stable 版本 JSON 和 vc595 APK 已原子发布；公网全量回下载与本地包 SHA-256 一致，失败路径具备回滚。 |
| 文档同步 | 100% | 仓库 142 个 Markdown 均带 `flutter-store-doc-sync` 标记；历史文档明确不是当前发布事实。 |

## 生产证据

- 公网 stable APK：415,669,560 字节。
- 公网与本地 APK SHA-256 均为 `d4b2510d1c2e56349c48bbb8195d8f253233fe6ab9b799a70a4520421ff62976`，完整回下载逐字节一致。
- APK：`com.gamecenter.app`，vc595，`1.4.1`，包含 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64`。
- APK Signature Scheme v2 验证通过；证书 SHA-256 为 `d058a18f9e89a29b5339eda27ece3ff9f78e0dbefe605d551e7745f724d2eddc`。
- 最终 Catalog：schemaVersion 2、catalogVersion 8、legacy version 28、34 个可发现模块；灰度验证条目已从最终目录移除，验证包保留为不可发现的运维资产。
- API 35：从生产 vc594 保留数据升级到 vc595 成功；Flutter 商店进入和 34 模块渲染通过；底部导航隐藏、拖拽排序、返回即时刷新、冷启动持久化及恢复默认通过；最终流程无目标崩溃签名、Flutter 插件注册错误或 `NoSuchMethodException`。
- 响应速度：同一 API 35 模拟器、同一 5 次进入流程，`Displayed` 中位数由 509 ms 降至 368 ms（约 28%）；首次进入 1182→1178 ms 基本持平，热进入最慢值由 1842 ms 收敛至 377 ms。
- 生产灰度：Asset 与 Unity Content 安装成功；Web v1.0.0 安装并启动，v1.0.1 被识别为更新，随后清洁安装并启动，CSP 拒绝与 FATAL 均为 0。

## 安全与运维边界

- Ed25519 私钥只以 Windows DPAPI CurrentUser 保护形式存在于 `local_private/`，签名时只在内存和临时环境变量中解密；不会进入仓库、APK、VPS 或日志。
- VPS SSH 使用严格 host-key 校验；历史 malformed `known_hosts` 行被忽略，但目标端口密钥必须与已信任主机密钥完全一致。
- Web/Asset/Unity ZIP 必须同时匹配已签名 Catalog V2、SHA-256、大小、URL、版本和归档 manifest；APK 继续执行 APK 证书验证。
- 发布脚本对 Catalog、Nginx 片段和配置执行备份、原子替换、`nginx -t`、reload、公网字节比对及签名复验。
- “100%”表示本轮工程、发布和验收门禁全部完成；长期可用性、真实用户崩溃率和容量趋势仍属于持续运维，不会因一次发布永久结束。

## 权威文档

- `ARCHITECTURE.md`：Flutter/Android 权威边界。
- `CATALOG_V2.md`：目录协议与签名要求。
- `RUNTIME_HANDLERS.md`：六类 Runtime 合同。
- `TEST_PLAN.md`：本地、设备与生产验证门禁。
- `ROLLBACK.md`：客户端与 VPS 回滚流程。
