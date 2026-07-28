<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 本文档记录 vc595 / 已签名 Catalog V8 的发布证据及当时的 Pigeon 桥接契约。它不是当前全局事实来源；当前版本、活动实现与发布门槛见 [`../CURRENT_STATE.md`](../CURRENT_STATE.md)。

# Flutter/Kotlin Bridge API

契约源：`flutter_module/pigeons/module_store_api.dart`。生成文件禁止手工编辑：Dart 位于 `lib/core/bridge/module_store_api.g.dart`，Kotlin 位于 `app/.../modules/bridge/generated/ModuleStoreApi.g.kt`。

重新生成：

```powershell
cd flutter_module
D:\Developmment\flutter\bin\dart.bat run pigeon --input pigeons/module_store_api.dart --dart_out lib/core/bridge/module_store_api.g.dart --kotlin_out ..\app\src\main\kotlin\com\gamecenter\app\modules\bridge\generated\ModuleStoreApi.g.kt --kotlin_package com.gamecenter.app.modules.bridge.generated
```

## Host API

| 领域 | 方法 |
|---|---|
| Catalog | `getCatalog`、`refreshCatalog` |
| 查询 | `getInstalledModules`、`getModuleStatus`、`getModuleDetails`、`getUpdateableModules`、`getDownloadProgress` |
| 生命周期 | `downloadModule`、`cancelDownload`、`installModule`、`updateModule`、`updateAllModules`、`uninstallModule` |
| 管理 | `enableModule`、`disableModule`、`rollbackModule`、`openModule` |
| UI 偏好 | `getUiPreference`、`setUiPreference`，仅接受白名单键且限制值长度 |
| 回退 | `openLegacyStore` |

`NativeOperationResult` 始终返回 `success`、可选最新模块视图和结构化 `NativeModuleError`。错误包含 code、用户消息、module ID、runtime、是否可恢复、建议动作和可选技术细节。

## Flutter API 事件

`onModuleEvent` 推送 `eventType`、module ID、runtime、状态、时间、可选下载进度和错误。已定义事件覆盖目录更新、排队、下载、验证、安装、启停、卸载、回滚和打开。

状态字符串统一使用：`not_installed`、`queued`、`downloading`、`verifying`、`installing`、`installed`、`update_available`、`disabled`、`failed`、`rolling_back`、`rolled_back`、`uninstalling`。

## 生成代码命名约束

Dart 对象自带 `Object.runtimeType`，因此 Pigeon 字段命名为 `runtime`，不能改为 `runtimeType`；Catalog JSON 线上字段仍保持 `runtimeType`。

## 线程和所有权

Pigeon Host 实现只调用 `ModuleCoreFacade`，不复制下载/安装逻辑。Catalog 的磁盘读取、解析和权威清单合并在专用串行后台线程执行，结果回到主线程后再通过 Pigeon callback 返回。4 项 UI 偏好由 Dart 并行请求；下载进度由事件流推送且不逐帧调用 `getModuleDetails`。Flutter 不能传入任意偏好键、URL、文件路径或原生类名。
