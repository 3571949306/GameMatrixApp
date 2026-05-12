# GameCenterApp 自动发布说明

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

#### Windows 批处理

```bash
# 发布 Beta 版
auto-publish.bat beta

# 发布正式版
auto-publish.bat release YOUR_GITHUB_TOKEN
```

#### PowerShell 脚本

```powershell
# 发布 Beta 版
.\publish-all.ps1 -Channel beta

# 发布正式版（带 GitHub Token）
.\publish-all.ps1 -Channel release -GithubToken YOUR_GITHUB_TOKEN
```

### 方式二：分步执行

#### 1. 编译 APK

```bash
# Beta 版
gradlew.bat assembleRelease -PupdateChannel=beta -x lintVitalAnalyzeRelease

# 正式版
gradlew.bat assembleRelease -PupdateChannel=release -x lintVitalAnalyzeRelease
```

#### 2. 生成 version.json

```bash
gradlew.bat generateVersionJson -PupdateChannel=beta
```

#### 3. 上传到 VPS

```bash
python tools\upload_to_vps.py ^
    --apk app\build\outputs\apk\release\app-release-unsigned.apk ^
    --version app\build\outputs\version.json ^
    --channel beta --skip-verify
```

#### 4. 上传到 GitHub Releases

```bash
python tools\upload_to_github_release.py ^
    app\build\outputs\apk\release\app-release-unsigned.apk ^
    "v1.11.0"
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
