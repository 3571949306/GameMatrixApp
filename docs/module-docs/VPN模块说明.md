<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# VPN 模块

## 模块概述

VPN 模块是 GameCenter 的可下载功能模块，提供多协议科学上网工具，支持 VMess/VLESS/Trojan/Shadowsocks 节点管理与 VPN 连接。

## 模块信息

| 属性 | 值 |
|------|-----|
| 模块 ID | vpn |
| 类型 | nav（导航模块） |
| 入口类 | `com.gamecenter.app.vpn.VpnModuleEntryPoint` |
| 入口方式 | 底部导航栏"VPN"Tab（模块安装后显示） |
| 模块商店分类 | vpn |
| 是否内置 | 否（需从模块商店下载） |
| 当前状态 | 独立 APK 模块，已实现动态加载 |
| 模块路径 | `module-store/feature/tools/vpn/` |

## 架构

### 模块文件

| 文件 | 版本 | 大小 | SHA-256 |
|------|------|------|---------|
| `feature_vpn_v100_v2.apk` | v1.0.0 (versionCode=100) | 661,544 bytes | `222b57edf262c23dd71752ba8ba52933c2ffe78cb1035fab48b00ce56d207bae` |

### 核心类

| 类 | 路径 | 说明 |
|----|------|------|
| `VpnModuleEntryPoint` | `com.gamecenter.app.vpn` | 模块入口点，实现 `ModuleInterface` 和 `FeatureModule` |
| `VpnFragment` | `com.gamecenter.app.vpn` | VPN 主界面 Fragment，展示连接状态和控制按钮 |
| `VpnServiceProxy` | `com.gamecenter.app.vpn.service`（在 :app 中，已 Kotlin 化为 `VpnServiceProxy.kt`） | VPN 服务代理，处理 VpnService 生命周期 |

### 协议支持

| 协议 | 实现类 | 说明 |
|------|--------|------|
| VMess | `VmessModule` | V2Ray VMess 协议 |
| VLESS | `VlessModule` | V2Ray VLESS 协议 |
| Trojan | `TrojanModule` | Trojan 协议 |
| Shadowsocks | `ShadowsocksModule` | Shadowsocks 协议 |

### 数据层

| 类 | 说明 |
|----|------|
| `Node` | 节点数据模型（地址、端口、协议等） |
| `NodeRepository` | 节点数据访问层 |
| `NodeAdapter` | 节点列表 RecyclerView 适配器 |
| `ProtocolFactory` | 协议工厂，根据配置创建协议实例 |

### 构建配置

```groovy
// feature/vpn/build.gradle
plugins {
    id 'com.android.application'  // 编译为独立 APK
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.gamecenter.app.vpn'
    applicationId "com.gamecenter.app.vpn"
}

dependencies {
    compileOnly project(':core:common')  // 运行时由主 APK 提供
    compileOnly 'androidx.appcompat:appcompat:1.6.1'
    compileOnly 'com.google.android.material:material:1.9.0'
    // ... 其他 compileOnly 依赖
}
```

## 加载流程

1. 用户在模块商店点击"下载" → 下载 `feature_vpn_v100_v2.apk` 到 `filesDir/modules/`
2. SHA-256 校验通过
3. 用户点击"打开" → `ModuleLoader` 通过 `DexClassLoader` 加载 APK
4. 实例化 `VpnModuleEntryPoint`，调用 `init()` → `start()`
5. `start()` 通过 `FeatureModule.createFragment()` 返回 `VpnFragment`
6. `ModuleShellFragment` 作为宿主加载 `VpnFragment`
7. 底部导航栏动态添加"VPN"Tab

## 依赖关系

```
:feature:vpn (独立 APK)
  └── compileOnly :core:common  → 运行时由主 APK 提供

:app
  ├── VpnServiceProxy（VPN 系统服务代理，保留在主包）
  └── ModuleShellFragment（模块宿主 Fragment）
```

## 关键设计决策

1. **VPN 服务保留在主包**：`VpnServiceProxy` 和 `android.net.VpnService` 的声明保留在 `:app` 的 AndroidManifest 中，因为 VPN 服务需要系统权限声明
2. **compileOnly 依赖**：所有依赖使用 `compileOnly`，运行时由主 APK 的 ClassLoader 提供
3. **FeatureModule 接口**：通过 `createFragment()` 返回 Fragment，由 `ModuleShellFragment` 宿主加载，避免资源加载问题
4. **自定义 FragmentFactory**：`ModuleShellFragment` 注入支持模块 ClassLoader 的 FragmentFactory，解决状态恢复时的 ClassNotFoundException

## 参考价值

VPN 是早期动态加载模块的代表，其架构可作为游戏与工具模块化的参考模板。项目现已扩展至 9 个动态模块（games/{hall,chinesechess,game2048,klotski,tts} + tools/{ai,tools,vpn,wrongbook}），VPN 模块的实践为后续模块奠定了基础。关键差异：
- VPN 模块无资源文件（纯代码），游戏模块有大量布局、图片和音频资源
- VPN 模块通过 Fragment 加载，游戏模块需要直接启动 Activity
- 游戏模块的资源加载需要额外的 `AssetManager` 合并处理
- 后续工具模块（tools / ai / wrongbook）沿用了 VPN 的 `compileOnly` + `FeatureModule.createFragment()` 模式


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)