# GameMatrixApp Feedback VPS Files

上传本目录内容到：

```bash
/var/www/update/feedback/
```

最小部署步骤：

```bash
sudo mkdir -p /var/www/update/feedback
sudo cp feedback_server.py /var/www/update/feedback/
sudo cp gamematrix-feedback.service /etc/systemd/system/
sudo chown -R www-data:www-data /var/www/update/feedback
sudo chmod +x /var/www/update/feedback/feedback_server.py
sudo systemctl daemon-reload
sudo systemctl enable --now gamematrix-feedback
```

推荐直接使用上一层目录的 `nginx-update.conf`，它同时包含更新服务和反馈服务的 80 端口代理规则。

如果只手动处理反馈接口，把 `nginx-feedback-location.conf` 里的三个 `location` 加到当前 `<YOUR_DOMAIN>` 的 80 端口 server 块中，然后：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

测试：

```bash
curl http://127.0.0.1:9011/api/feedback/health
curl -X POST http://<YOUR_DOMAIN>/api/feedback \
  -H 'Content-Type: application/json' \
  -d '{"message":"test","appVersion":"1.3.3","versionCode":137}'
```

查看反馈：

```bash
tail -f /var/www/update/feedback/feedback.log
sqlite3 /var/www/update/feedback/feedback.sqlite 'select * from feedback order by id desc limit 5;'
ls -lah /var/www/update/feedback/Bug反馈
ls -lah /var/www/update/feedback/功能建议
ls -lah /var/www/update/app/反馈/bug
ls -lah /var/www/update/app/反馈/功能
```

分类目录内的文件名格式：

```text
000006_功能建议_2026-05-08_15-59-20_反馈摘要.txt
000006_功能建议_2026-05-08_15-59-20_反馈摘要.json
```

同时会把方便查看的 `.txt` 副本镜像到：

```text
/var/www/update/app/反馈/bug/
/var/www/update/app/反馈/功能/
```

App 自动上传脚本只清理 `/var/www/update/app/` 目录下的普通 `.apk` / `.json` 文件，不会删除 `反馈` 子目录。

网页查看：

```text
http://<YOUR_DOMAIN>/admin/feedback?token=change-this-token
```

上线前请把 `gamematrix-feedback.service` 里的 `gamematrix_FEEDBACK_TOKEN` 改成自己的长随机字符串。
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
- 最新 GitHub Actions `CI/CD Pipeline` 已通过；正式签名、R8 混淆和 服务器部署/GitHub Release 发布仍以本机发布流程为准。

