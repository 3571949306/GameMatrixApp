# GameCenterApp 自动发布说明

## 重要更新（2026-05-12）

- ✅ **APK 签名问题已修复** - 现在 Release 构建自动签名
- ✅ **上传脚本逻辑已修复** - beta/release 版本命名正确
- ✅ **版本比较逻辑已修复** - 自动更新源选择正确
- ✅ **构建系统优化** - 禁用有问题的 lint 任务

## 更新源列表

本项目会自动将 APK 发布到以下三个更新源：

| 序号 | 更新源 | URL | 说明 |
|------|--------|-----|------|
| 1 | **香港 VPS** | https://hk-update.tcp0053.shop | 主更新源，低延迟 |
| 2 | **美国 VPS** | https://tcp0053.shop:1443 | 备用更新源 |
| 3 | **GitHub Releases** | https://github.com/3571949306/GameCenterApp/releases | 公开分发 |

## 自动化发布工具

项目提供以下自动化发布脚本：

### 方式一：一键发布脚本（推荐）

#### Gradle 任务

```bash
# 发布 Beta 版
.\gradlew.bat uploadReleaseArtifactsToVps

# 发布正式版
.\gradlew.bat uploadReleaseArtifactsToVps -PcurrentVersionChannel=stable
```

### 方式二：分步执行

#### 1. 编译 APK

```bash
# Beta 版
.\gradlew.bat assembleRelease -x lintVitalReportRelease -x lintVitalRelease

# 正式版
.\gradlew.bat assembleRelease -x lintVitalReportRelease -x lintVitalRelease
```

**注意**：现在 APK 会自动签名，无需手动签名步骤。

#### 2. 生成 version.json

```bash
gradlew.bat generateVersionJson -PupdateChannel=beta
```

#### 3. 上传到 VPS

```bash
python tools\upload_to_vps.py ^
    --apk app\build\outputs\apk\release\app-release.apk ^
    --version app\build\outputs\apk\release\version.json ^
    --channel beta --skip-verify
```

#### 4. 上传到 GitHub Releases

```bash
python tools\upload_to_github_release.py ^
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

确保 `local_private/vps/` 目录下有以下配置文件：

- `upload_config_hk.json` - 香港 VPS 配置
- `upload_config_us.json` - 美国 VPS 配置

配置示例：

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
    "systemctl restart gamecenter-update"
  ]
}
```

### 3. GitHub Token

1. 访问 https://github.com/settings/tokens
2. 创建新 Token，勾选 `repo` 权限
3. 复制 Token 并保存

## 发布流程

```
开始
  ↓
清理构建缓存
  ↓
编译 Release APK
  ↓
生成 version.json
  ↓
上传到 HK VPS ──→ 失败
  ↓成功
上传到 US VPS ──→ 失败
  ↓成功
上传到 GitHub Releases ──→ 失败
  ↓成功
验证所有更新源
  ↓
发布完成
```

## 验证发布

### 检查 VPS

访问以下 URL 确认文件已上传：

- 香港 VPS: https://hk-update.tcp0053.shop/version-beta.json
- 美国 VPS: https://tcp0053.shop:1443/version-beta.json

### 检查 GitHub Releases

访问：https://github.com/3571949306/GameCenterApp/releases

### 应用内检查

在应用设置中切换到对应更新源，点击"检查更新"。

## 故障排查

### VPS 上传失败

**可能原因：**
- VPS 配置文件不存在或格式错误
- SSH 服务未运行
- 网络连接问题
- 防火墙阻止 SSH 连接

**解决方法：**
1. 检查 `local_private/vps/upload_config_*.json` 是否存在
2. 验证 SSH 连接：`ssh root@your-vps-ip`
3. 确保 VPS 的 22 端口开放

### GitHub Releases 上传失败

**可能原因：**
- GitHub Token 无效或过期
- Token 缺少 `repo` 权限
- 仓库名称或 URL 错误

**解决方法：**
1. 重新生成 GitHub Token
2. 确认 Token 权限包含 `repo`
3. 检查仓库 URL 是否正确

### 版本号不匹配

**解决方法：**
1. 更新 `version.properties` 中的版本号
2. 清理构建缓存：`gradlew clean`
3. 重新编译并生成 version.json

## 自动化发布（CI/CD）

GitHub Actions 会自动在以下情况触发发布：

```yaml
# .github/workflows/ci.yml
on:
  push:
    branches: [main, master]
    tags: ['v*']
```

推送代码或打标签时，会自动：
1. 编译 Release APK
2. 运行单元测试
3. 上传到 GitHub Releases
4. 上传到 VPS（如果配置了密钥）

## 发布记录

| 日期 | 版本 | 渠道 | 状态 |
|------|------|------|------|
| 2026-05-11 | v1.11.0 | Beta | ✓ 已发布 |
| 2026-05-11 | v1.10.3 | Release | ✓ 已发布 |

---

**最后更新**: 2026-05-11  
**维护者**: GameCenter Team
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
