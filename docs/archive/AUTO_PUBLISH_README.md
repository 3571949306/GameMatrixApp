<!-- flutter-store-doc-sync: 2026-07-22; historical -->
> Historical snapshot: preserved for context and not current release truth. Flutter-first module store production completion is 100%; current evidence: /docs/flutter-store/MIGRATION_STATUS.md.

# GameMatrixApp 自动发布说明

## 重要更新�?026-05-12�?

- �?**APK 签名问题已修�?* - 现在 Release 构建自动签名
- �?**上传脚本逻辑已修�?* - beta/release 版本命名正确
- �?**版本比较逻辑已修�?* - 自动更新源选择正确
- �?**构建系统优化** - 禁用有问题的 lint 任务

## 更新源列�?

本项目会自动�?APK 发布到以下三个更新源�?

| 序号 | 更新�?| URL | 说明 |
|------|--------|-----|------|
| 1 | **香港 VPS** | https://your-server.example.com | 主更新源，低延迟 |
| 2 | **美国 VPS** | https://your-server.example.com:1443 | 备用更新�?|
| 3 | **GitHub Releases** | https://github.com/3571949306/GameMatrixApp/releases | 公开分发 |

## 自动化发布工�?

项目提供以下自动化发布脚本：

### 方式一：一键发布脚本（推荐�?

#### Gradle 任务

```bash
# 发布 Beta �?
.\gradlew.bat uploadReleaseArtifactsToVps

# 发布正式�?
.\gradlew.bat uploadReleaseArtifactsToVps -PcurrentVersionChannel=stable
```

### 方式二：分步执行

#### 1. 编译 APK

```bash
# Beta �?
.\gradlew.bat assembleRelease -x lintVitalReportRelease -x lintVitalRelease

# 正式�?
.\gradlew.bat assembleRelease -x lintVitalReportRelease -x lintVitalRelease
```

**注意**：现�?APK 会自动签名，无需手动签名步骤�?

#### 2. 生成 version.json

```bash
gradlew.bat generateVersionJson -PupdateChannel=beta
```

#### 3. 上传�?VPS

```bash
python 工具\\upload_to_vps.py ^
    --apk app\build\outputs\apk\release\app-release.apk ^
    --version app\build\outputs\apk\release\version.json ^
    --channel beta --skip-verify
```

#### 4. 上传�?GitHub Releases

```bash
python 工具\\upload_to_github_release.py ^
    --apk app\build\outputs\apk\release\app-release.apk ^
    --version-name 1.3.20-beta
```

## 前置要求

### 1. Python 环境

```bash
# 安装 Python 3.8+
# 安装依赖
pip install paramiko requests
```

### 2. VPS 配置

确保 `local_private/服务器部�?` 目录下有以下配置文件�?

- `upload_config_hk.json` - 香港 VPS 配置
- `upload_config_us.json` - 美国 VPS 配置

配置示例�?

```json
{
  "host": "your-vps-ip",
  "port": 22,
  "user": "root",
  "authMethod": "password",
  "password": "your-password",
  "remoteDir": "/var/www/update/app",
  "publicBaseUrl": "https://your-update-domain.com",
  "postUploadCommands": [
    "systemctl restart GameMatrix-update"
  ]
}
```

### 3. GitHub Token

1. 访问 https://github.com/settings/tokens
2. 创建�?Token，勾�?`repo` 权限
3. 复制 Token 并保�?

## 发布流程

```
开�?
  �?
清理构建缓存
  �?
编译 Release APK
  �?
生成 version.json
  �?
上传�?HK VPS ──�?失败
  ↓成�?
上传�?US VPS ──�?失败
  ↓成�?
上传�?GitHub Releases ──�?失败
  ↓成�?
验证所有更新源
  �?
发布完成
```

## 验证发布

### 检�?VPS

访问以下 URL 确认文件已上传：

- 香港 VPS: https://your-server.example.com/version-beta.json
- 美国 VPS: https://your-server.example.com:1443/version-beta.json

### 检�?GitHub Releases

访问：https://github.com/3571949306/GameMatrixApp/releases

### 应用内检�?

在应用设置中切换到对应更新源，点�?检查更�?�?

## 故障排查

### VPS 上传失败

**可能原因�?*
- VPS 配置文件不存在或格式错误
- SSH 服务未运�?
- 网络连接问题
- 防火墙阻�?SSH 连接

**解决方法�?*
1. 检�?`local_private/服务器部�?upload_config_*.json` 是否存在
2. 验证 SSH 连接：`ssh root@your-vps-ip`
3. 确保 VPS �?22 端口开�?

### GitHub Releases 上传失败

**可能原因�?*
- GitHub Token 无效或过�?
- Token 缺少 `repo` 权限
- 仓库名称�?URL 错误

**解决方法�?*
1. 重新生成 GitHub Token
2. 确认 Token 权限包含 `repo`
3. 检查仓�?URL 是否正确

### 版本号不匹配

**解决方法�?*
1. 更新 `version.properties` 中的版本�?
2. 清理构建缓存：`gradlew clean`
3. 重新编译并生�?version.json

## 自动化发布（CI/CD�?

GitHub Actions 会自动在以下情况触发发布�?

```yaml
# .github/workflows/ci.yml
on:
  push:
    branches: [main, master]
    tags: ['v*']
```

推送代码或打标签时，会自动�?
1. 编译 Release APK
2. 运行单元测试
3. 上传�?GitHub Releases
4. 上传�?VPS（如果配置了密钥�?

## 发布记录

| 日期 | 版本 | 渠道 | 状�?|
|------|------|------|------|
| 2026-05-11 | v1.11.0 | Beta | �?已发�?|
| 2026-05-11 | v1.10.3 | Release | �?已发�?|

---

**最后更�?*: 2026-05-11  
**维护�?*: GameMatrix Team
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
- 模块下载修复：ModuleDownloader 全面重写，添加全局异常捕获、降低超时、增加日�?- 内存泄漏全面修复：移�?WeakReference callback、Fragment 回调安全检查、视图引用彻底清�?- 压力测试通过�?0轮快速Tab切换无崩�?

- 2026-05-24 游戏美化+中国象棋提示改进+华容�?中国象棋模块商店上架：四个游戏视觉美化（斗地主径向渐变桌�?五子棋木�?D棋子/华容道深色渐变金色边�?中国象棋木纹角标波浪线）；中国象棋提示改为棋盘可视化（蓝色脉冲光�?箭头指引�?中文棋谱描述；华容道和中国象棋创建独立APK模块（feature/games/klotski、feature/games/chinesechess）v2.0.0上架模块商店