#!/usr/bin/env python3
"""
Fix version-beta.json on VPS to point to latest stable release.
This ensures beta users (v222) can see the stable update (v223).
"""

import json
import sys
import io
import os

try:
    import paramiko
except ImportError:
    print("paramiko is required. Install with: pip install paramiko")
    sys.exit(1)

# VPS configuration
VPS_HOST = "149.104.29.181"
VPS_USER = "root"
VPS_PASSWORD = os.environ.get("GAMECENTER_VPS_PASSWORD", "")
REMOTE_DIR = "/var/www/update/app"

if not VPS_PASSWORD:
    print("Set GAMECENTER_VPS_PASSWORD before running this legacy helper.")
    sys.exit(1)

# Connect to VPS and download version-release.json
try:
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(VPS_HOST, username=VPS_USER, password=VPS_PASSWORD)
    print(f"Connected to VPS: {VPS_HOST}")
    
    # Download version-release.json
    with client.open_sftp() as sftp:
        remote_path = f"{REMOTE_DIR}/version-release.json"
        import tempfile
        local_file = tempfile.NamedTemporaryFile(delete=False)
        sftp.get(remote_path, local_file.name)
        local_file.close()
        with open(local_file.name, 'r', encoding='utf-8') as f:
            release_data = json.load(f)
        print(f"Got version-release.json: vc={release_data['versionCode']}, vn={release_data['versionName']}")
    
    # Create version-beta.json
    beta_data = release_data.copy()
    beta_data['channel'] = 'beta'
    beta_data['isBeta'] = True
    beta_data['changelog'] = "🎉 正式版已发布！建议更新到正式版。\n\n" + release_data.get('changelog', '')
    
    print(f"Created version-beta.json: vc={beta_data['versionCode']}, channel={beta_data['channel']}")
    
    # Upload version-beta.json
    with client.open_sftp() as sftp:
        remote_path = f"{REMOTE_DIR}/version-beta.json"
        import io
        sftp.putfo(io.BytesIO(json.dumps(beta_data, indent=2, ensure_ascii=False).encode('utf-8')), remote_path)
        print(f"Uploaded {remote_path}")
    
    # Restart service
    stdin, stdout, stderr = client.exec_command("systemctl restart gamecenter-update")
    exit_code = stdout.channel.recv_exit_status()
    if exit_code == 0:
        print("Service restarted successfully")
    else:
        print(f"Service restart failed with code {exit_code}")
    
    client.close()
    print("Done!")
    
except Exception as e:
    print(f"Error: {e}")
    sys.exit(1)
