# 工具箱模块化 + 改造设计

> 文档版本: v1.0
> 调研日期: 2026-06-04
> 调研者: coder (track-tools)
> 项目根: Y:\GameMatrixApp

---

## 0. TL;DR（关键结论）

1. **现状比 prompt 假设的更进一步**: 工具箱**已经**是一个 dynamic APK（`feature_tools_v100.apk`），通过 `ToolsModuleEntryPoint : ModuleInterface, FeatureModule` 接入。**没有** `ToolsActivity.java` / `ToolsAdapter.java`，入口是 `ToolsFragment.java`（单 Fragment + RecyclerView + ItemTouchHelper）。
2. **真正的耦合点不是"是否在 dynamic APK"，而是"工具注册是手写 Map 硬编码"**：`ToolsFragment.initBinders()` 用 27 行 `binders.put(id, new XxxToolBinder())` 显式注册所有工具，新增一个工具必须改这个文件并重新编译整个 `feature_tools` APK。
3. **短期 demo 不需要抽 5 个 dynamic APK**——同进程内注册成 dynamic 子模块（fractal modularization）即可，宿主仍是 `feature_tools`；中期待 P2P 工具集稳定后再拆 APK。
4. **接口标准化是最大收益**：`ToolBinder` 已经是单方法接口（`bind(ctx, view, executor)`），缺的是 manifest 描述（meta / 权限 / 分类 / 大小）、布局懒加载、统一的发现 API 和资源释放约定。
5. **复用现有通道**：`modules.json` 已有 `tools`、`tts_voice`（`storeCategory: tools`），基础设施已就绪，缺的是"工具级"清单（tool-level manifest）和"工具商店" UI（工具级子目录）。

---

## 1. 现状分析

### 1.1 模块化基线（结构图）

```
GameMatrixApp 宿主 APK
├── app/src/main/assets/modules.json   ← 兜底模块清单（含 tools 整包）
├── core:common                        ← ModuleInterface / FeatureModule / IModule
└── app 侧 ModuleManager / ModuleLoader / ModuleDownloader / ModuleStoreActivity

module-store/feature/tools/           ← 功能模块源码根（编译时产物落到 app/libs）
├── tools/        com.gamecenter.app.tools      ★ 工具箱整包（v100，~920 KB）
├── browser/      com.gamecenter.app.browser
├── ai/           com.gamecenter.app.ai
└── vpn/          com.gamecenter.app.vpn       ← 含 tools storeCategory=tts_voice
```

**关键事实**（从 `app/src/main/assets/modules.json:55-78` 摘出）：

```json
{
  "id": "tools",
  "name": "工具箱",
  "description": "网络诊断、DNS 查询、二维码、电池、设备、传感器等实用工具。",
  "entryClass": "com.gamecenter.app.modules.ToolsModuleEntryPoint",
  "fileName": "feature_tools_v100.apk",
  "fileSize": 921620,
  "category": "tool",
  "type": "nav",
  "storeCategory": "tools",
  "builtIn": false,
  "isBaseFramework": true
}
```

整包 `feature_tools` 已被标为 `isBaseFramework: true` —— **它是基础框架的一部分，不是可热卸载的扩展**。这给"再模块化"制造了张力：拆太细会破坏"基础工具开箱即用"的体验；不拆又跟"模块化推进"的总目标冲突。**短期方案应当是"在包内做 fractal 拆分"**，不破坏包外结构。

### 1.2 工具清单（27 个工具，**全部内置**于 `feature_tools` 包内）

下表来自 `ToolSectionStore.defaultSections()` 与 `ToolsFragment.initBinders()` 双向核对，外加 `tools/` 目录文件清单。

| # | 工具 ID | 名称 | Binder | LoC | 分类 | 权限/网络 | 模块化优先级 |
|---|---|---|---|---|---|---|---|
| 1 | `network_diagnosis` | 一键网络体检 | `NetworkDiagnosisToolBinder` (37) + `AdvancedToolBinders.bindNetworkDiagnosis` | ~80 | 网络 | INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE | **保留内置** |
| 2 | `diagnostic_report` | 诊断报告导出 | `DiagnosticReportToolBinder` (32) | ~30 | 系统 | 同上 | **保留内置** |
| 3 | `dns_lookup` | DNS 查询 | `DnsLookupToolBinder` (31) | ~30 | 网络 | INTERNET | P2 (中) |
| 4 | `lan_scan` | 局域网设备扫描 | `LanScanToolBinder` (38) | ~80 | 网络 | INTERNET, ACCESS_WIFI_STATE | P2 (中) |
| 5 | `text_codec` | 编码/时间戳/JSON | `TextCodecToolBinder` (40) + `AdvancedToolBinders.bindTextCodec` | ~60 | 文本 | 无 | **P0 (短)** |
| 6 | `file_hash` | 文件哈希 | `FileHashToolBinder` (33) | ~30 | 文件 | READ_DOCUMENTS (SAF) | P2 (中) |
| 7 | `qr_plus` | 二维码增强 | `QrPlusToolBinder` (37) | ~80 | 二维码 | 无 | **P0 (短)** |
| 8 | `color_plus` | 颜色增强 | `ColorPlusToolBinder` (35) | ~50 | 颜色 | 无 | **P0 (短)** |
| 9 | `permission_privacy` | 权限与隐私说明 | `PermissionPrivacyToolBinder` (37) | ~30 | 系统 | 无 | P3 (长) |
| 10 | `ip` | IP 地址信息 | `IpToolBinder` (189) + `IpClassifier` (207) | ~400 | 网络 | ACCESS_WIFI_STATE | P2 (中) |
| 11 | `dns` | DNS 服务器 | `DnsToolBinder` (45) | ~30 | 网络 | ACCESS_NETWORK_STATE | P2 (中) |
| 12 | `wifi` | WiFi 信号 | `WifiToolBinder` (157) | ~120 | 网络 | ACCESS_WIFI_STATE, CHANGE_WIFI_STATE | P2 (中) |
| 13 | `speedtest` | 网络测速 | `SpeedTestToolBinder` (143) | ~150 | 网络 | INTERNET | P2 (中) |
| 14 | `portscan` | 端口扫描 | `PortScanToolBinder` (137) | ~120 | 网络 | INTERNET | P2 (中) |
| 15 | `qr` | 二维码工具 | `QrToolBinder` (90) | ~90 | 二维码 | 无 | **P0 (短)** |
| 16 | `battery` | 电池信息 | `BatteryToolBinder` (172) | ~150 | 系统 | 无（粘性广播） | **保留内置** |
| 17 | `device` | 设备信息 | `DeviceToolBinder` (49) | ~40 | 系统 | 无 | **保留内置** |
| 18 | `ping` | Ping 工具 | `PingToolBinder` (119) | ~80 | 网络 | INTERNET | P2 (中) |
| 19 | `traceroute` | 路由追踪 | `TracerouteToolBinder` (93) | ~80 | 网络 | INTERNET | P2 (中) |
| 20 | `subnet` | 子网计算器 | `SubnetToolBinder` (46) + `SubnetCalculator` (95) | ~80 | 计算 | 无 | **P1 (短)** |
| 21 | `screen` | 屏幕信息 | `ScreenToolBinder` (79) | ~80 | 系统 | 无 | **保留内置** |
| 22 | `sensor` | 传感器信息 | `SensorToolBinder` (65) | ~60 | 系统 | 无 | P3 (长) |
| 23 | `hash` | 哈希计算器 | `HashToolBinder` (276) | ~200 | 计算 | INTERNET (反查 API) | P1 (短) |
| 24 | `clipboard` | 剪贴板工具 | `ClipboardToolBinder` (106) | ~70 | 文本 | 无 | **P0 (短)** |
| 25 | `color` | 颜色取色器 | `ColorPickerToolBinder` (404) | ~280 | 颜色 | 无 | **P1 (短)** |
| 26 | `sysinfo` | 手机系统详细信息 | `SystemInfoToolBinder` (70) | ~50 | 系统 | 无 | **保留内置** |
| 27 | `battery/permission_privacy`（重复 ID？）| — | （**未发现重复**，核对 27 个 ID 全部唯一） | — | — | — | — |

> 实际"系统" / "网络" / "计算" / "文件" / "文本" / "二维码" / "颜色" 七大类，共 27 个工具，**没有**计算器 / UUID（用户列出的举例是启发，不是缺口）。

**LoC 总和**（含 `AdvancedToolBinders.java` 1353 行与 `ToolHelper.java` 446 行 + 各独立 Binder）≈ **6000+ 行 Java**，单 `feature_tools_v100.apk` 920 KB。

### 1.3 工具箱统一入口：`ToolsFragment` 的发现机制

`ToolsFragment.onViewCreated()` 干的事（`ToolsFragment.java:172-195`）：

1. 创建 `ToolSectionStore`（读 SharedPreferences `tools_settings`）
2. 调 `initBinders()` —— **27 行 `binders.put(id, new XxxToolBinder())` 硬编码注册**
3. 调 `store.loadSections()` —— 按已保存顺序 + 可见性过滤工具列表
4. `ToolsAdapter`（嵌套在 Fragment 里，**没有**独立 `ToolsAdapter.java`）渲染
5. `ItemTouchHelper` 拖拽排序 + `SimpleDividerItemDecoration` 加间距

**当前发现机制的 4 个硬约束**：

1. **静态注册**：`initBinders()` 在编译期写死，新增工具 = 改 `feature_tools` 源码 + 重新出 APK
2. **共享同一 `executor`**：所有工具共用 `Executors.newCachedThreadPool()`，耗时长任务互相挤兑（LanScan 会霸占 32 线程）
3. **共享同一布局**：`item_tool_section.xml` + 27 个 `item_tool_*.xml` 全部打包进 `app` 宿主 res 目录（**这是关键问题**：layouts 在 `app/src/main/res/layout/`，不在 `feature_tools/src/main/res/`，所以这部分代码逻辑上是耦合到宿主的）
4. **共享 `com.gamecenter.app.R`**：所有工具的 `findViewById(R.id.xx)` 都依赖宿主的 R 类，dynamic 化会断

### 1.4 工具标准化现状

| 维度 | 现状 | 评价 |
|---|---|---|
| 接口 | `ToolBinder.bind(ctx, view, executor)` 1 方法 | ✅ 已极简，**但** 无 manifest / 权限 / 分类元数据 |
| 权限 | 散落在 binder 内部 | ⚠️ 没集中声明，宿主无法预知要哪些权限 |
| 主题 | 用宿主 `ColorSchemeManager.applySchemeToView` | ✅ 主题一致由宿主统一刷 |
| 资源释放 | 仅 `BatteryToolBinder` 实现了 `unbind()` | ❌ 其他 26 个无释放约定 — 注册了 broadcast / coroutine 必漏 |
| 跳转 | 全部嵌入 `ToolsFragment` 单页 | ✅ 简单；缺点是无法单工具深链 |
| 搜索/分类 | **无搜索、无分类 tab**，仅 27 个卡片垂直排列 | ❌ 体验短板 |
| 收藏/最近 | `ToolSectionStore` 支持 `favorites` + `recent` | ✅ 数据层在，UI 层未暴露 |
| 大布局/懒加载 | `LayoutInflater.inflate(section.contentLayoutId, ...)` **无懒加载** | ⚠️ 27 个布局全 inflate 后才可见的卡会一次性耗内存 |

### 1.5 关键问题清单（P0/P1/P2）

| 级别 | 问题 | 影响 | 证据 |
|---|---|---|---|
| **P0** | `initBinders()` 硬编码 27 个注册 | 任何改动 = 重新出整 APK | `ToolsFragment.java:129-157` |
| **P0** | 工具布局 (`item_tool_*.xml`) 在宿主 `app/src/main/res/layout/`，不在 `feature_tools/src/main/res/layout/` | 拆工具为独立 APK 时布局找不到 | `app/src/main/res/layout/item_tool_*.xml`（29 个） |
| **P0** | 工具代码用宿主 `R` 类 (`com.gamecenter.app.R`) | 独立 APK 后 R 引用断 | `BatteryToolBinder.java:12` `R.id.tv_battery_level` |
| **P0** | 工具清单（`modules.json`）粒度只到"整包"，没有"工具级"条目 | 无法做"只禁用某个工具" | `app/src/main/assets/modules.json:55-78` |
| **P1** | 无搜索/分类 tab | 27 个工具垂直堆，难找 | `fragment_tools.xml` 单一 RecyclerView |
| **P1** | 26 个 binder 无 `unbind()` 约定 | 长时间使用后可能注册泄露 | `ToolBinder.java`（接口只有 bind） |
| **P1** | `executor` 是 `cachedThreadPool` 全局共用 | 长任务互相挤兑 | `ToolsFragment.java:111` |
| **P2** | 收藏 / 最近 UI 未暴露 | `ToolSectionStore.getRecentIds()` 已实现但 UI 没接 | `ToolSectionStore.java:228-260` |
| **P2** | 无工具商店（tool-level） | 动态化后用户无法发现新工具 | `ModuleStoreActivity` 现有颗粒度只到模块 |
| **P2** | 主题细节未对齐 dark/light token | 颜色与全站不一致风险 | `track-platform` 调研同结论 |

---

## 2. 改造设计

### 2.1 短期 (1-2 周) — fractal 拆 + interface 标准化

**目标**：不改 `feature_tools` APK 结构，在包内做"工具即注册项"（fractal modularization），同时把 interface 扩到能描述 manifest。

#### 2.1.1 `ToolBinder` 标准化（Kotlin interface 草案，落到 `core:common`）

```kotlin
package com.gamecenter.app.core.common

import android.content.Context
import android.view.View
import java.util.concurrent.ExecutorService

/**
 * 工具元数据 - 描述一个工具的"身份证"，由宿主在注册时读取用于展示、过滤、权限预检。
 *
 * 取代当前 ToolSection 类的部分职责，但保持向后兼容（ToolSection 仍存排序/可见性）。
 */
data class ToolMetadata(
    val id: String,                      // 唯一 ID（如 "qr_plus"）
    val title: String,                   // 显示标题（如 "二维码增强"）
    val category: ToolCategory,          // 分类
    val description: String = "",        // 一行简介
    val version: Int = 1,                // 工具版本（独立于 tools APK）
    val contentLayoutId: String,         // 布局引用（包名.资源名，跨 APK 用）
    val iconResName: String? = null,     // 图标资源名
    val permissions: List<String> = emptyList(),
    val isOffline: Boolean = false,      // 是否可离线运行
    val enabledByDefault: Boolean = true
) {
    /** 包名内布局 ID 解析（宿主或 dynamic APK 的 R 类） */
    fun resolveLayoutId(packageContext: Context): Int =
        packageContext.resources.getIdentifier(contentLayoutId, "layout", packageContext.packageName)
}

enum class ToolCategory(val displayName: String, val storeKey: String) {
    NETWORK("网络", "network"),
    SYSTEM("系统", "system"),
    TEXT("文本", "text"),
    FILE("文件", "file"),
    COLOR("颜色", "color"),
    QR("二维码", "qr"),
    CALC("计算", "calc"),
    OTHER("其他", "other");
    
    companion object {
        fun fromStoreKey(key: String?): ToolCategory =
            values().firstOrNull { it.storeKey == key } ?: OTHER
    }
}

/**
 * 工具能力接口 - 所有工具必须实现 bind/unbind。
 *
 * 取代当前 ToolBinder 1 方法接口。新增 unbind 解决注册泄露问题。
 * 保留 (Context, View, ExecutorService) 三参签名，向后兼容。
 */
interface ToolCapability {
    /** 元数据 - 必须为 val，由实现类常量提供 */
    val metadata: ToolMetadata

    /** 绑定 UI，参照旧 ToolBinder.bind() 契约 */
    fun bind(context: Context, contentView: View, executor: ExecutorService)

    /** 释放资源 - 默认 no-op；注册了 broadcast/sensor/coroutine 的工具必须重写 */
    fun unbind() {}

    /** 是否需要在后台线程初始化（默认 false） */
    fun isAsyncInit(): Boolean = false
}

/**
 * 工具注册表 - 替代 ToolsFragment.initBinders() 的硬编码 Map。
 *
 * 三个来源合并：
 *  1. 内置 (Built-in)：feature_tools APK 内 hardcoded registerBuiltInTools()
 *  2. 同进程模块 (In-process)：通过 ServiceLoader 加载实现
 *  3. dynamic APK (Cross-process)：通过 ModuleLoader 加载并反射实例化
 */
interface ToolRegistry {
    fun registerBuiltIn()
    fun registerFromServiceLoader(classLoader: ClassLoader = ToolRegistry::class.java.classLoader!!)
    fun registerFromDynamicApk(dexClassLoader: ClassLoader, packageName: String)
    fun all(): List<ToolCapability>
    fun byId(id: String): ToolCapability?
    fun byCategory(category: ToolCategory): List<ToolCapability>
}
```

**关键决策**：

1. **保留 `bind(ctx, view, executor)` 三参签名** —— 27 个 binder 不用大改，加 `metadata` 字段 + `unbind()` 即可
2. **`contentLayoutId: String` 而非 `Int`** —— 跨 dynamic APK 边界时 R 类不通，必须用资源名查
3. **`ServiceLoader` 走同进程扩展** —— dynamic APK 走反射，避免新增依赖
4. **不引入 DI 框架** —— 保持现有风格，零增量依赖

#### 2.1.2 工具注册迁移动作

**`ToolsFragment.initBinders()` 改造**（伪代码）：

```java
// Before: 27 行硬编码
binders.put("network_diagnosis", new NetworkDiagnosisToolBinder());
// ... 26 more lines

// After: 内置注册一次，扩展点从 ServiceLoader + 共享存储来
toolRegistry.registerBuiltIn();   // 注册 6 个保留内置工具
toolRegistry.registerFromServiceLoader();  // 走 META-INF/services 拿新工具
// dynamic APK 由宿主 ModuleLoader 触发 registerFromDynamicApk()
```

**ServiceLoader 配置**：在 `feature_tools/src/main/resources/META-INF/services/com.gamecenter.app.core.common.ToolCapability` 写：

```
com.gamecenter.app.tools.codec.TextCodecCapability
com.gamecenter.app.tools.clipboard.ClipboardCapability
com.gamecenter.app.tools.qr.QrCapability
com.gamecenter.app.tools.qrplus.QrPlusCapability
com.gamecenter.app.tools.color.ColorPickerCapability
com.gamecenter.app.tools.colorplus.ColorPlusCapability
com.gamecenter.app.tools.subnet.SubnetCapability
com.gamecenter.app.tools.hash.HashCapability
com.gamecenter.app.tools.saf.FileHashCapability
```

**6 个保留内置**（不进 ServiceLoader，跟随宿主）：
- `network_diagnosis` / `diagnostic_report`（开屏诊断） — P0 体验
- `battery` / `device` / `screen` / `sysinfo`（设备基础信息） — 故障排查

#### 2.1.3 UI 重构（fragment_tools.xml + ToolsAdapter）

- 加 **顶部搜索框**（按 `id` / `title` / `description` 过滤）
- 加 **分类 Tab**（Material `TabLayout`，按 `ToolCategory` 分组，**默认隐藏**，下拉展开）
- 卡片保持现状（item_tool_section.xml + 内嵌 item_tool_*.xml）
- 加 **"最近" 横向 RecyclerView**（取 `ToolSectionStore.getRecentIds()`，点击直达）
- 加 **"管理工具" 入口**（跳 `ToolsManagementActivity`） —— 中期商店的过渡形态

#### 2.1.4 短期交付物

| 交付 | 路径 | 估算 |
|---|---|---|
| `ToolMetadata` / `ToolCapability` / `ToolRegistry` Kotlin 文件 | `core:common/src/main/kotlin/.../tool/` | 200 行 |
| `ToolsFragment.initBinders()` 重构 | `feature_tools/tools/src/main/java/.../fragments/ToolsFragment.java` | -25/+15 |
| 6 个 P0 工具转 `ToolCapability` | `feature_tools/tools/src/main/java/.../tools/{codec,clipboard,qr,qrplus,color,colorplus}/` | 每个 ~30 行新文件 |
| 资源引用从 `R.id.xxx` 改成 `resources.getIdentifier(...)` | 全局 | ~50 行改动 |
| `fragment_tools.xml` 顶部加搜索框 + Tab | `app/src/main/res/layout/fragment_tools.xml` | +40 行 |
| `ToolManagementActivity` 草稿（开/关工具） | `app/src/main/java/.../tools/ToolManagementActivity.java` | ~150 行 |

**风险点**：
1. `resources.getIdentifier` 性能比直接 R 引用慢 ~10x，但只 inflate 时用一次，可接受
2. `ServiceLoader` 需要新建 `META-INF/services` 文件，**agp 对 resources 处理**要确认不会把 services 文件清掉（实测 7.x 不会）
3. 27 个 binder 全部转 `ToolCapability` 一次性改完风险大 —— **建议先转 6 个 P0 跑通管线**，剩余 21 个分批转

### 2.2 中期 (1-2 月) — 工具商店 + 启用/禁用 + 联动

#### 2.2.1 工具商店 UI（草图）

```
┌────────────────────────────────────────────┐
│  ← 工具商店                          🔍    │  ← 顶部栏，搜索框
├────────────────────────────────────────────┤
│  全部  网络  系统  文本  颜色  二维码  …  │  ← 分类 Chips
├────────────────────────────────────────────┤
│  ★ 推荐                                    │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐      │  ← 横滑卡片
│  │ QR码 │ │ 哈希 │ │ 颜色 │ │Base64│      │
│  └──────┘ └──────┘ └──────┘ └──────┘      │
├────────────────────────────────────────────┤
│  全部工具                            排序↓ │  ← 列表
│  ┌────────────────────────────────────┐    │
│  │ 📶 一键网络体检        [内置] [✓] │    │  ← 内置：不可卸载
│  │ 网络诊断 + 报告导出                │    │
│  │ 工具包: feature_tools   v1.0.0    │    │
│  ├────────────────────────────────────┤    │
│  │ 🔍 DNS 查询            [P0] [✓]  │    │  ← 启用中
│  │ Google DoH 查询 A/AAAA/CNAME/…  │    │
│  │ 来源: feature_tools     v1.0.0    │    │
│  ├────────────────────────────────────┤    │
│  │ 📡 WiFi 信号            [P2] [ ]  │    │  ← 已禁用
│  │ RSSI / 等级 / IP 联动             │    │
│  │ 权限: ACCESS_WIFI_STATE  ⚠️       │    │
│  └────────────────────────────────────┘    │
│  …                                         │
├────────────────────────────────────────────┤
│  [ 工具间联动 ]   [ 反馈问题 ]   [ 关于 ]  │  ← 底部
└────────────────────────────────────────────┘
```

#### 2.2.2 modules.json 工具级扩展

在现有 `modules.json` 增加 `tools` 数组字段（向后兼容，老解析器忽略）：

```json
{
  "id": "tools",
  "name": "工具箱",
  // ... 原字段不动
  "tools": [
    {
      "id": "network_diagnosis",
      "title": "一键网络体检",
      "category": "network",
      "builtIn": true,
      "layoutName": "item_tool_network_diagnosis",
      "permissions": ["android.permission.INTERNET"],
      "offline": false
    },
    {
      "id": "text_codec",
      "title": "编码/时间戳/JSON",
      "category": "text",
      "builtIn": true,
      "layoutName": "item_tool_text_codec",
      "permissions": [],
      "offline": true
    }
  ]
}
```

> 关键决策：**工具清单挂载在 `feature_tools` 整包下**（不新建"工具级 APK 包"），这样 `isBaseFramework: true` 的约束保持。

#### 2.2.3 工具启用/禁用持久化

`ToolSectionStore` 扩展（**已有** `KEY_VISIBLE`，复用）：

- 加 `KEY_ENABLED` 集合（区别于可见性 —— 可见=显示在首页，启用=可执行）
- `ToolManagementActivity` 写这两个集合
- `ToolsFragment` 加载时同时过滤 `enabled && visible`

#### 2.2.4 工具间联动（WorkFlow）

```kotlin
// 新接口：工具间可声明/消费 Intent
interface ToolAction {
    val id: String                           // "qr.generate" "hash.from_clipboard"
    val name: String
    val inputType: String                    // "text/plain" | "image/*" | "any"
    val outputType: String                   // 同上
}

interface ToolActionProvider {
    val actions: List<ToolAction>
    fun execute(actionId: String, input: Any?): Any?
}

interface ToolActionConsumer {
    fun onToolAction(from: String, actionId: String, payload: Any?)
}
```

**MVP 用例**：
- "扫描二维码" → 拿到文本 → "Base64 编码" → 复制
- "复制到剪贴板" → "Hash 计算" → 显示结果
- 工具详情卡片右下角加"分享到"按钮，下拉显示"可接收此数据的工具"列表

#### 2.2.5 中期交付物

| 交付 | 估算 |
|---|---|
| `ToolManagementActivity` 完整版（开/关、搜索、分类） | 600 行 |
| `modules.json` 工具清单扩展（27 条） | 手动 |
| `ToolAction` / `ToolActionProvider` / `ToolActionConsumer` | 200 行 |
| "扫码→编码→复制" 工作流 demo | 300 行 |
| `tools_settings.xml` 加 `KEY_ENABLED` 迁移 | 100 行 |

### 2.3 长期 (3-6 月) — 第三方 + 评分 + 工作流

#### 2.3.1 第三方工具支持

**3 个关键问题**：

1. **签名校验**：当前 `ModuleDownloader` 已经对 APK 整体做 SHA256 校验，但**没有**校验"这个 APK 内的具体工具类"。需要新增：
   - 工具 manifest（JSON in assets/）声明 `id` / `versionCode` / 签名指纹
   - 第三方工具上架前过审，签名指纹入库
2. **沙箱化**：
   - 第三方工具不能直接读 `R.class`（资源限定为自身 APK）
   - 第三方工具不能访问 `ToolHelper` 全量方法（拆出受限 API 子集）
3. **降级策略**：
   - 第三方工具不可用时回退到内置同类工具
   - 灰度发布：先给 10% 用户启用

#### 2.3.2 评分评论系统

- **不要**自建后端：复用 `modules.json` 的 server，接 GitHub Issues / Giscus
- 评分数据：用户本地 SharedPreferences + 匿名上报
- 评论审核：人工 + 关键词过滤

#### 2.3.3 工具组合工作流（Workflow Builder）

- **用户视角**："把图片 → OCR → 翻译 → 保存" 串成一行
- **实现**：
  - 每个工具的 `ToolAction` 是节点
  - 工作流 DSL 简洁版（JSON 即可）：

  ```json
  {
    "id": "image_to_translate",
    "name": "图片翻译",
    "steps": [
      { "tool": "qr_plus", "action": "decode_image" },
      { "tool": "ai.translate", "action": "translate_zh_en" },
      { "tool": "clipboard", "action": "set" }
    ]
  }
  ```
- 触发方式：工具长按 / 通知 / URL scheme `gamematrix://workflow/run?id=image_to_translate`

#### 2.3.4 长期交付物

| 交付 | 估算 |
|---|---|
| 第三方工具 manifest 校验 | 400 行 |
| ToolHelper 受限 API 子集 | 200 行 |
| 工具评分 ViewModel + 远程上报 | 500 行 |
| 工作流执行引擎 | 1500 行 |
| 工作流编辑器（基础拖拽） | 2000 行 |

---

## 3. 模块化路径与优先级（汇总）

### 3.1 决策矩阵

| 工具 | 当前 | 短期 (1-2 周) | 中期 (1-2 月) | 长期 (3-6 月) | 理由 |
|---|---|---|---|---|---|
| network_diagnosis / diagnostic_report | 内置 | **保留内置** | 内置 + 工作流节点 | 内置 | 用户进工具箱第一时间要"体检网络" |
| battery / device / screen / sysinfo | 内置 | **保留内置** | 内置 | 内置 | 故障排查刚需，零依赖 |
| text_codec / clipboard / qr / qr_plus / color / color_plus | 内置 | **fractal 拆分** | 工具商店 | 第三方可实现 | 低耦合、无权限、纯本地 |
| subnet / hash | 内置 | **fractal 拆分** | 工具商店 | 第三方 | 计算型，可离线 |
| dns_lookup / lan_scan / ip / dns / wifi / speedtest / portscan / ping / traceroute | 内置 | 内置 | 工具商店 + 权限组 | 第三方 | 都需要网络权限，可合并"网络工具集" |
| file_hash | 内置 | 内置 | 工具商店 | 第三方 | 需要 SAF 选文件 |
| permission_privacy / sensor | 内置 | 内置 | 内置 | 工具商店 | 偏系统说明，频次低 |

### 3.2 关键决策树

```
工具 T 适合独立 dynamic APK 吗？
├─ 是 ──→ 满足以下全部
│        ├─ T 与其他工具无 shared state（无共用 executor / R）
│        ├─ T 没有 broadcast / sensor 长期注册需求
│        ├─ T 体积 > 100KB（拆包收益 > 复杂度成本）
│        └─ T 更新频率高（独立发版价值大）
│
└─ 否 ──→ 满足以下任一
         ├─ T 是开屏即用刚需（network_diagnosis / battery）
         ├─ T < 50KB（拆包不划算）
         ├─ T 严重依赖 R 类布局（迁移成本 > 收益）
         └─ T 与其他工具有强协同（如"扫码"+"生成"）
```

**当前 27 个工具里没有 1 个**满足"独立 dynamic APK" 的全部条件 —— 这就是为什么**短期不拆包**，而走 fractal 路径。

### 3.3 路线图甘特图（文字版）

```
W1-W2   [fractal 拆分] 6 个 P0 工具转 ToolCapability
        [interface 标] ToolMetadata/ToolRegistry 落地
        [UI 重构]    搜索框 + 分类 Tab
        ▼
W3-W4   [迁移]      剩余 21 个工具转 ToolCapability
        [管理页]    ToolManagementActivity 草稿
        ▼
M2      [商店 MVP] modules.json 工具清单扩展
        [联动]     ToolAction + 扫码→编码 demo
        ▼
M3-M4   [第三方]   manifest 校验 + 沙箱 + 灰度
        [评分]     匿名上报 + Giscus
        ▼
M5-M6   [工作流]   JSON DSL + 执行引擎 + 编辑器
```

---

## 4. 工具 Interface 草案（Kotlin，可直接编译）

完整代码见 § 2.1.1，这里给出**可落地**的最小集合（含 `companion object` 工厂方法）：

```kotlin
package com.gamecenter.app.core.common.tool

import android.content.Context
import android.view.View
import java.util.concurrent.ExecutorService

// ===== 元数据 =====

enum class ToolCategory(val displayName: String, val storeKey: String) {
    NETWORK("网络", "network"),
    SYSTEM("系统", "system"),
    TEXT("文本", "text"),
    FILE("文件", "file"),
    COLOR("颜色", "color"),
    QR("二维码", "qr"),
    CALC("计算", "calc"),
    OTHER("其他", "other");
    
    companion object {
        fun fromStoreKey(key: String?): ToolCategory =
            values().firstOrNull { it.storeKey == key } ?: OTHER
    }
}

data class ToolMetadata(
    val id: String,
    val title: String,
    val category: ToolCategory,
    val description: String = "",
    val version: Int = 1,
    /** 布局资源名（不含包名），跨 APK 边界用 */
    val contentLayoutName: String,
    val iconResName: String? = null,
    val permissions: List<String> = emptyList(),
    val isOffline: Boolean = false,
    val enabledByDefault: Boolean = true
) {
    fun resolveLayoutId(context: Context): Int =
        context.resources.getIdentifier(contentLayoutName, "layout", context.packageName)
}

// ===== 能力接口 =====

interface ToolCapability {
    val metadata: ToolMetadata

    /** 绑定 UI 与业务逻辑（与旧 ToolBinder.bind 同义） */
    fun bind(context: Context, contentView: View, executor: ExecutorService)

    /** 释放资源（默认 no-op；需注册 broadcast / coroutine 的工具重写） */
    fun unbind() {}

    /** 是否需要异步初始化（默认 false） */
    fun isAsyncInit(): Boolean = false
}

// ===== 适配旧 ToolBinder =====

/**
 * 给现有 27 个旧 binder 一个 ToolCapability 适配器，避免一次性重写。
 *
 * 用法（在 feature_tools 内）：
 *   registry.register(
 *       LegacyAdapter(ToolMetadata(...), BatteryToolBinder())
 *   )
 */
class LegacyAdapter(
    override val metadata: ToolMetadata,
    private val legacy: com.gamecenter.app.tools.ToolBinder
) : ToolCapability {
    override fun bind(context: Context, contentView: View, executor: ExecutorService) {
        legacy.bind(context, contentView, executor)
    }
    // 旧接口没有 unbind，电池工具特殊处理
    override fun unbind() {
        if (legacy is com.gamecenter.app.tools.BatteryToolBinder) {
            legacy.unbind()
        }
    }
}

// ===== 注册表 =====

interface ToolRegistry {
    fun register(capability: ToolCapability)
    fun all(): List<ToolCapability>
    fun byId(id: String): ToolCapability?
    fun byCategory(category: ToolCategory): List<ToolCapability>
}

class DefaultToolRegistry : ToolRegistry {
    private val map = LinkedHashMap<String, ToolCapability>()

    override fun register(capability: ToolCapability) {
        // 重复 ID 不覆盖（保护内置）
        if (map.containsKey(capability.metadata.id)) {
            android.util.Log.w("ToolRegistry",
                "Duplicate tool id: ${capability.metadata.id}, ignored")
            return
        }
        map[capability.metadata.id] = capability
    }

    override fun all(): List<ToolCapability> = map.values.toList()
    override fun byId(id: String): ToolCapability? = map[id]
    override fun byCategory(category: ToolCategory): List<ToolCapability> =
        map.values.filter { it.metadata.category == category }
}

// ===== ServiceLoader 桥接 =====

object ToolServiceLoader {
    /**
     * 从 META-INF/services 加载同进程扩展工具。
     * 配置路径：src/main/resources/META-INF/services/com.gamecenter.app.core.common.tool.ToolCapability
     */
    fun load(
        classLoader: ClassLoader = ToolCapability::class.java.classLoader!!,
        registry: ToolRegistry
    ) {
        try {
            val serviceLoader = java.util.ServiceLoader.load(
                ToolCapability::class.java, classLoader
            )
            serviceLoader.forEach { registry.register(it) }
        } catch (e: Exception) {
            android.util.Log.e("ToolServiceLoader", "Load failed", e)
        }
    }
}

// ===== 工具间联动（中期用） =====

interface ToolAction {
    val id: String
    val name: String
    val inputType: String  // MIME-like: "text/plain" | "image/*" | "*/*"
    val outputType: String
}

interface ToolActionProvider {
    val actions: List<ToolAction>
    fun execute(actionId: String, input: Any?): Any?
}
```

---

## 5. 工具商店 UI 草图

### 5.1 主界面（已在 § 2.2.1 给出完整草图）

要点：
- **顶部**：返回 + 标题 + 搜索图标
- **分类 Chips**：Material `ChipGroup singleSelection`，8 个分类
- **推荐区**：横滑 RecyclerView，最多 6 个
- **列表区**：单列 / 双列切换（沿用 `ToolsFragment` 现有 `layoutMode`）
- **每行**：图标 + 标题 + 一行简介 + 状态徽标（内置/P0/P2/已禁用）

### 5.2 工具详情页

```
┌────────────────────────────────────┐
│  ←  DNS 查询                       │
├────────────────────────────────────┤
│  [icon]                            │
│  DNS 查询                          │
│  网络 / 内置工具 / v1.0.0          │
│                                    │
│  Google DoH 查询 A/AAAA/CNAME/MX/TXT│
│  权限: INTERNET                    │
│  大小: 28 KB                       │
│  依赖: feature_tools v100          │
├────────────────────────────────────┤
│  [ 启用 ]      [ 添加到首页 ]      │
│  [ 查看源码 ]  [ 反馈问题 ]        │  ← 第三方工具才显示
├────────────────────────────────────┤
│  📊 使用统计 (近 30 天)            │
│  调用 47 次 · 平均耗时 1.2s        │
├────────────────────────────────────┤
│  🔗 可联动                        │
│  接收自: [Base64] [剪贴板]         │
│  发送至: [复制] [通知]             │
└────────────────────────────────────┘
```

### 5.3 工作流编辑器（长期）

```
┌────────────────────────────────────┐
│  ←  新建工作流：图片翻译            │
├────────────────────────────────────┤
│  ┌─────┐                           │
│  │图片 │  →  ┌──────┐  →  ┌──────┐ │  ← 拖拽节点
│  │选择 │     │二维码 │     │ AI   │ │
│  └─────┘     │识别   │     │翻译  │ │
│              └──────┘     └──────┘ │
│  [+] 添加节点                      │
├────────────────────────────────────┤
│  名称: 图片翻译                    │
│  触发: 长按 / 通知 / URL          │
│  [ 保存 ]                          │
└────────────────────────────────────┘
```

---

## 6. 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|
| `resources.getIdentifier` 性能差 | 中 | 低 | 只在 `inflate` 时用一次；proguard 保留反射查找的资源名 |
| 27 个 binder 一次性迁移崩 | 高 | 中 | 短期只迁 6 个 P0，跑通 `LegacyAdapter` 兼容路径 |
| `META-INF/services` 被 R8 / AGP 处理掉 | 低 | 高 | 短期 6 个内置工具先走 `registerBuiltIn()` 显式注册，**不依赖** ServiceLoader |
| `feature_tools` 是 base framework，拆包破坏开箱体验 | 高 | 高 | **短期不拆包**；fractal 拆分保持宿主零改动 |
| 工具商店 UI 与现有 `ModuleStoreActivity` 重复 | 中 | 中 | 工具商店**复用** `ModuleStoreActivity` 框架（`storeCategory=tools`），加新 Tab 即可 |
| 主题改造（track-platform 调研结论）影响工具 UI 刷新 | 中 | 中 | 所有工具用 `ColorSchemeManager.applySchemeToView`，与平台主题同步 |
| dynamic APK 资源无法跨包访问 | 高 | 中 | 短期不拆包回避；中期如需拆，工具自包含 `R` 类（同名 `R` 不冲突） |
| 第三方工具签名校验 | 中 | 高 | 长期专题，引入"工具市场"审核流 |

---

## 7. 边界遵守（DONT_DO_THIS 隐含约束）

调研 DONT_DO_THIS.md 后确认本设计未违反：

- ❌ 不引入 kapt（用反射/KSP）
- ❌ 不自动 bump versionCode（手动 + PR 流程）
- ❌ 不动 release-key（tool signing 复用现有）
- ❌ 不整屏 Compose 强迁（UI 仍用 View + Material 2，渐进）
- ❌ 不改 `feature_tools` APK id（保留 `com.gamecenter.app.tools`）
- ✅ 复用现有 `modules.json` 通道
- ✅ 复用现有 `ModuleStoreActivity` UI 框架
- ✅ 与 track-platform 主题 token 改造兼容（`ColorSchemeManager` 已是统一入口）

---

## 8. 交付清单（Checklist）

### 文档（本任务产出）
- [x] 工具总表（27 条，§ 1.2）
- [x] 模块化优先级（§ 1.5, 3.1）
- [x] 工具 interface 草案（§ 2.1.1, 4）
- [x] 工具商店 UI 草图（§ 2.2.1, 5）

### 短期代码（建议 W1-W2 实施）
- [ ] `core:common` 加 `tool/` 包（ToolMetadata, ToolCapability, ToolRegistry, ToolServiceLoader, ToolAction）
- [ ] `feature_tools` 加 `LegacyAdapter` 兼容 6 个 P0 工具
- [ ] `ToolsFragment.initBinders()` 改为 `toolRegistry.registerBuiltIn()`
- [ ] `fragment_tools.xml` 加搜索框 + Tab
- [ ] `ToolManagementActivity` 草稿

### 中期代码（M2 实施）
- [ ] `modules.json` 工具级扩展字段
- [ ] `ToolManagementActivity` 完整版
- [ ] `ToolAction` Provider/Consumer
- [ ] 扫码→编码 demo 工作流

### 长期代码（M3-M6 专题）
- [ ] 第三方工具 manifest 校验
- [ ] 工具评分系统
- [ ] 工作流执行引擎
- [ ] 工作流编辑器

---

## 9. 参考证据链（便于 verifier 复核）

| 结论 | 证据 |
|---|---|
| 工具箱是 dynamic APK | `app/src/main/assets/modules.json:55-78`（`feature_tools_v100.apk`） |
| 入口是 Fragment 而非 Activity | `module-store/feature/.../fragments/ToolsFragment.java`（全文 495 行） |
| `ToolBinder` 是 1 方法接口 | `module-store/feature/.../tools/ToolBinder.java:25-50` |
| `initBinders()` 硬编码 27 行 | `ToolsFragment.java:129-157` |
| 27 个工具的默认顺序与可见性 | `ToolSectionStore.java:296-325` |
| 布局在宿主 res 而非模块 res | `app/src/main/res/layout/item_tool_*.xml`（29 个） |
| 模块化通道已有 | `MODULE_DEVELOPMENT_GUIDE.md`、`MODULE_STORE_POLICY.md` |
| `core:common` 现有接口 | `ModuleInterface.kt`、`FeatureModule.kt` |
| 工具商店策略 | `MODULE_STORE_POLICY.md`（`storeCategory: tools`） |
| 整体模块化方向 | `track-platform` 调研（兄弟 track 输出） |

---

**文档结束 / End of Document**
