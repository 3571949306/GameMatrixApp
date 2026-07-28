<!-- flutter-store-doc-sync: 2026-07-22; historical -->
> Historical snapshot: preserved for context and not current release truth. Flutter-first module store production completion is 100%; current facts: /docs/CURRENT_STATE.md.

# GameMatrixApp 发布状�?

## 2026-05-21 维护状�?

- 当前正式版目标：`v1.3.27` / `versionCode 265`�?
- 本次发布内容：系统栏安全区适配、AI 本地模型切换/路由修复、五子棋/中国象棋四档按钮难度与底部按钮重排�?
- 仓库维护分支：仅 `main`�?
- GitHub Dependabot open alerts：`0`�?
- 本地 GitHub 上传：Git �?`https://github.com` 使用 `http://127.0.0.1:10808`，无需依赖 xray TUN/虚拟网卡模式�?
- 发布提醒：正�?APK 发布仍走本机签名和既�?服务器部�?GitHub Release 脚本；云�?CI 负责验证，不保存 release 签名材料�?

## 最新发布信�?

**版本**: v1.3.27 (Stable)
**内部版本�?*: 265
**发布日期**: 2026-05-21
**状�?*: �?已发布到 HK/US VPS �?GitHub Release

---

## 更新源状�?

| 更新�?| URL | 状�?| 版本 |
|--------|-----|------|------|
| **香港 VPS** | https://your-server.example.com | �?已验�?| 265 (1.3.27) |
| **美国 VPS** | https://your-server.example.com:1443 | �?已验�?| 265 (1.3.27) |
| **GitHub Releases** | https://github.com/3571949306/GameMatrixApp/releases/tag/1.3.27 | �?已上�?| 265 (1.3.27) |

---

## 本次更新内容

### 重要修复
- �?**修复 APK 签名配置问题** - 解决 keystore 文件路径错误 (`storeFile rootProject.file()`)
- �?**启用 V1 �?V2 签名方案** - 确保兼容所�?Android 版本
- �?**修复自动更新源选择逻辑** - 版本号比较逻辑已修�?
- �?**修复开发者签名异常提�?* - 现在 APK 已正确签�?

### 构建系统优化
- �?修复 `upload_to_vps.py` 脚本中的文件名逻辑错误
- �?修正 release 版本上传任务，使用正确的 APK 路径
- �?�?debug �?release 构建都生�?version.json
- �?禁用有问题的 lint 任务以避免构建失�?

### 技术更�?
- �?更新 `keystore.properties` 配置
- �?创建新的 `GameMatrix.keystore` 签名文件
- �?配置 `enableV1Signing = true` �?`enableV2Signing = true`

---

## 签名验证

APK 已成功签名，可以通过以下命令验证�?

```bash
# 验证 APK 签名
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk

# 输出：jar 已验�?�?
```

签名信息�?
- 证书：CN=GameMatrix, OU=Development, O=GameMatrixApp, L=Shenzhen, ST=Guangdong, C=CN
- 签名算法：SHA384withRSA, 2048 位密�?
- 有效期：10000 �?

---

## 发布验证

### 本地构建验证 �?

```bash
# 构建 release 版本
.\gradlew.bat assembleRelease -x lintVitalReportRelease -x lintVitalRelease

# 验证签名
cd app\build\outputs\apk\release
jarsigner -verify app-release.apk
```

### VPS 上传

使用以下命令上传到所有更新源�?

```bash
# 上传�?VPS
.\gradlew.bat uploadReleaseArtifactsToVps

# 或手动上�?
python 工具\\upload_to_vps.py --apk app\build\outputs\apk\release\app-release.apk --version app\build\outputs\apk\release\version.json --channel beta
```

---

## 发布命令参�?

### 快速发布（推荐�?
```bash
# 一键发布到所�?VPS
py 工具\\upload_to_vps.py --apk app\build\outputs\apk\release\app-release.apk ^
    --version app\build\generated\assets\version\version.json ^
    --channel beta --skip-verify
```

### 分步发布
```bash
# 1. 编译
gradlew.bat assembleRelease -PupdateChannel=beta -x lintVitalAnalyzeRelease

# 2. 生成 version.json
gradlew.bat generateVersionJson -PupdateChannel=beta

# 3. 上传�?VPS
py 工具\\upload_to_vps.py --channel beta --skip-verify

# 4. 上传�?GitHub（可选）
.\工具\\upload-to-github.ps1 -GithubToken YOUR_TOKEN
```

---

## 发布记录

| 日期 | 版本 | 内部版本 | 渠道 | 状�?|
|------|------|----------|------|------|
| 2026-05-11 | 1.3.16 | 217 | Beta | �?已发�?|
| 2026-05-11 | 1.3.12 | 200 | Beta | �?已过�?|

---

**最后更�?*: 2026-05-11  
**维护�?*: GameMatrix Team  
**下次发布**: 执行 `auto-publish.bat beta` 即可
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