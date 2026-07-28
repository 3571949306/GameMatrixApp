<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 下文任何关于 vc595 / 已签名 Catalog V8 的陈述均记录 2026-07-22 的发布证据。它不是当前工作树或生产状态的声明；实时事实请查阅项目根目录的 `docs/CURRENT_STATE.md` 与当前实现。

# 模块商店混合架构改造 - 孤立/重复实现清单

> 本文档记录模块商店相关代码中已识别的孤立代码和重复实现。
> **本轮（P0/P1/P2）不删除任何文件**，仅做记录。后续阶段（P3+）按依赖链逐步清理。
> 创建时间：2026-07-20

## 1. 重复实现汇总

| 类型 | 主用实现 | 孤立/重复实现 | 状态 |
|---|---|---|---|
| ModuleLoader | `app/src/main/java/.../modules/ModuleLoader.kt` | 2 个孤立实现 | 不删除 |
| ModuleDownloader | `app/src/main/java/.../modules/ModuleDownloader.kt` | 1 个孤立实现 | 不删除 |
| ModuleManifest | `app/src/main/java/.../modules/ModuleManifest.kt` | 1 个孤立实现 | 不删除 |
| ModuleVerifier | `app/src/main/java/.../modules/ModuleVerifier.kt` | 1 个孤立实现 | 不删除 |
| HeroBannerAdapter | `app/src/main/java/.../modules/HeroBannerAdapter.kt` | 1 个同名不同业务实现 | **非重复** |

## 2. 详细清单

### 2.1 modular/ModuleLoader.kt

| 项 | 值 |
|---|---|
| 文件路径 | `app/src/main/kotlin/com/gamecenter/app/modular/ModuleLoader.kt` |
| 行数 | 126 |
| 类型 | `class`，基于协程，`LoadResult` 返回值风格 |
| 重复原因 | 与主用 `app/src/main/java/.../modules/ModuleLoader.kt`（object，反射+DexClassLoader+签名校验）功能重叠，但实现完全不同 |
| 当前调用方 | 仅 Hilt DI 的 `ModularModule.kt` 引用 |
| 替代实现 | 主用 `app/src/main/java/.../modules/ModuleLoader.kt`（544 行 ModuleManager 委托给它） |
| 删除风险 | **高**。`ModularModule.kt` 中 `provideModuleLoader` 方法返回此类型，KSP 编译期会解析类型。直接删除会重复 Batch 21 的 KSP 编译失败问题（见 `FILES_TO_DELETE_BATCH21.md`） |
| 后续清理方案 | 1. 重构 `ModularModule.kt` 移除 `provideModuleLoader` 方法；2. 检查 `ModuleCacheManager.kt` 和 modular/ModuleManager.kt 是否仍被生产代码引用；3. 删除后重新编译验证 |

### 2.2 core/module-host/ModuleLoader.kt

| 项 | 值 |
|---|---|
| 文件路径 | `core/module-host/src/main/kotlin/com/gamecenter/app/core/modulehost/ModuleLoader.kt` |
| 行数 | 183 |
| 类型 | `object`，使用 `ModuleClassLoaderPool` 管理生命周期 |
| 重复原因 | 修复主用 ModuleLoader 的 4 个问题（ClassLoader 不释放、SHA 跳过、回退、并发），但**业务代码无任何 import**，完全孤立 |
| 当前调用方 | 无（grep 全代码库无 import） |
| 替代实现 | 主用 `app/src/main/java/.../modules/ModuleLoader.kt` |
| 删除风险 | **低**。无任何引用，但属于 `core/module-host` 模块的一部分，单独删除可能影响模块完整性 |
| 后续清理方案 | 整个 `core/module-host` 模块的清理需要单独评估，不在本轮范围内 |

### 2.3 modular/ModuleDownloader.kt

| 项 | 值 |
|---|---|
| 文件路径 | `app/src/main/kotlin/com/gamecenter/app/modular/ModuleDownloader.kt` |
| 行数 | 240 |
| 类型 | `class`，基于 `OkHttpClient` 注入 + 协程 + `DownloadResult` 返回值风格 |
| 重复原因 | 与主用 `app/src/main/java/.../modules/ModuleDownloader.kt`（object，回调风格，功能完整）功能重叠 |
| 功能差异 | **无签名校验、无 CDN fallback、无指标收集、无取消支持** |
| 当前调用方 | 仅 Hilt DI 的 `ModularModule.kt` 中 `provideModuleDownloader` 方法引用 |
| 替代实现 | 主用 `app/src/main/java/.../modules/ModuleDownloader.kt`（467 行，多源+HTTPS+签名+指标） |
| 删除风险 | **高**。Batch 21 已尝试删除，导致 `ModularModule.kt` 的 `provideModuleDownloader` / `provideModuleCacheManager` / `provideModuleManager` 三个 @Provides 方法 KSP 编译失败。详见 `FILES_TO_DELETE_BATCH21.md` |
| 后续清理方案 | 1. 重构 `ModularModule.kt` 移除 3 个相关 @Provides 方法；2. 重构 `ModuleCacheManager.kt` 不再依赖此类型；3. 重构 modular/ModuleManager.kt 不再依赖此类型；4. 删除后重新编译验证 |

### 2.4 core/module-host/ModuleManifest.kt

| 项 | 值 |
|---|---|
| 文件路径 | `core/module-host/src/main/kotlin/com/gamecenter/app/core/modulehost/ModuleManifest.kt` |
| 行数 | 154 |
| 类型 | `data class`，"v2" 版本 |
| 重复原因 | 与主用 `app/src/main/java/.../modules/ModuleManifest.kt`（197 行）功能重叠 |
| 与主用版的差异 | 1. 新增 `minAppVersionCode`（兼容旧 `minAppVersion`）和 `required` 字段；2. **不再硬编码覆盖 name/description**；3. `fromJsonArray` 跳过单个格式错误条目而不影响其他模块；4. `getAllDownloadUrls` 没有 `githubUrl` 字段 |
| 当前调用方 | 仅被孤立的 `core/module-host/ModuleLoader.kt` 引用 |
| 替代实现 | 主用 `app/src/main/java/.../modules/ModuleManifest.kt`（被 ModuleManager / ModuleStoreActivity / ModuleAdapter / ModuleDetailBottomSheet 等使用） |
| 删除风险 | **低**。无业务代码引用 |
| 后续清理方案 | 本轮 P1 会修改主用版 `ModuleManifest.fromJson()`，移除硬编码覆盖并解析新字段，吸收此孤立版本的优点。删除此文件需要与 `core/module-host` 模块整体清理一起评估 |

### 2.5 core/security/ModuleVerifier.kt

| 项 | 值 |
|---|---|
| 文件路径 | `core/security/src/main/kotlin/com/gamecenter/app/core/security/ModuleVerifier.kt` |
| 行数 | 102 |
| 类型 | `object`，返回 `VerifyResult` sealed class |
| 重复原因 | 与主用 `app/src/main/java/.../modules/ModuleVerifier.kt`（75 行）功能重叠 |
| 与主用版的差异 | 包含文件大小预校验（4MB buffer），返回 `VerifyResult` sealed class 而非 boolean |
| 当前调用方 | 仅被孤立的 `core/module-host/ModuleLoader.kt` 引用 |
| 替代实现 | 主用 `app/src/main/java/.../modules/ModuleVerifier.kt` |
| 删除风险 | **低**。无业务代码引用 |
| 后续清理方案 | 与 `core/module-host` 模块整体清理一起评估 |

### 2.6 ui/HeroBannerAdapter.kt（**非重复，仅同名**）

| 项 | 值 |
|---|---|
| 文件路径 | `app/src/main/kotlin/com/gamecenter/app/ui/HeroBannerAdapter.kt` |
| 行数 | 94 |
| 类型 | `class`，接收 `HeroBannerItem` 列表（不是 ModuleManifest） |
| 业务场景 | `GamesFragment` 首页每日精选横幅（不是模块商店） |
| 与商店版的关系 | **不是重复实现**，是两个不同业务场景的 Banner，只是共用 `item_hero_banner.xml` 布局 |
| 当前调用方 | `app/src/main/java/com/gamecenter/app/GamesFragment.java:47` |
| 处理方式 | **保留不动** |

## 3. 教训与约束

### 3.1 Batch 21 的教训（来自 FILES_TO_DELETE_BATCH21.md）

1. **grep 验证不能仅查全限定名**：同包内的短名引用会漏掉。需同时搜 `import com.gamecenter.app.modular.ModuleDownloader` 和 `\bModuleDownloader\b`（同包短名）
2. **删除文件前必须先确认无 Hilt/DI 引用**：Hilt `@Provides` 方法的返回类型和参数类型会通过 KSP 在编译期解析
3. **删除文件必须重新编译验证**：不能只看 grep 结果就删除

### 3.2 本轮处理原则

- **不删除任何文件**：本轮 P0/P1/P2 的目标是引入远程目录和 UI 配置，不是清理代码
- **不重命名重复类**：避免影响 Hilt DI 绑定
- **不修改 modular/ 和 core/module-host/ 下的代码**：这些是孤立代码，修改无意义
- **修改主用版时优先复用孤立版的优点**：例如 P1 修改 `ModuleManifest.fromJson()` 时会吸收 `core/module-host/ModuleManifest.kt` 的 `required` 字段解析和 `fromJsonArray` 容错逻辑

## 4. 后续阶段（P3+）清理路径

P3 阶段（目录签名 + staging/current/last_good/quarantine 事务安装）完成后，可按以下顺序清理：

1. **第一步**：重构 `ModularModule.kt`，移除对 modular/ModuleDownloader.kt、modular/ModuleLoader.kt、modular/ModuleManager.kt 的 @Provides 方法
2. **第二步**：检查 modular/ModuleCacheManager.kt 是否仍被生产代码引用，若无则删除
3. **第三步**：删除 modular/ModuleDownloader.kt、modular/ModuleLoader.kt、modular/ModuleManager.kt
4. **第四步**：评估 core/module-host 模块的整体清理（包括 ModuleLoader.kt、ModuleManifest.kt、ModuleClassLoaderPool 等）
5. **第五步**：评估 core/security/ModuleVerifier.kt 的清理（仅被 core/module-host 引用）

每一步删除后必须：
- 重新编译 `:app:assembleDebug`
- 跑 `:app:testDebugUnitTest`
- 真机验证模块下载/安装/加载功能
- logcat 无 FATAL EXCEPTION