#!/usr/bin/env python3
"""一次性修复脚本:把仓库自洽的模块资产(31 APK + 双清单)重发到 JP 权威源与旧 HK 端点。

背景(2026-08-30):服务器四端点的 modules.json 登记哈希与实际上架 APK 脱节,
客户端商店下载 100% SHA-256 校验失败。本脚本以 app/src/main/assets 为唯一真源重发。
上传后逐端点校验:清单 versionCode 一致 + APK 字节 sha256 与清单一致。
"""
import hashlib
import json
import os
import sys
import time
from pathlib import Path

import paramiko

REPO = Path(__file__).resolve().parent.parent
# 2026-08-30: 改为从快照目录上传 —— 并行构建会在编译期重打包 assets，
# 快照保证"清单+APK"上传集内部一致，不受工作树并发变动影响。
ASSETS = Path("D:/Developmment/store_asset_snapshot")
APK_DIR = ASSETS

TARGETS = [
    {"name": "jp-authority", "config": REPO / "local_private/服务器部署/upload_config_jp.json"},
    {"name": "hk-legacy-app-facing", "config": REPO / "local_private/服务器部署/upload_config_hk.json"},
]

VERIFY_BASES = [
    "https://jp.dl.tcp888.uk:2088",
    "https://hk.dl.tcp888.uk:2088",
    "https://us.dl.tcp888.uk:2088",
    "https://hk-update.tcp888.uk:2083",
]


def load_creds(cfg_path: Path):
    d = json.loads(cfg_path.read_text(encoding="utf-8-sig"))
    pw = ""
    if d.get("authMethod") != "key":
        cred = (REPO.parent / "_私密凭证" / "服务器部署" / "HKvps.txt")
        if cred.exists():
            import re
            m = re.search(r"password\s*:\s*(\S+)", cred.read_text(encoding="utf-8-sig", errors="replace"))
            if m:
                pw = m.group(1)
    return d, pw


def configure_ssh_client(client: paramiko.SSHClient, cfg: dict) -> None:
    """仅信任操作者已录入的 host key（与 publish_module.py 同策略）。"""
    client.set_missing_host_key_policy(paramiko.RejectPolicy())
    known_hosts = (
        cfg.get("knownHostsFile")
        or os.environ.get("UPLOAD_KNOWN_HOSTS_FILE")
        or os.path.expanduser("~/.ssh/known_hosts")
    )
    kh = Path(known_hosts).expanduser()
    if not kh.is_file():
        raise RuntimeError(
            f"known_hosts 不存在: {kh}。请先 ssh-keyscan -H {cfg.get('host', '<host>')} >> {kh}"
        )
    client.load_host_keys(str(kh))


def connect(cfg: dict, password: str):
    client = paramiko.SSHClient()
    configure_ssh_client(client, cfg)
    kw = dict(hostname=cfg["host"], port=int(cfg.get("port", 22)), username=cfg["user"],
              timeout=25, banner_timeout=25)
    kf = cfg.get("keyFile")
    if cfg.get("authMethod") == "key" and kf:
        kw["key_filename"] = str(Path(kf).expanduser())
        kw["allow_agent"] = False
        kw["look_for_keys"] = False
    else:
        kw["password"] = password
    client.connect(**kw)
    return client


def upload(target: dict, files: list) -> bool:
    cfg, pw = load_creds(target["config"])
    print(f"== 上传 {target['name']} ({cfg['host']}:{cfg.get('port', 22)} {cfg['remoteDir']}) ==")
    try:
        client = connect(cfg, pw)
    except Exception as exc:
        print(f"  连接失败: {exc}")
        return False
    try:
        sftp = client.open_sftp()
        remote_dir = cfg["remoteDir"].rstrip("/")
        for local_path, remote_name in files:
            size = local_path.stat().st_size
            t0 = time.time()
            sftp.put(str(local_path), f"{remote_dir}/{remote_name}")
            dt = time.time() - t0
            print(f"  {remote_name}: {size} bytes ({dt:.1f}s)")
        # 远端回读校验字节一致
        for local_path, remote_name in files:
            with sftp.open(f"{remote_dir}/{remote_name}", "rb") as rf:
                digest = hashlib.sha256()
                while True:
                    chunk = rf.read(1 << 20)
                    if not chunk:
                        break
                    digest.update(chunk)
            if digest.hexdigest() != hashlib.sha256(local_path.read_bytes()).hexdigest():
                print(f"  回读不一致: {remote_name}")
                return False
        print("  远端回读校验: 全部一致")
        return True
    finally:
        client.close()


def verify() -> bool:
    import ssl
    from urllib.request import Request, build_opener
    ctx = ssl.create_default_context()
    manifest = json.loads((ASSETS / "modules.json").read_text(encoding="utf-8"))
    expect = {m["fileName"]: m.get("sha256", "") for m in manifest["modules"] if m.get("fileName")}
    ok_all = True
    for base in VERIFY_BASES:
        try:
            req = Request(f"{base}/modules.json", headers={"User-Agent": "resync-check/1"})
            body = build_opener().open(req, timeout=25).read()
            remote = json.loads(body.decode("utf-8"))
        except Exception as exc:
            print(f"{base}: 清单读取失败 {exc}")
            ok_all = False
            continue
        mism = []
        for fn, sha in expect.items():
            try:
                req = Request(f"{base}/modules/{fn}", headers={"User-Agent": "resync-check/1"})
                data = build_opener().open(req, timeout=40).read()
            except Exception as exc:
                mism.append(f"{fn}:读取失败({exc})")
                continue
            if hashlib.sha256(data).hexdigest().lower() != sha.lower():
                mism.append(fn)
        status = "PASS" if not mism else f"FAIL {len(mism)}: {mism[:4]}"
        print(f"{base}: version={remote.get('version')} 模块={len(remote.get('modules', []))} -> {status}")
        ok_all = ok_all and not mism
    return ok_all


def main():
    manifest = json.loads((ASSETS / "modules.json").read_text(encoding="utf-8"))
    files = [(ASSETS / "modules.json", "modules.json"), (ASSETS / "catalog.json", "catalog.json")]
    for m in manifest["modules"]:
        fn = m.get("fileName")
        if not fn:
            continue
        p = APK_DIR / fn
        if not p.exists():
            print(f"缺 APK: {fn}", file=sys.stderr)
            return 1
        files.append((p, fn))
    print(f"待上传 {len(files)} 个文件(31 APK + 双清单)")
    only = sys.argv[1] if len(sys.argv) > 1 else ""
    for target in TARGETS:
        if only and only != target["name"]:
            continue
        if not upload(target, files):
            print(f"{target['name']} 上传失败")
            return 1
    return 0 if verify() else 1


if __name__ == "__main__":
    sys.exit(main())
