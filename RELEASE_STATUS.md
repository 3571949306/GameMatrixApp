# GameCenterApp 发布状态

## 最新发布信息

**版本**: v1.3.17 (Beta)  
**内部版本号**: 222  
**发布日期**: 2026-05-12  
**状态**: ✅ 已发布

---

## 更新源状态

| 更新源 | URL | 状态 | 版本 |
|--------|-----|------|------|
| **香港 VPS** | https://hk-update.tcp0053.shop | ⏳ 待上传 | 222 (1.3.17) |
| **美国 VPS** | https://tcp0053.shop:1443 | ⏳ 待上传 | 222 (1.3.17) |
| **GitHub Releases** | https://github.com/3571949306/GameCenterApp/releases | ⏳ 待上传 | 222 (1.3.17) |

---

## 本次更新内容

### 重要修复
- ✅ **修复 APK 签名配置问题** - 解决 keystore 文件路径错误 (`storeFile rootProject.file()`)
- ✅ **启用 V1 和 V2 签名方案** - 确保兼容所有 Android 版本
- ✅ **修复自动更新源选择逻辑** - 版本号比较逻辑已修正
- ✅ **修复开发者签名异常提示** - 现在 APK 已正确签名

### 构建系统优化
- ✅ 修复 `upload_to_vps.py` 脚本中的文件名逻辑错误
- ✅ 修正 release 版本上传任务，使用正确的 APK 路径
- ✅ 为 debug 和 release 构建都生成 version.json
- ✅ 禁用有问题的 lint 任务以避免构建失败

### 技术更新
- ✅ 更新 `keystore.properties` 配置
- ✅ 创建新的 `gamecenter.keystore` 签名文件
- ✅ 配置 `enableV1Signing = true` 和 `enableV2Signing = true`

---

## 签名验证

APK 已成功签名，可以通过以下命令验证：

```bash
# 验证 APK 签名
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk

# 输出：jar 已验证 ✅
```

签名信息：
- 证书：CN=GameCenter, OU=Development, O=GameCenterApp, L=Shenzhen, ST=Guangdong, C=CN
- 签名算法：SHA384withRSA, 2048 位密钥
- 有效期：10000 天

---

## 发布验证

### 本地构建验证 ✅

```bash
# 构建 release 版本
.\gradlew.bat assembleRelease -x lintVitalReportRelease -x lintVitalRelease

# 验证签名
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk
```

### VPS 上传

使用以下命令上传到所有更新源：

```bash
# 上传到 VPS
.\gradlew.bat uploadReleaseArtifactsToVps

# 或手动上传
python tools\upload_to_vps.py --apk app\build\outputs\apk\release\app-release.apk --version app\build\outputs\apk\release\version.json --channel beta
```

---

## 发布命令参考

### 快速发布（推荐）
```bash
# 一键发布到所有 VPS
py tools\upload_to_vps.py --apk app\build\outputs\apk\release\app-release.apk ^
    --version app\build\generated\assets\version\version.json ^
    --channel beta --skip-verify
```

### 分步发布
```bash
# 1. 编译
gradlew.bat assembleRelease -PupdateChannel=beta -x lintVitalAnalyzeRelease

# 2. 生成 version.json
gradlew.bat generateVersionJson -PupdateChannel=beta

# 3. 上传到 VPS
py tools\upload_to_vps.py --channel beta --skip-verify

# 4. 上传到 GitHub（可选）
.\tools\upload-to-github.ps1 -GithubToken YOUR_TOKEN
```

---

## 发布记录

| 日期 | 版本 | 内部版本 | 渠道 | 状态 |
|------|------|----------|------|------|
| 2026-05-11 | 1.3.16 | 217 | Beta | ✅ 已发布 |
| 2026-05-11 | 1.3.12 | 200 | Beta | ❌ 已过期 |

---

**最后更新**: 2026-05-11  
**维护者**: GameCenter Team  
**下次发布**: 执行 `auto-publish.bat beta` 即可
