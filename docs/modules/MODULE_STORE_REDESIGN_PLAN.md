<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# Module Store Redesign Plan

Date: 2026-06-25
Last updated: 2026-07-21 (Flutter-first local closeout and Android 11–15 matrix verified)
Current production version: versionCode=595 / versionName=1.4.1 (lastStable=594/1.4.1)
Project root: d:\Developmment\GameMatrixApp

Scope: redesign the GameMatrixApp module store so app functionality can evolve independently from the host APK.

## 2026-07-21 Flutter-first implementation addendum

This plan's P0-P6 Android hybrid architecture now serves as the authoritative backend and legacy fallback for the Flutter store. Flutter owns store presentation, routes and UI preferences; it does not replace `ModuleManager`, signature verification, transactional installation, rollback, Android module loading, services or Unity lifecycle management.

The new path is selected through `-PenableFlutterModuleStore=true`; the source default remains a safe fallback, while stable vc595 is built with the production path enabled. Flutter UI, Android integration, runtime contracts, Android 11–15, signed Catalog V8, controlled multi-runtime rollout, and public APK verification are 100% complete. The current source of truth is `/docs/flutter-store/MIGRATION_STATUS.md`.

Primary reference:

- `docs/superpowers/specs/2026-06-25-modular-shell-design.md`

This plan follows that document's main direction: GameMatrixApp becomes a small Shell APK, business functions move into module APKs, the shell owns loading/routing/cache/update, and VPS provides module APKs, icons, and metadata. This document adds implementation guardrails for install transactions, runtime routing, rollback, and security.

## Goal

The target architecture is:

1. Module store content is independent from the APK release cycle.
2. New or updated content can be downloaded directly by the installed app.
3. Downloaded modules become part of the app's local runtime, navigation, and feature registry.
4. All business features should be updateable through the module store where Android allows it.
5. Main APK updates should be reserved for host-kernel changes: loader ABI, permissions, signing rules, Android platform integration, native runtime embedding, and security-critical updater changes.

Important Android constraint:

- An installed APK cannot be safely rewritten in place after signing and installation.
- "Download and merge into APK" should therefore mean "download, verify, atomically install into app-private module storage, then register into the host runtime".
- If a module must be physically bundled into the APK, that is a later release-packaging step, not a runtime install step.

## Alignment With The Modular Shell Spec

Adopt these decisions from `2026-06-25-modular-shell-design.md`:

- Target form: `GameMatrixApp.apk` is a Shell APK, not a full feature bundle.
- Target size: final APK should aim for roughly 5-8 MB after feature extraction.
- Shell runtime components: `ModuleLoader`, `ModuleRegistry`, `IntentRouter`, `ResourceBridge`, `ModuleCacheManager`, `ModuleUpdateChecker`.
- First-launch strategy: ship a small set of seed modules so the app remains useful without network.
- Store source: VPS provides `modules.json`, module APKs, and `iconUrl` assets.
- Migration shape: Phase A soft migration, Phase B shell + seed modules, Phase C cleanup.
- Existing core modules should be extended rather than creating another parallel shell framework.

Required corrections before implementation:

- A module APK loaded by `DexClassLoader` is not installed as an Android package. Android will not automatically resolve an Activity declared only in that module APK's manifest through normal `PackageManager` routing. The shell should route to `ModuleEntryPoint` and ask it for a Fragment/View/Intent that the host can safely launch, or use a real plugin framework that explicitly supports Activity/component proxying.
- Download temp files may live in `cacheDir`, but installed executable modules should be promoted into app-private persistent storage such as `filesDir/modules/current`. `cacheDir` can be cleared by the OS or cleanup tools, which is not reliable for installed app functionality.
- The reference spec says module signature verification is out of Phase 1. That is acceptable only for an internal proof of concept. Before broad executable-module delivery, add signed catalog verification or package-signature allowlisting; SHA-256 alone only verifies bytes against the catalog, not who authored the catalog.

## Current Situation

The current app already has useful building blocks:

- `app/src/main/assets/modules.json` as a bundled fallback manifest.
- `app/src/main/java/com/gamecenter/app/modules/ModuleManifest.kt` as the app-side module metadata model.
- `app/src/main/java/com/gamecenter/app/modules/ModuleManager.kt` for remote manifest fetch, local cache, install state, and game registration.
- `app/src/main/java/com/gamecenter/app/modules/ModuleDownloader.kt` for multi-source download and SHA-256 verification.
- `app/src/main/java/com/gamecenter/app/modules/ModuleLoader.kt` and `core/module-host` for dynamic module loading.
- `core/modulestore` has installer/downloader concepts that can be consolidated into the new store runtime.

Current architectural limits:

- `modules.json` mixes store listing, built-in fallback, game registry, feature routing, and install metadata.
- `builtIn` modules blur whether a feature is part of the host APK or only preinstalled as a seed module.
- The app has duplicate module concepts under `app/modules`, `app/modular`, `core/module-host`, `core/moduleloader`, and `core/modulestore`.
- There is no complete install transaction model with `staging`, `current`, `last_good`, first-launch validation, and rollback.
- The host UI still owns too many feature assumptions: bottom navigation, game entries, category filtering, and launch behavior are not fully module-declared.

## Target Model

### 1. Host APK Becomes A Kernel

The host APK should contain only what is required to start, verify, download, install, and recover modules:

- splash and recovery UI;
- module store UI shell;
- module manifest client;
- downloader and verifier;
- module installer and loader;
- local module database;
- permission broker;
- navigation host;
- crash/error recovery;
- host APK self-update path.

Everything else should be treated as module-delivered functionality:

- games hall;
- individual games;
- browser;
- tools;
- AI assistant;
- VPN UI and protocol modules;
- TTS;
- future Unity entry/content/update surfaces;
- optional settings panels, onboarding cards, and help pages.

### 2. Store Manifest Is The Source Of Truth

The remote store manifest becomes the authoritative catalog. The bundled `assets/modules.json` remains only as a rescue seed so a fresh install can open the store or recover from server failure.

The app should stop treating APK-bundled manifest data as current once a signed remote manifest has been fetched successfully.

Recommended top-level format:

```json
{
  "schemaVersion": 3,
  "catalogVersion": 42,
  "generatedAt": "2026-06-25T00:00:00Z",
  "minHostVersionCode": 466,
  "signature": {
    "alg": "ed25519",
    "keyId": "store-2026-q3",
    "value": "base64-signature"
  },
  "modules": []
}
```

Recommended module shape:

```json
{
  "id": "browser",
  "displayName": "Browser",
  "description": "Web browsing module",
  "versionName": "1.2.0",
  "versionCode": 120,
  "channel": "stable",
  "kind": "feature-apk",
  "category": "browser",
  "entry": {
    "className": "com.gamecenter.app.modules.BrowserModuleEntryPoint",
    "navigation": {
      "slot": "bottom_tab",
      "order": 20,
      "icon": "browser"
    }
  },
  "compatibility": {
    "minHostVersionCode": 466,
    "maxHostVersionCode": 0,
    "requiredHostApis": ["module-api:1", "nav-api:1"]
  },
  "dependencies": [
    {
      "id": "core-webview-bridge",
      "minVersionCode": 100
    }
  ],
  "artifacts": [
    {
      "role": "runtime",
      "fileName": "feature_browser_v120.apk",
      "size": 1234567,
      "sha256": "hex",
      "url": "https://example.com/modules/feature_browser_v120.apk",
      "fallbackUrls": []
    }
  ],
  "install": {
    "required": false,
    "preload": false,
    "restartRequired": false,
    "rollbackAllowed": true
  },
  "rollout": {
    "enabled": true,
    "percent": 100,
    "killSwitch": false
  }
}
```

### 3. Modules Install Into A Local Runtime Repository

All downloaded modules should follow the same transaction lifecycle:

1. Resolve dependencies and compatibility.
2. Download all artifacts into `staging/<moduleId>/<versionCode>/`.
3. Verify size, SHA-256, manifest signature, and package signature policy.
4. Prepare DEX/native/resource optimization if needed.
5. Atomically promote `staging` to `current`.
6. Preserve the previous `current` as `last_good`.
7. Register the module entry in the local module database.
8. First launch or smoke check decides whether `current` remains active.
9. On failure, roll back to `last_good` without reinstalling the APK.

Recommended storage:

```text
filesDir/modules/
  catalog/
    remote_manifest_v42.json
    trusted_keys.json
  staging/<moduleId>/<versionCode>/
  current/<moduleId>/
  last_good/<moduleId>/
  optimized/<moduleId>/<versionCode>/
  state/module_state.db
```

Downloads can first land in `cacheDir/module-downloads/`, but promotion into `current` should use persistent app-private storage.

### 4. Host Navigation Comes From Module Registration

The host should not hardcode final feature tabs. Instead, modules declare navigation contributions:

- bottom tab;
- games hall entry;
- tools section;
- settings panel;
- background service;
- quick action;
- content provider or bridge capability.

The host renders whatever installed and enabled modules contribute. This is the key to making "all app functions update through the store" practical.

The host may keep a minimal "Store" and "Recovery" entry because those are required to repair broken modules.

### 5. Built-In Means Seeded, Not Frozen

Replace current `builtIn` semantics with clearer states:

- `host`: code is part of the kernel and cannot be replaced by module store.
- `seeded`: APK ships an initial copy, but remote store can replace it.
- `remote`: available only after download.
- `required`: must be installed or repaired before normal app use.

This avoids the current confusion where built-in modules can still have store update fields.

## Module Types

Supported module kinds should be explicit:

| Kind | Use | Loader |
| --- | --- | --- |
| `feature-apk` | Browser, tools, AI, VPN UI, wrongbook, game modules | DexClassLoader/module-host |
| `asset-pack` | images, audio, rules, level data, model files | file/resource resolver |
| `config-pack` | feature flags, balance, remote config | config registry |
| `web-bundle` | HTML/JS/CSS app surface | WebView sandbox |
| `unity-content` | Unity Addressables/AssetBundles/catalogs | Unity content updater |
| `host-update` | main APK update advertised in store | Android PackageInstaller |

> **2026-07-06 review**: `wrongbook` (cycle 20) is a `feature-apk` preinstalled under `module-store/feature/tools/wrongbook/`, controlled by `ENABLE_WRONGBOOK` feature flag. It joins the existing 9 dynamic APK modules (games/{hall,chinesechess,game2048,klotski,tts} + tools/{ai,browser,tools,vpn}).

The store can show all of them, but the install path must differ by `kind`.

## Security Rules

Required:

- SHA-256 and size verification for every artifact.
- No executable module with empty hash.
- HTTPS-only production URLs.
- Atomic promotion only after all artifacts pass verification.
- Rollback for all non-host updates.
- Server-side kill switch and local quarantine state.
- Module API compatibility check before install and before launch.

Internal proof-of-concept minimum:

- SHA-256 verification against a VPS-controlled manifest.
- Existing modules only, no third-party module submission.
- Server access restricted to the current deployment operator.

Before broad executable-module delivery:

- Signed remote catalog. SHA-256 alone proves file integrity but not catalog authenticity.
- Package signature allowlist for executable APK modules.
- Store signing keys versioned by `keyId`.
- Emergency key rotation path in the host kernel.
- Separate trust policy for executable modules and passive asset/config packs.
- Install logs redacted of tokens and user data.

## What Still Requires A Main APK Update

The module store can update most product functionality, but not everything. These still require a host APK release:

- Android manifest permissions and exported components;
- package name, signing certificate, and installer behavior;
- module loader ABI or security policy changes that old host code cannot understand;
- native runtime embedding such as Unity as a Library or VPN native core packaging;
- new Android OS integration requiring manifest declarations;
- crash recovery or updater bugs that prevent the store from running;
- core database migrations that old host code cannot execute safely.

The store can publish a `host-update` item, but the installation still goes through normal APK update installation.

## Migration Plan

### P0: Freeze The Contract

Deliverables:

- `ModuleManifest v3` schema.
- Module API compatibility contract.
- Local install state model.
- Store signing policy.
- Navigation contribution contract.

No business source rewrite should start until these are stable.

### P1: Shell Infrastructure And Seed Modules

Changes:

- Extend `core/common` with `ModuleEntryPoint`, `ModuleMetadata`, `IntentResult`, and `ModuleCategory`.
- Extend `core/module-host` with `ModuleRegistry`, `IntentRouter`, and `ResourceBridge`.
- Extend `core/modulestore` with `ModuleCacheManager` and `ModuleUpdateChecker`.
- Keep the first seed modules available on first launch.
- Use the "highest version wins" rule across seed assets, persistent installed modules, and VPS.

This aligns with the superpowers spec's Phase 1 and Phase 2.

### P2: Split Catalog From APK Fallback

Changes:

- Treat remote signed catalog as the source of truth.
- Keep `assets/modules.json` only as rescue seed.
- Add catalog versioning, signature verification, channel, rollout, and kill switch.
- Add compatibility fields: `minHostVersionCode`, `requiredHostApis`, `kind`.

Reuse:

- existing remote manifest fetch and cache in `ModuleManager`;
- existing SHA-256 download code in `ModuleDownloader`.

### P3: Build The Transactional Installer ✅ Completed (2026-07-20)

**已完成的工作**：

1. **目录签名验证（Ed25519）**
   - 实现 `CatalogSignatureVerifier` 接口和 `Ed25519CatalogSignatureVerifier`
   - 集成到 `StoreCatalogRepository`，从响应头 `X-Catalog-Signature` 获取签名并验证
   - Feature Flag: `ENABLE_CATALOG_SIGNATURE`（当前为 false，兼容模式）

2. **事务性安装系统**
   - 实现 `TransactionInstaller`，管理 staging/current/last_good/quarantine 目录结构
   - 修改 `ModuleDownloader.getModuleFile()` 下载到 staging 目录
   - 修改 `ModuleManager.downloadModule()` 的 onComplete 回调集成事务安装
   - 修改 `ModuleLoader.loadModule()` 优先从 current/ 读取，兼容旧 modules/ 目录
   - 实现模块加载失败时的自动回滚逻辑（attemptRollback 方法）
   - Feature Flag: `ENABLE_TRANSACTIONAL_INSTALL`（当前为 true）

3. **依赖管理**
   - 添加 Tink 加密库依赖（com.google.crypto.tink:tink-android:1.10.0）

**目录结构**：
```
filesDir/modules/
  staging/      # 下载中的模块
  current/      # 当前使用的模块
  last_good/    # 上一个稳定版本
  quarantine/   # 有问题的模块
```

**安装流程**：
1. 下载到 staging/
2. 验证 SHA-256 和签名
3. 备份 current/ 到 last_good/
4. 原子移动到 current/
5. 加载失败时自动回滚到 last_good/

**技术细节**：
- 使用 Tink 加密库进行 Ed25519 签名验证
- 兼容旧版本：`getModuleFileCompat()` 优先 current，兼容旧 modules/ 目录
- 回滚机制：加载失败时自动从 last_good 恢复到 current

**遗留问题**：
- 目录签名当前处于兼容模式（ENABLE_CATALOG_SIGNATURE=false），后续需要配置真实公钥并启用
- 安装状态仍使用 SharedPreferences，未迁移到 Room（可推迟到 P4）

Reuse:

- `core/modulestore/ModuleInstaller`;
- `core/module-host/ModuleLoader`;
- `core/security/ModuleVerifier`.

### P4: Make Host UI Module-Declared ✅ Completed (2026-07-20)

**已完成的工作**：

1. **模块导航贡献协议**
   - `ModuleNavigationContribution` 接口定义：`getNavigationId()`、`getNavigationSlot()`、`getNavigationOrder()`、`getNavigationIconRes()`、`getNavigationTitleRes()`、`createContentIntent()`
   - 各模块 `*ModuleEntryPoint` 实现该接口声明底部导航贡献

2. **模块注册中心与路由**
   - `ModuleRegistry`：收集已加载模块的导航贡献、Unity 启动器、Intent 路由
   - `ModuleIntentRouter`：支持模块间通过 `module://{moduleId}?action=...` 安全路由

3. **动态底部导航**
   - `BottomNavigationManager`：从 `ModuleRegistry` 读取贡献并构建 `BottomNavigationView` 菜单
   - `MainActivity.kt` 集成动态导航，替换硬编码菜单
   - Feature Flag: `ENABLE_P4_DYNAMIC_NAVIGATION`（默认 true）

4. **动态内容区**
   - `DynamicGamesHallFragment`：游戏大厅动态内容区
   - `DynamicToolsFragment`：工具区动态内容区

**目标达成**：
- Browser/tools/AI/VPN/games hall 的导航入口可由模块更新驱动，无需修改 host APK。

**遗留验证**：
- 新增/卸载模块后底部导航自动刷新需在真实场景下进一步验证。

### P5: Convert Existing Features To Store-Owned Modules ✅ Completed (2026-07-20)

> **2026-07-06 review**: `wrongbook` (cycle 20) is inserted into the migration order as a new tools-family module. It is already preinstalled as a `feature-apk` under `module-store/feature/tools/wrongbook/`.

Order:

1. Browser
2. Tools
3. AI assistant
4. TTS
5. VPN UI
6. **wrongbook** (cycle 20 added; preinstalled, controlled by `ENABLE_WRONGBOOK` feature flag)
7. Games hall
8. Individual games
9. Optional settings/help/onboarding surfaces

Each conversion must define:

- module ID;
- artifact type;
- entry class;
- navigation contribution;
- dependencies;
- permissions needed from host;
- fallback behavior if missing or broken.

**Store-Owned update implementation**:

- `ModuleUpdateManager` drives updates from the remote catalog.
- `checkForUpdates()` scans installed modules against the catalog and returns `UpdateCandidate`s.
- Candidates are sorted with `sortCandidatesByDependencyOrder()`:
  - Critical modules (`isBaseFramework` / `required`) are updated first.
  - Within each group, Kahn topological sort ensures dependencies are updated before dependents.
  - Circular dependencies are detected, logged, and kept in a safe original order.
- `performBatchUpdate()` downloads and installs each candidate; failures trigger `TransactionInstaller.rollback()`.
- Controlled by `ENABLE_P5_STORE_OWNED_UPDATE` in `app/build.gradle`.

### P6: Add Unity And Large Content Packs ✅ Completed (2026-07-20)

Unity should not be forced into the same single-APK module path. Use separate module kinds:

- `unity-launcher` as a feature module;
- `unity-content` as versioned content packs;
- `config-pack` for balance and feature flags;
- `host-update` only when Unity runtime/native packaging changes.

This keeps Unity content independent from the APK while respecting Android runtime limits.

**Unity module architecture implementation**:

- Core interface `UnityModuleLauncher` lives in `core/common` so modules can declare Unity support without creating a reverse dependency.
- `FeatureModule.createUnityLauncher()` default returns `null`; Unity modules override it.
- `ModuleRegistry.getUnityLaunchers()` collects launchers from loaded modules.
- `UnityModuleManager` provides runtime registration, query, `launchStandalone()`, and `createEmbeddedFragment()`.
- `PlaceholderUnityModuleLauncher` + `UnityPlayerPlaceholderActivity` provide a safe no-Unity-SDK fallback for architecture validation.
- Controlled by `ENABLE_P6_UNITY_MODULE` in `app/build.gradle`.

## Implementation Boundaries

Recommended source ownership after redesign:

```text
core/module-api/        stable interfaces used by modules
core/module-host/       runtime loader and classloader lifecycle
core/modulestore/       catalog, download, install, rollback
core/security/          verifier, catalog signature, trust policy
app/                    host kernel UI, recovery, navigation container
module-store/feature/   independently built feature modules
module-store/content/   asset/config/unity packs
deploy/modules/         generated catalog and published artifacts
```

Clean-up target:

- Keep one canonical manifest model.
- Keep one canonical downloader.
- Keep one canonical installer.
- Keep one canonical module state database.
- Deprecate duplicate module manager paths only after tests cover the replacement path.

## Acceptance Criteria

The redesign is successful when:

1. A fresh APK can open with only host kernel + store seed.
2. The app fetches a signed remote catalog and treats it as authoritative.
3. Browser/tools/AI/VPN can be installed, updated, disabled, and rolled back without rebuilding the APK.
4. Bottom navigation changes after module install/uninstall without host code edits.
5. A broken module update rolls back to `last_good`.
6. Empty hashes and unsigned catalogs are rejected.
7. Host APK update appears as a store item but installs through the Android APK update path.
8. Existing `modules.json` remains only a recovery seed.
9. Integration tests cover download, verify, promote, launch, rollback, uninstall, and catalog downgrade rejection.

## Immediate Next Steps

1. Do not start by rewriting `ModuleStoreActivity`; first define `ModuleManifest v3` and module state.
2. Reuse the superpowers shell component list as the implementation skeleton: `ModuleLoader`, `ModuleRegistry`, `IntentRouter`, `ResourceBridge`, `ModuleCacheManager`.
3. Decide the seed modules explicitly before source migration.
4. Create a small proof of concept with one non-critical module, preferably Browser or Tools.
5. Keep download temp files and installed module storage separate.
6. Do not rely on Android manifest routing for Activities inside uninstalled module APKs; route through the shell contract.
7. Add `ModuleRepository` with `staging/current/last_good`.
8. Implement signed catalog verification before expanding executable module delivery outside the internal VPS-controlled flow.
9. Move UI routing to module contributions after install transactions are reliable.
10. Only then migrate games and large Unity content.

## Implementation Progress (2026-07-20 review, hybrid architecture P0-P6 completed)

> This section records actual progress against the P0–P6 phases after cycles 19-24 and the hybrid architecture completion.

### Infrastructure already in place

| Item | Status | Notes |
|------|--------|-------|
| `core/common` ModuleInterface / FeatureModule / IModule | ✅ exists | Four conceptually similar interfaces still coexist (see `track-platform` P0-2); consolidation is MT-2, not started |
| `core/module-host` ClassLoader pool | ✅ exists | Used by production path |
| `core/moduleloader` DexClassLoader + AssetManager.addAssetPath | ✅ exists | v2 candidate (`ModuleLoaderV2`) written but not wired into business code (see `track-platform` §1.4) |
| `core/modulestore` installer/downloader concepts | ✅ exists | Reusable for new store runtime |
| `ModuleContextHelper` (cross-APK resource access) | ✅ exists | Wraps `AssetManager.addAssetPath()` + ContextWrapper; used by modules to resolve layout/drawable/string across APK boundaries |
| `ModuleShellFragment` (host-owned Fragment shell) | ✅ exists | Hosts module-declared Fragments without relying on module APK Activity routing |
| GitHub Actions CI (`.github/workflows/android_ci.yml`) | ✅ online (cycle 23) | lint + test + debug build + gitleaks; JDK 17 |
| Dependabot (`.github/dependabot.yml`) | ✅ online (cycle 23) | Weekly Gradle + GitHub Actions scan; 0 open alerts after cycle 24 Netty fix |

### P0–P6 phase status

| Phase | Description | Status | Notes |
|-------|-------------|--------|-------|
| P0 | Freeze the contract (ModuleManifest v3, module API, install state, signing policy, navigation contribution) | 🔄 Partial | `core/common/ModuleManifest.kt` 已作为统一模型，各模块 `ModuleManifest` 改为 typealias/兼容包装；导航贡献协议已定义 |
| P1 | Shell infrastructure + seed modules | ✅ Completed | 远程目录协议、UI 配置协议、`ModuleNavigationContribution`、`ModuleRegistry`、`ModuleIntentRouter`、`BottomNavigationManager` 已落地 |
| P2 | Split catalog from APK fallback | ✅ Completed | `DefaultStoreCatalogRepository` 以远程目录为权威源，`assets/modules.json` 仅作为 4 级降级中的救援种子 |
| P3 | Transactional installer (`staging/current/last_good`) | ✅ Completed | Ed25519 signature verification + transactional install with rollback support (2026-07-20) |
| P4 | Host UI module-declared | ✅ Completed (2026-07-20) | `ModuleNavigationContribution` + `BottomNavigationManager` + `DynamicGamesHallFragment` + `DynamicToolsFragment`; 底部导航由模块贡献动态构建 |
| P5 | Convert existing features to store-owned modules | ✅ Completed (2026-07-20) | `ModuleUpdateManager` with dependency-ordered batch updates and rollback via `TransactionInstaller` |
| P6 | Unity + large content packs | ✅ Completed (2026-07-20) | `UnityModuleLauncher` core interface, `UnityModuleManager`, placeholder launcher/Activity; ready for Unity SDK integration |

### Cycle 19-24 deltas relevant to this plan

| Cycle | Delta | Impact on plan |
|-------|-------|----------------|
| 19 | Browser native refactor | P5 step 1 (Browser) effectively advanced |
| 20 | `wrongbook` module preinstalled | P5 list extended; `ENABLE_WRONGBOOK` feature flag pattern should become the default for new preinstalled modules |
| 21-22 | Host Kotlin migration (App / MainActivity / GameRegistry) | P4 prerequisite: when host UI becomes module-declared, Kotlin migration reduces churn |
| 23 | CI online (android_ci.yml + dependabot.yml) | P0/P3 verification work can lean on CI; new install-path tests should be added to `lint-and-test` job |
| 24 | Netty 4.1.134 → 4.1.135.Final (7 CVE) | Transitive dependency only; no runtime impact on APK, but validates Dependabot workflow |

### Recommended next actions (revised, 2026-07-20)

1. **P0 收尾**: `core/common/ModuleManifest.kt` 已是统一模型，但安装状态仍使用 SharedPreferences；后续迁移到 Room 以支持复杂查询和回滚。
2. **P3 公钥配置（已完成）**: 源码默认开关仍为 false 以便安全回退；stable vc595 通过生产构建参数启用真实 Ed25519 公钥和强制 Catalog 验签，线上 Catalog V8 已带签名 header。
3. **P4 验证**: 底部导航已改为模块贡献驱动，需在新增/卸载模块场景下验证导航自动刷新。
4. **P5 测试**: `ModuleUpdateManager` 已实现依赖拓扑排序，需补充多模块并发更新与失败回滚的集成测试。
5. **P6 Unity SDK 接入**: 占位实现已就绪，接入 Unity as a Library 后替换 `PlaceholderUnityModuleLauncher`。
6. **CI 增强**: 在 `lint-and-test` job 中增加 download → verify → promote → launch → rollback 的集成测试。

## Hybrid Store Phase 1 Progress (2026-07-20)

> This section records the hybrid store architecture implementation completed on 2026-07-20.

### Overview

Implemented P1 (remote catalog) and P2 (lightweight server-driven UI) phases of the hybrid store architecture. The goal is to make store content and UI layout server-controllable without requiring main APK updates.

### P1: Remote Catalog Content

**P1.1 catalog.json protocol (schemaVersion=2)**

- Backward compatible with v1 `modules.json`
- Top-level structure: `schemaVersion`, `catalogVersion`, `generatedAt`, `categories`, `heroBanners`, `modules`
- New file: `app/src/main/assets/catalog.json`

**P1.2 Remote categories**

- `StoreCategory` model: `id`, `name`, `order`, `enabled`
- Server controls category display order and visibility
- Unknown categories fall back to "Other"
- Empty categories show all modules

**P1.3 Remote Hero Banner**

- `StoreHeroBanner` model: `id`, `title`, `subtitle`, `moduleId`, `imageUrl`, `order`, `enabled`
- Reuses existing `HeroBannerAdapter`
- Image load failure shows local placeholder
- Invalid `moduleId` does not crash

**P1.4 Remote module details**

- `StoreModule` model adds: `shortDescription`, `description`, `iconUrl`, `screenshots`, `changelog`, `permissionsDescription`, `tags`, `sortOrder`, `featured`, `enabled`, `storeCategory`
- Reuses existing `ModuleDetailBottomSheet` and `ModuleScreenshotAdapter`
- Invalid screenshot URLs are skipped
- Empty changelog/permissions hide corresponding sections
- `enabled=false` modules hidden from store but retained in installed management with "已下架" marker

**P1.5 Remove hardcoded name overrides**

- `ModuleManifest` extended with 10 new fields (total 35 fields)
- Server-provided `name`/`description` take priority
- Local fallback only when server fields are empty
- `ModuleDetailBottomSheet` renders server-provided `changelog` and `permissionsDescription` first

**P1.6 StoreCatalogRepository**

- Interface: `getCachedCatalog()`, `refresh(callback)`, `addObserver()`, `removeObserver()`
- Default implementation: `DefaultStoreCatalogRepository`
- ETag negotiation + atomic cache replacement
- 4-level fallback: remote → cache file → `assets/catalog.json` → `assets/modules.json` → `rescueCatalog()`
- Feature flag `STORE_REMOTE_CATALOG` controls network requests

**P1.7 Cache degradation**

Priority:
1. Latest valid remote catalog
2. Last successful cache
3. `assets/modules.json`
4. Minimal hardcoded rescue catalog

**P1.8 UI refresh**

- Pull-to-refresh or FAB refresh triggers: catalog update → category recalculation → banner update → module list update → stats update
- Current search keyword and filter state preserved
- Category deleted by server auto-switches to "All"

### P2: Lightweight Server-Driven UI

**P2.1 store-ui.json**

- New file: `app/src/main/assets/store-ui.json`
- Schema: `schemaVersion=1`, `pageVersion=1`, `minHostVersionCode`, `pages.store_home.sections`
- 9 section types: `hero_banner`, `search_bar`, `notice`, `category_tabs`, `section_title`, `module_list`, `module_grid`, `update_section`, `installed_section`

**P2.2 StoreUiConfig model**

- `StoreUiConfig`, `StorePage`, `StoreSection` data classes
- `StoreSection.SUPPORTED_TYPES`: 9 whitelist types
- `columns` valid range [1,4], invalid values clamp to 0 (caller uses DEFAULT_COLUMNS=2)
- `params` parsed as `Map<String, String>`

**P2.3 StoreUiConfigRepository**

- ETag + atomic cache + `minHostVersionCode` validation
- 4-level fallback: remote → cache → `assets/store-ui.json` → `defaultConfig()`
- Feature flag `STORE_REMOTE_UI` controls network requests

**P2.4 StoreSectionRenderer**

- Interface: `supports(type)`, `render(section, container, host)`
- `StoreRendererHost` callback interface: `hostContext()`, `currentModules()`, `installedModuleIds()`, `dispatchAction()`, `triggerRefresh()`, `switchCategory()`
- `StoreSectionRendererRegistry.dispatchRender()`: renders sections in order, skips duplicate IDs, skips unknown types, try-catch per section
- 9 renderers: `HeroBannerRenderer`, `SearchBarRenderer`, `NoticeRenderer`, `CategoryTabsRenderer`, `SectionTitleRenderer`, `ModuleListRenderer`, `ModuleGridRenderer`, `UpdateSectionRenderer`, `InstalledSectionRenderer`

**P2.5 Action whitelist**

- 6 allowed actions: `open_module`, `open_module_detail`, `open_installed_modules`, `refresh_catalog`, `switch_category`, `open_update_list`
- Required params: `open_module`/`open_module_detail` need `moduleId`, `switch_category` needs `categoryId`
- Parameter value blacklist regex: `[\"\\;`$|&<>\n\r]|\b(Intent|Activity|Class|Runtime|Process|exec|shell|javascript|intent)\b` (case-insensitive)
- Prevents arbitrary Intent URI, class name, Shell command, JavaScript injection

**P2.6 StoreViewModel + StorePageState**

- `StorePageState`: immutable state data class (catalog, uiConfig, modules, currentCategory, searchKeyword, isLoading, error, installedModuleIds)
- `StoreViewModel`: coordinates `StoreCatalogRepository` + `StoreUiConfigRepository`, thread-safe state via `AtomicReference`
- `ModuleStoreActivity` implements `StoreRendererHost`, delegates section rendering to `StoreSectionRendererRegistry`

### Testing

**Unit tests (63 tests, all passing)**

- `StoreCatalogTest` (20 tests): v1/v2 compatibility, missing fields, `enabled=false`, screenshot filtering, duplicate IDs, corrupted entries, `rescueCatalog`, `toJson` roundtrip
- `StoreUiConfigTest` (18 tests): default layout, section order, disabled sections, list/grid switching, unknown components, columns range, schemaVersion, params parsing, `toJson` roundtrip
- `StoreActionRouterTest` (25 tests): 6 whitelist actions, unknown action rejection, required params, parameter value blacklist (Intent/Activity/Runtime/exec/shell/javascript/semicolon/backtick/dollar/pipe/angle brackets)

**Real device testing (Xiaomi ares M2012K10C)**

- Module store opens successfully
- Hero Banner, 3-column stats, category tabs, subcategories, search history, module cards all display correctly
- Pull-to-refresh triggers `StorePageState updated: catalog=true uiConfig.pages=1`
- Module detail BottomSheet shows screenshots, description, changelog, permissions
- No FATAL EXCEPTION in logcat

### Feature flags

- `STORE_REMOTE_CATALOG`: controls remote catalog network requests
- `STORE_REMOTE_UI`: controls remote UI config network requests
- `STORE_SECTION_RENDERER`: controls section renderer dispatch

All three flags can be set to `false` in `app/build.gradle` to disable remote features without affecting compilation.

### Files modified

**New files:**
- `app/src/main/assets/catalog.json`
- `app/src/main/assets/store-ui.json`
- `app/src/main/java/com/gamecenter/app/modules/store/model/StoreCatalog.kt`
- `app/src/main/java/com/gamecenter/app/modules/store/model/StoreCategory.kt`
- `app/src/main/java/com/gamecenter/app/modules/store/model/StoreModule.kt`
- `app/src/main/java/com/gamecenter/app/modules/store/model/StoreHeroBanner.kt`
- `app/src/main/java/com/gamecenter/app/modules/store/model/StoreUiConfig.kt`
- `app/src/main/java/com/gamecenter/app/modules/store/StoreCatalogRepository.kt`
- `app/src/main/java/com/gamecenter/app/modules/store/StoreUiConfigRepository.kt`
- `app/src/main/java/com/gamecenter/app/modules/store/StoreSectionRenderer.kt`
- `app/src/main/java/com/gamecenter/app/modules/store/StoreActionRouter.kt`
- `app/src/main/java/com/gamecenter/app/modules/store/StorePageState.kt`
- `app/src/main/java/com/gamecenter/app/modules/store/StoreViewModel.kt`
- `app/src/test/java/com/gamecenter/app/modules/store/StoreCatalogTest.kt`
- `app/src/test/java/com/gamecenter/app/modules/store/StoreUiConfigTest.kt`
- `app/src/test/java/com/gamecenter/app/modules/store/StoreActionRouterTest.kt`

**Modified files:**
- `app/build.gradle`: 3 feature flags + testOptions.returnDefaultValues=true
- `app/src/main/java/com/gamecenter/app/modules/ModuleManifest.kt`: 10 new fields
- `app/src/main/java/com/gamecenter/app/modules/HeroBannerAdapter.kt`: server-provided banner support
- `app/src/main/java/com/gamecenter/app/modules/ModuleScreenshotAdapter.kt`: server-provided screenshots
- `app/src/main/java/com/gamecenter/app/modules/ModuleDetailBottomSheet.kt`: server-provided changelog/permissions
- `app/src/main/java/com/gamecenter/app/modules/ModuleStoreActivity.kt`: implements `StoreRendererHost`, integrates repositories
- `app/src/main/res/layout/activity_module_store.xml`: root container id for renderer
- `app/src/test/java/com/gamecenter/app/modules/ModuleDependencyTest.java`: adapt to 35-field ModuleManifest
- `.gitignore`: exclude Gradle 8.13+ cache directories

### Rollback methods

- **Full rollback**: `git revert` the 4 commits in reverse order
- **Disable remote features**: set 3 feature flags to `false` in `app/build.gradle`
- **Delete new files**: remove `app/src/main/java/com/gamecenter/app/modules/store/` directory and `catalog.json`/`store-ui.json` assets

### Remaining issues

1. **Duplicate downloaders**: `ModuleDownloader` in `app/modules/` and `core/modulestore/` still coexist; not deleted per user instruction
2. **Download concurrency**: no limit on simultaneous downloads; recommended max 2 concurrent downloads
3. **Real-time progress**: `ModuleDownloadManager.getDownloadProgress()` is dead code; UI does not show real-time download speed
4. **Catalog signature**: ✅ P3 已实现 Ed25519 目录签名验证（`CatalogSignatureVerifier`），当前处于兼容模式（`ENABLE_CATALOG_SIGNATURE=false`），后续需要配置真实公钥并启用
5. **Transactional install**: ✅ P3 已实现 `staging/current/last_good/quarantine` 事务安装，支持加载失败自动回滚
6. **Dynamic store APK**: not implemented; store UI still in main APK; deferred to P4
7. **⚠️ 模块签名证书不匹配（2026-07-20 ADB 真机测试发现，高优先级）**: `core/security/.../ModuleSignatureVerifier.kt` 的 `loadPinnedCertificate()` 加载 `res/raw/release_signer.cer` 与服务器模块 APK 签名证书不一致，导致 tools/ai/vpn 模块下载后 `Result.Failure("签名者证书不匹配")`，无法完成端到端安装。修复方向：①统一 debug/release 签名证书；②debug 构建放宽校验策略；③用主 APK 签名证书重新签名服务器模块 APK 后上传。详见 `docs/ADB_REAL_DEVICE_TEST_PLAN.md` §21.1.2
8. **搜索范围限制（2026-07-20 ADB 真机测试发现，中优先级）**: 当前模块商店搜索仅在当前选中的分类下生效，需评估是否扩展为全部分类搜索或提供搜索范围提示

### Next phase recommendation

**P4: Host UI module-declared**

- 底部导航读取已安装模块贡献
- 游戏大厅读取已安装游戏贡献
- 工具区读取已安装工具贡献
- 商店分类页读取目录元数据，而非硬编码列表
- 已安装模块页读取本地模块数据库

**目标**：
- 浏览器/工具/AI/VPN/游戏大厅可通过模块更新替换，无需修改主 APK


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
