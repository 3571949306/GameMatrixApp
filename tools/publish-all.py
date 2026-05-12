#!/usr/bin/env python3
"""
一键上传 GameCenterApp APK 到所有更新源：
1. 香港 VPS (HK VPS)
2. 美国 VPS (US VPS)  
3. GitHub Releases

使用方法:
    python publish-all.py --channel beta --github-token YOUR_TOKEN
    python publish-all.py --channel release --github-token YOUR_TOKEN
"""

import argparse
import json
import os
import sys
import subprocess
from pathlib import Path
from typing import List, Dict, Any

# 尝试导入 paramiko (VPS 上传需要)
try:
    import paramiko
    HAS_PARAMIKO = True
except ImportError:
    HAS_PARAMIKO = False
    print("警告：paramiko 未安装，VPS 上传功能不可用。安装：pip install paramiko")

# 尝试导入 requests (GitHub API 需要)
try:
    import requests
    HAS_REQUESTS = True
except ImportError:
    HAS_REQUESTS = False
    print("警告：requests 未安装，GitHub Releases 上传功能不可用。安装：pip install requests")

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_APK = REPO_ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release-unsigned.apk"
DEFAULT_VERSION_JSON = REPO_ROOT / "app" / "build" / "outputs" / "version.json"

# ============ 配置 ============

VPS_CONFIG_DIR = REPO_ROOT / "local_private" / "vps"

# 更新源列表
UPDATE_SOURCES = {
    "hk_vps": {
        "name": "香港 VPS",
        "url": "https://hk-update.tcp0053.shop",
        "type": "vps",
        "config_file": "upload_config_hk.json"
    },
    "us_vps": {
        "name": "美国 VPS",
        "url": "https://tcp0053.shop:1443",
        "type": "vps",
        "config_file": "upload_config_us.json"
    },
    "github": {
        "name": "GitHub Releases",
        "url": "https://github.com/3571949306/GameCenterApp/releases",
        "type": "github",
        "requires_token": True
    }
}


def load_vps_config(config_file: str) -> Dict[str, Any]:
    """加载单个 VPS 配置文件"""
    config_path = VPS_CONFIG_DIR / config_file
    if not config_path.exists():
        print(f"警告：VPS 配置文件不存在：{config_path}")
        return {}
    
    with open(config_path, "r", encoding="utf-8") as f:
        return json.load(f)


def upload_to_vps(config: Dict[str, Any], apk_path: Path, version_path: Path, 
                  channel: str, skip_verify: bool = False) -> bool:
    """上传到单个 VPS 服务器"""
    if not HAS_PARAMIKO:
        print(f"  ✗ 跳过 {config.get('host', '?')}: paramiko 未安装")
        return False
    
    from tools.upload_to_vps import connect, atomic_put, run_remote, read_version_summary
    
    host = config.get("host", "?")
    remote_dir = config.get("remoteDir", "/var/www/update/app")
    name = config.get("name", host)
    
    remote_apk = f"app-{channel}.apk"
    remote_ver = f"version-{channel}.json"
    
    print(f"\n  上传到 {name} ({host})...")
    
    try:
        client = connect(config)
    except Exception as e:
        print(f"  ✗ 连接失败：{e}")
        return False
    
    try:
        # 创建远程目录
        run_remote(client, f"mkdir -p {remote_dir}")
        
        # 上传文件
        with client.open_sftp() as sftp:
            atomic_put(sftp, apk_path, remote_dir, remote_apk)
            atomic_put(sftp, version_path, remote_dir, remote_ver)
            print(f"  ✓ 已上传 {remote_apk} + {remote_ver}")
        
        # 执行后处理命令
        for cmd in config.get("postUploadCommands", []):
            print(f"  执行：{cmd}")
            try:
                run_remote(client, cmd)
            except Exception as e:
                print(f"  警告：{e}")
        
        return True
    except Exception as e:
        print(f"  ✗ 上传失败：{e}")
        return False
    finally:
        client.close()


def upload_to_github_release(apk_path: Path, version_path: Path, 
                              channel: str, github_token: str) -> bool:
    """上传到 GitHub Releases"""
    if not HAS_REQUESTS:
        print(f"  ✗ 跳过 GitHub Releases: requests 未安装")
        return False
    
    if not github_token:
        print(f"  ✗ 跳过 GitHub Releases: 未提供 GitHub Token")
        return False
    
    # 读取版本信息
    with open(version_path, "r", encoding="utf-8") as f:
        version_data = json.load(f)
    
    version_name = version_data.get("versionName", "unknown")
    version_code = version_data.get("versionCode", 0)
    is_beta = channel == "beta"
    
    tag_name = f"v{version_name}" if not is_beta else f"v{version_name}-beta"
    release_name = f"GameCenterApp v{version_name}"
    if is_beta:
        release_name += " (Beta)"
    
    print(f"\n  上传到 GitHub Releases...")
    print(f"  Tag: {tag_name}")
    print(f"  Release: {release_name}")
    
    headers = {
        "Authorization": f"token {github_token}",
        "Accept": "application/vnd.github.v3+json"
    }
    
    # 1. 检查 Release 是否已存在
    api_url = f"https://api.github.com/repos/3571949306/GameCenterApp/releases/tags/{tag_name}"
    response = requests.get(api_url, headers=headers)
    
    if response.status_code == 200:
        # Release 已存在，获取 upload_url
        release_data = response.json()
        release_id = release_data["id"]
        upload_url = release_data["upload_url"].split("{?")[0]
        print(f"  使用现有 Release (ID: {release_id})")
    else:
        # 创建新 Release
        release_payload = {
            "tag_name": tag_name,
            "name": release_name,
            "body": f"GameCenterApp {release_name}\n\n更新内容详见 CHANGELOG.md",
            "draft": False,
            "prerelease": is_beta
        }
        
        create_url = "https://api.github.com/repos/3571949306/GameCenterApp/releases"
        response = requests.post(create_url, headers=headers, json=release_payload)
        
        if response.status_code not in (200, 201):
            print(f"  ✗ 创建 Release 失败：{response.text}")
            return False
        
        release_data = response.json()
        upload_url = release_data["upload_url"].split("{?")[0]
        print(f"  ✓ 创建 Release 成功 (ID: {release_data['id']})")
    
    # 2. 上传 APK 文件
    apk_filename = f"GameCenterApp-v{version_name}.apk"
    upload_params = {"name": apk_filename}
    
    with open(apk_path, "rb") as f:
        apk_data = f.read()
    
    upload_headers = headers.copy()
    upload_headers["Content-Type"] = "application/vnd.android.package-archive"
    
    response = requests.post(
        upload_url,
        headers=upload_headers,
        params=upload_params,
        data=apk_data
    )
    
    if response.status_code not in (200, 201):
        print(f"  ✗ 上传 APK 失败：{response.text}")
        return False
    
    print(f"  ✓ APK 上传成功")
    
    # 3. 上传 version.json (可选)
    # version_json_filename = f"version-{channel}.json"
    # ... 类似上传 ...
    
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="一键上传 APK 到所有更新源")
    parser.add_argument("--apk", default=str(DEFAULT_APK), help="APK 文件路径")
    parser.add_argument("--version", default=str(DEFAULT_VERSION_JSON), help="version.json 路径")
    parser.add_argument("--channel", choices=["beta", "release"], default="beta", help="发布渠道")
    parser.add_argument("--github-token", default="", help="GitHub Token")
    parser.add_argument("--skip-verify", action="store_true", help="跳过验证")
    parser.add_argument("--sources", nargs="+", choices=list(UPDATE_SOURCES.keys()), 
                       default=list(UPDATE_SOURCES.keys()), help="要上传的更新源")
    
    args = parser.parse_args()
    
    apk_path = Path(args.apk).resolve()
    version_path = Path(args.version).resolve()
    channel = args.channel
    
    # 验证文件存在
    if not apk_path.exists():
        print(f"错误：APK 文件不存在：{apk_path}")
        return 1
    if not version_path.exists():
        print(f"错误：version.json 不存在：{version_path}")
        return 1
    
    # 读取版本信息
    with open(version_path, "r", encoding="utf-8") as f:
        version_data = json.load(f)
    
    print("=" * 70)
    print("  GameCenterApp 一键发布工具")
    print("=" * 70)
    print(f"\n版本信息:")
    print(f"  版本号：{version_data.get('versionName', '?')}")
    print(f"  版本代码：{version_data.get('versionCode', 0)}")
    print(f"  渠道：{channel}")
    print(f"  APK 大小：{apk_path.stat().st_size / 1024 / 1024:.2f} MB")
    print(f"\n目标更新源:")
    
    for source_id in args.sources:
        source = UPDATE_SOURCES[source_id]
        print(f"  - {source['name']}: {source['url']}")
    
    print("\n" + "=" * 70)
    
    # 执行上传
    results = {}
    
    for source_id in args.sources:
        source = UPDATE_SOURCES[source_id]
        
        if source["type"] == "vps":
            config = load_vps_config(source["config_file"])
            if config:
                success = upload_to_vps(config, apk_path, version_path, channel, args.skip_verify)
                results[source_id] = success
            else:
                print(f"\n  跳过 {source['name']}: 配置文件缺失")
                results[source_id] = False
        
        elif source["type"] == "github":
            success = upload_to_github_release(apk_path, version_path, channel, args.github_token)
            results[source_id] = success
    
    # 汇总结果
    print("\n" + "=" * 70)
    print("  发布结果汇总")
    print("=" * 70)
    
    for source_id, success in results.items():
        source = UPDATE_SOURCES[source_id]
        status = "✓ 成功" if success else "✗ 失败"
        print(f"  {source['name']}: {status}")
    
    total_success = sum(results.values())
    total_sources = len(results)
    
    print(f"\n总计：{total_success}/{total_sources} 个更新源上传成功")
    print("=" * 70)
    
    if total_success == 0:
        print("\n错误：所有更新源上传失败！")
        return 1
    elif total_success < total_sources:
        print("\n警告：部分更新源上传失败")
        return 0
    else:
        print("\n成功：所有更新源上传完成！")
        return 0


if __name__ == "__main__":
    sys.exit(main())
