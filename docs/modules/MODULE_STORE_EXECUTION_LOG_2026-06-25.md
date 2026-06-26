# Module Store Execution Log

Date: 2026-06-25
Scope: build new shell APK, publish stable/beta app updates, publish module-store catalog and module APKs to HK VPS.

## Completed

- Restored the missing design reference at `docs/superpowers/specs/2026-06-25-modular-shell-design.md`.
- Fixed preinstalled module extraction so bundled APKs are copied to `filesDir/modules`, matching `ModuleDownloader` and `ModuleLoader`.
- Rebuilt `app/src/main/assets/modules.json` as valid JSON.
- Published catalog version `20` with 33 module entries.
- Published remote module entries for:
  - `browser`
  - `tools`
  - `ai`
  - `tts_voice`
  - `vpn`
- Kept current host games as built-in catalog entries until their independent module APKs build cleanly.
- Added `tools/upload_to_vps.py` and wired Gradle upload to it.
- Corrected deployment routing:
  - app update files: `/var/www/update/app`
  - module-store files served by port 9001: `/var/www/modules`

## APK Output

- Local APK: `app/build/outputs/apk/release/app-release.apk`
- Version: `versionCode=493`, `versionName=1.4.1`, `channel=beta`
- APK SHA-256: `23AF8895B3C90F748B662FEC7D23B7912D5E1BE22A42F198011C4B3DE8F88358`
- APK size: `63875969` bytes
- Signing verification: passed with V2 signer.

## VPS Deployment

Published public endpoints:

- `https://hk-update.tcp0053.shop/app-beta.apk?v=493`
- `https://hk-update.tcp0053.shop/version-beta.json`
- `https://hk-update.tcp0053.shop/version.json?acceptBeta=true`
- `https://hk-update.tcp0053.shop/modules.json`
- `https://hk-update.tcp0053.shop/modules/feature_ai_v100.apk`
- `https://hk-update.tcp0053.shop/modules/feature_browser_v100.apk`
- `https://hk-update.tcp0053.shop/modules/feature_tools_v100.apk`
- `https://hk-update.tcp0053.shop/modules/feature_tts_voice_v101.apk`
- `https://hk-update.tcp0053.shop/modules/vpn-debug.apk`

Independent verification:

- `app-beta.apk?v=493`: public size and SHA-256 match local APK.
- `modules.json`: public size and SHA-256 match local catalog.
- `feature_tools_v100.apk`: public size and SHA-256 match local module (`951622` bytes).
- `vpn-debug.apk`: public size and SHA-256 match local module.
- `version-beta.json` and `version.json?acceptBeta=true`: update service dynamically adds `downloadUrl`, `fileSize`, and `md5`; core fields verify as `versionCode=493`, `channel=beta`, `apkName=app-beta.apk`, `downloadUrl=https://hk-update.tcp0053.shop/app-beta.apk?v=493`.

## VPS Runtime Adjustment

Cloudflare had a cached copy of bare `app-beta.apk`. The source server already served the new file, but the public bare URL remained cached. To make the update immediately fetch the current APK, `/var/www/update/update_server.py` was backed up and patched so generated `downloadUrl` values include `?v={versionCode}`. The service `gamecenter-update.service` was restarted and verified active.

## Stable Channel Correction

After beta deployment, ordinary users still received the old stable package because the default update path checks `version-release.json` and downloads `app-release.apk`. The public stable JSON had been reporting `466`, but `app-release.apk?v=466` was still an APK with manifest `versionCode=465`.

Corrective action:

- Rebuilt release APK with `-PupdateChannel=stable`.
- Regenerated `app/build/outputs/apk/release/version.json` for the stable channel.
- Uploaded the stable package with `--channel release`, which updates:
  - `/var/www/update/app/app-release.apk`
  - `/var/www/update/app/version-release.json`
  - `/var/www/update/app/version.json`
- Verified public default update endpoints now return `versionCode=493`, `channel=stable`, `apkName=app-release.apk`, and `downloadUrl=https://hk-update.tcp0053.shop/app-release.apk?v=493`.
- Downloaded `https://hk-update.tcp0053.shop/app-release.apk?v=493` from the public endpoint and verified its APK manifest is `versionCode=493`, `versionName=1.4.1`.

Stable APK verification:

- Local/public APK size: `63875962` bytes.
- SHA-256: `00709A3FC1E8282BF299CB243109B62731F9CC6DBF2E85B9F25BC53C35A7082C`.
- Signing verification: passed with V2 signer.

## Remaining Blockers

- Release lint still needs a separate fix. The APK was built with `-PskipReleaseLint=true` because `lintVitalReportRelease` fails on an AGP path-variable serialization issue.
- Split game module APKs are not publishable yet:
  - `hall`: missing Compose dependencies.
  - `game2048`: calls missing `GameUsageStore.recordScore(String, int)`.
- Full-worktree `git diff --check` is blocked by unrelated existing whitespace changes in many files. The files touched in this execution pass `git diff --check`.

## Commands Used

```powershell
.\gradlew.bat :module-store:feature:tools:vpn:assembleDebug -PautoUploadVps=false -PautoBumpVersion=false
.\gradlew.bat :app:assembleRelease -PskipReleaseLint=true -PautoUploadVps=false -PautoBumpVersion=false
.\gradlew.bat :app:generateVersionJson -PskipReleaseLint=true -PautoUploadVps=false -PautoBumpVersion=false
python tools\upload_to_vps.py --apk app\build\outputs\apk\release\app-release.apk --version app\build\outputs\apk\release\version.json --channel beta --modules-json app\src\main\assets\modules.json --module-dir app\src\main\assets\modules --module-apk module-store\feature\tools\vpn\build\outputs\apk\debug\vpn-debug.apk
.\gradlew.bat :app:assembleRelease :app:generateVersionJson -PupdateChannel=stable -PskipReleaseLint=true -PautoUploadVps=false -PautoBumpVersion=false
.\gradlew.bat :app:generateVersionJson -PupdateChannel=stable -PskipReleaseLint=true -PautoUploadVps=false -PautoBumpVersion=false
python tools\upload_to_vps.py --apk app\build\outputs\apk\release\app-release.apk --version app\build\outputs\apk\release\version.json --channel release --modules-json app\src\main\assets\modules.json --module-dir app\src\main\assets\modules --module-apk module-store\feature\tools\vpn\build\outputs\apk\debug\vpn-debug.apk
```


---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
