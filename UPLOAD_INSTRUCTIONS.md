# GameCenterApp 上传指南

## 当前状态

- **本地最新版本**: versionCode 217 (v1.3.16)
- **VPS 当前版本**: versionCode 200 (v1.3.12)
- **状态**: ⚠️ 需要上传

## 上传方法

由于 Windows 环境限制，请选择以下任一方法：

### 方法一：安装 Python（推荐）

1. 安装 Python 3.8+
   - 下载地址：https://www.python.org/downloads/
   - 勾选 "Add Python to PATH"

2. 安装依赖：
   ```bash
   pip install paramiko
   ```

3. 执行上传：
   ```bash
   # 上传到香港 VPS
   python tools\upload_to_vps.py ^
       --apk app\build\outputs\apk\release\app-release-unsigned.apk ^
       --version app\build\generated\assets\version\version.json ^
       --channel beta --skip-verify
   
   # 上传到美国 VPS（需要配置 SSH 密钥）
   python tools\upload_to_vps.py ^
       --apk app\build\outputs\apk\release\app-release-unsigned.apk ^
       --version app\build\generated\assets\version\version.json ^
       --channel beta --skip-verify
   ```

### 方法二：使用 WinSCP（图形界面）

1. 下载并安装 WinSCP：https://winscp.net

2. 连接到香港 VPS：
   - 主机：149.104.29.181
   - 用户名：root
   - 密码：!H8sfw6=v-
   - 端口：22
   - 协议：SFTP

3. 上传文件：
   - 本地：`app\build\outputs\apk\release\app-release-unsigned.apk`
   - 远程：`/var/www/update/app/app-beta.apk`
   
   - 本地：`app\build\generated\assets\version\version.json`
   - 远程：`/var/www/update/app/version-beta.json`

4. 对美国 VPS 重复上述步骤：
   - 主机：38.165.22.161
   - 端口：22
   - 使用 SSH 密钥认证

### 方法三：使用 PowerShell 脚本（需要安装 SSH 工具）

如果已安装 WinSCP 或 PuTTY，可以使用提供的 PowerShell 脚本：

```bash
# 使用 WinSCP
.\tools\upload-to-hk-vps.ps1 -Channel beta

# 或使用 PuTTY/PSCP
.\tools\upload-to-hk-vps.ps1 -Channel beta
```

### 方法四：使用 GitHub Actions（自动化）

推送代码到 GitHub 会自动触发 CI/CD 流程：

```yaml
# .github/workflows/ci.yml
on:
  push:
    branches: [main]
```

工作流程会：
1. 编译 APK
2. 运行测试
3. 上传到 GitHub Releases
4. （可选）上传到 VPS（如果配置了密钥）

## 验证上传

上传完成后，访问以下 URL 验证：

### 香港 VPS
```
https://hk-update.tcp0053.shop/version-beta.json
```

应该显示：
```json
{
  "versionCode": 217,
  "versionName": "1.3.16",
  "channel": "beta"
}
```

### 美国 VPS
```
https://tcp0053.shop:1443/version-beta.json
```

### GitHub Releases
```
https://github.com/3571949306/GameCenterApp/releases
```

## 上传检查清单

- [ ] 编译 Release APK
- [ ] 生成 version.json
- [ ] 上传到香港 VPS
- [ ] 上传到美国 VPS
- [ ] 上传到 GitHub Releases（可选）
- [ ] 验证所有 URL 可访问
- [ ] 检查 version.json 版本号正确
- [ ] 测试应用内检查更新功能

## 常见问题

### Q: 为什么 VPS 上的版本还是旧的？

A: 可能原因：
1. 上传脚本未执行成功
2. 使用了错误的 channel（beta vs release）
3. 远程文件权限问题
4. VPS 服务未重启

解决方法：
```bash
# SSH 连接到 VPS
ssh root@149.104.29.181

# 检查文件
ls -la /var/www/update/app/

# 重启服务
systemctl restart gamecenter-update
```

### Q: 上传时提示权限错误

A: 确保：
1. SSH 用户名和密码正确
2. 远程目录有写权限
3. 防火墙允许 SSH 连接

### Q: 如何同时上传到所有三个更新源？

A: 使用一键发布脚本（需要 Python）：
```bash
pip install paramiko requests
python tools\publish-all.py --channel beta --github-token YOUR_TOKEN
```

## 联系支持

如有问题，请查看：
- `PUBLISH_SYSTEM_OVERVIEW.md` - 完整发布系统说明
- `docs/AUTO_PUBLISH_README.md` - 自动化发布指南
- `docs/PUBLISH_GUIDE.md` - 发布指南

---

**最后更新**: 2026-05-11  
**当前版本**: 217 (1.3.16)  
**VPS 版本**: 200 (1.3.12) - 需要更新
