#!/usr/bin/env python3
"""Atomically deploy a signed Catalog V2 and its packages to production."""

from __future__ import annotations

import argparse
import hashlib
import json
import posixpath
import re
import shlex
import sys
import time
import urllib.request
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import upload_to_vps  # noqa: E402


NGINX_CONFIG = "/etc/nginx/conf.d/02-hk-update.conf"
SIGNATURE_SNIPPET = "/etc/nginx/snippets/game-matrix-catalog-signature.conf"
MODULE_ROOT = "/var/www/modules"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True)
    parser.add_argument("--catalog", type=Path, required=True)
    parser.add_argument("--signature", type=Path, required=True)
    parser.add_argument("--nginx-include", type=Path, required=True)
    parser.add_argument("--public-key", type=Path, required=True)
    parser.add_argument("--module-dir", type=Path, required=True)
    return parser.parse_args()


def exec_checked(client, command: str) -> str:
    stdin, stdout, stderr = client.exec_command(command, timeout=60)
    del stdin
    code = stdout.channel.recv_exit_status()
    output = stdout.read().decode("utf-8", errors="replace")
    error = stderr.read().decode("utf-8", errors="replace")
    if code:
        raise RuntimeError(f"remote command failed ({code}): {error.strip()}")
    return output


def inject_signature_include(config: str) -> str:
    include_line = f"        include {SIGNATURE_SNIPPET};"
    for endpoint in ("catalog.json", "modules.json"):
        pattern = re.compile(
            rf"(location\s*=\s*/{re.escape(endpoint)}\s*\{{)(.*?)(\n\s*\}})",
            re.DOTALL,
        )
        match = pattern.search(config)
        if not match:
            raise RuntimeError(f"nginx location not found: /{endpoint}")
        body = match.group(2)
        if SIGNATURE_SNIPPET not in body:
            replacement = match.group(1) + "\n" + include_line + body + match.group(3)
            config = config[: match.start()] + replacement + config[match.end() :]
    return config


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verify_public(
    base: str,
    catalog: Path,
    signature: str,
    public_key: str,
    module_dir: Path,
) -> None:
    sys.path.insert(0, str(TOOLS_DIR.parent / "scripts"))
    from catalog_signing import verify_catalog_signature

    expected = catalog.read_bytes()
    for endpoint in ("catalog.json", "modules.json"):
        url = f"{base.rstrip('/')}/{endpoint}"
        request = urllib.request.Request(
            url,
            headers={
                "Cache-Control": "no-cache",
                "User-Agent": "Mozilla/5.0 GameMatrixDeploy/1.0",
            },
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            body = response.read()
            header_signature = response.headers.get("X-Catalog-Signature", "").strip()
        if body != expected:
            raise RuntimeError(f"public {endpoint} does not match the deployed catalog")
        if header_signature != signature:
            raise RuntimeError(f"public {endpoint} signature header mismatch")
        if not verify_catalog_signature(body, header_signature, public_key):
            raise RuntimeError(f"public {endpoint} signature verification failed")

    parsed = json.loads(expected.decode("utf-8"))
    for module in parsed["modules"]:
        if module.get("deliveryType") == "builtin":
            continue
        package = module["package"]
        filename = package["fileName"]
        local_package = module_dir / filename
        if not local_package.is_file():
            raise RuntimeError(f"local package is missing during public verification: {filename}")
        request = urllib.request.Request(
            package["downloadUrl"],
            headers={"Cache-Control": "no-cache", "User-Agent": "GameMatrixDeploy/1.0"},
        )
        digest = hashlib.sha256()
        size = 0
        with urllib.request.urlopen(request, timeout=120) as response:
            while chunk := response.read(1024 * 1024):
                size += len(chunk)
                digest.update(chunk)
        if size != local_package.stat().st_size or size != package["fileSize"]:
            raise RuntimeError(f"public package size mismatch: {filename}")
        if digest.hexdigest().lower() != package["sha256"].lower():
            raise RuntimeError(f"public package SHA-256 mismatch: {filename}")


def main() -> int:
    args = parse_args()
    cfg = upload_to_vps.load_config(Path(args.config))
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    signature = args.signature.read_text(encoding="ascii").strip()
    public_key = args.public_key.read_text(encoding="ascii").strip()
    include_text = args.nginx_include.read_text(encoding="ascii")
    if signature not in include_text:
        raise RuntimeError("nginx signature include does not match the detached signature")

    expected_packages = {
        module["package"]["fileName"]: module["package"]["sha256"].lower()
        for module in catalog["modules"]
        if module.get("deliveryType") != "builtin"
    }
    for name, expected_hash in expected_packages.items():
        package = args.module_dir / name
        if not package.is_file() or sha256(package) != expected_hash:
            raise RuntimeError(f"package missing or hash mismatch: {name}")

    client = upload_to_vps.connect(cfg)
    stamp = time.strftime("%Y%m%d-%H%M%S", time.gmtime())
    backup_dir = f"/var/backups/game-matrix/{stamp}"
    sftp = None
    try:
        exec_checked(client, f"mkdir -p {shlex.quote(backup_dir)} /etc/nginx/snippets")
        for remote in (
            f"{MODULE_ROOT}/catalog.json",
            f"{MODULE_ROOT}/modules.json",
            NGINX_CONFIG,
            SIGNATURE_SNIPPET,
        ):
            command = (
                f"if test -e {shlex.quote(remote)}; then "
                f"cp -a {shlex.quote(remote)} {shlex.quote(backup_dir + '/' + posixpath.basename(remote))}; fi"
            )
            exec_checked(client, command)

        sftp = client.open_sftp()
        remote_config = sftp.open(NGINX_CONFIG, "r").read().decode("utf-8")
        updated_config = inject_signature_include(remote_config).encode("utf-8")

        for package_name in expected_packages:
            upload_to_vps.upload_atomic(
                sftp, args.module_dir / package_name, f"{MODULE_ROOT}/{package_name}"
            )
        upload_to_vps.upload_atomic(sftp, args.catalog, f"{MODULE_ROOT}/catalog.json")
        upload_to_vps.upload_atomic(sftp, args.catalog, f"{MODULE_ROOT}/modules.json")
        upload_to_vps.upload_atomic(sftp, args.nginx_include, SIGNATURE_SNIPPET)

        config_temp = NGINX_CONFIG + ".tmp"
        with sftp.open(config_temp, "wb") as handle:
            handle.write(updated_config)
        exec_checked(client, f"mv -f {shlex.quote(config_temp)} {shlex.quote(NGINX_CONFIG)}")
        exec_checked(client, "nginx -t")
        exec_checked(client, "systemctl reload nginx")
    except Exception:
        try:
            exec_checked(
                client,
                "for f in catalog.json modules.json 02-hk-update.conf "
                "game-matrix-catalog-signature.conf; do "
                f"test -e {shlex.quote(backup_dir)}/$f || continue; "
                "case $f in 02-hk-update.conf) d=/etc/nginx/conf.d/$f;; "
                "game-matrix-catalog-signature.conf) d=/etc/nginx/snippets/$f;; "
                f"*) d={MODULE_ROOT}/$f;; esac; cp -a {shlex.quote(backup_dir)}/$f $d; done; "
                "nginx -t && systemctl reload nginx",
            )
        finally:
            raise
    finally:
        if sftp is not None:
            sftp.close()
        client.close()

    verify_public(cfg["publicBaseUrl"], args.catalog, signature, public_key, args.module_dir)
    print(
        f"Production Catalog V2 deployed and verified: modules={len(catalog['modules'])} "
        f"backup={backup_dir}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
