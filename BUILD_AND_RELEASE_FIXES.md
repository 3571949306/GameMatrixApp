# 构建与发布问题修复说明

**日期**: 2026-05-12  
**版本**: v1.3.17 (222)

---

## 问题概述

在版本 217 的测试中发现以下问题：

1. **APK 签名异常** - 安装包提示"开发者签名有异常"
2. **自动更新源选择错误** - 显示"已是最新版本"，实际有新版本
3. **构建配置问题** - release 版本未正确签名

---

## 问题 1: APK 签名配置错误

### 症状
- 安装 APK 时提示"开发者签名异常"
- `jarsigner -verify` 显示"jar 未签名"
- 用户无法正常安装应用

### 根本原因

`build.gradle` 中的签名配置使用了错误的路径引用：

```groovy
// ❌ 错误配置
signingConfigs {
    release {
        storeFile file(props['STORE_FILE'])  // 相对路径错误
    }
}
```

### 解决方案

修改为使用 `rootProject.file()` 获取绝对路径：

```groovy
// ✅ 正确配置
signingConfigs {
    release {
        storeFile rootProject.file(props['STORE_FILE'])
        enableV1Signing = true
        enableV2Signing = true
    }
}
```

### 验证步骤

```bash
# 1. 构建 release 版本
.\gradlew.bat assembleRelease -x lintVitalReportRelease -x lintVitalRelease

# 2. 验证签名
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk

# 输出：jar 已验证 ✅
```

---

## 问题 2: 自动更新源选择逻辑错误

### 症状
- 测试版本 217，选择自动更新源时显示"已是最新版本"
- 实际 VPS 上已有更新版本
- 用户无法检测到新版本

### 根本原因

`UpdateManager.java` 中的版本比较逻辑存在缺陷，未正确比较 versionCode。

### 解决方案

修复 `UpdateManager.java` 中的版本比较逻辑：

```java
// ✅ 修复后的版本比较
if (latestVersionCode > currentVersionCode) {
    // 有新版本
    showUpdateDialog();
} else {
    // 已是最新版本
    showNoUpdateMessage();
}
```

### 验证步骤

1. 安装版本 217
2. 在 VPS 上部署版本 222
3. 应用内检查更新
4. 应正确显示新版本提示

---

## 问题 3: 构建系统配置问题

### 症状
- release 构建未生成签名 APK
- 上传脚本使用错误的文件名
- version.json 未正确生成

### 解决方案

#### 3.1 修复上传脚本

**文件**: `tools/upload_to_vps.py`

```python
# ❌ 错误逻辑
remote_ver = f"version-{'release' if channel == 'beta' else 'beta'}.json"

# ✅ 正确逻辑
remote_ver = f"version-{channel}.json"
```

#### 3.2 修复 build.gradle 上传任务

**文件**: `app/build.gradle`

```groovy
// ✅ 使用正确的 release APK 路径
task uploadReleaseArtifactsToVps(type: Exec) {
    dependsOn generateVersionJson
    def args = [
            file("${rootDir}/tools/upload_to_vps.py").absolutePath,
            "--apk", file("${buildDir}/outputs/apk/release/app-release.apk").absolutePath,
            "--version", file("${buildDir}/outputs/apk/release/version.json").absolutePath,
            "--channel", uploadChannel
    ]
}
```

#### 3.3 修复 version.json 生成

```groovy
// ✅ 为 debug 和 release 都生成 version.json
task generateVersionJson {
    doLast {
        def debugApkDir = file("${buildDir}/outputs/apk/debug")
        def releaseApkDir = file("${buildDir}/outputs/apk/release")
        debugApkDir.mkdirs()
        releaseApkDir.mkdirs()
        new File(debugApkDir, "version.json").setText(buildVersionJsonText(), "UTF-8")
        new File(releaseApkDir, "version.json").setText(buildVersionJsonText(), "UTF-8")
    }
}
```

---

## 完整修复验证流程

### 1. 本地构建验证

```bash
# 清理并重新构建
.\gradlew.bat clean assembleRelease -x lintVitalReportRelease -x lintVitalRelease

# 验证签名
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk
```

### 2. 上传到 VPS

```bash
# 使用 Gradle 任务上传
.\gradlew.bat uploadReleaseArtifactsToVps

# 或手动上传
python tools\upload_to_vps.py --apk app\build\outputs\apk\release\app-release.apk --version app\build\outputs\apk\release\version.json --channel beta
```

### 3. 验证 VPS 更新源

```powershell
# 检查 version.json
Invoke-RestMethod -Uri "https://hk-update.tcp0053.shop/version-beta.json"

# 应显示：
# {
#   "versionCode": 222,
#   "versionName": "1.3.17",
#   "channel": "beta"
# }
```

### 4. 应用内验证

1. 安装已签名的 APK
2. 打开应用，进入设置
3. 选择更新源（HK VPS）
4. 点击"检查更新"
5. 应正确显示版本 222

---

## 防止未来问题的措施

### 1. 签名配置检查

在 `build.gradle` 中添加验证：

```groovy
buildTypes {
    release {
        signingConfig signingConfigs.release
        if (signingConfigs.release.storeFile == null) {
            throw new GradleException("Release signing configuration is incomplete")
        }
    }
}
```

### 2. 构建后自动验证签名

添加验证任务：

```groovy
task verifyReleaseSignature(type: Exec) {
    dependsOn assembleRelease
    workingDir "${buildDir}/outputs/apk/release"
    commandLine "jarsigner", "-verify", "app-release.apk"
}
```

### 3. 文档更新

- ✅ 更新 `README.md` - 构建与部署说明
- ✅ 更新 `RELEASE_STATUS.md` - 发布状态跟踪
- ✅ 更新 `docs/PUBLISH_GUIDE.md` - 发布指南
- ✅ 更新 `CHANGELOG.md` - 版本日志
- ✅ 创建 `BUILD_AND_RELEASE_FIXES.md` - 修复说明

---

## 总结

本次修复解决了三个关键问题：

1. ✅ **APK 签名配置** - 使用正确的路径引用，启用 V1 和 V2 签名
2. ✅ **更新源选择逻辑** - 修复版本比较，正确检测新版本
3. ✅ **构建系统配置** - 修复上传脚本和 Gradle 任务

现在每次编译打包到发布源的版本都可以正常安装，并且用户能够正确检测到更新。

---

## 参考文档

- [APK 签名配置](https://developer.android.com/studio/build/signing-apk)
- [版本管理](https://developer.android.com/studio/publish/versioning)
- [发布指南](docs/PUBLISH_GUIDE.md)
- [发布状态](RELEASE_STATUS.md)
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平台 Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题。
- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言。
- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项。
- 发布前检查需覆盖中文/英文两种语言、深色/浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮。
## 2026-05-15 文档同步：Dependabot 与 CI 修复

- Dependabot 安全告警已处理：升级 Android Gradle Plugin 到 8.13.2、Gradle Wrapper 到 8.13、Kotlin 到 2.2.21、Hilt 到 2.57.2。
- 构建 classpath 已强制解析到安全版本：Netty 4.1.133.Final、BouncyCastle 1.84、commons-compress 1.26.0、jose4j 0.9.6、jdom2 2.0.6.1。
- GitHub Actions 已改为验证型 CI：使用 JDK 21，执行 debug 构建与单元测试，不在云端构建 release 包，避免暴露或依赖 release 签名文件。
- CI 命令统一添加 `-PautoBumpVersion=false`，避免自动修改 `version.properties`。
- `.gitignore` 的 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/gamecenter/app/ai/data/` 源码。
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 VPS/GitHub Release 发布仍以本机发布流程为准。
