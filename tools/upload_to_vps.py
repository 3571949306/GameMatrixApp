#!/usr/bin/env python3
"""Upload GameMatrix release artifacts and module-store files to the update VPS."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import posixpath
import socket
import sys
import urllib.error
import urllib.request
from pathlib import Path

try:
    import paramiko
    from paramiko.hostkeys import HostKeyEntry
except ImportError as exc:  # pragma: no cover - environment check
    raise SystemExit("paramiko is required: python -m pip install paramiko") from exc


DEFAULT_CONFIG = (
    Path("local_private")
    / "\u670d\u52a1\u5668\u90e8\u7f72"
    / "upload_config_hk.json"
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Upload APK, version.json, modules.json, and module APKs to VPS."
    )
    parser.add_argument("--config", default=str(DEFAULT_CONFIG), help="VPS upload config JSON")
    parser.add_argument("--apk", help="Built app-release.apk path")
    parser.add_argument("--version", help="Generated version.json path")
    parser.add_argument("--channel", default="beta", help="beta, release, or stable")
    parser.add_argument("--modules-json", help="Module catalog JSON to publish")
    parser.add_argument("--module-dir", help="Directory containing module APK files")
    parser.add_argument(
        "--store-ui",
        help="store-ui.json path to publish alongside modules.json (module store UI config)",
    )
    parser.add_argument(
        "--module-remote-dir",
        help="Remote directory served by /modules.json and /modules/*.apk",
    )
    parser.add_argument(
        "--module-apk",
        action="append",
        default=[],
        help="Additional module APK path; may be repeated",
    )
    parser.add_argument("--dry-run", action="store_true", help="Print planned uploads only")
    parser.add_argument("--skip-verify", action="store_true", help="Skip public HTTP checks")
    return parser.parse_args()


def load_config(path: Path) -> dict:
    if not path.exists():
        raise FileNotFoundError(f"config not found: {path}")
    with path.open("r", encoding="utf-8-sig") as fh:
        cfg = json.load(fh)
    for key in ("host", "port", "user", "remoteDir", "publicBaseUrl"):
        if not cfg.get(key):
            raise ValueError(f"missing config key: {key}")
    return cfg


def channel_name(raw: str) -> str:
    value = (raw or "beta").strip().lower()
    if value in {"stable", "release", "formal", "prod", "production"}:
        return "release"
    return "beta"


def remote_join(*parts: str) -> str:
    cleaned = [p.strip("/") for p in parts if p]
    if not cleaned:
        return "/"
    prefix = "/" if parts[0].startswith("/") else ""
    return prefix + posixpath.join(*cleaned)


def collect_uploads(args: argparse.Namespace, cfg: dict) -> list[tuple[Path, str, str]]:
    channel = channel_name(args.channel)
    app_name = "app-release.apk" if channel == "release" else "app-beta.apk"
    version_name = "version-release.json" if channel == "release" else "version-beta.json"
    version_code = ""
    if args.version:
        try:
            version_code = str(json.loads(Path(args.version).read_text(encoding="utf-8-sig")).get("versionCode") or "")
        except Exception:
            version_code = ""
    app_public_path = app_name + (f"?v={version_code}" if version_code else "")
    remote_dir = cfg["remoteDir"].rstrip("/")
    default_module_remote_dir = (
        "/var/www/modules"
        if remote_dir.startswith("/var/www/update/")
        else remote_join(posixpath.dirname(remote_dir), "modules")
    )
    module_remote_dir = (
        args.module_remote_dir
        or cfg.get("moduleRemoteDir")
        or default_module_remote_dir
    ).rstrip("/")
    compatibility_version_public_path = (
        "version.json" if channel == "release" else "version.json?acceptBeta=true"
    )

    uploads: list[tuple[Path, str, str]] = []
    if args.apk:
        uploads.append((Path(args.apk), remote_join(remote_dir, app_name), app_public_path))
    if args.version:
        uploads.append((Path(args.version), remote_join(remote_dir, version_name), version_name))
        uploads.append((Path(args.version), remote_join(remote_dir, "version.json"), compatibility_version_public_path))

    if args.modules_json:
        uploads.append(
            (Path(args.modules_json), remote_join(module_remote_dir, "modules.json"), "modules.json")
        )

    if args.store_ui:
        uploads.append(
            (Path(args.store_ui), remote_join(module_remote_dir, "store-ui.json"), "store-ui.json")
        )

    module_files: list[Path] = []
    if args.module_dir:
        module_dir = Path(args.module_dir)
        if module_dir.exists():
            module_files.extend(sorted(module_dir.glob("*.apk")))
        else:
            raise FileNotFoundError(f"module dir not found: {module_dir}")

    module_files.extend(Path(p) for p in args.module_apk)
    for module_apk in module_files:
        uploads.append(
            (
                module_apk,
                remote_join(module_remote_dir, module_apk.name),
                "modules/" + module_apk.name,
            )
        )

    missing = [str(src) for src, _, _ in uploads if not src.exists()]
    if missing:
        raise FileNotFoundError("missing upload source(s): " + ", ".join(missing))
    return uploads


def load_known_hosts_lenient(client: paramiko.SSHClient, path: str) -> int:
    """Load valid host keys while ignoring unrelated malformed historical lines."""
    valid_entries = 0
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            try:
                entry = HostKeyEntry.from_line(line.strip())
            except Exception:
                continue
            if entry is None:
                continue
            for hostname in entry.hostnames:
                client.get_host_keys().add(hostname, entry.key.get_name(), entry.key)
                valid_entries += 1
    if valid_entries == 0:
        raise RuntimeError(f"no valid host keys found in: {path}")
    return valid_entries


def add_trusted_port_alias(client: paramiko.SSHClient, host: str, port: int) -> None:
    """Bind a non-default SSH port only when it presents the trusted host key."""
    if port == 22:
        return
    port_label = f"[{host}]:{port}"
    sock = socket.create_connection((host, port), timeout=15)
    transport = paramiko.Transport(sock)
    try:
        transport.start_client(timeout=15)
        remote_key = transport.get_remote_server_key()
    finally:
        transport.close()
    host_keys = client.get_host_keys()
    if not (host_keys.check(port_label, remote_key) or host_keys.check(host, remote_key)):
        raise RuntimeError(
            f"SSH host key for {port_label} does not match the trusted host record"
        )
    host_keys.add(port_label, remote_key.get_name(), remote_key)


def connect(cfg: dict) -> paramiko.SSHClient:
    client = paramiko.SSHClient()
    # 安全性：使用 RejectPolicy 替代 AutoAddPolicy，防止中间人攻击
    # 修复 GitHub Code Scanning alert #32 (py/paramiko-missing-host-key-validation)
    # 已知主机密钥从以下来源加载（按优先级）：
    #   1. cfg["knownHostsFile"] 显式指定
    #   2. 环境变量 UPLOAD_KNOWN_HOSTS_FILE
    #   3. 默认 ~/.ssh/known_hosts
    # 首次连接需先执行: ssh-keyscan -H <host> >> ~/.ssh/known_hosts
    client.set_missing_host_key_policy(paramiko.RejectPolicy())

    known_hosts_file = (
        cfg.get("knownHostsFile")
        or os.environ.get("UPLOAD_KNOWN_HOSTS_FILE")
        or os.path.expanduser("~/.ssh/known_hosts")
    )
    if os.path.exists(known_hosts_file):
        load_known_hosts_lenient(client, known_hosts_file)
    else:
        # 显式加载系统主机密钥（RejectPolicy 下若主机未在 known_hosts 中会拒绝连接）
        try:
            client.load_system_host_keys()
        except Exception as exc:
            raise RuntimeError(
                f"known_hosts file not found: {known_hosts_file}. "
                f"Please run: ssh-keyscan -H {cfg.get('host', '<host>')} >> {known_hosts_file}"
            ) from exc

    auth_method = str(cfg.get("authMethod", "password")).lower()
    kwargs = {
        "hostname": cfg["host"],
        "port": int(cfg["port"]),
        "username": cfg["user"],
        "timeout": 30,
        "banner_timeout": 30,
        "auth_timeout": 30,
    }
    if auth_method == "password":
        password = cfg.get("password")
        if not password:
            raise ValueError("password auth selected but password is empty")
        kwargs["password"] = password
    else:
        key_file = cfg.get("keyFile") or cfg.get("privateKey")
        if not key_file:
            raise ValueError("key auth selected but keyFile/privateKey is empty")
        kwargs["key_filename"] = os.path.expanduser(str(key_file))

    add_trusted_port_alias(client, str(cfg["host"]), int(cfg["port"]))
    client.connect(**kwargs)
    return client


def mkdir_p(sftp: paramiko.SFTPClient, path: str) -> None:
    if not path or path == "/":
        return
    current = "/" if path.startswith("/") else ""
    for part in path.strip("/").split("/"):
        current = remote_join(current, part)
        try:
            sftp.stat(current)
        except FileNotFoundError:
            sftp.mkdir(current)


def upload_atomic(sftp: paramiko.SFTPClient, src: Path, remote_path: str) -> None:
    remote_parent = posixpath.dirname(remote_path)
    mkdir_p(sftp, remote_parent)
    temp_path = remote_path + ".tmp"
    sftp.put(str(src), temp_path)
    try:
        sftp.posix_rename(temp_path, remote_path)
    except (AttributeError, OSError):
        # Older SFTP servers may not support POSIX rename. Keep this fallback for
        # compatibility, while preferring the atomic replacement path above.
        try:
            sftp.remove(remote_path)
        except FileNotFoundError:
            pass
        sftp.rename(temp_path, remote_path)


def public_url(base: str, public_path: str) -> str:
    return base.rstrip("/") + "/" + public_path.lstrip("/")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_version_payload(data: bytes, src: Path) -> None:
    public_json = json.loads(data.decode("utf-8-sig"))
    local_json = json.loads(src.read_text(encoding="utf-8-sig"))
    for key in ("versionCode", "versionName", "channel", "isBeta", "apkName"):
        if public_json.get(key) != local_json.get(key):
            raise RuntimeError(
                f"{key} mismatch: public={public_json.get(key)!r}, local={local_json.get(key)!r}"
            )
    for key in ("fileSize", "sha256", "githubReleaseTag"):
        if key in local_json and public_json.get(key) != local_json.get(key):
            raise RuntimeError(
                f"{key} mismatch: public={public_json.get(key)!r}, local={local_json.get(key)!r}"
            )


def http_check(url: str, src: Path, public_path: str) -> None:
    headers = {
        "User-Agent": "GameMatrixDeploy/1.0",
        "Cache-Control": "no-cache",
    }

    if public_path.startswith("version"):
        request = urllib.request.Request(url, headers=headers, method="GET")
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read()
        verify_version_payload(data, src)
        return

    request = urllib.request.Request(url, headers=headers, method="HEAD")
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            if response.status >= 400:
                raise RuntimeError(f"HTTP {response.status}")
            content_length = response.headers.get("Content-Length")
            if content_length and int(content_length) != src.stat().st_size:
                raise RuntimeError(
                    f"size mismatch: public={content_length}, local={src.stat().st_size}"
                )
    except Exception:
        request = urllib.request.Request(
            url,
            headers={**headers, "Range": "bytes=0-0"},
            method="GET",
        )
        with urllib.request.urlopen(request, timeout=20) as response:
            if response.status >= 400:
                raise RuntimeError(f"HTTP {response.status}")
            content_range = response.headers.get("Content-Range", "")
            if "/" in content_range:
                public_size = int(content_range.rsplit("/", 1)[1])
                if public_size != src.stat().st_size:
                    raise RuntimeError(
                        f"size mismatch: public={public_size}, local={src.stat().st_size}"
                    )

    request = urllib.request.Request(url, headers=headers, method="GET")
    digest = hashlib.sha256()
    body_size = 0
    with urllib.request.urlopen(request, timeout=120) as response:
        while chunk := response.read(1024 * 1024):
            body_size += len(chunk)
            digest.update(chunk)
    if body_size != src.stat().st_size:
        raise RuntimeError(
            f"body size mismatch: public={body_size}, local={src.stat().st_size}"
        )
    remote_hash = digest.hexdigest()
    local_hash = sha256_file(src)
    if remote_hash != local_hash:
        raise RuntimeError(f"sha256 mismatch: public={remote_hash}, local={local_hash}")


def run_post_upload_commands(client: paramiko.SSHClient, cfg: dict) -> None:
    commands = cfg.get("postUploadCommands") or []
    for command in commands:
        if not command:
            continue
        stdin, stdout, stderr = client.exec_command(command, timeout=60)
        del stdin
        exit_code = stdout.channel.recv_exit_status()
        if exit_code != 0:
            message = stderr.read().decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"post upload command failed ({exit_code}): {message}")


def main() -> int:
    args = parse_args()
    cfg = load_config(Path(args.config))
    uploads = collect_uploads(args, cfg)
    remote_dir = cfg["remoteDir"].rstrip("/")

    print(f"Target VPS: {cfg['user']}@{cfg['host']}:{cfg['port']}")
    for src, dst, public_path in uploads:
        print(f"UPLOAD {src} -> {dst} [{public_path}] ({src.stat().st_size} bytes)")

    if args.dry_run:
        print("Dry run complete; nothing uploaded.")
        return 0

    client = connect(cfg)
    try:
        sftp = client.open_sftp()
        try:
            for src, dst, _public_path in uploads:
                upload_atomic(sftp, src, dst)
                print(f"OK {dst}")
        finally:
            sftp.close()
        run_post_upload_commands(client, cfg)
    finally:
        client.close()

    if not args.skip_verify:
        public_base = cfg["publicBaseUrl"]
        for src, _dst, public_path in uploads:
            url = public_url(public_base, public_path)
            try:
                http_check(url, src, public_path)
            except (urllib.error.URLError, TimeoutError, RuntimeError) as exc:
                raise RuntimeError(f"HTTP verify failed: {url}: {exc}") from exc
            print(f"VERIFY {url}")

    print("Upload complete.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
