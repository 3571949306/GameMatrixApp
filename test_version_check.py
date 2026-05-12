#!/usr/bin/env python3
"""
Simulate version check for v222 beta user.
"""

import json
import urllib.request

# Simulate local version (v222 beta)
local_version_code = 222
local_version_name = "1.3.16 beta"
local_channel = "beta"
accept_beta = True

print(f"=== 本地版本 ===")
print(f"versionCode: {local_version_code}")
print(f"versionName: {local_version_name}")
print(f"channel: {local_channel}")
print(f"acceptBeta: {accept_beta}")
print()

# Check version-beta.json
print("=== 检查 version-beta.json ===")
try:
    with urllib.request.urlopen("https://hk-update.tcp0053.shop/version-beta.json", timeout=10) as resp:
        beta_data = json.load(resp)
    
    print(f"Remote versionCode: {beta_data['versionCode']}")
    print(f"Remote versionName: {beta_data['versionName']}")
    print(f"Remote channel: {beta_data['channel']}")
    print(f"Remote isBeta: {beta_data['isBeta']}")
    
    # Simulate shouldOfferUpdate logic
    has_update = False
    if beta_data['versionCode'] > local_version_code:
        has_update = True
        print(f"\n✅ versionCode {beta_data['versionCode']} > {local_version_code} → 有更新")
    elif beta_data['versionCode'] == local_version_code:
        release_changed = beta_data['versionName'] != local_version_name
        if not beta_data['isBeta'] and local_channel == 'beta' and release_changed:
            has_update = True
            print(f"\n✅ 同版本但 releaseChanged={release_changed} → 有更新")
        elif beta_data['isBeta'] and accept_beta and release_changed:
            has_update = True
            print(f"\n✅ 同版本 beta 但 releaseChanged={release_changed} → 有更新")
        else:
            print(f"\n❌ 同版本，无更新")
    else:
        print(f"\n❌ versionCode {beta_data['versionCode']} < {local_version_code} → 无更新")
    
    print(f"\nhasUpdate: {has_update}")
    
    if not has_update:
        # Check version-release.json
        print("\n=== 检查 version-release.json ===")
        with urllib.request.urlopen("https://hk-update.tcp0053.shop/version-release.json", timeout=10) as resp:
            release_data = json.load(resp)
        
        print(f"Remote versionCode: {release_data['versionCode']}")
        print(f"Remote versionName: {release_data['versionName']}")
        
        if release_data['versionCode'] > local_version_code:
            print(f"\n✅ versionCode {release_data['versionCode']} > {local_version_code} → 有更新")
        else:
            print(f"\n❌ versionCode {release_data['versionCode']} <= {local_version_code} → 无更新")
    
except Exception as e:
    print(f"Error: {e}")
