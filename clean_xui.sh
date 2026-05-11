#!/bin/bash
# Remove old cert entries and keep only the new ones
sqlite3 /etc/x-ui/x-ui.db "DELETE FROM settings WHERE key='webCertFile' AND value LIKE '/root/cert%';"
sqlite3 /etc/x-ui/x-ui.db "DELETE FROM settings WHERE key='webKeyFile' AND value LIKE '/root/cert%';"

echo "=== remaining settings ==="
sqlite3 /etc/x-ui/x-ui.db "SELECT key,value FROM settings WHERE key LIKE 'web%';"

echo "=== restart ==="
systemctl restart x-ui
sleep 2

echo "=== final status ==="
x-ui settings 2>&1
echo "=== test direct x-ui ==="
curl -sk https://127.0.0.1:41370/ -o /dev/null -w '%{http_code}\n'
