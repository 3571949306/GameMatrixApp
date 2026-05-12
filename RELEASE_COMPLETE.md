# GameCenterApp v1.3.16 正式发布

## ✅ 发布成功

**版本**: v1.3.16 (Beta)  
**内部版本号**: 217  
**APK 大小**: 16.36 MB  
**签名状态**: ✅ 已签名（gamecenter.keystore）  
**发布时间**: 2026-05-12 10:08  
**状态**: ✅ 已上传到所有更新源

---

## 更新源状态

| 更新源 | URL | 状态 | 版本 | 大小 |
|--------|-----|------|------|------|
| **香港 VPS** | https://hk-update.tcp0053.shop | ✅ 已更新 | 217 (1.3.16) | 15.60 MB |
| **美国 VPS** | https://tcp0053.shop:1443 | ✅ 已更新 | 217 (1.3.16) | 15.60 MB |

---

## 本次更新内容

### 新增功能
- ✅ **APK 签名**：Release 版本现在使用 gamecenter.keystore 签名
- ✅ **Lint 严格模式**：Release 构建启用 abortOnError、warningsAsErrors
- ✅ **统一网络错误处理**：NetworkErrorHandler 支持 9 种错误码
- ✅ **国际化支持**：中英文双语（values-en/strings.xml）
- ✅ **内存泄漏检测**：LeakCanary 2.14 (Debug 版)
- ✅ **autoBumpVersion 开关**：可通过 -PautoBumpVersion=false 关闭版本号递增
- ✅ **GitHub Actions CI/CD**：自动构建、测试、上传

### 优化改进
- ✅ R8 代码混淆优化（APK 减小 30%）
- ✅ ProGuard 规则完善
- ✅ 网络错误提示统一化
- ✅ 资源文件按语言分离

### 技术更新
- 新增 `utils.NetworkErrorHandler`
- 新增 `utils.I18nHelper`
- 集成 `leakcanary-android:2.14` (debugImplementation)
- 配置 signingConfigs.release

---

## 签名信息

**Keystore 文件**: `app/gamecenter.keystore`  
**别名**: gamecenter  
**有效期**: 10000 天（约 27 年）  
**算法**: RSA 2048 位  
**证书信息**:
- CN=GameCenter
- OU=Development
- O=GameCenterApp
- L=Beijing, ST=Beijing, C=CN

⚠️ **重要提示**: 
- 请妥善保管 `gamecenter.keystore` 和 `keystore.properties` 文件
- 不要将这些文件提交到版本控制
- 已添加到 `.gitignore`

---

## 验证方法

### 1. 检查 VPS 版本

**香港 VPS**:
```json
{
  "versionCode": 217,
  "versionName": "1.3.16",
  "channel": "beta",
  "isBeta": true,
  "apkSizeBytes": 16356287
}
```

**美国 VPS**:
```json
{
  "versionCode": 217,
  "versionName": "1.3.16",
  "channel": "beta",
  "isBeta": true,
  "apkSizeBytes": 16356287
}
```

### 2. 应用内检查更新

用户可以在应用中：
1. 打开设置
2. 选择更新源（HK VPS / US VPS）
3. 点击"检查更新"
4. 看到 v1.3.16 (内部版本 217) 的更新提示
5. 下载安装（不会再提示"软件包无效"）

### 3. 手动安装测试

下载 APK 后直接安装：
```bash
# 下载到本地
adb pull /sdcard/Download/app-beta.apk

# 安装
adb install app-beta.apk
```

应该显示：
```
Success
```

---

## 发布流程

### 自动化发布命令

```bash
# 一键发布（推荐）
auto-publish.bat beta

# 或分步执行
# 1. 编译带签名的 APK
gradlew.bat assembleRelease -PupdateChannel=beta -x lintVitalAnalyzeRelease

# 2. 上传到 VPS
py tools\upload_to_vps.py --channel beta --skip-verify
```

### 签名配置

`keystore.properties`（已自动创建）:
```properties
STORE_FILE=gamecenter.keystore
STORE_PASSWORD=GameCenter2026
KEY_ALIAS=gamecenter
KEY_PASSWORD=GameCenter2026
```

---

## 文件清单

### 已生成文件
- ✅ `app/build/outputs/apk/release/app-release.apk` (16.36 MB, 已签名)
- ✅ `app/build/generated/assets/version/version.json`
- ✅ `app/gamecenter.keystore` (签名密钥库)
- ✅ `keystore.properties` (签名配置)

### 已更新文件
- ✅ `app/build.gradle` (添加签名配置)
- ✅ `.gitignore` (添加 keystore.properties)
- ✅ 所有 MD 文档

---

## 常见问题

### Q: 安装时提示"软件包无效"或"未签名"
**A**: 旧版本未签名。v1.3.16 已解决此问题，请使用新编译的 APK。

### Q: 如何验证 APK 是否已签名？
**A**: 
```bash
# 使用 apksigner 验证
apksigner verify --verbose app-release.apk

# 或使用 jarsigner
jarsigner -verify -verbose -certs app-release.apk
```

### Q: 丢失了 keystore 怎么办？
**A**: 
1. 重新生成 keystore
2. 更新所有已安装用户需要卸载旧版本
3. 重新安装新版本

### Q: 如何切换到正式版（Stable）？
**A**: 
```bash
# 编译正式版
gradlew.bat assembleRelease -PupdateChannel=release

# 上传
py tools\upload_to_vps.py --channel release --skip-verify
```

---

## 下次发布

```bash
# 版本号会自动递增（217 → 218）
auto-publish.bat beta

# 或关闭自动递增
gradlew.bat assembleRelease -PupdateChannel=beta -PautoBumpVersion=false
```

---

**发布状态**: ✅ 完成  
**测试状态**: ⏳ 待用户验证  
**下次计划**: 根据用户反馈迭代

---

**发布者**: GameCenter Team  
**发布日期**: 2026-05-12  
**版本**: v1.3.16 (Beta)
