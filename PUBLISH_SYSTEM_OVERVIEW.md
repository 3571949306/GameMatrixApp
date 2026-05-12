# GameCenterApp 自动发布系统 - 快速开始

## 📦 已创建的发布工具

### 重要更新（2026-05-12）

- ✅ **APK 签名问题已修复** - Release 构建自动签名
- ✅ **上传脚本逻辑已修复** - beta/release 版本命名正确
- ✅ **版本比较逻辑已修复** - 自动更新源选择正确

### 1. 一键发布脚本

#### `uploadReleaseArtifactsToVps` (Gradle 任务 - 推荐)
**功能**: 编译 APK 并自动上传到 VPS

**使用方法**:
```bash
# 发布 Beta 版
.\gradlew.bat uploadReleaseArtifactsToVps

# 发布正式版
.\gradlew.bat uploadReleaseArtifactsToVps -PcurrentVersionChannel=stable
```

**执行流程**:
1. ✅ 编译 Release APK（带签名）
2. ✅ 生成 version.json
3. ✅ 上传到香港 VPS
4. ✅ 上传到美国 VPS

---

### 2. PowerShell 发布脚本

#### `publish-all.ps1` (跨平台 PowerShell)
**功能**: 完整的发布流程，支持选择性上传

**使用方法**:
```powershell
# 发布 Beta 版
.\publish-all.ps1 -Channel beta

# 发布正式版（带 GitHub Token）
.\publish-all.ps1 -Channel release -GithubToken YOUR_GITHUB_TOKEN

# 只上传到特定更新源
.\publish-all.ps1 -Channel beta -Sources HK_VPS, GitHub
```

**参数说明**:
- `-Channel`: 发布渠道（beta/release）
- `-GithubToken`: GitHub API Token（上传 GitHub Releases 必需）
- `-SkipVerify`: 跳过发布后验证
- `-Sources`: 指定更新源（默认全部）

---

### 3. Python 发布工具

#### `tools/publish-all.py` (跨平台 Python)
**功能**: 高级发布工具，支持细粒度控制

**使用方法**:
```bash
# 安装依赖
pip install paramiko requests

# 发布 Beta 版
python tools/publish-all.py --channel beta --github-token YOUR_TOKEN

# 发布正式版
python tools/publish-all.py --channel release --github-token YOUR_TOKEN

# 只上传到特定更新源
python tools/publish-all.py --channel beta --sources hk_vps github --github-token YOUR_TOKEN
```

**支持的更新源**:
- `hk_vps` - 香港 VPS
- `us_vps` - 美国 VPS
- `github` - GitHub Releases

---

## 🎯 三个更新源

### 1. 香港 VPS
- **URL**: https://hk-update.tcp0053.shop
- **类型**: SFTP 上传
- **配置文件**: `local_private/vps/upload_config_hk.json`
- **用途**: 主更新源，低延迟
- **上传文件**: 
  - `app-{channel}.apk`
  - `version-{channel}.json`

### 2. 美国 VPS
- **URL**: https://tcp0053.shop:1443
- **类型**: SFTP 上传
- **配置文件**: `local_private/vps/upload_config_us.json`
- **用途**: 备用更新源
- **上传文件**: 
  - `app-{channel}.apk`
  - `version-{channel}.json`

### 3. GitHub Releases
- **URL**: https://github.com/3571949306/GameCenterApp/releases
- **类型**: HTTPS API 上传
- **需要**: GitHub Token（`repo` 权限）
- **用途**: 公开分发
- **上传文件**: 
  - `GameCenterApp-v{version}.apk`

---

## ⚡ 快速开始

### 最简单的方式（一键发布）

```bash
# 在项目根目录执行
auto-publish.bat beta
```

**自动完成**:
1. 编译 APK
2. 生成 version.json
3. 上传到 HK VPS ✓
4. 上传到 US VPS ✓
5. 上传到 GitHub Releases（如果提供了 Token）✓

---

## 📋 前置配置

### 1. VPS 配置文件

确保以下文件存在并配置正确：

**`local_private/vps/upload_config_hk.json`**:
```json
{
  "host": "149.104.29.181",
  "port": 22,
  "user": "root",
  "authMethod": "password",
  "password": "!H8sfw6=v-",
  "remoteDir": "/var/www/update/app",
  "publicBaseUrl": "https://update.tcp0053.shop",
  "postUploadCommands": [
    "systemctl restart gamecenter-update"
  ]
}
```

**`local_private/vps/upload_config_us.json`**:
```json
{
  "host": "38.165.22.161",
  "port": 22,
  "user": "root",
  "authMethod": "key",
  "knownHostsFile": "local_private/vps/known_hosts",
  "remoteDir": "/var/www/update/app",
  "publicBaseUrl": "https://tcp0053.shop:1443",
  "postUploadCommands": [
    "systemctl restart update-server"
  ],
  "identityFile": "C:\\Users\\tcw\\.ssh\\id_ed25519"
}
```

### 2. Python 环境（可选）

如果使用 Python 脚本，需要安装：

```bash
pip install paramiko requests
```

### 3. GitHub Token

获取步骤：
1. 访问 https://github.com/settings/tokens
2. 创建新 Token
3. 勾选 `repo` 权限
4. 复制并保存 Token

---

## 📊 发布结果验证

### 检查 VPS

访问以下 URL 确认文件已上传：

```bash
# 香港 VPS
https://hk-update.tcp0053.shop/version-beta.json

# 美国 VPS
https://tcp0053.shop:1443/version-beta.json
```

### 检查 GitHub Releases

访问：
https://github.com/3571949306/GameCenterApp/releases

### 应用内检查

在应用设置中：
1. 选择更新源（HK VPS / US VPS / GitHub）
2. 点击"检查更新"
3. 应该能看到最新发布的版本

---

## 🔧 故障排查

### 问题：VPS 上传失败

**检查清单**:
- [ ] `local_private/vps/upload_config_*.json` 是否存在
- [ ] SSH 连接是否正常：`ssh root@vps-ip`
- [ ] 防火墙是否开放 22 端口
- [ ] 远程目录是否有写权限

### 问题：GitHub Releases 上传失败

**检查清单**:
- [ ] GitHub Token 是否有效
- [ ] Token 是否有 `repo` 权限
- [ ] 仓库名称是否正确

### 问题：编译失败

**解决方法**:
```bash
# 清理缓存
gradlew.bat clean

# 重新编译
gradlew.bat assembleRelease -PupdateChannel=beta -x lintVitalAnalyzeRelease
```

---

## 📝 发布记录模板

发布成功后，脚本会显示：

```
============================================================
  发布结果汇总
============================================================
  香港 VPS: ✓ 成功
  美国 VPS: ✓ 成功
  GitHub Releases: ✓ 成功

总计：3/3 个更新源上传成功
============================================================

成功：所有更新源上传完成！
```

---

## 🚀 下一步

1. **测试发布流程**:
   ```bash
   auto-publish.bat beta
   ```

2. **验证发布结果**:
   - 访问 VPS URL 检查 version.json
   - 查看 GitHub Releases 页面

3. **配置 CI/CD**（可选）:
   - GitHub Actions 已配置在 `.github/workflows/ci.yml`
   - 推送代码自动触发发布

---

**创建时间**: 2026-05-11  
**版本**: v1.11.0  
**维护者**: GameCenter Team
