<!-- flutter-store-doc-sync: 2026-07-22; historical -->
> Historical snapshot: preserved for context and not current release truth. Flutter-first module store production completion is 100%; current evidence: /docs/flutter-store/MIGRATION_STATUS.md.

# Modular Shell APK Design

Date: 2026-06-25
Status: execution baseline restored

## Goal

GameMatrixApp should move toward a stable shell APK where feature content is delivered by the module store and can be updated from the VPS without rebuilding the host for every feature change.

The design target is:

- Module store metadata and APK files are published independently from the shell APK.
- The shell APK can preload selected module APKs for first-run availability.
- Installed modules can be replaced by newer VPS versions after SHA-256 verification.
- Host releases are reserved for runtime, ABI, permission, signing, bridge, or Android platform changes.
- Feature, game, AI, browser, tools, and voice behavior should be updateable through the module channel where technically possible.

## Boundary

Android cannot rewrite the already installed host APK in place. The shell can only:

- download and load external module APK or DEX payloads;
- switch routing from built-in host classes to downloaded module entries;
- install a new host APK through the normal app-update flow when shell/runtime contracts change.

Therefore "all app features can update through the module store" means feature behavior moves behind module contracts. It does not mean changing AndroidManifest-declared permissions, native ABI, target SDK, signing identity, or other host-level contracts without a new APK.

## Runtime Contract

Each module catalog entry must include:

- `id`, `name`, `description`
- `versionCode`, `versionName`
- `entryClass`
- `fileName`, `fileSize`, `sha256`
- `downloadUrl`, optional fallback URLs
- `type`, `storeCategory`
- `minAppVersion` and `minAppVersionCode`
- `builtIn`, `required`, `isBaseFramework`
- game routing fields when `type == "game"`: `gameId`, `gameCategory`, `gameDesc`, `activityClass`

External modules are stored under `filesDir/modules/{fileName}`. Preinstalled module APKs from `assets/modules/` must be extracted to the same directory so the downloader and loader share one source of truth.

## Catalog Deployment

The VPS update root should publish:

- `version.json`
- `version-beta.json` or `version-release.json`
- `app-beta.apk` or `app-release.apk`
- `modules.json`
- `modules/*.apk`

The shell should load `BuildConfig.MODULES_URL`, cache successful catalog responses, and fall back to bundled `assets/modules.json` when the network is unavailable.

## Safety Rules

- Non-built-in modules require a non-empty SHA-256 and file-size metadata.
- Module files must be verified before load.
- Module APKs should be copied or downloaded atomically, using a temporary file then rename.
- A remote catalog entry must not point at a file that was not uploaded and verified.
- Built-in game entries may keep `fileName`, `downloadUrl`, and `sha256` empty until the game has a real external module APK.

## Current Execution Baseline

As of this restored spec:

- Browser, tools, AI, TTS voice, and VPN are the current remote APK module candidates.
- Built-in games remain catalog-visible with host `activityClass` routing.
- The game module split is not fully executable until module build errors are resolved.
- Host APK deployment and module catalog deployment should be performed together so a new shell can immediately see the current remote module set.


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)