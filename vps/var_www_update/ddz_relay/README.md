# 斗地主 Beta 云联机中转服务

这个服务只负责斗地主 Beta 的房间码中转，不保存牌局数据到磁盘。房主创建 6 位房间码，客户端通过 `/api/ddz-relay/` 长轮询收发消息，避免普通家庭网络、运营商 NAT、Android 高版本后台限制导致的直连失败。

部署路径：

```bash
sudo mkdir -p /var/www/update/ddz_relay
sudo cp ddz_relay_server.py /var/www/update/ddz_relay/
sudo cp gamecenter-ddz-relay.service /etc/systemd/system/
sudo chown -R www-data:www-data /var/www/update/ddz_relay
sudo chmod +x /var/www/update/ddz_relay/ddz_relay_server.py
sudo systemctl daemon-reload
sudo systemctl enable --now gamecenter-ddz-relay
```

nginx 使用上一层 `nginx-update.conf`，它会把 `/api/ddz-relay/` 转发到本机 `127.0.0.1:9012`。

验证：

```bash
curl http://127.0.0.1:9012/api/ddz-relay/health
curl http://<YOUR_DOMAIN>/api/ddz-relay/health
```
