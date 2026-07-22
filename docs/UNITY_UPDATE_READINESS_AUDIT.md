<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# Unity Update Readiness Audit

Date: 2026-06-21

Scope:

- Main app: `D:\Developmment\GameMatrixApp`
- Early Unity project: `D:\unity\ZombieGateShooter`
- Existing Unity integration planning docs: `D:\unity\integration-poc\docs`

## Conclusion

The main app does not need a large source rewrite before the first Unity proof of concept. It already has a remote module list, module download, SHA-256 verification, local cache, and main APK update path.

However, Unity should not be treated as another ordinary `ModuleManifest` package. The current module system is designed around one installable artifact per module, while a Unity game in early development needs frequent updates to content, config, balance data, and sometimes runtime or native code. Those update layers need different safety rules.

Recommended direction:

1. Keep the main app stable and update it only when host capabilities, bridge APIs, permissions, ABI packaging, or Unity runtime embedding changes.
2. Add a separate Unity update manifest and content updater instead of overloading `modules.json`.
3. Let Unity content and config update independently through staged download, SHA-256 verification, atomic promotion, and rollback.
4. Add the main app entry point for `ZombieGateShooter` only after the first playable Unity build is exportable and the bridge contract is clear.

## Current Main App Capabilities

### Remote Module System

Evidence:

- `app/src/main/java/com/gamecenter/app/modules/ModuleManifest.kt`
- `app/src/main/java/com/gamecenter/app/modules/ModuleDownloader.kt`
- `app/src/main/java/com/gamecenter/app/modules/ModuleManager.kt`
- `app/src/main/assets/modules.json`

What works:

- The main app can read a module manifest from a remote URL.
- It can cache module metadata locally.
- It downloads module files to app-private storage.
- It verifies module package integrity with SHA-256.
- It can register bundled module metadata from `assets/modules.json`.

Limit:

- The manifest describes one module artifact with fields such as `fileName`, `sha256`, `downloadUrl`, `entryClass`, and `versionCode`.
- It does not model Unity-specific versions such as host bridge version, Unity runtime version, game logic version, content version, config version, rollout, kill switch, staged install, or last-good rollback.

### Dynamic Code Loading

Evidence:

- `app/src/main/java/com/gamecenter/app/modules/ModuleLoader.kt`

What works:

- The current loader uses `DexClassLoader`.
- It expects a Java/Kotlin module entry class implementing the app's module interface.

Limit:

- This is suitable for feature APK or Dex-style modules.
- It is not the right primary mechanism for Unity content updates.
- It should not be used as the main strategy for loading Unity native runtime, IL2CPP output, or asset bundles.

### Shell Entry

Evidence:

- `app/src/main/java/com/gamecenter/app/features/ModuleShellFragment.kt`
- `app/src/main/java/com/gamecenter/app/features/SmartModuleLoader.kt`

What works:

- The main app can show a shell page for modules.
- Existing shell routing knows the current top-level features: games hall, browser, tools, AI, and VPN.

Limit:

- There is no `ZombieGateShooter` entry yet.
- The smart loader has little value for Unity fallback today because built-in fragment creation currently returns no Unity screen.

### APK Update

Evidence:

- `core/update/src/main/java/com/gamecenter/app/update/UpdateInfo.java`
- `core/update/src/main/java/com/gamecenter/app/update/UpdateDownloader.java`

What works:

- The main app has an APK update path with multiple download sources and file size validation.

Limit:

- The APK update metadata currently uses MD5.
- Module packages already use SHA-256. Unity runtime or main APK updates should move toward SHA-256 as well.

### Saves

Evidence:

- `app/src/main/java/com/gamecenter/app/SaveManager.java`

What works:

- The current save manager stores JSON strings through `SharedPreferences`.

Limit:

- This is acceptable for small early-stage progress JSON.
- It is not suitable for large or frequently written Unity save data. Unity should use app-private files with throttled writes, and the main app should store only summaries or indexes.

### ABI Packaging

Evidence:

- `app/build.gradle`

What works:

- Release builds already target `arm64-v8a`, which matches the likely primary Unity Android target and reduces package size.

Limit:

- If Unity as a Library is embedded later, native library packaging, duplicate `.so` handling, keep rules, and asset packaging must be reviewed explicitly.

## Required Change Framework

### P0: Do Now Before Deep Integration

These are design and boundary-setting changes. They can be done before Unity gameplay is stable.

1. Create a Unity update contract.

   Suggested docs:

   - `docs/unity/UPDATE_MANIFEST.md`
   - `docs/unity/BRIDGE_CONTRACT.md`
   - `docs/unity/RELEASE_RUNBOOK.md`

2. Define a separate Unity update manifest.

   Do not add Unity content packs directly into the existing `modules.json` as ordinary ZIP game modules. Use a separate manifest such as:

   ```json
   {
     "gameId": "zombiegate",
     "manifestVersion": 1,
     "minHostVersionCode": 123,
     "bridgeVersion": "1.0",
     "unityRuntimeVersion": "unity-2022.3-android-arm64-r1",
     "gameLogicVersion": 1,
     "contentVersion": 1,
     "configVersion": 1,
     "rollout": {
       "percent": 10
     },
     "killSwitch": false,
     "files": [
       {
         "path": "content/catalog.json",
         "url": "https://example.com/unity/zombiegate/content/catalog.json",
         "size": 12345,
         "sha256": "..."
       }
     ]
   }
   ```

3. Keep the first integration mode simple.

   For the first playable stage, prefer one of these:

   - main app launches a separately installed Unity APK by package name;
   - main app opens a shell placeholder with download/update/start status;
   - main app links to an exported Unity build artifact for manual installation.

   This avoids locking the main app into Unity as a Library before the Unity project stabilizes.

4. Decide the minimum bridge API.

   Keep it small:

   - host app version and capability query;
   - launch parameters;
   - save/load path handoff;
   - remote config handoff;
   - analytics/error event handoff;
   - exit/result callback.

### P1: Add Unity Content Update Manager After First Playable Build

Add a dedicated manager rather than extending `ModuleDownloader` directly.

Suggested Android-side component:

- `core/unityupdate`
- or `app/src/main/java/com/gamecenter/app/unityupdate`

Responsibilities:

- fetch Unity update manifest;
- compare `runtimeVersion`, `gameLogicVersion`, `contentVersion`, and `configVersion` independently;
- download to staging directories;
- verify every file with SHA-256;
- promote only after all files pass verification;
- keep `current` and `last_good` pointers;
- roll back automatically when launch fails or a kill switch is received.

Suggested storage layout:

```text
filesDir/unity/zombiegate/
  manifests/
  staging/<contentVersion>/
  current -> version pointer in preferences or a small metadata file
  last_good -> version pointer in preferences or a small metadata file
  saves/
  logs/
```

Important behavior:

- Never delete the current working Unity content before the new content is fully downloaded and verified.
- Do not mark an update active until the first launch succeeds or a defined smoke check passes.
- Keep rollback possible without requiring a main app reinstall.

### P2: Embed Unity Later Only When Stable

Consider Unity as a Library only after these are stable:

- Android package name and signing flow;
- Unity version;
- target ABI;
- bridge API;
- save format;
- content packaging format;
- update server layout;
- crash and rollback policy.

When Unity as a Library is introduced, the main app must review:

- Gradle project layout;
- Unity generated Android library module;
- native `.so` packaging;
- ProGuard/R8 rules;
- asset compression and streaming assets;
- lifecycle forwarding;
- permission ownership;
- memory pressure handling;
- cold start cost;
- crash isolation.

## Main App Update Policy

Use this rule:

| Update Type | Should Require Main App Update | Preferred Path |
| --- | --- | --- |
| Host UI entry, bridge API, permissions, ABI, Unity runtime embedding | Yes | Main APK update |
| Unity native code or IL2CPP player changes | Usually yes | Main APK or separate Unity APK |
| Unity content, levels, textures, catalogs | No | Unity content manifest |
| Balance values, feature flags, difficulty, spawn rates | No | Remote config |
| Emergency disable | No | Kill switch in Unity manifest/config |
| Save format breaking change | Maybe | Bridge-gated migration |

## What Not To Do Now

- Do not force every Unity change through the main app release pipeline.
- Do not use the current `DexClassLoader` module loader as the Unity runtime update mechanism.
- Do not store large Unity saves in `SharedPreferences`.
- Do not delete current Unity content before the replacement is fully verified.
- Do not add a hard bottom-nav Unity entry until there is a launchable build and a fallback screen.
- Do not mix the Unity update manifest into the existing module manifest unless the fields and semantics are separated clearly.

## Recommended Next Steps

1. Keep the current main app code unchanged until the Unity project produces a first Android build artifact.
2. Add `docs/unity/UPDATE_MANIFEST.md` and `docs/unity/BRIDGE_CONTRACT.md` to lock the interface before implementation.
3. Build the first Unity artifact as a separately launchable APK first.
4. Add a main app `ZombieGate` shell entry only when the launch path is known.
5. Implement `UnityContentUpdateManager` after Unity has real content packs to update.
6. Convert APK update metadata from MD5 to SHA-256 before relying on large Unity runtime updates.

## Worktree Note

The main app worktree currently has many modified and untracked files. Avoid source edits until those changes are committed, stashed, or intentionally separated into a dedicated branch for Unity integration.


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)