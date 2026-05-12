#!/usr/bin/env python3
"""
Fix version-beta.json on US VPS to point to latest stable release.
"""

import json
import sys
import io
import tempfile

try:
    import paramiko
except ImportError:
    print("paramiko is required. Install with: pip install paramiko")
    sys.exit(1)

# US VPS configuration
VPS_HOST = "38.165.22.161"
VPS_PORT = 22
VPS_USER = "root"
VPS_KEY_FILE = "C:\\Users\\tcw\\.ssh\\id_ed25519"
REMOTE_DIR = "/var/www/update/app"

try:
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(VPS_HOST, port=VPS_PORT, username=VPS_USER, key_filename=VPS_KEY_FILE)
    print(f"Connected to US VPS: {VPS_HOST}:{VPS_PORT}")
    
    # Download version-release.json
    with client.open_sftp() as sftp:
        remote_path = f"{REMOTE_DIR}/version-release.json"
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
        sftp.putfo(io.BytesIO(json.dumps(beta_data, indent=2, ensure_ascii=False).encode('utf-8')), remote_path)
        print(f"Uploaded {remote_path}")
    
    client.close()
    print("Done!")
    
except Exception as e:
    print(f"Error: {e}")
    sys.exit(1)
