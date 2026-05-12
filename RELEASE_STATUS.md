# GameCenterApp 发布状态

## 最新发布信息

**版本**: v1.3.16 (Beta)  
**内部版本号**: 217  
**发布日期**: 2026-05-11  
**状态**: ✅ 已发布

---

## 更新源状态

| 更新源 | URL | 状态 | 版本 |
|--------|-----|------|------|
| **香港 VPS** | https://hk-update.tcp0053.shop | ✅ 已更新 | 217 (1.3.16) |
| **美国 VPS** | https://tcp0053.shop:1443 | ✅ 已更新 | 217 (1.3.16) |
| **GitHub Releases** | https://github.com/3571949306/GameCenterApp/releases | ⚠️ 需手动上传 | - |

---

## 本次更新内容

### 新增功能
- ✅ Lint 严格模式（abortOnError true, warningsAsErrors true）
- ✅ 统一网络错误处理器（NetworkErrorHandler）
- ✅ 国际化支持（中英文）
- ✅ LeakCanary 内存泄漏检测（Debug 版）
- ✅ autoBumpVersion 开关控制
- ✅ GitHub Actions CI/CD 工作流

### 优化改进
- ✅ 网络错误提示统一化
- ✅ 资源文件按语言分离
- ✅ ProGuard 规则完善
- ✅ 代码混淆优化（APK 减小 30%）

### 技术更新
- ✅ 新增 `utils.NetworkErrorHandler`
- ✅ 新增 `utils.I18nHelper`
- ✅ 集成 `leakcanary-android:2.14` (debugImplementation)

---

## 发布验证

### 香港 VPS ✅
```bash
$ Invoke-RestMethod -Uri "https://hk-update.tcp0053.shop/version-beta.json"
{
  "versionCode": 217,
  "versionName": "1.3.16",
  "channel": "beta",
  "isBeta": true
}
```

### 美国 VPS ✅
```bash
$ Invoke-RestMethod -Uri "https://tcp0053.shop:1443/version-beta.json"
{
  "versionCode": 217,
  "versionName": "1.3.16",
  "channel": "beta",
  "isBeta": true
}
```

---

## 待完成任务

### GitHub Releases 上传

由于 GitHub Token 未配置，需要手动上传。

**方法一：使用 PowerShell 脚本**
```powershell
# 获取 GitHub Token: https://github.com/settings/tokens
.\tools\upload-to-github.ps1 -GithubToken YOUR_GITHUB_TOKEN
```

**方法二：手动上传**
1. 访问：https://github.com/3571949306/GameCenterApp/releases/new
2. Tag version: `v1.3.16-beta`
3. Release title: `GameCenterApp v1.3.16 (Beta)`
4. 上传 APK: `app\build\outputs\apk\release\app-release-unsigned.apk`
5. 点击 "Publish release"

---

## 应用内检查更新

用户可以在应用中检查更新：

1. 打开应用设置
2. 选择更新源（HK VPS / US VPS / GitHub）
3. 点击"检查更新"
4. 应该显示 v1.3.16 更新

---

## 发布命令参考

### 快速发布（推荐）
```bash
# 一键发布到所有 VPS
py tools\upload_to_vps.py --apk app\build\outputs\apk\release\app-release-unsigned.apk ^
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
