#!/bin/bash
# 完全重置 x-ui 面板 - 恢复出厂设置，账号密码设为 123 / 123

echo "=== 1. 停止 x-ui ==="
systemctl stop x-ui

echo "=== 2. 删除所有用户数据 ==="
sqlite3 /etc/x-ui/x-ui.db "DELETE FROM users;"
sqlite3 /etc/x-ui/x-ui.db "DELETE FROM settings;"

echo "=== 3. 重新插入默认设置 ==="
sqlite3 /etc/x-ui/x-ui.db "INSERT INTO settings (key, value) VALUES ('webPort', '41370');"
sqlite3 /etc/x-ui/x-ui.db "INSERT INTO settings (key, value) VALUES ('webBasePath', '/');"
sqlite3 /etc/x-ui/x-ui.db "INSERT INTO settings (key, value) VALUES ('webCertFile', '/usr/local/x-ui/bin/x-ui.pem');"
sqlite3 /etc/x-ui/x-ui.db "INSERT INTO settings (key, value) VALUES ('webKeyFile', '/usr/local/x-ui/bin/x-ui.key');"

echo "=== 4. 确保 Cloudflare 证书存在 ==="
cp /etc/nginx/ssl/cloudflare_origin.pem /usr/local/x-ui/bin/x-ui.pem
cp /etc/nginx/ssl/cloudflare_origin.key /usr/local/x-ui/bin/x-ui.key
chmod 600 /usr/local/x-ui/bin/x-ui.*

echo "=== 5. 启动 x-ui ==="
systemctl start x-ui
sleep 3

echo "=== 6. 设置新用户名和密码 ==="
x-ui settings -u 123 -p 123 2>&1

echo ""
echo "=== 7. 最终状态 ==="
x-ui settings 2>&1
