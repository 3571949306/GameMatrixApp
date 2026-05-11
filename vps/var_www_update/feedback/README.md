# GameCenterApp Feedback VPS Files

上传本目录内容到：

```bash
/var/www/update/feedback/
```

最小部署步骤：

```bash
sudo mkdir -p /var/www/update/feedback
sudo cp feedback_server.py /var/www/update/feedback/
sudo cp gamecenter-feedback.service /etc/systemd/system/
sudo chown -R www-data:www-data /var/www/update/feedback
sudo chmod +x /var/www/update/feedback/feedback_server.py
sudo systemctl daemon-reload
sudo systemctl enable --now gamecenter-feedback
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

上线前请把 `gamecenter-feedback.service` 里的 `GAMECENTER_FEEDBACK_TOKEN` 改成自己的长随机字符串。
