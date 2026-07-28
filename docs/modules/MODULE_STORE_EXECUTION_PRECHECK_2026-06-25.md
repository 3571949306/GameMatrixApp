<!-- flutter-store-doc-sync: 2026-07-22; historical -->
> Historical snapshot: preserved for context and not current release truth. Flutter-first module store production completion is 100%; current facts: /docs/CURRENT_STATE.md.

# Module Store Execution Precheck

Date: 2026-06-25

Scope: check whether the modular Shell APK / module store redesign can start from the current workspace.

## Summary

Execution is possible, but not safe to start directly from feature migration. There are two immediate blockers and several missing implementation pieces.

Start with cleanup and contract work, not `ModuleStoreActivity`.

## Immediate Blockers

### 1. Referenced superpowers spec is missing on disk

Expected file:

```text
D:\Developmment\GameMatrixApp\docs\superpowers\specs\2026-06-25-modular-shell-design.md
```

Current result:

- `docs\superpowers\specs\` exists.
- The directory is empty.
- `rg --files docs` does not find `superpowers` or `modular-shell-design`.
- The current committed/tracked file list does not include that spec.

Impact:

- The detailed Shell APK plan cannot be treated as the stable source of truth unless it is restored.
- The current executable design source should temporarily be:

```text
D:\Developmment\GameMatrixApp\docs\modules\MODULE_STORE_REDESIGN_PLAN.md
```

Required action:

- Restore the superpowers spec if it should remain the master plan, or make `MODULE_STORE_REDESIGN_PLAN.md` the master execution plan.

### 2. `modules.json` is not valid JSON

File:

```text
D:\Developmment\GameMatrixApp\app\src\main\assets\modules.json
```

Observed:

- `ConvertFrom-Json` fails.
- Several `description` strings appear to be missing closing quotes after mojibake text, for example near lines 7 and 33.

Impact:

- The bundled rescue catalog cannot be trusted.
- Any code path that parses `assets/modules.json` can fail.
- Catalog migration work should not proceed until this file is repaired or replaced.

Required action:

- Regenerate `modules.json` from a clean source.
- Validate it with a JSON parser before using it as seed catalog.
- Prefer ASCII/escaped text or verified UTF-8 to avoid another mojibake break.

## High-Risk Workspace State

The worktree is very dirty before implementation starts:

- Many app resources and adapters are modified.
- `settings.gradle`, `version.properties`, and `app/build.gradle` are modified.
- A large number of files under `module-store/feature/games/games/` are deleted.
- New app-side game config/model files are untracked.

Impact:

- Starting a large module-store rewrite now risks mixing unrelated UI/game changes with shell architecture work.
- It will be hard to review, revert, or bisect.

Required action:

- Either commit/stash the current unrelated work, or create a dedicated branch/worktree and explicitly keep these changes out of the shell rewrite.

## Existing Pieces That Can Be Reused

These are present and useful:

```text
core/common
core/module-host
core/moduleloader
core/modulestore
core/security
app/src/main/java/com/gamecenter/app/modules
app/src/main/kotlin/com/gamecenter/app/modular
```

Specific reusable code:

- `core/common`: existing `ModuleInterface` and `FeatureModule`.
- `core/module-host`: `ModuleManifest`, `ModuleLoader`, `ModuleClassLoaderPool`.
- `core/moduleloader`: `ModuleLoaderV2`, `DexCacheManager`, tests.
- `core/modulestore`: `ModuleInstaller`, `ModuleDownloadManager`, `ModuleVersionChecker`, `ModuleUninstaller`.
- `app/modules`: current `ModuleManager`, `ModuleDownloader`, `ModuleLoader`, `ModuleStoreActivity`.
- `app/modular`: existing Room-backed `ModuleDatabase`, `ModuleDao`, `ModuleEntity`, `ModuleCacheManager`.

## Missing Files / Components From The Shell Plan

These do not exist yet and need to be created or mapped to existing equivalents:

```text
core/common/src/main/java/com/gamecenter/app/api/ModuleEntryPoint.kt
core/common/src/main/java/com/gamecenter/app/api/ModuleMetadata.kt
core/common/src/main/java/com/gamecenter/app/api/IntentResult.kt
core/common/src/main/java/com/gamecenter/app/api/ModuleCategory.kt
core/module-host/src/main/.../ModuleRegistry.kt
core/module-host/src/main/.../IntentRouter.kt
core/module-host/src/main/.../ResourceBridge.kt
core/modulestore/src/main/.../ModuleCacheManager.kt
core/modulestore/src/main/.../ModuleUpdateChecker.kt
```

Notes:

- Existing module entry classes currently implement `ModuleInterface` / `FeatureModule`, not the proposed `ModuleEntryPoint` contract.
- If the new `ModuleCacheManager` is placed in `core/modulestore`, that module currently lacks Room dependencies. Either add Room support there or reuse/move the app-side `app/modular` database carefully.

## Module Project Availability

The Gradle-included module directories exist and have `build.gradle`:

- `module-store/feature/tools/vpn`
- `module-store/feature/tools/browser`
- `module-store/feature/tools/tools`
- `module-store/feature/tools/ai`
- `module-store/feature/games/games/game2048`
- `module-store/feature/games/games/hall`
- `module-store/feature/games/games/chinesechess`
- `module-store/feature/games/games/klotski`
- `module-store/feature/games/games/tts`

This is enough to start API/loader work, but not enough for final Shell APK migration.

## Seed APK / Asset Gaps

Current `app/src/main/assets/modules/` contains only:

- `feature_ai_v100.apk`
- `feature_browser_v100.apk`
- `feature_tools_v100.apk`
- `feature_tts_voice_v101.apk`

Missing for the referenced seed strategy:

- `feature_games_hall_v100.apk`
- `feature_game2048_v100.apk`
- `feature_chinesechess_v201.apk`
- `feature_klotski_v200.apk`
- any agreed fifth game seed, such as `blackjack`
- `feature_vpn_v100_v2.apk` if VPN should be a seed module

Also, `app/build.gradle` currently bundles only:

- browser
- tools
- ai
- tts

It does not bundle the proposed five game seed modules.

## Server / Publish Script Gaps

Missing paths:

```text
D:\Developmment\GameMatrixApp\deploy
D:\Developmment\GameMatrixApp\deploy\modules.json
D:\Developmment\GameMatrixApp\tools
D:\Developmment\GameMatrixApp\tools\upload_modules.py
D:\Developmment\GameMatrixApp\tools\build_and_publish_all.py
D:\Developmment\GameMatrixApp\tools\upload_to_vps.py
```

Existing `app/build.gradle` has app release upload tasks, but not the module publishing workflow described by the Shell spec.

Required action:

- Decide whether module publishing should live under a new `tools/` directory, existing Chinese `工具/`, Gradle tasks, or another deployment folder.
- Create one canonical generated catalog location.

## Technical Execution Risks

### Activity routing

The current code already contains the correct warning in `ModuleStoreActivity`: dynamic modules loaded by `DexClassLoader` cannot directly start Activities that are not declared in the host APK manifest.

Use one of these:

- `FeatureModule.createFragment()` hosted by shell activity/fragment.
- A host `DynamicGameActivity` / proxy activity.
- A real plugin framework that supports component proxying.

Do not implement the superpowers spec literally if it assumes Android will resolve Activity declarations from an uninstalled module APK.

### Duplicate module systems

There are multiple overlapping module layers:

- `app/src/main/java/com/gamecenter/app/modules`
- `app/src/main/kotlin/com/gamecenter/app/modular`
- `core/module-host`
- `core/moduleloader`
- `core/modulestore`

The rewrite should first define which layer becomes canonical. Otherwise the implementation will duplicate state, loaders, and verification paths.

## Recommended Start Order

1. Restore or replace the missing superpowers spec.
2. Repair/regenerate `app/src/main/assets/modules.json` and validate it.
3. Freeze the canonical API in `core/common`.
4. Choose the canonical runtime ownership:
   - loader: `core/module-host` or `core/moduleloader`;
   - store state: `core/modulestore` or app-side `app/modular`.
5. Create `ModuleRegistry`, `IntentRouter`, and `ResourceBridge`.
6. Add transaction storage: temp download, staging, current, last_good.
7. Update `app/build.gradle` seed bundling only after seed modules are chosen.
8. Migrate one small module first, preferably Browser or Tools.
9. Add tests for JSON validation, download verification, install promotion, load failure, rollback, and uninstall.

## Current Go / No-Go

No-go for direct feature migration.

Go for architecture groundwork after the two blockers are resolved:

- missing master spec restored or replaced;
- invalid `modules.json` repaired.



---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)