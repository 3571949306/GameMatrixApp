import paramiko
import time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('38.165.22.161', 22, 'root', key_filename=r'C:\Users\tcw\.ssh\id_ed25519')

# 检查 1443 端口的 nginx 配置
stdin, stdout, stderr = ssh.exec_command('grep -r "1443" /etc/nginx/ --include="*.conf"')
nginx_conf = stdout.read().decode()
print("=== Nginx 1443 config ===")
print(nginx_conf)

# 检查 proxy cache
stdin2, stdout2, stderr2 = ssh.exec_command('grep -r "proxy_cache" /etc/nginx/ --include="*.conf"')
cache_conf = stdout2.read().decode()
print("\n=== Proxy cache config ===")
print(cache_conf)

# 检查是否有缓存目录
stdin3, stdout3, stderr3 = ssh.exec_command('ls -la /var/cache/nginx/ 2>/dev/null || echo "no cache dir"')
cache_dir = stdout3.read().decode()
print("\n=== Cache dir ===")
print(cache_dir)

# 重启 nginx
stdin4, stdout4, stderr4 = ssh.exec_command('nginx -s reload && echo "nginx reloaded"')
reload_out = stdout4.read().decode()
print("\n=== Reload result ===")
print(reload_out)

# 等待后验证
time.sleep(2)
stdin5, stdout5, stderr5 = ssh.exec_command('curl -s https://127.0.0.1:1443/version-beta.json --insecure | head -5')
verify = stdout5.read().decode()
print("\n=== After reload (curl 127.0.0.1:1443) ===")
print(verify)

ssh.close()
