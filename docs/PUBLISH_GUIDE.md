# GameCenterApp 发布指南

> **文档版本**: v1.0.0  
> **最后更�?*: 2026-05-26  
> **维护�?*: GameCenterApp 开发团�?
---

## 目录

1. [发布流程概览](#发布流程概览)
2. [前置条件](#前置条件)
3. [版本管理](#版本管理)
4. [构建流程](#构建流程)
5. [签名配置](#签名配置)
6. [上传部署](#上传部署)
7. [模块商店更新](#模块商店更新)
8. [测试检查清单](#测试检查清�?
9. [回滚计划](#回滚计划)
10. [常见问题](#常见问题)

---

## 发布流程概览

GameCenterApp 使用 **双版本分发策�?*�?
| 通道 | 版本类型 | 分发目标 | 上传位置 |
|------|---------|---------|---------|
| **Beta** | 测试�?| 开�?接受测试�?的用�?| VPS 服务�?|
| **Stable** | 正式�?| 所有用�?| VPS 服务�?+ GitHub Releases |

### 发布流程�?
```
┌─────────────────�?�? 更新版本�?    �?�? (version.properties) �?└────────┬────────�?          �?          �?┌─────────────────�?�? 更新 CHANGELOG.md �?└────────┬────────�?          �?          �?┌─────────────────�?�? 执行测试        �?�? (单元测试 + 真机测试) �?└────────┬────────�?          �?          �?┌─────────────────�?�? 构建 Release APK �?└────────┬────────�?          �?          �?┌─────────────────�?�? 上传�?VPS      �?�? (upload_to_vps.py) �?└────────┬────────�?          �?          �?┌─────────────────�?�? (可�? 上传�?GitHub Releases �?└────────┬────────�?          �?          �?┌─────────────────�?�? 更新 modules.json (模块商店) �?└────────┬────────�?          �?          �?┌─────────────────�?�? 通知用户更新     �?└─────────────────�?```

---

## 前置条件

### 1. Java 环境

构建需�?**Java 17+**�?
```bash
# 检�?Java 版本
java -version

# 输出应该类似�?# openjdk version "17.0.10" 2024-01-16
# OpenJDK Runtime Environment (build 17.0.10+7)
# OpenJDK 64-Bit Server VM (build 17.0.10+7, mixed mode)
```

如果未安�?Java 17+，请下载并安装：
- **推荐**: Eclipse Temurin (�?AdoptOpenJDK) - https://adoptium.net/
- **备�?*: Oracle JDK 17 - https://www.oracle.com/java/technologies/downloads/

设置 `JAVA_HOME` 环境变量�?
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

Release APK 需要签名密�?(`gamecenter.keystore`)�?
- **文件位置**: 项目根目�?`gamecenter.keystore`
- **备份位置**: 安全位置（不要提交到 Git�?- **密钥库配�?*: `keystore.properties`（不要提交到 Git�?
如果缺少签名密钥，请联系项目负责人获取�?
### 3. 服务器配�?
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

GameCenterApp 使用 **双版本号系统**�?
| 版本�?| 用�?| 示例 | 文件位置 |
|---------|------|------|---------|
| **versionCode** | 内部版本号（整数，递增�?| `341` | `version.properties` |
| **versionName** | 用户展示版本号（语义化版本） | `1.4.0` | `version.properties` |

### version.properties 格式

```properties
# version.properties
versionCode=341
versionName=1.4.0
lastStableVersionCode=340
lastStableVersionName=1.3.30
betaNoticeVersionGap=3
```

### 更新版本�?
**Beta 版本**（默认）�?```bash
# 自动递增 versionCode（不改变 versionName�?.\gradlew.bat :app:bumpVersion
```

**Stable 版本**�?```bash
# 1. 手动更新 version.properties
#    - versionName=2.0.0（例如）
#    - versionCode=350（例如）
#    - lastStableVersionCode=341（上一个正式版 versionCode�?#    - lastStableVersionName=1.4.0（上一个正式版 versionName�?
# 2. 构建并上�?.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable
```

---

## 构建流程

### 1. Debug 构建（本地测试）

```bash
# 构建 Debug APK（未签名，可调试�?.\gradlew.bat :app:assembleDebug

# 输出位置
# app/build/outputs/apk/debug/app-debug.apk
```

### 2. Release 构建（正式发布）

```bash
# 构建 Release APK（签名，混淆，资源收缩）
.\gradlew.bat :app:assembleRelease

# 输出位置
# app/build/outputs/apk/release/app-release.apk
```

### 3. AAB 构建（Google Play�?
```bash
# 构建 Release AAB（Android App Bundle�?.\gradlew.bat :app:bundleRelease

# 输出位置
# app/build/outputs/bundle/release/app-release.aab
```

### 4. 构建并上传到 VPS（Beta 通道�?
```bash
# 构建、生�?version.json、上传到 VPS（Beta 通道�?.\gradlew.bat :app:buildAndUploadDebugToVps

# 自动执行�?# 1. assembleRelease
# 2. generateVersionJson
# 3. uploadReleaseArtifactsToVps
# 4. bumpVersion（如�?autoBumpVersion=true�?```

### 5. 构建并上传到 VPS + GitHub Releases（Stable 通道�?
```bash
# 构建、生�?version.json、上传到 VPS �?GitHub Releases（Stable 通道�?.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable

# 自动执行�?# 1. assembleRelease
# 2. generateVersionJson
# 3. uploadReleaseArtifactsToVps
# 4. uploadApkToGitHubRelease
# 5. bumpVersion（如�?autoBumpVersion=true�?```

---

## 签名配置

### 1. 创建 keystore.properties

在项目根目录创建 `keystore.properties`�?*不要提交�?Git**）：

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
                // 启用 v1 �?v2 签名方案
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
            
            // 只有�?keystore 存在时才应用签名配置
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

# 输出应该包含�?# jar is unsigned. (signatures missing or not parsable)
# �?# jar verified.
```

---

## 上传部署

### 1. 上传�?VPS 服务�?
使用 `工具/upload_to_vps.py` 脚本�?
```bash
# 上传 Beta 版本�?VPS
python 工具/upload_to_vps.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --version app/build/outputs/apk/release/version.json \
  --channel beta

# 上传 Stable 版本�?VPS
python 工具/upload_to_vps.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --version app/build/outputs/apk/release/version.json \
  --channel stable
```

### 2. 上传�?GitHub Releases

**仅适用�?Stable 通道**�?
```bash
# 上传�?GitHub Releases
python 工具/upload_to_github_release.py \
  --apk app/build/outputs/apk/release/app-release.apk \
  --version-name 1.4.0 \
  --changelog-file CHANGELOG.md
```

### 3. 上传模块 APK �?VPS

```bash
# 上传模块 APK �?VPS
scp 模块商店/功能模块/游戏/games/build/outputs/apk/release/*.apk user@your-server:/var/www/modules/

# 更新 modules.json
scp 模块商店/modules.json user@your-server:/var/www/update/modules/modules.json
```

---

## 模块商店更新

### 1. 更新 modules.json

�?`模块商店/modules.json` 中添加新模块或更新现有模块：

```json
{
  "version": 11,
  "modules": [
    {
      "id": "games_hall",
      "name": "游戏大厅",
      "description": "聚合内置和已下载游戏的游戏大厅模块�?,
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

### 2. 计算模块 APK �?SHA-256

```bash
# 计算 SHA-256 哈希�?sha256sum 模块商店/功能模块/游戏/games/build/outputs/apk/release/feature_games_hall_v100.apk

# 输出示例�?# dd05bb25c737893969826a98c1495251cd6d363f31c6021a6fe9d0ddb9a4d900  feature_games_hall_v100.apk
```

### 3. 上传 modules.json 和模�?APK

```bash
# 上传 modules.json
scp 模块商店/modules.json user@your-server:/var/www/update/modules/modules.json

# 上传模块 APK
scp 模块商店/功能模块/游戏/games/build/outputs/apk/release/*.apk user@your-server:/var/www/modules/
```

---

## 测试检查清�?
### 1. 单元测试

```bash
# 运行所有单元测�?.\gradlew.bat test

# 运行特定模块的单元测�?.\gradlew.bat :core:common:test
.\gradlew.bat :core:moduleloader:test
.\gradlew.bat :core:modulestore:test
```

### 2. 集成测试

```bash
# 运行集成测试（需要真机或模拟器）
.\gradlew.bat connectedAndroidTest
```

### 3. 手动测试检查清�?
- [ ] 应用启动正常
- [ ] 模块商店加载正常
- [ ] 模块下载、安装、卸载正�?- [ ] 斗地主、五子棋等内置游戏运行正�?- [ ] 联机功能（WebSocket）正�?- [ ] VPN 功能正常（如果适用�?- [ ] AI 功能正常（如果适用�?- [ ] 热更新功能正�?- [ ] APK 体积 �?5MB（框�?APK�?- [ ] 无崩溃、ANR、内存泄�?
---

## 回滚计划

### 1. 回滚到上一个版�?
如果用户报告严重 Bug，可以快速回滚：

```bash
# 1. 恢复上一个版本的 APK �?version.json
#    （在 VPS 服务器上�?cp /var/www/update/backup/app-release-v1.3.30.apk /var/www/update/app-release.apk
cp /var/www/update/backup/version-v1.3.30.json /var/www/update/version.json

# 2. 重启 VPS 服务器（如果需要）
sudo systemctl restart nginx
```

### 2. 紧急修复版�?
```bash
# 1. 创建紧急修复分�?git checkout -b hotfix/v1.4.1

# 2. 修复 Bug
#    （修改代码）

# 3. 更新版本�?#    （修�?version.properties：versionCode=342, versionName=1.4.1�?
# 4. 构建并上�?.\gradlew.bat :app:buildAndUploadDebugToVps

# 5. 合并�?main 分支
git checkout main
git merge hotfix/v1.4.1
git push origin main
```

---

## 常见问题

### Q1: 构建失败，提�?"JAVA_HOME is not set"

**解决方案**�?1. 安装 Java 17+（参�?[前置条件](#前置条件)�?2. 设置 `JAVA_HOME` 环境变量
3. 重新启动 PowerShell/终端

### Q2: 上传�?VPS 失败，提�?"Connection refused"

**解决方案**�?1. 检�?VPS 服务器是否在�?2. 检�?SSH 密钥是否配置正确
3. 检�?VPS 服务器地址是否正确（在 `local.properties` 中配置）

### Q3: 模块下载失败，提�?"SHA-256 verification failed"

**解决方案**�?1. 重新计算模块 APK �?SHA-256 哈希�?2. 更新 `modules.json` 中的 `sha256` 字段
3. 重新上传模块 APK �?`modules.json` �?VPS

### Q4: APK 体积超过 15MB

**解决方案**�?1. 检�?R8 全模式是否启用（`minifyEnabled true`、`shrinkResources true`�?2. 检�?ABI 拆分是否配置正确（仅保留 `arm64-v8a`�?3. 检查是否有不必要的资源或库被打�?4. 参�?[T08: 框架 APK 体积优化](文档/MODULAR_ARCHITECTURE_DESIGN.md#T08-框架-APK-体积优化) 文档

### Q5: 签名验证失败

**解决方案**�?1. 检�?`keystore.properties` 配置是否正确
2. 检�?`gamecenter.keystore` 文件是否存在
3. 重新生成签名密钥（如果需要）

---

## 附录

### A. Gradle 任务速查�?
| 任务 | 命令 | 说明 |
|------|------|------|
| 构建 Debug APK | `.\gradlew.bat :app:assembleDebug` | 构建未签名、可调试�?APK |
| 构建 Release APK | `.\gradlew.bat :app:assembleRelease` | 构建签名、混淆、资源收缩的 APK |
| 构建 Release AAB | `.\gradlew.bat :app:bundleRelease` | 构建 Android App Bundle |
| 上传 Beta �?VPS | `.\gradlew.bat :app:buildAndUploadDebugToVps` | 构建并上�?Beta 版本�?VPS |
| 上传 Stable �?VPS + GitHub | `.\gradlew.bat :app:buildAndUploadToVpsAndGitHub -PupdateChannel=stable` | 构建并上�?Stable 版本�?VPS �?GitHub |
| 递增版本�?| `.\gradlew.bat :app:bumpVersion` | 自动递增 versionCode |
| 运行单元测试 | `.\gradlew.bat test` | 运行所有单元测�?|
| 运行集成测试 | `.\gradlew.bat connectedAndroidTest` | 运行集成测试（需要真机或模拟器） |

### B. 文件结构

```
GameCenterApp/
├── app/                          # 主应用模�?�?  ├── build.gradle               # 构建配置（签名、混淆、ABI 拆分�?�?  ├── proguard-rules.pro        # ProGuard 混淆规则
�?  └── src/main/
�?      ├── assets/               # 内置资源（pkgInfo.txt、modules.json�?�?      └── res/                 # 应用资源
├── core/                        # 核心库模�?�?  ├── common/                  # 通用库（数据模型、接口）
�?  ├── moduleloader/            # 模块加载�?�?  ├── modulestore/            # 模块商店
�?  ├── network/                 # 网络�?�?  ├── update/                  # 更新�?�?  └── security/                # 安全�?├── modules/                     # 独立功能模块
�?  └── online-core/            # 联机核心模块
├── 模块商店/                    # 模块商店目录
�?  ├── 压缩模块/               # 游戏模块压缩包（ZIP�?�?  ├── 功能模块/               # 独立功能模块源代�?�?  └── modules.json            # 模块市场清单文件
├── keystore.properties          # 签名密钥配置（不要提交到 Git�?├── local.properties            # 服务器配置（不要提交�?Git�?├── version.properties          # 版本号配�?├── gamecenter.keystore        # 签名密钥（不要提交到 Git�?├── CHANGELOG.md              # 版本更新日志
├── README.md                  # 项目说明文档
└── docs/
    └── PUBLISH_GUIDE.md     # 本文档（发布指南�?```

### C. 参考文�?
- [模块化架构设计文档](文档/MODULAR_ARCHITECTURE_DESIGN.md)
- [模块开发指南](文档/MODULE_DEVELOPMENT_GUIDE.md)
- [项目上下文](PROJECT_CONTEXT.md)
- [代码Wiki](CODE_WIKI.md)
- [Android 官方文档 - 签署应用](https://developer.android.com/studio/publish/app-signing)
- [Android 官方文档 - 缩减、混淆和优化应用](https://developer.android.com/studio/build/shrink-code)
- [Gradle 官方文档](https://docs.gradle.org/)

---

**文档维护**：如果发现错误或需要补充，请提�?Issue �?Pull Request�?

---
[🔙 返回文档索引](/docs/DOCUMENTATION_INDEX.md)
