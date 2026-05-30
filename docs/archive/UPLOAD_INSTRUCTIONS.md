# GameMatrixApp 上传指南

## 当前状�?

- **本地最新版�?*: versionCode 236 (v1.3.20)
- **VPS Beta 当前版本**: versionCode 236 (v1.3.20)
- **状�?*: �?Beta 通道已上传签�?R8 release �?

## 重要更新�?026-05-12�?

### 版本检查问题修�?🔥🔥

�?v1.3.19 开始，修复了两个关键问题：

#### 问题1：版本检查显�?已是最新版�? - 已修�?�?
**原因**�?
- VPS 返回�?`version-release.json` 可能缺少关键�?`versionCode` 字段
- 导致比较逻辑失效，新版本无法被检测到

**修复**�?
- �?`UpdateManager.java` 中确保从 `BuildConfig.VERSION_CODE` 获取本地版本号作为后�?
- 添加了详细的日志输出（`remote.versionCode` vs `local.versionCode`�?
- `applyUpdatePolicy` 方法现在直接比较 `remote.versionCode > local.versionCode`

#### 问题2：切换更新源失效 - 已修�?�?
**原因**�?
- `buildUpdateUrls` 方法中自定义 URL 的处理逻辑有问�?
- 自定�?URL 没有被正确添加到 URL 列表的首�?
- 没有添加备用源，导致自定�?URL 失效时无法更�?

**修复**�?
- 重构�?`buildUpdateUrls` 方法
- 自定�?URL 现在被优先放在列表的第一�?
- 添加备用源（香港 VPS �?美国 VPS �?GitHub）作为兜�?
- 添加了日志输出显示完整的 URL 构建列表

### 双版本分发架构重�?🎯

�?v1.3.19 开始，发布系统已重构为**双版本分发架�?*�?

#### 核心变化
- **测试版和正式版完全分�?*：VPS 上同时维�?`app-beta.apk` �?`app-release.apk`
- **上传脚本修复**：`upload_to_vps.py` �?`cleanup_remote` 函数现在保护两个通道的文�?
- **更新逻辑优化**�?
  - 用户开�?接收测试�? �?检�?version-beta.json
  - 用户关闭"接收测试�? �?只检�?version-release.json
  - 旧版 APP 使用 /api/update/check API 自动兼容

#### VPS 文件结构

```
/var/www/update/app/
├── app-beta.apk         # 测试版安装包
├── version-beta.json     # 测试版元数据
├── app-release.apk      # 正式版安装包
└── version-release.json  # 正式版元数据
```

### APK 签名问题已修�?

现在 APK 会自动签名，无需手动签名步骤�?

**验证签名**�?
```bash
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk
# 输出：jar 已验�?�?
```

## 上传方法

### 方法一：使�?Gradle 任务（推荐）

```bash
# 上传到所�?VPS
.\gradlew.bat uploadReleaseArtifactsToVps
```

这会自动�?
1. 构建 release APK（带签名�?
2. 生成 version.json
3. 上传到香�?VPS 和美�?VPS

### 方法二：使用 Python 脚本

1. 安装 Python 3.8+
   - 下载地址：https://www.python.org/downloads/
   - 勾�?"Add Python to PATH"

2. 安装依赖�?
   ```bash
   pip install paramiko
   ```

3. 执行上传�?
   ```bash
   # 发布测试�?
   python 工具\\upload_to_vps.py ^
       --apk app\build\outputs\apk\release\app-release.apk ^
       --version app\build\outputs\apk\release\version.json ^
       --channel beta

   # 发布正式�?
   python 工具\\upload_to_vps.py ^
       --apk app\build\outputs\apk\release\app-release.apk ^
       --version app\build\outputs\apk\release\version.json ^
       --channel release
   ```

### 方法三：手动上传

1. 使用 SFTP 客户端（�?WinSCP）连接到 VPS
2. 上传文件�?
   - 测试�? `/var/www/update/app/app-beta.apk`, `/var/www/update/app/version-beta.json`
   - 正式�? `/var/www/update/app/app-release.apk`, `/var/www/update/app/version-release.json`

## 验证上传

### 检�?VPS 更新�?

```powershell
# 检查香�?VPS 正式�?
Invoke-RestMethod -Uri "https://your-server.example.com/version-release.json"

# 检查美�?VPS 正式�?
Invoke-RestMethod -Uri "https://your-server.example.com:1443/version-release.json"

# 检查香�?VPS 测试�?
Invoke-RestMethod -Uri "https://your-server.example.com/version-beta.json"

# 检查美�?VPS 测试�?
Invoke-RestMethod -Uri "https://your-server.example.com:1443/version-beta.json"

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
# 下载正式�?APK
Invoke-WebRequest -Uri "https://your-server.example.com/app-release.apk" -OutFile "app-release.apk"

# 下载测试�?APK
Invoke-WebRequest -Uri "https://your-server.example.com/app-beta.apk" -OutFile "app-beta.apk"

# 验证签名
jarsigner -verify app-release.apk
```

## 常见问题

### Q: 上传失败 "Connection refused"
A: 检�?VPS 配置是否正确，确�?SSH 端口 22 开放�?

### Q: 上传后应用内检查更新仍显示旧版�?
A: 清除应用缓存或重启应用。确�?VPS �?`version-release.json` �?`versionCode` 大于本地版本�?

### Q: APK 安装时提示签名异�?
A: 确保使用已签名的 APK（`app-release.apk` 而非 `app-release-unsigned.apk`）�?

### Q: 测试版和正式版会互相覆盖吗？
A: 不会！从 v1.3.19 开始，上传脚本会保护两个通道的文件，不会互相覆盖�?

### Q: 旧版 APP 能检测到新版内容吗？
A: 可以！服务器端同时支持新�?API，旧�?APP 使用 `/api/update/check`，只�?`versionCode` 更低就能检测到更新�?

## 参考文�?

- [构建与发布修复说明](BUILD_AND_RELEASE_FIXES.md)
- [发布指南](文档/PUBLISH_GUIDE.md)
- [发布状态](RELEASE_STATUS.md)
---

## 2026-05-14 文档同步：文字适配与应用语言

- 新增全局按钮文字适配样式，统一提升 MaterialButton 与平�?Button 的最小高度、内边距和两行显示能力，减少“进入游戏”“发送”等按钮文字被裁切的问题�?
- 设置弹窗新增应用语言选项：跟随系统、中文、English；应用启动时会恢复已选择语言�?
- AI 任务下拉改为资源字符串，切换 English 后可显示 Chat、Summary、Translate 等英文选项�?
- 发布前检查需覆盖中文/英文两种语言、深�?浅色主题、游戏大厅卡片按钮、AI 发送按钮、工具箱小按钮和斗地主操作按钮�?
## 2026-05-15 文档同步：Dependabot �?CI 修复

- Dependabot 安全告警已处理：升级 Android Gradle Plugin �?8.13.2、Gradle Wrapper �?8.13、Kotlin �?2.2.21、Hilt �?2.57.2�?
- 构建 classpath 已强制解析到安全版本：Netty 4.1.133.Final、BouncyCastle 1.84、commons-compress 1.26.0、jose4j 0.9.6、jdom2 2.0.6.1�?
- GitHub Actions 已改为验证型 CI：使�?JDK 21，执�?debug 构建与单元测试，不在云端构建 release 包，避免暴露或依�?release 签名文件�?
- CI 命令统一添加 `-PautoBumpVersion=false`，避免自动修�?`version.properties`�?
- `.gitignore` �?`data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/GameMatrix/app/ai/data/` 源码�?
- 最�?GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆�?服务器部�?GitHub Release 发布仍以本机发布流程为准�?

## 2026-05-19 Modularization Note

The build now includes `:core:common`, `:core:network`, and `:core:update`. Release and upload commands should continue to target `:app` tasks, but maintainers must keep module-level BuildConfig generation in sync when adding new release fields to `local.properties` or `version.properties`.

## 2026-05-24 文档同步
- 底部导航切换闪退修复：创�?KeepStateNavigator 自定义导航器，使�?add/show/hide 策略替代 Navigation 默认 replace 策略
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日�?
- 内存泄漏全面修复：移�?WeakReference callback、Fragment 回调安全检查、视图引用彻底清�?
- 压力测试通过�?0轮快速Tab切换无崩�?

- 2026-05-24 游戏美化+中国象棋提示改进+华容�?中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌�?五子棋木�?D棋子/华容道深色渐变金色边�?中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光�?箭头指引�?中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店
