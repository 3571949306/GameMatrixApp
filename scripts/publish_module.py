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
import re
import sys
from pathlib import Path

import paramiko

REPO = Path(__file__).resolve().parent.parent
ASSETS = REPO / "app" / "src" / "main" / "assets"
MANIFESTS = [ASSETS / "modules.json", ASSETS / "catalog.json"]


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
    new_name = args.new_name or old_name
    digest = sha256_of(apk_path)

    cfg, password = load_creds(REPO / "local_private" / "服务器部署" / "upload_config_hk.json")
    base = cfg["publicBaseUrl"].rstrip("/")
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
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
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

    # 公网回读校验
    import urllib.request
    url = f"{base}/modules.json"
    with urllib.request.urlopen(url, timeout=20) as r:
        remote = json.loads(r.read().decode("utf-8"))
    re_ = next((m for m in remote["modules"] if m["id"] == args.id), None)
    ok = re_ and re_["sha256"] == digest and int(re_["versionCode"]) == new_vc
    print("公网校验:", "PASS" if ok else "FAIL", f"(remote version={remote.get('version')})")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
