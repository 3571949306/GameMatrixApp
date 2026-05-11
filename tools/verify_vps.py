import paramiko
import json
import hashlib

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('38.165.22.161', 22, 'root', key_filename=r'C:\Users\tcw\.ssh\id_ed25519')

# 检查 version-beta.json
sftp = ssh.open_sftp()
with sftp.open('/var/www/update/app/version-beta.json') as f:
    content = f.read().decode()
    data = json.loads(content)
    print(f"version-beta.json: versionCode={data['versionCode']}, versionName={data['versionName']}")

# 检查 APK MD5
print("\nAPK file info:")
stdin, stdout, stderr = ssh.exec_command('md5sum /var/www/update/app/app-beta.apk && ls -lh /var/www/update/app/app-beta.apk')
print(stdout.read().decode())

# 通过 HTTP 本地检查
stdin2, stdout2, stderr2 = ssh.exec_command('curl -s http://127.0.0.1:9000/version-beta.json | python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"HTTP 9000: versionCode={d[\\\"versionCode\\\"]}\")"')
print(stdout2.read().decode())

# 通过 HTTPS 本地检查
stdin3, stdout3, stderr3 = ssh.exec_command('curl -s https://127.0.0.1:1443/version-beta.json --insecure | python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"HTTPS 1443: versionCode={d[\\\"versionCode\\\"]}\")"')
print(stdout3.read().decode())

# 检查 update_server.py 进程
stdin4, stdout4, stderr4 = ssh.exec_command('ps aux | grep "server.py" | grep -v grep')
print("\nupdate_server process:")
print(stdout4.read().decode())

sftp.close()
ssh.close()
