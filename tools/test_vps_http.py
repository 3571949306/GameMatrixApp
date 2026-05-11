import paramiko
import json

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('38.165.22.161', 22, 'root', key_filename=r'C:\Users\tcw\.ssh\id_ed25519')

# Direct HTTP test
print("=== curl http://127.0.0.1:9000/version-beta.json ===")
stdin, stdout, stderr = ssh.exec_command('curl -s -D - http://127.0.0.1:9000/version-beta.json')
output = stdout.read().decode()
print(output[:500])

print("\n=== curl https://127.0.0.1:1443/version-beta.json ===")
stdin2, stdout2, stderr2 = ssh.exec_command('curl -s -k https://127.0.0.1:1443/version-beta.json')
output2 = stdout2.read().decode()
print(output2[:500])

# Check the actual server.py
print("\n=== Check server.py file ===")
stdin3, stdout3, stderr3 = ssh.exec_command('head -30 /var/www/update/server.py')
print(stdout3.read().decode())

ssh.close()
