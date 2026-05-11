#!/bin/bash
# 1. Copy Cloudflare Origin CA cert to x-ui
cp /etc/nginx/ssl/cloudflare_origin.pem /usr/local/x-ui/bin/x-ui.pem
cp /etc/nginx/ssl/cloudflare_origin.key /usr/local/x-ui/bin/x-ui.key
chmod 600 /usr/local/x-ui/bin/x-ui.*

echo "=== Certificate copied ==="
openssl x509 -in /usr/local/x-ui/bin/x-ui.pem -noout -subject

# 2. Show current settings
echo "=== current webBasePath ==="
sqlite3 /etc/x-ui/x-ui.db "SELECT key,value FROM settings WHERE key='webBasePath';"
echo "=== current port ==="
sqlite3 /etc/x-ui/x-ui.db "SELECT key,value FROM settings WHERE key='port';"
echo "=== current webCertFile ==="
sqlite3 /etc/x-ui/x-ui.db "SELECT key,value FROM settings WHERE key='webCertFile';"
echo "=== current webKeyFile ==="
sqlite3 /etc/x-ui/x-ui.db "SELECT key,value FROM settings WHERE key='webKeyFile';"

# 3. Remove web base path suffix
sqlite3 /etc/x-ui/x-ui.db "UPDATE settings SET value = '' WHERE key = 'webBasePath';"
echo "=== webBasePath cleared ==="
sqlite3 /etc/x-ui/x-ui.db "SELECT key,value FROM settings WHERE key='webBasePath';"

# 4. Set x-ui to use Cloudflare Origin CA cert
sqlite3 /etc/x-ui/x-ui.db "INSERT OR REPLACE INTO settings (key, value) VALUES ('webCertFile', '/usr/local/x-ui/bin/x-ui.pem');"
sqlite3 /etc/x-ui/x-ui.db "INSERT OR REPLACE INTO settings (key, value) VALUES ('webKeyFile', '/usr/local/x-ui/bin/x-ui.key');"
echo "=== cert paths set ==="
sqlite3 /etc/x-ui/x-ui.db "SELECT key,value FROM settings WHERE key LIKE 'web%';"

# 5. Restart x-ui
systemctl restart x-ui
sleep 2

echo "=== x-ui restarted ==="
systemctl status x-ui --no-pager | head -10
x-ui settings 2>&1
