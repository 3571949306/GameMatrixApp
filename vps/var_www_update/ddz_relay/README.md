# 斗地主 Beta 云联机中转服务

这个服务只负责斗地主 Beta 的房间码中转，不保存牌局数据到磁盘。房主创建 6 位房间码，客户端通过 `/api/ddz-relay/` 长轮询收发消息，避免普通家庭网络、运营商 NAT、Android 高版本后台限制导致的直连失败。

部署路径：

```bash
sudo mkdir -p /var/www/update/ddz_relay
sudo cp ddz_relay_server.py /var/www/update/ddz_relay/
sudo cp gamematrix-ddz-relay.service /etc/systemd/system/
sudo chown -R www-data:www-data /var/www/update/ddz_relay
sudo chmod +x /var/www/update/ddz_relay/ddz_relay_server.py
sudo systemctl daemon-reload
sudo systemctl enable --now gamematrix-ddz-relay
```

nginx 使用上一层 `nginx-update.conf`，它会把 `/api/ddz-relay/` 转发到本机 `127.0.0.1:9012`。

验证：

```bash
curl http://127.0.0.1:9012/api/ddz-relay/health
curl http://<YOUR_DOMAIN>/api/ddz-relay/health
```
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
- `.gitignore` 的 `data/` 规则已收窄为 `/data/`，防止误忽略 `app/src/main/java/com/gamematrix/app/ai/data/` 源码。
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 VPS/GitHub Release 发布仍以本机发布流程为准。
