<!-- flutter-store-doc-sync: 2026-07-22 -->
> **Flutter-first production sync:** The Flutter module-store UI and customizable host navigation are live in stable vc595; Android remains authoritative for catalog trust, download, install, rollback, and runtime lifecycle. Production completion: 100%. See `/docs/flutter-store/MIGRATION_STATUS.md`.

# AI Coding Rules for GameMatrixApp

This file is mandatory reading for any AI or automation agent working in this
repository. Read it before editing code.

## Prime Directive

Make changes only after understanding the project shape, then verify them on
the real build path. A local source change is not done until the APK that users
or the emulator receives has been rebuilt and tested.

## Required Context

Before non-trivial work, read:

- `docs/AI_CODING_STANDARDS.md`
- `docs/AI_CONTEXT.md`
- `docs/DONT_DO_THIS.md`
- `docs/flutter-store/MIGRATION_STATUS.md`（涉及模块商店、Catalog、Runtime 或 Flutter 时）
- relevant module docs under `docs/modules/` or `docs/module-docs/`

If these documents conflict, prefer the more specific and newer rule, then
record the decision in the final response.

## Non-Negotiable Rules

- Do not revert user or unrelated work. This repository often has a dirty
  worktree.
- Do not trust a successful Gradle task alone. Install and exercise the affected
  app flow when the change is user-visible.
- Do not treat dynamic feature APKs like normal in-process app code. Resource
  IDs, `R.styleable`, activities, themes, and preinstalled APK caching are
  common failure points.
- Do not add an Activity inside a dynamic module and start it directly unless
  the host manifest or a host proxy Activity explicitly supports it.
- Do not use Material/AppCompat XML widgets inside a dynamically loaded APK
  unless you have verified `R.styleable` compatibility on device. Prefer host
  UI, plain Android widgets, or a host-owned screen.
- Do not assume `:app:assembleDebug` contains the latest preinstalled module.
  Verify the APK asset under `assets/modules/` by size or SHA-256.
- Do not report success without checking logcat for `FATAL EXCEPTION` after the
  target flow runs.
- `RELEASE_NOTES.md` is the only user-facing update announcement source for
  GitHub Releases, `version.json`, and the in-app update prompt. Keep it short,
  plain-language, and limited to the current release. Never publish the full
  `CHANGELOG.md`, raw GitHub-style `@mentions`, source paths, build logs, or
  rollback commands as user-facing notes. Run
  `python tools/validate_release_notes.py RELEASE_NOTES.md --version-file version.properties`
  before a stable release.
- `README.md` is an App user page, not a developer report. Keep it focused on
  what the App does, installation, updates, permissions, privacy, and common
  questions. Do not add build commands, CI details, architecture notes, test
  matrices, internal paths, hashes, or release-operation logs.
- Stable user APKs must be ARM64-only and pass
  `python tools/validate_release_apk.py app/build/outputs/apk/release/app-release.apk`.
  Never publish Flutter debug sections or emulator ABIs in the universal APK.
  Keep `flutterNdkVersion` aligned with Flutter's required NDK version; do not
  enable legacy JNI packaging merely to hide an oversized APK.
- Do not echo secrets from local config, keystore, token, or password files.

## Standard Verification Commands

Debug build with stable versioning:

```powershell
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace
```

When a preinstalled dynamic module changed:

```powershell
.\gradlew.bat :module-store:feature:tools:<module>:assembleDebug :app:bundlePreinstalledModules -PautoBumpVersion=false --stacktrace
.\gradlew.bat :app:assembleDebug -PautoBumpVersion=false --stacktrace
```

When validating the Flutter-first module store, add `-PenableFlutterModuleStore=true -PautoUploadVps=false` to the relevant app tasks, then run `flutter analyze` and `flutter test` from `flutter_module/`. The flag defaults to false and must remain opt-in until the release gates in `docs/flutter-store/MIGRATION_STATUS.md` are complete.

Stable production APK verification:

```powershell
.\gradlew.bat :app:validateReleaseApk -PupdateChannel=stable -PenableFlutterModuleStore=true -Ptarget-platform=android-arm64 -PenableCatalogSignature=true -PcatalogSigningProfile=production -PautoBumpVersion=false -PautoUploadVps=false -PpublishGitHubRelease=false --stacktrace
```

Install and smoke test on a connected emulator:

```powershell
adb devices
adb -s <serial> install -r -d app\build\outputs\apk\debug\app-debug.apk
adb -s <serial> logcat -c
adb -s <serial> shell monkey -p com.gamecenter.app -c android.intent.category.LAUNCHER 1
```

After exercising the changed UI, inspect logs:

```powershell
adb -s <serial> logcat -d -t 2000 |
  Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime|Resources\$NotFoundException|InflateException|ClassNotFoundException|ActivityNotFoundException|Caused by:"
```

## Completion Criteria

A task is complete only when:

- source changes are scoped to the request,
- debug or release build relevant to the request succeeds,
- the built APK contains the intended bundled assets/modules,
- the affected emulator path is exercised,
- logcat is clean for the tested path,
- remaining limitations are explicitly reported.
