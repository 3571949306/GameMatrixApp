# VPN模块变更日志

## 模块信息

| 属性 | 值 |
|------|-----|
| 模块ID | vpn |
| 模块类型 | nav（导航模块） |
| 入口类 | `VpnModuleEntryPoint` |
| 入口方式 | 底部导航"VPN"Tab → ModuleShellFragment → VpnFragment |
| 模块商店分类 | vpn |
| 是否内置 | 否（builtIn=false） |
| 当前版本 | v1.0.0 |
| 代码位置 | `feature/vpn/src/main/java/com/gamecenter/app/vpn/` |
| APK文件 | `feature_vpn_v100_v2.apk` |
| 文件大小 | 662KB |

## 功能概述

- 多协议科学上网工具，支持 VMess/VLESS/Trojan/Shadowsocks 节点管理与 VPN 连接
- 独立 APK 模块，通过模块商店下载安装
- VpnServiceProxy 桥接 + ModuleShellFragment 动态宿主

## 支持的协议

| 协议 | 说明 |
|------|------|
| VMess | V2Ray 原生协议 |
| VLESS | V2Ray 轻量协议 |
| Trojan | Trojan 协议 |
| Shadowsocks | SS 协议 |

## 模块架构

```
主 APK（壳）
├── VpnServiceProxy（~70行，仅负责 TUN 隧道建立/拆除）
├── ModuleShellFragment（通用动态模块宿主）
└── VpnDelegate 接口（core:common）

VPN 模块 APK（feature/vpn）
├── VpnModuleEntryPoint（实现 ModuleInterface + FeatureModule + VpnDelegate）
├── VpnFragment（纯代码构建 UI，无 XML 布局依赖）
├── ProtocolFactory（协议工厂）
├── VMessModule / VLESSModule / TrojanModule / SSModule
└── Node / Config 数据模型
```

---

## v1.0.0 — 2026-05-23

### 初始版本
- 支持 VMess/VLESS/Trojan/Shadowsocks 四种协议
- 节点管理：添加、编辑、删除、导入
- VPN 连接/断开控制
- VpnServiceProxy 桥接架构：主 APK 中唯一的 VpnService 实现，仅负责 TUN 隧道建立/拆除
- ModuleShellFragment 动态宿主：未下载模块时显示引导页，下载后自动加载模块 Fragment
- VpnFragment 采用纯代码构建 UI（无 XML 布局依赖），确保动态加载时资源可用
- 模块 APK 已上传至 HK VPS：`feature_vpn_v100_v2.apk`（662KB）
- modules.json 已更新 vpn 条目：entryClass、downloadUrl、sha256、fileSize
- 底部导航栏动态显示：安装VPN模块后自动出现"VPN"Tab

### 修复记录
- 修复内存泄漏：ProtocolFactory 及四个协议模块改用 applicationContext 代替 Activity Context
- 修复模块 ID 不一致：统一为 "vpn"（原 modules.json 为 "vpn_basic"，MainActivity 检查 "vpn"）
- 修复 CloudFlare CDN 缓存旧响应：APK 文件改名为 feature_vpn_v100_v2.apk

## v1.0.1 — 2026-06-23

### 修复记录
- 修复 Android 14+ 后台启动服务限制：VpnFragment.connectToNode 中将 `startService` 改为 `ContextCompat.startForegroundService`，避免在后台启动服务时抛 `IllegalStateException`。VpnServiceProxy.onStartCommand 已调用 `startForeground`，使用 `startForegroundService` 安全。添加 try-catch 兜底，失败时 Toast 提示用户保持应用在前台重试。
