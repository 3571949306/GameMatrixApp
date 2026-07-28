<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 本文档记录 vc595 / 已签名 Catalog V8 的发布证据及预期 Catalog V2 契约。它不是当前全局事实来源；当前版本、活动实现与发布门槛见 [`../CURRENT_STATE.md`](../CURRENT_STATE.md)。

# Catalog V2 协议

规范文件：`app/src/main/assets/catalog_v2.schema.json`。运行时实现：`CatalogModels.kt`、`CatalogV2Parser.kt`、`CatalogSchemaValidator.kt`、`LegacyCatalogAdapter.kt`。

## 顶层字段

| 字段 | 要求 | 说明 |
|---|---|---|
| `schemaVersion` | 必须为 `2` | Multi-runtime 合同版本 |
| `catalogVersion` | 正整数 | 目录版本，不等同 App/模块发布版本 |
| `generatedAt` | 可选字符串 | 建议 ISO-8601 |
| `modules` | 数组 | ID 必须唯一 |

## 模块核心字段

每项必须提供 `id`、`name`、`versionName`、正数 `versionCode`、`runtimeType`、`deliveryType`。运行时枚举：`flutter`、`web`、`asset`、`android`、`native_service`、`unity`；交付枚举：`builtin`、`apk`、`zip`、`content`。

运行时与交付类型必须满足下表；JSON schema 与 Kotlin 校验器会同时拒绝其他组合：

| Runtime | 允许的 Delivery |
|---|---|
| `flutter` | `builtin` |
| `web` | `builtin`, `zip` |
| `asset` | `builtin`, `zip` |
| `android` | `builtin`, `apk` |
| `native_service` | `builtin`, `apk` |
| `unity` | `builtin`, `apk`, `content` |

按运行时还需提供：Flutter 的绝对 `route`；Web 的相对 `entry`；非内置 Android/Native Service 的 `entryClass`；Native Service 的 `serviceType`；Unity 的 `launcherId`。宿主范围由 `minHostVersionCode`/`maxHostVersionCode` 控制。

非 `builtin` 模块必须提供嵌套 `package`：非空 `fileName`、至少一个 HTTPS URL、64 位 SHA-256；可附加包签名。`assets` 可单独声明资源 URL、SHA-256 与签名。

```json
{
  "schemaVersion": 2,
  "catalogVersion": 1,
  "generatedAt": "2026-07-21T00:00:00Z",
  "modules": [
    {
      "id": "sample_web_tool",
      "name": "Sample Web Tool",
      "versionName": "1.0.0",
      "versionCode": 1,
      "runtimeType": "web",
      "deliveryType": "zip",
      "entry": "index.html",
      "category": "tools",
      "package": {
        "fileName": "sample_web_tool.zip",
        "fileSize": 1024,
        "downloadUrl": "https://modules.example.invalid/sample_web_tool.zip",
        "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      }
    }
  ]
}
```

示例域名不可用于生产。生产目录继续经过现有 Ed25519 目录签名与下载域名白名单，不得因为 V2 解析通过而跳过网络层、SHA 或包签名校验。

客户端不再包含占位公钥。`CatalogSignatureVerifier` 从 BuildConfig 接收 1–3 把原始 32 字节 Base64 Ed25519 公钥，支持密钥轮换，并拒绝空、全零、重复或错误长度配置。Flutter Store Release 必须启用目录验签；stable 构建还必须使用 `catalogSigningProfile=production`。

生产私钥不得进入仓库、Gradle 属性、命令行日志或 APK。`scripts/catalog_signing.py` 只从 `GAME_MATRIX_CATALOG_ED25519_PRIVATE_KEY` 环境变量读取私钥，在严格验证正式 Catalog V2 后对原始字节生成 detached signature，可同时生成 Nginx `X-Catalog-Signature` header 片段。客户端验证的是收到的精确字节，签名后增加换行也会失败。

当前线上 Catalog V8 已返回 `X-Catalog-Signature`，34 个正式条目均具备显式 `runtimeType`/`deliveryType`；`catalog.json` 与 `modules.json` 的响应体和签名均已通过公网读回复验。强制验签与无签名 fail-closed 仍是不可降级的发布门禁。

## 旧目录兼容

以下输入走 `LegacyCatalogAdapter`：

- 顶层 `version` 的旧 `modules.json`；
- 虽标记 `schemaVersion=2`，但模块未完整声明 `runtimeType`/`deliveryType` 的现有展示目录。

适配器从 `kind`、`type`、文件扩展名和内置标记推断运行时/交付方式，并保留规范 `ModuleManifest` 引用。所有模块声明完成显式字段之前，不能删除该兼容路径。

## 校验与降级

结构、运行时必填项、宿主版本范围、HTTPS 和 SHA 校验失败时拒绝该正式 V2；仓库随后使用已验证缓存、assets/旧目录或 `ModuleManager` 救援视图。刷新失败不删除上次有效缓存。

正式 V2 的非内置条目还必须映射到权威 `ModuleManifest`。未映射条目的操作会返回 `package_not_registered`，不会进入排队、下载或安装状态。
