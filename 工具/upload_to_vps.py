#!/usr/bin/env python3
"""
Upload GameCenterApp APK and version.json to VPS servers.

Channels:
  beta    -> HK VPS + US VPS (test users only)
  release -> HK VPS + US VPS + GitHub Releases (all users)

Credentials are loaded from local_private/服务器部署/upload_config_*.json
(that directory is excluded from version control).
"""

from __future__ import annotations

import argparse
import json
import os
import posixpath
import shlex
import stat
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

try:
    import paramiko
except ImportError as exc:
    raise SystemExit(
        "paramiko is required. Install it with: python -m pip install paramiko"
    ) from exc

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_APK = REPO_ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
DEFAULT_VERSION_JSON = REPO_ROOT / "app" / "build" / "outputs" / "apk" / "release" / "version.json"
VPS_CONFIG_DIR = REPO_ROOT / "local_private" / "vps"

summary = {}


def load_vps_configs() -> list[dict[str, Any]]:
    """Load VPS server configs from local_private/服务器部署/upload_config_*.json."""
    if not VPS_CONFIG_DIR.exists():
        raise SystemExit(f"VPS config directory not found: {VPS_CONFIG_DIR}")

    configs = []
    for config_file in sorted(VPS_CONFIG_DIR.glob("upload_config_*.json")):
        if config_file.name.endswith(".template.json"):
            continue
        with open(config_file, "r", encoding="utf-8") as fh:
            data = json.load(fh)
        # Derive server name from filename: upload_config_hk.json -> HK
        stem = config_file.stem  # upload_config_hk
        name = stem.replace("upload_config_", "").upper()
        data.setdefault("name", name)
        configs.append(data)

    if not configs:
        raise SystemExit(f"No upload_config_*.json files found in {VPS_CONFIG_DIR}")
    return configs


def resolve_repo_path(value: str | None) -> Path | None:
    if not value:
        return None
    path = Path(os.path.expanduser(value))
    return path if path.is_absolute() else REPO_ROOT / path


def load_private_key(path: Path, passphrase: str | None):
    for loader in (paramiko.Ed25519Key, paramiko.RSAKey, paramiko.ECDSAKey):
        try:
            return loader.from_private_key_file(str(path), password=passphrase)
        except Exception:
            pass
    raise SystemExit(f"Unable to load SSH key: {path}")


def connect(server: dict[str, Any]):
    host = server["host"]
    port = int(server.get("port", 22))
    user = server.get("user", "root")
    auth_method = server.get("authMethod", "password").lower()
    identity_file = resolve_repo_path(server.get("identityFile"))
    password = server.get("password") or os.environ.get("GAMECENTER_VPS_PASSWORD", "")

    client = paramiko.SSHClient()

    if auth_method == "password":
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    else:
        known_hosts = resolve_repo_path(server.get("knownHostsFile"))
        if known_hosts and known_hosts.exists():
            client.load_host_keys(str(known_hosts))
        client.set_missing_host_key_policy(paramiko.RejectPolicy())

    pkey = None
    if auth_method in ("key", "private_key") and identity_file and identity_file.exists():
        pkey = load_private_key(identity_file, os.environ.get("GAMECENTER_VPS_KEY_PASSPHRASE"))

    if not pkey and not password:
        raise SystemExit(f"No auth configured for {host}")

    client.connect(hostname=host, port=port, username=user, pkey=pkey, password=password,
                   look_for_keys=False, allow_agent=False, timeout=20)
    return client


def run_remote(client, command: str) -> str:
    _stdin, stdout, stderr = client.exec_command(command)
    rc = stdout.channel.recv_exit_status()
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if rc != 0:
        raise SystemExit(f"Remote failed ({rc}): {command}\n{err}")
    return out


def atomic_put(sftp, local_path: Path, remote_dir: str, remote_name: str) -> None:
    remote_final = posixpath.join(remote_dir, remote_name)
    remote_temp = remote_final + ".uploading"
    sftp.put(str(local_path), remote_temp)
    try:
        sftp.posix_rename(remote_temp, remote_final)
    except IOError:
        try:
            sftp.remove(remote_final)
        except IOError:
            pass
        sftp.rename(remote_temp, remote_final)


def cleanup_remote(sftp, remote_dir: str, keep_files: set[str]) -> None:
    """
    清理远程目录中的旧文件。
    
    **重要**：这个函数现在会保留两个通道的文件：
    - beta 通道：app-beta.apk, version-beta.json
    - release 通道：app-release.apk, version-release.json
    
    只删除：
    - 旧的 app-debug.apk
    - 旧的 version.json
    - 正在上传的临时文件（.uploading 后缀）
    """
    protected_patterns = {
        "app-beta.apk",
        "version-beta.json",
        "app-release.apk",
        "version-release.json",
    }
    
    for item in sftp.listdir_attr(remote_dir):
        name = item.filename
        # 保留所有通道的文件和正在上传的临时文件
        if name in protected_patterns or name.endswith(".uploading"):
            continue
        # 只删除旧版本的文件（app-debug.apk, version.json 等）
        if stat.S_ISREG(item.st_mode) and (name.endswith(".apk") or name.endswith(".json")):
            sftp.remove(posixpath.join(remote_dir, name))


def read_version_summary(version_path: Path) -> dict[str, Any]:
    with version_path.open("r", encoding="utf-8") as fh:
        data = json.load(fh)
    return {
        "versionCode": data.get("versionCode"),
        "versionName": data.get("versionName"),
        "channel": data.get("channel"),
        "isBeta": data.get("isBeta"),
    }


def verify_public(public_base_url: str, expected: dict[str, Any], channel: str) -> None:
    suffix = "beta" if channel == "beta" else "release"
    base = public_base_url.rstrip("/")
    version_url = f"{base}/version-{suffix}.json"
    with urllib.request.urlopen(version_url, timeout=20) as resp:
        remote = json.loads(resp.read().decode("utf-8"))
    for key in ("versionCode", "versionName", "channel", "isBeta"):
        if remote.get(key) != expected.get(key):
            raise SystemExit(f"Version mismatch {key}: expected={expected.get(key)!r}, got={remote.get(key)!r}")
    apk_name = f"app-{suffix}.apk"
    req = urllib.request.Request(f"{base}/{apk_name}", method="HEAD")
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            if int(resp.headers.get("Content-Length", "0")) <= 0:
                raise SystemExit(f"{apk_name} empty Content-Length")
    except urllib.error.HTTPError:
        with urllib.request.urlopen(f"{base}/{apk_name}", timeout=20) as resp:
            if int(resp.headers.get("Content-Length", "0")) <= 0:
                raise SystemExit(f"{apk_name} empty Content-Length")


def upload_to_vps(server: dict[str, Any], apk_path: Path, version_path: Path, channel: str, skip_verify: bool) -> bool:
    host = server.get("host", "?")
    remote_dir = server.get("remoteDir", "/var/www/update/app")
    public_base_url = server.get("publicBaseUrl", "").strip()
    name = server.get("name", host)

    remote_apk = f"app-{channel}.apk"
    remote_ver = f"version-{channel}.json"

    print(f"\n{'='*60}")
    print(f"VPS: {name} ({host}) | Remote: {remote_dir}")
    print(f"{'='*60}")

    try:
        client = connect(server)
    except Exception as e:
        print(f"FAILED connect {host}: {e}")
        return False

    try:
        run_remote(client, f"mkdir -p {shlex.quote(remote_dir)}")
        with client.open_sftp() as sftp:
            remote_apk = f"app-{channel}.apk"
            remote_ver = f"version-{channel}.json"
            atomic_put(sftp, apk_path, remote_dir, remote_apk)
            atomic_put(sftp, version_path, remote_dir, remote_ver)
            # 保留两个通道的所有文件，防止误删
            keep = {
                "app-beta.apk",
                "version-beta.json",
                "app-release.apk",
                "version-release.json",
            }
            cleanup_remote(sftp, remote_dir, keep)
        print(f"Uploaded {remote_apk} + {remote_ver}")

        for cmd in server.get("postUploadCommands", []):
            print(f"  Running: {cmd}")
            try:
                run_remote(client, cmd)
            except SystemExit as e:
                print(f"  Warning: {e}")
    except Exception as e:
        print(f"FAILED upload {host}: {e}")
        return False
    finally:
        client.close()

    if public_base_url and not skip_verify:
        try:
            verify_public(public_base_url, summary, channel)
            print(f"Verify OK: {public_base_url}")
        except Exception as e:
            print(f"Warning verify {host}: {e}")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", default=str(DEFAULT_APK))
    parser.add_argument("--version", default=str(DEFAULT_VERSION_JSON))
    parser.add_argument("--channel", choices=["beta", "release"], default="beta")
    parser.add_argument("--skip-verify", action="store_true")
    args = parser.parse_args()

    apk_path = Path(args.apk).resolve()
    version_path = Path(args.version).resolve()
    channel = args.channel

    if not apk_path.exists():
        raise SystemExit(f"Missing APK: {apk_path}")
    if not version_path.exists():
        raise SystemExit(f"Missing version.json: {version_path}")

    global summary
    summary = read_version_summary(version_path)
    print(f"Upload [{channel}]: vc={summary['versionCode']}, v={summary['versionName']}, "
          f"size={apk_path.stat().st_size} bytes")

    vps_servers = load_vps_configs()
    print(f"Target VPS: {len(vps_servers)} server(s)")
    for s in vps_servers:
        print(f"  {s['name']}: {s['host']}")

    ok = 0
    for s in vps_servers:
        if upload_to_vps(s, apk_path, version_path, channel, args.skip_verify):
            ok += 1

    print(f"\nResult: {ok}/{len(vps_servers)} VPS uploaded")
    if ok == 0:
        raise SystemExit("All uploads failed!")
    return 0


if __name__ == "__main__":
    sys.exit(main())

