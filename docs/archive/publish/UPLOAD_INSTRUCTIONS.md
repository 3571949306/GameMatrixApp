# GameMatrixApp 上传指南

## 当前状态

- **本地最新版本**: versionCode 236 (v1.3.20)
- **VPS Beta 当前版本**: versionCode 236 (v1.3.20)
- **状态**: ✅ Beta 通道已上传签名 R8 release 包

## 重要更新（2026-05-12）

### 版本检查问题修复 🔥🔥

从 v1.3.19 开始，修复了两个关键问题：

#### 问题1：版本检查显示"已是最新版本" - 已修复 ✅
**原因**：
- VPS 返回的 `version-release.json` 可能缺少关键的 `versionCode` 字段
- 导致比较逻辑失效，新版本无法被检测到

**修复**：
- 在 `UpdateManager.java` 中确保从 `BuildConfig.VERSION_CODE` 获取本地版本号作为后备
- 添加了详细的日志输出（`remote.versionCode` vs `local.versionCode`）
- `applyUpdatePolicy` 方法现在直接比较 `remote.versionCode > local.versionCode`

#### 问题2：切换更新源失效 - 已修复 ✅
**原因**：
- `buildUpdateUrls` 方法中自定义 URL 的处理逻辑有问题
- 自定义 URL 没有被正确添加到 URL 列表的首位
- 没有添加备用源，导致自定义 URL 失效时无法更新

**修复**：
- 重构了 `buildUpdateUrls` 方法
- 自定义 URL 现在被优先放在列表的第一位
- 添加备用源（香港 VPS → 美国 VPS → GitHub）作为兜底
- 添加了日志输出显示完整的 URL 构建列表

### 双版本分发架构重构 🎯

从 v1.3.19 开始，发布系统已重构为**双版本分发架构**：

#### 核心变化
- **测试版和正式版完全分离**：VPS 上同时维护 `app-beta.apk` 和 `app-release.apk`
- **上传脚本修复**：`upload_to_vps.py` 的 `cleanup_remote` 函数现在保护两个通道的文件
- **更新逻辑优化**：
  - 用户开启"接收测试版" → 检查 version-beta.json
  - 用户关闭"接收测试版" → 只检查 version-release.json
  - 旧版 APP 使用 /api/update/check API 自动兼容

#### VPS 文件结构

```
/var/www/update/app/
├── app-beta.apk         # 测试版安装包
├── version-beta.json     # 测试版元数据
├── app-release.apk      # 正式版安装包
└── version-release.json  # 正式版元数据
```

### APK 签名问题已修复

现在 APK 会自动签名，无需手动签名步骤。

**验证签名**：
```bash
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk
# 输出：jar 已验证 ✅
```

## 上传方法

### 方法一：使用 Gradle 任务（推荐）

```bash
# 上传到所有 VPS
.\gradlew.bat uploadReleaseArtifactsToVps
```

这会自动：
1. 构建 release APK（带签名）
2. 生成 version.json
3. 上传到香港 VPS 和美国 VPS

### 方法二：使用 Python 脚本

1. 安装 Python 3.8+
   - 下载地址：https://www.python.org/downloads/
   - 勾选 "Add Python to PATH"

2. 安装依赖：
   ```bash
   pip install paramiko
   ```

3. 执行上传：
   ```bash
   # 发布测试版
   python tools\upload_to_vps.py ^
       --apk app\build\outputs\apk\release\app-release.apk ^
       --version app\build\outputs\apk\release\version.json ^
       --channel beta

   # 发布正式版
   python tools\upload_to_vps.py ^
       --apk app\build\outputs\apk\release\app-release.apk ^
       --version app\build\outputs\apk\release\version.json ^
       --channel release
   ```

### 方法三：手动上传

1. 使用 SFTP 客户端（如 WinSCP）连接到 VPS
2. 上传文件：
   - 测试版: `/var/www/update/app/app-beta.apk`, `/var/www/update/app/version-beta.json`
   - 正式版: `/var/www/update/app/app-release.apk`, `/var/www/update/app/version-release.json`

## 验证上传

### 检查 VPS 更新源

```powershell
# 检查香港 VPS 正式版
Invoke-RestMethod -Uri "https://hk-update.tcp0053.shop/version-release.json"

# 检查美国 VPS 正式版
Invoke-RestMethod -Uri "https://tcp0053.shop:1443/version-release.json"

# 检查香港 VPS 测试版
Invoke-RestMethod -Uri "https://hk-update.tcp0053.shop/version-beta.json"

# 检查美国 VPS 测试版
Invoke-RestMethod -Uri "https://tcp0053.shop:1443/version-beta.json"

# 应显示：
# {
#   "versionCode": 224,
#   "versionName": "1.3.19",
#   "channel": "stable|beta",
#   "isBeta": false|true
# }
```

### 下载 APK 验证

```powershell
# 下载正式版 APK
Invoke-WebRequest -Uri "https://hk-update.tcp0053.shop/app-release.apk" -OutFile "app-release.apk"

# 下载测试版 APK
Invoke-WebRequest -Uri "https://hk-update.tcp0053.shop/app-beta.apk" -OutFile "app-beta.apk"

# 验证签名
jarsigner -verify app-release.apk
```

## 常见问题

### Q: 上传失败 "Connection refused"
A: 检查 VPS 配置是否正确，确保 SSH 端口 22 开放。

### Q: 上传后应用内检查更新仍显示旧版本
A: 清除应用缓存或重启应用。确保 VPS 上 `version-release.json` 的 `versionCode` 大于本地版本。

### Q: APK 安装时提示签名异常
A: 确保使用已签名的 APK（`app-release.apk` 而非 `app-release-unsigned.apk`）。

### Q: 测试版和正式版会互相覆盖吗？
A: 不会！从 v1.3.19 开始，上传脚本会保护两个通道的文件，不会互相覆盖。

### Q: 旧版 APP 能检测到新版内容吗？
A: 可以！服务器端同时支持新旧 API，旧版 APP 使用 `/api/update/check`，只要 `versionCode` 更低就能检测到更新。

## 参考文档

- [构建与发布修复说明](BUILD_AND_RELEASE_FIXES.md)
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
- `.gitignore` 的 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/GameMatrix/app/ai/data/` 源码。
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 VPS/GitHub Release 发布仍以本机发布流程为准。

## 2026-05-19 Modularization Note

The build now includes `:core:common`, `:core:network`, and `:core:update`. Release and upload commands should continue to target `:app` tasks, but maintainers must keep module-level BuildConfig generation in sync when adding new release fields to `local.properties` or `version.properties`.
