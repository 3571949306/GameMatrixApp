#!/usr/bin/env python3
"""单模块发布工具：构建产物 → 本地清单双写 → 上传 HK 服务器 → 公网校验。

模块热更改造 Phase 4（docs/模块热更改造计划_2026-08-29.md）。

用法（以 tetris v101 为例）：
    python scripts/publish_module.py --id tetris \
        --apk module-store/feature/games/games/tetris/build/outputs/apk/release/tetris-release.apk \
        [--version-code 101] [--version-name 1.0.1]

行为：
1. 读取 assets/modules.json 中该模块条目，确定新 versionCode（--version-code 或自动+1）；
2. 目标远端文件名沿用现有命名规则（game_tetris_v101.apk / feature_ai_v100.apk）；
3. 更新条目 versionCode/versionName/fileName/fileSize/sha256/downloadUrl，
   顶层 version+1、catalogVersion+1、generatedAt 刷新，modules.json/catalog.json 双写并校验一致；
4. 经 paramiko 上传 APK 与两份清单到 upload_config_hk.json 指定的远端目录；
5. 公网回读（publicBaseUrl）校验 sha256 与清单一致。
"""

import argparse
import datetime
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urljoin, urlparse
from urllib.request import HTTPRedirectHandler, Request, build_opener

import paramiko

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "app" / "src" / "main" / "assets"
MANIFESTS = [ASSETS / "modules.json", ASSETS / "catalog.json"]
SAFE_ASSET_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
MAX_PUBLIC_METADATA_BYTES = 4 * 1024 * 1024
MAX_PUBLIC_APK_BYTES = 1024 * 1024 * 1024
MAX_PUBLIC_REDIRECTS = 5
REDIRECT_CODES = {301, 302, 303, 307, 308}


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def versioned_name(old: str, vc: int) -> str:
    return re.sub(r"_v\d+(\.\w+)$", rf"_v{vc}\1", old)


def load_creds(cfg: Path):
    d = json.loads(cfg.read_text(encoding="utf-8-sig"))
    pw = ""
    hk = REPO.parent / "ssh-keys"
    cred_txt = Path(__file__).resolve().parent.parent / "local_private" / "服务器部署" / "HKvps.txt"
    m = re.search(r"password\s*:\s*(\S+)", cred_txt.read_text(encoding="utf-8-sig", errors="replace"))
    if m:
        pw = m.group(1)
    return d, pw


def configure_ssh_client(client: paramiko.SSHClient, cfg: dict) -> None:
    """Require an operator-provided host key before any publish connection."""
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    known_hosts_file = (
        cfg.get("knownHostsFile")
        or os.environ.get("UPLOAD_KNOWN_HOSTS_FILE")
        or os.path.expanduser("~/.ssh/known_hosts")
    )
    known_hosts_path = Path(known_hosts_file).expanduser()
    if not known_hosts_path.is_file():
        raise RuntimeError(
            f"known_hosts file not found: {known_hosts_path}. "
            f"Please run: ssh-keyscan -H {cfg.get('host', '<host>')} >> {known_hosts_path}"
        )
    try:
        client.load_host_keys(str(known_hosts_path))
    except Exception as exc:
        raise RuntimeError(f"failed to load trusted host keys: {known_hosts_path}") from exc


def validate_publish_metadata_name(name: str) -> str:
    """Keep the remote SFTP target a single, bounded APK filename."""
    if not name or not SAFE_ASSET_NAME.fullmatch(name) or not name.lower().endswith(".apk"):
        raise ValueError(f"unsafe module APK filename: {name!r}")
    return name


def validate_public_base_url(raw_url: str) -> str:
    """Publishing must advertise an HTTPS endpoint, never a clear-text URL."""
    normalized = raw_url or ""
    if normalized != normalized.strip():
        raise ValueError("publicBaseUrl must not contain surrounding whitespace")
    normalized = normalized.strip()
    parsed = urlparse(normalized)
    try:
        port = parsed.port
    except ValueError as exc:
        raise ValueError("publicBaseUrl has an invalid port") from exc
    if (parsed.scheme.lower() != "https" or not parsed.hostname
            or parsed.username is not None or parsed.password is not None
            or parsed.fragment or port == 0 or (port is not None and not 1 <= port <= 65535)):
        raise ValueError("publicBaseUrl must be an HTTPS URL without credentials or fragments")
    return normalized.rstrip("/")


def _parse_public_https_url(raw_url: str):
    """Parse a public URL and reject credentials, fragments and bad ports."""
    normalized = raw_url or ""
    if normalized != normalized.strip():
        raise ValueError("public URL must not contain surrounding whitespace")
    normalized = normalized.strip()
    parsed = urlparse(normalized)
    try:
        port = parsed.port
    except ValueError as exc:
        raise ValueError("public URL has an invalid port") from exc
    if (parsed.scheme.lower() != "https" or not parsed.hostname
            or parsed.username is not None or parsed.password is not None
            or parsed.fragment or port == 0 or (port is not None and not 1 <= port <= 65535)):
        raise ValueError("public URL must be HTTPS without credentials or fragments")
    return normalized, parsed


class _NoRedirectHandler(HTTPRedirectHandler):
    """Expose redirects to the caller so every hop can be checked."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _public_origin(parsed) -> tuple[str, int]:
    return parsed.hostname.lower(), parsed.port or 443


def _resolve_public_redirect(current_url: str, location: str, expected_origin: tuple[str, int]) -> str:
    if not location or location != location.strip():
        raise ValueError("public verification redirect has no safe Location")
    next_url = urljoin(current_url, location)
    normalized, parsed = _parse_public_https_url(next_url)
    if _public_origin(parsed) != expected_origin:
        raise ValueError("public verification redirect changed origin")
    return normalized


def _open_public_response(url: str, max_bytes: int):
    """Open an HTTPS response with bounded same-origin redirects."""
    normalized, parsed = _parse_public_https_url(url)
    expected_origin = _public_origin(parsed)
    opener = build_opener(_NoRedirectHandler())
    current_url = normalized
    for redirect_count in range(MAX_PUBLIC_REDIRECTS + 1):
        request = Request(current_url, headers={"User-Agent": "GameMatrixApp-publisher/1"})
        try:
            response = opener.open(request, timeout=20)
        except HTTPError as error:
            if error.code not in REDIRECT_CODES:
                raise
            if redirect_count >= MAX_PUBLIC_REDIRECTS:
                raise RuntimeError("too many public verification redirects") from error
            try:
                location = error.headers.get("Location")
            finally:
                error.close()
            current_url = _resolve_public_redirect(current_url, location, expected_origin)
            continue
        status = response.getcode()
        if status is not None and not 200 <= status < 300:
            response.close()
            raise RuntimeError(f"public verification HTTP {status}")
        content_length = response.headers.get("Content-Length")
        if content_length:
            try:
                if int(content_length) > max_bytes:
                    response.close()
                    raise RuntimeError("public verification response is too large")
            except ValueError as exc:
                response.close()
                raise RuntimeError("public verification returned an invalid Content-Length") from exc
        return response
    raise RuntimeError("public verification redirect loop")


def _read_public_bytes(url: str, max_bytes: int) -> bytes:
    response = _open_public_response(url, max_bytes)
    try:
        chunks = []
        total = 0
        while True:
            chunk = response.read(1 << 20)
            if not chunk:
                break
            total += len(chunk)
            if total > max_bytes:
                raise RuntimeError("public verification response is too large")
            chunks.append(chunk)
        return b"".join(chunks)
    finally:
        response.close()


def _sha256_public_file(url: str) -> tuple[str, int]:
    response = _open_public_response(url, MAX_PUBLIC_APK_BYTES)
    digest = hashlib.sha256()
    total = 0
    try:
        while True:
            chunk = response.read(1 << 20)
            if not chunk:
                break
            total += len(chunk)
            if total > MAX_PUBLIC_APK_BYTES:
                raise RuntimeError("public APK is too large")
            digest.update(chunk)
    finally:
        response.close()
    return digest.hexdigest(), total


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--id", required=True)
    parser.add_argument("--apk", required=True, help="新构建的模块 APK 路径")
    parser.add_argument("--version-code", type=int, default=0, help="默认=清单当前值+1")
    parser.add_argument("--version-name", default="")
    parser.add_argument("--new-name", default="", help="默认沿用现有 fileName（跨版本稳定，保证更新按钮判定）")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    apk_path = (REPO / args.apk).resolve() if not Path(args.apk).is_absolute() else Path(args.apk)
    if not apk_path.exists():
        print(f"错误：APK 不存在 {apk_path}", file=sys.stderr)
        return 1

    data = json.loads(MANIFESTS[0].read_text(encoding="utf-8"))
    entry = next((m for m in data["modules"] if m["id"] == args.id), None)
    if entry is None:
        print(f"错误：清单中无模块 {args.id}", file=sys.stderr)
        return 1

    new_vc = args.version_code or (int(entry.get("versionCode", 0)) + 1)
    new_vn = args.version_name or entry.get("versionName", "1.0.0")
    old_vc = int(entry.get("versionCode", 0))
    if new_vc <= old_vc:
        print(f"错误：新 versionCode({new_vc}) 必须大于当前({old_vc})", file=sys.stderr)
        return 1

    # 沿用仓库既有约定：fileName 跨版本稳定（bundle 任务目标名亦不随 versionCode 变化），
    # 仅递增 versionCode/versionName——否则"已安装/有更新"判定会因文件名漂移而失效。
    old_name = entry.get("fileName") or f"{args.id}.apk"
    new_name = validate_publish_metadata_name(args.new_name or old_name)
    digest = sha256_of(apk_path)

    cfg, password = load_creds(REPO / "local_private" / "服务器部署" / "upload_config_hk.json")
    base = validate_public_base_url(cfg["publicBaseUrl"])
    entry.update({
        "versionCode": new_vc,
        "versionName": new_vn,
        "fileName": new_name,
        "fileSize": apk_path.stat().st_size,
        "sha256": digest,
        "downloadUrl": f"{base}/modules/{new_name}",
    })
    data["version"] = int(data.get("version", 0)) + 1
    data["catalogVersion"] = int(data.get("catalogVersion", 0)) + 1
    data["generatedAt"] = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    print(f"发布 {args.id}: {old_vc} -> {new_vc} ({new_vn})")
    print(f"  APK: {apk_path.name} ({apk_path.stat().st_size} bytes) sha256={digest[:16]}...")
    print(f"  顶层 version -> {data['version']}, catalogVersion -> {data['catalogVersion']}")
    if args.dry_run:
        print("dry-run：未写文件、未上传。")
        return 0

    payload = json.dumps(data, ensure_ascii=True, indent=2).encode("utf-8") + b"\n"
    for m in MANIFESTS:
        m.write_bytes(payload)
    if MANIFESTS[0].read_bytes() != MANIFESTS[1].read_bytes():
        print("错误：双写不一致！", file=sys.stderr)
        return 1

    # 上传（APK + 双清单）
    client = paramiko.SSHClient()
    configure_ssh_client(client, cfg)
    client.connect(cfg["host"], port=int(cfg.get("port", 22)), username=cfg["user"],
                   password=password, timeout=15, banner_timeout=15)
    sftp = client.open_sftp()
    remote_dir = cfg["remoteDir"].rstrip("/")
    sftp.put(str(apk_path), f"{remote_dir}/{new_name}")  # nginx /modules/ 剥前缀，APK 必须在 BASE_DIR 根
    sftp.put(str(MANIFESTS[0]), f"{remote_dir}/modules.json")
    sftp.put(str(MANIFESTS[1]), f"{remote_dir}/catalog.json")
    sftp.close()
    client.close()
    print(f"已上传: modules/{new_name} + modules.json + catalog.json -> {cfg['host']}:{remote_dir}")

    # 公网回读校验：清单和实际 APK 都必须从同一 HTTPS origin 返回，
    # 并且 APK 字节哈希/大小必须与刚发布的本地产物一致。仅回读清单
    # 无法发现对象存储/CDN 的旧缓存、部分上传或远端文件被替换。
    manifest_url = f"{base}/modules.json"
    remote = json.loads(_read_public_bytes(manifest_url, MAX_PUBLIC_METADATA_BYTES).decode("utf-8"))
    re_ = next((m for m in remote["modules"] if m["id"] == args.id), None)
    remote_name = re_.get("fileName") if re_ else None
    remote_apk_sha = ""
    remote_apk_size = -1
    if re_ and remote_name == new_name:
        remote_apk_sha, remote_apk_size = _sha256_public_file(
            f"{base}/modules/{new_name}"
        )
    ok = (re_ and remote_name == new_name
          and re_.get("sha256", "").lower() == digest.lower()
          and int(re_.get("versionCode", -1)) == new_vc
          and remote_apk_sha.lower() == digest.lower()
          and remote_apk_size == apk_path.stat().st_size)
    print("公网校验:", "PASS" if ok else "FAIL",
          f"(remote version={remote.get('version')}, apk_size={remote_apk_size}, "
          f"apk_sha256={remote_apk_sha[:16]}...)")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
