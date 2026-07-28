<!-- flutter-store release-evidence: 2026-07-22 -->
> **Flutter-first 发布证据快照：** 下文任何关于 vc595 / 已签名 Catalog V8 的陈述均记录 2026-07-22 的发布证据。它不是当前工作树或生产状态的声明；实时事实请查阅项目根目录的 `docs/CURRENT_STATE.md` 与当前实现。

# GameCenterApp 发布指南

## Flutter-first 商店发布附加门禁（2026-07-22）

源码默认开关保留回退能力；vc595 Flutter 商店、Catalog 和多 Runtime 结果是 2026-07-22 的发布证据。后续发布必须重新执行对应的 Flutter、Android、APK、Catalog、签名、回滚和真机门禁；发布判断以当前构建参数、产物和 [`CURRENT_STATE.md`](CURRENT_STATE.md) 为准。

> **文档版本**: v1.2.0
> **最后更新**: 2026-07-23
> **维护者**: GameCenterApp 开发团队

---

## 目录

1. [发布流程概览](#发布流程概览)
2. [前置条件](#前置条件)
3. [版本管理](#版本管理)
4. [构建流程](#构建流程)
5. [签名配置](#签名配置)
6. [上传部署](#上传部署)
7. [模块商店更新](#模块商店更新)
8. [GitHub Actions CI/CD](#github-actions-cicd)
9. [测试检查清单](#测试检查清单)
10. [回滚计划](#回滚计划)
11. [常见问题](#常见问题)

---

## 发布流程概览

GameCenterApp 使用 **双版本分发策略**：

| 通道 | 版本类型 | 分发目标 | 上传位置 |
|------|---------|---------|---------|
| **Beta** | 测试版 | 开发者接受测试版的用户 | VPS 服务器 |
| **Stable** | 正式版 | 所有用户 | VPS 服务器 + GitHub Releases |

### 发布流程图

```
┌─────────────────┐
│ 更新版本号      │ (version.properties)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 更新 CHANGELOG.md │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 执行测试        │ (单元测试 + 真机测试)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 构建 Release APK │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 上传到 VPS      │ (upload_to_vps.py)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ (可选) 上传到 GitHub Releases │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 更新 modules.json (模块商店) │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 通知用户更新    │
└─────────────────┘
```

---

## 前置条件

### 1. Java 环境

构建需要 **Java 17+**：

```bash
# 检查 Java 版本
java -version

# 输出应该类似：
# openjdk version "17.0.10" 2024-01-16
# OpenJDK Runtime Environment (build 17.0.10+7)
# OpenJDK 64-Bit Server VM (build 17.0.10+7, mixed mode)
```

如果未安装 Java 17+，请下载并安装：
- **推荐**: Eclipse Temurin (原 AdoptOpenJDK) - https://adoptium.net/
- **备选**: Oracle JDK 17 - https://www.oracle.com/java/technologies/downloads/

设置 `JAVA_HOME` 环境变量。

**Windows (PowerShell)**:
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot"
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $env:JAVA_HOME, [System.EnvironmentVariableTarget]::User)
```

**macOS/Linux (Bash/Zsh)**:
```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk' >> ~/.bashrc
```

### 2. 签名密钥

Release APK 需要签名密钥 (`gamecenter.keystore`)：

- **文件位置**: 项目根目录 `gamecenter.keystore`
- **备份位置**: 安全位置（不要提交到 Git）
- **密钥库配置**: `keystore.properties`（不要提交到 Git）

如果缺少签名密钥，请联系项目负责人获取。

### 3. 服务器配置

构建需要配置服务器地址（`local.properties`）：

```properties
# local.properties
server.url=https://your-server.example.com
# 2026-06-19: server.url.fallback 已废弃（美国 VPS 已下线），保留空值向后兼容
# server.url.fallback=
relay.url=https://your-server.example.com/api/ddz-relay
ws.url=wss://your-server.example.com/ws
feedback.url=https://your-server.example.com/api/feedback
```

---

## 版本管理

GameCenterApp 使用 **双版本号系统**：

| 版本号 | 用途 | 示例 | 文件位置 |
|---------|------|------|---------|
| **versionCode** | 内部版本号（整数，递增） | `599` | `version.properties` |
| **versionName** | 用户展示版本号（语义化版本） | `1.4.1` | `version.properties` |

> **版本规则**：`version.properties` 是唯一版本事实源。`versionCode` 表示当前工作版本；`lastStableVersion*` 表示最近稳定发布版本。构建、上传和公网回读均通过后，才可将某个工作版本称为已发布。

### version.properties 格式

```properties
# 示例；发布前请以 version.properties 的实际值为准
versionCode=<当前工作版本>
versionName=<当前展示版本>
lastStableVersionCode=<最近稳定发布版本>
lastStableVersionName=<最近稳定展示版本>
betaNoticeVersionGap=<稳定版提示阈值>
```

### 更新版本号

**Beta 版本**（默认）：

```bash
# 自动递增 versionCode（不改变 versionName）
.\gradlew.bat :app:bumpVersion
```

**Stable 版本**：

```powershell
# 1. 手动更新 version.properties
#    - versionName=1.4.2（例如）
#    - versionCode=600（例如）
#    - lastStableVersionCode=567（上一个正式版 versionCode）
#    - lastStableVersionName=1.4.1（上一个正式版 versionName）
# 2. 先构建并校验 ARM64 正式 APK
.\gradlew.bat :app:validateReleaseApk -PupdateChannel=stable -PenableFlutterModuleStore=true -Ptarget-platform=android-arm64 -PenableCatalogSignature=true -PcatalogSigningProfile=production -PautoBumpVersion=false -PautoUploadVps=false -PpublishGitHubRelease=false
# 3. 确认校验通过后再上传
.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable -PenableFlutterModuleStore=true -Ptarget-platform=android-arm64 -PenableCatalogSignature=true -PcatalogSigningProfile=production
```

---

## 构建流程

### 1. Debug 构建（本地测试）

```bash
# 构建 Debug APK（未签名，可调试）
.\gradlew.bat :app:assembleDebug

# 输出位置
# app/build/outputs/apk/debug/app-debug.apk
```

### 2. Release 构建（正式发布）

```powershell
# 构建并校验用户发布用 ARM64 APK（签名、混淆、资源收缩、符号剥离）
.\gradlew.bat :app:validateReleaseApk -PupdateChannel=stable -PenableFlutterModuleStore=true -Ptarget-platform=android-arm64 -PenableCatalogSignature=true -PcatalogSigningProfile=production -PautoBumpVersion=false -PautoUploadVps=false -PpublishGitHubRelease=false

# 输出位置
# app/build/outputs/apk/release/app-release.apk
```

该门禁会检查 APK 体积、ABI 集合及 Flutter 原生库调试符号。任何一项不符合要求都不得上传。

### 3. AAB 构建（Google Play）

```bash
# 构建 Release AAB（Android App Bundle）
.\gradlew.bat :app:bundleRelease

# 输出位置
# app/build/outputs/bundle/release/app-release.aab
```

### 4. 构建并上传到 VPS（Beta 通道）

```bash
# 构建、生成 version.json、上传到 VPS（Beta 通道）
.\gradlew.bat :app:buildAndUploadDebugToVps

# 自动执行：
# 1. assembleRelease
# 2. generateVersionJson
# 3. uploadReleaseArtifactsToVps
# 4. bumpVersion（如果 autoBumpVersion=true）
```

### 5. 构建并上传到 VPS + GitHub Releases（Stable 通道）

```bash
# 构建、生成 version.json、上传到 VPS 和 GitHub Releases（Stable 通道）
.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable

# 自动执行：
# 1. assembleRelease
# 2. generateVersionJson
# 3. uploadReleaseArtifactsToVps
# 4. uploadApkToGitHubRelease
# 5. bumpVersion（如果 autoBumpVersion=true）
```

---

## 签名配置

### 1. 创建 keystore.properties

在项目根目录创建 `keystore.properties`（*不要提交到 Git*）：

```properties
# keystore.properties
STORE_FILE=../gamecenter.keystore
STORE_PASSWORD=your_store_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

### 2. 配置 app/build.gradle

签名配置已在 `app/build.gradle` 中配置：

```groovy
android {
    signingConfigs {
        release {
            def keystorePropsFile = rootProject.file("keystore.properties")
            if (keystorePropsFile.exists()) {
                def props = new Properties()
                props.load(new FileInputStream(keystorePropsFile))
                storeFile rootProject.file(props['STORE_FILE'])
                storePassword props['STORE_PASSWORD']
                keyAlias props['KEY_ALIAS']
                keyPassword props['KEY_PASSWORD']
                // 启用 v1 和 v2 签名方案
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'

            // 只有当 keystore 存在时才应用签名配置
            if (signingConfigs.release.storeFile != null) {
                signingConfig signingConfigs.release
            }
        }
    }
}
```

### 3. 验证签名

```bash
# 验证 APK 签名
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk

# 输出应该包含：
# jar is unsigned. (signatures missing or not parsable)
# 或
# jar verified.
```

---

## 上传部署

### 1. 上传到 VPS 服务器

使用 `tools/upload_to_vps.py` 脚本（配置文件位于 `local_private/服务器部署/upload_config_hk.json`）：

```bash
# 上传 Beta 版本到 VPS
python tools/upload_to_vps.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --version app/build/outputs/apk/release/version.json \
  --channel beta

# 上传 Stable 版本到 VPS
python tools/upload_to_vps.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --version app/build/outputs/apk/release/version.json \
  --channel stable

# 仅发布模块商店资源（不重传 APK）：--apk/--version 可省略
python tools/upload_to_vps.py \
  --modules-json app/src/main/assets/modules.json \
  --store-ui app/src/main/assets/store-ui.json \
  --module-dir app/src/main/assets/modules \
  --channel release
```

> `app/build.gradle` 的 `uploadReleaseArtifactsToVps` 只发布已校验的主应用 APK 和更新元数据。模块包、Catalog 与签名必须用 `tools/deploy_production_catalog.py` 作为一个可回读验证的独立步骤发布，不能混在主应用上传中。

### 2. 上传到 GitHub Releases

**仅适用于 Stable 通道**。

`RELEASE_NOTES.md` 是唯一允许发布给用户的公告正文。它只描述本次版本给用户带来的变化；完整开发记录继续写入 `CHANGELOG.md`，不得整份复制到 Release。

发布前先校验公告：

```bash
python tools/validate_release_notes.py RELEASE_NOTES.md \
  --version-file version.properties \
  --tag v<versionName>-vc<versionCode>
```

校验会阻止过长公告、超过 8 条的要点、裸写的 `@用户名`、版本号不匹配，以及源码文件名、构建日志、哈希和回滚命令等开发者信息。

使用下面的任务发布。它会创建或更新精确的 `v<versionName>-vc<versionCode>` 标签，
仅上传 `app-release.apk`，并让 GitHub 回传的文件名、大小和 SHA-256 与本地 APK 一致后才报告成功。运行环境需要已有 `GITHUB_TOKEN` 或 `GH_TOKEN`；不要把令牌写入命令、文档或日志。

```powershell
.\gradlew.bat :app:uploadApkToGitHubRelease `
  -PupdateChannel=stable `
  -PenableFlutterModuleStore=true `
  -Ptarget-platform=android-arm64 `
  -PenableCatalogSignature=true `
  -PcatalogSigningProfile=production `
  -PautoBumpVersion=false `
  -PautoUploadVps=false
```

主更新通道为香港 VPS，GitHub Releases 为备用源。两处均发布并完成公网回读后，才算稳定版完成。

### 3. 上传模块 APK 到 VPS

```bash
# 上传模块 APK 到 VPS
scp 模块商店/功能模块/游戏/games/build/outputs/apk/release/*.apk user@your-server:/var/www/modules/

# 更新 modules.json
scp 模块商店/modules.json user@your-server:/var/www/update/modules/modules.json
```

---

## 模块商店更新

### 1. 更新 modules.json

在 `模块商店/modules.json` 中添加新模块或更新现有模块：

```json
{
  "version": 11,
  "modules": [
    {
      "id": "games_hall",
      "name": "游戏大厅",
      "description": "聚合内置和已下载游戏的游戏大厅模块。",
      "versionName": "1.0.0",
      "versionCode": 100,
      "entryClass": "com.gamecenter.app.features.BuiltInGamesHallModuleEntryPoint",
      "fileName": "feature_games_hall_v100.apk",
      "fileSize": 888226,
      "sha256": "dd05bb25c737893969826a98c1495251cd6d363f31c6021a6fe9d0ddb9a4d900",
      "downloadUrl": "https://your-server.example.com/modules/feature_games_hall_v100.apk",
      "fallbackUrl": "",
      "githubUrl": "",
      "iconUrl": "",
      "category": "game",
      "minAppVersion": 288,
      "type": "nav",
      "activityClass": "",
      "gameId": "",
      "gameCategory": "",
      "gameDesc": "",
      "builtIn": true,
      "storeCategory": "game",
      "isBaseFramework": true,
      "builtInVersionCode": 100
    }
  ]
}
```

### 2. 计算模块 APK 的 SHA-256

```bash
# 计算 SHA-256 哈希
sha256sum 模块商店/功能模块/游戏/games/build/outputs/apk/release/feature_games_hall_v100.apk

# 输出示例：
# dd05bb25c737893969826a98c1495251cd6d363f31c6021a6fe9d0ddb9a4d900  feature_games_hall_v100.apk
```

### 3. 上传 modules.json 和模块 APK

```bash
# 上传 modules.json
scp 模块商店/modules.json user@your-server:/var/www/update/modules/modules.json

# 上传模块 APK
scp 模块商店/功能模块/游戏/games/build/outputs/apk/release/*.apk user@your-server:/var/www/modules/
```

---

## GitHub Actions CI/CD

> **2026-07-06 复核**：循环 23 上线 `.github/workflows/android_ci.yml` + `.github/dependabot.yml`，CI/CD 流程已就位。

### 1. CI Workflow 文件

| 文件 | 用途 | 触发 |
|------|------|------|
| `.github/workflows/android_ci.yml` | 主 CI（lint + test + debug build + gitleaks） | push / PR |
| `.github/workflows/cloud-build.yml` | 云编译专用（debug + release artifact 上传） | push / PR |
| `.github/dependabot.yml` | 依赖周扫描（Gradle + GitHub Actions） | 每周自动 |
| `.gitleaks.toml` | secret 扫描配置 | 由 CI 调用 |

### 2. CI Job 说明

`android_ci.yml` 包含 3 个 job：

| Job | 内容 | 触发 |
|-----|------|------|
| `lint-and-test` | detekt + ktlint + 单元测试 + JaCoCo 覆盖率 | push / PR |
| `build` | `:app:assembleDebug` + `:app:assembleRelease` (unsigned) | push / PR |
| `gitleaks` | secret 扫描（基于 `.gitleaks.toml`） | push / PR |

### 3. Artifact 保留策略

- `app-debug` APK：保留 7 天
- `coverage-report`：保留 14 天
- release APK：未上传（CI 不签名，签名流程见 [签名配置](#签名配置)）

### 4. 触发云编译

任一即可：

```bash
# 1. 推到 main 分支（自动触发）
git push origin main

# 2. 在 GitHub 网页手动触发
#    → Actions 页面 → "CI" workflow → "Run workflow"

# 3. 提 PR
gh pr create --base main --head your-branch --title "feat: ..."
```

### 5. 下载编译产物

```bash
# 列出最近 build
gh run list --workflow=android_ci.yml --limit=5

# 下载最新 debug APK
gh run download --name app-debug
```

### 6. Dependabot 配置

`.github/dependabot.yml` 已配置：

- Gradle 依赖：每周扫描，自动开 PR 升级
- GitHub Actions：每周扫描，自动开 PR 升级
- 当前状态：0 open alerts（7 个历史 alerts 已在循环 24 Netty 4.1.134 → 4.1.135 升级后 dismissed）

> 详细 CI/CD 配置与 VPS 协同流程见 [`CLOUD-BUILD.md`](../CLOUD-BUILD.md)。

---

## 测试检查清单

### 1. 单元测试

```bash
# 运行所有单元测试
.\gradlew.bat test

# 运行特定模块的单元测试
.\gradlew.bat :core:common:test
.\gradlew.bat :core:moduleloader:test
.\gradlew.bat :core:modulestore:test
```

### 2. 集成测试

```bash
# 运行集成测试（需要真机或模拟器）
.\gradlew.bat connectedAndroidTest
```

### 3. 手动测试检查清单
- [ ] 应用启动正常
- [ ] 模块商店加载正常
- [ ] 模块下载、安装、卸载正常
- [ ] 斗地主、五子棋等内置游戏运行正常
- [ ] 联机功能（WebSocket）正常
- [ ] VPN 功能正常（如果适用）
- [ ] AI 功能正常（如果适用）
- [ ] 热更新功能正常
- [ ] APK 体积 ≤ 15MB（框架 APK）
- [ ] 无崩溃、ANR、内存泄漏

---

## 回滚计划

### 1. 回滚到上一个版本

如果用户报告严重 Bug，可以快速回滚：

```bash
# 1. 恢复上一个版本的 APK 和 version.json
#    （在 VPS 服务器上）
cp /var/www/update/backup/app-release-v1.4.0.apk /var/www/update/app-release.apk
cp /var/www/update/backup/version-v1.4.0.json /var/www/update/version.json

# 2. 重启 VPS 服务器（如果需要）
sudo systemctl restart nginx
```

### 2. 紧急修复版本
```bash
# 1. 创建紧急修复分支
git checkout -b hotfix/v1.4.2

# 2. 修复 Bug
#    （修改代码）

# 3. 更新版本号
#    （修改 version.properties：versionCode=568, versionName=1.4.2）
# 4. 构建并上传
.\gradlew.bat :app:buildAndUploadDebugToVps

# 5. 合并到 main 分支
git checkout main
git merge hotfix/v1.4.2
git push origin main
```

---

## 常见问题

### Q1: 构建失败，提示 "JAVA_HOME is not set"

**解决方案**：
1. 安装 Java 17+（参考 [前置条件](#前置条件)）
2. 设置 `JAVA_HOME` 环境变量
3. 重新启动 PowerShell/终端

### Q2: 上传到 VPS 失败，提示 "Connection refused"

**解决方案**：
1. 检查 VPS 服务器是否在线
2. 检查 SSH 密钥是否配置正确
3. 检查 VPS 服务器地址是否正确（在 `local.properties` 中配置）

### Q3: 模块下载失败，提示 "SHA-256 verification failed"

**解决方案**：
1. 重新计算模块 APK 的 SHA-256 哈希
2. 更新 `modules.json` 中的 `sha256` 字段
3. 重新上传模块 APK 和 `modules.json` 到 VPS

### Q4: APK 体积超过 15MB

**解决方案**：检查 R8、ABI、资源依赖，并参考 [模块商店重设计计划](modules/MODULE_STORE_REDESIGN_PLAN.md) 与 Flutter Release 体积门禁。Flutter Debug APK 体积不能代表 Release。

### Q5: 签名验证失败

**解决方案**：
1. 检查 `keystore.properties` 配置是否正确
2. 检查 `gamecenter.keystore` 文件是否存在
3. 重新生成签名密钥（如果需要）

---

## 附录

### A. Gradle 任务速查表

| 任务 | 命令 | 说明 |
|------|------|------|
| 构建 Debug APK | `.\gradlew.bat :app:assembleDebug` | 构建未签名、可调试的 APK |
| 构建 Release APK | `.\gradlew.bat :app:assembleRelease` | 构建签名、混淆、资源收缩的 APK |
| 构建 Release AAB | `.\gradlew.bat :app:bundleRelease` | 构建 Android App Bundle |
| 上传 Beta 到 VPS | `.\gradlew.bat :app:buildAndUploadDebugToVps` | 构建并上传 Beta 版本到 VPS |
| 上传 Stable 到 VPS + GitHub | `.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable` | 构建并上传 Stable 版本到 VPS 和 GitHub |
| 递增版本号 | `.\gradlew.bat :app:bumpVersion` | 自动递增 versionCode |
| 运行单元测试 | `.\gradlew.bat test` | 运行所有单元测试 |
| 运行集成测试 | `.\gradlew.bat connectedAndroidTest` | 运行集成测试（需要真机或模拟器） |

### B. 文件结构

```
GameCenterApp/
├── app/                          # 主应用模块
│   ├── build.gradle               # 构建配置（签名、混淆、ABI 拆分）
│   ├── proguard-rules.pro        # ProGuard 混淆规则
│   └── src/main/
│       ├── assets/               # 内置资源（pkgInfo.txt、modules.json、预装模块 APK）
│       └── res/                 # 应用资源
├── core/                        # 核心库模块
│   ├── common/                  # 通用库（数据模型、接口）
│   ├── moduleloader/            # 模块加载器
│   ├── modulestore/            # 模块商店
│   ├── network/                 # 网络库
│   ├── update/                  # 更新库
│   └── security/                # 安全库
├── modules/                     # 独立功能模块
│   └── online-core/            # 联机核心模块
├── module-store/feature/        # 动态 APK 模块源码
│   ├── games/games/             # 游戏模块（hall/chinesechess/game2048/klotski/tts）
│   └── tools/                   # 工具模块（ai/browser/tools/vpn/wrongbook）
├── .github/                     # GitHub 配置（2026-07-06 循环 23 上线）
│   ├── workflows/
│   │   ├── android_ci.yml       # 主 CI workflow
│   │   └── cloud-build.yml      # 云编译专用 workflow
│   ├── dependabot.yml           # 依赖周扫描配置
│   └── gitleaks.toml            # secret 扫描配置
├── keystore.properties          # 签名密钥配置（不要提交到 Git）
├── local.properties            # 服务器配置（不要提交到 Git）
├── version.properties          # 版本号配置（单一事实源）
├── gamecenter.keystore        # 签名密钥（不要提交到 Git）
├── RELEASE_NOTES.md          # 当前版本面向用户的简短公告
├── CHANGELOG.md              # 完整开发历史（不直接发布）
├── README.md                  # 项目说明文档
├── CLOUD-BUILD.md             # 云编译 & VPS 部署指南
└── docs/
    └── PUBLISH_GUIDE.md     # 本文档（发布指南）
```

### C. 参考文献
- [模块化架构设计文档](modules/MODULE_STORE_REDESIGN_PLAN.md)
- [模块开发指南](modules/MODULE_DEVELOPMENT_GUIDE.md)
- [当前项目状态](CURRENT_STATE.md)
- [文档治理规则](DOCUMENTATION_GOVERNANCE.md)
- [代码 Wiki](../CODE_WIKI.md)
- [Android 官方文档 - 签署应用](https://developer.android.com/studio/publish/app-signing)
- [Android 官方文档 - 缩减、混淆和优化应用](https://developer.android.com/studio/build/shrink-code)
- [Gradle 官方文档](https://docs.gradle.org/)

---

**文档维护**：如果发现错误或需要补充，请提交 Issue 或 Pull Request。

---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
